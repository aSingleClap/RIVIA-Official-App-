package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.util.Log
import java.util.Locale
import kotlin.math.log10
import kotlin.math.tanh

data class ClipMetrics(
  val samplesCount: Int,
  val clipLengthSec: Float,
  val rms: Float,
  val peak: Float,
  val silentCapture: Boolean,
)

data class MicCaptureResult(
  val mode: String, // "A" or "B"
  val samples16kMono: FloatArray,
  val pcm16Bytes: ByteArray,
  val clipLengthSec: Float,
  val rms: Float,
  val peak: Float,
  val silentCapture: Boolean,
  val recognizerPackage: String,
  val captureError: String? = null,
  val pipeBrokeEarly: Boolean = false,
  val audioRecordStarted: Boolean = false,
  val pipeBytesWritten: Long = 0L,
  val vadSpeechDetected: Boolean = false,
  val vadEndedTurn: Boolean = false,
  val vadMaxProb: Float = 0f,
  val speechDurationSec: Float = 0f,
  val audioSource: String = "VOICE_RECOGNITION",
  val inputDevice: String = "unknown",
  val pipeClosedAtMs: Long = 0L,
  val preScoredWindows: List<EmotionPrediction> = emptyList(),
  val remainderWindowSamples: FloatArray = FloatArray(0),
  val rmsAfterGain: Float = 0f,
  val appliedGainDb: Float = 0f,
  val peakAfterGain: Float = 0f,
  val finalScoreStartedAtVadEnd: Boolean = false,
  val preStartedScoreDeferred: Deferred<Result<EmotionPrediction>>? = null,
)

/**
 * Fix 7: Automatic Gain Control (AGC) + peak limiter applied ONLY to the PCM16 audio
 * written to the SpeechRecognizer pipe.
 * - Target RMS: about -20 dBFS (0.10 linear amplitude)
 * - Max gain: +20 dB (10.0x linear amplitude)
 * - Soft-knee + hard peak limiter so peaks never clip [-32767, 32767] / [-1.0, 1.0].
 * - Silero VAD, saved WAV clips, and the audEERING emotion model keep the raw un-amplified audio.
 */
class PipeAutoGainProcessor(
  val targetRmsLinear: Float = TARGET_RMS_LINEAR,
  val maxGainLinear: Float = MAX_GAIN_LINEAR,
) {
  companion object {
    const val TARGET_RMS_DBFS = -20.0f
    const val TARGET_RMS_LINEAR = 0.10f // 10^(-20 / 20) = 0.10
    const val MAX_GAIN_DB = 20.0f
    const val MAX_GAIN_LINEAR = 10.0f // 10^(+20 / 20) = 10.0x
    private const val MIN_ACTIVE_RMS = 1e-5f
    private const val LIMITER_KNEE = 0.85f
    private const val LIMITER_HEADROOM = 1.0f - LIMITER_KNEE // 0.15f

    /**
     * Smooth soft-knee limiter above [LIMITER_KNEE] (0.85) approaching 1.0 asymptotically via tanh,
     * followed by a hard clamp in [-32767/32768, +32767/32768] so peaks NEVER clip.
     */
    fun limitSample(x: Float): Float {
      val absX = abs(x)
      if (absX <= LIMITER_KNEE) return x
      val excess = (absX - LIMITER_KNEE) / LIMITER_HEADROOM
      val compressed = LIMITER_KNEE + LIMITER_HEADROOM * tanh(excess.toDouble()).toFloat()
      val clamped = compressed.coerceAtMost(32767f / 32768f)
      return if (x >= 0f) clamped else -clamped
    }

    fun linearToDbFs(linear: Float): Float {
      if (linear <= 1e-9f) return -180.0f
      return (20.0 * log10(linear.toDouble())).toFloat()
    }
  }

  private var rawSumSquares: Double = 0.0
  private var afterGainSumSquares: Double = 0.0
  private var totalSamplesProcessed: Long = 0L
  private var maxPeakBeforeGain: Float = 0f
  private var maxPeakAfterGain: Float = 0f
  private var smoothedGain: Float = 1.0f
  private var gainInitialized: Boolean = false

  fun reset() {
    rawSumSquares = 0.0
    afterGainSumSquares = 0.0
    totalSamplesProcessed = 0L
    maxPeakBeforeGain = 0f
    maxPeakAfterGain = 0f
    smoothedGain = 1.0f
    gainInitialized = false
  }

  /**
   * Processes a chunk of raw 16-bit PCM samples ([rawShorts], [count]) and returns the
   * gain-adjusted + limited little-endian PCM16 [ByteArray] to write ONLY to the SpeechRecognizer pipe.
   * Never mutates [rawShorts].
   */
  fun processChunkToPcm16Bytes(rawShorts: ShortArray, count: Int): ByteArray {
    if (count <= 0) return ByteArray(0)
    var chunkSumSq = 0.0
    for (i in 0 until count) {
      val norm = rawShorts[i] / 32768.0f
      val absNorm = abs(norm)
      if (absNorm > maxPeakBeforeGain) maxPeakBeforeGain = absNorm
      chunkSumSq += (norm * norm).toDouble()
    }
    rawSumSquares += chunkSumSq
    val chunkRms = sqrt(chunkSumSq / count.toDouble()).toFloat()

    val desiredGain =
      if (chunkRms >= MIN_ACTIVE_RMS) {
        (targetRmsLinear / chunkRms).coerceIn(1.0f, maxGainLinear)
      } else {
        1.0f
      }

    smoothedGain =
      if (!gainInitialized && chunkRms >= MIN_ACTIVE_RMS) {
        gainInitialized = true
        desiredGain
      } else if (chunkRms >= MIN_ACTIVE_RMS) {
        (0.6f * desiredGain + 0.4f * smoothedGain).coerceIn(1.0f, maxGainLinear)
      } else {
        smoothedGain
      }

    val outBytes = ByteArray(count * 2)
    var chunkOutSumSq = 0.0
    for (i in 0 until count) {
      val rawNorm = rawShorts[i] / 32768.0f
      val amplified = rawNorm * smoothedGain
      val limited = limitSample(amplified)
      val absLimited = abs(limited)
      if (absLimited > maxPeakAfterGain) maxPeakAfterGain = absLimited
      chunkOutSumSq += (limited * limited).toDouble()

      val pcmInt = (limited * 32768.0f).toInt().coerceIn(-32767, 32767)
      outBytes[i * 2] = (pcmInt and 0xFF).toByte()
      outBytes[i * 2 + 1] = ((pcmInt shr 8) and 0xFF).toByte()
    }
    afterGainSumSquares += chunkOutSumSq
    totalSamplesProcessed += count.toLong()
    return outBytes
  }

  val rmsBeforeGain: Float
    get() =
      if (totalSamplesProcessed > 0L) {
        sqrt(rawSumSquares / totalSamplesProcessed.toDouble()).toFloat()
      } else {
        0f
      }

  val rmsAfterGain: Float
    get() =
      if (totalSamplesProcessed > 0L) {
        sqrt(afterGainSumSquares / totalSamplesProcessed.toDouble()).toFloat()
      } else {
        0f
      }

  val appliedGainDb: Float
    get() {
      val before = rmsBeforeGain
      val after = rmsAfterGain
      if (before <= 1e-6f || after <= 1e-6f) return 0f
      return (20.0 * log10((after / before).toDouble())).toFloat()
    }

  val peakBeforeGain: Float
    get() = maxPeakBeforeGain

  val peakAfterGain: Float
    get() = maxPeakAfterGain
}

