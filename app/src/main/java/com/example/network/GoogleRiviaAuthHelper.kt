package com.example.network

import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope

/**
 * Builds the Google Play Services AuthorizationClient request used to obtain an offline
 * server_auth_code for the Rivia tablet server (/app/login).
 *
 * Uses AuthorizationClient (not Credential Manager) so Google returns a serverAuthCode
 * with the required offline Calendar + Drive scopes.
 */
object GoogleRiviaAuthHelper {
  const val WEB_CLIENT_ID =
    "83613706666-uoisu45esq9jm2v3kfmbp3l0drf09ocj.apps.googleusercontent.com"

  fun buildAuthorizationRequest(): AuthorizationRequest {
    return AuthorizationRequest.builder()
      .setRequestedScopes(
        listOf(
          Scope("openid"),
          Scope("https://www.googleapis.com/auth/userinfo.email"),
          Scope("https://www.googleapis.com/auth/calendar.events.readonly"),
          Scope("https://www.googleapis.com/auth/calendar.app.created"),
          Scope("https://www.googleapis.com/auth/drive.file"),
        )
      )
      .requestOfflineAccess(WEB_CLIENT_ID, true)
      .build()
  }
}
