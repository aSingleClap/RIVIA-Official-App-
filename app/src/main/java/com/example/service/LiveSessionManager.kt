package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.audio.AudioPlayerManager
import com.example.audio.EmotionModelManager
import com.example.audio.EmotionPrediction
import com.example.audio.MicCaptureResult
import com.example.audio.MicSharingRecorder
import com.example.audio.SileroVadConfig
import com.example.audio.SileroVadManager
import com.example.security.SecureKeyRepository
import com.example.ui.components.OrbState
import com.example.util.TextAudioTagProcessor
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LiveUiState(
  val isActive: Boolean = false,
  val orbState: OrbState = OrbState.IDLE,
  val isMuted: Boolean = false,
  val isPaused: Boolean = false,
  val pausedForPhoneCall: Boolean = false,
  val stoppedAfterThreeErrors: Boolean = false,
  val consecutiveErrors: Int = 0,
  val userCaption: String = "",
  val riviaCaption: String = "",
  val noticeCaption: String? = null,
  val errorCaption: String? = null,
)

data class TurnRecognizerDiagnostics(
  val partialResultsCount: Int,
  val callbacksFired: String,
  val usedPartialFallback: Boolean,
  val finalText: String = "",
  val pipeCloseToFinalMs: Long? = null,
  val lastPartialText: String = "",
  val transcriptSource: String = "", // "onResults", "onSegmentResults", "used partial", "none"
)

data class TurnEmotionBarrierResult(
  val turnRequestId: String = "",
  val prediction: EmotionPrediction? = null,
  val waitMs: Long = 0L,
  val failureReason: String? = null, // "emotion_timeout" or "emotion_error" when prediction == null
  val captureResult: MicCaptureResult? = null,
)

data class LiveTurnDispatchContext(
  val turnRequestId: String,
  val transcriptSource: String,
  val barrierResult: TurnEmotionBarrierResult?,
  val vadEndEpochMs: Long?,
  val transcriptReadyEpochMs: Long,
  val emotionReadyEpochMs: Long?,
  val emotionTimeoutEpochMs: Long?,
)

/**
 * Coordinates the hands-free Live loop between SpeechRecognizer, Silero VAD end-of-turn detection,
 * Gemini streaming, and ElevenLabs sentence playback, enforcing strict mic gating, splitter rules,
 * H7 spoken sentence limit, H9 spoken fallback template, and catch-all error recovery.
 */
object LiveSessionManager {
  const val TAG = "LiveSessionManager"
  const val H9_SPOKEN_FALLBACK_LINE = "No reply came back. It's in the log."
  const val PIPE_CLOSE_FALLBACK_TIMEOUT_MS = 3000L
  const val PIPE_DRAIN_BEFORE_STOP_MS = 120L
  const val EMOTION_BARRIER_TIMEOUT_MS = 300L
  const val EMOTION_REASON_TIMEOUT = "emotion_timeout"
  const val EMOTION_REASON_LATE = "emotion_late"
  const val EMOTION_REASON_ERROR = "emotion_error"
  const val EMOTION_REASON_NO_SCORES = "no_scores"

  data class RecognizerCallbackEvent(
    val name: String,
    val timestampMs: Long,
  )

  data class TerminalTranscriptResolution(
    val finalSpoken: String,
    val usedPartial: Boolean,
    val chosenSource: String,
    val recognizerOk: Boolean,
  )

  fun formatCallbackRelativeToPipeClose(
    name: String,
    callbackTimestampMs: Long,
    pipeCloseAtMs: Long,
  ): String {
    if (pipeCloseAtMs <= 0L) return name
    val deltaMs = callbackTimestampMs - pipeCloseAtMs
    val sign = if (deltaMs >= 0L) "+" else ""
    return "$name(${sign}${deltaMs}ms)"
  }

  fun formatCallbackEventsList(
    events: List<RecognizerCallbackEvent>,
    pipeCloseAtMs: Long,
  ): String {
    if (events.isEmpty()) return "none"
    return events.joinToString(", ") { ev ->
      formatCallbackRelativeToPipeClose(ev.name, ev.timestampMs, pipeCloseAtMs)
    }
  }

  /**
   * Fix 1: Ends the transcript wait as soon as the recognizer reaches a terminal state
   * (`onResults` with empty text or `onError`), using the last partial immediately if no final
   * text was produced. The 3,000 ms timer is kept strictly as a last resort if no terminal callback fires.
   */
  fun resolveTerminalCallbackTranscript(
    directResult: String = "",
    finalTextFromRecognizer: String = "",
    finalTextSource: String = "",
    combinedSegments: String = "",
    lastPartialText: String = "",
    isErrorCallback: Boolean = false,
  ): TerminalTranscriptResolution {
    val cleanDirect = directResult.trim()
    val cleanFinal = finalTextFromRecognizer.trim()
    val cleanSegments = combinedSegments.trim()
    val cleanPartial = lastPartialText.trim()

    return when {
      cleanDirect.isNotEmpty() ->
        TerminalTranscriptResolution(
          finalSpoken = cleanDirect,
          usedPartial = false,
          chosenSource = "onResults",
          recognizerOk = true,
        )
      cleanFinal.isNotEmpty() -> {
        val src = finalTextSource.ifBlank { "onSegmentResults" }
        TerminalTranscriptResolution(
          finalSpoken = cleanFinal,
          usedPartial = false,
          chosenSource = src,
          recognizerOk = true,
        )
      }
      cleanSegments.isNotEmpty() ->
        TerminalTranscriptResolution(
          finalSpoken = cleanSegments,
          usedPartial = false,
          chosenSource = "onSegmentResults",
          recognizerOk = true,
        )
      cleanPartial.isNotEmpty() ->
        TerminalTranscriptResolution(
          finalSpoken = cleanPartial,
          usedPartial = true,
          chosenSource = "used partial",
          recognizerOk = true,
        )
      else ->
        TerminalTranscriptResolution(
          finalSpoken = "",
          usedPartial = false,
          chosenSource = "none",
          recognizerOk = false,
        )
    }
  }

