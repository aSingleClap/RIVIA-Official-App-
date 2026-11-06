package com.example.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class SileroVadConfig(
  val startProbThreshold: Float = 0.50f,
  val startSpeechMs: Int = 250,
  val endProbThreshold: Float = 0.35f,
  val endSilenceMs: Int = 700,
  val sampleRateHz: Int = 16000,
  val chunkSamples: Int = 512,
) {
  val chunkDurationMs: Int
    get() = ((chunkSamples * 1000) / sampleRateHz).coerceAtLeast(1) // 32 ms at 16 kHz / 512 samples
}

data class SileroVadTurnSnapshot(
  val chunkProb: Float,
  val maxProb: Float,
  val speechStarted: Boolean,
  val turnEnded: Boolean,
  val consecutiveSpeechMs: Int,
  val consecutiveSilenceMs: Int,
  val chunksProcessed: Int,
  val totalSpeechMs: Int = 0,
  val speechStartedByExternalConfirm: Boolean = false,
) {
  val speechDurationSec: Float
    get() = totalSpeechMs.toFloat() / 1000.0f
}

/**
 * Pure state machine for Silero VAD end-of-turn detection on 512-sample (32 ms @ 16 kHz) chunks.
 * - Speech starts when prob >= [config.startProbThreshold] (0.5) for [config.startSpeechMs] (250 ms).
 * - Turn ends after [config.endSilenceMs] (700 ms) below [config.endProbThreshold] (0.35) once speech has started.
 * - Tracks total Silero speech duration ([totalSpeechMs]) across active speech chunks (excluding silence).
 */
class SileroVadTurnTracker(
  var config: SileroVadConfig = SileroVadConfig(),
) {
  var speechStarted: Boolean = false
    private set
  var speechStartedByExternalConfirm: Boolean = false
    private set
  var turnEnded: Boolean = false
    private set
  var consecutiveSpeechMs: Int = 0
    private set
  var consecutiveSilenceMs: Int = 0
    private set
  var totalSpeechMs: Int = 0
    private set
  var maxProb: Float = 0f
    private set
  var lastProb: Float = 0f
    private set
  var chunksProcessed: Int = 0
    private set

  val speechDurationSec: Float
    get() = totalSpeechMs.toFloat() / 1000.0f

  fun reset(newConfig: SileroVadConfig = config) {
    config = newConfig
    speechStarted = false
    speechStartedByExternalConfirm = false
    turnEnded = false
    consecutiveSpeechMs = 0
    consecutiveSilenceMs = 0
    totalSpeechMs = 0
    maxProb = 0f
    lastProb = 0f
    chunksProcessed = 0
  }

  /**
   * If SpeechRecognizer has already emitted non-empty partial/segment text, mark speech as started
   * so that even a sub-250ms utterance ("yes", "no") still triggers end-of-turn after 700 ms < 0.35.
   */
  fun notifyExternalSpeechConfirmed() {
    if (!speechStarted && !turnEnded) {
      speechStarted = true
      speechStartedByExternalConfirm = true
      if (consecutiveSpeechMs > 0) {
        totalSpeechMs += consecutiveSpeechMs
      } else if (totalSpeechMs == 0) {
        totalSpeechMs = config.chunkDurationMs
      }
      consecutiveSilenceMs = 0
    }
  }

  fun onChunkProbability(prob: Float): SileroVadTurnSnapshot {
    val chunkMs = config.chunkDurationMs
    chunksProcessed++
    lastProb = prob
    if (prob > maxProb) {
      maxProb = prob
    }

    if (!speechStarted) {
      if (prob >= config.startProbThreshold) {
        consecutiveSpeechMs += chunkMs
        if (consecutiveSpeechMs >= config.startSpeechMs) {
          speechStarted = true
          speechStartedByExternalConfirm = false
          totalSpeechMs += consecutiveSpeechMs
          consecutiveSilenceMs = 0
        }
      } else {
        consecutiveSpeechMs = 0
      }
    } else if (!turnEnded) {
      if (prob < config.endProbThreshold) {
        consecutiveSilenceMs += chunkMs
        if (consecutiveSilenceMs >= config.endSilenceMs) {
          turnEnded = true
        }
      } else {
        consecutiveSilenceMs = 0
        totalSpeechMs += chunkMs
      }
    }

    return SileroVadTurnSnapshot(
      chunkProb = prob,
      maxProb = maxProb,
      speechStarted = speechStarted,
      turnEnded = turnEnded,
      consecutiveSpeechMs = consecutiveSpeechMs,
      consecutiveSilenceMs = consecutiveSilenceMs,
      chunksProcessed = chunksProcessed,
      totalSpeechMs = totalSpeechMs,
      speechStartedByExternalConfirm = speechStartedByExternalConfirm,
    )
  }
}

