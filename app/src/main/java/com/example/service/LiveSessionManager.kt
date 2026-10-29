package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.audio.AudioPlayerManager
import com.example.security.SecureKeyRepository
import com.example.ui.components.OrbState
import com.example.util.TextAudioTagProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
  val errorCaption: String? = null,
)

/**
 * Coordinates the hands-free Live loop between SpeechRecognizer, Gemini streaming,
 * and ElevenLabs sentence playback, enforcing strict mic gating and the 3-error rule.
 */
object LiveSessionManager {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _uiState = MutableStateFlow(LiveUiState())
  val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

  private var speechRecognizer: SpeechRecognizer? = null
  private var appContext: Context? = null
  private var audioPlayerManager: AudioPlayerManager? = null
  private var keyRepository: SecureKeyRepository? = null
  private var onSendLiveTurn:
    (suspend (
      userText: String,
      voiceDurationLabel: String,
      onSentenceReady: (String) -> Unit,
      onReplyDelta: (String) -> Unit,
      onTurnComplete: () -> Unit,
      onTurnError: (String) -> Unit,
    ) -> Unit)? =
    null

  private var turnListenStartMs: Long = 0L
  private var activeTurnJob: Job? = null
  private var callMonitorJob: Job? = null
  private var usingEarbudCommDevice: Boolean = false

  fun attachDependencies(
    context: Context,
    playerManager: AudioPlayerManager,
    keyRepo: SecureKeyRepository,
    liveTurnHandler:
      suspend (
        userText: String,
        voiceDurationLabel: String,
        onSentenceReady: (String) -> Unit,
        onReplyDelta: (String) -> Unit,
        onTurnComplete: () -> Unit,
        onTurnError: (String) -> Unit,
      ) -> Unit,
  ) {
    appContext = context.applicationContext
    audioPlayerManager = playerManager
    keyRepository = keyRepo
    onSendLiveTurn = liveTurnHandler
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
        errorCaption = null,
      )

