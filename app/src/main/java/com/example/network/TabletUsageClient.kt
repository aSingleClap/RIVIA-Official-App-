package com.example.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Step 5.7: the client for GET /app/progress/usage, what Layer 5's section 5.5 draws.
// Jordan (Nov 23): "Id like the rpm and red limits to be put in there for each model so I know if im hitting the
// models actual limits. I also want adjustable token limits and visuals (graphs) in layer 5 in Section 5.5".
//
// Read-only. The limits themselves (each goal's tokens and calls, each model's RPM/TPM/RPD) are adjustable values on
// the 5.5 and Model limits cards (TabletProgressClient); this only fetches use, to draw it. Same door and rules as
// the other Layer 5 calls: the app token, plain HTTP, never an MCP tool, no retries, 10 s timeouts, the token never
// logged or shown.
//
// Words used on the card:
//   RPM  requests a minute       TPM  input tokens a minute       RPD  requests a day (Google restarts the count at
//   midnight Pacific, 3 AM in New York). Status: ok, near (80% or more), at (100% or more), not set (no number typed).

/** One goal kind on one day: how many goals, average and most tokens, how many would have hit their own limit. */
data class KindDay(val goals: Int, val tokensAvg: Long, val tokensMax: Long, val wouldStop: Int)

/** One model on one Pacific day: requests, and the busiest minute's requests and input tokens. */
data class ModelDay(val requests: Long, val peakRpm: Long, val peakTpm: Long)

/** One gauge right now: used against Jordan's number. limit and share are null when he hasn't typed one in. */
data class Gauge(val used: Long, val limit: Long?, val share: Double?, val status: String)

data class ModelGauges(val rpm: Gauge, val tpm: Gauge, val rpd: Gauge, val worst: String)

/** The limits a kind of goal starts with now (Jordan's app values or the code's), for the graph's limit line. */
data class GoalLimitLine(val tokens: Long, val modelCalls: Long, val toolCalls: Long, val timeS: Double, val costUsd: Double?)

data class UsageSnapshot(
  /** "measure": tokens and calls are logged, not enforced. "enforce": they stop her. */
  val mode: String,
  /** New York days, oldest first. */
  val days: List<String>,
  val kinds: Map<String, List<KindDay>>,
  /** Pacific days, oldest first (Google's day). */
  val modelDays: List<String>,
  val models: Map<String, List<ModelDay>>,
  val gauges: Map<String, ModelGauges>,
  /** Epoch seconds when today's RPD count starts over. */
  val rpdResetsAt: Long,
  val goalLimits: Map<String, GoalLimitLine>,
  val goalDefaults: Map<String, GoalLimitLine>,
  val ceilingTokens: Long,
  /** Which device's calls are counted ("tablet"), and the plain note on what's left out. */
  val seen: List<String>,
  val seenNote: String,
  val problems: List<String>,
)

sealed class UsageResult {
  data class Success(val snapshot: UsageSnapshot) : UsageResult()

  data class Failure(val message: String, val errorCode: String? = null) : UsageResult()
}

