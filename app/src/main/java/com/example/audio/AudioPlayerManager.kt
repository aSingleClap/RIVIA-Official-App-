package com.example.audio

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared ExoPlayer & Transient Audio Focus Manager.
 *
 * Fix #2: Every public AudioPlayerManager method switches to Dispatchers.Main itself
 * so ExoPlayer is never touched off the main thread even when called from Dispatchers.IO.
 * Fix #8: An ElevenLabs model fallback is a notice, not an error.
 * Fix #9: Audio tags are kept only for eleven_v3*, eleven_v4, and eleven_v4_turbo.
 */
class AudioPlayerManager(
  private val context: Context,
  private val keyRepository: SecureKeyRepository,
  private val elevenLabsClient: ElevenLabsClient,
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _speakingMessageId = MutableStateFlow<String?>(null)
  val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

  private val _lastAudioError = MutableStateFlow<String?>(null)
  val lastAudioError: StateFlow<String?> = _lastAudioError.asStateFlow()

  private val _lastAudioNotice = MutableStateFlow<String?>(null)
  val lastAudioNotice: StateFlow<String?> = _lastAudioNotice.asStateFlow()

  private val exceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
      if (throwable !is CancellationException) {
        _lastAudioError.value = throwable.message ?: "Voice playback error"
        _isSpeaking.value = false
        _speakingMessageId.value = null
      }
    }

  private val scope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)

  private var exoPlayer: ExoPlayer? = null
  private var synthesisJob: Job? = null
  private var allChunksEnqueued = true
  private var pausedByTransientFocusLoss = false
  private var liveSessionHoldsFocus = false
  private var onQueueFinishedCallback: (() -> Unit)? = null
  private val tempFiles = mutableListOf<File>()

  // Sentence queue for Live streaming mode
  private var livePreviousText: String? = null
  private val liveSentenceChannel = Channel<String>(Channel.UNLIMITED)
  private var liveQueueJob: Job? = null

  private val focusChangeListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      scope.launch(Dispatchers.Main.immediate) {
        try {
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
              stopPlaybackInternalOnMain(abandonFocus = !liveSessionHoldsFocus)
            }
          }
        } catch (_: Throwable) {}
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
      .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
      .build()

  @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
  private fun getOrCreatePlayerOnMain(): ExoPlayer {
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

    return ExoPlayer.Builder(context.applicationContext)
      .setLoadControl(loadControl)
      .build()
      .apply {
        setAudioAttributes(media3AudioAttributes, /* handleAudioFocus = */ false)
        addListener(
          object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
              if (playbackState == Player.STATE_ENDED && allChunksEnqueued) {
                onAllPlaybackCompletedOnMain()
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
                onAllPlaybackCompletedOnMain()
              }
            }

            override fun onPlayerError(error: PlaybackException) {
              _lastAudioError.value = "Audio playback error: ${error.message}"
              if (allChunksEnqueued) {
                onAllPlaybackCompletedOnMain()
              }
            }
          }
        )
      }
      .also { exoPlayer = it }
  }

  private fun enqueueAudioFileOnMain(audioFile: File, firstChunkStarted: Boolean): Boolean {
    val player = getOrCreatePlayerOnMain()
    val mediaItem = MediaItem.fromUri(Uri.fromFile(audioFile))
    if (!firstChunkStarted) {
      if (!liveSessionHoldsFocus) {
        requestTransientFocus()
      }
      player.setMediaItem(mediaItem)
      player.prepare()
      if (!pausedByTransientFocusLoss) {
        player.play()
      }
      return true
    } else {
      val wasEnded = player.playbackState == Player.STATE_ENDED
      val newIndex = player.mediaItemCount
      player.addMediaItem(mediaItem)
      if (wasEnded) {
        player.seekTo(newIndex, 0L)
      }
      if (player.playbackState == Player.STATE_IDLE) {
        player.prepare()
      }
      if (!pausedByTransientFocusLoss && !player.isPlaying) {
        player.play()
      }
      return true
    }
  }

  private fun requestTransientFocus(): Boolean {
    return try {
      val res = audioManager.requestAudioFocus(audioFocusRequest)
      res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
        res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
    } catch (_: Throwable) {
      false
    }
  }

  private fun abandonTransientFocus() {
    try {
      audioManager.abandonAudioFocusRequest(audioFocusRequest)
    } catch (_: Throwable) {}
  }

  /**
   * Fix #2: Public Live focus method switches to Main thread itself.
   */
  fun acquireLiveSessionAudioFocus() {
    scope.launch(Dispatchers.Main.immediate) {
      liveSessionHoldsFocus = true
      requestTransientFocus()
    }
  }

  /**
   * Fix #2: Public Live focus method switches to Main thread itself.
   */
  fun releaseLiveSessionAudioFocus() {
    scope.launch(Dispatchers.Main.immediate) {
      liveSessionHoldsFocus = false
      stopPlaybackInternalOnMain(abandonFocus = true)
    }
  }

  fun clearError() {
    scope.launch(Dispatchers.Main.immediate) {
      _lastAudioError.value = null
      _lastAudioNotice.value = null
    }
  }

  /**
   * Fix #2: Switches to Main thread itself before touching ExoPlayer or playback state.
   */
  fun speakMarkdownMessage(
    messageId: String?,
    rawMarkdown: String,
    isLiveChannel: Boolean = false,
    onFinished: (() -> Unit)? = null,
  ) {
    scope.launch(Dispatchers.Main.immediate) {
      try {
        if (messageId != null && _speakingMessageId.value == messageId && _isSpeaking.value) {
          stopPlaybackInternalOnMain(abandonFocus = !liveSessionHoldsFocus)
          return@launch
        }

        stopPlaybackInternalOnMain(abandonFocus = !liveSessionHoldsFocus)
        _lastAudioError.value = null
        _lastAudioNotice.value = null
        _speakingMessageId.value = messageId
        onQueueFinishedCallback = onFinished

        synthesisJob =
          scope.launch(Dispatchers.Main) {
            var firstChunkStarted = false
            try {
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

              var activeModelId =
                if (isLiveChannel) voicePrefs.liveModelId else voicePrefs.readAloudModelId
              val keepTags =
                voicePrefs.sendAudioTags &&
                  TextAudioTagProcessor.modelSupportsAudioTags(activeModelId)

              val speakable =
                TextAudioTagProcessor.toSpeakableText(
                  markdown = rawMarkdown,
                  keepAudioTags = keepTags,
                )
              val sentences = TextAudioTagProcessor.splitIntoSentences(speakable)
              if (sentences.isEmpty()) {
                _speakingMessageId.value = null
                onFinished?.invoke()
                return@launch
              }

              allChunksEnqueued = false
              _isSpeaking.value = true

              var prevText: String? = null

              for ((index, sentence) in sentences.withIndex()) {
                val result =
                  elevenLabsClient.streamSentenceToFile(
                    apiKey = apiKey,
                    voicePrefs = voicePrefs,
                    modelId = activeModelId,
                    text = sentence,
                    previousText = prevText,
                    cacheDir = context.cacheDir,
                  )

                result.fold(
                  onSuccess = { chunk ->
                    // Fix #8: Model fallback is a notice, not an error
                    if (!chunk.fallbackNotice.isNullOrBlank()) {
                      _lastAudioNotice.value = chunk.fallbackNotice
                    }
                    if (chunk.usedModelId.isNotBlank()) {
                      activeModelId = chunk.usedModelId
                    }
                    if (!chunk.skippedEmpty && chunk.audioFile != null) {
                      tempFiles.add(chunk.audioFile)
                      firstChunkStarted =
                        enqueueAudioFileOnMain(chunk.audioFile, firstChunkStarted)
                      prevText = sentence
                    }
                  },
                  onFailure = { err ->
                    _lastAudioError.value = err.message ?: "ElevenLabs synthesis failed"
                    allChunksEnqueued = true
                    if (!firstChunkStarted) {
                      onAllPlaybackCompletedOnMain()
                    }
                    return@launch
                  },
                )
                if (index == sentences.lastIndex) {
                  allChunksEnqueued = true
                  val player = exoPlayer
                  if (
                    !firstChunkStarted ||
                      (player != null &&
                        !player.isPlaying &&
                        player.playbackState == Player.STATE_ENDED &&
                        !pausedByTransientFocusLoss)
                  ) {
                    onAllPlaybackCompletedOnMain()
                  }
                }
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Throwable) {
              _lastAudioError.value = e.message ?: "Voice synthesis error"
              allChunksEnqueued = true
              if (!firstChunkStarted) {
                onAllPlaybackCompletedOnMain()
              }
            }
          }
      } catch (e: Throwable) {
        _lastAudioError.value = e.message ?: "Voice playback error"
        _isSpeaking.value = false
        _speakingMessageId.value = null
      }
    }
  }

  /**
   * Fix #2 & Fix #8: Switches to Main thread itself, and reports model fallback as a notice
   * (onNotice) rather than an error so no error tone plays, no strike is added, and the mic reopens.
   */
  fun startLiveTurnStream(
    onTurnPlaybackFinished: () -> Unit,
    onNotice: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    scope.launch(Dispatchers.Main.immediate) {
      try {
        stopPlaybackInternalOnMain(abandonFocus = false)
        allChunksEnqueued = false
        livePreviousText = null
        onQueueFinishedCallback = onTurnPlaybackFinished

        liveQueueJob =
          scope.launch(Dispatchers.Main) {
            var firstChunkStarted = false
            try {
              val apiKey = keyRepository.getElevenLabsKey()
              val voicePrefs = keyRepository.voicePreferencesFlow.first()
              var activeLiveModelId = voicePrefs.liveModelId

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
                    onAllPlaybackCompletedOnMain()
                  }
                  break
                }

                if (apiKey.isBlank() || voicePrefs.voiceId.isBlank()) {
                  onError(
                    if (apiKey.isBlank()) "ElevenLabs API key is missing."
                    else "ElevenLabs Voice ID is not configured in Settings > Voice."
                  )
                  allChunksEnqueued = true
                  onAllPlaybackCompletedOnMain()
                  break
                }

                val keepTags =
                  voicePrefs.sendAudioTags &&
                    TextAudioTagProcessor.modelSupportsAudioTags(activeLiveModelId)
                val speakable =
                  TextAudioTagProcessor.toSpeakableText(
                    markdown = rawSentence,
                    keepAudioTags = keepTags,
                  )
                if (speakable.isBlank()) continue

                _isSpeaking.value = true
                val res =
                  elevenLabsClient.streamSentenceToFile(
                    apiKey = apiKey,
                    voicePrefs = voicePrefs,
                    modelId = activeLiveModelId,
                    text = speakable,
                    previousText = livePreviousText,
                    cacheDir = context.cacheDir,
                  )

                res.fold(
                  onSuccess = { chunk ->
                    // Fix #8: Model fallback is a notice, not an error!
                    if (!chunk.fallbackNotice.isNullOrBlank()) {
                      onNotice(chunk.fallbackNotice)
                    }
                    if (chunk.usedModelId.isNotBlank()) {
                      activeLiveModelId = chunk.usedModelId
                    }
                    if (!chunk.skippedEmpty && chunk.audioFile != null) {
                      tempFiles.add(chunk.audioFile)
                      firstChunkStarted =
                        enqueueAudioFileOnMain(chunk.audioFile, firstChunkStarted)
                      livePreviousText = speakable
                    }
                  },
                  onFailure = { err ->
                    onError(err.message ?: "ElevenLabs synthesis failed")
                    allChunksEnqueued = true
                    if (!firstChunkStarted) {
                      onAllPlaybackCompletedOnMain()
                    }
                  },
                )
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Throwable) {
              onError(e.message ?: "Live voice playback error")
              allChunksEnqueued = true
              onAllPlaybackCompletedOnMain()
            }
          }
      } catch (e: Throwable) {
        onError(e.message ?: "Live audio stream initialization error")
      }
    }
  }

  /**
   * Fix #2: Switches to Main thread itself.
   */
  fun enqueueLiveSentence(sentence: String) {
    scope.launch(Dispatchers.Main.immediate) {
      liveSentenceChannel.trySend(sentence)
    }
  }

  /**
   * Fix #2: Switches to Main thread itself.
   */
  fun finishLiveTurnEnqueuing() {
    scope.launch(Dispatchers.Main.immediate) {
      liveSentenceChannel.trySend("__END_OF_TURN__")
    }
  }

  /**
   * Fix #2: Switches to Main thread itself.
   */
  fun playPreviewUrl(url: String) {
    scope.launch(Dispatchers.Main.immediate) {
      try {
        stopPlaybackInternalOnMain(abandonFocus = !liveSessionHoldsFocus)
        requestTransientFocus()
        allChunksEnqueued = true
        _isSpeaking.value = true
        val player = getOrCreatePlayerOnMain()
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.play()
      } catch (e: Throwable) {
        _lastAudioError.value = "Preview playback error: ${e.message}"
        _isSpeaking.value = false
      }
    }
  }

  suspend fun playShortErrorTone() =
    withContext(Dispatchers.Default) {
      try {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 180)
        kotlinx.coroutines.delay(200)
        toneGen.release()
      } catch (_: Throwable) {}
    }

  /**
   * Fix #2: Switches to Main thread itself.
   */
  fun stopPlayback() {
    scope.launch(Dispatchers.Main.immediate) {
      stopPlaybackInternalOnMain(abandonFocus = !liveSessionHoldsFocus)
    }
  }

  private fun stopPlaybackInternalOnMain(abandonFocus: Boolean) {
    synthesisJob?.cancel()
    synthesisJob = null
    liveQueueJob?.cancel()
    liveQueueJob = null
    while (liveSentenceChannel.tryReceive().isSuccess) {
      // drain channel
    }
    onQueueFinishedCallback = null
    pausedByTransientFocusLoss = false
    allChunksEnqueued = false
    try {
      exoPlayer?.stop()
      exoPlayer?.clearMediaItems()
    } catch (_: Throwable) {}
    allChunksEnqueued = true
    _isSpeaking.value = false
    _speakingMessageId.value = null
    cleanupTempFiles()
    if (abandonFocus) {
      abandonTransientFocus()
    }
  }

  private fun onAllPlaybackCompletedOnMain() {
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
