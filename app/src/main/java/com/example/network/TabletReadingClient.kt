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

// Step 7.11: the client for Layer 7 on the tablet (/app/reading): how she reads you against your own normal in each
// situation (before an HMC shift, after one, a day off, other), pattern guesses you confirm or reject, and the
// questions she would have asked (shadow: she doesn't ask yet).
// Same door and rules as /app/trust: plain HTTP with the app token, never an MCP tool, so Gemini never sees it.
// No retries, 10 s timeouts, never logs or shows the app token. The adjustable values on /app/reading come back in
// /app/progress's shape: TabletProgressClient("/app/reading") reads those, so the small per-step card is the same.

data class ReadingNormal(val measure: String, val words: String, val unit: String, val middle: Double?,
                         val enough: Boolean, val count: Int, val need: Int?)

data class ReadingSituation(val id: String, val words: String, val messages: Int, val enough: Boolean,
                            val normals: List<ReadingNormal>)

data class ReadingNowMeasure(val measure: String, val zone: String, val direction: String?)

data class ReadingPattern(val id: Int, val situation: String, val state: String, val words: String, val daysSeen: Int)

data class ReadingAsk(val at: Long, val question: String, val triggers: List<String>)

data class ReadingSnapshot(
  val note: String,
  val situations: List<ReadingSituation>,
  /** The situation your last few messages were in, or "" if none in the last few hours. */
  val nowSituation: String,
  val now: List<ReadingNowMeasure>,
  val patterns: List<ReadingPattern>,
  val wouldAsk: List<ReadingAsk>,
  val skipped: List<Pair<Long, String>>,
  val wouldAdjust: Int,
  val checksTotal: Int,
  val checksAgree: Int,
  val checksDisagree: Int,
  val wordList: String,
  val flashOn: Boolean,
  /** Today's Flash reads by status: ok, no_key, at_limit, failed, unusable, off, waiting. */
  val flashToday: Map<String, Int>,
  val calendarOk: Boolean?,
  val calendarNote: String,
  val wordingRules: List<String>,
  val leftOut: List<String>,
  val problems: List<String>,
)

sealed class ReadingResult {
  data class Success(val snapshot: ReadingSnapshot?, val message: String = "") : ReadingResult()

  data class Failure(val message: String, val errorCode: String? = null) : ReadingResult()
}

