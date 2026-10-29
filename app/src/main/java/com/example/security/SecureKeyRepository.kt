package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

class SecureKeyRepository(private val context: Context) {
  companion object {
    private val ENC_GEMINI_KEY = stringPreferencesKey("enc_gemini_api_key")
    private val ENC_ELEVEN_KEY = stringPreferencesKey("enc_elevenlabs_api_key")

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

    private val PRESETS_JSON = stringPreferencesKey("mode_presets_json")

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

  val presetsJsonFlow: Flow<String> =
    context.secureKeyDataStore.data.map { prefs -> prefs[PRESETS_JSON].orEmpty() }

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

  suspend fun savePresetsJson(json: String) {
    context.secureKeyDataStore.edit { prefs -> prefs[PRESETS_JSON] = json }
  }
}
