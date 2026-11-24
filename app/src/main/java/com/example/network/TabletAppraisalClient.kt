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

// Step 3.9: the client for Layer 3 on the tablet (/app/appraisal): what just happened and how far it would move her
// (shadow: her real mood isn't changed), and the shadow rewording of her finished replies. It also sends the tablet a
// copy of each finished typed reply of hers (/app/appraisal/reply), after you've seen it, so the rewording can be
// tried quietly. Same door and rules as /app/style: plain HTTP with the app token, never an MCP tool, so Gemini never
// sees it. No retries, 10 s timeouts, never logs or shows the app token. The adjustable values on /app/appraisal come
// back in /app/progress's shape: TabletProgressClient("/app/appraisal") reads and saves those, so the small per-step
// card is the same one Layers 5 to 8 use.

data class AppraisalPush(
  val at: Long,
  /** e.g. "you thanked her (strength 1)" */
  val events: List<String>,
  val before: String,
  val after: String,
  val limited: Boolean,
)

data class AppraisalSkip(val at: Long, val why: String)

data class RewordSample(
  val at: Long,
  val plain: String,
  val reworded: String,
  val outcome: String,
  val why: String,
  val style: String,
)

data class AppraisalSnapshot(
  val note: String,
  val wouldBe: String,
  val wouldBeCapped: Boolean,
  val realMood: String,
  val pushes: List<AppraisalPush>,
  /** event words -> times in the last 14 days */
  val eventCounts: Map<String, Int>,
  val skips: List<AppraisalSkip>,
  val caution: List<String>,
  val rewordNote: String,
  val outcomes: Map<String, Int>,
  /** 0..1, or null before any reply was tried */
  val passRate: Double?,
  val medianWaitMs: Long?,
  val slowestWaitMs: Long?,
  val keptReplies: Int,
  val samples: List<RewordSample>,
  val waitsOn: List<String>,
  val problems: List<String>,
)

sealed class AppraisalResult {
  data class Success(val snapshot: AppraisalSnapshot?) : AppraisalResult()

  data class Failure(val message: String, val errorCode: String? = null) : AppraisalResult()
}

class TabletAppraisalClient {
  companion object {
    private const val NOT_INSTALLED_MESSAGE = "Layer 3 isn't installed on the tablet yet"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    /** The tablet takes up to 16 KB; her text is cut well under that. */
    const val TEXT_MAX = 6000

    fun buildUrl(rawServerUrl: String, sub: String = ""): Result<String> =
      TabletMcpClient.validateServerUrl(rawServerUrl).map { it.removeSuffix("/app/mcp") + "/app/appraisal" + sub }

    private fun str(o: JSONObject?, key: String): String =
      if (o == null || !o.has(key) || o.isNull(key)) "" else o.optString(key, "")

    private fun strings(arr: JSONArray?): List<String> = buildList {
      if (arr == null) return@buildList
      for (i in 0 until arr.length()) arr.optString(i, "").takeIf { it.isNotBlank() }?.let { add(it) }
    }

    private fun objects(arr: JSONArray?): List<JSONObject> = buildList {
      if (arr == null) return@buildList
      for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(it) }
    }

    private fun ms(o: JSONObject, key: String): Long = (o.optDouble(key, 0.0) * 1000).toLong()

    private fun optLong(o: JSONObject?, key: String): Long? =
      if (o == null || !o.has(key) || o.isNull(key)) null else o.optLong(key)

    /** The body of one reply copy. Text only, no names or chat titles. */
    fun replyBody(messageId: String, text: String, atMs: Long, toolOnly: Boolean): JSONObject =
      JSONObject()
        .put("message_id", messageId.take(80))
        .put("text", text.take(TEXT_MAX))
        .put("at", atMs)
        .put("tool_only", toolOnly)