sealed class SileroVadModelState {
  data object NotDownloaded : SileroVadModelState()

  data class Downloading(
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : SileroVadModelState()

  data class Ready(
    val onnxFile: File,
    val sizeBytes: Long,
  ) : SileroVadModelState()

  data class Error(val message: String) : SileroVadModelState()
}

/**
 * Manages downloading and running Silero VAD (silero_vad.onnx, MIT, ~2 MB) on the same ONNX Runtime
 * on 16 kHz mono audio in 512-sample chunks while keeping its recurrent state between chunks.
 */
class SileroVadManager(private val context: Context) {
  companion object {
    const val SAMPLE_RATE_HZ = 16000L
    const val CHUNK_SAMPLES = 512
    const val CONTEXT_SAMPLES_16K = 64

    val CANDIDATE_URLS =
      listOf(
        "https://raw.githubusercontent.com/snakers4/silero-vad/master/src/silero_vad/data/silero_vad.onnx",
        "https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx",
        "https://raw.githubusercontent.com/snakers4/silero-vad/v5.1.2/src/silero_vad/data/silero_vad.onnx",
        "https://raw.githubusercontent.com/snakers4/silero-vad/v4.0/files/silero_vad.onnx",
        "https://huggingface.co/onnx-community/silero-vad/resolve/main/onnx/model.onnx",
      )
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val vadDir = File(context.filesDir, "silero_vad")
  private val onnxFile = File(vadDir, "silero_vad.onnx")

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  private val _modelState = MutableStateFlow<SileroVadModelState>(SileroVadModelState.NotDownloaded)
  val modelState: StateFlow<SileroVadModelState> = _modelState.asStateFlow()

  private val downloadMutex = Mutex()
  private val stateLock = Any()
  private var downloadJob: Job? = null

  @Volatile private var cachedSession: OrtSession? = null
  private var useContext576: Boolean = true
  private var srIsScalar: Boolean = false

  // Silero VAD v5 recurrent state [2, 1, 128] + 64-sample context at 16 kHz
  private val stateV5 = FloatArray(2 * 1 * 128)
  private val context64 = FloatArray(CONTEXT_SAMPLES_16K)

  // Silero VAD v4 recurrent state h [2, 1, 64] and c [2, 1, 64]
  private val hV4 = FloatArray(2 * 1 * 64)
  private val cV4 = FloatArray(2 * 1 * 64)

  private val turnTracker = SileroVadTurnTracker()

  init {
    refreshModelState()
  }

  fun isModelReady(): Boolean = _modelState.value is SileroVadModelState.Ready

  fun refreshModelState() {
    try {
      // First check if bundled in assets
      if (!onnxFile.exists() || onnxFile.length() <= 1024L) {
        copyFromAssetsIfPresent()
      }
      if (onnxFile.exists() && onnxFile.length() > 1024L) {
        _modelState.value = SileroVadModelState.Ready(onnxFile, onnxFile.length())
        scope.launch { runCatching { ensureSessionLoaded() } }
      } else {
        _modelState.value = SileroVadModelState.NotDownloaded
      }
    } catch (e: Throwable) {
      _modelState.value = SileroVadModelState.Error("Silero VAD init error: ${e.message}")
    }
  }

  private fun copyFromAssetsIfPresent(): Boolean {
    return try {
      context.assets.open("silero_vad.onnx").use { input ->
        if (!vadDir.exists()) vadDir.mkdirs()
        FileOutputStream(onnxFile).use { output ->
          input.copyTo(output)
          output.flush()
        }
      }
      onnxFile.exists() && onnxFile.length() > 1024L
    } catch (_: Throwable) {
      false
    }
  }

  fun ensureDownloadedAsync(forceRedownload: Boolean = false) {
    if (!forceRedownload) {
      val cur = _modelState.value
      if (cur is SileroVadModelState.Ready || cur is SileroVadModelState.Downloading) {
        scope.launch { runCatching { ensureSessionLoaded() } }
        return
      }
    }
    if (downloadJob?.isActive == true) return
    downloadJob = scope.launch { downloadIfNeeded(forceRedownload) }
  }

  suspend fun downloadIfNeeded(forceRedownload: Boolean = false): Result<SileroVadModelState.Ready> =
    withContext(Dispatchers.IO) {
      downloadMutex.withLock {
        try {
          if (!forceRedownload && onnxFile.exists() && onnxFile.length() > 1024L) {
            val ready = SileroVadModelState.Ready(onnxFile, onnxFile.length())
            _modelState.value = ready
            ensureSessionLoaded()
            return@withLock Result.success(ready)
          }

          if (!vadDir.exists()) vadDir.mkdirs()

          synchronized(stateLock) {
            runCatching { cachedSession?.close() }
            cachedSession = null
          }

          val tmpFile = File(vadDir, "silero_vad.onnx.tmp")
          var lastErr: Throwable? = null
          var downloadedOk = false

          for (url in CANDIDATE_URLS) {
            try {
              downloadFile(url, tmpFile)
              if (tmpFile.exists() && tmpFile.length() > 100_000L) {
                downloadedOk = true
                break
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Throwable) {
              lastErr = e
            }
          }

          if (!downloadedOk) {
            val msg =
              "Silero VAD download failed: ${lastErr?.message ?: "network error"}"
            _modelState.value = SileroVadModelState.Error(msg)
            return@withLock Result.failure(Exception(msg))
          }

          if (onnxFile.exists()) onnxFile.delete()
          tmpFile.renameTo(onnxFile)

          val ready = SileroVadModelState.Ready(onnxFile, onnxFile.length())
          _modelState.value = ready
          ensureSessionLoaded()
          Result.success(ready)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          val msg = "Silero VAD setup failed: ${e.message ?: e.javaClass.simpleName}"
          _modelState.value = SileroVadModelState.Error(msg)
          Result.failure(e)
        }
      }
    }

  private fun downloadFile(url: String, dest: File) {
    val req = Request.Builder().url(url).build()
    httpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) {
        throw IllegalStateException("HTTP ${resp.code} from $url")
      }
      val body = resp.body ?: throw IllegalStateException("Empty body from $url")
      val total = body.contentLength()
      _modelState.value = SileroVadModelState.Downloading(0L, total)

      BufferedInputStream(body.byteStream(), 32 * 1024).use { input ->
        FileOutputStream(dest).use { output ->
          val buf = ByteArray(32 * 1024)
          var downloaded = 0L
          var read: Int
          while (input.read(buf).also { read = it } != -1) {
            output.write(buf, 0, read)
            downloaded += read
            _modelState.value = SileroVadModelState.Downloading(downloaded, total)
          }
          output.flush()
        }
      }
    }
  }

