package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.secureKeyDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "rivia_secure_keys")

data class VoicePreferences(
  val voiceId: String = "",
  val voiceName: String = "",
  val voiceCategory: String = "",
  val readAloudModelId: String = "eleven_v4",
  val liveModelId: String = "eleven_v4_turbo",
  val stability: Float = 0.5f,
  val similarityBoost: Float = 0.75f,
  val useSpeakerBoost: Boolean = true,
  val style: Float = 0.0f,
  val speed: Float = 1.0f,
  val cloneStrength: Float = 0.8f,
  val isProfessionalClone: Boolean = false,
  val textNormalization: String = "auto", // auto, on, off
  val outputFormat: String = "mp3_44100_128",
  val sendAudioTags: Boolean = true,
  val sttLanguage: String = "en-US",
  val preferOnDeviceRecognition: Boolean = false,
  val keepScreenOnInLive: Boolean = true,
  val useEarbudMicInLive: Boolean = false,
)

data class LabMicTestPreferences(
  val recordAlongsideRecognizer: Boolean = true, // default ON per Pass 2 spec
  val micTestMode: String = "B", // Mode B is the Live default
  val saveWavClips: Boolean = false, // keeps last 10 clips as WAV for playback
  val vadStartThreshold: Float = 0.50f, // Speech starts at prob >= 0.5
  val vadStartDurationMs: Int = 250, // for 250 ms
  val vadEndThreshold: Float = 0.35f, // Turn ends below 0.35
  val vadEndSilenceMs: Int = 700, // after 700 ms
  val emotionBaselineResetAt: Long = 0L, // Timestamp of last "Reset emotion baseline"
  val callStopListeningOnPipeClose: Boolean = false, // Fix 1: default false = skip stopListening() and let pipe EOF finish
)