    /** GET /app/appraisal's reply -> what the card draws. Unknown fields are ignored. */
    fun parseSnapshot(obj: JSONObject): AppraisalSnapshot {
      val words = buildMap {
        for (e in objects(obj.optJSONArray("events"))) put(e.optString("name"), e.optString("words"))
      }
      fun eventWords(name: String) = words[name]?.takeIf { it.isNotBlank() } ?: name.replace('_', ' ')
      val wouldBe = if (obj.isNull("would_be")) null else obj.optJSONObject("would_be")
      val pushes = objects(obj.optJSONArray("recent_pushes")).map { p ->
        AppraisalPush(
          at = ms(p, "at"),
          events = objects(p.optJSONArray("events")).map { "${eventWords(it.optString("name"))} (strength ${it.optInt("strength", 1)})" },
          before = str(p, "before"),
          after = str(p, "after"),
          limited = p.optBoolean("limited", false),
        )
      }
      val counts = buildMap {
        val c = obj.optJSONObject("event_counts_14d") ?: JSONObject()
        c.keys().forEach { put(eventWords(it), c.optInt(it, 0)) }
      }
      val r = obj.optJSONObject("rewording")
      val outcomes = buildMap {
        val c = r?.optJSONObject("outcomes_14d") ?: JSONObject()
        c.keys().forEach { put(it, c.optInt(it, 0)) }
      }
      val wait = r?.optJSONObject("added_wait_ms")
      return AppraisalSnapshot(
        note = str(obj, "note"),
        wouldBe = str(wouldBe, "words").ifBlank { "not started" },
        wouldBeCapped = wouldBe?.optBoolean("capped", false) ?: false,
        realMood = str(obj, "real_mood").ifBlank { "unknown" },
        pushes = pushes.reversed(),
        eventCounts = counts,
        skips = objects(obj.optJSONArray("skips")).map { AppraisalSkip(ms(it, "at"), str(it, "why")) }.reversed(),
        caution = strings(obj.optJSONArray("caution")),
        rewordNote = str(r, "note"),
        outcomes = outcomes,
        passRate = if (r == null || r.isNull("pass_rate")) null else r.optDouble("pass_rate"),
        medianWaitMs = optLong(wait, "median"),
        slowestWaitMs = optLong(wait, "slowest"),
        keptReplies = r?.optInt("kept_replies", 0) ?: 0,
        samples = objects(r?.optJSONArray("spot_check")).map {
          RewordSample(ms(it, "at"), str(it, "plain"), str(it, "reworded"), str(it, "outcome"), str(it, "why"), str(it, "style"))
        },
        waitsOn = strings(obj.optJSONArray("waits_on")),
        problems = strings(obj.optJSONArray("problems")),
      )
    }
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getAppraisal(serverUrl: String, appToken: String): AppraisalResult = call(serverUrl, appToken, "", null)

  /**
   * A copy of one finished typed reply of hers, sent after you've seen it. The tablet answers at once and tries the
   * shadow rewording in the background; her reply on your screen never changes.
   */
  suspend fun sendReply(serverUrl: String, appToken: String, messageId: String, text: String, atMs: Long, toolOnly: Boolean): AppraisalResult =
    call(serverUrl, appToken, "/reply", replyBody(messageId, text, atMs, toolOnly))

  private suspend fun call(serverUrl: String, appToken: String, sub: String, body: JSONObject?): AppraisalResult =
    withContext(Dispatchers.IO) {
      val url =
        buildUrl(serverUrl, sub).getOrElse { err ->
          return@withContext AppraisalResult.Failure(err.message ?: "Server URL must start with https:// and end in /app/mcp")
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext AppraisalResult.Failure("Sign in first")
      val builder = Request.Builder().url(url).header("Authorization", "Bearer $cleanToken")
      val request =
        if (body == null) builder.get().build()
        else builder.post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).header("Content-Type", "application/json").build()
      try {
        httpClient.newCall(request).execute().use { resp ->
          val raw = resp.body?.string().orEmpty()
          val parsed = parse(raw)
          if (resp.code != 200) {
            val (msg, code) = failure(resp.code, parsed, cleanToken)
            return@withContext AppraisalResult.Failure(msg, code)
          }
          if (parsed == null || !parsed.optBoolean("ok", false)) {
            return@withContext AppraisalResult.Failure(NOT_INSTALLED_MESSAGE)
          }
          AppraisalResult.Success(if (body == null) parseSnapshot(parsed) else null)
        }
      } catch (_: SocketTimeoutException) {
        AppraisalResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: InterruptedIOException) {
        AppraisalResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: IOException) {
        AppraisalResult.Failure("Couldn't reach the tablet", "network_error")
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