  private fun ensureSessionLoaded(): OrtSession? {
    val existing = cachedSession
    if (existing != null) return existing
    if (!onnxFile.exists() || onnxFile.length() <= 1024L) return null

    synchronized(stateLock) {
      cachedSession?.let { return it }
      return try {
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(1)
        val session = env.createSession(onnxFile.absolutePath, opts)
        val srInfo = session.inputInfo["sr"]?.info as? TensorInfo
        if (srInfo != null) {
          srIsScalar = srInfo.shape.isEmpty()
        }
        val inputInfo = session.inputInfo["input"]?.info as? TensorInfo
        if (inputInfo != null && inputInfo.shape.size >= 2) {
          val dim1 = inputInfo.shape[1]
          if (dim1 == 512L) {
            useContext576 = false
          } else if (dim1 == 576L) {
            useContext576 = true
          }
        }
        cachedSession = session
        session
      } catch (e: Throwable) {
        _modelState.value =
          SileroVadModelState.Error("Silero ONNX load error: ${e.message}")
        null
      }
    }
  }

  /**
   * Resets the recurrent state tensors and turn tracker at the start of a new Live turn.
   */
  fun resetTurnState(config: SileroVadConfig) {
    synchronized(stateLock) {
      stateV5.fill(0f)
      context64.fill(0f)
      hV4.fill(0f)
      cV4.fill(0f)
      turnTracker.reset(config)
    }
  }