class SecureKeyRepository(private val context: Context) {
  companion object {
    private val ENC_GEMINI_KEY = stringPreferencesKey("enc_gemini_api_key")
    private val ENC_ELEVEN_KEY = stringPreferencesKey("enc_elevenlabs_api_key")

    private val ACTIVE_UI_MODE = stringPreferencesKey("active_ui_mode")

    // Rivia / Dev shared Voice preferences
    private val VOICE_ID = stringPreferencesKey("voice_id")
    private val VOICE_NAME = stringPreferencesKey("voice_name")
    private val VOICE_CATEGORY = stringPreferencesKey("voice_category")
    private val READ_ALOUD_MODEL = stringPreferencesKey("read_aloud_model_id")
    private val LIVE_MODEL = stringPreferencesKey("live_model_id")
    private val STABILITY = floatPreferencesKey("voice_stability")
    private val SIMILARITY_BOOST = floatPreferencesKey("voice_similarity_boost")
    private val USE_SPEAKER_BOOST = booleanPreferencesKey("voice_use_speaker_boost")
    private val STYLE = floatPreferencesKey("voice_style")
    private val SPEED = floatPreferencesKey("voice_speed")
    private val CLONE_STRENGTH = floatPreferencesKey("voice_clone_strength")
    private val IS_PRO_CLONE = booleanPreferencesKey("voice_is_pro_clone")
    private val TEXT_NORM = stringPreferencesKey("voice_text_normalization")
    private val OUTPUT_FORMAT = stringPreferencesKey("voice_output_format")
    private val SEND_AUDIO_TAGS = booleanPreferencesKey("voice_send_audio_tags")
    private val STT_LANGUAGE = stringPreferencesKey("stt_language")
    private val PREFER_ON_DEVICE = booleanPreferencesKey("prefer_on_device_stt")
    private val KEEP_SCREEN_ON_LIVE = booleanPreferencesKey("keep_screen_on_live")
    private val USE_EARBUD_MIC_LIVE = booleanPreferencesKey("use_earbud_mic_live")

    // Labs sandboxed Voice preferences (seeded from Rivia defaults until edited in Labs)
    private val LABS_VOICE_ID = stringPreferencesKey("labs_voice_id")
    private val LABS_VOICE_NAME = stringPreferencesKey("labs_voice_name")
    private val LABS_VOICE_CATEGORY = stringPreferencesKey("labs_voice_category")
    private val LABS_READ_ALOUD_MODEL = stringPreferencesKey("labs_read_aloud_model_id")
    private val LABS_LIVE_MODEL = stringPreferencesKey("labs_live_model_id")
    private val LABS_STABILITY = floatPreferencesKey("labs_voice_stability")
    private val LABS_SIMILARITY_BOOST = floatPreferencesKey("labs_voice_similarity_boost")
    private val LABS_USE_SPEAKER_BOOST = booleanPreferencesKey("labs_voice_use_speaker_boost")
    private val LABS_STYLE = floatPreferencesKey("labs_voice_style")
    private val LABS_SPEED = floatPreferencesKey("labs_voice_speed")
    private val LABS_CLONE_STRENGTH = floatPreferencesKey("labs_voice_clone_strength")
    private val LABS_IS_PRO_CLONE = booleanPreferencesKey("labs_voice_is_pro_clone")
    private val LABS_TEXT_NORM = stringPreferencesKey("labs_voice_text_normalization")
    private val LABS_OUTPUT_FORMAT = stringPreferencesKey("labs_voice_output_format")
    private val LABS_SEND_AUDIO_TAGS = booleanPreferencesKey("labs_voice_send_audio_tags")
    private val LABS_STT_LANGUAGE = stringPreferencesKey("labs_stt_language")
    private val LABS_PREFER_ON_DEVICE = booleanPreferencesKey("labs_prefer_on_device_stt")
    private val LABS_KEEP_SCREEN_ON_LIVE = booleanPreferencesKey("labs_keep_screen_on_live")
    private val LABS_USE_EARBUD_MIC_LIVE = booleanPreferencesKey("labs_use_earbud_mic_live")

    private val PRESETS_JSON = stringPreferencesKey("mode_presets_json")

    // Rivia / Dev shared Mic & Emotion test preferences + Silero VAD settings
    private val LAB_RECORD_ALONGSIDE_RECOGNIZER =
      booleanPreferencesKey("lab_record_alongside_recognizer")
    private val LAB_MIC_TEST_MODE_V2 = stringPreferencesKey("lab_mic_test_mode_v2")
    private val LAB_SAVE_WAV_CLIPS = booleanPreferencesKey("lab_save_wav_clips")
    private val LAB_VAD_START_THRESHOLD = floatPreferencesKey("lab_vad_start_threshold")
    private val LAB_VAD_START_DURATION_MS = intPreferencesKey("lab_vad_start_duration_ms")
    private val LAB_VAD_END_THRESHOLD = floatPreferencesKey("lab_vad_end_threshold")
    private val LAB_VAD_END_SILENCE_MS = intPreferencesKey("lab_vad_end_silence_ms")
    private val LAB_EMOTION_BASELINE_RESET_AT =
      androidx.datastore.preferences.core.longPreferencesKey("lab_emotion_baseline_reset_at")
    private val LAB_CALL_STOP_LISTENING_ON_PIPE_CLOSE =
      booleanPreferencesKey("lab_call_stop_listening_on_pipe_close")

    // Labs sandboxed Mic & VAD settings (seeded from Rivia defaults until edited in Labs)
    private val LABS_SANDBOX_RECORD_ALONGSIDE_RECOGNIZER =
      booleanPreferencesKey("labs_sandbox_record_alongside_recognizer")
    private val LABS_SANDBOX_MIC_TEST_MODE_V2 =
      stringPreferencesKey("labs_sandbox_mic_test_mode_v2")
    private val LABS_SANDBOX_SAVE_WAV_CLIPS =
      booleanPreferencesKey("labs_sandbox_save_wav_clips")
    private val LABS_SANDBOX_VAD_START_THRESHOLD =
      floatPreferencesKey("labs_sandbox_vad_start_threshold")
    private val LABS_SANDBOX_VAD_START_DURATION_MS =
      intPreferencesKey("labs_sandbox_vad_start_duration_ms")
    private val LABS_SANDBOX_VAD_END_THRESHOLD =
      floatPreferencesKey("labs_sandbox_vad_end_threshold")
    private val LABS_SANDBOX_VAD_END_SILENCE_MS =
      intPreferencesKey("labs_sandbox_vad_end_silence_ms")
    private val LABS_SANDBOX_EMOTION_BASELINE_RESET_AT =
      androidx.datastore.preferences.core.longPreferencesKey("labs_sandbox_emotion_baseline_reset_at")
    private val LABS_SANDBOX_CALL_STOP_LISTENING_ON_PIPE_CLOSE =
      booleanPreferencesKey("labs_sandbox_call_stop_listening_on_pipe_close")

    fun redactText(input: String, geminiKey: String, elevenKey: String): String {
      var result = input
      if (geminiKey.isNotBlank() && geminiKey.length >= 4) {
        result = result.replace(geminiKey, "[REDACTED_GEMINI_KEY]")
      }
      if (elevenKey.isNotBlank() && elevenKey.length >= 4) {
        result = result.replace(elevenKey, "[REDACTED_ELEVENLABS_KEY]")
      }
      // Also redact any header representations if present
      result =
        result.replace(
          Regex("""("x-goog-api-key"\s*:\s*")([^"]+)(")""", RegexOption.IGNORE_CASE),
          "$1[REDACTED]$3",
        )
      result =
        result.replace(
          Regex("""("xi-api-key"\s*:\s*")([^"]+)(")""", RegexOption.IGNORE_CASE),
          "$1[REDACTED]$3",
        )
      result =
        result.replace(
          Regex("""(AIza[0-9A-Za-z\-_]{20,})"""),
          "[REDACTED_KEY]",
        )
      result =
        result.replace(
          Regex("""(sk_[0-9A-Za-z]{20,})"""),
          "[REDACTED_KEY]",
        )
      return result
    }
  }