/**
 * Step 1 Edge-Silence Trimmer:
 * - Normal VAD speech start: keeps a ring buffer of the last ~200 ms ([PRE_ROLL_SAMPLES] = 3,200 samples @ 16 kHz)
 *   of audio before speech starts, starting emotion-window audio and saved WAV audio at speech start minus that 200 ms pre-roll.
 * - Recognizer-triggered speech start ([SileroVadTurnSnapshot.speechStartedByExternalConfirm]): keeps a 1.5 s
 *   ([RECOGNIZER_RING_BUFFER_SAMPLES] = 24,000 samples @ 16 kHz) ring buffer. Begins the clip at the earliest
 *   buffered chunk with speech probability >= 0.35, minus the 200 ms pre-roll (or the whole 1.5 s buffer if none qualifies).
 * - Preserves mid-turn silence/pauses inside the turn: any below-threshold chunks (`consecutiveSilenceMs > 0`)
 *   followed by resumed speech (`consecutiveSilenceMs == 0`) are flushed into committed audio in order.
 * - Post-roll at turn end: keeps the first 200 ms ([POST_ROLL_SAMPLES] = 3,200 samples) of the trailing
 *   end-of-turn silence instead of dropping all 700 ms, so word endings aren't clipped.
 */
class EdgeSilenceTrimmer(
  val preRollSamplesCapacity: Int = MicSharingRecorder.PRE_ROLL_SAMPLES,
  val recognizerBufferSamplesCapacity: Int = MicSharingRecorder.RECOGNIZER_RING_BUFFER_SAMPLES,
  val recognizerSpeechProbThreshold: Float = MicSharingRecorder.RECOGNIZER_SPEECH_PROB_THRESHOLD,
  val postRollSamplesCapacity: Int = MicSharingRecorder.POST_ROLL_SAMPLES,
  val windowSamples4s: Int = MicSharingRecorder.WINDOW_SAMPLES_4S,
  private val onFullWindowReady: ((FloatArray) -> Unit)? = null,
) {
  private data class BufferedChunk(
    val samples: FloatArray,
    val prob: Float,
  )

  private val preRollRing = FloatArray(preRollSamplesCapacity)
  private var preRollWritePos = 0
  private var preRollCount = 0

  private val recognizerRingChunks = ArrayDeque<BufferedChunk>()
  private var recognizerRingSamples = 0

  private val candidateOnsetChunks = ArrayList<FloatArray>()
  private val pendingTrailingSilenceChunks = ArrayList<FloatArray>()

  var speechHasStarted: Boolean = false
    private set

  private val committedFloatChunks = ArrayList<FloatArray>()
  private var totalCommittedSamples = 0

  private val windowBuffer4s = FloatArray(windowSamples4s)
  private var windowSampleIndex = 0

  fun onChunk512(chunk512: FloatArray, snap: SileroVadTurnSnapshot) {
    if (!speechHasStarted) {
      if (!snap.speechStarted) {
        pushToRecognizerRing(chunk512, snap.chunkProb)
        if (snap.consecutiveSpeechMs > 0) {
          // Candidate speech onset chunk (prob >= startProbThreshold); hold outside preRollRing
          // so preRollRing retains the ~200 ms immediately preceding speech onset.
          candidateOnsetChunks.add(chunk512.copyOf())
        } else {
          // Not speech: fold any short false-start chunks into preRollRing, then push this chunk.
          if (candidateOnsetChunks.isNotEmpty()) {
            for (c in candidateOnsetChunks) {
              pushToPreRoll(c)
            }
            candidateOnsetChunks.clear()
          }
          pushToPreRoll(chunk512)
        }
      } else if (snap.speechStartedByExternalConfirm) {
        // Recognizer-triggered speech start: flush from 1.5 s ring buffer starting at earliest
        // buffered chunk with prob >= 0.35 minus 200 ms pre-roll (or whole buffer if none qualifies),
        // then handle the current chunk according to whether it is active speech or silence.
        onRecognizerTriggeredStart()
        handlePostStartChunk(chunk512, snap)
      } else {
        // Normal VAD speech start! Commit: (1) ~200 ms pre-roll, (2) speech onset chunks, (3) current chunk.
        speechHasStarted = true
        val preRoll = extractPreRollSamples()
        if (preRoll.isNotEmpty()) {
          commitSamples(preRoll)
        }
        if (candidateOnsetChunks.isNotEmpty()) {
          for (c in candidateOnsetChunks) {
            commitSamples(c)
          }
          candidateOnsetChunks.clear()
        }
        recognizerRingChunks.clear()
        recognizerRingSamples = 0
        commitSamples(chunk512.copyOf())
      }
    } else {
      handlePostStartChunk(chunk512, snap)
    }
  }

  /**
   * Flushes the 1.5 s ring buffer when speech start was triggered by the recognizer
   * (`notifyRecognizerHeardSpeech` / `notifyExternalSpeechConfirmed`).
   * Begins at the earliest buffered chunk with speech probability >= 0.35 minus the 200 ms pre-roll,
   * or commits the whole 1.5 s buffer if no buffered chunk qualifies.
   */
  fun onRecognizerTriggeredStart() {
    if (speechHasStarted) return
    speechHasStarted = true
    val fromRing = extractRecognizerTriggeredBufferSamples()
    if (fromRing.isNotEmpty()) {
      commitSamples(fromRing)
    }
    recognizerRingChunks.clear()
    recognizerRingSamples = 0
    candidateOnsetChunks.clear()
  }

  private fun handlePostStartChunk(chunk512: FloatArray, snap: SileroVadTurnSnapshot) {
    if (snap.consecutiveSilenceMs == 0) {
      // Active speech chunk (prob >= endProbThreshold):
      // If there were buffered below-threshold chunks, they were a mid-turn pause -> keep them!
      if (pendingTrailingSilenceChunks.isNotEmpty()) {
        for (pauseChunk in pendingTrailingSilenceChunks) {
          commitSamples(pauseChunk)
        }
        pendingTrailingSilenceChunks.clear()
      }
      commitSamples(chunk512.copyOf())
    } else {
      // Below endProbThreshold (consecutiveSilenceMs > 0):
      // Hold in pendingTrailingSilenceChunks. If speech resumes, it will be committed as a mid-turn pause;
      // if the turn ends, the first 200 ms will be kept as post-roll and the rest dropped.
      pendingTrailingSilenceChunks.add(chunk512.copyOf())
    }
  }

  /**
   * Finalizes the turn:
   * - If speech started and ended in a trailing below-threshold silence run, keeps the first 200 ms
   *   ([postRollSamplesCapacity]) of [pendingTrailingSilenceChunks] so word endings aren't clipped,
   *   and drops the remaining ~500 ms of trailing silence.
   * - If speech started and was not in a trailing silence run, commits any [leftoverSamples].
   */
  fun finishTurn(leftoverSamples: FloatArray = FloatArray(0)): TrimmedTurnAudio {
    if (speechHasStarted) {
      if (pendingTrailingSilenceChunks.isEmpty()) {
        if (leftoverSamples.isNotEmpty()) {
          commitSamples(leftoverSamples.copyOf())
        }
      } else if (postRollSamplesCapacity > 0) {
        val postRoll = extractLeadingSamples(pendingTrailingSilenceChunks, postRollSamplesCapacity)
        if (postRoll.isNotEmpty()) {
          commitSamples(postRoll)
        }
      }
    }
    // Drop the remainder of pendingTrailingSilenceChunks after the 200 ms post-roll
    pendingTrailingSilenceChunks.clear()
    candidateOnsetChunks.clear()

    val allCommitted = FloatArray(totalCommittedSamples)
    var pos = 0
    for (arr in committedFloatChunks) {
      System.arraycopy(arr, 0, allCommitted, pos, arr.size)
      pos += arr.size
    }

    val remainder =
      if (windowSampleIndex > 0) {
        windowBuffer4s.copyOfRange(0, windowSampleIndex)
      } else {
        FloatArray(0)
      }

    return TrimmedTurnAudio(
      trimmedSamples = allCommitted,
      trimmedPcm16LeBytes = MicSharingRecorder.floatArrayToPcm16LeBytes(allCommitted),
      remainderWindowSamples = remainder,
      preRollFallbackSamples = if (!speechHasStarted) extractRecognizerBufferAllSamples() else FloatArray(0),
    )
  }

  private fun pushToRecognizerRing(samples: FloatArray, prob: Float) {
    if (recognizerBufferSamplesCapacity <= 0 || samples.isEmpty()) return
    recognizerRingChunks.addLast(BufferedChunk(samples.copyOf(), prob))
    recognizerRingSamples += samples.size
    while (recognizerRingSamples > recognizerBufferSamplesCapacity && recognizerRingChunks.isNotEmpty()) {
      val excess = recognizerRingSamples - recognizerBufferSamplesCapacity
      val oldest = recognizerRingChunks.first()
      if (oldest.samples.size <= excess) {
        recognizerRingChunks.removeFirst()
        recognizerRingSamples -= oldest.samples.size
      } else {
        val trimmedOldest = oldest.samples.copyOfRange(excess, oldest.samples.size)
        recognizerRingChunks.removeFirst()
        recognizerRingChunks.addFirst(BufferedChunk(trimmedOldest, oldest.prob))
        recognizerRingSamples -= excess
      }
    }
  }

  private fun extractRecognizerTriggeredBufferSamples(): FloatArray {
    if (recognizerRingSamples <= 0 || recognizerRingChunks.isEmpty()) return FloatArray(0)
    val allBuffered = extractRecognizerBufferAllSamples()
    var sampleOffset = 0
    var earliestQualifyingOffset = -1
    for (chunk in recognizerRingChunks) {
      if (chunk.prob >= recognizerSpeechProbThreshold) {
        earliestQualifyingOffset = sampleOffset
        break
      }
      sampleOffset += chunk.samples.size
    }
    if (earliestQualifyingOffset < 0) {
      // No buffered chunk had prob >= 0.35 -> keep the whole 1.5 s buffer
      return allBuffered
    }
    val startIndex = (earliestQualifyingOffset - preRollSamplesCapacity).coerceAtLeast(0)
    return if (startIndex == 0) {
      allBuffered
    } else {
      allBuffered.copyOfRange(startIndex, allBuffered.size)
    }
  }

  private fun extractRecognizerBufferAllSamples(): FloatArray {
    if (recognizerRingSamples <= 0 || recognizerRingChunks.isEmpty()) {
      return extractPreRollSamples()
    }
    val out = FloatArray(recognizerRingSamples)
    var pos = 0
    for (chunk in recognizerRingChunks) {
      System.arraycopy(chunk.samples, 0, out, pos, chunk.samples.size)
      pos += chunk.samples.size
    }
    return out
  }

  private fun extractLeadingSamples(chunks: List<FloatArray>, maxSamples: Int): FloatArray {
    if (maxSamples <= 0 || chunks.isEmpty()) return FloatArray(0)
    var available = 0
    for (c in chunks) {
      available += c.size
      if (available >= maxSamples) {
        available = maxSamples
        break
      }
    }
    if (available <= 0) return FloatArray(0)
    val out = FloatArray(available)
    var pos = 0
    for (c in chunks) {
      val toCopy = minOf(c.size, available - pos)
      if (toCopy > 0) {
        System.arraycopy(c, 0, out, pos, toCopy)
        pos += toCopy
      }
      if (pos >= available) break
    }
    return out
  }

  private fun pushToPreRoll(samples: FloatArray) {
    if (preRollSamplesCapacity <= 0 || samples.isEmpty()) return
    for (s in samples) {
      preRollRing[preRollWritePos] = s
      preRollWritePos = (preRollWritePos + 1) % preRollSamplesCapacity
      if (preRollCount < preRollSamplesCapacity) {
        preRollCount++
      }
    }
  }

  fun extractPreRollSamples(): FloatArray {
    if (preRollCount <= 0) return FloatArray(0)
    val out = FloatArray(preRollCount)
    val startIdx =
      if (preRollCount == preRollSamplesCapacity) {
        preRollWritePos
      } else {
        0
      }
    for (i in 0 until preRollCount) {
      out[i] = preRollRing[(startIdx + i) % preRollSamplesCapacity]
    }
    return out
  }

  private fun commitSamples(samples: FloatArray) {
    if (samples.isEmpty()) return
    committedFloatChunks.add(samples)
    totalCommittedSamples += samples.size

    var winOffset = 0
    while (winOffset < samples.size) {
      val winNeeded = windowSamples4s - windowSampleIndex
      val winToCopy = minOf(winNeeded, samples.size - winOffset)
      System.arraycopy(samples, winOffset, windowBuffer4s, windowSampleIndex, winToCopy)
      windowSampleIndex += winToCopy
      winOffset += winToCopy

      if (windowSampleIndex == windowSamples4s) {
        val windowCopy = windowBuffer4s.copyOf()
        windowSampleIndex = 0
        onFullWindowReady?.invoke(windowCopy)
      }
    }
  }
}

