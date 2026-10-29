package com.example.audio

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.example.network.ElevenLabsClient
import com.example.security.SecureKeyRepository
import com.example.util.TextAudioTagProcessor
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared ExoPlayer & Transient Audio Focus Manager for both:
 * 1) Typed Chat ("Listen" button and auto read-aloud — never starts LiveVoiceService or needs RECORD_AUDIO)
 * 2) Live Mode (pipelined sentence streaming while Rivia's reply streams in)
 *
 * Strictly enforces:
 * - DefaultLoadControl with bufferForPlaybackMs = 250ms
 * - USAGE_MEDIA + AUDIO_CONTENT_TYPE_SPEECH
 * - handleAudioFocus = false on ExoPlayer
 * - Manual AudioFocusRequest(AUDIOFOCUS_GAIN_TRANSIENT):
 *   - For Listen / auto read-aloud: requested right before first sentence plays, abandoned when last sentence finishes.
 *   - For Live mode: requested from Live start until End, then abandoned.
 *   - On transient focus loss (call, alarm, notification): pauses Rivia and resumes where she left off when focus returns.
 *   - On permanent focus loss: stops Rivia.
 */
class AudioPlayerManager(
  private val context: Context,
  private val keyRepository: SecureKeyRepository,
  private val elevenLabsClient: ElevenLabsClient,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _speakingMessageId = MutableStateFlow<String?>(null)
  val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

  private val _lastAudioError = MutableStateFlow<String?>(null)
  val lastAudioError: StateFlow<String?> = _lastAudioError.asStateFlow()

  private var exoPlayer: ExoPlayer? = null
  private var synthesisJob: Job? = null
  private var allChunksEnqueued = true
  private var pausedByTransientFocusLoss = false
  private var liveSessionHoldsFocus = false
  private var onQueueFinishedCallback: (() -> Unit)? = null
  private val tempFiles = mutableListOf<File>()

  // Sentence queue for Live streaming mode
  private var livePreviousText: String? = null
  private val liveSentenceChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
  private var liveQueueJob: Job? = null

  private val focusChangeListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      scope.launch {
        when (focusChange) {
          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            // Pause Rivia and resume where she left off when focus returns
            val player = exoPlayer
            if (player != null && (player.isPlaying || _isSpeaking.value)) {
              pausedByTransientFocusLoss = true
              player.pause()
            }
          }
          AudioManager.AUDIOFOCUS_GAIN -> {
            // Resume where she left off if paused by transient focus loss
            if (pausedByTransientFocusLoss) {
              pausedByTransientFocusLoss = false
              exoPlayer?.play()
            }
          }
          AudioManager.AUDIOFOCUS_LOSS -> {
            // Permanent focus loss stops her playback
            pausedByTransientFocusLoss = false
            stopPlaybackInternal(abandonFocus = !liveSessionHoldsFocus)
          }
        }
      }
    }

  private val audioFocusRequest: AudioFocusRequest =
    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(
        PlatformAudioAttributes.Builder()
          .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
          .setContentType(PlatformAudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      )
      .setAcceptsDelayedFocusGain(true)
      .setOnAudioFocusChangeListener(focusChangeListener)
      .build()

  @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
  private fun getOrCreatePlayer(): ExoPlayer {
    exoPlayer?.let { return it }

    val loadControl =
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(
          /* minBufferMs = */ 2500,
          /* maxBufferMs = */ 15000,
          /* bufferForPlaybackMs = */ 250,
          /* bufferForPlaybackAfterRebufferMs = */ 500,
        )
        .build()

    val media3AudioAttributes =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .build()

    return ExoPlayer.Builder(context)
      .setLoadControl(loadControl)
      .build()
      .apply {
        // handleAudioFocus = false so ExoPlayer never takes permanent focus
        setAudioAttributes(media3AudioAttributes, /* handleAudioFocus = */ false)
        addListener(
          object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
              if (playbackState == Player.STATE_ENDED && allChunksEnqueued) {
                onAllPlaybackCompleted()
              }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
              if (isPlaying) {
                _isSpeaking.value = true
              } else if (
                playbackState == Player.STATE_ENDED &&
                  allChunksEnqueued &&
                  !pausedByTransientFocusLoss
              ) {
                onAllPlaybackCompleted()
              }
            }

            override fun onPlayerError(error: PlaybackException) {
              _lastAudioError.value = "Audio playback error: ${error.message}"
              if (allChunksEnqueued) {
                onAllPlaybackCompleted()
              }
            }
          }
        )
      }
      .also { exoPlayer = it }
  }

  private fun requestTransientFocus(): Boolean {
    val res = audioManager.requestAudioFocus(audioFocusRequest)
    return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
      res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
  }

  private fun abandonTransientFocus() {
    try {
      audioManager.abandonAudioFocusRequest(audioFocusRequest)
    } catch (_: Exception) {}
  }

  /**
   * Called by Live mode on start to hold transient audio focus until End.
   */
  fun acquireLiveSessionAudioFocus() {
    liveSessionHoldsFocus = true
    requestTransientFocus()
  }

  /**
   * Called by Live mode on End to release transient audio focus so paused music resumes.
   */
  fun releaseLiveSessionAudioFocus() {
    liveSessionHoldsFocus = false
    stopPlayback()
    abandonTransientFocus()
  }

  fun clearError() {
    _lastAudioError.value = null
  }

  /**
   * Speaks a complete markdown message in Typed Chat (Listen button or auto read-aloud)
   * or settings Test button.
   * Splits at sentence boundaries, requests chunks sequentially with previous_text,
   * and starts playing the first chunk as soon as it arrives.
   */
  fun speakMarkdownMessage(
    messageId: String?,
    rawMarkdown: String,
    isLiveChannel: Boolean = false,
    onFinished: (() -> Unit)? = null,
  ) {
    // If tapping Listen on the message that is already speaking, stop it
    if (messageId != null && _speakingMessageId.value == messageId && _isSpeaking.value) {
      stopPlayback()
      return
    }

    stopPlaybackInternal(abandonFocus = !liveSessionHoldsFocus)
    _lastAudioError.value = null
    _speakingMessageId.value = messageId
    onQueueFinishedCallback = onFinished

    synthesisJob =
      scope.launch {
        val apiKey = keyRepository.getElevenLabsKey()
        val voicePrefs = keyRepository.voicePreferencesFlow.first()
        if (apiKey.isBlank()) {
          _lastAudioError.value = "ElevenLabs API key is not set. Add it in Settings."
          _speakingMessageId.value = null
          onFinished?.invoke()
          return@launch
        }
        if (voicePrefs.voiceId.isBlank()) {
          _lastAudioError.value =
            "No ElevenLabs Voice selected. Choose your custom voice in Settings > Voice."
          _speakingMessageId.value = null
          onFinished?.invoke()
          return@launch
        }

        val speakable =
          TextAudioTagProcessor.toSpeakableText(
            markdown = rawMarkdown,
            sendAudioTags = voicePrefs.sendAudioTags,
          )
        val sentences = TextAudioTagProcessor.splitIntoSentences(speakable)
        if (sentences.isEmpty()) {
          _speakingMessageId.value = null
          onFinished?.invoke()
          return@launch
        }

        allChunksEnqueued = false
        _isSpeaking.value = true
        val modelId =
          if (isLiveChannel) voicePrefs.liveModelId else voicePrefs.readAloudModelId

        var prevText: String? = null
        var firstChunkStarted = false

        try {
          for ((index, sentence) in sentences.withIndex()) {
            val result =
              elevenLabsClient.streamSentenceToFile(
                apiKey = apiKey,
                voicePrefs = voicePrefs,
                modelId = modelId,
                text = sentence,
                previousText = prevText,
                cacheDir = context.cacheDir,
              )

            result.fold(
              onSuccess = { chunk ->
                if (!chunk.fallbackErrorNotice.isNullOrBlank()) {
                  _lastAudioError.value = chunk.fallbackErrorNotice
                }
                tempFiles.add(chunk.audioFile)
                val player = getOrCreatePlayer()
                if (!firstChunkStarted) {
                  firstChunkStarted = true
                  if (!liveSessionHoldsFocus) {
                    requestTransientFocus()
                  }
                  player.setMediaItem(MediaItem.fromUri(Uri.fromFile(chunk.audioFile)))
                  player.prepare()
                  if (!pausedByTransientFocusLoss) {
                    player.play()
                  }
                } else {
                  player.addMediaItem(MediaItem.fromUri(Uri.fromFile(chunk.audioFile)))
                  if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition(player.mediaItemCount - 1)
                    player.prepare()
                    if (!pausedByTransientFocusLoss) {
                      player.play()
                    }
                  }
                }
                prevText = sentence
              },
              onFailure = { err ->
                _lastAudioError.value = err.message ?: "ElevenLabs synthesis failed"
                allChunksEnqueued = true
                if (!firstChunkStarted) {
                  onAllPlaybackCompleted()
                }
                return@launch
              },
            )
            if (index == sentences.lastIndex) {
              allChunksEnqueued = true
              val player = exoPlayer
              if (player != null && !player.isPlaying && player.playbackState == Player.STATE_ENDED && !pausedByTransientFocusLoss) {
                onAllPlaybackCompleted()
              }
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _lastAudioError.value = e.message ?: "Voice synthesis error"
          allChunksEnqueued = true
          if (!firstChunkStarted) {
            onAllPlaybackCompleted()
          }
        }
      }
  }

  /**
   * Starts a streaming Live turn queue where sentences are fed as Gemini streams them in.
   */
  fun startLiveTurnStream(onTurnPlaybackFinished: () -> Unit, onError: (String) -> Unit) {
    stopPlaybackInternal(abandonFocus = false)
    allChunksEnqueued = false
    livePreviousText = null
    onQueueFinishedCallback = onTurnPlaybackFinished

    liveQueueJob =
      scope.launch {
        val apiKey = keyRepository.getElevenLabsKey()
        val voicePrefs = keyRepository.voicePreferencesFlow.first()
        var firstChunkStarted = false

        try {
          for (rawSentence in liveSentenceChannel) {
            if (rawSentence == "__END_OF_TURN__") {
              allChunksEnqueued = true
              val player = exoPlayer
              if (
                !firstChunkStarted ||
                  (player != null &&
                    !player.isPlaying &&
                    player.playbackState == Player.STATE_ENDED &&
                    !pausedByTransientFocusLoss)
              ) {
                onAllPlaybackCompleted()
              }
              break
            }

            if (apiKey.isBlank() || voicePrefs.voiceId.isBlank()) {
              onError(
                if (apiKey.isBlank()) "ElevenLabs API key is missing."
                else "ElevenLabs Voice ID is not configured in Settings > Voice."
              )
              allChunksEnqueued = true
              onAllPlaybackCompleted()
              break
            }

            val speakable =
              TextAudioTagProcessor.toSpeakableText(
                markdown = rawSentence,
                sendAudioTags = voicePrefs.sendAudioTags,
              )
            if (speakable.isBlank()) continue

            _isSpeaking.value = true
            val res =
              elevenLabsClient.streamSentenceToFile(
                apiKey = apiKey,
                voicePrefs = voicePrefs,
                modelId = voicePrefs.liveModelId,
                text = speakable,
                previousText = livePreviousText,
                cacheDir = context.cacheDir,
              )

            res.fold(
              onSuccess = { chunk ->
                if (!chunk.fallbackErrorNotice.isNullOrBlank()) {
                  onError(chunk.fallbackErrorNotice)
                }
                tempFiles.add(chunk.audioFile)
                val player = getOrCreatePlayer()
                if (!firstChunkStarted) {
                  firstChunkStarted = true
                  player.setMediaItem(MediaItem.fromUri(Uri.fromFile(chunk.audioFile)))
                  player.prepare()
                  if (!pausedByTransientFocusLoss) {
                    player.play()
                  }
                } else {
                  player.addMediaItem(MediaItem.fromUri(Uri.fromFile(chunk.audioFile)))
                  if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition(player.mediaItemCount - 1)
                    player.prepare()
                    if (!pausedByTransientFocusLoss) {
                      player.play()
                    }
                  }
                }
                livePreviousText = speakable
              },
              onFailure = { err ->
                onError(err.message ?: "ElevenLabs synthesis failed")
                allChunksEnqueued = true
                if (!firstChunkStarted) {
                  onAllPlaybackCompleted()
                }
              },
            )
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          onError(e.message ?: "Live voice playback error")
          allChunksEnqueued = true
          onAllPlaybackCompleted()
        }
      }
  }

  fun enqueueLiveSentence(sentence: String) {
    liveSentenceChannel.trySend(sentence)
  }

  fun finishLiveTurnEnqueuing() {
    liveSentenceChannel.trySend("__END_OF_TURN__")
  }

  /**
   * Plays a direct preview URL (for Voice Picker preview button).
   */
  fun playPreviewUrl(url: String) {
    stopPlaybackInternal(abandonFocus = !liveSessionHoldsFocus)
    requestTransientFocus()
    allChunksEnqueued = true
    _isSpeaking.value = true
    val player = getOrCreatePlayer()
    player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
    player.prepare()
    player.play()
  }

  /**
   * Plays a short error tone in Live mode when a non-silence error occurs.
   */
  suspend fun playShortErrorTone() =
    withContext(Dispatchers.Default) {
      try {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 180)
        kotlinx.coroutines.delay(200)
        toneGen.release()
      } catch (_: Exception) {}
    }

  fun stopPlayback() {
    stopPlaybackInternal(abandonFocus = !liveSessionHoldsFocus)
  }

  private fun stopPlaybackInternal(abandonFocus: Boolean) {
    synthesisJob?.cancel()
    synthesisJob = null
    liveQueueJob?.cancel()
    liveQueueJob = null
    while (liveSentenceChannel.tryReceive().isSuccess) {
      // drain channel
    }
    pausedByTransientFocusLoss = false
    allChunksEnqueued = true
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    _isSpeaking.value = false
    _speakingMessageId.value = null
    cleanupTempFiles()
    if (abandonFocus) {
      abandonTransientFocus()
    }
  }

  private fun onAllPlaybackCompleted() {
    _isSpeaking.value = false
    _speakingMessageId.value = null
    pausedByTransientFocusLoss = false
    cleanupTempFiles()
    if (!liveSessionHoldsFocus) {
      abandonTransientFocus()
    }
    val cb = onQueueFinishedCallback
    onQueueFinishedCallback = null
    cb?.invoke()
  }

  private fun cleanupTempFiles() {
    val copy = tempFiles.toList()
    tempFiles.clear()
    scope.launch(Dispatchers.IO) {
      copy.forEach { runCatching { it.delete() } }
    }
  }
}