class TabletUsageClient {
  companion object {
    private const val NOT_INSTALLED_MESSAGE = "The 5.5 graphs aren't installed on the tablet yet"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."
    const val DAYS = 14
    val STATUSES = setOf("ok", "near", "at", "not_set")

    /** Rivia's models, in the order Jordan's model plan lists them, with the names the card shows. */
    val MODEL_NAMES =
      linkedMapOf(
        "gemini-4-hydrogen" to "Gemini 4 Hydrogen (everyday)",
        "gemini-4-neon" to "Gemini 4 Neon (deep)",
        "gemini-3.8-flash" to "Gemini 3.8 Flash (live)",
      )

    val KIND_NAMES =
      linkedMapOf(
        "plan_run" to "Building or checking a day plan",
        "day_score" to "Scoring a finished day",
        "question" to "A question over a few turns",
        "debug" to "Looking into why something is broken",
      )

    fun buildUsageUrl(rawServerUrl: String, days: Int = DAYS): Result<String> =
      TabletMcpClient.validateServerUrl(rawServerUrl).map { cleanMcpUrl ->
        cleanMcpUrl.removeSuffix("/app/mcp") + "/app/progress/usage?days=" + days.coerceIn(1, 31)
      }

    fun modelName(id: String): String = MODEL_NAMES[id] ?: id

    fun kindName(id: String): String = KIND_NAMES[id] ?: id

    /** A status in plain words, with the share when there is one: "near (90%)". */
    fun statusWords(g: Gauge): String =
      when (g.status) {
        "ok" -> "ok" + pct(g.share)
        "near" -> "near" + pct(g.share)
        "at" -> "at limit" + pct(g.share)
        else -> "not set"
      }

    /** "9 of 10" or "9 used, no limit typed in". */
    fun usedWords(g: Gauge): String =
      if (g.limit == null) "${count(g.used)} used, no limit typed in" else "${count(g.used)} of ${count(g.limit)}"

    /** When RPD starts over, in Jordan's own time: "RPD starts over at 3:00 AM your time". */
    fun resetLine(epochS: Long, zone: ZoneId = ZoneId.systemDefault()): String {
      if (epochS <= 0L) return ""
      val t = Instant.ofEpochSecond(epochS).atZone(zone)
      return "RPD starts over at " + DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(t) + " your time"
    }

    /** 12,500 / 1.2M style counts for small labels. */
    fun count(n: Long): String =
      when {
        n >= 1_000_000L -> trim(n / 1_000_000.0) + "M"
        n >= 10_000L -> trim(n / 1_000.0) + "k"
        else -> String.format(Locale.US, "%,d", n)
      }

    /** The top of a bar graph: the biggest bar or the limit line, whichever is higher, so the line always shows. */
    fun graphTop(values: List<Long>, line: Long?): Long = maxOf(values.maxOrNull() ?: 0L, line ?: 0L, 1L)

    /** "Nov 23" from "2026-11-23"; the raw text if it isn't a date. */
    fun shortDay(iso: String): String =
      try {
        DateTimeFormatter.ofPattern("MMM d", Locale.US).format(java.time.LocalDate.parse(iso))
      } catch (_: Exception) {
        iso
      }

    private fun pct(share: Double?): String =
      if (share == null) "" else " (" + Math.round(share * 100).toString() + "%)"

    private fun trim(d: Double): String = String.format(Locale.US, "%.1f", d).removeSuffix(".0")

    /** The tablet's reply as a snapshot, or null when it isn't the expected shape. */
    fun parseUsage(obj: JSONObject): UsageSnapshot? {
      if (!obj.optBoolean("ok", false)) return null
      val days = strings(obj.optJSONArray("days")) ?: return null
      val modelDays = strings(obj.optJSONArray("model_days")) ?: return null
      val kindsObj = obj.optJSONObject("kinds") ?: return null
      val kinds = linkedMapOf<String, List<KindDay>>()
      for (k in kindsObj.keys()) {
        val arr = kindsObj.optJSONArray(k) ?: return null
        if (arr.length() != days.size) return null
        kinds[k] =
          (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: return null
            KindDay(o.optInt("goals"), o.optLong("tokens_avg"), o.optLong("tokens_max"), o.optInt("would_stop"))
          }
      }
      val modelsObj = obj.optJSONObject("models") ?: return null
      val models = linkedMapOf<String, List<ModelDay>>()
      for (m in ordered(modelsObj.keys().asSequence().toList())) {
        val arr = modelsObj.optJSONArray(m) ?: return null
        if (arr.length() != modelDays.size) return null
        models[m] =
          (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: return null
            ModelDay(o.optLong("requests"), o.optLong("peak_rpm"), o.optLong("peak_tpm"))
          }
      }
      val gaugesObj = obj.optJSONObject("gauges") ?: return null
      val gauges = linkedMapOf<String, ModelGauges>()
      for (m in ordered(gaugesObj.keys().asSequence().toList())) {
        val o = gaugesObj.optJSONObject(m) ?: return null
        val worst = o.optString("worst", "")
        if (worst !in STATUSES) return null
        gauges[m] =
          ModelGauges(
            gauge(o.optJSONObject("rpm")) ?: return null,
            gauge(o.optJSONObject("tpm")) ?: return null,
            gauge(o.optJSONObject("rpd")) ?: return null,
            worst,
          )
      }
      val ceiling = obj.optJSONObject("hard_ceiling")
      return UsageSnapshot(
        mode = obj.optString("mode", ""),
        days = days,
        kinds = kinds,
        modelDays = modelDays,
        models = models,
        gauges = gauges,
        rpdResetsAt = obj.optLong("rpd_resets_at", 0L),
        goalLimits = limitLines(obj.optJSONObject("goal_limits")),
        goalDefaults = limitLines(obj.optJSONObject("goal_defaults")),
        ceilingTokens = ceiling?.optLong("tokens", 0L) ?: 0L,
        seen = strings(obj.optJSONArray("seen")) ?: emptyList(),
        seenNote = obj.optString("seen_note", ""),
        problems = strings(obj.optJSONArray("problems")) ?: emptyList(),
      )
    }

    private fun ordered(names: List<String>): List<String> =
      MODEL_NAMES.keys.filter { it in names } + names.filter { it !in MODEL_NAMES }.sorted()

    private fun gauge(o: JSONObject?): Gauge? {
      if (o == null) return null
      val status = o.optString("status", "")
      if (status !in STATUSES) return null
      return Gauge(
        used = o.optLong("used", 0L),
        limit = if (o.isNull("limit")) null else o.optLong("limit"),
        share = if (o.isNull("share")) null else o.optDouble("share").takeIf { !it.isNaN() },
        status = status,
      )
    }

    private fun limitLines(o: JSONObject?): Map<String, GoalLimitLine> {
      if (o == null) return emptyMap()
      val out = linkedMapOf<String, GoalLimitLine>()
      for (k in o.keys()) {
        val x = o.optJSONObject(k) ?: continue
        out[k] =
          GoalLimitLine(
            tokens = x.optLong("tokens"),
            modelCalls = x.optLong("model_calls"),
            toolCalls = x.optLong("tool_calls"),
            timeS = x.optDouble("time_s"),
            costUsd = if (x.isNull("cost_usd")) null else x.optDouble("cost_usd"),
          )
      }
      return out
    }

    private fun strings(arr: org.json.JSONArray?): List<String>? {
      if (arr == null) return null
      return (0 until arr.length()).map { arr.optString(it, "") }
    }
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getUsage(serverUrl: String, appToken: String, days: Int = DAYS): UsageResult =
    withContext(Dispatchers.IO) {
      val url =
        buildUsageUrl(serverUrl, days).getOrElse { err ->
          return@withContext UsageResult.Failure(
            message = err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext UsageResult.Failure(message = "Sign in first")
      val request = Request.Builder().url(url).get().header("Authorization", "Bearer $cleanToken").build()
      try {
        httpClient.newCall(request).execute().use { resp ->
          val raw = resp.body?.string().orEmpty().trim()
          val parsed =
            if (raw.startsWith("{")) {
              try {
                JSONObject(raw)
              } catch (_: Exception) {
                null
              }
            } else null
          if (resp.code == 401) return@withContext UsageResult.Failure("Signed out, sign in again", "http_401")
          if (resp.code == 404 || resp.code == 405) {
            return@withContext UsageResult.Failure(NOT_INSTALLED_MESSAGE, "http_${resp.code}")
          }
          if (resp.code == 403) {
            val desc = parsed?.optString("error_description", "")?.trim().orEmpty()
            return@withContext UsageResult.Failure(sanitize(desc, cleanToken).ifBlank { WRONG_DOOR_FALLBACK }, "http_403")
          }
          if (resp.code != 200) {
            val err = parsed?.optString("error", "")?.trim().orEmpty()
            return@withContext UsageResult.Failure(
              sanitize(err, cleanToken).ifBlank { "HTTP ${resp.code}" },
              parsed?.optString("code", "")?.ifBlank { null } ?: "http_${resp.code}",
            )
          }
          val snap = parsed?.let { parseUsage(it) } ?: return@withContext UsageResult.Failure(NOT_INSTALLED_MESSAGE)
          UsageResult.Success(snap)
        }
      } catch (_: SocketTimeoutException) {
        UsageResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: InterruptedIOException) {
        UsageResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: IOException) {
        UsageResult.Failure("Couldn't reach the tablet", "network_error")
      }
    }

  private fun sanitize(raw: String, cleanToken: String): String =
    if (cleanToken.length >= 4) raw.replace(cleanToken, "[REDACTED]") else raw
}