data class TrimmedTurnAudio(
  val trimmedSamples: FloatArray,
  val trimmedPcm16LeBytes: ByteArray,
  val remainderWindowSamples: FloatArray,
  val preRollFallbackSamples: FloatArray = FloatArray(0),
)

/**
 * Pass 2: Implements Mode A (parallel AudioRecord alongside SpeechRecognizer) and
 * Mode B (Android >= 13 AudioRecord piped to SpeechRecognizer via RecognizerIntent.EXTRA_AUDIO_SOURCE, default),
 * records with AudioSource.VOICE_RECOGNITION, tags capture mode / audio source / input device (getRoutedDevice type + name),
 * runs Silero VAD in 512-sample chunks to detect end of turn and measure speech duration,
 * trims leading silence (keeping a ~200 ms pre-roll ring buffer before VAD speech start, or a 1.5 s ring buffer
 * on recognizer-triggered start) and trailing end-of-turn silence (keeping a 200 ms post-roll and dropping the
 * remaining ~500 ms of the 700 ms below-threshold run) while preserving mid-turn pauses for emotion windows and saved WAV clips,
 * scores 4 s windows as they arrive on long turns, closes the pipe write end when Silero ends the turn,
 * computes clip RMS, peak, and SILENT CAPTURE detection, and saves the last 10 clips as 16 kHz mono WAV.
 */
