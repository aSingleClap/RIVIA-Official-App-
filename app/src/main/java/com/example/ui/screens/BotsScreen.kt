package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.SecureKeyRepository
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaSurface3
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaTextTertiary
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BotsScreen(
  viewModel: RiviaViewModel,
  onBack: () -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val savedTabletUrl by viewModel.tabletServerUrl.collectAsStateWithLifecycle()
  val savedTabletToken by viewModel.tabletAppToken.collectAsStateWithLifecycle()
  val tabletTestRunning by viewModel.tabletTestRunning.collectAsStateWithLifecycle()
  val tabletTestResultText by viewModel.tabletTestResultText.collectAsStateWithLifecycle()
  val tabletTestIsError by viewModel.tabletTestIsError.collectAsStateWithLifecycle()
  val last20Results by viewModel.last20TabletResults.collectAsStateWithLifecycle()

  var tabletUrlDraft by remember { mutableStateOf(savedTabletUrl) }
  var tabletTokenDraft by remember { mutableStateOf(savedTabletToken) }
  var showTabletTokenPlain by remember { mutableStateOf(false) }

  LaunchedEffect(savedTabletUrl) { tabletUrlDraft = savedTabletUrl }
  LaunchedEffect(savedTabletToken) { tabletTokenDraft = savedTabletToken }

  fun commitTabletIfChanged() {
    val cleanUrl = SecureKeyRepository.normalizeNoWhitespace(tabletUrlDraft)
    val cleanToken = SecureKeyRepository.normalizeNoWhitespace(tabletTokenDraft)
    if (cleanUrl != savedTabletUrl || cleanToken != savedTabletToken) {
      viewModel.saveTabletSettings(cleanUrl, cleanToken)
    }
  }

  val commitRef by rememberUpdatedState(::commitTabletIfChanged)
  DisposableEffect(Unit) { onDispose { commitRef() } }

  BackHandler {
    commitTabletIfChanged()
    focusManager.clearFocus()
    onBack()
  }

  val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    // Header
    Surface(color = RiviaBackground, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IconButton(
          onClick = {
            commitTabletIfChanged()
            focusManager.clearFocus()
            onBack()
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("bots_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = RiviaText,
          )
        }
        Text(
          text = "Tablet",
          style = MaterialTheme.typography.titleLarge,
          color = RiviaText,
        )
        Surface(
          shape = RoundedCornerShape(50),
          color = RiviaSurface2,
          border = BorderStroke(1.dp, RiviaBorder),
        ) {
          Text(
            text = "DEV · TABLET MCP",
            color = RiviaTextSecondary,
            fontFamily = ChivoMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
          )
        }
      }
    }

    HorizontalDivider(color = RiviaBorder)

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Tablet Server URL & App Token + Test Button
      item {
        BotsCardSection(title = "Tablet") {
          Text(
            text =
              "Encrypted with Android Keystore AES-256. Server URL must start with https:// and end in /app/mcp.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
          OutlinedTextField(
            value = tabletUrlDraft,
            onValueChange = { tabletUrlDraft = it },
            label = { Text("Server URL") },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  commitTabletIfChanged()
                  focusManager.clearFocus()
                }
              ),
            modifier =
              Modifier.fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) commitTabletIfChanged() }
                .testTag("bots_tablet_url_input"),
            colors = botsTextFieldColors(),
          )

          OutlinedTextField(
            value = tabletTokenDraft,
            onValueChange = { tabletTokenDraft = it },
            label = { Text("App token") },
            shape = RoundedCornerShape(14.dp),
            visualTransformation =
              if (showTabletTokenPlain) VisualTransformation.None
              else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(
                onClick = { showTabletTokenPlain = !showTabletTokenPlain },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector =
                    if (showTabletTokenPlain) Icons.Outlined.VisibilityOff
                    else Icons.Outlined.Visibility,
                  contentDescription =
                    if (showTabletTokenPlain) "Hide App token" else "Show App token",
                  tint = RiviaMutedText,
                )
              }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  commitTabletIfChanged()
                  focusManager.clearFocus()
                }
              ),
            modifier =
              Modifier.fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) commitTabletIfChanged() }
                .testTag("bots_tablet_token_input"),
            colors = botsTextFieldColors(),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.weight(1f).padding(end = 12.dp),
            ) {
              StatusShape(
                status =
                  when {
                    tabletTestIsError -> StatusType.ERROR
                    !tabletTestResultText.isNullOrBlank() -> StatusType.OK
                    tabletTokenDraft.isNotBlank() -> StatusType.OK
                    else -> StatusType.WARNING
                  },
                size = 8.dp,
              )
              Text(
                text =
                  tabletTestResultText
                    ?: if (tabletTokenDraft.isNotBlank()) "Token saved in Keystore"
                    else "No token entered",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChivoMonoFontFamily,
                color = if (tabletTestIsError) RiviaErrorRed else RiviaMutedText,
                modifier = Modifier.testTag("bots_test_inline_result"),
              )
            }

            OutlinedButton(
              onClick = {
                focusManager.clearFocus()
                viewModel.testTabletConnection(tabletUrlDraft, tabletTokenDraft)
              },
              enabled = !tabletTestRunning,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, RiviaBorder),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = RiviaSurface2,
                  contentColor = RiviaTextPrimary,
                ),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("bots_test_button"),
            ) {
              Text(
                if (tabletTestRunning) "Testing..." else "Test",
                color = RiviaTextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }

      // 4. Last 20 Results in Memory
      item {
        BotsCardSection(title = "Last 20 results (${last20Results.size})") {
          if (last20Results.isEmpty()) {
            Text(
              text = "No tablet calls recorded in this session yet.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaTextTertiary,
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              last20Results.forEach { rec ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = RiviaSurface3,
                  border = BorderStroke(1.dp, RiviaBorder),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        StatusShape(
                          status = if (rec.ok) StatusType.OK else StatusType.ERROR,
                          size = 8.dp,
                        )
                        Text(
                          text = rec.tool,
                          fontFamily = ChivoMonoFontFamily,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = RiviaTextPrimary,
                        )
                      }
                      Text(
                        text = "${timeFormatter.format(Date(rec.timestamp))} · ${rec.latencyMs}ms",
                        fontFamily = ChivoMonoFontFamily,
                        fontSize = 11.sp,
                        color = RiviaTextTertiary,
                      )
                    }

                    val metaParts = buildList {
                      add("ok=${rec.ok}")
                      add("status=${rec.status}")
                      if (!rec.ticket.isNullOrBlank()) add("ticket=${rec.ticket}")
                      if (!rec.errorCode.isNullOrBlank()) add("error=${rec.errorCode}")
                    }
                    Text(
                      text = metaParts.joinToString(" · "),
                      fontFamily = ChivoMonoFontFamily,
                      fontSize = 11.sp,
                      color = if (rec.ok) RiviaTextSecondary else RiviaErrorRed,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BotsCardSection(
  title: String,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = RiviaSurface2,
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = RiviaTextPrimary,
        fontWeight = FontWeight.SemiBold,
      )
      content()
    }
  }
}

@Composable
private fun botsTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = RiviaTextPrimary,
    unfocusedTextColor = RiviaTextPrimary,
    focusedContainerColor = RiviaSurface2,
    unfocusedContainerColor = RiviaSurface2,
    focusedBorderColor = RiviaMint,
    unfocusedBorderColor = RiviaBorder,
    focusedLabelColor = RiviaTextSecondary,
    unfocusedLabelColor = RiviaTextTertiary,
    cursorColor = RiviaMint,
  )