  val uiModeFlow: Flow<String> =
    context.secureKeyDataStore.data.map { prefs -> prefs[ACTIVE_UI_MODE] ?: "RIVIA" }

  suspend fun setUiMode(modeKey: String) {
    val clean =
      when (modeKey.trim().uppercase()) {
        "DEV" -> "DEV"
        "LABS" -> "LABS"
        else -> "RIVIA"
      }
    context.secureKeyDataStore.edit { prefs -> prefs[ACTIVE_UI_MODE] = clean }
  }

  val geminiKeyFlow: Flow<String> =
    context.secureKeyDataStore.data.map { prefs ->
      val enc = prefs[ENC_GEMINI_KEY].orEmpty()
      KeystoreCrypto.decrypt(enc)
    }

  val elevenLabsKeyFlow: Flow<String> =
    context.secureKeyDataStore.data.map { prefs ->
      val enc = prefs[ENC_ELEVEN_KEY].orEmpty()
      KeystoreCrypto.decrypt(enc)
    }

  val voicePreferencesFlow: Flow<VoicePreferences> =
    context.secureKeyDataStore.data.map { prefs ->
      val isLabs = prefs[ACTIVE_UI_MODE] == "LABS"
      if (isLabs) {
        VoicePreferences(
          voiceId = prefs[LABS_VOICE_ID] ?: prefs[VOICE_ID] ?: "",
          voiceName = prefs[LABS_VOICE_NAME] ?: prefs[VOICE_NAME] ?: "",
          voiceCategory = prefs[LABS_VOICE_CATEGORY] ?: prefs[VOICE_CATEGORY] ?: "",
          readAloudModelId = prefs[LABS_READ_ALOUD_MODEL] ?: prefs[READ_ALOUD_MODEL] ?: "eleven_v4",
          liveModelId = prefs[LABS_LIVE_MODEL] ?: prefs[LIVE_MODEL] ?: "eleven_v4_turbo",
          stability = prefs[LABS_STABILITY] ?: prefs[STABILITY] ?: 0.5f,
          similarityBoost = prefs[LABS_SIMILARITY_BOOST] ?: prefs[SIMILARITY_BOOST] ?: 0.75f,
          useSpeakerBoost = prefs[LABS_USE_SPEAKER_BOOST] ?: prefs[USE_SPEAKER_BOOST] ?: true,
          style = prefs[LABS_STYLE] ?: prefs[STYLE] ?: 0.0f,
          speed = prefs[LABS_SPEED] ?: prefs[SPEED] ?: 1.0f,
          cloneStrength = prefs[LABS_CLONE_STRENGTH] ?: prefs[CLONE_STRENGTH] ?: 0.8f,
          isProfessionalClone = prefs[LABS_IS_PRO_CLONE] ?: prefs[IS_PRO_CLONE] ?: false,
          textNormalization = prefs[LABS_TEXT_NORM] ?: prefs[TEXT_NORM] ?: "auto",
          outputFormat = prefs[LABS_OUTPUT_FORMAT] ?: prefs[OUTPUT_FORMAT] ?: "mp3_44100_128",
          sendAudioTags = prefs[LABS_SEND_AUDIO_TAGS] ?: prefs[SEND_AUDIO_TAGS] ?: true,
          sttLanguage = prefs[LABS_STT_LANGUAGE] ?: prefs[STT_LANGUAGE] ?: "en-US",
          preferOnDeviceRecognition = prefs[LABS_PREFER_ON_DEVICE] ?: prefs[PREFER_ON_DEVICE] ?: false,
          keepScreenOnInLive = prefs[LABS_KEEP_SCREEN_ON_LIVE] ?: prefs[KEEP_SCREEN_ON_LIVE] ?: true,
          useEarbudMicInLive = prefs[LABS_USE_EARBUD_MIC_LIVE] ?: prefs[USE_EARBUD_MIC_LIVE] ?: false,
        )
      } else {
        VoicePreferences(
          voiceId = prefs[VOICE_ID] ?: "",
          voiceName = prefs[VOICE_NAME] ?: "",
          voiceCategory = prefs[VOICE_CATEGORY] ?: "",
          readAloudModelId = prefs[READ_ALOUD_MODEL] ?: "eleven_v4",
          liveModelId = prefs[LIVE_MODEL] ?: "eleven_v4_turbo",
          stability = prefs[STABILITY] ?: 0.5f,
          similarityBoost = prefs[SIMILARITY_BOOST] ?: 0.75f,
          useSpeakerBoost = prefs[USE_SPEAKER_BOOST] ?: true,
          style = prefs[STYLE] ?: 0.0f,
          speed = prefs[SPEED] ?: 1.0f,
          cloneStrength = prefs[CLONE_STRENGTH] ?: 0.8f,
          isProfessionalClone = prefs[IS_PRO_CLONE] ?: false,
          textNormalization = prefs[TEXT_NORM] ?: "auto",
          outputFormat = prefs[OUTPUT_FORMAT] ?: "mp3_44100_128",
          sendAudioTags = prefs[SEND_AUDIO_TAGS] ?: true,
          sttLanguage = prefs[STT_LANGUAGE] ?: "en-US",
          preferOnDeviceRecognition = prefs[PREFER_ON_DEVICE] ?: false,
          keepScreenOnInLive = prefs[KEEP_SCREEN_ON_LIVE] ?: true,
          useEarbudMicInLive = prefs[USE_EARBUD_MIC_LIVE] ?: false,
        )
      }
    }