class TabletReadingClient {
  companion object {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private const val NOT_INSTALLED_MESSAGE = "Layer 7 isn't installed on the tablet yet"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."

    fun buildUrl(rawServerUrl: String, sub: String = ""): Result<String> =
      TabletMcpClient.validateServerUrl(rawServerUrl).map { it.removeSuffix("/app/mcp") + "/app/reading" + sub }

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

    /** GET /app/reading's reply -> what the card draws. Unknown fields are ignored. */
    fun parseSnapshot(obj: JSONObject): ReadingSnapshot {
      val situations = objects(obj.optJSONArray("situations")).map { s ->
        val n = s.optJSONObject("normal") ?: JSONObject()
        val normals = buildList {
          n.keys().forEach { k ->
            val m = n.optJSONObject(k) ?: return@forEach
            add(
              ReadingNormal(
                measure = k,
                words = m.optString("words", k),
                unit = m.optString("unit", ""),
                middle = if (m.isNull("middle")) null else m.optDouble("middle").takeIf { !it.isNaN() },
                enough = m.optBoolean("enough", false),
                count = m.optInt("count", 0),
                need = if (m.isNull("need") || !m.has("need")) null else m.optInt("need"),
              )
            )
          }
        }
        ReadingSituation(s.optString("id", ""), s.optString("words", ""), s.optInt("messages", 0),
                         s.optBoolean("enough", false), normals)
      }
      val nowObj = if (obj.isNull("now")) null else obj.optJSONObject("now")
      val now = buildList {
        val ms = nowObj?.optJSONObject("measures") ?: JSONObject()
        ms.keys().forEach { k ->
          val m = ms.optJSONObject(k) ?: return@forEach
          add(ReadingNowMeasure(k, m.optString("zone", "no_data"), str(m, "direction").ifBlank { null }))
        }
      }
      val patterns = objects(obj.optJSONArray("patterns")).map {
        ReadingPattern(it.optInt("id"), it.optString("situation"), it.optString("state", "proposed"),
                       it.optString("words"), it.optInt("days_seen", 0))
      }
      val asks = objects(obj.optJSONArray("would_ask")).map {
        ReadingAsk((it.optDouble("at", 0.0) * 1000).toLong(), it.optString("question"),
                   strings(it.optJSONArray("triggers")))
      }
      val skipped = objects(obj.optJSONArray("skipped_asks")).map {
        (it.optDouble("at", 0.0) * 1000).toLong() to it.optString("why")
      }
      val checks = obj.optJSONObject("checks")
      val scores = obj.optJSONObject("scores")
      val flash = scores?.optJSONObject("flash")
      val today = buildMap {
        val t = flash?.optJSONObject("today") ?: JSONObject()
        t.keys().forEach { put(it, t.optInt(it, 0)) }
      }
      val cal = if (obj.isNull("calendar")) null else obj.optJSONObject("calendar")
      return ReadingSnapshot(
        note = str(obj, "note"),
        situations = situations,
        nowSituation = str(nowObj, "situation"),
        now = now,
        patterns = patterns,
        wouldAsk = asks,
        skipped = skipped,
        wouldAdjust = obj.optJSONArray("would_adjust")?.length() ?: 0,
        checksTotal = checks?.optInt("total", 0) ?: 0,
        checksAgree = checks?.optInt("agree", 0) ?: 0,
        checksDisagree = checks?.optInt("disagree", 0) ?: 0,
        wordList = scores?.optString("word_list", "off") ?: "off",
        flashOn = flash?.optBoolean("on", false) ?: false,
        flashToday = today,
        calendarOk = cal?.optBoolean("ok"),
        calendarNote = str(cal, "note"),
        wordingRules = strings(obj.optJSONArray("wording_rules")),
        leftOut = strings(obj.optJSONArray("left_out")),
        problems = strings(obj.optJSONArray("problems")),
      )
    }

    fun patternBody(id: Int, confirm: Boolean): JSONObject = JSONObject().put("id", id).put("confirm", confirm)
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getReading(serverUrl: String, appToken: String): ReadingResult = call(serverUrl, appToken, "", null)

  /** You confirm (true) or reject (false) one of her pattern guesses. Only you can; in shadow it changes nothing. */
  suspend fun decidePattern(serverUrl: String, appToken: String, id: Int, confirm: Boolean): ReadingResult =
    call(serverUrl, appToken, "/pattern", patternBody(id, confirm))

  private suspend fun call(serverUrl: String, appToken: String, sub: String, body: JSONObject?): ReadingResult =
    withContext(Dispatchers.IO) {
      val url =
        buildUrl(serverUrl, sub).getOrElse { err ->
          return@withContext ReadingResult.Failure(
            err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext ReadingResult.Failure("Sign in first")
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
            return@withContext ReadingResult.Failure(msg, code)
          }
          if (parsed == null || !parsed.optBoolean("ok", false)) {
            return@withContext ReadingResult.Failure(NOT_INSTALLED_MESSAGE)
          }
          val snap = if (body == null) parseSnapshot(parsed) else null
          val msg = if (parsed.isNull("note")) "" else parsed.optString("note", "")
          ReadingResult.Success(snap, msg)
        }
      } catch (_: SocketTimeoutException) {
        ReadingResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: InterruptedIOException) {
        ReadingResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: IOException) {
        ReadingResult.Failure("Couldn't reach the tablet", "network_error")
      }
    }

  private fun failure(status: Int, parsed: JSONObject?, cleanToken: String): Pair<String, String> {
    if (status == 401) return "Signed out, sign in again" to "http_401"
    if (status == 405 || (status == 404 && parsed?.optString("code") != "unknown")) {
      return NOT_INSTALLED_MESSAGE to "http_$status"
    }
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