  fun notifyExternalSpeechConfirmed() {
    synchronized(stateLock) {
      turnTracker.notifyExternalSpeechConfirmed()
    }
  }

  fun getCurrentSnapshot(): SileroVadTurnSnapshot {
    synchronized(stateLock) {
      return SileroVadTurnSnapshot(
        chunkProb = turnTracker.lastProb,
        maxProb = turnTracker.maxProb,
        speechStarted = turnTracker.speechStarted,
        turnEnded = turnTracker.turnEnded,
        consecutiveSpeechMs = turnTracker.consecutiveSpeechMs,
        consecutiveSilenceMs = turnTracker.consecutiveSilenceMs,
        chunksProcessed = turnTracker.chunksProcessed,
        totalSpeechMs = turnTracker.totalSpeechMs,
        speechStartedByExternalConfirm = turnTracker.speechStartedByExternalConfirm,
      )
    }
  }

  /**
   * Runs Silero VAD on a single 512-sample 16 kHz float32 chunk while preserving recurrent state
   * between chunks, and updates the start/end-of-turn tracker.
   */
  fun processChunk512(chunk512: FloatArray): SileroVadTurnSnapshot {
    require(chunk512.size == CHUNK_SAMPLES) {
      "Silero VAD requires 512-sample chunks at 16 kHz (got ${chunk512.size})"
    }
    synchronized(stateLock) {
      val session = ensureSessionLoaded()
      val prob =
        if (session != null) {
          runCatching { evalChunkInternal(session, chunk512) }.getOrDefault(0f)
        } else {
          0f
        }
      return turnTracker.onChunkProbability(prob)
    }
  }

  private fun evalChunkInternal(session: OrtSession, chunk512: FloatArray): Float {
    val env = OrtEnvironment.getEnvironment()
    val inputNames = session.inputNames

    return if (inputNames.contains("state")) {
      // Silero VAD v5: inputs = [input, state, sr], outputs = [output, stateN]
      try {
        evalV5(env, session, chunk512, withContext64 = useContext576, scalarSr = srIsScalar)
      } catch (_: Throwable) {
        // Fallback if graph expects [1, 512] instead of [1, 576] or 1D vs scalar sr
        useContext576 = !useContext576
        try {
          evalV5(env, session, chunk512, withContext64 = useContext576, scalarSr = srIsScalar)
        } catch (_: Throwable) {
          srIsScalar = !srIsScalar
          evalV5(env, session, chunk512, withContext64 = useContext576, scalarSr = srIsScalar)
        }
      }
    } else {
      // Silero VAD v4: inputs = [input, sr, h, c], outputs = [output, hn, cn]
      evalV4(env, session, chunk512, scalarSr = srIsScalar)
    }
  }

