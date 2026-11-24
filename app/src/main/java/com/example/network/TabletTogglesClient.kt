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

// Layer switches (Nov 24): the client for the tablet's switches (/app/toggles).
// Jordan (Nov 24): "Next big plan is to allow each layer and piece of a layer to be toggles on and off in the app",
// and "In the app layers/pieces that are dependent on another to function will be stated and clearly shown in the app".
// He picked the mix: switches for work the tablet does are saved on the tablet, and the tablet checks them each time
// the work would run. Switches for things only the phone does (the mood dial, the 2.4 controls, the thumbs) are kept
// on the phone. The tablet keeps the one list of what needs what, for every piece, phone pieces included, and sends
// it here, so the phone never keeps its own copy.
// Jordan (Nov 24): "Each layer and layer piece stays where it is in the app but each piece gets a small label next to
// the toggle that says tablet or app". So each piece says which step row it sits on ("step"), and where it is saved.
// Same door and rules as /app/reading: plain HTTP with the app token, never an MCP tool, so Gemini never sees it.
// No retries, 10 s timeouts, never logs or shows the app token.

/** One "needs" or "needed by" link. hard: forced off without it. Not hard (soft): still runs, but has nothing new. */
data class ToggleLink(val id: String, val hard: Boolean)

data class TogglePiece(
  val id: String,
  /** 1 to 8. */
  val layer: Int,
  /** The step row it sits on, like "1.5" or "8.2b". Null for a whole-layer switch, shown under the layer's name. */
  val step: String?,
  val title: String,
  /** "tablet" or "phone". */
  val where: String,
  val switchable: Boolean,
  /** Your saved setting for a tablet switch. Null for phone pieces and pieces with nothing to switch. */
  val value: Boolean?,
  /** on, off, off_by_parent, off_by_env, waiting, nothing_to_switch, always_on, or phone. */
  val state: String,
  val needs: List<ToggleLink>,
  val neededBy: List<ToggleLink>,
  /** Why it isn't simply on, in plain words. "" when there's nothing to say. */
  val why: String,
  /** Ids that turning this off would also turn off. */
  val wouldAlsoTurnOff: List<String>,
)

data class TogglesSnapshot(val pieces: List<TogglePiece>, val problems: List<String>, val note: String)

sealed class TogglesResult {
  data class Success(val snapshot: TogglesSnapshot) : TogglesResult()

  data class Failure(val message: String, val errorCode: String? = null) : TogglesResult()
}

class TabletTogglesClient {
  companion object {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    const val NOT_INSTALLED_MESSAGE = "Switches not installed on the tablet"
    const val NOT_INSTALLED_CODE = "not_installed"
    private const val WRONG_DOOR_FALLBACK = "This token isn't for the app door."

    fun buildUrl(rawServerUrl: String): Result<String> =
      TabletMcpClient.validateServerUrl(rawServerUrl).map { it.removeSuffix("/app/mcp") + "/app/toggles" }

    private fun str(o: JSONObject?, key: String): String =
      if (o == null || !o.has(key) || o.isNull(key)) "" else o.optString(key, "")

    private fun strings(arr: JSONArray?): List<String> = buildList {
      if (arr == null) return@buildList
      for (i in 0 until arr.length()) arr.optString(i, "").trim().takeIf { it.isNotBlank() }?.let { add(it) }
    }

    private fun objects(arr: JSONArray?): List<JSONObject> = buildList {
      if (arr == null) return@buildList
      for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(it) }
    }

    private fun links(arr: JSONArray?): List<ToggleLink> =
      objects(arr).mapNotNull { o ->
        val id = o.optString("id", "").trim()
        if (id.isEmpty()) null else ToggleLink(id, o.optString("kind", "hard") != "soft")
      }

    private fun parsePiece(o: JSONObject): TogglePiece? {
      val id = o.optString("id", "").trim()
      if (id.isEmpty()) return null
      return TogglePiece(
        id = id,
        layer = o.optInt("layer", 0),
        step = str(o, "step").trim().ifBlank { null },
        title = str(o, "title").ifBlank { id },
        where = str(o, "where").ifBlank { "tablet" },
        switchable = o.optBoolean("switchable", false),
        value = if (!o.has("value") || o.isNull("value")) null else o.optBoolean("value", false),
        state = str(o, "state").ifBlank { "on" },
        needs = links(o.optJSONArray("needs")),
        neededBy = links(o.optJSONArray("needed_by")),
        why = str(o, "why"),
        wouldAlsoTurnOff = strings(o.optJSONArray("would_also_turn_off")),
      )
    }

    /** GET or POST /app/toggles's reply -> what the app draws. Unknown fields are ignored. */
    fun parseSnapshot(obj: JSONObject): TogglesSnapshot =
      TogglesSnapshot(
        pieces = objects(obj.optJSONArray("pieces")).mapNotNull { parsePiece(it) },
        problems = strings(obj.optJSONArray("problems")),
        note = str(obj, "note"),
      )

    fun setBody(id: String, on: Boolean): JSONObject = JSONObject().put("id", id).put("on", on)
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getToggles(serverUrl: String, appToken: String): TogglesResult = call(serverUrl, appToken, null)

  /** Saves one tablet switch. The tablet refuses phone pieces, guardrails, and a piece whose parent is off. */
  suspend fun setToggle(serverUrl: String, appToken: String, id: String, on: Boolean): TogglesResult =
    call(serverUrl, appToken, setBody(id, on))

  private suspend fun call(serverUrl: String, appToken: String, body: JSONObject?): TogglesResult =
    withContext(Dispatchers.IO) {
      val url =
        buildUrl(serverUrl).getOrElse { err ->
          return@withContext TogglesResult.Failure(
            err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext TogglesResult.Failure("Sign in first")
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
            return@withContext TogglesResult.Failure(msg, code)
          }
          if (parsed == null || !parsed.optBoolean("ok", false) || !parsed.has("pieces")) {
            return@withContext TogglesResult.Failure(NOT_INSTALLED_MESSAGE, NOT_INSTALLED_CODE)
          }
          TogglesResult.Success(parseSnapshot(parsed))
        }
      } catch (_: SocketTimeoutException) {
        TogglesResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: InterruptedIOException) {
        TogglesResult.Failure("Tablet didn't answer", "timeout")
      } catch (_: IOException) {
        TogglesResult.Failure("Couldn't reach the tablet", "network_error")
      }
    }

  private fun failure(status: Int, parsed: JSONObject?, cleanToken: String): Pair<String, String> {
    if (status == 401) return "Signed out, sign in again" to "http_401"
    if (status == 405 || (status == 404 && parsed?.optString("code") != "unknown_id")) {
      return NOT_INSTALLED_MESSAGE to NOT_INSTALLED_CODE
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
