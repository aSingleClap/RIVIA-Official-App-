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
import org.json.JSONArray
import org.json.JSONObject

// Step 5.7: the client for /app/progress on the tablet, where Jordan adjusts Step 5's values.
// Same door and rules as the mood dial (TabletMoodClient): plain HTTP with the app token, never an MCP tool,
// so Gemini never sees it. No retries, 10 s timeouts, never logs or shows the app token.
//
// The tablet lists every adjustable value grouped by layer and step ("piece"), each with its words, type, hard range,
// the code's value and the value in use. Nothing here is specific to one step, so the same parsing and the same small
// card can serve a future screen of only adjustable values for every layer.

/**
 * One adjustable value. type: "switch", "int", "num", "money" (dollars; blank means no cap) or "limit" (a whole
 * number; blank means not set, used for each model's RPM, TPM and RPD).
 */
data class AdjustableSetting(
  /** Path the tablet knows it by, e.g. "scores.plan_held.weight" or "mood.caps.worried". */
  val path: String,
  val words: String,
  val type: String,
  val min: Double?,
  val max: Double?,
  val step: Double?,
  val unit: String,
  /** For a switch: on/off. Null for numbers. */
  val switchValue: Boolean?,
  val switchCode: Boolean?,
  /** For numbers: the value in use and the code's value. Null for a switch, or "no cap" for money. */
  val numberValue: Double?,
  val numberCode: Double?,
  /** True when Jordan's saved change is what's in use. */
  val changed: Boolean,
) {
  val isSwitch: Boolean get() = type == "switch"
  val isWhole: Boolean get() = type == "int" || type == "limit"
  val blankMeansNone: Boolean get() = type == "money" || type == "limit"
  /** What an empty box means, in the screen's words. */
  val blankWord: String get() = if (type == "limit") "not set" else "none"

  /** A slider when the range is small enough to drag; a number box otherwise (tokens, seconds, dollars). */
  val usesSlider: Boolean
    get() {
      if (isSwitch || blankMeansNone || min == null || max == null || step == null || step <= 0.0) return false
      return (max - min) / step <= 200.0
    }
}

data class AdjustablePiece(
  val layer: Int,
  /** "5.2", "5.4", "5.5", "models" or "goals". */
  val piece: String,
  val title: String,
  val about: String,
  val settings: List<AdjustableSetting>,
  /** Plain lines on what stays fixed for this step. */
  val notes: List<String>,
)

data class ProgressLocked(val name: String, val why: String)

data class ProgressChange(val what: String, val piece: String, val old: String, val new: String)

data class ProgressEdit(
  val at: Long,
  val who: String,
  val action: String,
  val note: String,
  val changes: List<ProgressChange>,
)

data class AdjustableSnapshot(
  val pieces: List<AdjustablePiece>,
  val locked: List<ProgressLocked>,
  val problems: List<String>,
  val changed: Boolean,
  val history: List<ProgressEdit>,
) {
  fun piece(layer: Int, piece: String): AdjustablePiece? =
    pieces.firstOrNull { it.layer == layer && it.piece == piece }
}

sealed class ProgressResult {
  data class Success(
    val snapshot: AdjustableSnapshot,
    /** What this press changed (old and new). Empty on a GET or when nothing changed. */
    val edited: List<ProgressChange> = emptyList(),
  ) : ProgressResult()

  data class Failure(val message: String, val errorCode: String? = null) : ProgressResult()
}

/** What a card has switched or typed, keyed by path. Slider moves are kept as text too. */
data class AdjustableDraft(
  val switches: Map<String, Boolean> = emptyMap(),
  val texts: Map<String, String> = emptyMap(),
)