object MicSharingRecorder {
  const val SAMPLE_RATE_HZ = 16000
  const val VAD_CHUNK_SAMPLES = 512
  const val PRE_ROLL_MS = 200
  const val PRE_ROLL_SAMPLES = (SAMPLE_RATE_HZ * PRE_ROLL_MS) / 1000 // 3,200 samples (~200 ms @ 16 kHz)
  const val RECOGNIZER_RING_BUFFER_MS = 1500
  const val RECOGNIZER_RING_BUFFER_SAMPLES =
    (SAMPLE_RATE_HZ * RECOGNIZER_RING_BUFFER_MS) / 1000 // 24,000 samples (1.5 s @ 16 kHz)
  const val RECOGNIZER_SPEECH_PROB_THRESHOLD = 0.35f
  const val POST_ROLL_MS = 200
  const val POST_ROLL_SAMPLES = (SAMPLE_RATE_HZ * POST_ROLL_MS) / 1000 // 3,200 samples (~200 ms @ 16 kHz)
  const val WINDOW_SAMPLES_4S = 4 * SAMPLE_RATE_HZ // 64,000 samples (4.0 s @ 16 kHz)
  const val SILENT_CAPTURE_RMS_THRESHOLD = 1e-4f
  const val MAX_SAVED_WAV_CLIPS = 10
  private const val TAG = "MicSharingRecorder"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _modeBRuntimeNote = MutableStateFlow<String?>(null)
  val modeBRuntimeNote: StateFlow<String?> = _modeBRuntimeNote.asStateFlow()

  private var activeAudioRecord: AudioRecord? = null
  private val isCapturing = AtomicBoolean(false)
  private val pipeWritingAllowed = AtomicBoolean(false)
  private var captureJob: Job? = null
  private var currentMode: String = "B"
  private var currentAudioSource: String = "VOICE_RECOGNITION"
  private var currentInputDevice: String = "unknown"
  private var currentPcmBuffer: ByteArrayOutputStream? = null
  private var currentTrimmedAudio: TrimmedTurnAudio? = null
  private var currentUsedEdgeTrimmer: Boolean = false
  private var currentCaptureError: String? = null
  private var currentPipeBrokeEarly: Boolean = false
  private var currentAudioRecordStarted: Boolean = false
  private val currentPipeBytesWritten = AtomicLong(0L)
  private val currentPipeClosedAtMs = AtomicLong(0L)
  private var currentVadSpeechDetected: Boolean = false
  private var currentVadEndedTurn: Boolean = false
  private var currentVadMaxProb: Float = 0f
  private var currentSpeechDurationSec: Float = 0f
  private var activeVadManager: SileroVadManager? = null
  private val pipeAgcProcessor = PipeAutoGainProcessor()
  private var currentRmsAfterGain: Float = 0f
  private var currentAppliedGainDb: Float = 0f
  private var currentPeakAfterGain: Float = 0f
  @Volatile private var currentFinalScoreStartedAtVadEnd: Boolean = false
  @Volatile private var preStartedTurnScoreDeferred: Deferred<Result<EmotionPrediction>>? = null

  private val inFlightWindowTasks =
    Collections.synchronizedList(mutableListOf<Deferred<Result<EmotionPrediction>>>())
  private var currentRemainderSamples: FloatArray = FloatArray(0)

  private val pipeLock = Any()
  private var activePipeReadPfd: ParcelFileDescriptor? = null
  private var activePipeWriteStream: OutputStream? = null

  /**
   * Formats AudioRecord.getRoutedDevice() as "<TYPE_NAME> (<productName>)" for per-turn tagging.
   */
  fun formatRoutedDevice(device: AudioDeviceInfo?): String {
    if (device == null) return "unknown"
    val typeLabel = audioDeviceTypeToString(device.type)
    val productName = runCatching { device.productName?.toString()?.trim().orEmpty() }.getOrDefault("")
    return if (productName.isNotBlank() && !productName.equals(typeLabel, ignoreCase = true)) {
      "$typeLabel ($productName)"
    } else {
      typeLabel
    }
  }

  fun audioDeviceTypeToString(type: Int): String {
    return when (type) {
      AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
      AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
      AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
      AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
      AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
      AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
      AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
      AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
      AudioDeviceInfo.TYPE_IP -> "IP"
      AudioDeviceInfo.TYPE_BUS -> "BUS"
      AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "REMOTE_SUBMIX"
      AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
      AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
      AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
      AudioDeviceInfo.TYPE_HDMI -> "HDMI"
      AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
      AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
      else -> "TYPE_$type"
    }
  }

