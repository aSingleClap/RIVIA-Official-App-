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

// Step 6.9: the client for Layer 6 on the tablet (/app/trust): trust, your satisfaction,
// sure-by-area, the unlocks you switch by hand, the off switch, and thumbs on her messages.
// Same door and rules as the mood dial and /app/progress: plain HTTP with the app token, never an MCP tool, so
// Gemini never sees it. No retries, 10 s timeouts, never logs or shows the app token.
// The adjustable values on /app/trust come back in /app/progress's shape: TabletProgressClient("/app/trust") reads
// those, so the small per-step card is the same one Layer 5 uses.

data class TrustUnlock(
  val id: String,
  val words: String,
  /** "earning", "ready" (she'd bring it up), "active", "paused" (on but under a line) or "killed". */
  val state: String,
  val switchedOn: Boolean,
  val trustLine: Double?,
  val satisfactionLine: Double?,
  val daysLine: Int?,
)

data class TrustFlag(val area: String, val value: Double, val say: String)

data class TrustArea(val area: String, val value: Double, val words: String)

data class TrustDay(val day: String, val predictions: Int, val hits: Int, val herMisses: Int, val quiet: Boolean)

data class TrustAction(val at: Long, val kind: String, val what: String, val reason: String, val who: String)

data class TrustSnapshot(
  /** Null until Step 6 has judged a day (scheduling parked means none yet). */
  val trust: Double?,
  val trustStart: Double?,
  val trustDays: Int,
  val satisfaction: Double?,
  val satisfactionEvidence: Double,
  val areas: List<TrustArea>,
  val flags: List<TrustFlag>,
  val unlocks: List<TrustUnlock>,
  val killed: Boolean,
  val hardLimits: List<Pair<String, String>>,
  val days: List<TrustDay>,
  val actions: List<TrustAction>,
  /** The tablet's plain note when nothing is judged yet. */
  val note: String,
  val problems: List<String>,
  /** Your recent thumbs by message id (1 up, -1 down), so the buttons show what you pressed. */
  val thumbs: Map<String, Int> = emptyMap(),
)

sealed class TrustResult {
  data class Success(val snapshot: TrustSnapshot?, val message: String = "") : TrustResult()

  data class Failure(val message: String, val errorCode: String? = null) : TrustResult()
}

class TabletTrustClient {
  companion object {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private const val NOT_INSTALLED_MESSAGE = "Layer 6 isn't installed on the tablet yet"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."

    fun buildUrl(rawServerUrl: String, sub: String = ""): Result<String> =
      TabletMcpClient.validateServerUrl(rawServerUrl).map { it.removeSuffix("/app/mcp") + "/app/trust" + sub }

    private fun optDouble(o: JSONObject?, key: String): Double? =
      if (o == null || !o.has(key) || o.isNull(key)) null else o.optDouble(key).takeIf { !it.isNaN() }

    /** GET /app/trust's reply -> what the card draws. Unknown fields are ignored. */
    fun parseSnapshot(obj: JSONObject): TrustSnapshot {
      val state = obj.optJSONObject("state")
      val tr = state?.optJSONObject("trust")
      val sat = state?.optJSONObject("satisfaction")
      val areas = buildList {
        val conf = state?.optJSONObject("confidence")
        conf?.keys()?.forEach { k ->
          val c = conf.optJSONObject(k) ?: return@forEach
          add(TrustArea(k, c.optDouble("value", 1.0), c.optString("words", k)))
        }
      }.sortedBy { it.value }
      val flags = buildList {
        val arr = state?.optJSONArray("flags") ?: JSONArray()
        for (i in 0 until arr.length()) {
          val f = arr.optJSONObject(i) ?: continue
          add(TrustFlag(f.optString("area", ""), f.optDouble("value", 0.0), f.optString("say", "")))
        }
      }
      val unlocks = buildList {
        val u = obj.optJSONObject("unlocks") ?: JSONObject()
        u.keys().forEach { id ->
          val o = u.optJSONObject(id) ?: return@forEach
          val lines = o.optJSONObject("lines")
          add(
            TrustUnlock(
              id = id,
              words = o.optString("words", id),
              state = o.optString("state", "earning"),
              switchedOn = o.optBoolean("switched_on", false),
              trustLine = optDouble(lines, "trust"),
              satisfactionLine = optDouble(lines, "satisfaction"),
              daysLine = lines?.let { if (it.has("days")) it.optInt("days") else null },
            )
          )
        }
      }
      val hard = buildList {
        val h = obj.optJSONObject("hard_limits") ?: JSONObject()
        h.keys().forEach { add(it to h.optString(it, "")) }
      }
      val days = buildList {
        val arr = obj.optJSONArray("days") ?: JSONArray()
        for (i in 0 until arr.length()) {
          val d = arr.optJSONObject(i) ?: continue
          val s = d.optJSONObject("summary")
          add(
            TrustDay(
              day = d.optString("day", ""),
              predictions = d.optInt("predictions", 0),
              hits = s?.optInt("hits", 0) ?: 0,
              herMisses = s?.optJSONObject("misses")?.optInt("rivia", 0) ?: 0,
              quiet = d.optBoolean("quiet", false),
            )
          )
        }
      }
      val actions = buildList {
        val arr = obj.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until arr.length()) {
          val a = arr.optJSONObject(i) ?: continue
          add(
            TrustAction(
              a.optLong("at", 0L),
              a.optString("kind", ""),
              a.optString("what", ""),
              a.optString("reason", ""),
              a.optString("who", ""),
            )
          )
        }
      }
      val problems = buildList {
        val arr = obj.optJSONArray("problems") ?: JSONArray()
        for (i in 0 until arr.length()) arr.optString(i, "").takeIf { it.isNotBlank() }?.let { add(it) }
      }
      val thumbs = buildMap {
        val arr = obj.optJSONArray("thumbs") ?: JSONArray()
        for (i in 0 until arr.length()) {
          val t = arr.optJSONObject(i) ?: continue
          val id = if (t.isNull("message_id")) "" else t.optString("message_id", "")
          if (id.isNotBlank()) put(id, if (t.optDouble("value", 0.0) > 0) 1 else -1)
        }
      }
      return TrustSnapshot(
        trust = optDouble(tr, "value"),
        trustStart = optDouble(tr, "start"),
        trustDays = tr?.optInt("days", 0) ?: 0,
        satisfaction = optDouble(sat, "value"),
        satisfactionEvidence = optDouble(sat, "evidence") ?: 0.0,
        areas = areas,
        flags = flags,
        unlocks = unlocks,
        killed = obj.optJSONObject("kill")?.optBoolean("killed", false) ?: false,
        hardLimits = hard,
        days = days,
        actions = actions,
        note = if (obj.isNull("note")) "" else obj.optString("note", ""),
        problems = problems,
        thumbs = thumbs,
      )
    }