  private fun evalV5(
    env: OrtEnvironment,
    session: OrtSession,
    chunk512: FloatArray,
    withContext64: Boolean,
    scalarSr: Boolean,
  ): Float {
    val inputSamples: FloatArray
    if (withContext64) {
      inputSamples = FloatArray(CONTEXT_SAMPLES_16K + CHUNK_SAMPLES)
      System.arraycopy(context64, 0, inputSamples, 0, CONTEXT_SAMPLES_16K)
      System.arraycopy(chunk512, 0, inputSamples, CONTEXT_SAMPLES_16K, CHUNK_SAMPLES)
      System.arraycopy(
        chunk512,
        CHUNK_SAMPLES - CONTEXT_SAMPLES_16K,
        context64,
        0,
        CONTEXT_SAMPLES_16K,
      )
    } else {
      inputSamples = chunk512
    }

    val inputShape = longArrayOf(1L, inputSamples.size.toLong())
    val stateShape = longArrayOf(2L, 1L, 128L)

    OnnxTensor.createTensor(env, FloatBuffer.wrap(inputSamples), inputShape).use { inputTensor ->
      OnnxTensor.createTensor(env, FloatBuffer.wrap(stateV5), stateShape).use { stateTensor ->
        createSrTensor(env, scalarSr).use { srTensor ->
          val inputs =
            mapOf(
              "input" to inputTensor,
              "state" to stateTensor,
              "sr" to srTensor,
            )
          session.run(inputs).use { results ->
            val prob = extractProbFromResult(results)
            if (results.size() > 1) {
              val stateOut = results.get(1) as? OnnxTensor
              val fb = stateOut?.floatBuffer
              if (fb != null && fb.remaining() == stateV5.size) {
                fb.get(stateV5)
              }
            }
            return prob
          }
        }
      }
    }
  }

  private fun evalV4(
    env: OrtEnvironment,
    session: OrtSession,
    chunk512: FloatArray,
    scalarSr: Boolean,
  ): Float {
    val inputShape = longArrayOf(1L, CHUNK_SAMPLES.toLong())
    val hcShape = longArrayOf(2L, 1L, 64L)

    OnnxTensor.createTensor(env, FloatBuffer.wrap(chunk512), inputShape).use { inputTensor ->
      OnnxTensor.createTensor(env, FloatBuffer.wrap(hV4), hcShape).use { hTensor ->
        OnnxTensor.createTensor(env, FloatBuffer.wrap(cV4), hcShape).use { cTensor ->
          createSrTensor(env, scalarSr).use { srTensor ->
            val inputs =
              mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor,
              )
            session.run(inputs).use { results ->
              val prob = extractProbFromResult(results)
              if (results.size() > 1) {
                val hnOut = results.get(1) as? OnnxTensor
                val fbH = hnOut?.floatBuffer
                if (fbH != null && fbH.remaining() == hV4.size) {
                  fbH.get(hV4)
                }
              }
              if (results.size() > 2) {
                val cnOut = results.get(2) as? OnnxTensor
                val fbC = cnOut?.floatBuffer
                if (fbC != null && fbC.remaining() == cV4.size) {
                  fbC.get(cV4)
                }
              }
              return prob
            }
          }
        }
      }
    }
  }

  private fun createSrTensor(env: OrtEnvironment, scalarSr: Boolean): OnnxTensor {
    return if (scalarSr) {
      OnnxTensor.createTensor(env, SAMPLE_RATE_HZ)
    } else {
      OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(SAMPLE_RATE_HZ)), longArrayOf(1L))
    }
  }

  private fun extractProbFromResult(results: OrtSession.Result): Float {
    if (results.size() == 0) return 0f
    val firstTensor = results.get(0) as? OnnxTensor
    val fb = firstTensor?.floatBuffer
    if (fb != null && fb.remaining() > 0) {
      return fb.get(0).coerceIn(0f, 1f)
    }
    return 0f
  }
}
