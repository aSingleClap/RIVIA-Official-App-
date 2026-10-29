package com.example.network

import com.example.security.VoicePreferences
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ElevenLabsVoice(
  val voiceId: String,
  val name: String,
  val category: String,
  val previewUrl: String?,
)

data class ElevenLabsModel(
  val modelId: String,
  val name: String,
  val description: String,
)

data class ElevenLabsVoiceSettingsDto(
  val stability: Float,
  val similarityBoost: Float,
  val useSpeakerBoost: Boolean,
  val style: Float,
  val speed: Float,
  val cloneStrength: Float?,
)

data class SynthesizedChunkResult(
  val audioFile: File,
  val fallbackErrorNotice: String? = null,
)

class ElevenLabsClient {
  companion object {
    private const val BASE_URL = "https://api.elevenlabs.io"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    const val FALLBACK_MODEL_ID = "eleven_flash_v2_5"

    val SUPPORTED_MP3_FORMATS =
      listOf(
        "mp3_44100_128",
        "mp3_44100_192",
        "mp3_44100_96",
        "mp3_44100_64",
        "mp3_22050_32",
      )
  }

  // Zero HTTP logging of headers or bodies
  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()

  /**
   * Section 5: Voice picker from GET https://api.elevenlabs.io/v2/voices?voice_type=personal
   * (header xi-api-key), showing name, category and a preview.
   */
  suspend fun fetchPersonalVoices(apiKey: String): Result<List<ElevenLabsVoice>> =
    withContext(Dispatchers.IO) {
      if (apiKey.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("ElevenLabs API key is empty. Paste your key in Settings.")
        )
      }
      val request =
        Request.Builder()
          .url("$BASE_URL/v2/voices?voice_type=personal")
          .addHeader("xi-api-key", apiKey)
          .get()
          .build()

      try {
        httpClient.newCall(request).execute().use { resp ->
          val bodyStr = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            return@withContext Result.failure(
              IOException(extractElevenLabsError(resp.code, bodyStr))
            )
          }
          val root = JSONObject(bodyStr)
          val voicesArr = root.optJSONArray("voices") ?: JSONArray()
          val list = mutableListOf<ElevenLabsVoice>()
          for (i in 0 until voicesArr.length()) {
            val v = voicesArr.optJSONObject(i) ?: continue
            val id = v.optString("voice_id")
            if (id.isBlank()) continue
            list.add(
              ElevenLabsVoice(
                voiceId = id,
                name = v.optString("name", id),
                category = v.optString("category", "personal"),
                previewUrl = v.optString("preview_url").takeIf { it.isNotBlank() },
              )
            )
          }
          Result.success(list)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Section 5: Model pickers filled from GET https://api.elevenlabs.io/v1/models,
   * keeping models whose can_do_text_to_speech is true.
   */
  suspend fun fetchTtsModels(apiKey: String): Result<List<ElevenLabsModel>> =
    withContext(Dispatchers.IO) {
      if (apiKey.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("ElevenLabs API key is empty. Paste your key in Settings.")
        )
      }
      val request =
        Request.Builder()
          .url("$BASE_URL/v1/models")
          .addHeader("xi-api-key", apiKey)
          .get()
          .build()

      try {
        httpClient.newCall(request).execute().use { resp ->
          val bodyStr = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            return@withContext Result.failure(
              IOException(extractElevenLabsError(resp.code, bodyStr))
            )
          }
          val arr = JSONArray(bodyStr)
          val list = mutableListOf<ElevenLabsModel>()
          for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            if (!m.optBoolean("can_do_text_to_speech", false)) continue
            val id = m.optString("model_id")
            if (id.isBlank()) continue
            list.add(
              ElevenLabsModel(
                modelId = id,
                name = m.optString("name", id),
                description = m.optString("description", ""),
              )
            )
          }
          Result.success(list)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Section 5: Voice settings starting from GET https://api.elevenlabs.io/v1/voices/{voice_id}/settings
   */
  suspend fun fetchVoiceSettings(
    apiKey: String,
    voiceId: String,
  ): Result<ElevenLabsVoiceSettingsDto> =
    withContext(Dispatchers.IO) {
      if (apiKey.isBlank() || voiceId.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("ElevenLabs key and Voice ID are required.")
        )
      }
      val request =
        Request.Builder()
          .url("$BASE_URL/v1/voices/${voiceId.trim()}/settings")
          .addHeader("xi-api-key", apiKey)
          .get()
          .build()

      try {
        httpClient.newCall(request).execute().use { resp ->
          val bodyStr = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            return@withContext Result.failure(
              IOException(extractElevenLabsError(resp.code, bodyStr))
            )
          }
          val obj = JSONObject(bodyStr)
          Result.success(
            ElevenLabsVoiceSettingsDto(
              stability = obj.optDouble("stability", 0.5).toFloat(),
              similarityBoost = obj.optDouble("similarity_boost", 0.75).toFloat(),
              useSpeakerBoost = obj.optBoolean("use_speaker_boost", true),
              style = obj.optDouble("style", 0.0).toFloat(),
              speed = obj.optDouble("speed", 1.0).toFloat(),
              cloneStrength =
                if (obj.has("clone_strength")) obj.optDouble("clone_strength").toFloat() else null,
            )
          )
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Section 5: POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}/stream?output_format=...
   * with {text, model_id, voice_settings, previous_text, apply_text_normalization}.
   * If a model is rejected, returns the error notice and falls back to eleven_flash_v2_5.
   */
  suspend fun streamSentenceToFile(
    apiKey: String,
    voicePrefs: VoicePreferences,
    modelId: String,
    text: String,
    previousText: String?,
    cacheDir: File,
  ): Result<SynthesizedChunkResult> =
    withContext(Dispatchers.IO) {
      if (apiKey.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("ElevenLabs API key is missing. Add it in Settings.")
        )
      }
      val voiceId = voicePrefs.voiceId.trim()
      if (voiceId.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("ElevenLabs Voice ID is not selected. Choose or paste a Voice ID in Settings > Voice.")
        )
      }