    /** The body for one thumb. value: 1 up, -1 down. Her text goes along only to pick the area (6.3). */
    fun feedbackBody(messageId: String, value: Int, text: String): JSONObject =
      JSONObject().put("value", value).put("message_id", messageId.take(80)).put("text", text.take(500))

    /**
     * A copy of a message you typed. Only your text goes, never a verdict: tablet code decides praise, correction or
     * neither, and keeps only what it heard. Her reply before it only picks the area for a correction.
     */
    fun wordsBody(
      messageId: String,
      text: String,
      replyText: String?,
      sentAtMs: Long? = null,
      replyAtMs: Long? = null,
    ): JSONObject =
      JSONObject().put("message_id", messageId.take(80)).put("text", text.take(4000)).also { b ->
        if (!replyText.isNullOrBlank()) b.put("reply_text", replyText.take(1500))
        // Step 7.1: when you sent it and when her reply before it finished, so the tablet can see how quickly you
        // answered. Times only; Step 7 keeps numbers, never your text.
        if (sentAtMs != null && sentAtMs > 0) b.put("sent_at", sentAtMs)
        if (replyAtMs != null && replyAtMs > 0) b.put("reply_at", replyAtMs)
      }
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getTrust(serverUrl: String, appToken: String): TrustResult = call(serverUrl, appToken, "", null)

  /** A thumbs up (1) or down (-1) on one of her messages. Pressing again replaces your earlier press. */
  suspend fun sendThumb(serverUrl: String, appToken: String, messageId: String, value: Int, text: String): TrustResult =
    call(serverUrl, appToken, "/feedback", feedbackBody(messageId, value, text))

  /** Your words, so "good plan" and "that's wrong" count. Chats go phone to Google and never pass the tablet. */
  suspend fun sendWords(
    serverUrl: String,
    appToken: String,
    messageId: String,
    text: String,
    replyText: String?,
    sentAtMs: Long? = null,
    replyAtMs: Long? = null,
  ): TrustResult = call(serverUrl, appToken, "/words", wordsBody(messageId, text, replyText, sentAtMs, replyAtMs))

  /** You switch one unlock on or off. Hard limits are refused by the tablet (403). */
  suspend fun setUnlock(serverUrl: String, appToken: String, id: String, on: Boolean): TrustResult =
    call(serverUrl, appToken, "/unlock", JSONObject().put("unlock", id).put("on", on))

  /** The off switch: every unlock off, trust starts over. */
  suspend fun setKill(serverUrl: String, appToken: String, kill: Boolean): TrustResult =
    call(serverUrl, appToken, "/kill", JSONObject().put("kill", kill))

  private suspend fun call(serverUrl: String, appToken: String, sub: String, body: JSONObject?): TrustResult =
    withContext(Dispatchers.IO) {
      val url =
        buildUrl(serverUrl, sub).getOrElse { err ->
          return@withContext TrustResult.Failure(err.message ?: "Server URL must start with https:// and end in /app/mcp")
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext TrustResult.Failure("Sign in first")
      val builder = Request.Builder().url(url).header("Authorization", "Bearer $cleanToken")
      val request =
        if (body == null) builder.get().build()
        else
          builder
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()
      try {
        httpClient.newCall(request).execute().use { resp ->
          val raw = resp.body?.string().orEmpty()
          val parsed = parse(raw)
          if (resp.code != 200) {
            val (msg, code) = failure(resp.code, parsed, cleanToken)
            return@withContext TrustResult.Failure(msg, code)
          }
          if (parsed == null || !parsed.optBoolean("ok", false)) {
            return@withContext TrustResult.Failure(NOT_INSTALLED_MESSAGE)
          }
          val snap = if (body == null) parseSnapshot(parsed) else null
          val msg = if (parsed.isNull("note")) "" else parsed.optString("note", "")
          TrustResult.Success(snap, msg)
        }
      } catch (_: SocketTimeoutException) {
        TrustResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: InterruptedIOException) {
        TrustResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: IOException) {
        TrustResult.Failure("Couldn't reach the tablet", "network_error")
      }
    }

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

  private fun parse(raw: String): JSONObject? {
    val t = raw.trim()
    if (!t.startsWith("{")) return null
    return try {
      JSONObject(t)
    } catch (_: Exception) {
      null
    }
  }

  private fun sanitize(raw: String, cleanToken: String): String =
    if (cleanToken.length >= 4) raw.replace(cleanToken, "[REDACTED]") else raw
}
