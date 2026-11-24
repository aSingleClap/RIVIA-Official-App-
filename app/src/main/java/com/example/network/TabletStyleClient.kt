package com.example.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

// Step 8.11: the client for Layer 8 on the tablet (/app/style): how she would sound (shadow: her replies don't change
// yet), the steady-then-calmer plan when you're worked up, what she has learned from your reactions, and bits cooling
// down. Same door and rules as /app/reading: plain HTTP with the app token, never an MCP tool, so Gemini never sees
// it. No retries, 10 s timeouts, never logs or shows the app token. The adjustable values on /app/style come back in
// /app/progress's shape: TabletProgressClient("/app/style") reads and saves those, so the small per-step card is the
// same one Layers 5, 6 and 7 use. This client only reads.

data class StyleLine(
  val at: Long,
  val style: String,
  val strength: Int,
  val sass: Boolean,
  val warmth: Int,
  val cursingOk: Boolean,
  val energy: String,
  val words: String,
  val workedUp: Boolean,
  val why: String,
  val herLabel: String,
  val notes: List<String>,
)

data class StyleLearned(val style: String, val adj: Int, val mutedUntil: Long?)

data class StyleSnapshot(
  val note: String,
  val latest: StyleLine?,
  val recent: List<StyleLine>,
  val styleCounts: Map<String, Int>,
  /** "usual" or "easing" */
  val energyPhase: String,
  val easeStep: Int,
  val messages: Int,
  val workedUp: Int,
  val swearing: Int,
  val rapidFire: Int,
  val learned: List<StyleLearned>,
  val reactions: Int,
  val bitsCooling: Int,
  val fixedRules: List<String>,
  val waitsOn: List<String>,
  val problems: List<String>,
)

sealed class StyleResult {
  data class Success(val snapshot: StyleSnapshot) : StyleResult()

  data class Failure(val message: String, val errorCode: String? = null) : StyleResult()
}

class TabletStyleClient {
  companion object {
    private const val NOT_INSTALLED_MESSAGE = "Layer 8 isn't installed on the tablet yet"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."

    fun buildUrl(rawServerUrl: String): Result<String> =
      TabletMcpClient.validateServerUrl(rawServerUrl).map { it.removeSuffix("/app/mcp") + "/app/style" }

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

    private fun line(o: JSONObject): StyleLine {
      val him = o.optJSONObject("him")
      val her = o.optJSONObject("her")
      return StyleLine(
        at = (o.optDouble("at", 0.0) * 1000).toLong(),
        style = o.optString("style", "plain"),
        strength = o.optInt("strength", 0),
        sass = o.optBoolean("sass", false),
        warmth = o.optInt("warmth", 0),
        cursingOk = o.optBoolean("curse_ok", false),
        energy = o.optString("energy", ""),
        words = o.optString("line", ""),
        workedUp = him?.optBoolean("worked_up", false) ?: false,
        why = str(him, "why"),
        herLabel = listOf(str(her, "intensity"), str(her, "name")).filter { it.isNotBlank() && it != "neutral" }
          .joinToString(" ").ifBlank { "neutral" },
        notes = strings(o.optJSONArray("notes")),
      )
    }

    /** GET /app/style's reply -> what the card draws. Unknown fields are ignored. */
    fun parseSnapshot(obj: JSONObject): StyleSnapshot {
      val latest = if (obj.isNull("latest")) null else obj.optJSONObject("latest")?.let { line(it) }
      val counts = buildMap {
        val c = obj.optJSONObject("style_counts") ?: JSONObject()
        c.keys().forEach { put(it, c.optInt(it, 0)) }
      }
      val energy = obj.optJSONObject("energy")
      val m = obj.optJSONObject("messages_14d")
      val learned = objects(obj.optJSONArray("learning")).map {
        StyleLearned(
          it.optString("style"),
          it.optInt("adj", 0),
          if (it.isNull("muted_until") || !it.has("muted_until")) null
          else (it.optDouble("muted_until", 0.0) * 1000).toLong(),
        )
      }
      return StyleSnapshot(
        note = str(obj, "note"),
        latest = latest,
        recent = objects(obj.optJSONArray("recent")).map { line(it) },
        styleCounts = counts,
        energyPhase = energy?.optString("phase", "usual") ?: "usual",
        easeStep = energy?.optInt("step_i", 0) ?: 0,
        messages = m?.optInt("total", 0) ?: 0,
        workedUp = m?.optInt("worked_up", 0) ?: 0,
        swearing = m?.optInt("swearing", 0) ?: 0,
        rapidFire = m?.optInt("rapid_fire", 0) ?: 0,
        learned = learned,
        reactions = obj.optJSONArray("reactions")?.length() ?: 0,
        bitsCooling = obj.optInt("bits_cooling", 0),
        fixedRules = strings(obj.optJSONArray("fixed_rules")),
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

  suspend fun getStyle(serverUrl: String, appToken: String): StyleResult =
    withContext(Dispatchers.IO) {
      val url =
        buildUrl(serverUrl).getOrElse { err ->
          return@withContext StyleResult.Failure(err.message ?: "Server URL must start with https:// and end in /app/mcp")
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext StyleResult.Failure("Sign in first")
      val request = Request.Builder().url(url).header("Authorization", "Bearer $cleanToken").get().build()
      try {
        httpClient.newCall(request).execute().use { resp ->
          val raw = resp.body?.string().orEmpty()
          val parsed = parse(raw)
          if (resp.code != 200) {
            val (msg, code) = failure(resp.code, parsed, cleanToken)
            return@withContext StyleResult.Failure(msg, code)
          }
          if (parsed == null || !parsed.optBoolean("ok", false)) {
            return@withContext StyleResult.Failure(NOT_INSTALLED_MESSAGE)
          }
          StyleResult.Success(parseSnapshot(parsed))
        }
      } catch (_: SocketTimeoutException) {
        StyleResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: InterruptedIOException) {
        StyleResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: IOException) {
        StyleResult.Failure("Couldn't reach the tablet", "network_error")
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
