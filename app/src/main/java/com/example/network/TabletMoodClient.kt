package com.example.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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

data class MoodManualInfo(
  val tag: String,
  val since: String = "",
  val at: String = "",
  val note: String = "",
)

data class MoodLayerEntry(
  val v: Double,
  val a: Double,
  val d: Double,
  val label: String,
  val intensity: String,
  val manual: MoodManualInfo? = null,
)

data class MoodLayersSnapshot(
  val emotion: MoodLayerEntry? = null,
  val mood: MoodLayerEntry? = null,
  val baseline: MoodLayerEntry? = null,
)

data class MoodDialMeta(
  val movableLayers: Set<String> = setOf("emotion", "mood", "baseline"),
  val lockedLayers: Set<String> = setOf("mood", "baseline"),
  val tagsByLayer: Map<String, List<String>> =
    mapOf(
      "emotion" to emptyList(),
      "mood" to listOf("test"),
      "baseline" to listOf("test", "calibration"),
    ),
) {
  fun isMovable(layer: String): Boolean = layer.lowercase() in movableLayers

  fun requiresLock(layer: String): Boolean = layer.lowercase() in lockedLayers

  fun allowedTags(layer: String): List<String> = tagsByLayer[layer.lowercase()].orEmpty()
}

data class MoodLockInfo(
  val locked: Boolean = true,
  val secondsLeft: Int = 0,
  val layers: List<String> = listOf("mood", "baseline"),
)

sealed class MoodResult {
  data class Success(
    val state: MoodState,
    val clamped: List<String> = emptyList(),
    val layers: MoodLayersSnapshot? = null,
    val dial: MoodDialMeta? = null,
    val lock: MoodLockInfo? = null,
  ) : MoodResult()

  data class Failure(
    val message: String,
    val errorCode: String? = null,
  ) : MoodResult()
}

sealed class MoodPostCommand {
  data object Unlock : MoodPostCommand()

  data object Lock : MoodPostCommand()

  data class EndTest(val layer: String) : MoodPostCommand()

  data class SetLayer(
    val v: Double,
    val a: Double,
    val d: Double,
    val layer: String,
    val tag: String,
    val note: String = "",
  ) : MoodPostCommand()

  data class NudgeLayer(
    val dv: Double,
    val da: Double,
    val dd: Double,
    val layer: String,
    val tag: String,
    val note: String = "",
  ) : MoodPostCommand()
}

sealed class MoodCommandResult {
  data class Success(
    val newState: MoodState? = null,
    val clamped: List<String> = emptyList(),
    val layers: MoodLayersSnapshot? = null,
    val dial: MoodDialMeta? = null,
    val lock: MoodLockInfo? = null,
  ) : MoodCommandResult()

  data class Failure(
    val message: String,
    val errorCode: String? = null,
  ) : MoodCommandResult()
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