  val presetsJsonFlow: Flow<String> =
    context.secureKeyDataStore.data.map { prefs -> prefs[PRESETS_JSON].orEmpty() }

  val labMicTestPreferencesFlow: Flow<LabMicTestPreferences> =
    context.secureKeyDataStore.data.map { prefs ->
      val isLabs = prefs[ACTIVE_UI_MODE] == "LABS"
      if (isLabs) {
        LabMicTestPreferences(
          recordAlongsideRecognizer =
            prefs[LABS_SANDBOX_RECORD_ALONGSIDE_RECOGNIZER]
              ?: prefs[LAB_RECORD_ALONGSIDE_RECOGNIZER]
              ?: true,
          micTestMode =
            (prefs[LABS_SANDBOX_MIC_TEST_MODE_V2] ?: prefs[LAB_MIC_TEST_MODE_V2] ?: "B").let {
              if (it == "A") "A" else "B"
            },
          saveWavClips =
            prefs[LABS_SANDBOX_SAVE_WAV_CLIPS] ?: prefs[LAB_SAVE_WAV_CLIPS] ?: false,
          vadStartThreshold =
            (prefs[LABS_SANDBOX_VAD_START_THRESHOLD]
                ?: prefs[LAB_VAD_START_THRESHOLD]
                ?: 0.50f)
              .coerceIn(0.05f, 0.95f),
          vadStartDurationMs =
            (prefs[LABS_SANDBOX_VAD_START_DURATION_MS]
                ?: prefs[LAB_VAD_START_DURATION_MS]
                ?: 250)
              .coerceIn(32, 2000),
          vadEndThreshold =
            (prefs[LABS_SANDBOX_VAD_END_THRESHOLD] ?: prefs[LAB_VAD_END_THRESHOLD] ?: 0.35f)
              .coerceIn(0.05f, 0.90f),
          vadEndSilenceMs =
            (prefs[LABS_SANDBOX_VAD_END_SILENCE_MS] ?: prefs[LAB_VAD_END_SILENCE_MS] ?: 700)
              .coerceIn(100, 4000),
          emotionBaselineResetAt =
            prefs[LABS_SANDBOX_EMOTION_BASELINE_RESET_AT]
              ?: prefs[LAB_EMOTION_BASELINE_RESET_AT]
              ?: 0L,
          callStopListeningOnPipeClose =
            prefs[LABS_SANDBOX_CALL_STOP_LISTENING_ON_PIPE_CLOSE]
              ?: prefs[LAB_CALL_STOP_LISTENING_ON_PIPE_CLOSE]
              ?: false,
        )
      } else {
        LabMicTestPreferences(
          recordAlongsideRecognizer = prefs[LAB_RECORD_ALONGSIDE_RECOGNIZER] ?: true,
          micTestMode = (prefs[LAB_MIC_TEST_MODE_V2] ?: "B").let { if (it == "A") "A" else "B" },
          saveWavClips = prefs[LAB_SAVE_WAV_CLIPS] ?: false,
          vadStartThreshold = (prefs[LAB_VAD_START_THRESHOLD] ?: 0.50f).coerceIn(0.05f, 0.95f),
          vadStartDurationMs = (prefs[LAB_VAD_START_DURATION_MS] ?: 250).coerceIn(32, 2000),
          vadEndThreshold = (prefs[LAB_VAD_END_THRESHOLD] ?: 0.35f).coerceIn(0.05f, 0.90f),
          vadEndSilenceMs = (prefs[LAB_VAD_END_SILENCE_MS] ?: 700).coerceIn(100, 4000),
          emotionBaselineResetAt = prefs[LAB_EMOTION_BASELINE_RESET_AT] ?: 0L,
          callStopListeningOnPipeClose = prefs[LAB_CALL_STOP_LISTENING_ON_PIPE_CLOSE] ?: false,
        )
      }
    }

