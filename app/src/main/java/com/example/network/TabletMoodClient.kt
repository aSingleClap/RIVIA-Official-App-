package com.example.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class MoodState(
  val v: Double,
  val a: Double,
  val d: Double,
  val updated: String,
  val label: String,
  val intensity: String,
  val magnitude: Double,
)

sealed class MoodResult {
  data class Success(
    val state: MoodState,
    val clamped: List<String> = emptyList(),
  ) : MoodResult()

  data class Failure(
    val message: String,
    val errorCode: String? = null,
  ) : MoodResult()
}

/**
 * Direct HTTP client for the manual VAD mood control route (/app/emotion).
 * Strictly isolated from MCP tools and Gemini — no retries, 10s timeouts,
 * and never logs or exposes the app token or user note.
 */
class TabletMoodClient {
  companion object {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private const val NOT_INSTALLED_MESSAGE = "Mood control isn't installed on the tablet yet"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."

    fun buildEmotionUrl(rawServerUrl: String): Result<String> {
      return TabletMcpClient.validateServerUrl(rawServerUrl).map { cleanMcpUrl ->
        cleanMcpUrl.removeSuffix("/app/mcp") + "/app/emotion"
      }
    }

    private fun round2(value: Double): Double {
      if (!value.isFinite()) return 0.0
      val rounded = Math.round(value * 100.0) / 100.0
      return if (rounded == 0.0) 0.0 else rounded
    }
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getEmotion(
    serverUrl: String,
    appToken: String,
  ): MoodResult =
    withContext(Dispatchers.IO) {
      val emotionUrl =
        buildEmotionUrl(serverUrl).getOrElse { err ->
          return@withContext MoodResult.Failure(
            message = err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) {
        return@withContext MoodResult.Failure(message = "Sign in first")
      }

      val request =
        Request.Builder()
          .url(emotionUrl)
          .get()
          .header("Authorization", "Bearer $cleanToken")
          .build()

      executeRequest(request = request, isPost = false, cleanToken = cleanToken)
    }

  suspend fun postEmotion(
    serverUrl: String,
    appToken: String,
    isSet: Boolean,
    v: Double,
    a: Double,
    d: Double,
    note: String,
  ): MoodResult =
    withContext(Dispatchers.IO) {
      val emotionUrl =
        buildEmotionUrl(serverUrl).getOrElse { err ->
          return@withContext MoodResult.Failure(
            message = err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) {
        return@withContext MoodResult.Failure(message = "Sign in first")
      }

      val payload =
        JSONObject().apply {
          if (isSet) {
            put(
              "set",
              JSONObject().apply {
                put("v", round2(v))
                put("a", round2(a))
                put("d", round2(d))
              },
            )
          } else {
            put(
              "nudge",
              JSONObject().apply {
                put("dv", round2(v))
                put("da", round2(a))
                put("dd", round2(d))
              },
            )
          }
          val trimmedNote = note.trim()
          if (trimmedNote.isNotEmpty()) {
            put("note", trimmedNote)
          }
        }

      val reqBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
      val request =
        Request.Builder()
          .url(emotionUrl)
          .post(reqBody)
          .header("Authorization", "Bearer $cleanToken")
          .header("Content-Type", "application/json")
          .build()

      executeRequest(request = request, isPost = true, cleanToken = cleanToken)
    }

  private fun executeRequest(
    request: Request,
    isPost: Boolean,
    cleanToken: String,
  ): MoodResult {
    return try {
      httpClient.newCall(request).execute().use { resp ->
        val statusCode = resp.code
        // Check HTTP status FIRST before attempting any JSON parse.
        when (statusCode) {
          401 -> {
            MoodResult.Failure(
              message = "Signed out, sign in again",
              errorCode = "http_401",
            )
          }
          403 -> {
            val rawBody = resp.body?.string().orEmpty()
            val parsed = parseJsonObjectOrNull(rawBody)
            val desc = sanitize(parsed?.optString("error_description", "").orEmpty().trim(), cleanToken)
            MoodResult.Failure(
              message = desc.ifBlank { WRONG_DOOR_FALLBACK },
              errorCode = "http_403",
            )
          }
          404,
          405 -> {
            MoodResult.Failure(
              message = NOT_INSTALLED_MESSAGE,
              errorCode = "http_$statusCode",
            )
          }
          400,
          413,
          500 -> {
            val rawBody = resp.body?.string().orEmpty()
            val parsed = parseJsonObjectOrNull(rawBody)
              ?: return MoodResult.Failure(
                message = NOT_INSTALLED_MESSAGE,
                errorCode = "http_$statusCode",
              )
            val errorText = sanitize(parsed.optString("error", "").trim(), cleanToken)
            val codeText = parsed.optString("code", "").trim()
            MoodResult.Failure(
              message = errorText.ifBlank { "HTTP $statusCode" },
              errorCode = codeText.ifBlank { "http_$statusCode" },
            )
          }
          200 -> {
            val rawBody = resp.body?.string().orEmpty()
            val parsed = parseJsonObjectOrNull(rawBody)
              ?: return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
            if (!parsed.optBoolean("ok", false)) {
              return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
            }
            if (isPost) {
              val newState = parseMoodState(parsed.optJSONObject("new"))
                ?: return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
              val clampedArr = parsed.optJSONArray("clamped")
              val clampedList =
                buildList {
                  if (clampedArr != null) {
                    for (i in 0 until clampedArr.length()) {
                      val axis = clampedArr.optString(i, "").trim()
                      if (axis.isNotEmpty()) add(axis)
                    }
                  }
                }
              MoodResult.Success(state = newState, clamped = clampedList)
            } else {
              val state = parseMoodState(parsed.optJSONObject("state"))
                ?: return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
              MoodResult.Success(state = state)
            }
          }
          else -> {
            val rawBody = resp.body?.string().orEmpty()
            if (rawBody.isNotBlank() && parseJsonObjectOrNull(rawBody) == null) {
              MoodResult.Failure(
                message = NOT_INSTALLED_MESSAGE,
                errorCode = "http_$statusCode",
              )
            } else {
              MoodResult.Failure(
                message = "HTTP $statusCode",
                errorCode = "http_$statusCode",
              )
            }
          }
        }
      }
    } catch (_: SocketTimeoutException) {
      MoodResult.Failure(message = "Tablet didn't answer", errorCode = "timeout")
    } catch (_: InterruptedIOException) {
      MoodResult.Failure(message = "Tablet didn't answer", errorCode = "timeout")
    } catch (_: IOException) {
      MoodResult.Failure(message = "Couldn't reach the tablet", errorCode = "network_error")
    }
  }

  private fun parseJsonObjectOrNull(raw: String): JSONObject? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return null
    return try {
      JSONObject(trimmed)
    } catch (_: Exception) {
      null
    }
  }

  private fun parseMoodState(obj: JSONObject?): MoodState? {
    if (obj == null) return null
    if (!obj.has("v") || !obj.has("a") || !obj.has("d")) return null
    val v = obj.optDouble("v", Double.NaN)
    val a = obj.optDouble("a", Double.NaN)
    val d = obj.optDouble("d", Double.NaN)
    if (!v.isFinite() || !a.isFinite() || !d.isFinite()) return null
    val updated = obj.optString("updated", "")
    val label = obj.optString("label", "")
    val intensity = obj.optString("intensity", "")
    val magnitude = obj.optDouble("magnitude", 0.0).let { if (it.isFinite()) it else 0.0 }
    return MoodState(
      v = v,
      a = a,
      d = d,
      updated = updated,
      label = label,
      intensity = intensity,
      magnitude = magnitude,
    )
  }

  private fun sanitize(raw: String, cleanToken: String): String {
    return if (cleanToken.length >= 4) raw.replace(cleanToken, "[REDACTED]") else raw
  }
}
