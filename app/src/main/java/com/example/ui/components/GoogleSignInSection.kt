package com.example.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.GoogleRiviaAuthHelper
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaTextPrimary
import com.example.viewmodel.RiviaViewModel
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException

/**
 * Reusable "Sign in with Google" action block backed by Play Services AuthorizationClient.
 * Requests offline access for the Rivia server Web client ID and posts serverAuthCode to
 * /app/login to receive and save a fresh Rivia app_token.
 */
@Composable
fun GoogleSignInActionBlock(
  viewModel: RiviaViewModel,
  currentServerUrlProvider: (() -> String)? = null,
  onBeforeStart: (() -> Unit)? = null,
  buttonTestTag: String = "google_sign_in_button",
  statusTestTag: String = "google_sign_in_status_text",
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val isRunning by viewModel.googleSignInRunning.collectAsStateWithLifecycle()
  val statusText by viewModel.googleSignInStatusText.collectAsStateWithLifecycle()
  val isError by viewModel.googleSignInIsError.collectAsStateWithLifecycle()
  val savedToken by viewModel.tabletAppToken.collectAsStateWithLifecycle()

  val authResolutionLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
      if (activityResult.resultCode != Activity.RESULT_OK || activityResult.data == null) {
        viewModel.setGoogleSignInCancelled()
        return@rememberLauncherForActivityResult
      }
      try {
        val authResult =
          Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(activityResult.data)
        val authCode = authResult.serverAuthCode
        if (authCode.isNullOrBlank()) {
          viewModel.setGoogleSignInFailed("Google did not return an authorization code.")
        } else {
          viewModel.completeGoogleSignInWithAuthCode(
            serverAuthCode = authCode,
            customRawUrl = currentServerUrlProvider?.invoke(),
          )
        }
      } catch (e: ApiException) {
        viewModel.setGoogleSignInFailed(
          e.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "Google sign-in failed (${e.statusCode})"
        )
      } catch (e: Exception) {
        viewModel.setGoogleSignInFailed(
          e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
        )
      }
    }

  fun startGoogleSignIn() {
    focusManager.clearFocus()
    onBeforeStart?.invoke()
    viewModel.setGoogleSignInStarted()

    Identity.getAuthorizationClient(context)
      .authorize(GoogleRiviaAuthHelper.buildAuthorizationRequest())
      .addOnSuccessListener { result ->
        if (result.hasResolution()) {
          val pendingIntent = result.pendingIntent
          if (pendingIntent != null) {
            try {
              authResolutionLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
              )
            } catch (e: Exception) {
              viewModel.setGoogleSignInFailed(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                  ?: "Could not open Google sign-in"
              )
            }
          } else {
            viewModel.setGoogleSignInFailed("Google sign-in resolution was unavailable.")
          }
        } else {
          val authCode = result.serverAuthCode
          if (authCode.isNullOrBlank()) {
            viewModel.setGoogleSignInFailed("Google did not return an authorization code.")
          } else {
            viewModel.completeGoogleSignInWithAuthCode(
              serverAuthCode = authCode,
              customRawUrl = currentServerUrlProvider?.invoke(),
            )
          }
        }
      }
      .addOnFailureListener { e ->
        viewModel.setGoogleSignInFailed(
          e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
        )
      }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedButton(
      onClick = { startGoogleSignIn() },
      enabled = !isRunning,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, RiviaBorder),
      colors =
        ButtonDefaults.outlinedButtonColors(
          containerColor = RiviaSurface2,
          contentColor = RiviaTextPrimary,
        ),
      modifier =
        Modifier.fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag(buttonTestTag),
    ) {
      Icon(
        imageVector = Icons.Outlined.AccountCircle,
        contentDescription = "Sign in with Google",
        tint = RiviaIconDefault,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = if (isRunning) "Signing in…" else "Sign in with Google",
        color = RiviaTextPrimary,
        fontWeight = FontWeight.SemiBold,
      )
    }

    if (!statusText.isNullOrBlank() || savedToken.isNotBlank()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
      ) {
        StatusShape(
          status =
            when {
              isError -> StatusType.ERROR
              !statusText.isNullOrBlank() || savedToken.isNotBlank() -> StatusType.OK
              else -> StatusType.WARNING
            },
          size = 8.dp,
        )
        Text(
          text = statusText ?: "Signed in to Rivia",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = if (isError) RiviaErrorRed else RiviaMutedText,
          modifier = Modifier.testTag(statusTestTag),
        )
      }
    }
  }
}