  suspend fun updateLabMicTestPreferences(
    transform: (LabMicTestPreferences) -> LabMicTestPreferences
  ) {
    val current = labMicTestPreferencesFlow.first()
    val updated = transform(current)
    context.secureKeyDataStore.edit { prefs ->
      val isLabs = prefs[ACTIVE_UI_MODE] == "LABS"
      if (isLabs) {
        prefs[LABS_SANDBOX_RECORD_ALONGSIDE_RECOGNIZER] = updated.recordAlongsideRecognizer
        prefs[LABS_SANDBOX_MIC_TEST_MODE_V2] = if (updated.micTestMode == "A") "A" else "B"
        prefs[LABS_SANDBOX_SAVE_WAV_CLIPS] = updated.saveWavClips
        prefs[LABS_SANDBOX_VAD_START_THRESHOLD] = updated.vadStartThreshold.coerceIn(0.05f, 0.95f)
        prefs[LABS_SANDBOX_VAD_START_DURATION_MS] = updated.vadStartDurationMs.coerceIn(32, 2000)
        prefs[LABS_SANDBOX_VAD_END_THRESHOLD] = updated.vadEndThreshold.coerceIn(0.05f, 0.90f)
        prefs[LABS_SANDBOX_VAD_END_SILENCE_MS] = updated.vadEndSilenceMs.coerceIn(100, 4000)
        prefs[LABS_SANDBOX_EMOTION_BASELINE_RESET_AT] = updated.emotionBaselineResetAt
        prefs[LABS_SANDBOX_CALL_STOP_LISTENING_ON_PIPE_CLOSE] = updated.callStopListeningOnPipeClose
      } else {
        prefs[LAB_RECORD_ALONGSIDE_RECOGNIZER] = updated.recordAlongsideRecognizer
        prefs[LAB_MIC_TEST_MODE_V2] = if (updated.micTestMode == "A") "A" else "B"
        prefs[LAB_SAVE_WAV_CLIPS] = updated.saveWavClips
        prefs[LAB_VAD_START_THRESHOLD] = updated.vadStartThreshold.coerceIn(0.05f, 0.95f)
        prefs[LAB_VAD_START_DURATION_MS] = updated.vadStartDurationMs.coerceIn(32, 2000)
        prefs[LAB_VAD_END_THRESHOLD] = updated.vadEndThreshold.coerceIn(0.05f, 0.90f)
        prefs[LAB_VAD_END_SILENCE_MS] = updated.vadEndSilenceMs.coerceIn(100, 4000)
        prefs[LAB_EMOTION_BASELINE_RESET_AT] = updated.emotionBaselineResetAt
        prefs[LAB_CALL_STOP_LISTENING_ON_PIPE_CLOSE] = updated.callStopListeningOnPipeClose
      }
    }
  }