  private val exceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
      if (throwable !is CancellationException) {
        _uiState.value =
          _uiState.value.copy(
            orbState = OrbState.IDLE,
            errorCaption = throwable.message ?: "Live session error",
          )
      }
    }
  private val scope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
  private val bgCaptureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _uiState = MutableStateFlow(LiveUiState())
  val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

  private val _lastTurnEmotionBarrierResult = MutableStateFlow<TurnEmotionBarrierResult?>(null)
  val lastTurnEmotionBarrierResult: StateFlow<TurnEmotionBarrierResult?> =
    _lastTurnEmotionBarrierResult.asStateFlow()

  private val pendingTurnDispatchContexts =
    Collections.synchronizedMap(mutableMapOf<String, LiveTurnDispatchContext>())

  fun getTurnDispatchContext(turnRequestId: String): LiveTurnDispatchContext? {
    return pendingTurnDispatchContexts[turnRequestId]
  }

  private var speechRecognizer: SpeechRecognizer? = null
  private var appContext: Context? = null
  private var audioPlayerManager: AudioPlayerManager? = null
  private var keyRepository: SecureKeyRepository? = null
  private var sileroVadManager: SileroVadManager? = null
  private var emotionModelManager: EmotionModelManager? = null
  private var getH7SpokenMaxSentences: (() -> Int?)? = null
  private var isRiviaLiveTurnProvider: (() -> Boolean)? = null
  private var onEnsureEmotionModelReady: (() -> Unit)? = null
  private var onLiveTtsFirstAudio: ((requestId: String, epochMs: Long) -> Unit)? = null
  private var onLiveTurnPlaybackDone: ((requestId: String, epochMs: Long) -> Unit)? = null
  private var onMicCaptureCompleted:
    ((
      requestId: String,
      recognizerOk: Boolean,
      recognizerErrorCode: Int?,
      recognizedText: String,
      diagnostics: TurnRecognizerDiagnostics,
      captureResult: MicCaptureResult,
      precomputedEmotionResult: Result<EmotionPrediction>?,
      emotionWaitMs: Long?,
      emotionBarrierFailureReason: String?,
    ) -> Unit)? =
    null
  private var onSendLiveTurn:
    (suspend (
      userText: String,
      voiceDurationLabel: String,
      turnRequestId: String,
      onSentenceReady: (String) -> Unit,
      onReplyDelta: (String) -> Unit,
      onTurnComplete: () -> Unit,
      onTurnError: (String, Boolean) -> Unit,
    ) -> Unit)? =
    null

  private var turnListenStartMs: Long = 0L
  private var turnMicCaptureActive: Boolean = false
  private val activeListenTurnSeq = AtomicLong(0L)
  private var vadFallbackTimerJob: Job? = null
  private var activeTurnJob: Job? = null
  private var callMonitorJob: Job? = null
  private var usingEarbudCommDevice: Boolean = false

  fun attachDependencies(
    context: Context,
    playerManager: AudioPlayerManager,
    keyRepo: SecureKeyRepository,
    vadManager: SileroVadManager? = null,
    emotionManager: EmotionModelManager? = null,
    h7LimitProvider: () -> Int? = { null },
    isRiviaTurnProvider: () -> Boolean = { true },
    ensureEmotionModelReady: () -> Unit = {},
    liveTtsFirstAudioHandler: (requestId: String, epochMs: Long) -> Unit = { _, _ -> },
    liveTurnPlaybackDoneHandler: (requestId: String, epochMs: Long) -> Unit = { _, _ -> },
    micCaptureCompletedHandler:
      (
        requestId: String,
        recognizerOk: Boolean,
        recognizerErrorCode: Int?,
        recognizedText: String,
        diagnostics: TurnRecognizerDiagnostics,
        captureResult: MicCaptureResult,
        precomputedEmotionResult: Result<EmotionPrediction>?,
        emotionWaitMs: Long?,
        emotionBarrierFailureReason: String?,
      ) -> Unit =
      { _, _, _, _, _, _, _, _, _ -> },
    liveTurnHandler:
      suspend (
        userText: String,
        voiceDurationLabel: String,
        turnRequestId: String,
        onSentenceReady: (String) -> Unit,
        onReplyDelta: (String) -> Unit,
        onTurnComplete: () -> Unit,
        onTurnError: (String, Boolean) -> Unit,
      ) -> Unit,
  ) {
    appContext = context.applicationContext
    audioPlayerManager = playerManager
    keyRepository = keyRepo
    sileroVadManager = vadManager
    emotionModelManager = emotionManager
    getH7SpokenMaxSentences = h7LimitProvider
    isRiviaLiveTurnProvider = isRiviaTurnProvider
    onEnsureEmotionModelReady = ensureEmotionModelReady
    onLiveTtsFirstAudio = liveTtsFirstAudioHandler
    onLiveTurnPlaybackDone = liveTurnPlaybackDoneHandler
    onMicCaptureCompleted = micCaptureCompletedHandler
    onSendLiveTurn = liveTurnHandler
  }

  /**
   * Step 1 Sync Barrier:
   * Awaits [finishCaptureBlock] and [scoreTurnBlock] with a [timeoutMs] (300 ms) timeout.
   * - If it times out, returns immediately with [EMOTION_REASON_TIMEOUT] ("emotion_timeout") so the reply
   *   is never held longer than [timeoutMs], while background scoring keeps running and invokes
   *   [onCaptureAndScoreCompletedAsync] when finished with [EMOTION_REASON_LATE] ("emotion_late") and the total ms.
   * - Records how many ms the wait took for each turn in logcat and [lastTurnEmotionBarrierResult].
   */
  suspend fun awaitEmotionScoresWithBarrier(
    turnRequestId: String,
    timeoutMs: Long = EMOTION_BARRIER_TIMEOUT_MS,
    finishCaptureBlock: suspend () -> MicCaptureResult,
    scoreTurnBlock: suspend (MicCaptureResult) -> Result<EmotionPrediction>,
    onCaptureAndScoreCompletedAsync:
      (suspend (MicCaptureResult, Result<EmotionPrediction>, Long, String?) -> Unit)? =
      null,
  ): TurnEmotionBarrierResult {
    val startNs = System.nanoTime()
    val deferred =
      bgCaptureScope.async {
        val capture = finishCaptureBlock()
        val scoreResult =
          if (capture.mode == "A" && !capture.audioRecordStarted) {
            Result.failure(IllegalStateException(EMOTION_REASON_NO_SCORES))
          } else if (capture.samples16kMono.isEmpty()) {
            Result.failure(
              IllegalStateException(capture.captureError ?: "AudioRecord captured 0 samples")
            )
          } else if (!capture.captureError.isNullOrBlank()) {
            Result.failure(IllegalStateException(capture.captureError))
          } else if (capture.preStartedScoreDeferred != null) {
            runCatching { capture.preStartedScoreDeferred.await() }
              .getOrElse { err -> Result.failure(err) }
          } else {
            runCatching { scoreTurnBlock(capture) }.getOrElse { err -> Result.failure(err) }
          }
        Pair(capture, scoreResult)
      }

    val awaited = withTimeoutOrNull(timeoutMs) { deferred.await() }
    val waitMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)

    if (awaited == null) {
      val reason = EMOTION_REASON_TIMEOUT
      Log.i(
        TAG,
        "Turn emotion sync barrier [$turnRequestId]: status=$reason waitMs=$waitMs (timeout=${timeoutMs}ms)",
      )
      if (onCaptureAndScoreCompletedAsync != null) {
        bgCaptureScope.launch {
          runCatching {
            val (capture, scoreResult) = deferred.await()
            val totalMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(waitMs)
            val lateReason =
              when {
                capture.mode == "A" && !capture.audioRecordStarted -> EMOTION_REASON_NO_SCORES
                scoreResult.isSuccess -> EMOTION_REASON_LATE
                else -> EMOTION_REASON_ERROR
              }
            Log.i(
              TAG,
              "Turn emotion sync barrier [$turnRequestId] late completion: status=$lateReason totalMs=$totalMs",
            )
            onCaptureAndScoreCompletedAsync(capture, scoreResult, totalMs, lateReason)
          }
        }
      }
      val result =
        TurnEmotionBarrierResult(
          turnRequestId = turnRequestId,
          prediction = null,
          waitMs = waitMs,
          failureReason = reason,
          captureResult = null,
        )
      _lastTurnEmotionBarrierResult.value = result
      return result
    }

    val (capture, scoreResult) = awaited
    val pred = scoreResult.getOrNull()
    val isModeANoScores = capture.mode == "A" && !capture.audioRecordStarted
    val reason =
      when {
        pred != null -> null
        isModeANoScores -> EMOTION_REASON_NO_SCORES
        else -> EMOTION_REASON_ERROR
      }

    if (pred != null) {
      Log.i(
        TAG,
        "Turn emotion sync barrier [$turnRequestId]: status=ok waitMs=$waitMs " +
          "overlappedAtVadEnd=${capture.finalScoreStartedAtVadEnd} " +
          "arousal=${"%.3f".format(Locale.US, pred.arousal)} " +
          "dominance=${"%.3f".format(Locale.US, pred.dominance)} " +
          "valence=${"%.3f".format(Locale.US, pred.valence)} " +
          "windows=${pred.scoredWindowsCount} inferenceMs=${pred.inferenceMs}",
      )
    } else if (isModeANoScores) {
      Log.i(
        TAG,
        "Turn emotion sync barrier [$turnRequestId]: Mode A Google capture (acoustic=null, words_vs_scores=no_scores) waitMs=$waitMs",
      )
    } else {
      val errMsg = scoreResult.exceptionOrNull()?.message ?: "unknown emotion error"
      Log.w(
        TAG,
        "Turn emotion sync barrier [$turnRequestId]: status=$reason waitMs=$waitMs error=$errMsg",
      )
    }

    if (onCaptureAndScoreCompletedAsync != null) {
      bgCaptureScope.launch {
        runCatching {
          onCaptureAndScoreCompletedAsync(capture, scoreResult, waitMs, reason)
        }
      }
    }

    val result =
      TurnEmotionBarrierResult(
        turnRequestId = turnRequestId,
        prediction = pred,
        waitMs = waitMs,
        failureReason = reason,
        captureResult = capture,
      )
    _lastTurnEmotionBarrierResult.value = result
    return result
  }

  fun onServiceStarted() {
    val ctx = appContext ?: return
    val player = audioPlayerManager ?: return

    player.acquireLiveSessionAudioFocus()
    _uiState.value =
      LiveUiState(
        isActive = true,
        orbState = OrbState.LISTENING,
        isMuted = false,
        isPaused = false,
        consecutiveErrors = 0,
        userCaption = "",
        riviaCaption = "",
        noticeCaption = null,
        errorCaption = null,
      )

    scope.launch {
      val prefs = keyRepository?.voicePreferencesFlow?.first()
      if (prefs?.useEarbudMicInLive == true) {
        enableEarbudMicIfConnected(ctx)
      }
      val labMicPrefs = keyRepository?.labMicTestPreferencesFlow?.first()
      if (labMicPrefs?.recordAlongsideRecognizer != false) {
        runCatching { onEnsureEmotionModelReady?.invoke() }
        // Ensure Silero VAD (~2 MB) is downloaded and loaded so turn 1 has VAD ready
        sileroVadManager?.let { vad ->
          withTimeoutOrNull(4500L) {
            vad.downloadIfNeeded(forceRedownload = false)
          }
        }
      }
      startPhoneCallMonitor(ctx)
      startListening()
    }
  }

  fun onServiceStopped() {
    vadFallbackTimerJob?.cancel()
    vadFallbackTimerJob = null
    activeTurnJob?.cancel()
    activeTurnJob = null
    callMonitorJob?.cancel()
    callMonitorJob = null
    closeMic()
    appContext?.let { clearEarbudMicRouting(it) }
    audioPlayerManager?.releaseLiveSessionAudioFocus()
    _uiState.value = LiveUiState(isActive = false, orbState = OrbState.IDLE)
  }

  fun toggleMute() {
    val current = _uiState.value
    if (!current.isActive) return
    val nextMuted = !current.isMuted
    _uiState.value = current.copy(isMuted = nextMuted)
    if (nextMuted) {
      vadFallbackTimerJob?.cancel()
      vadFallbackTimerJob = null
      closeMic()
      if (_uiState.value.orbState == OrbState.LISTENING) {
        _uiState.value = _uiState.value.copy(orbState = OrbState.IDLE)
      }
    } else if (!_uiState.value.isPaused && _uiState.value.orbState != OrbState.SPEAKING) {
      startListening()
    }
  }

  fun togglePause() {
    val current = _uiState.value
    if (!current.isActive) return
    val nextPaused = !current.isPaused
    _uiState.value = current.copy(isPaused = nextPaused)
    if (nextPaused) {
      vadFallbackTimerJob?.cancel()
      vadFallbackTimerJob = null
      activeTurnJob?.cancel()
      closeMic()
      audioPlayerManager?.stopPlayback()
      _uiState.value = _uiState.value.copy(orbState = OrbState.IDLE)
    } else if (!_uiState.value.isMuted) {
      startListening()
    }
  }

  fun onOrbTapped() {
    val current = _uiState.value
    if (!current.isActive) return

    vadFallbackTimerJob?.cancel()
    vadFallbackTimerJob = null
    activeTurnJob?.cancel()
    activeTurnJob = null
    audioPlayerManager?.stopPlayback()

    _uiState.value =
      current.copy(
        isPaused = false,
        isMuted = false,
        stoppedAfterThreeErrors = false,
        consecutiveErrors = 0,
        errorCaption = null,
      )
    startListening()
  }

  private fun startPhoneCallMonitor(context: Context) {
    callMonitorJob?.cancel()
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    callMonitorJob =
      scope.launch {
        while (_uiState.value.isActive) {
          val mode = audioManager.mode
          val inPhoneCall =
            mode == AudioManager.MODE_IN_CALL ||
              (mode == AudioManager.MODE_IN_COMMUNICATION && !usingEarbudCommDevice)
          val wasPausedForCall = _uiState.value.pausedForPhoneCall

          if (inPhoneCall && !wasPausedForCall) {
            _uiState.value =
              _uiState.value.copy(pausedForPhoneCall = true, orbState = OrbState.IDLE)
            vadFallbackTimerJob?.cancel()
            vadFallbackTimerJob = null
            closeMic()
            audioPlayerManager?.stopPlayback()
          } else if (!inPhoneCall && wasPausedForCall) {
            _uiState.value = _uiState.value.copy(pausedForPhoneCall = false)
            if (!_uiState.value.isMuted && !_uiState.value.isPaused) {
              startListening()
            }
          }
          delay(1000L)
        }
      }
  }

  private fun closeRecognizerOnly() {
    try {
      speechRecognizer?.stopListening()
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    } catch (_: Exception) {}
    speechRecognizer = null
  }

  private fun closeMic() {
    vadFallbackTimerJob?.cancel()
    vadFallbackTimerJob = null
    closeRecognizerOnly()
    if (turnMicCaptureActive) {
      turnMicCaptureActive = false
      runCatching { MicSharingRecorder.stopCaptureImmediate() }
    }
  }

  private fun startListening() {
    val ctx = appContext ?: return
    val state = _uiState.value
    if (
      !state.isActive ||
        state.isMuted ||
        state.isPaused ||
        state.pausedForPhoneCall ||
        state.stoppedAfterThreeErrors
    ) {
      return
    }

    if (audioPlayerManager?.isSpeaking?.value == true) {
      return
    }

    closeMic()
    if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
      handleNonSilenceError("Speech recognition service is not available on this device.")
      return
    }

    val turnSeq = activeListenTurnSeq.incrementAndGet()

    scope.launch {
      try {
        val prefs = keyRepository?.voicePreferencesFlow?.first()
        val labMicPrefs = keyRepository?.labMicTestPreferencesFlow?.first()
        val recordAlongside = labMicPrefs?.recordAlongsideRecognizer ?: true
        val requestedMicMode = labMicPrefs?.micTestMode ?: "B"
        val vadConfig =
          SileroVadConfig(
            startProbThreshold = labMicPrefs?.vadStartThreshold ?: 0.50f,
            startSpeechMs = labMicPrefs?.vadStartDurationMs ?: 250,
            endProbThreshold = labMicPrefs?.vadEndThreshold ?: 0.35f,
            endSilenceMs = labMicPrefs?.vadEndSilenceMs ?: 700,
          )

        if (activeListenTurnSeq.get() != turnSeq) return@launch

        // Per-turn diagnostic state (Fixes 1, 3, 4)
        val turnFinalized = AtomicBoolean(false)
        val pipeClosedByVad = AtomicBoolean(false)
        val pipeCloseAtMs = AtomicLong(0L)
        val partialResultsCount = AtomicInteger(0)
        var lastPartialText = ""
        var finalTextFromRecognizer = ""
        var finalTextSource = "" // "onResults" or "onSegmentResults"
        var pipeCloseToFinalMs: Long? = null
        val segmentResultsList = Collections.synchronizedList(mutableListOf<String>())
        val callbackEventsList =
          Collections.synchronizedList(mutableListOf<RecognizerCallbackEvent>())
        var lastErrorCode: Int? = null
        val callStopListeningOnPipeClose = labMicPrefs?.callStopListeningOnPipeClose ?: false

        fun recordCallbackEvent(name: String) {
          val nowMs = System.currentTimeMillis()
          callbackEventsList.add(RecognizerCallbackEvent(name = name, timestampMs = nowMs))
          val closedAt = pipeCloseAtMs.get()
          if (closedAt > 0L) {
            Log.i(
              TAG,
              "Recognizer callback relative to pipe close: ${formatCallbackRelativeToPipeClose(name, nowMs, closedAt)}"
            )
          } else {
            val fromStartMs = (nowMs - turnListenStartMs).coerceAtLeast(0L)
            Log.i(
              TAG,
              "Recognizer callback (before pipe close, +${fromStartMs}ms from listen start): $name"
            )
          }
        }

        fun recordNonEmptyFinalResult(text: String, source: String) {
          val trimmed = text.trim()
          if (trimmed.isEmpty()) return
          finalTextFromRecognizer = trimmed
          finalTextSource = source
          val closedAt = pipeCloseAtMs.get()
          pipeCloseToFinalMs =
            if (closedAt > 0L) {
              (System.currentTimeMillis() - closedAt).coerceAtLeast(0L)
            } else {
              0L
            }
        }

        fun finalizeListeningTurn(
          finalSpoken: String,
          usedPartial: Boolean,
          chosenSource: String,
          recognizerOk: Boolean,
          explicitErrorCode: Int?,
        ) {
          if (activeListenTurnSeq.get() != turnSeq) return
          if (!turnFinalized.compareAndSet(false, true)) return

          vadFallbackTimerJob?.cancel()
          vadFallbackTimerJob = null

          val closedAtForFinal = pipeCloseAtMs.get()
          if (closedAtForFinal > 0L && pipeCloseToFinalMs == null) {
            pipeCloseToFinalMs = (System.currentTimeMillis() - closedAtForFinal).coerceAtLeast(0L)
          }

          val wasCapturing = turnMicCaptureActive
          turnMicCaptureActive = false
          closeRecognizerOnly()

          val cleanTranscript = finalSpoken.trim()
          val effectiveErrCode = explicitErrorCode ?: lastErrorCode
          val effectiveSource =
            when {
              chosenSource.isNotBlank() -> chosenSource
              usedPartial -> "used partial"
              cleanTranscript.isNotEmpty() && finalTextSource.isNotBlank() -> finalTextSource
              cleanTranscript.isNotEmpty() -> "onResults"
              else -> "none"
            }
          val callbacksSummary =
            synchronized(callbackEventsList) {
              formatCallbackEventsList(callbackEventsList.toList(), closedAtForFinal)
            }
          Log.i(
            TAG,
            "Turn recognizer finalized: source=$effectiveSource, pipeCloseToFinalMs=${pipeCloseToFinalMs ?: -1L}ms, callbacks=[$callbacksSummary]"
          )
          val diagnostics =
            TurnRecognizerDiagnostics(
              partialResultsCount = partialResultsCount.get(),
              callbacksFired = callbacksSummary,
              usedPartialFallback = usedPartial,
              finalText = finalTextFromRecognizer,
              pipeCloseToFinalMs = pipeCloseToFinalMs,
              lastPartialText = lastPartialText,
              transcriptSource = effectiveSource,
            )

          val turnReqId = TextAudioTagProcessor.generateRequestId()
          val isRiviaTurn = isRiviaLiveTurnProvider?.invoke() ?: true
          val transcriptReadyEpochMs = System.currentTimeMillis()
          val vadEndEpochMs = pipeCloseAtMs.get().takeIf { it > 0L }

          // Send the transcript to Gemini as a Live turn
          if (cleanTranscript.isNotEmpty()) {
            val elapsedSec =
              ((System.currentTimeMillis() - turnListenStartMs) / 1000L).coerceAtLeast(1L)
            val mins = elapsedSec / 60L
            val secs = elapsedSec % 60L
            val voiceLabel = "voice · %d:%02d".format(mins, secs)

            _uiState.value =
              _uiState.value.copy(
                userCaption = cleanTranscript,
                riviaCaption = "",
                noticeCaption = null,
                errorCaption = null,
                consecutiveErrors = 0,
                orbState = OrbState.THINKING,
              )

            if (isRiviaTurn) {
              // Step 1 Sync Barrier (Rivia live voice turns only):
              // Await finishCapture() and scoreTurnWithWindows() (or preStartedScoreDeferred started at VAD end-of-turn)
              // before calling processUserSpokenTurn(), with a 300 ms timeout. Never hold the reply longer than the timeout.
              scope.launch {
                val barrierRes: TurnEmotionBarrierResult =
                  if (wasCapturing) {
                    awaitEmotionScoresWithBarrier(
                      turnRequestId = turnReqId,
                      timeoutMs = EMOTION_BARRIER_TIMEOUT_MS,
                      finishCaptureBlock = {
                        val capture =
                          MicSharingRecorder.finishCapture(
                            context = ctx,
                            recognizerHeardSpeech = true,
                          )
                        if (capture.mode == "B") {
                          MicSharingRecorder.noteModeBOutcome(
                            recognizerOk = recognizerOk || usedPartial,
                            errorCode = effectiveErrCode,
                            pipeBrokeEarly = capture.pipeBrokeEarly,
                            usedPartialFallback = usedPartial,
                          )
                        }
                        capture
                      },
                      scoreTurnBlock = { capture ->
                        // Fix 3: If MicSharingRecorder already started scoring the final emotion window at VAD end-of-turn,
                        // await that overlapped Deferred directly!
                        val preStarted = capture.preStartedScoreDeferred
                        if (preStarted != null) {
                          preStarted.await()
                        } else {
                          val mgr = emotionModelManager
                          if (mgr != null) {
                            mgr.scoreTurnWithWindows(
                              rawSamples16kMono = capture.samples16kMono,
                              preScoredWindows = capture.preScoredWindows,
                              remainderSamples = capture.remainderWindowSamples,
                            )
                          } else {
                            Result.failure(IllegalStateException("EmotionModelManager not attached"))
                          }
                        }
                      },
                      onCaptureAndScoreCompletedAsync = { capture, scoreResult, waitMs, failureReason ->
                        onMicCaptureCompleted?.invoke(
                          turnReqId,
                          recognizerOk || usedPartial,
                          effectiveErrCode,
                          cleanTranscript,
                          diagnostics,
                          capture,
                          scoreResult.takeIf { it.isSuccess },
                          waitMs,
                          failureReason,
                        )
                      },
                    )
                  } else {
                    val fallbackBarrier =
                      TurnEmotionBarrierResult(
                        turnRequestId = turnReqId,
                        prediction = null,
                        waitMs = 0L,
                        failureReason = EMOTION_REASON_ERROR,
                        captureResult = null,
                      )
                    _lastTurnEmotionBarrierResult.value = fallbackBarrier
                    Log.w(
                      TAG,
                      "Turn emotion sync barrier [$turnReqId]: status=$EMOTION_REASON_ERROR waitMs=0 error=mic_capture_inactive",
                    )
                    fallbackBarrier
                  }

                val afterBarrierEpochMs = System.currentTimeMillis()
                val effectiveVadEndMs =
                  vadEndEpochMs
                    ?: barrierRes.captureResult?.pipeClosedAtMs?.takeIf { it > 0L }
                val isTimeout = barrierRes.failureReason == EMOTION_REASON_TIMEOUT
                pendingTurnDispatchContexts[turnReqId] =
                  LiveTurnDispatchContext(
                    turnRequestId = turnReqId,
                    transcriptSource = effectiveSource,
                    barrierResult = barrierRes,
                    vadEndEpochMs = effectiveVadEndMs,
                    transcriptReadyEpochMs = transcriptReadyEpochMs,
                    emotionReadyEpochMs = if (barrierRes.prediction != null) afterBarrierEpochMs else null,
                    emotionTimeoutEpochMs = if (isTimeout) afterBarrierEpochMs else null,
                  )

                if (
                  activeListenTurnSeq.get() != turnSeq ||
                    !_uiState.value.isActive ||
                    _uiState.value.isPaused ||
                    _uiState.value.isMuted
                ) {
                  return@launch
                }
                processUserSpokenTurn(cleanTranscript, voiceLabel, turnReqId)
              }
            } else {
              // Lab chats stay unchanged: fire-and-forget capture + logging, call processUserSpokenTurn immediately
              if (wasCapturing) {
                bgCaptureScope.launch {
                  runCatching {
                    val capture =
                      MicSharingRecorder.finishCapture(
                        context = ctx,
                        recognizerHeardSpeech = true,
                      )
                    if (capture.mode == "B") {
                      MicSharingRecorder.noteModeBOutcome(
                        recognizerOk = recognizerOk || usedPartial,
                        errorCode = effectiveErrCode,
                        pipeBrokeEarly = capture.pipeBrokeEarly,
                        usedPartialFallback = usedPartial,
                      )
                    }
                    val preScoredRes = capture.preStartedScoreDeferred?.await()
                    onMicCaptureCompleted?.invoke(
                      turnReqId,
                      recognizerOk || usedPartial,
                      effectiveErrCode,
                      cleanTranscript,
                      diagnostics,
                      capture,
                      preScoredRes,
                      null,
                      if (capture.mode == "A" && !capture.audioRecordStarted) EMOTION_REASON_NO_SCORES else null,
                    )
                  }
                }
              }
              processUserSpokenTurn(cleanTranscript, voiceLabel, turnReqId)
            }
          } else {
            // No transcript produced
            if (wasCapturing) {
              bgCaptureScope.launch {
                runCatching {
                  val capture =
                    MicSharingRecorder.finishCapture(
                      context = ctx,
                      recognizerHeardSpeech = false,
                    )
                  if (capture.mode == "B") {
                    MicSharingRecorder.noteModeBOutcome(
                      recognizerOk = recognizerOk,
                      errorCode = effectiveErrCode,
                      pipeBrokeEarly = capture.pipeBrokeEarly,
                      usedPartialFallback = usedPartial,
                    )
                  }
                  val shouldLog =
                    effectiveErrCode != SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                      capture.rms >= 0.005f ||
                      capture.mode == "B" ||
                      capture.vadSpeechDetected ||
                      capture.captureError != null ||
                      diagnostics.partialResultsCount > 0

                  if (shouldLog) {
                    onMicCaptureCompleted?.invoke(
                      turnReqId,
                      false,
                      effectiveErrCode,
                      "",
                      diagnostics,
                      capture,
                      null,
                      null,
                      if (capture.mode == "A" && !capture.audioRecordStarted) EMOTION_REASON_NO_SCORES else null,
                    )
                  }
                }
              }
            }

            if (
              effectiveErrCode == null ||
                effectiveErrCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                effectiveErrCode == SpeechRecognizer.ERROR_NO_MATCH
            ) {
              if (
                _uiState.value.isActive && !_uiState.value.isMuted && !_uiState.value.isPaused
              ) {
                scope.launch {
                  delay(180L)
                  startListening()
                }
              }
            } else {
              handleNonSilenceError(speechErrorToString(effectiveErrCode))
            }
          }
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
        speechRecognizer = recognizer
        turnListenStartMs = System.currentTimeMillis()

        _uiState.value =
          _uiState.value.copy(
            orbState = OrbState.LISTENING,
            userCaption = "",
          )

        recognizer.setRecognitionListener(
          object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
              turnListenStartMs = System.currentTimeMillis()
              recordCallbackEvent("onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
              turnListenStartMs = System.currentTimeMillis()
              recordCallbackEvent("onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
              recordCallbackEvent("onEndOfSpeech")
            }

            override fun onPartialResults(partialResults: Bundle?) {
              if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return
              val matches =
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              val partial = matches?.firstOrNull()?.trim().orEmpty()
              if (partial.isNotEmpty()) {
                val count = partialResultsCount.incrementAndGet()
                lastPartialText = partial
                recordCallbackEvent("onPartialResults#$count")
                MicSharingRecorder.notifyRecognizerHeardSpeech()
                _uiState.value = _uiState.value.copy(userCaption = partial)
              }
            }

            override fun onSegmentResults(segmentResults: Bundle) {
              if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return
              recordCallbackEvent("onSegmentResults")
              val matches =
                segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              val segText = matches?.firstOrNull()?.trim().orEmpty()
              if (segText.isNotEmpty()) {
                segmentResultsList.add(segText)
                MicSharingRecorder.notifyRecognizerHeardSpeech()
                val combined =
                  synchronized(segmentResultsList) { segmentResultsList.joinToString(" ").trim() }
                recordNonEmptyFinalResult(combined, "onSegmentResults")
                _uiState.value = _uiState.value.copy(userCaption = combined)

                // If Silero VAD has already ended the turn (or mic capture is off / Mode A), accept the non-empty final segment immediately
                if (pipeClosedByVad.get() || !recordAlongside || requestedMicMode == "A") {
                  recordCallbackEvent("used:onSegmentResults")
                  finalizeListeningTurn(
                    finalSpoken = combined,
                    usedPartial = false,
                    chosenSource = "onSegmentResults",
                    recognizerOk = true,
                    explicitErrorCode = null,
                  )
                }
              }
            }

            override fun onEndOfSegmentedSession() {
              if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return
              recordCallbackEvent("onEndOfSegmentedSession")
              val combinedSegments =
                synchronized(segmentResultsList) { segmentResultsList.joinToString(" ").trim() }
              val res =
                resolveTerminalCallbackTranscript(
                  directResult = "",
                  finalTextFromRecognizer = finalTextFromRecognizer,
                  finalTextSource = finalTextSource,
                  combinedSegments = combinedSegments,
                  lastPartialText = lastPartialText,
                  isErrorCallback = false,
                )
              if (res.finalSpoken.isNotEmpty()) {
                if (!res.usedPartial && finalTextFromRecognizer.isEmpty()) {
                  recordNonEmptyFinalResult(res.finalSpoken, res.chosenSource)
                }
                recordCallbackEvent(
                  if (res.usedPartial) "used partial" else "used:${res.chosenSource}"
                )
                finalizeListeningTurn(
                  finalSpoken = res.finalSpoken,
                  usedPartial = res.usedPartial,
                  chosenSource = res.chosenSource,
                  recognizerOk = res.recognizerOk,
                  explicitErrorCode = null,
                )
              }
            }

            override fun onResults(results: Bundle?) {
              if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return
              recordCallbackEvent("onResults")
              val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              val directResult = matches?.firstOrNull()?.trim().orEmpty()
              val combinedSegments =
                synchronized(segmentResultsList) { segmentResultsList.joinToString(" ").trim() }

              // Fix 1: onResults is a terminal recognizer state! End the wait immediately
              // (even if directResult is empty) and use the last partial immediately rather than waiting 3,000 ms.
              val res =
                resolveTerminalCallbackTranscript(
                  directResult = directResult,
                  finalTextFromRecognizer = finalTextFromRecognizer,
                  finalTextSource = finalTextSource,
                  combinedSegments = combinedSegments,
                  lastPartialText = lastPartialText,
                  isErrorCallback = false,
                )
              if (!res.usedPartial && res.finalSpoken.isNotEmpty()) {
                recordNonEmptyFinalResult(res.finalSpoken, res.chosenSource)
                recordCallbackEvent("used:${res.chosenSource}")
              } else if (res.usedPartial) {
                recordCallbackEvent("used partial")
              }
              finalizeListeningTurn(
                finalSpoken = res.finalSpoken,
                usedPartial = res.usedPartial,
                chosenSource = res.chosenSource,
                recognizerOk = res.recognizerOk,
                explicitErrorCode = null,
              )
            }

            override fun onError(error: Int) {
              if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return
              recordCallbackEvent("onError($error)")
              lastErrorCode = error

              val combinedSegments =
                synchronized(segmentResultsList) { segmentResultsList.joinToString(" ").trim() }

              // Fix 1: onError is a terminal recognizer state! End the wait immediately
              // and use any final text or lastPartialText immediately without waiting for the 3 s timer.
              val res =
                resolveTerminalCallbackTranscript(
                  directResult = "",
                  finalTextFromRecognizer = finalTextFromRecognizer,
                  finalTextSource = finalTextSource,
                  combinedSegments = combinedSegments,
                  lastPartialText = lastPartialText,
                  isErrorCallback = true,
                )
              if (!res.usedPartial && res.finalSpoken.isNotEmpty()) {
                if (finalTextFromRecognizer.isEmpty()) {
                  recordNonEmptyFinalResult(res.finalSpoken, res.chosenSource)
                }
                recordCallbackEvent("used:${res.chosenSource}")
              } else if (res.usedPartial) {
                recordCallbackEvent("used partial")
              }
              finalizeListeningTurn(
                finalSpoken = res.finalSpoken,
                usedPartial = res.usedPartial,
                chosenSource = res.chosenSource,
                recognizerOk = res.recognizerOk,
                explicitErrorCode = error,
              )
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
          }
        )

        val intent =
          Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
              RecognizerIntent.EXTRA_LANGUAGE_MODEL,
              RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs?.sttLanguage ?: "en-US")
            if (prefs?.preferOnDeviceRecognition == true) {
              putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
          }

        if (recordAlongside) {
          runCatching {
            onEnsureEmotionModelReady?.invoke()
            sileroVadManager?.ensureDownloadedAsync(forceRedownload = false)
            MicSharingRecorder.startCaptureForLiveTurn(
              context = ctx,
              requestedMode = requestedMicMode,
              recognizerIntent = intent,
              vadManager = sileroVadManager,
              vadConfig = vadConfig,
              emotionModelManager = emotionModelManager,
              onVadEndOfTurn = { closedAtMs ->
                // Fix 1: When Silero ends the turn:
                // MicSharingRecorder has stopped writing and closed the pipe's write end at [closedAtMs] (sending pipe EOF).
                // By default (callStopListeningOnPipeClose == false), we skip speechRecognizer.stopListening()
                // and let the pipe EOF finish recognition naturally.
                // Any terminal callback (onResults / onError / onSegmentResults) ends the wait immediately.
                // The 3 s timer is kept strictly as a last-resort safety net if no callback fires at all.
                scope.launch {
                  if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return@launch
                  pipeCloseAtMs.compareAndSet(0L, closedAtMs)
                  pipeClosedByVad.set(true)
                  recordCallbackEvent("pipeEOF")

                  // If a non-empty final result already arrived before pipe close and no newer partial goes beyond it,
                  // prefer the final result immediately.
                  if (
                    finalTextFromRecognizer.isNotEmpty() &&
                      finalTextFromRecognizer.length >= lastPartialText.length
                  ) {
                    val src = finalTextSource.ifBlank { "onSegmentResults" }
                    recordCallbackEvent("used:$src")
                    finalizeListeningTurn(
                      finalSpoken = finalTextFromRecognizer,
                      usedPartial = false,
                      chosenSource = src,
                      recognizerOk = true,
                      explicitErrorCode = lastErrorCode,
                    )
                    return@launch
                  }

                  vadFallbackTimerJob?.cancel()
                  vadFallbackTimerJob =
                    scope.launch {
                      if (callStopListeningOnPipeClose) {
                        delay(PIPE_DRAIN_BEFORE_STOP_MS)
                        if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return@launch
                        recordCallbackEvent("stopListening()")
                        runCatching { speechRecognizer?.stopListening() }
                        val remainingWaitMs =
                          (PIPE_CLOSE_FALLBACK_TIMEOUT_MS - PIPE_DRAIN_BEFORE_STOP_MS).coerceAtLeast(100L)
                        delay(remainingWaitMs)
                      } else {
                        // Skip stopListening() and let pipe EOF finish; 3 s timer is strictly a last resort
                        delay(PIPE_CLOSE_FALLBACK_TIMEOUT_MS)
                      }
                      if (activeListenTurnSeq.get() != turnSeq || turnFinalized.get()) return@launch
                      recordCallbackEvent("timeout3000ms")

                      val combinedSegs =
                        synchronized(segmentResultsList) {
                          segmentResultsList.joinToString(" ").trim()
                        }
                      val res =
                        resolveTerminalCallbackTranscript(
                          directResult = "",
                          finalTextFromRecognizer = finalTextFromRecognizer,
                          finalTextSource = finalTextSource,
                          combinedSegments = combinedSegs,
                          lastPartialText = lastPartialText,
                          isErrorCallback = false,
                        )
                      if (!res.usedPartial && res.finalSpoken.isNotEmpty()) {
                        recordCallbackEvent("used:${res.chosenSource}")
                      } else if (res.usedPartial) {
                        recordCallbackEvent("used partial")
                      }
                      finalizeListeningTurn(
                        finalSpoken = res.finalSpoken,
                        usedPartial = res.usedPartial,
                        chosenSource = res.chosenSource,
                        recognizerOk = res.recognizerOk,
                        explicitErrorCode = lastErrorCode,
                      )
                    }
                }
              },
            )
            turnMicCaptureActive = true
          }
        } else {
          turnMicCaptureActive = false
        }

        recognizer.startListening(intent)
      } catch (e: Throwable) {
        handleNonSilenceError(e.message ?: "Failed to start speech recognizer")
      }
    }
  }

  private fun processUserSpokenTurn(
    userText: String,
    voiceDurationLabel: String,
    turnRequestId: String,
  ) {
    val handler = onSendLiveTurn ?: return
    val player = audioPlayerManager ?: return
    val h7Limit = getH7SpokenMaxSentences?.invoke()

    closeRecognizerOnly()

    activeTurnJob?.cancel()
    activeTurnJob =
      scope.launch {
        var turnHadError = false

        player.startLiveTurnStream(
          onTurnPlaybackFinished = {
            onLiveTurnPlaybackDone?.invoke(turnRequestId, System.currentTimeMillis())
            scope.launch {
              if (
                _uiState.value.isActive &&
                  !_uiState.value.isMuted &&
                  !_uiState.value.isPaused &&
                  !_uiState.value.stoppedAfterThreeErrors &&
                  !turnHadError
              ) {
                startListening()
              }
            }
          },
          onNotice = { fallbackNotice ->
            scope.launch {
              _uiState.value = _uiState.value.copy(noticeCaption = fallbackNotice)
            }
          },
          onError = { ttsErr ->
            turnHadError = true
            handleNonSilenceError(ttsErr)
          },
          onFirstAudioStarted = {
            onLiveTtsFirstAudio?.invoke(turnRequestId, System.currentTimeMillis())
          },
        )

        val rawReplyAccumulator = StringBuilder()
        var enqueuedSentenceCount = 0
        var finishedEnqueuing = false

        // Fix #3: Catch every exception in the Live turn job and turn it into a Live error caption.
        try {
          handler(
            userText,
            voiceDurationLabel,
            turnRequestId,
            /* onSentenceReady = */ { sentence ->
              if (!finishedEnqueuing && (h7Limit == null || enqueuedSentenceCount < h7Limit)) {
                scope.launch {
                  _uiState.value = _uiState.value.copy(orbState = OrbState.SPEAKING)
                }
                player.enqueueLiveSentence(sentence)
                enqueuedSentenceCount++
                if (h7Limit != null && enqueuedSentenceCount >= h7Limit) {
                  finishedEnqueuing = true
                  player.finishLiveTurnEnqueuing()
                }
              }
            },
            /* onReplyDelta = */ { delta ->
              rawReplyAccumulator.append(delta)
              val sentencesSoFar =
                TextAudioTagProcessor.splitIntoSentences(rawReplyAccumulator.toString())

              // Display only what was spoken (up to h7Limit if active)
              val visibleRaw =
                if (h7Limit != null && sentencesSoFar.size > h7Limit) {
                  sentencesSoFar.take(h7Limit).joinToString(" ")
                } else {
                  rawReplyAccumulator.toString()
                }
              val cleanCaption = TextAudioTagProcessor.stripAudioTagsForScreen(visibleRaw)

              scope.launch {
                _uiState.value =
                  _uiState.value.copy(
                    riviaCaption = cleanCaption,
                    orbState = OrbState.SPEAKING,
                  )
              }

              // Count sentences with the same splitter that feeds ElevenLabs.
              // When sentencesSoFar.size >= 2, all sentences before the last one are complete.
              if (!finishedEnqueuing && sentencesSoFar.size >= 2) {
                val completedCount = sentencesSoFar.size - 1
                while (enqueuedSentenceCount < completedCount) {
                  if (h7Limit != null && enqueuedSentenceCount >= h7Limit) {
                    finishedEnqueuing = true
                    player.finishLiveTurnEnqueuing()
                    break
                  }
                  player.enqueueLiveSentence(sentencesSoFar[enqueuedSentenceCount])
                  enqueuedSentenceCount++
                  if (h7Limit != null && enqueuedSentenceCount >= h7Limit) {
                    finishedEnqueuing = true
                    player.finishLiveTurnEnqueuing()
                    break
                  }
                }
              }
            },
            /* onTurnComplete = */ {
              if (!finishedEnqueuing) {
                val finalSentences =
                  TextAudioTagProcessor.splitIntoSentences(rawReplyAccumulator.toString())
                while (enqueuedSentenceCount < finalSentences.size) {
                  if (h7Limit != null && enqueuedSentenceCount >= h7Limit) {
                    break
                  }
                  player.enqueueLiveSentence(finalSentences[enqueuedSentenceCount])
                  enqueuedSentenceCount++
                }
                finishedEnqueuing = true
                player.finishLiveTurnEnqueuing()
              }
              if (h7Limit != null) {
                val finalSentences =
                  TextAudioTagProcessor.splitIntoSentences(rawReplyAccumulator.toString())
                if (finalSentences.size > h7Limit) {
                  val keptRaw = finalSentences.take(h7Limit).joinToString(" ")
                  val cleanCaption = TextAudioTagProcessor.stripAudioTagsForScreen(keptRaw)
                  scope.launch {
                    _uiState.value = _uiState.value.copy(riviaCaption = cleanCaption)
                  }
                }
              }
            },
            /* onTurnError = */ { apiErr, speakH9Fallback ->
              turnHadError = true
              player.stopPlayback()
              handleNonSilenceError(
                errorMessage = apiErr,
                speakFixedLineInVoice = if (speakH9Fallback) H9_SPOKEN_FALLBACK_LINE else null,
              )
            },
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          turnHadError = true
          player.stopPlayback()
          handleNonSilenceError(e.message ?: "Live turn failed")
        }
      }
  }

  private fun handleNonSilenceError(
    errorMessage: String,
    speakFixedLineInVoice: String? = null,
  ) {
    scope.launch {
      closeMic()
      val newCount = _uiState.value.consecutiveErrors + 1
      val stopAfterThree = newCount >= 3

      _uiState.value =
        _uiState.value.copy(
          consecutiveErrors = newCount,
          stoppedAfterThreeErrors = stopAfterThree,
          orbState = OrbState.IDLE,
          errorCaption =
            if (stopAfterThree) {
              "$errorMessage · Paused after 3 errors in a row. Tap the orb to resume."
            } else {
              errorMessage
            },
        )

      val player = audioPlayerManager
      if (speakFixedLineInVoice != null && player != null) {
        var fellBackToTone = false
        player.speakMarkdownMessage(
          messageId = null,
          rawMarkdown = speakFixedLineInVoice,
          isLiveChannel = true,
          onError = {
            if (!fellBackToTone) {
              fellBackToTone = true
              scope.launch { player.playShortErrorTone() }
            }
          },
          onFinished = {
            scope.launch {
              if (
                !stopAfterThree &&
                  _uiState.value.isActive &&
                  !_uiState.value.isMuted &&
                  !_uiState.value.isPaused
              ) {
                delay(300L)
                startListening()
              }
            }
          },
        )
      } else {
        player?.playShortErrorTone()
        if (
          !stopAfterThree &&
            _uiState.value.isActive &&
            !_uiState.value.isMuted &&
            !_uiState.value.isPaused
        ) {
          delay(450L)
          startListening()
        }
      }
    }
  }

  private fun enableEarbudMicIfConnected(context: Context) {
    try {
      val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val devices = audioManager.availableCommunicationDevices
      val btDevice =
        devices.firstOrNull {
          it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
      if (btDevice != null) {
        usingEarbudCommDevice = audioManager.setCommunicationDevice(btDevice)
      }
    } catch (_: Exception) {}
  }

  private fun clearEarbudMicRouting(context: Context) {
    if (!usingEarbudCommDevice) return
    try {
      val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      audioManager.clearCommunicationDevice()
    } catch (_: Exception) {}
    usingEarbudCommDevice = false
  }

  private fun speechErrorToString(error: Int): String =
    when (error) {
      SpeechRecognizer.ERROR_AUDIO -> "SpeechRecognizer error: Audio recording error"
      SpeechRecognizer.ERROR_CLIENT -> "SpeechRecognizer error: Client side error"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "SpeechRecognizer error: Insufficient microphone permissions"
      SpeechRecognizer.ERROR_NETWORK -> "SpeechRecognizer error: Network error"
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "SpeechRecognizer error: Network timeout"
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "SpeechRecognizer error: Recognizer busy"
      SpeechRecognizer.ERROR_SERVER -> "SpeechRecognizer error: Server error"
      SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "SpeechRecognizer error: Too many requests"
      else -> "SpeechRecognizer error (code $error)"
    }
}
