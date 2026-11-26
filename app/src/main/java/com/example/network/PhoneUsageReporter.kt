package com.example.network

import android.content.Context
import com.example.data.RiviaDao
import com.example.security.SecureKeyRepository
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class PhoneUsageReporter(
  context: Context,
  private val dao: RiviaDao,
  private val keyRepository: SecureKeyRepository,
) {
  companion object {
    private const val PREFS_NAME = "phone_usage_reporter"
    private const val KEY_LAST_SENT_TS = "last_sent_ts"
    private const val BATCH_LIMIT = 200
    private const val MAX_BATCHES = 5
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val MODEL_REGEX = Regex("^[a-z0-9._-]{1,64}$")
    private val CLEAN_ID_REGEX = Regex("[^a-zA-Z0-9\\-_.:]")

    fun clean(raw: String): String = CLEAN_ID_REGEX.replace(raw, "_")
  }

  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val mutex = Mutex()

  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()

  suspend fun sendPending() =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val url =
          TabletMcpClient.validateServerUrl(keyRepository.getTabletServerUrl())
            .map { cleanMcpUrl -> cleanMcpUrl.removeSuffix("/app/mcp") + "/app/progress/usage" }
            .getOrElse { return@withLock }
        val token = TabletMcpClient.normalizeAppToken(keyRepository.getTabletAppToken())
        if (token.isBlank()) return@withLock

        for (batch in 0 until MAX_BATCHES) {
          val lastSent = prefs.getLong(KEY_LAST_SENT_TS, 0L)
          val rows = try {
            dao.getRequestLogsAfter(lastSent, BATCH_LIMIT)
          } catch (_: Exception) {
            return@withLock
          }
          if (rows.isEmpty()) return@withLock

          val newestTs = rows.last().timestamp
          val calls = JSONArray()
          for (row in rows) {
            val totalTokens = row.inputTokens + row.outputTokens + row.thoughtTokens
            if (totalTokens == 0) continue
            val cleanedModel = row.model.lowercase(Locale.US).removePrefix("models/")
            if (!MODEL_REGEX.matches(cleanedModel)) continue

            val kindPrefix =
              if (row.chatKind.equals("Rivia", ignoreCase = true)) "rivia" else "lab"
            val kindSuffix = if (row.channel == "voice") "_voice" else "_text"

            val callObj =
              JSONObject()
                .put("id", clean(row.requestId).take(60) + "-" + row.timestamp)
                .put("at", row.timestamp / 1000.0)
                .put("model", cleanedModel)
                .put("in", row.inputTokens)
                .put("out", row.outputTokens + row.thoughtTokens)
                .put("kind", kindPrefix + kindSuffix)
            calls.put(callObj)
          }

          if (calls.length() == 0) {
            prefs.edit().putLong(KEY_LAST_SENT_TS, newestTs).apply()
            return@withLock
          }

          val body = JSONObject().put("calls", calls).toString().toRequestBody(JSON_MEDIA_TYPE)
          val request =
            Request.Builder()
              .url(url)
              .post(body)
              .header("Authorization", "Bearer $token")
              .header("Content-Type", "application/json")
              .build()

          val ok =
            try {
              httpClient.newCall(request).execute().use { resp -> resp.code == 200 }
            } catch (_: Exception) {
              false
            }

          if (!ok) return@withLock

          prefs.edit().putLong(KEY_LAST_SENT_TS, newestTs).apply()
          if (rows.size < BATCH_LIMIT) return@withLock
        }
      }
    }
}