  suspend fun saveGeminiKey(plainKey: String) {
    val trimmed = plainKey.trim()
    val encrypted = if (trimmed.isEmpty()) "" else KeystoreCrypto.encrypt(trimmed)
    context.secureKeyDataStore.edit { prefs -> prefs[ENC_GEMINI_KEY] = encrypted }
  }

  suspend fun saveElevenLabsKey(plainKey: String) {
    val trimmed = plainKey.trim()
    val encrypted = if (trimmed.isEmpty()) "" else KeystoreCrypto.encrypt(trimmed)
    context.secureKeyDataStore.edit { prefs -> prefs[ENC_ELEVEN_KEY] = encrypted }
  }

  suspend fun getGeminiKey(): String = geminiKeyFlow.first()

  suspend fun getElevenLabsKey(): String = elevenLabsKeyFlow.first()

  suspend fun redact(text: String): String {
    val gKey = getGeminiKey()
    val eKey = getElevenLabsKey()
    return redactText(text, gKey, eKey)
  }

  suspend fun updateVoicePreferences(transform: (VoicePreferences) -> VoicePreferences) {
    val current = voicePreferencesFlow.first()
    val updated = transform(current)
    context.secureKeyDataStore.edit { prefs ->
      val isLabs = prefs[ACTIVE_UI_MODE] == "LABS"
      if (isLabs) {
        prefs[LABS_VOICE_ID] = updated.voiceId
        prefs[LABS_VOICE_NAME] = updated.voiceName
        prefs[LABS_VOICE_CATEGORY] = updated.voiceCategory
        prefs[LABS_READ_ALOUD_MODEL] = updated.readAloudModelId
        prefs[LABS_LIVE_MODEL] = updated.liveModelId
        prefs[LABS_STABILITY] = updated.stability
        prefs[LABS_SIMILARITY_BOOST] = updated.similarityBoost
        prefs[LABS_USE_SPEAKER_BOOST] = updated.useSpeakerBoost
        prefs[LABS_STYLE] = updated.style
        prefs[LABS_SPEED] = updated.speed
        prefs[LABS_CLONE_STRENGTH] = updated.cloneStrength
        prefs[LABS_IS_PRO_CLONE] = updated.isProfessionalClone
        prefs[LABS_TEXT_NORM] = updated.textNormalization
        prefs[LABS_OUTPUT_FORMAT] = updated.outputFormat
        prefs[LABS_SEND_AUDIO_TAGS] = updated.sendAudioTags
        prefs[LABS_STT_LANGUAGE] = updated.sttLanguage
        prefs[LABS_PREFER_ON_DEVICE] = updated.preferOnDeviceRecognition
        prefs[LABS_KEEP_SCREEN_ON_LIVE] = updated.keepScreenOnInLive
        prefs[LABS_USE_EARBUD_MIC_LIVE] = updated.useEarbudMicInLive
      } else {
        prefs[VOICE_ID] = updated.voiceId
        prefs[VOICE_NAME] = updated.voiceName
        prefs[VOICE_CATEGORY] = updated.voiceCategory
        prefs[READ_ALOUD_MODEL] = updated.readAloudModelId
        prefs[LIVE_MODEL] = updated.liveModelId
        prefs[STABILITY] = updated.stability
        prefs[SIMILARITY_BOOST] = updated.similarityBoost
        prefs[USE_SPEAKER_BOOST] = updated.useSpeakerBoost
        prefs[STYLE] = updated.style
        prefs[SPEED] = updated.speed
        prefs[CLONE_STRENGTH] = updated.cloneStrength
        prefs[IS_PRO_CLONE] = updated.isProfessionalClone
        prefs[TEXT_NORM] = updated.textNormalization
        prefs[OUTPUT_FORMAT] = updated.outputFormat
        prefs[SEND_AUDIO_TAGS] = updated.sendAudioTags
        prefs[STT_LANGUAGE] = updated.sttLanguage
        prefs[PREFER_ON_DEVICE] = updated.preferOnDeviceRecognition
        prefs[KEEP_SCREEN_ON_LIVE] = updated.keepScreenOnInLive
        prefs[USE_EARBUD_MIC_LIVE] = updated.useEarbudMicInLive
      }
    }
  }

  suspend fun savePresetsJson(json: String) {
    context.secureKeyDataStore.edit { prefs -> prefs[PRESETS_JSON] = json }
  }
}
