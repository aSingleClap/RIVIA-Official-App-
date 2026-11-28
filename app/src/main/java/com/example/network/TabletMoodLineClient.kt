package com.example.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Layer 1 live (Nov 25). Jordan: "Can layer one go live now instead of being shadowed?"
// Her mood line (Step 1.6) is written on the tablet by rivia_inject.for_gemini(). This file asks the tablet for that
// line once per Rivia reply, by plain HTTP to GET /app/emotion/line with the existing app token, the same way the mood
// dial reads /app/emotion. It is never a Gemini tool: the phone reads the line and puts it into her instructions itself.
// Two things must both be on before anything reaches her:
//   1. the tablet's shadow for the mood line is off (a hand edit on the tablet, by design), and
//   2. the App switch "1.6 Mood line to Gemini" on this phone is on.
// Fails safe: if the tablet is slow (over 1.5 s), unreachable, signed out, in shadow, says she's neutral, or sends
// anything odd, the reply goes out with no mood line, exactly as before. Never logs or shows the app token.

/** What the tablet answered for one reply. line is "" when there is nothing to send. */
data class MoodLineFetch(val line: String, val shadow: Boolean?, val problem: String)

class TabletMoodLineClient {
  companion object {
    /** Total time the phone waits for the tablet before sending the reply without a mood line. */
    const val WAIT_MS = 1500L

    fun buildLineUrl(rawServerUrl: String): Result<String> =
      TabletMcpClient.validateServerUrl(rawServerUrl).map { cleanMcpUrl ->
        cleanMcpUrl.removeSuffix("/app/mcp") + "/app/emotion/line"
      }
  }

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(WAIT_MS, TimeUnit.MILLISECONDS)
      .readTimeout(WAIT_MS, TimeUnit.MILLISECONDS)
      .callTimeout(WAIT_MS, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun getLine(serverUrl: String, appToken: String): MoodLineFetch =
    withContext(Dispatchers.IO) {
      val url =
        buildLineUrl(serverUrl).getOrElse {
          return@withContext MoodLineFetch("", null, "the tablet address isn't set")
        }
      val cleanToken = TabletMcpClient.normalizeAppToken(appToken)
      if (cleanToken.isBlank()) return@withContext MoodLineFetch("", null, "not signed in")
      val request = Request.Builder().url(url).get().header("Authorization", "Bearer $cleanToken").build()
      try {
        httpClient.newCall(request).execute().use { resp ->
          val raw = resp.body?.string().orEmpty().trim()
          val json =
            if (raw.startsWith("{")) {
              try {
                JSONObject(raw)
              } catch (_: Exception) {
                null
              }
            } else {
              null
            }
          when {
            resp.code == 401 -> MoodLineFetch("", null, "signed out on the tablet")
            resp.code == 404 || resp.code == 405 -> MoodLineFetch("", null, "the tablet has no mood line route yet")
            json?.optString("code") == "layer_off" -> MoodLineFetch("", null, "Layer 1 is off on the tablet")
            resp.code != 200 || json == null || !json.optBoolean("ok", false) ->
              MoodLineFetch("", null, "the tablet answered with an error")
            else -> {
              val shadow = if (json.has("shadow") && !json.isNull("shadow")) json.optBoolean("shadow") else null
              val line = if (json.isNull("line")) "" else json.optString("line", "")
              MoodLineFetch(line, shadow, "")
            }
          }
        }
      } catch (_: InterruptedIOException) {
        MoodLineFetch("", null, "the tablet didn't answer in time")
      } catch (_: IOException) {
        MoodLineFetch("", null, "couldn't reach the tablet")
      }
    }
}

/** The phone's side of her mood line: the App switch check, the safety checks, the wording, and the last result. */
object MoodLine {
  private val client = TabletMoodLineClient()

  /** One plain sentence about the last Rivia reply, shown on the 1.6 row. */
  var lastStatus by mutableStateOf("No Rivia reply since the app opened.")
    private set

  /**
   * The words Gemini gets, or "" for none. The tablet's words go in unchanged, between a fixed start and end.
   * The scale matches the tablet's: faint 1, mild 2, moderate 3, strong 4.
   */
  fun frame(tabletLine: String): String {
    // The tablet sends e.g. "mood: annoyance, intensity 2 of 4". Drop its "mood:" start so it isn't said twice.
    val words = tabletLine.trim().removePrefix("mood:").trim()
    return "Your mood right now: $words (1 is faint, 4 is strong). Let it color how you speak, never what you do."
  }

  /** The tablet's line made safe: one short line, no "fear" or "scared". Null if it fails a check. */
  fun clean(raw: String): String? {
    val oneLine = raw.replace('\r', ' ').replace('\n', ' ').replace(Regex("\\s+"), " ").trim().trimEnd('.').trim()
    if (oneLine.isEmpty() || oneLine.length > 120) return null
    val lower = oneLine.lowercase()
    if ("fear" in lower || "scared" in lower) return null
    return oneLine
  }

  /**
   * Called once per Rivia reply, before the first request to Gemini. Returns the framed line, or "" for none.
   * Never throws. record = false (the request preview) leaves the 1.6 row's status alone.
   */
  suspend fun forTurn(context: Context, serverUrl: String, appToken: String, record: Boolean = true): String {
    try {
      LayerSwitches.loadPhone(context)
      if (!LayerSwitches.moodLineOn()) {
        if (record) lastStatus = "Last reply: no mood line. The App switch is off."
        return ""
      }
      val fetch = client.getLine(serverUrl, appToken)
      if (fetch.problem.isNotEmpty()) {
        if (record) lastStatus = "Last reply: no mood line. ${fetch.problem.replaceFirstChar { it.uppercase() }}."
        return ""
      }
      if (fetch.line.isBlank()) {
        if (record) {
          lastStatus =
            when (fetch.shadow) {
              true -> "Last reply: no mood line. The tablet's mood line is still in shadow (logged only)."
              false -> "Last reply: no mood line. She reads neutral, and neutral sends nothing."
              null -> "Last reply: no mood line. She reads neutral, or the tablet is still in shadow."
            }
        }
        return ""
      }
      val safe = clean(fetch.line)
      if (safe == null) {
        if (record) lastStatus = "Last reply: no mood line. The tablet's line failed the app's checks."
        return ""
      }
      if (record) lastStatus = "Last reply: sent \"$safe\"."
      return frame(safe)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      if (record) lastStatus = "Last reply: no mood line. Something went wrong reading it."
      return ""
    }
  }
}