/** path: "/app/progress" for Layer 5, "/app/trust" for Layer 6 (Step 6.9). Both answer in the same shape. */
class TabletProgressClient(private val path: String = "/app/progress") {
  companion object {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private const val NOT_INSTALLED_MESSAGE = "These settings aren't installed on the tablet yet"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."

    fun buildProgressUrl(rawServerUrl: String, path: String = "/app/progress"): Result<String> {
      return TabletMcpClient.validateServerUrl(rawServerUrl).map { cleanMcpUrl ->
        cleanMcpUrl.removeSuffix("/app/mcp") + path
      }
    }

    /**
     * 5.2 only: the weights of the scores that are switched on, after the draft, for the card's "Total" line.
     * Null when a weight box doesn't hold a whole number, or the step has no weights.
     */
    fun weightTotal(piece: AdjustablePiece, draft: AdjustableDraft): Int? {
      var total = 0
      var any = false
      for (s in piece.settings) {
        if (!s.path.startsWith("scores.") || !s.path.endsWith(".weight")) continue
        any = true
        val name = s.path.removeSuffix(".weight")
        val onSetting = piece.settings.firstOrNull { it.path == "$name.on" }
        val on = draft.switches["$name.on"] ?: onSetting?.switchValue ?: true
        if (!on) continue
        val raw = draft.texts[s.path] ?: show(s.numberValue)
        total += raw.trim().toDoubleOrNull()?.takeIf { it == Math.floor(it) }?.toInt() ?: return null
      }
      return if (any) total else null
    }

    /**
     * Only this step's values that differ from what's in use, as POST /app/progress's "set" takes them:
     * {path: value}. Returns null with a reason when a box doesn't hold a number. Ranges, the 100% total and the
     * rest are checked again on the tablet, which has the final say.
     */
    fun buildChanges(piece: AdjustablePiece, draft: AdjustableDraft): Pair<JSONObject?, String?> {
      val out = JSONObject()
      for (s in piece.settings) {
        if (s.isSwitch) {
          val v = draft.switches[s.path]
          if (v != null && v != s.switchValue) out.put(s.path, v)
          continue
        }
        val raw = draft.texts[s.path] ?: continue
        val t = raw.trim()
        if (t.isEmpty()) {
          if (!s.blankMeansNone) return null to "${s.words}: needs a number"
          if (s.numberValue != null) out.put(s.path, JSONObject.NULL)
          continue
        }
        val d = t.toDoubleOrNull() ?: return null to "${s.words}: needs a number"
        if (d.isNaN() || d.isInfinite()) return null to "${s.words}: needs a number"
        if (s.isWhole && d != Math.floor(d)) return null to "${s.words}: needs a whole number"
        if (s.numberValue != null && d == s.numberValue) continue
        out.put(s.path, if (s.isWhole) d.toLong() else d)
      }
      return out to null
    }

    fun buildSetJson(changes: JSONObject, note: String = ""): JSONObject =
      JSONObject().apply {
        put("set", changes)
        val trimmed = note.trim()
        if (trimmed.isNotEmpty()) put("note", trimmed)
      }

    /** target: "piece:5.4" for one step, a setting's path for one value, or "all". */
    fun buildResetJson(target: String, note: String = ""): JSONObject =
      JSONObject().apply {
        put("reset", target)
        val trimmed = note.trim()
        if (trimmed.isNotEmpty()) put("note", trimmed)
      }

    /** The tablet's reply (GET or POST) as a snapshot, or null when it isn't the expected shape. */
    fun parseSnapshot(obj: JSONObject): AdjustableSnapshot? {
      val layers = obj.optJSONArray("layers") ?: return null
      val notes = obj.optJSONObject("notes") ?: JSONObject()
      val pieces = mutableListOf<AdjustablePiece>()
      for (i in 0 until layers.length()) {
        val l = layers.optJSONObject(i) ?: continue
        val layer = l.optInt("layer", 0)
        val arr = l.optJSONArray("pieces") ?: continue
        for (j in 0 until arr.length()) {
          val p = arr.optJSONObject(j) ?: continue
          val id = p.optString("piece", "")
          if (id.isBlank()) continue
          val rows = mutableListOf<AdjustableSetting>()
          val sArr = p.optJSONArray("settings") ?: JSONArray()
          for (k in 0 until sArr.length()) {
            parseSetting(sArr.optJSONObject(k) ?: continue)?.let { rows += it }
          }
          pieces +=
            AdjustablePiece(
              layer = layer,
              piece = id,
              title = p.optString("title", id),
              about = p.optString("about", ""),
              settings = rows,
              notes = stringList(notes.optJSONArray(id)),
            )
        }
      }
      if (pieces.isEmpty()) return null
      val locked = mutableListOf<ProgressLocked>()
      obj.optJSONArray("locked")?.let { arr ->
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          locked += ProgressLocked(o.optString("name", ""), o.optString("why", ""))
        }
      }
      return AdjustableSnapshot(
        pieces = pieces,
        locked = locked,
        problems = stringList(obj.optJSONArray("problems")),
        changed = obj.optBoolean("changed", false),
        history = parseHistory(obj.optJSONArray("history")),
      )
    }

    private fun parseSetting(o: JSONObject): AdjustableSetting? {
      val path = o.optString("path", "")
      val type = o.optString("type", "")
      if (path.isBlank() || type !in setOf("switch", "int", "num", "money", "limit")) return null
      val sw = type == "switch"
      return AdjustableSetting(
        path = path,
        words = o.optString("words", path),
        type = type,
        min = optDouble(o, "min"),
        max = optDouble(o, "max"),
        step = optDouble(o, "step"),
        unit = if (o.isNull("unit")) "" else o.optString("unit", ""),
        switchValue = if (sw) o.optBoolean("value", false) else null,
        switchCode = if (sw) o.optBoolean("code", false) else null,
        numberValue = if (sw) null else optDouble(o, "value"),
        numberCode = if (sw) null else optDouble(o, "code"),
        changed = o.optBoolean("changed", false),
      )
    }