    scope.launch {
      val prefs = keyRepository?.voicePreferencesFlow?.first()
      if (prefs?.useEarbudMicInLive == true) {
        enableEarbudMicIfConnected(ctx)
      }
      startPhoneCallMonitor(ctx)
      startListening()
    }
  }

  fun onServiceStopped() {
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
      activeTurnJob?.cancel()
      closeMic()
      audioPlayerManager?.stopPlayback()
      _uiState.value = _uiState.value.copy(orbState = OrbState.IDLE)
    } else if (!_uiState.value.isMuted) {
      startListening()
    }
  }

  /**
   * Tapping the orb while Rivia speaks stops her and starts listening.
   * Also if stopped after 3 errors in a row, tapping the orb resets the error counter and restarts listening.
   */
  fun onOrbTapped() {
    val current = _uiState.value
    if (!current.isActive) return

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

  private fun closeMic() {
    try {
      speechRecognizer?.stopListening()
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    } catch (_: Exception) {}
    speechRecognizer = null
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

    // Ensure Rivia is NOT speaking while the mic is open
    if (audioPlayerManager?.isSpeaking?.value == true) {
      return
    }

    closeMic()
    if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
      handleNonSilenceError("Speech recognition is not available on this device.")
      return
    }

    scope.launch {
      val prefs = keyRepository?.voicePreferencesFlow?.first()
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
          }

          override fun onBeginningOfSpeech() {
            turnListenStartMs = System.currentTimeMillis()
          }

          override fun onRmsChanged(rmsdB: Float) {}

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            // Mic closes at end of user's speech
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val matches =
              partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull().orEmpty()
            if (partial.isNotBlank()) {
              _uiState.value = _uiState.value.copy(userCaption = partial)
            }
          }

          override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val finalSpoken =
              matches?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: _uiState.value.userCaption

            closeMic()
            if (finalSpoken.isBlank()) {
              // Treat empty match as silence -> restart listening with no tone or caption
              startListening()
            } else {
              val elapsedSec =
                ((System.currentTimeMillis() - turnListenStartMs) / 1000L).coerceAtLeast(1L)
              val mins = elapsedSec / 60L
              val secs = elapsedSec % 60L
              val voiceLabel = "voice · %d:%02d".format(mins, secs)

              _uiState.value =
                _uiState.value.copy(
                  userCaption = finalSpoken,
                  riviaCaption = "",
                  errorCaption = null,
                  consecutiveErrors = 0,
                  orbState = OrbState.THINKING,
                )
              processUserSpokenTurn(finalSpoken, voiceLabel)
            }
          }

          override fun onError(error: Int) {
            closeMic()
            // Rule 3: Treat ERROR_SPEECH_TIMEOUT and ERROR_NO_MATCH as silence:
            // restart listening with no tone or caption.
            if (
              error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_NO_MATCH
            ) {
              if (_uiState.value.isActive && !_uiState.value.isMuted && !_uiState.value.isPaused) {
                scope.launch {
                  delay(180L)
                  startListening()
                }
              }
              return
            }

            val errDesc = speechErrorToString(error)
            handleNonSilenceError(errDesc)
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
      recognizer.startListening(intent)
    }
  }

  private fun processUserSpokenTurn(userText: String, voiceDurationLabel: String) {
    val handler = onSendLiveTurn ?: return
    val player = audioPlayerManager ?: return

    // Close mic strictly before Rivia thinks & speaks
    closeMic()

    activeTurnJob?.cancel()
    activeTurnJob =
      scope.launch {
        var turnHadError = false

        player.startLiveTurnStream(
          onTurnPlaybackFinished = {
            // Reopen mic ONLY when playback ends and if no 3-strike stop occurred
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
          onError = { ttsErr ->
            turnHadError = true
            handleNonSilenceError(ttsErr)
          },
        )

        var sentenceBuffer = StringBuilder()

        handler(
          userText,
          voiceDurationLabel,
          /* onSentenceReady = */ { sentence ->
            _uiState.value = _uiState.value.copy(orbState = OrbState.SPEAKING)
            player.enqueueLiveSentence(sentence)
          },
          /* onReplyDelta = */ { delta ->
            val updatedRaw = _uiState.value.riviaCaption + delta
            val cleanCaption = TextAudioTagProcessor.stripAudioTagsForScreen(updatedRaw)
            _uiState.value =
              _uiState.value.copy(
                riviaCaption = cleanCaption,
                orbState = OrbState.SPEAKING,
              )

            sentenceBuffer.append(delta)
            val currentBuf = sentenceBuffer.toString()
            val lastPunct =
              currentBuf.indexOfLast { it == '.' || it == '!' || it == '?' || it == '\n' }
            if (lastPunct >= 0 && lastPunct < currentBuf.length - 1) {
              val readyPart = currentBuf.substring(0, lastPunct + 1).trim()
              val remainder = currentBuf.substring(lastPunct + 1)
              sentenceBuffer = StringBuilder(remainder)
              if (readyPart.isNotBlank()) {
                player.enqueueLiveSentence(readyPart)
              }
            }
          },
          /* onTurnComplete = */ {
            val remaining = sentenceBuffer.toString().trim()
            if (remaining.isNotBlank()) {
              player.enqueueLiveSentence(remaining)
            }
            player.finishLiveTurnEnqueuing()
          },
          /* onTurnError = */ { apiErr ->
            turnHadError = true
            player.stopPlayback()
            handleNonSilenceError(apiErr)
          },
        )
      }
  }

  /**
   * Rule 3: For any non-silence error, show it in the captions, play the short error tone,
   * and reopen the mic — but after 3 errors in a row stop listening until the user taps the orb.
   */
  private fun handleNonSilenceError(errorMessage: String) {
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

      audioPlayerManager?.playShortErrorTone()

      if (!stopAfterThree && _uiState.value.isActive && !_uiState.value.isMuted && !_uiState.value.isPaused) {
        delay(450L)
        startListening()
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
