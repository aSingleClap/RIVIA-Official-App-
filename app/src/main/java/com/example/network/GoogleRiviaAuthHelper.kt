package com.example.network

import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GoogleUserInfo(
  val email: String,
  val pictureUrl: String,
)

/**
 * Builds the Google Play Services AuthorizationClient request used to obtain an offline
 * server_auth_code for the Rivia tablet server (/app/login) and fetches OIDC userinfo
 * (email and picture) using the returned access token.
 *
 * Uses AuthorizationClient (not Credential Manager) so Google returns a serverAuthCode
 * with the required offline Calendar + Drive scopes.
 */
object GoogleRiviaAuthHelper {
  const val WEB_CLIENT_ID =
    "83613706666-uoisu45esq9jm2v3kfmbp3l0drf09ocj.apps.googleusercontent.com"

  private const val USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"

  private val userInfoClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()

  fun buildAuthorizationRequest(): AuthorizationRequest {
    return AuthorizationRequest.builder()
      .setRequestedScopes(
        listOf(
          Scope("openid"),
          Scope("profile"),
          Scope("https://www.googleapis.com/auth/userinfo.email"),
          Scope("https://www.googleapis.com/auth/calendar.events.readonly"),
          Scope("https://www.googleapis.com/auth/calendar.app.created"),
          Scope("https://www.googleapis.com/auth/drive.file"),
        )
      )
      .requestOfflineAccess(WEB_CLIENT_ID, true)
      .build()
  }

  /**
   * Fetches email and picture URL from Google's OIDC userinfo endpoint using the
   * AuthorizationResult access token. Never logs the access token or email.
   */
  suspend fun fetchUserInfo(accessToken: String): GoogleUserInfo? =
    withContext(Dispatchers.IO) {
      val cleanToken = accessToken.trim()
      if (cleanToken.isBlank()) return@withContext null

      val req =
        Request.Builder()
          .url(USERINFO_URL)
          .get()
          .header("Authorization", "Bearer $cleanToken")
          .build()

      try {
        userInfoClient.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@withContext null
          val bodyStr = resp.body?.string().orEmpty().trim()
          if (!bodyStr.startsWith("{")) return@withContext null
          val json = JSONObject(bodyStr)
          val email = json.optString("email", "").trim()
          val picture = json.optString("picture", "").trim()
          if (email.isBlank()) null else GoogleUserInfo(email = email, pictureUrl = picture)
        }
      } catch (_: Throwable) {
        null
      }
    }
}