    fun parseChanges(arr: JSONArray?): List<ProgressChange> {
      if (arr == null) return emptyList()
      return buildList {
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          add(
            ProgressChange(
              o.optString("what", ""),
              o.optString("piece", ""),
              show(o.opt("old")),
              show(o.opt("new")),
            )
          )
        }
      }
    }

    private fun parseHistory(arr: JSONArray?): List<ProgressEdit> {
      if (arr == null) return emptyList()
      return buildList {
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          add(
            ProgressEdit(
              at = o.optLong("at", 0L),
              who = o.optString("who", ""),
              action = o.optString("action", ""),
              note = if (o.isNull("note")) "" else o.optString("note", ""),
              changes = parseChanges(o.optJSONArray("changes")),
            )
          )
        }
      }
    }

    /** A value as the screen shows it: "none" for no cap (see blankWord for a model limit), whole numbers
     * without ".0", at most 3 decimals. */
    fun show(v: Any?): String =
      when (v) {
        null,
        JSONObject.NULL -> "none"
        is Boolean -> if (v) "on" else "off"
        is Number -> {
          val d = v.toDouble()
          if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString()
          else String.format(java.util.Locale.US, "%.3f", d).trimEnd('0').trimEnd('.')
        }
        else -> v.toString()
      }

    /** Snap a slider position to the setting's step and range. */
    fun snap(s: AdjustableSetting, raw: Double): Double {
      val lo = s.min ?: return raw
      val hi = s.max ?: return raw
      val st = s.step ?: return raw.coerceIn(lo, hi)
      val n = Math.round((raw - lo) / st)
      return (lo + n * st).coerceIn(lo, hi).let { Math.round(it * 1000.0) / 1000.0 }
    }

    private fun optDouble(o: JSONObject, key: String): Double? =
      if (!o.has(key) || o.isNull(key)) null else o.optDouble(key).takeIf { !it.isNaN() }

    private fun stringList(arr: JSONArray?): List<String> {
      if (arr == null) return emptyList()
      return buildList {
        for (i in 0 until arr.length()) {
          val v = arr.optString(i, "").trim()
          if (v.isNotEmpty()) add(v)
        }
      }
    }
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getProgress(serverUrl: String, appToken: String): ProgressResult =
    withContext(Dispatchers.IO) {
      val url =
        buildProgressUrl(serverUrl, path).getOrElse { err ->
          return@withContext ProgressResult.Failure(
            message = err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext ProgressResult.Failure(message = "Sign in first")
      val request = Request.Builder().url(url).get().header("Authorization", "Bearer $cleanToken").build()
      execute(request, cleanToken)
    }

  suspend fun post(serverUrl: String, appToken: String, payload: JSONObject): ProgressResult =
    withContext(Dispatchers.IO) {
      val url =
        buildProgressUrl(serverUrl, path).getOrElse { err ->
          return@withContext ProgressResult.Failure(
            message = err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext ProgressResult.Failure(message = "Sign in first")
      val request =
        Request.Builder()
          .url(url)
          .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
          .header("Authorization", "Bearer $cleanToken")
          .header("Content-Type", "application/json")
          .build()
      execute(request, cleanToken)
    }

  private fun execute(request: Request, cleanToken: String): ProgressResult {
    return try {
      httpClient.newCall(request).execute().use { resp ->
        val raw = resp.body?.string().orEmpty()
        val parsed = parseJsonObjectOrNull(raw)
        if (resp.code != 200) {
          val (msg, code) = failure(resp.code, parsed, cleanToken)
          return ProgressResult.Failure(message = msg, errorCode = code)
        }
        if (parsed == null || !parsed.optBoolean("ok", false)) {
          return ProgressResult.Failure(message = NOT_INSTALLED_MESSAGE)
        }
        val snap = parseSnapshot(parsed) ?: return ProgressResult.Failure(message = NOT_INSTALLED_MESSAGE)
        ProgressResult.Success(snapshot = snap, edited = parseChanges(parsed.optJSONArray("edited")))
      }
    } catch (_: SocketTimeoutException) {
      ProgressResult.Failure(message = "Tablet didn't answer", errorCode = "timeout")
    } catch (_: InterruptedIOException) {
      ProgressResult.Failure(message = "Tablet didn't answer", errorCode = "timeout")
    } catch (_: IOException) {
      ProgressResult.Failure(message = "Couldn't reach the tablet", errorCode = "network_error")
    }
  }

  /** Same rules as the mood dial: 401 signs out, 404/405 "not installed", the tablet's own reason otherwise. */
  private fun failure(status: Int, parsed: JSONObject?, cleanToken: String): Pair<String, String> {
    if (status == 401) return "Signed out, sign in again" to "http_401"
    if (status == 404 || status == 405) return NOT_INSTALLED_MESSAGE to "http_$status"
    if (parsed != null && parsed.opt("ok") == false && !parsed.isNull("error")) {
      val err = sanitize(parsed.optString("error", "").trim(), cleanToken)
      val code = parsed.optString("code", "").trim()
      if (err.isNotEmpty()) return err to code.ifBlank { "http_$status" }
    }
    if (status == 403) {
      val desc = if (parsed == null) "" else sanitize(parsed.optString("error_description", "").trim(), cleanToken)
      return desc.ifBlank { WRONG_DOOR_FALLBACK } to "http_403"
    }
    return "HTTP $status" to "http_$status"
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

  private fun sanitize(raw: String, cleanToken: String): String =
    if (cleanToken.length >= 4) raw.replace(cleanToken, "[REDACTED]") else raw
}