    internal fun buildCommandJson(command: MoodPostCommand): JSONObject {
      return when (command) {
        MoodPostCommand.Unlock -> JSONObject().apply { put("unlock", true) }
        MoodPostCommand.Lock -> JSONObject().apply { put("lock", true) }
        is MoodPostCommand.EndTest -> JSONObject().apply { put("end_test", command.layer) }
        is MoodPostCommand.SetLayer ->
          JSONObject().apply {
            put(
              "set",
              JSONObject().apply {
                put("v", round2(command.v))
                put("a", round2(command.a))
                put("d", round2(command.d))
              },
            )
            put("layer", command.layer)
            put("tag", command.tag)
            val trimmedNote = command.note.trim()
            if (trimmedNote.isNotEmpty()) {
              put("note", trimmedNote)
            }
          }
        is MoodPostCommand.NudgeLayer ->
          JSONObject().apply {
            put(
              "nudge",
              JSONObject().apply {
                put("dv", round2(command.dv))
                put("da", round2(command.da))
                put("dd", round2(command.dd))
              },
            )
            put("layer", command.layer)
            put("tag", command.tag)
            val trimmedNote = command.note.trim()
            if (trimmedNote.isNotEmpty()) {
              put("note", trimmedNote)
            }
          }
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

      executeMoodRequest(request = request, isPost = false, cleanToken = cleanToken)
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

      executeMoodRequest(request = request, isPost = true, cleanToken = cleanToken)
    }

  suspend fun postCommand(
    serverUrl: String,
    appToken: String,
    command: MoodPostCommand,
  ): MoodCommandResult {
    return postJson(serverUrl = serverUrl, appToken = appToken, payload = buildCommandJson(command))
  }

  suspend fun postJson(
    serverUrl: String,
    appToken: String,
    payload: JSONObject,
  ): MoodCommandResult =
    withContext(Dispatchers.IO) {
      val emotionUrl =
        buildEmotionUrl(serverUrl).getOrElse { err ->
          return@withContext MoodCommandResult.Failure(
            message = err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) {
        return@withContext MoodCommandResult.Failure(message = "Sign in first")
      }

      val reqBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
      val request =
        Request.Builder()
          .url(emotionUrl)
          .post(reqBody)
          .header("Authorization", "Bearer $cleanToken")
          .header("Content-Type", "application/json")
          .build()

      executeGeneralPostRequest(request = request, cleanToken = cleanToken)
    }

  private fun executeMoodRequest(
    request: Request,
    isPost: Boolean,
    cleanToken: String,
  ): MoodResult {
    return try {
      httpClient.newCall(request).execute().use { resp ->
        val statusCode = resp.code
        if (statusCode != 200) {
          val rawBody = resp.body?.string().orEmpty()
          val err = parseNon200Failure(statusCode, rawBody, cleanToken)
          return MoodResult.Failure(message = err.first, errorCode = err.second)
        }

        val rawBody = resp.body?.string().orEmpty()
        val parsed =
          parseJsonObjectOrNull(rawBody)
            ?: return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
        if (!parsed.optBoolean("ok", false)) {
          val explicitErr = extractOkFalseError(parsed, cleanToken, statusCode)
          if (explicitErr != null) {
            return MoodResult.Failure(message = explicitErr.first, errorCode = explicitErr.second)
          }
          return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
        }

        val layers = parseLayersSnapshot(parsed.optJSONObject("layers"), cleanToken)
        val dial = parseDialMeta(parsed.optJSONObject("dial"))
        val lock = parseLockInfo(parsed.optJSONObject("lock"))

        if (isPost) {
          val newState =
            parseMoodState(parsed.optJSONObject("new"))
              ?: return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
          val clampedList = parseStringList(parsed.optJSONArray("clamped"))
          MoodResult.Success(
            state = newState,
            clamped = clampedList,
            layers = layers,
            dial = dial,
            lock = lock,
          )
        } else {
          val state =
            parseMoodState(parsed.optJSONObject("state"))
              ?: layers?.emotion?.let {
                MoodState(
                  v = it.v,
                  a = it.a,
                  d = it.d,
                  updated = "",
                  label = it.label,
                  intensity = it.intensity,
                  magnitude = 0.0,
                )
              }
              ?: return MoodResult.Failure(message = NOT_INSTALLED_MESSAGE)
          MoodResult.Success(
            state = state,
            layers = layers,
            dial = dial,
            lock = lock,
          )
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

  private fun executeGeneralPostRequest(
    request: Request,
    cleanToken: String,
  ): MoodCommandResult {
    return try {
      httpClient.newCall(request).execute().use { resp ->
        val statusCode = resp.code
        if (statusCode != 200) {
          val rawBody = resp.body?.string().orEmpty()
          val err = parseNon200Failure(statusCode, rawBody, cleanToken)
          return MoodCommandResult.Failure(message = err.first, errorCode = err.second)
        }

        val rawBody = resp.body?.string().orEmpty()
        val parsed =
          parseJsonObjectOrNull(rawBody)
            ?: return MoodCommandResult.Failure(message = NOT_INSTALLED_MESSAGE)
        if (!parsed.optBoolean("ok", false)) {
          val explicitErr = extractOkFalseError(parsed, cleanToken, statusCode)
          if (explicitErr != null) {
            return MoodCommandResult.Failure(
              message = explicitErr.first,
              errorCode = explicitErr.second,
            )
          }
          return MoodCommandResult.Failure(message = NOT_INSTALLED_MESSAGE)
        }

        val newState =
          parseMoodState(parsed.optJSONObject("new"))
            ?: parseMoodState(parsed.optJSONObject("state"))
        val clampedList = parseStringList(parsed.optJSONArray("clamped"))
        val layers = parseLayersSnapshot(parsed.optJSONObject("layers"), cleanToken)
        val dial = parseDialMeta(parsed.optJSONObject("dial"))
        val lock = parseLockInfo(parsed.optJSONObject("lock"))

        MoodCommandResult.Success(
          newState = newState,
          clamped = clampedList,
          layers = layers,
          dial = dial,
          lock = lock,
        )
      }
    } catch (_: SocketTimeoutException) {
      MoodCommandResult.Failure(message = "Tablet didn't answer", errorCode = "timeout")
    } catch (_: InterruptedIOException) {
      MoodCommandResult.Failure(message = "Tablet didn't answer", errorCode = "timeout")
    } catch (_: IOException) {
      MoodCommandResult.Failure(message = "Couldn't reach the tablet", errorCode = "network_error")
    }
  }

  /**
   * Error handling rules:
   * - 401 stays as now (sign out: "Signed out, sign in again", "http_401").
   * - 404 and 405 stay "not installed".
   * - Every other non-200 status (including 403, 409, 423, 400, 413, 500):
   *   if the body is JSON with "ok": false and an "error" field, return that error text and its "code".
   *   Only if it is not, keep the current behavior for that status (403 still reads error_description
   *   as "wrong door" and never signs out).
   */
  private fun parseNon200Failure(
    statusCode: Int,
    rawBody: String,
    cleanToken: String,
  ): Pair<String, String> {
    if (statusCode == 401) {
      return "Signed out, sign in again" to "http_401"
    }
    if (statusCode == 404 || statusCode == 405) {
      return NOT_INSTALLED_MESSAGE to "http_$statusCode"
    }

    val parsed = parseJsonObjectOrNull(rawBody)
    val okFalseErr = extractOkFalseError(parsed, cleanToken, statusCode)
    if (okFalseErr != null) {
      return okFalseErr
    }

    return when (statusCode) {
      403 -> {
        val desc = sanitize(optStringOrEmpty(parsed, "error_description").trim(), cleanToken)
        desc.ifBlank { WRONG_DOOR_FALLBACK } to "http_403"
      }
      400,
      413,
      500 -> {
        if (parsed == null) {
          NOT_INSTALLED_MESSAGE to "http_$statusCode"
        } else {
          val errorText = sanitize(optStringOrEmpty(parsed, "error").trim(), cleanToken)
          val codeText = optStringOrEmpty(parsed, "code").trim()
          errorText.ifBlank { "HTTP $statusCode" } to codeText.ifBlank { "http_$statusCode" }
        }
      }
      else -> {
        if (rawBody.isNotBlank() && parsed == null) {
          NOT_INSTALLED_MESSAGE to "http_$statusCode"
        } else {
          "HTTP $statusCode" to "http_$statusCode"
        }
      }
    }
  }

  private fun extractOkFalseError(
    parsed: JSONObject?,
    cleanToken: String,
    statusCode: Int,
  ): Pair<String, String>? {
    if (parsed == null) return null
    if (!parsed.has("ok") || parsed.isNull("ok")) return null
    val okVal = parsed.opt("ok")
    if (okVal != false) return null
    if (!parsed.has("error") || parsed.isNull("error")) return null
    val errText = sanitize(optStringOrEmpty(parsed, "error").trim(), cleanToken)
    if (errText.isEmpty()) return null
    val codeText = optStringOrEmpty(parsed, "code").trim()
    return errText to codeText.ifBlank { "http_$statusCode" }
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
    val updated = optStringOrEmpty(obj, "updated")
    val label = optStringOrEmpty(obj, "label")
    val intensity = optStringOrEmpty(obj, "intensity")
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

  private fun parseLayersSnapshot(
    layersObj: JSONObject?,
    cleanToken: String,
  ): MoodLayersSnapshot? {
    if (layersObj == null) return null
    return MoodLayersSnapshot(
      emotion = parseLayerEntry(layersObj.optJSONObject("emotion"), cleanToken),
      mood = parseLayerEntry(layersObj.optJSONObject("mood"), cleanToken),
      baseline = parseLayerEntry(layersObj.optJSONObject("baseline"), cleanToken),
    )
  }

  private fun parseLayerEntry(
    obj: JSONObject?,
    cleanToken: String,
  ): MoodLayerEntry? {
    if (obj == null) return null
    val v = obj.optDouble("v", Double.NaN)
    val a = obj.optDouble("a", Double.NaN)
    val d = obj.optDouble("d", Double.NaN)
    if (!v.isFinite() || !a.isFinite() || !d.isFinite()) return null
    val label = optStringOrEmpty(obj, "label")
    val intensity = optStringOrEmpty(obj, "intensity")
    val manualObj = if (obj.isNull("manual")) null else obj.optJSONObject("manual")
    val manual =
      manualObj?.let { m ->
        val tag = optStringOrEmpty(m, "tag").trim()
        if (tag.isEmpty()) {
          null
        } else {
          MoodManualInfo(
            tag = tag,
            since = optStringOrEmpty(m, "since").trim(),
            at = optStringOrEmpty(m, "at").trim(),
            note = sanitize(optStringOrEmpty(m, "note").trim(), cleanToken),
          )
        }
      }
    return MoodLayerEntry(
      v = v,
      a = a,
      d = d,
      label = label,
      intensity = intensity,
      manual = manual,
    )
  }

  private fun parseDialMeta(dialObj: JSONObject?): MoodDialMeta? {
    if (dialObj == null) return null
    val movable = mutableSetOf("emotion", "mood", "baseline")
    val locked = mutableSetOf("mood", "baseline")
    val tagsMap =
      mutableMapOf(
        "emotion" to emptyList<String>(),
        "mood" to listOf("test"),
        "baseline" to listOf("test", "calibration"),
      )

    // Support top-level arrays/maps if present
    val topMovable =
      dialObj.optJSONArray("movable")
        ?: dialObj.optJSONArray("moves")
        ?: dialObj.optJSONArray("layers")
    if (topMovable != null) {
      movable.clear()
      parseStringList(topMovable).forEach { movable.add(it.lowercase()) }
    }

    val topLocked =
      dialObj.optJSONArray("needs_lock")
        ?: dialObj.optJSONArray("requires_lock")
        ?: dialObj.optJSONArray("locked_layers")
        ?: dialObj.optJSONArray("lock")
    if (topLocked != null) {
      locked.clear()
      parseStringList(topLocked).forEach { locked.add(it.lowercase()) }
    }

    val topTagsObj = dialObj.optJSONObject("tags")
    if (topTagsObj != null) {
      for (layerKey in listOf("emotion", "mood", "baseline")) {
        val arr = topTagsObj.optJSONArray(layerKey)
        if (arr != null) {
          tagsMap[layerKey] = parseStringList(arr)
        }
      }
    }

    // Also support per-layer descriptor objects: dial: { emotion: {...}, mood: {...}, baseline: {...} }
    for (layerKey in listOf("emotion", "mood", "baseline")) {
      val sub = dialObj.optJSONObject(layerKey) ?: continue
      if (sub.has("movable") || sub.has("moves") || sub.has("writable")) {
        val canMove =
          sub.optBoolean(
            "movable",
            sub.optBoolean("moves", sub.optBoolean("writable", true)),
          )
        if (canMove) movable.add(layerKey) else movable.remove(layerKey)
      }
      if (sub.has("needs_lock") || sub.has("requires_lock") || sub.has("locked")) {
        val reqLock =
          sub.optBoolean(
            "needs_lock",
            sub.optBoolean("requires_lock", sub.optBoolean("locked", layerKey != "emotion")),
          )
        if (reqLock) locked.add(layerKey) else locked.remove(layerKey)
      }
      val subTags = sub.optJSONArray("tags")
      if (subTags != null) {
        tagsMap[layerKey] = parseStringList(subTags)
      } else {
        val singleTag = optStringOrEmpty(sub, "tag").trim()
        if (singleTag.isNotEmpty()) {
          tagsMap[layerKey] = listOf(singleTag)
        }
      }
    }

    return MoodDialMeta(
      movableLayers = movable,
      lockedLayers = locked,
      tagsByLayer = tagsMap,
    )
  }

  private fun parseLockInfo(lockObj: JSONObject?): MoodLockInfo? {
    if (lockObj == null) return null
    val locked = lockObj.optBoolean("locked", true)
    val rawSeconds = lockObj.optDouble("seconds_left", 0.0)
    val secondsLeft =
      if (rawSeconds.isFinite() && rawSeconds > 0.0) {
        ceil(rawSeconds).toInt()
      } else {
        lockObj.optInt("seconds_left", 0).coerceAtLeast(0)
      }
    val layersArr = lockObj.optJSONArray("layers")
    val layers =
      if (layersArr != null) {
        parseStringList(layersArr)
      } else {
        listOf("mood", "baseline")
      }
    return MoodLockInfo(
      locked = locked,
      secondsLeft = if (locked) 0 else secondsLeft,
      layers = layers,
    )
  }

  private fun parseStringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        if (!arr.isNull(i)) {
          val v = arr.optString(i, "").trim()
          if (v.isNotEmpty()) add(v)
        }
      }
    }
  }

  private fun optStringOrEmpty(obj: JSONObject?, key: String): String {
    if (obj == null || obj.isNull(key)) return ""
    return obj.optString(key, "")
  }

  private fun sanitize(raw: String, cleanToken: String): String {
    return if (cleanToken.length >= 4) raw.replace(cleanToken, "[REDACTED]") else raw
  }
}
