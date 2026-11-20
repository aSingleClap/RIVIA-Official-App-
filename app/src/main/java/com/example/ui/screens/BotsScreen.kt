package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.schedule.ZoneLocationCoordinator
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
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaSurface3
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaTextTertiary
import com.example.ui.theme.RiviaWarningAmber
import com.example.ui.components.GoogleSignInActionBlock
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotsScreen(
  viewModel: RiviaViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val savedTabletUrl by viewModel.tabletServerUrl.collectAsStateWithLifecycle()
  val savedTabletToken by viewModel.tabletAppToken.collectAsStateWithLifecycle()
  val tabletTestRunning by viewModel.tabletTestRunning.collectAsStateWithLifecycle()
  val tabletTestResultText by viewModel.tabletTestResultText.collectAsStateWithLifecycle()
  val tabletTestIsError by viewModel.tabletTestIsError.collectAsStateWithLifecycle()
  val devTabletToolsEnabled by viewModel.devTabletToolsEnabled.collectAsStateWithLifecycle()
  val tabletToolsUnavailable by viewModel.tabletToolsUnavailable.collectAsStateWithLifecycle()
  val zoneConfigs by viewModel.zoneConfigs.collectAsStateWithLifecycle()
  val pinLocationRunning by viewModel.pinLocationRunning.collectAsStateWithLifecycle()
  val pinLocationStatusText by viewModel.pinLocationStatusText.collectAsStateWithLifecycle()
  val pinLocationIsError by viewModel.pinLocationIsError.collectAsStateWithLifecycle()
  val pinLocationPendingApproval by viewModel.pinLocationPendingApproval.collectAsStateWithLifecycle()
  val last20Results by viewModel.last20TabletResults.collectAsStateWithLifecycle()

  var tabletUrlDraft by remember { mutableStateOf(savedTabletUrl) }
  var tabletTokenDraft by remember { mutableStateOf(savedTabletToken) }
  var showTabletTokenPlain by remember { mutableStateOf(false) }

  var pinModeIsNewPlace by remember { mutableStateOf(false) }
  var pinExistingPlaceInput by remember { mutableStateOf("home") }
  var pinNewPlaceNameInput by remember { mutableStateOf("") }
  var pinRadiusInput by remember { mutableStateOf("") }
  var pendingPinAfterFgPerm by remember { mutableStateOf<(() -> Unit)?>(null) }

  val fgLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
      viewModel.refreshZonePermissionsState()
      val granted = grants.values.any { it }
      if (granted && pendingPinAfterFgPerm != null) {
        val action = pendingPinAfterFgPerm
        pendingPinAfterFgPerm = null
        action?.invoke()
      } else {
        pendingPinAfterFgPerm = null
      }
    }

  val knownPlaceIds =
    remember(zoneConfigs) {
      val canonical = ZoneLocationCoordinator.CANONICAL_PLACES.map { it.id }
      val extras = zoneConfigs.map { it.id }.filter { it !in canonical }
      canonical + extras
    }

  LaunchedEffect(savedTabletUrl) { tabletUrlDraft = savedTabletUrl }
  LaunchedEffect(savedTabletToken) { tabletTokenDraft = savedTabletToken }

  fun commitTabletIfChanged() {
    val cleanUrl = SecureKeyRepository.normalizeNoWhitespace(tabletUrlDraft)
    val cleanToken = SecureKeyRepository.normalizeNoWhitespace(tabletTokenDraft)
    if (cleanUrl != savedTabletUrl || cleanToken != savedTabletToken) {
      viewModel.saveTabletSettings(cleanUrl, cleanToken)
    }
  }

  fun triggerPinAction(
    existingPlace: String?,
    newPlaceName: String?,
    radiusMeters: Int?,
  ) {
    focusManager.clearFocus()
    commitTabletIfChanged()
    val runPin = {
      viewModel.pinMyLocation(
        existingPlace = existingPlace,
        newPlaceName = newPlaceName,
        radiusMeters = radiusMeters,
      )
    }
    if (!ZoneLocationCoordinator.hasForegroundLocationPermission(context)) {
      pendingPinAfterFgPerm = runPin
      fgLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
    } else {
      runPin()
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
      // 1. Tablet Server URL & App Token + Test Button + Tablet tools toggle
      item {
        BotsCardSection(title = "Tablet") {
          Text(
            text =
              "Encrypted with Android Keystore AES-256. Server URL must start with https:// and end in /app/mcp.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
          GoogleSignInActionBlock(
            viewModel = viewModel,
            currentServerUrlProvider = { tabletUrlDraft },
            buttonTestTag = "bots_google_sign_in_button",
            statusTestTag = "bots_google_sign_in_status",
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

          HorizontalDivider(color = RiviaBorder, thickness = 1.dp)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.weight(1f).padding(end = 12.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = "Tablet tools",
                color = RiviaTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Let Rivia call eligible tools from your tablet server during chats",
                color = RiviaMutedText,
                style = MaterialTheme.typography.bodySmall,
              )
              if (devTabletToolsEnabled && tabletToolsUnavailable) {
                Text(
                  text = "Tablet tools unavailable",
                  color = RiviaErrorRed,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = ChivoMonoFontFamily,
                  modifier = Modifier.testTag("bots_tablet_tools_unavailable_text"),
                )
              }
            }
            Switch(
              checked = devTabletToolsEnabled,
              onCheckedChange = { enabled ->
                commitTabletIfChanged()
                viewModel.setDevTabletToolsEnabled(enabled)
              },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = Color(0xFF0A0A0A),
                  checkedTrackColor = RiviaMint,
                  uncheckedThumbColor = RiviaTextSecondary,
                  uncheckedTrackColor = RiviaSurface3,
                  uncheckedBorderColor = RiviaBorder,
                ),
              modifier = Modifier.testTag("bots_tablet_tools_switch"),
            )
          }
        }
      }

      // 2. Pin my location (app_pin_place)
      item {
        BotsCardSection(title = "Pin my location") {
          Text(
            text =
              "Gets your phone's current position and calls app_pin_place to move an existing place or add a new place. Waits for approval in the hub, then pulls app_places.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { pinModeIsNewPlace = false },
              shape = RoundedCornerShape(10.dp),
              border =
                BorderStroke(
                  1.dp,
                  if (!pinModeIsNewPlace) RiviaMint else RiviaBorder,
                ),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = if (!pinModeIsNewPlace) RiviaMint.copy(alpha = 0.14f) else RiviaSurface2,
                  contentColor = if (!pinModeIsNewPlace) RiviaMint else RiviaTextSecondary,
                ),
              modifier =
                Modifier.weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("bots_pin_mode_move_button"),
            ) {
              Text("Move place", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
              onClick = { pinModeIsNewPlace = true },
              shape = RoundedCornerShape(10.dp),
              border =
                BorderStroke(
                  1.dp,
                  if (pinModeIsNewPlace) RiviaMint else RiviaBorder,
                ),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = if (pinModeIsNewPlace) RiviaMint.copy(alpha = 0.14f) else RiviaSurface2,
                  contentColor = if (pinModeIsNewPlace) RiviaMint else RiviaTextSecondary,
                ),
              modifier =
                Modifier.weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("bots_pin_mode_add_button"),
            ) {
              Text("Add new place", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          if (!pinModeIsNewPlace) {
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              for (id in knownPlaceIds) {
                val selected = pinExistingPlaceInput.equals(id, ignoreCase = true)
                Surface(
                  shape = RoundedCornerShape(50),
                  color = if (selected) RiviaMint.copy(alpha = 0.18f) else RiviaSurface1,
                  border = BorderStroke(1.dp, if (selected) RiviaMint else RiviaBorder),
                  modifier =
                    Modifier.clip(RoundedCornerShape(50))
                      .clickable { pinExistingPlaceInput = id }
                      .testTag("bots_pin_chip_$id"),
                ) {
                  Text(
                    text = id,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) RiviaMint else RiviaTextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }

            OutlinedTextField(
              value = pinExistingPlaceInput,
              onValueChange = { pinExistingPlaceInput = it },
              label = { Text("Place id or name to move (e.g. home)") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors = botsTextFieldColors(),
              modifier = Modifier.fillMaxWidth().testTag("bots_pin_existing_place_input"),
            )
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = pinNewPlaceNameInput,
                onValueChange = { pinNewPlaceNameInput = it },
                label = { Text("New place name (e.g. Giant)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = botsTextFieldColors(),
                modifier = Modifier.weight(1.4f).testTag("bots_pin_new_place_name_input"),
              )
              OutlinedTextField(
                value = pinRadiusInput,
                onValueChange = { pinRadiusInput = it.filter { ch -> ch.isDigit() } },
                label = { Text("Radius m (50–400)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = botsTextFieldColors(),
                modifier = Modifier.weight(1f).testTag("bots_pin_new_place_radius_input"),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(
              onClick = {
                val parsedRadius = pinRadiusInput.trim().toIntOrNull()
                if (pinModeIsNewPlace) {
                  triggerPinAction(
                    existingPlace = null,
                    newPlaceName = pinNewPlaceNameInput,
                    radiusMeters = parsedRadius,
                  )
                } else {
                  triggerPinAction(
                    existingPlace = pinExistingPlaceInput,
                    newPlaceName = null,
                    radiusMeters = null,
                  )
                }
              },
              enabled =
                !pinLocationRunning &&
                  if (pinModeIsNewPlace) pinNewPlaceNameInput.isNotBlank()
                  else pinExistingPlaceInput.isNotBlank(),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, RiviaMint),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = RiviaMint.copy(alpha = 0.14f),
                  contentColor = RiviaMint,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("bots_pin_my_location_button"),
            ) {
              Text(
                text = if (pinLocationRunning) "Pinning…" else "Pin my location",
                color = RiviaMint,
                fontWeight = FontWeight.SemiBold,
              )
            }

            if (pinLocationPendingApproval) {
              TextButton(
                onClick = {
                  focusManager.clearFocus()
                  commitTabletIfChanged()
                  viewModel.refreshZonesFromTabletNow("bots_pin_approved_pull")
                },
                modifier = Modifier.testTag("bots_pull_places_after_approval_button"),
              ) {
                Text(
                  text = "Approved · Pull places",
                  color = RiviaMint,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }

          if (!pinLocationStatusText.isNullOrBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              StatusShape(
                status =
                  when {
                    pinLocationIsError -> StatusType.ERROR
                    pinLocationPendingApproval -> StatusType.WARNING
                    else -> StatusType.OK
                  },
                size = 8.dp,
              )
              Text(
                text = pinLocationStatusText.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChivoMonoFontFamily,
                color =
                  when {
                    pinLocationIsError -> RiviaErrorRed
                    pinLocationPendingApproval -> RiviaWarningAmber
                    else -> RiviaTextSecondary
                  },
                modifier = Modifier.testTag("bots_pin_location_status_text"),
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