      val firstTry =
        executeTtsStreamRequest(
          apiKey = apiKey,
          voiceId = voiceId,
          voicePrefs = voicePrefs,
          modelId = modelId,
          text = text,
          previousText = previousText,
          cacheDir = cacheDir,
        )

      if (firstTry.isSuccess || modelId == FALLBACK_MODEL_ID) {
        return@withContext firstTry.map { SynthesizedChunkResult(it, null) }
      }

      val initialError = firstTry.exceptionOrNull()?.message ?: "Model $modelId rejected"
      // Fall back to eleven_flash_v2_5 and surface the original rejection error
      val fallbackTry =
        executeTtsStreamRequest(
          apiKey = apiKey,
          voiceId = voiceId,
          voicePrefs = voicePrefs,
          modelId = FALLBACK_MODEL_ID,
          text = text,
          previousText = previousText,
          cacheDir = cacheDir,
        )

      fallbackTry.map { file ->
        SynthesizedChunkResult(
          audioFile = file,
          fallbackErrorNotice = "$initialError (Fell back to $FALLBACK_MODEL_ID)",
        )
      }
    }

  private fun executeTtsStreamRequest(
    apiKey: String,
    voiceId: String,
    voicePrefs: VoicePreferences,
    modelId: String,
    text: String,
    previousText: String?,
    cacheDir: File,
  ): Result<File> {
    val isV4OrV4Turbo = modelId == "eleven_v4" || modelId == "eleven_v4_turbo"
    val voiceSettingsJson =
      JSONObject().apply {
        put("stability", voicePrefs.stability.toDouble())
        put("similarity_boost", voicePrefs.similarityBoost.toDouble())
        put("use_speaker_boost", voicePrefs.useSpeakerBoost)
        if (!isV4OrV4Turbo) {
          put("style", voicePrefs.style.toDouble())
          put("speed", voicePrefs.speed.toDouble())
        }
        if (modelId == "eleven_v4" && voicePrefs.isProfessionalClone) {
          put("clone_strength", voicePrefs.cloneStrength.toDouble())
        }
      }

    val payload =
      JSONObject().apply {
        put("text", text)
        put("model_id", modelId)
        put("voice_settings", voiceSettingsJson)
        if (!previousText.isNullOrBlank()) {
          put("previous_text", previousText)
        }
        put("apply_text_normalization", voicePrefs.textNormalization)
      }

    val outputFormat =
      voicePrefs.outputFormat.takeIf { it.startsWith("mp3_") } ?: "mp3_44100_128"
    val url = "$BASE_URL/v1/text-to-speech/$voiceId/stream?output_format=$outputFormat"

    val request =
      Request.Builder()
        .url(url)
        .addHeader("xi-api-key", apiKey)
        .addHeader("Content-Type", "application/json")
        .addHeader("Accept", "audio/mpeg")
        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    return try {
      httpClient.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) {
          val errBody = resp.body?.string().orEmpty()
          return Result.failure(IOException(extractElevenLabsError(resp.code, errBody)))
        }
        val body = resp.body ?: return Result.failure(IOException("Empty audio stream response"))
        val outFile = File.createTempFile("rivia_tts_", ".mp3", cacheDir)
        FileOutputStream(outFile).use { fos ->
          body.byteStream().copyTo(fos)
        }
        Result.success(outFile)
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun extractElevenLabsError(code: Int, rawBody: String): String {
    if (rawBody.isBlank()) return "ElevenLabs HTTP $code"
    return try {
      val obj = JSONObject(rawBody)
      val detail = obj.opt("detail")
      when (detail) {
        is JSONObject -> {
          val msg = detail.optString("message").ifBlank { detail.toString() }
          "ElevenLabs HTTP $code: $msg"
        }
        is String -> "ElevenLabs HTTP $code: $detail"
        else -> "ElevenLabs HTTP $code: $rawBody"
      }
    } catch (_: Exception) {
      "ElevenLabs HTTP $code: $rawBody"
    }
  }
}