  /**
   * Checks whether Android API level is >= 33 (Android 13 / TIRAMISU) for RecognizerIntent.EXTRA_AUDIO_SOURCE.
   */
  fun isModeBApiSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  }

  fun getModeBSupportDescription(context: Context): String {
    if (!isModeBApiSupported()) {
      return "Unsupported on this device (requires Android 13 / API 33+; current API ${Build.VERSION.SDK_INT})."
    }
    val pkg = resolveRecognizerPackage(context)
    val runtimeNote = _modeBRuntimeNote.value
    return if (!runtimeNote.isNullOrBlank()) {
      "Android 13+ EXTRA_AUDIO_SOURCE ($pkg): $runtimeNote"
    } else {
      "Supported on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · Recognizer: $pkg"
    }
  }

  fun noteModeBOutcome(
    recognizerOk: Boolean,
    errorCode: Int?,
    pipeBrokeEarly: Boolean,
    usedPartialFallback: Boolean = false,
  ) {
    if (recognizerOk) {
      _modeBRuntimeNote.value =
        if (usedPartialFallback) {
          "EXTRA_AUDIO_SOURCE working (Silero VAD + used partial fallback)"
        } else {
          "Verified working with EXTRA_AUDIO_SOURCE"
        }
    } else if (pipeBrokeEarly || errorCode != null) {
      _modeBRuntimeNote.value =
        "Recognizer error on EXTRA_AUDIO_SOURCE (code ${errorCode ?: "pipe closed"})"
    }
  }

  /**
   * Resolves the active SpeechRecognizer service package name for logging.
   */
  fun resolveRecognizerPackage(context: Context): String {
    try {
      val setting =
        Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
      if (!setting.isNullOrBlank()) {
        val pkg = setting.substringBefore('/').trim()
        if (pkg.isNotBlank()) return pkg
      }
    } catch (_: Throwable) {}

    try {
      val resolveInfo =
        context.packageManager.resolveService(
          Intent(RecognitionService.SERVICE_INTERFACE),
          0,
        )
      val pkg = resolveInfo?.serviceInfo?.packageName
      if (!pkg.isNullOrBlank()) return pkg
    } catch (_: Throwable) {}

    return "system_default"
  }

  /**
   * Notifies the active Silero VAD tracker that SpeechRecognizer has emitted partial/segment speech,
   * ensuring sub-250ms utterances still trigger end-of-turn after 700 ms below 0.35.
   */
  fun notifyRecognizerHeardSpeech() {
    currentVadSpeechDetected = true
    activeVadManager?.notifyExternalSpeechConfirmed()
  }

  /**
   * Closes ONLY the write end of the EXTRA_AUDIO_SOURCE pipe so SpeechRecognizer receives EOF
   * and flushes its final recognition result. Records [currentPipeClosedAtMs] on first close.
   */
  fun closePipeWriteEndOnly(): Long {
    pipeWritingAllowed.set(false)
    val now = System.currentTimeMillis()
    currentPipeClosedAtMs.compareAndSet(0L, now)
    synchronized(pipeLock) {
      val writeStream = activePipeWriteStream
      activePipeWriteStream = null
      runCatching { writeStream?.flush() }
      runCatching { writeStream?.close() }
    }
    return currentPipeClosedAtMs.get()
  }

  /**
   * Starts AudioRecord at 16 kHz mono PCM_16BIT using AudioSource.VOICE_RECOGNITION.
   * - SpeechRecognizer keeps getting the full, untrimmed pipe audio immediately on every read.
   * - Runs Silero VAD on the app's own 16 kHz audio in 512-sample chunks, keeping state between chunks.
   * - Uses [EdgeSilenceTrimmer] to keep a ~200 ms pre-roll ring buffer before speech starts, preserve
   *   mid-turn pauses, and drop the trailing 700 ms below-threshold end-of-turn silence before scoring
   *   the final remainder window and saving the WAV clip.
   * - Scores 4-second (64,000-sample) windows in the background as they arrive during long turns.
   * - When Silero ends the turn: stops writing, closes the pipe's write end, stops AudioRecord capture,
   *   and invokes [onVadEndOfTurn] with the pipe-close timestamp.
   */
  @SuppressLint("MissingPermission")
  fun startCaptureForLiveTurn(
    context: Context,
    requestedMode: String,
    recognizerIntent: Intent,
    vadManager: SileroVadManager? = null,
    vadConfig: SileroVadConfig = SileroVadConfig(),
    emotionModelManager: EmotionModelManager? = null,
    onVadEndOfTurn: ((pipeClosedAtMs: Long) -> Unit)? = null,
  ): String {
    stopCaptureImmediate()

    val effectiveMode =
      if (requestedMode == "B") {
        if (isModeBApiSupported()) {
          "B"
        } else {
          _modeBRuntimeNote.value =
            "Mode B is unsupported on API ${Build.VERSION.SDK_INT}; fell back to Mode A."
          "A"
        }
      } else {
        "A"
      }

    currentMode = effectiveMode
    currentAudioSource = "VOICE_RECOGNITION"
    currentInputDevice = "unknown"
    currentCaptureError = null
    currentPipeBrokeEarly = false
    currentAudioRecordStarted = false
    currentPipeBytesWritten.set(0L)
    currentPipeClosedAtMs.set(0L)
    currentVadSpeechDetected = false
    currentVadEndedTurn = false
    currentVadMaxProb = 0f
    currentSpeechDurationSec = 0f
    pipeAgcProcessor.reset()
    currentRmsAfterGain = 0f
    currentAppliedGainDb = 0f
    currentPeakAfterGain = 0f
    currentFinalScoreStartedAtVadEnd = false
    preStartedTurnScoreDeferred = null
    activeVadManager = vadManager
    vadManager?.resetTurnState(vadConfig)
    synchronized(inFlightWindowTasks) { inFlightWindowTasks.clear() }
    currentRemainderSamples = FloatArray(0)
    currentTrimmedAudio = null
    currentUsedEdgeTrimmer = false

    // Fix 8: In Mode A ("Google capture"), don't start the app's AudioRecord at all,
    // so SpeechRecognizer gets the microphone itself directly.
    if (effectiveMode == "A") {
      currentAudioSource = "GOOGLE_RECOGNIZER"
      currentInputDevice = "SpeechRecognizer (direct mic)"
      currentAudioRecordStarted = false
      currentPcmBuffer = null
      Log.i(
        TAG,
        "Mode A (Google capture): skipping app AudioRecord so SpeechRecognizer gets the mic directly (acoustic=null, words_vs_scores=no_scores)"
      )
      return "A"
    }

    val pcmOut = ByteArrayOutputStream(SAMPLE_RATE_HZ * 2 * 10)
    currentPcmBuffer = pcmOut

    try {
      val minBufSize =
        AudioRecord.getMinBufferSize(
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (minBufSize <= 0) {
        currentCaptureError = "AudioRecord.getMinBufferSize returned $minBufSize"
        return effectiveMode
      }
      val bufferSizeInBytes = (minBufSize * 2).coerceAtLeast(SAMPLE_RATE_HZ * 2)

      // Requirement 6: Record with AudioSource.VOICE_RECOGNITION (peaks were hitting 1.00 with MIC)
      var recorder =
        createInitializedAudioRecord(
          MediaRecorder.AudioSource.VOICE_RECOGNITION,
          bufferSizeInBytes,
        )
      if (recorder != null) {
        currentAudioSource = "VOICE_RECOGNITION"
      } else {
        recorder =
          createInitializedAudioRecord(
            MediaRecorder.AudioSource.MIC,
            bufferSizeInBytes,
          )
        if (recorder != null) {
          currentAudioSource = "MIC"
        }
      }
      if (recorder == null) {
        currentCaptureError = "AudioRecord failed to initialize (STATE_UNINITIALIZED)"
        return effectiveMode
      }

      if (effectiveMode == "B" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
          val pipe = ParcelFileDescriptor.createPipe()
          val readPfd = pipe[0]
          val writePfd = pipe[1]
          synchronized(pipeLock) {
            activePipeReadPfd = readPfd
            activePipeWriteStream = ParcelFileDescriptor.AutoCloseOutputStream(writePfd)
          }
          pipeWritingAllowed.set(true)

          recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readPfd)
          recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
          recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
            AudioFormat.ENCODING_PCM_16BIT,
          )
          recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
            SAMPLE_RATE_HZ,
          )
        } catch (e: Throwable) {
          pipeWritingAllowed.set(false)
          currentCaptureError = "Mode B pipe setup failed: ${e.message}"
          _modeBRuntimeNote.value = "Pipe creation failed: ${e.message}"
        }
      } else {
        pipeWritingAllowed.set(false)
      }

      recorder.startRecording()
      if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        currentAudioRecordStarted = false
        currentCaptureError =
          "AudioRecord.startRecording() did not enter RECORDSTATE_RECORDING (state=${recorder.recordingState})"
        runCatching { recorder.release() }
        closePipeQuietly()
        return effectiveMode
      }

      currentAudioRecordStarted = true
      currentInputDevice = runCatching { formatRoutedDevice(recorder.routedDevice) }.getOrDefault("unknown")
      activeAudioRecord = recorder
      isCapturing.set(true)

      val useEdgeTrimmer = vadManager != null && vadManager.isModelReady()
      currentUsedEdgeTrimmer = useEdgeTrimmer

      captureJob =
        scope.launch(Dispatchers.IO) {
          val shortBuf = ShortArray(VAD_CHUNK_SAMPLES)
          val byteChunk = ByteArray(VAD_CHUNK_SAMPLES * 2)
          val vadChunk512 = FloatArray(VAD_CHUNK_SAMPLES)
          var vadStagingIndex = 0
          var vadTriggeredEnd = false

          // Streaming 4-second (64,000-sample) window staging buffer for fallback when VAD is not active
          val fallbackWindowBuffer4s = FloatArray(WINDOW_SAMPLES_4S)
          var fallbackWindowSampleIndex = 0

          val edgeTrimmer =
            if (useEdgeTrimmer) {
              EdgeSilenceTrimmer(
                preRollSamplesCapacity = PRE_ROLL_SAMPLES,
                windowSamples4s = WINDOW_SAMPLES_4S,
                onFullWindowReady = { windowCopy ->
                  if (emotionModelManager != null) {
                    val deferred =
                      scope.async(Dispatchers.Default) {
                        emotionModelManager.runInference(windowCopy)
                      }
                    inFlightWindowTasks.add(deferred)
                  }
                },
              )
            } else {
              null
            }

          try {
            while (isCapturing.get()) {
              val readCount = recorder.read(shortBuf, 0, shortBuf.size)
              if (readCount > 0) {
                if (currentInputDevice == "unknown") {
                  val routed = runCatching { formatRoutedDevice(recorder.routedDevice) }.getOrDefault("unknown")
                  if (routed != "unknown") {
                    currentInputDevice = routed
                  }
                }

                val bytesLen = readCount * 2
                ByteBuffer.wrap(byteChunk, 0, bytesLen)
                  .order(ByteOrder.LITTLE_ENDIAN)
                  .asShortBuffer()
                  .put(shortBuf, 0, readCount)

                // Always keep raw fallback bytes in pcmOut in case VAD is not active
                synchronized(pcmOut) {
                  pcmOut.write(byteChunk, 0, bytesLen)
                }

                // Fix 7: Apply automatic gain (-20 dBFS RMS target, max +20 dB, peak limiter)
                // ONLY to the audio written to the SpeechRecognizer pipe.
                // VAD and the emotion model keep the raw audio (shortBuf / byteChunk).
                if (pipeWritingAllowed.get() && !currentPipeBrokeEarly) {
                  val gainedPipeBytes = pipeAgcProcessor.processChunkToPcm16Bytes(shortBuf, readCount)
                  synchronized(pipeLock) {
                    val outStream = activePipeWriteStream
                    if (outStream != null && pipeWritingAllowed.get()) {
                      try {
                        outStream.write(gainedPipeBytes, 0, gainedPipeBytes.size)
                        outStream.flush()
                        currentPipeBytesWritten.addAndGet(gainedPipeBytes.size.toLong())
                      } catch (_: Throwable) {
                        currentPipeBrokeEarly = true
                        pipeWritingAllowed.set(false)
                      }
                    }
                  }
                }

                if (!useEdgeTrimmer && emotionModelManager != null) {
                  var winOffset = 0
                  while (winOffset < readCount) {
                    val winNeeded = WINDOW_SAMPLES_4S - fallbackWindowSampleIndex
                    val winToCopy = minOf(winNeeded, readCount - winOffset)
                    for (i in 0 until winToCopy) {
                      fallbackWindowBuffer4s[fallbackWindowSampleIndex + i] =
                        (shortBuf[winOffset + i].toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
                    }
                    fallbackWindowSampleIndex += winToCopy
                    winOffset += winToCopy

                    if (fallbackWindowSampleIndex == WINDOW_SAMPLES_4S) {
                      val windowCopy = fallbackWindowBuffer4s.copyOf()
                      fallbackWindowSampleIndex = 0
                      val deferred =
                        scope.async(Dispatchers.Default) {
                          emotionModelManager.runInference(windowCopy)
                        }
                      inFlightWindowTasks.add(deferred)
                    }
                  }
                }

                // Feed exact 512-sample chunks into Silero VAD and EdgeSilenceTrimmer (raw audio)
                if (vadManager != null && !vadTriggeredEnd) {
                  var offset = 0
                  while (offset < readCount) {
                    val needed = VAD_CHUNK_SAMPLES - vadStagingIndex
                    val toCopy = minOf(needed, readCount - offset)
                    for (i in 0 until toCopy) {
                      vadChunk512[vadStagingIndex + i] =
                        (shortBuf[offset + i].toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
                    }
                    vadStagingIndex += toCopy
                    offset += toCopy

                    if (vadStagingIndex == VAD_CHUNK_SAMPLES) {
                      vadStagingIndex = 0
                      val snap = vadManager.processChunk512(vadChunk512)
                      if (snap.maxProb > currentVadMaxProb) {
                        currentVadMaxProb = snap.maxProb
                      }
                      currentSpeechDurationSec = snap.speechDurationSec
                      if (snap.speechStarted) {
                        currentVadSpeechDetected = true
                      }
                      edgeTrimmer?.onChunk512(vadChunk512, snap)

                      if (snap.turnEnded && !vadTriggeredEnd) {
                        vadTriggeredEnd = true
                        currentVadEndedTurn = true
                        // When Silero ends the turn: stop writing, close the pipe's write end,
                        // and stop AudioRecord so the clip ends here, then notify LiveSessionManager.
                        val closedAt = closePipeWriteEndOnly()
                        isCapturing.set(false)
                        runCatching { recorder.stop() }
                        onVadEndOfTurn?.invoke(closedAt)
                        break
                      }
                    }
                  }
                }
              } else if (readCount < 0) {
                currentCaptureError = "AudioRecord.read error code $readCount"
                break
              }
            }
          } catch (e: Throwable) {
            if (isCapturing.get()) {
              currentCaptureError = "AudioRecord read loop error: ${e.message}"
            }
          } finally {
            currentRmsAfterGain = pipeAgcProcessor.rmsAfterGain
            currentAppliedGainDb = pipeAgcProcessor.appliedGainDb
            currentPeakAfterGain = pipeAgcProcessor.peakAfterGain

            if (edgeTrimmer != null) {
              if (!edgeTrimmer.speechHasStarted) {
                val snap = vadManager?.getCurrentSnapshot()
                if (snap?.speechStarted == true && snap.speechStartedByExternalConfirm) {
                  edgeTrimmer.onRecognizerTriggeredStart()
                }
              }
              val leftover =
                if (vadStagingIndex > 0) vadChunk512.copyOfRange(0, vadStagingIndex)
                else FloatArray(0)
              val trimmed = edgeTrimmer.finishTurn(leftover)
              currentTrimmedAudio = trimmed
              currentRemainderSamples = trimmed.remainderWindowSamples
            } else if (fallbackWindowSampleIndex > 0) {
              currentRemainderSamples = fallbackWindowBuffer4s.copyOfRange(0, fallbackWindowSampleIndex)
            }

            // Fix 3: Start scoring the final emotion window right at VAD end-of-turn (inside capture completion),
            // NOT after the transcript resolves, so emotion inference and transcript resolution overlap!
            if (emotionModelManager != null) {
              val trimmedForScore = currentTrimmedAudio
              val rawForScore =
                when {
                  trimmedForScore != null && trimmedForScore.trimmedSamples.isNotEmpty() ->
                    trimmedForScore.trimmedSamples
                  else -> {
                    val rawBytes = synchronized(pcmOut) { pcmOut.toByteArray() }
                    pcm16LeToFloatArray(rawBytes)
                  }
                }
              val remainderForScore = currentRemainderSamples
              if (rawForScore.isNotEmpty()) {
                val windowTasksSnap =
                  synchronized(inFlightWindowTasks) { inFlightWindowTasks.toList() }
                currentFinalScoreStartedAtVadEnd = true
                Log.i(
                  TAG,
                  "Starting final emotion window scoring at VAD end-of-turn (overlapping with transcript wait): totalSamples=${rawForScore.size}, remainderSamples=${remainderForScore.size}, preFlightWindows=${windowTasksSnap.size}"
                )
                preStartedTurnScoreDeferred =
                  scope.async(Dispatchers.Default) {
                    val preScored = mutableListOf<EmotionPrediction>()
                    for (task in windowTasksSnap) {
                      runCatching { task.await().getOrNull() }
                        .getOrNull()
                        ?.let { preScored.add(it) }
                    }
                    emotionModelManager.scoreTurnWithWindows(
                      rawSamples16kMono = rawForScore,
                      preScoredWindows = preScored,
                      remainderSamples = remainderForScore,
                    )
                  }
              }
            }
          }
        }
    } catch (e: Throwable) {
      currentCaptureError = "AudioRecord start error: ${e.message ?: e.javaClass.simpleName}"
      closePipeQuietly()
    }
    return effectiveMode
  }

  @SuppressLint("MissingPermission")
  private fun createInitializedAudioRecord(
    audioSource: Int,
    bufferSizeInBytes: Int,
  ): AudioRecord? {
    return try {
      val rec =
        AudioRecord(
          audioSource,
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSizeInBytes,
        )
      if (rec.state == AudioRecord.STATE_INITIALIZED) {
        rec
      } else {
        runCatching { rec.release() }
        null
      }
    } catch (_: Throwable) {
      null
    }
  }

  /**
   * Stops the active AudioRecord session and returns the captured 16 kHz mono float32 clip,
   * diagnostics (audioRecordStarted, pipeBytesWritten, Silero VAD stats, speechDurationSec,
   * audioSource, inputDevice, preScoredWindows), and RMS/peak/SILENT CAPTURE metrics.
   * When Silero VAD trimming is active and speech started, both [MicCaptureResult.samples16kMono]
   * and [MicCaptureResult.pcm16Bytes] (for saved WAV) start at speech start minus the ~200 ms pre-roll
   * and omit the trailing 700 ms end-of-turn silence, while keeping mid-turn pauses intact.
   */
  suspend fun finishCapture(
    context: Context,
    recognizerHeardSpeech: Boolean,
  ): MicCaptureResult =
    withContext(Dispatchers.IO) {
      isCapturing.set(false)
      pipeWritingAllowed.set(false)
      val job = captureJob
      captureJob = null
      val recorder = activeAudioRecord
      activeAudioRecord = null

      if (currentInputDevice == "unknown" && recorder != null) {
        currentInputDevice = runCatching { formatRoutedDevice(recorder.routedDevice) }.getOrDefault("unknown")
      }

      runCatching { recorder?.stop() }
      runCatching { job?.join() }
      runCatching { recorder?.release() }
      closePipeQuietly()

      val vadSnap = activeVadManager?.getCurrentSnapshot()
      activeVadManager = null

      val pkg = resolveRecognizerPackage(context)

      // Fix 8: Mode A ("Google capture") never starts AudioRecord; return clean Mode A result with no voice scores.
      if (currentMode == "A" && !currentAudioRecordStarted) {
        return@withContext MicCaptureResult(
          mode = "A",
          samples16kMono = FloatArray(0),
          pcm16Bytes = ByteArray(0),
          clipLengthSec = 0f,
          rms = 0f,
          peak = 0f,
          silentCapture = false,
          recognizerPackage = pkg,
          captureError = null,
          pipeBrokeEarly = false,
          audioRecordStarted = false,
          pipeBytesWritten = 0L,
          vadSpeechDetected = false,
          vadEndedTurn = false,
          vadMaxProb = 0f,
          speechDurationSec = 0f,
          audioSource = currentAudioSource,
          inputDevice = currentInputDevice,
          pipeClosedAtMs = 0L,
          preScoredWindows = emptyList(),
          remainderWindowSamples = FloatArray(0),
          rmsAfterGain = 0f,
          appliedGainDb = 0f,
          peakAfterGain = 0f,
          finalScoreStartedAtVadEnd = false,
          preStartedScoreDeferred = null,
        )
      }

      val preStartedDeferred = preStartedTurnScoreDeferred
      preStartedTurnScoreDeferred = null
      val scoreStartedAtVadEnd = currentFinalScoreStartedAtVadEnd
      currentFinalScoreStartedAtVadEnd = false

      // Collect any 4-second windows that were scored in flight during the turn.
      // If preStartedDeferred is already running and awaiting them inside the emotion barrier,
      // only harvest already-completed windows here so finishCapture() returns immediately!
      val windowTasksSnapshot =
        synchronized(inFlightWindowTasks) {
          val copy = inFlightWindowTasks.toList()
          inFlightWindowTasks.clear()
          copy
        }
      val scoredWindows = mutableListOf<EmotionPrediction>()
      for (task in windowTasksSnapshot) {
        if (preStartedDeferred == null || task.isCompleted) {
          runCatching {
            task.await().onSuccess { pred -> scoredWindows.add(pred) }
          }
        }
      }
      val remainderSamples = currentRemainderSamples
      currentRemainderSamples = FloatArray(0)

      val trimmed = currentTrimmedAudio
      currentTrimmedAudio = null
      val usedTrimmer = currentUsedEdgeTrimmer
      currentUsedEdgeTrimmer = false

      val pcmOut = currentPcmBuffer
      currentPcmBuffer = null
      val rawFallbackBytes =
        if (pcmOut != null) {
          synchronized(pcmOut) { pcmOut.toByteArray() }
        } else {
          ByteArray(0)
        }

      val finalSamples: FloatArray
      val finalPcmBytes: ByteArray
      if (usedTrimmer && trimmed != null) {
        if (trimmed.trimmedSamples.isNotEmpty()) {
          finalSamples = trimmed.trimmedSamples
          finalPcmBytes = trimmed.trimmedPcm16LeBytes
        } else if (trimmed.preRollFallbackSamples.isNotEmpty() && recognizerHeardSpeech) {
          // Speech was heard by recognizer (or silent capture test) but VAD prob stayed < threshold
          finalSamples = trimmed.preRollFallbackSamples
          finalPcmBytes = floatArrayToPcm16LeBytes(trimmed.preRollFallbackSamples)
        } else {
          finalSamples = pcm16LeToFloatArray(rawFallbackBytes)
          finalPcmBytes = rawFallbackBytes
        }
      } else {
        finalSamples = pcm16LeToFloatArray(rawFallbackBytes)
        finalPcmBytes = rawFallbackBytes
      }

      val metrics =
        computeClipMetrics(
          samples = finalSamples,
          sampleRateHz = SAMPLE_RATE_HZ,
          recognizerHeardSpeech = recognizerHeardSpeech,
        )
      val speechSec = vadSnap?.speechDurationSec ?: currentSpeechDurationSec
      val rmsAfter = currentRmsAfterGain
      val gainDb = currentAppliedGainDb
      val peakAfter = currentPeakAfterGain

      // Fix 7: Log RMS before and after gain for each turn
      Log.i(
        TAG,
        "Turn pipe AGC metrics: rmsBefore=${"%.4f".format(Locale.US, metrics.rms)} (${"%.1f".format(Locale.US, PipeAutoGainProcessor.linearToDbFs(metrics.rms))} dBFS) -> rmsAfter=${"%.4f".format(Locale.US, rmsAfter)} (${"%.1f".format(Locale.US, PipeAutoGainProcessor.linearToDbFs(rmsAfter))} dBFS), gain=+${"%.1f".format(Locale.US, gainDb)} dB, peakBefore=${"%.4f".format(Locale.US, metrics.peak)} -> peakAfter=${"%.4f".format(Locale.US, peakAfter)}"
      )

      MicCaptureResult(
        mode = currentMode,
        samples16kMono = finalSamples,
        pcm16Bytes = finalPcmBytes,
        clipLengthSec = metrics.clipLengthSec,
        rms = metrics.rms,
        peak = metrics.peak,
        silentCapture = metrics.silentCapture,
        recognizerPackage = pkg,
        captureError = currentCaptureError,
        pipeBrokeEarly = currentPipeBrokeEarly,
        audioRecordStarted = currentAudioRecordStarted,
        pipeBytesWritten = currentPipeBytesWritten.get(),
        vadSpeechDetected = vadSnap?.speechStarted ?: currentVadSpeechDetected,
        vadEndedTurn = vadSnap?.turnEnded ?: currentVadEndedTurn,
        vadMaxProb = maxOf(currentVadMaxProb, vadSnap?.maxProb ?: 0f),
        speechDurationSec = speechSec,
        audioSource = currentAudioSource,
        inputDevice = currentInputDevice,
        pipeClosedAtMs = currentPipeClosedAtMs.get(),
        preScoredWindows = scoredWindows,
        remainderWindowSamples = remainderSamples,
        rmsAfterGain = rmsAfter,
        appliedGainDb = gainDb,
        peakAfterGain = peakAfter,
        finalScoreStartedAtVadEnd = scoreStartedAtVadEnd,
        preStartedScoreDeferred = preStartedDeferred,
      )
    }

  fun stopCaptureImmediate() {
    isCapturing.set(false)
    pipeWritingAllowed.set(false)
    captureJob?.cancel()
    captureJob = null
    activeVadManager = null
    preStartedTurnScoreDeferred?.cancel()
    preStartedTurnScoreDeferred = null
    currentFinalScoreStartedAtVadEnd = false
    synchronized(inFlightWindowTasks) {
      inFlightWindowTasks.forEach { it.cancel() }
      inFlightWindowTasks.clear()
    }
    currentRemainderSamples = FloatArray(0)
    currentTrimmedAudio = null
    currentUsedEdgeTrimmer = false
    val recorder = activeAudioRecord
    activeAudioRecord = null
    if (recorder != null) {
      scope.launch {
        runCatching { recorder.stop() }
        runCatching { recorder.release() }
      }
    }
    closePipeQuietly()
  }

  private fun closePipeQuietly() {
    pipeWritingAllowed.set(false)
    synchronized(pipeLock) {
      val writeStream = activePipeWriteStream
      activePipeWriteStream = null
      val readPfd = activePipeReadPfd
      activePipeReadPfd = null
      runCatching { writeStream?.close() }
      runCatching { readPfd?.close() }
    }
  }

  /**
   * Converts float32 samples in [-1.0, 1.0] to little-endian 16-bit PCM bytes.
   */
  fun floatArrayToPcm16LeBytes(samples: FloatArray): ByteArray {
    if (samples.isEmpty()) return ByteArray(0)
    val out = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (s in samples) {
      val clamped = s.coerceIn(-1.0f, 1.0f)
      val pcmShort =
        kotlin.math.round(clamped * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
      out.putShort(pcmShort)
    }
    return out.array()
  }

  /**
   * Converts little-endian 16-bit PCM bytes to float32 in [-1.0, 1.0].
   */
  fun pcm16LeToFloatArray(pcmBytes: ByteArray): FloatArray {
    val sampleCount = pcmBytes.size / 2
    if (sampleCount <= 0) return FloatArray(0)
    val shortBuf =
      ByteBuffer.wrap(pcmBytes, 0, sampleCount * 2)
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()
    val floats = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      floats[i] = (shortBuf.get(i).toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
    }
    return floats
  }

  /**
   * Computes clip length (s), RMS, peak, and flags SILENT CAPTURE if RMS is near zero
   * while the recognizer heard speech.
   */
  fun computeClipMetrics(
    samples: FloatArray,
    sampleRateHz: Int = SAMPLE_RATE_HZ,
    recognizerHeardSpeech: Boolean,
  ): ClipMetrics {
    if (samples.isEmpty()) {
      return ClipMetrics(
        samplesCount = 0,
        clipLengthSec = 0f,
        rms = 0f,
        peak = 0f,
        silentCapture = recognizerHeardSpeech,
      )
    }
    var sumSq = 0.0
    var maxPeak = 0f
    for (s in samples) {
      val a = abs(s)
      if (a > maxPeak) maxPeak = a
      sumSq += (s * s).toDouble()
    }
    val rms = sqrt(sumSq / samples.size.toDouble()).toFloat()
    val lengthSec = samples.size.toFloat() / sampleRateHz.toFloat()
    val isSilent = recognizerHeardSpeech && rms < SILENT_CAPTURE_RMS_THRESHOLD

    return ClipMetrics(
      samplesCount = samples.size,
      clipLengthSec = lengthSec,
      rms = rms,
      peak = maxPeak,
      silentCapture = isSilent,
    )
  }

  /**
   * Writes a 16 kHz mono 16-bit PCM WAV file into app-private storage and prunes old clips
   * so only the newest [MAX_SAVED_WAV_CLIPS] (10) WAV files are retained.
   */
  fun saveWavClipAndPrune(
    context: Context,
    requestId: String,
    pcm16LeBytes: ByteArray,
    timestampMs: Long = System.currentTimeMillis(),
  ): File? {
    if (pcm16LeBytes.isEmpty()) return null
    return try {
      val clipsDir = File(context.filesDir, "emotion_clips")
      if (!clipsDir.exists()) clipsDir.mkdirs()

      val safeReq = requestId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
      val outFile = File(clipsDir, "clip_${timestampMs}_${safeReq}.wav")
      val wavBytes = encodePcm16MonoToWavBytes(pcm16LeBytes, SAMPLE_RATE_HZ)
      FileOutputStream(outFile).use { fos ->
        fos.write(wavBytes)
        fos.flush()
      }

      // Keep only the last 10 WAV files
      val allWavs =
        clipsDir
          .listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }
          ?.sortedByDescending { it.lastModified() }
          .orEmpty()
      if (allWavs.size > MAX_SAVED_WAV_CLIPS) {
        allWavs.drop(MAX_SAVED_WAV_CLIPS).forEach { oldFile ->
          runCatching { oldFile.delete() }
        }
      }
      outFile
    } catch (_: Throwable) {
      null
    }
  }

  /**
   * Builds a standard 44-byte RIFF WAVE header + PCM16 little-endian mono payload.
   */
  fun encodePcm16MonoToWavBytes(
    pcm16LeBytes: ByteArray,
    sampleRateHz: Int = SAMPLE_RATE_HZ,
  ): ByteArray {
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRateHz * channels * (bitsPerSample / 8)
    val blockAlign = channels * (bitsPerSample / 8)
    val dataSize = pcm16LeBytes.size
    val chunkSize = 36 + dataSize

    val out = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    // "RIFF"
    out.put('R'.code.toByte())
    out.put('I'.code.toByte())
    out.put('F'.code.toByte())
    out.put('F'.code.toByte())
    out.putInt(chunkSize)
    // "WAVE"
    out.put('W'.code.toByte())
    out.put('A'.code.toByte())
    out.put('V'.code.toByte())
    out.put('E'.code.toByte())
    // "fmt " subchunk
    out.put('f'.code.toByte())
    out.put('m'.code.toByte())
    out.put('t'.code.toByte())
    out.put(' '.code.toByte())
    out.putInt(16) // Subchunk1Size for PCM
    out.putShort(1.toShort()) // AudioFormat = 1 (PCM)
    out.putShort(channels.toShort())
    out.putInt(sampleRateHz)
    out.putInt(byteRate)
    out.putShort(blockAlign.toShort())
    out.putShort(bitsPerSample.toShort())
    // "data" subchunk
    out.put('d'.code.toByte())
    out.put('a'.code.toByte())
    out.put('t'.code.toByte())
    out.put('a'.code.toByte())
    out.putInt(dataSize)
    out.put(pcm16LeBytes)
    return out.array()
  }
}
