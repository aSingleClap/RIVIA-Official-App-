package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.RunSettings
import com.example.ui.components.GuideHeaderButton
import com.example.ui.components.InfoHelpIconButton
import com.example.ui.components.RiviaSettingsGuide
import com.example.ui.components.SettingHelpBottomSheet
import com.example.ui.components.SettingHelpTopic
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaTextTertiary
import com.example.ui.theme.RiviaWarningAmber
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunSettingsScreen(
  viewModel: RiviaViewModel,
  isEditingDefaultsOnly: Boolean = false,
  onBack: () -> Unit,
) {
  val currentSettings by
    (if (isEditingDefaultsOnly) viewModel.defaultRunSettings else viewModel.currentRunSettings)
      .collectAsStateWithLifecycle()
  val cachedModels by viewModel.cachedModels.collectAsStateWithLifecycle()
  val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
  val modePresets by viewModel.modePresets.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current

  var modelDraft by remember { mutableStateOf(currentSettings.model) }
  var liveModelDraft by remember { mutableStateOf(currentSettings.liveModel) }
  var systemInstructionDraft by remember { mutableStateOf(currentSettings.systemInstruction) }
  var styleTypedDraft by remember { mutableStateOf(currentSettings.styleForTypedChat) }
  var styleLiveDraft by remember { mutableStateOf(currentSettings.styleForLiveMode) }
  var maxTokensDraft by remember {
    mutableStateOf(currentSettings.maxOutputTokens?.toString() ?: "")
  }
  var seedDraft by remember { mutableStateOf(currentSettings.seed?.toString() ?: "") }
  var topKDraft by remember { mutableStateOf(currentSettings.topK?.toString() ?: "") }
  var mapsLatDraft by remember { mutableStateOf(currentSettings.googleMapsLatitude) }
  var mapsLngDraft by remember { mutableStateOf(currentSettings.googleMapsLongitude) }
  var schemaDraft by remember { mutableStateOf(currentSettings.structuredOutputSchema) }
  var extraJsonDraft by remember { mutableStateOf(currentSettings.extraJson) }

  LaunchedEffect(currentSettings) {
    modelDraft = currentSettings.model
    liveModelDraft = currentSettings.liveModel
    systemInstructionDraft = currentSettings.systemInstruction
    styleTypedDraft = currentSettings.styleForTypedChat
    styleLiveDraft = currentSettings.styleForLiveMode
    maxTokensDraft = currentSettings.maxOutputTokens?.toString() ?: ""
    seedDraft = currentSettings.seed?.toString() ?: ""
    topKDraft = currentSettings.topK?.toString() ?: ""
    mapsLatDraft = currentSettings.googleMapsLatitude
    mapsLngDraft = currentSettings.googleMapsLongitude
    schemaDraft = currentSettings.structuredOutputSchema
    extraJsonDraft = currentSettings.extraJson
  }

  val activeModelInfo =
    remember(modelDraft, cachedModels) {
      cachedModels.firstOrNull {
        it.id.equals(modelDraft.trim().removePrefix("models/"), ignoreCase = true)
      }
    }

  val maxOutputLimit = activeModelInfo?.outputTokenLimit?.takeIf { it > 0 } ?: 65536
  val maxTempLimit = activeModelInfo?.maxTemperature ?: 2.0f

  fun buildMergedSettings(base: RunSettings = currentSettings): RunSettings {
    val parsedMaxTokens =
      maxTokensDraft.filter { it.isDigit() }.toIntOrNull()?.coerceAtMost(maxOutputLimit)
    val parsedSeed = seedDraft.filter { it.isDigit() || it == '-' }.toIntOrNull()
    val parsedTopK = topKDraft.filter { it.isDigit() }.toIntOrNull()
    val trimmedModel = modelDraft.trim().ifBlank { base.model }
    val trimmedLiveModel = liveModelDraft.trim().ifBlank { base.liveModel }
    val modeName = if (trimmedModel != base.model) "Custom" else base.activeModeName

    return base.copy(
      model = trimmedModel,
      liveModel = trimmedLiveModel,
      activeModeName = modeName,
      systemInstruction = systemInstructionDraft,
      styleForTypedChat = styleTypedDraft,
      styleForLiveMode = styleLiveDraft,
      maxOutputTokens = parsedMaxTokens,
      seed = parsedSeed,
      topK = parsedTopK,
      googleMapsLatitude = mapsLatDraft,
      googleMapsLongitude = mapsLngDraft,
      structuredOutputSchema = schemaDraft,
      extraJson = extraJsonDraft,
    )
  }

  fun updateSettings(newSettings: RunSettings) {
    if (isEditingDefaultsOnly) {
      viewModel.updateDefaultSettings(newSettings)
    } else {
      viewModel.updateCurrentChatSettings(newSettings)
    }
  }

  fun commitDraftsIfChanged() {
    val merged = buildMergedSettings(currentSettings)
    if (merged != currentSettings) {
      updateSettings(merged)
    }
  }

  val currentCommitRef by rememberUpdatedState(::commitDraftsIfChanged)

  DisposableEffect(Unit) { onDispose { currentCommitRef() } }

  BackHandler {
    commitDraftsIfChanged()
    onBack()
  }

  var showModelPickerForLive by remember { mutableStateOf<Boolean?>(null) }
  var previewJsonText by remember { mutableStateOf<String?>(null) }
  var newStopSequence by remember { mutableStateOf("") }
  var newFileStoreName by remember { mutableStateOf("") }
  var newLabelKey by remember { mutableStateOf("") }
  var newLabelValue by remember { mutableStateOf("") }
  var advancedExpanded by rememberSaveable { mutableStateOf(false) }

  // Help / Description sheet state
  var activeHelpTopic by remember { mutableStateOf<SettingHelpTopic?>(null) }
  var showFullGuideSheet by remember { mutableStateOf(false) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    // Top bar
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    Surface(color = RiviaBackground, modifier = Modifier.fillMaxWidth()) {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompactWidth = maxWidth < 600.dp
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            IconButton(
              onClick = {
                commitDraftsIfChanged()
                onBack()
              },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = RiviaText,
              )
            }
            Text(
              text = if (isEditingDefaultsOnly) "Lab defaults" else "Run settings",
              style = MaterialTheme.typography.titleLarge,
              color = RiviaText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
          }

          if (isCompactWidth) {
            Box {
              IconButton(
                onClick = { overflowMenuExpanded = true },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("run_settings_overflow_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "More options",
                  tint = RiviaText,
                )
              }
              DropdownMenu(
                expanded = overflowMenuExpanded,
                onDismissRequest = { overflowMenuExpanded = false },
              ) {
                DropdownMenuItem(
                  text = { Text("Guide") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Info,
                      contentDescription = null,
                      tint = RiviaTextTertiary,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  onClick = {
                    overflowMenuExpanded = false
                    showFullGuideSheet = true
                  },
                  modifier = Modifier.testTag("overflow_guide_item"),
                )
                if (!isEditingDefaultsOnly) {
                  DropdownMenuItem(
                    text = { Text("Save default") },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = null,
                        tint = RiviaIconDefault,
                        modifier = Modifier.size(18.dp),
                      )
                    },
                    onClick = {
                      overflowMenuExpanded = false
                      val merged = buildMergedSettings()
                      updateSettings(merged)
                      viewModel.saveCurrentSettingsAsDefault(merged)
                    },
                    modifier = Modifier.testTag("save_as_default_button"),
                  )
                }
                DropdownMenuItem(
                  text = { Text("Reset") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Restore,
                      contentDescription = null,
                      tint = RiviaIconDefault,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  onClick = {
                    overflowMenuExpanded = false
                    focusManager.clearFocus()
                    if (isEditingDefaultsOnly) {
                      viewModel.updateDefaultSettings(RunSettings())
                    } else {
                      viewModel.resetCurrentSettingsToDefault()
                    }
                  },
                  modifier = Modifier.testTag("reset_to_defaults_button"),
                )
              }
            }
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              GuideHeaderButton(onClick = { showFullGuideSheet = true })

              if (!isEditingDefaultsOnly) {
                TextButton(
                  onClick = {
                    val merged = buildMergedSettings()
                    updateSettings(merged)
                    viewModel.saveCurrentSettingsAsDefault(merged)
                  },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("save_as_default_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = "Save as default for new chats",
                    tint = RiviaIconDefault,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Save default", color = RiviaTextPrimary, fontSize = 12.sp)
                }
              }
              TextButton(
                onClick = {
                  focusManager.clearFocus()
                  if (isEditingDefaultsOnly) {
                    viewModel.updateDefaultSettings(RunSettings())
                  } else {
                    viewModel.resetCurrentSettingsToDefault()
                  }
                },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("reset_to_defaults_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Restore,
                  contentDescription = "Reset to defaults",
                  tint = RiviaIconDefault,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", color = RiviaTextSecondary, fontSize = 12.sp)
              }
            }
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Mode Presets (Segmented selector)
      item {
        SettingsCardSection(
          title = "Mode Presets",
          onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("mode_presets") },
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = RiviaRaisedSurface,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              modePresets.forEach { preset ->
                val isSelected = currentSettings.activeModeName == preset.name
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color =
                    if (isSelected) RiviaMint
                    else androidx.compose.ui.graphics.Color.Transparent,
                  modifier =
                    Modifier.weight(1f)
                      .clip(RoundedCornerShape(10.dp))
                      .clickable {
                        modelDraft = preset.model
                        updateSettings(
                          buildMergedSettings().copy(
                            model = preset.model,
                            thinkingLevel = preset.thinkingLevel,
                            activeModeName = preset.name,
                          )
                        )
                      },
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    Text(
                      text = preset.name,
                      color = if (isSelected) RiviaOnAccent else RiviaTextPrimary,
                      fontWeight = FontWeight.SemiBold,
                      style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                      text = preset.thinkingLevel,
                      color =
                        if (isSelected) RiviaOnAccent else RiviaTextSecondary,
                      fontFamily = ChivoMonoFontFamily,
                      fontSize = 10.sp,
                    )
                  }
                }
              }
            }
          }
          Text(
            text =
              "Active: ${currentSettings.activeModeName} · ${currentSettings.model} (${currentSettings.thinkingLevel})",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaMutedText,
          )
        }
      }

      // 2. Models
      item {
        SettingsCardSection(
          title = "Models",
          onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("models") },
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (cachedModels.isEmpty()) "No cached models yet — tap Refresh"
                else "${cachedModels.size} models cached",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
              modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            OutlinedButton(
              onClick = { viewModel.refreshGeminiModels() },
              enabled = !isLoadingModels,
              border = BorderStroke(1.dp, RiviaBorder),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = RiviaSurface2,
                  contentColor = RiviaTextPrimary,
                ),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh model list",
                tint = RiviaIconDefault,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isLoadingModels) "Refreshing..." else "Refresh models",
                color = RiviaTextPrimary,
                fontSize = 12.sp,
              )
            }
          }

          OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            label = { Text("Typed Chat Model") },
            trailingIcon = {
              TextButton(onClick = { showModelPickerForLive = false }) {
                Text("Pick", color = RiviaTextPrimary)
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  commitDraftsIfChanged()
                  focusManager.clearFocus()
                }
              ),
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )

          if (activeModelInfo != null) {
            Text(
              text =
                "${activeModelInfo.displayName} · In: ${activeModelInfo.inputTokenLimit} · Out: ${activeModelInfo.outputTokenLimit}",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaTextSecondary,
            )
          }

          OutlinedTextField(
            value = liveModelDraft,
            onValueChange = { liveModelDraft = it },
            label = { Text("Live Voice Model") },
            trailingIcon = {
              TextButton(onClick = { showModelPickerForLive = true }) {
                Text("Pick", color = RiviaTextPrimary)
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  commitDraftsIfChanged()
                  focusManager.clearFocus()
                }
              ),
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )
        }
      }

      // 3. Thinking & Reasoning
      item {
        val allowedThinking =
          remember(modelDraft) { RunSettings.allowedThinkingLevels(modelDraft) }
        val allowedLiveThinking =
          remember(liveModelDraft) { RunSettings.allowedThinkingLevels(liveModelDraft) }

        SettingsCardSection(
          title = "Thinking & Reasoning",
          onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("thinking") },
        ) {
          Text(
            text = "Typed chat thinking effort",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            allowedThinking.forEach { level ->
              val label = if (level == "model_default") "Model default" else level
              val selected = currentSettings.thinkingLevel == level
              OptionChip(
                label = label,
                selected = selected,
                onClick = {
                  updateSettings(
                    buildMergedSettings().copy(
                      thinkingLevel = level,
                      activeModeName = "Custom",
                    )
                  )
                },
              )
            }
          }

          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Live voice thinking effort",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            allowedLiveThinking.forEach { level ->
              val label = if (level == "model_default") "Model default" else level
              val selected = currentSettings.liveThinkingLevel == level
              OptionChip(
                label = label,
                selected = selected,
                onClick = {
                  updateSettings(buildMergedSettings().copy(liveThinkingLevel = level))
                },
              )
            }
          }

          Spacer(modifier = Modifier.height(2.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("Thought summaries", color = RiviaText)
              Text(
                "Show collapsible reasoning steps above replies",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("auto", "none").forEach { mode ->
                OptionChip(
                  label = mode,
                  selected = currentSettings.thoughtSummaries == mode,
                  onClick = {
                    updateSettings(buildMergedSettings().copy(thoughtSummaries = mode))
                  },
                )
              }
            }
          }
        }
      }

      // 4. System Instructions & Styles
      item {
        SettingsCardSection(
          title = "System Instructions & Styles",
          onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("system_instructions") },
        ) {
          OutlinedTextField(
            value = systemInstructionDraft,
            onValueChange = { systemInstructionDraft = it },
            label = { Text("System instructions") },
            minLines = 2,
            maxLines = 6,
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )
          OutlinedTextField(
            value = styleTypedDraft,
            onValueChange = { styleTypedDraft = it },
            label = { Text("Style for typed chat") },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )
          OutlinedTextField(
            value = styleLiveDraft,
            onValueChange = { styleLiveDraft = it },
            label = { Text("Style for Live voice") },
            minLines = 2,
            maxLines = 6,
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )
        }
      }

      // 5. Capabilities & Tools (Clean labels without raw JSON)
      item {
        val tabletToolsUnavailable by
          viewModel.tabletToolsUnavailable.collectAsStateWithLifecycle()

        SettingsCardSection(
          title = "Capabilities & Tools",
          onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("tools") },
        ) {
          // Tablet tools (Bots Pass 1 Section 3)
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Tablet tools", color = RiviaText, fontWeight = FontWeight.Medium)
                Text(
                  "Enable ask_bot and get_tablet_battery from your tablet server",
                  style = MaterialTheme.typography.bodySmall,
                  color = RiviaMutedText,
                )
              }
              Switch(
                checked = currentSettings.tabletToolsEnabled,
                onCheckedChange = {
                  updateSettings(buildMergedSettings().copy(tabletToolsEnabled = it))
                },
                colors = riviaSwitchColors(),
                modifier = Modifier.testTag("tablet_tools_switch"),
              )
            }
            if (currentSettings.tabletToolsEnabled && tabletToolsUnavailable) {
              Text(
                text = "Tablet tools unavailable",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaWarningAmber,
                modifier =
                  Modifier.padding(top = 4.dp).testTag("tablet_tools_unavailable_label"),
              )
            }
          }

          // Google Search
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("Google Search", color = RiviaText, fontWeight = FontWeight.Medium)
              Text(
                "Ground answers with live web or image search",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
            }
            Switch(
              checked = currentSettings.googleSearchEnabled,
              onCheckedChange = {
                updateSettings(buildMergedSettings().copy(googleSearchEnabled = it))
              },
              colors = riviaSwitchColors(),
            )
          }
          if (currentSettings.googleSearchEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("web", "image").forEach { mode ->
                OptionChip(
                  label = "Search: $mode",
                  selected = currentSettings.googleSearchMode == mode,
                  onClick = {
                    updateSettings(buildMergedSettings().copy(googleSearchMode = mode))
                  },
                )
              }
            }
          }

          // URL Context
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("URL Context", color = RiviaText, fontWeight = FontWeight.Medium)
              Text(
                "Fetch and read web pages linked in your prompt",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
            }
            Switch(
              checked = currentSettings.urlContextEnabled,
              onCheckedChange = {
                updateSettings(buildMergedSettings().copy(urlContextEnabled = it))
              },
              colors = riviaSwitchColors(),
            )
          }

          // Code execution
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("Code Execution", color = RiviaText, fontWeight = FontWeight.Medium)
              Text(
                "Run Python in a sandbox for math and data analysis",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
            }
            Switch(
              checked = currentSettings.codeExecutionEnabled,
              onCheckedChange = {
                updateSettings(buildMergedSettings().copy(codeExecutionEnabled = it))
              },
              colors = riviaSwitchColors(),
            )
          }

          // Google Maps
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("Google Maps", color = RiviaText, fontWeight = FontWeight.Medium)
              Text(
                "Look up places, distances, and local businesses",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
            }
            Switch(
              checked = currentSettings.googleMapsEnabled,
              onCheckedChange = {
                updateSettings(buildMergedSettings().copy(googleMapsEnabled = it))
              },
              colors = riviaSwitchColors(),
            )
          }
          if (currentSettings.googleMapsEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = mapsLatDraft,
                onValueChange = { mapsLatDraft = it },
                label = { Text("Latitude") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                  KeyboardActions(
                    onDone = {
                      commitDraftsIfChanged()
                      focusManager.clearFocus()
                    }
                  ),
                modifier =
                  Modifier.weight(1f)
                    .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
                colors = riviaTextFieldColors(),
              )
              OutlinedTextField(
                value = mapsLngDraft,
                onValueChange = { mapsLngDraft = it },
                label = { Text("Longitude") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                  KeyboardActions(
                    onDone = {
                      commitDraftsIfChanged()
                      focusManager.clearFocus()
                    }
                  ),
                modifier =
                  Modifier.weight(1f)
                    .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
                colors = riviaTextFieldColors(),
              )
            }
          }

          // File Search
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("File Search", color = RiviaText, fontWeight = FontWeight.Medium)
              Text(
                "Query your indexed Gemini file vector stores",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
            }
            Switch(
              checked = currentSettings.fileSearchEnabled,
              onCheckedChange = {
                updateSettings(buildMergedSettings().copy(fileSearchEnabled = it))
              },
              colors = riviaSwitchColors(),
            )
          }
          if (currentSettings.fileSearchEnabled) {
            if (currentSettings.fileSearchStoreNames.isNotEmpty()) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                currentSettings.fileSearchStoreNames.forEach { storeName ->
                  RemovableChip(
                    label = storeName,
                    onRemove = {
                      updateSettings(
                        buildMergedSettings().copy(
                          fileSearchStoreNames =
                            currentSettings.fileSearchStoreNames - storeName
                        )
                      )
                    },
                  )
                }
              }
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = newFileStoreName,
                onValueChange = { newFileStoreName = it },
                label = { Text("Store name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                  KeyboardActions(
                    onDone = {
                      if (newFileStoreName.isNotBlank()) {
                        updateSettings(
                          buildMergedSettings().copy(
                            fileSearchStoreNames =
                              (currentSettings.fileSearchStoreNames + newFileStoreName.trim())
                                .distinct()
                          )
                        )
                        newFileStoreName = ""
                      }
                      focusManager.clearFocus()
                    }
                  ),
                modifier = Modifier.weight(1f),
                colors = riviaTextFieldColors(),
              )
              IconButton(
                onClick = {
                  if (newFileStoreName.isNotBlank()) {
                    updateSettings(
                      buildMergedSettings().copy(
                        fileSearchStoreNames =
                          (currentSettings.fileSearchStoreNames + newFileStoreName.trim())
                            .distinct()
                      )
                    )
                    newFileStoreName = ""
                  }
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = "Add file search store name",
                  tint = RiviaIconDefault,
                )
              }
            }
          }
        }
      }

      // 6. Collapsible Advanced Parameters Toggle Card
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = RiviaSurface2,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .clickable { advancedExpanded = !advancedExpanded }
              .testTag("toggle_advanced_parameters"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f).padding(end = 12.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = RiviaIconDefault,
                modifier = Modifier.size(20.dp),
              )
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Advanced Parameters",
                  style = MaterialTheme.typography.titleMedium,
                  color = RiviaTextPrimary,
                )
                Text(
                  text =
                    "Sampling, tokens, tool choice, JSON schema, safety, service tier & extra JSON",
                  style = MaterialTheme.typography.bodySmall,
                  color = RiviaTextSecondary,
                )
              }
            }
            Icon(
              imageVector =
                if (advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
              contentDescription =
                if (advancedExpanded) "Collapse advanced parameters"
                else "Expand advanced parameters",
              tint = RiviaIconDefault,
            )
          }
        }
      }

      // Expanded Advanced Parameters Sections
      item {
        AnimatedVisibility(visible = advancedExpanded) {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 6a. Output & Sampling
            val samplingSupported =
              remember(modelDraft) { RunSettings.isSamplingSupported(modelDraft) }

            SettingsCardSection(
              title = "Output & Sampling",
              onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("output_sampling") },
            ) {
              OutlinedTextField(
                value = maxTokensDraft,
                onValueChange = { txt -> maxTokensDraft = txt.filter { it.isDigit() } },
                label = {
                  Text("Max output tokens (includes thinking · max $maxOutputLimit)")
                },
                keyboardOptions =
                  KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                  ),
                keyboardActions =
                  KeyboardActions(
                    onDone = {
                      commitDraftsIfChanged()
                      focusManager.clearFocus()
                    }
                  ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
                colors = riviaTextFieldColors(),
              )

              Text(
                text = "Stop sequences",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
              if (currentSettings.stopSequences.isNotEmpty()) {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  currentSettings.stopSequences.forEach { seq ->
                    RemovableChip(
                      label = seq,
                      onRemove = {
                        updateSettings(
                          buildMergedSettings().copy(
                            stopSequences = currentSettings.stopSequences - seq
                          )
                        )
                      },
                    )
                  }
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = newStopSequence,
                  onValueChange = { newStopSequence = it },
                  label = { Text("Add stop sequence") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                  keyboardActions =
                    KeyboardActions(
                      onDone = {
                        if (newStopSequence.isNotEmpty()) {
                          updateSettings(
                            buildMergedSettings().copy(
                              stopSequences =
                                (currentSettings.stopSequences + newStopSequence).distinct()
                            )
                          )
                          newStopSequence = ""
                        }
                        focusManager.clearFocus()
                      }
                    ),
                  modifier = Modifier.weight(1f),
                  colors = riviaTextFieldColors(),
                )
                IconButton(
                  onClick = {
                    if (newStopSequence.isNotEmpty()) {
                      updateSettings(
                        buildMergedSettings().copy(
                          stopSequences =
                            (currentSettings.stopSequences + newStopSequence).distinct()
                        )
                      )
                      newStopSequence = ""
                    }
                  },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add stop sequence",
                    tint = RiviaIconDefault,
                  )
                }
              }

              OutlinedTextField(
                value = seedDraft,
                onValueChange = { txt ->
                  seedDraft = txt.filter { it.isDigit() || it == '-' }
                },
                label = { Text("Seed (optional integer)") },
                keyboardOptions =
                  KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                  ),
                keyboardActions =
                  KeyboardActions(
                    onDone = {
                      commitDraftsIfChanged()
                      focusManager.clearFocus()
                    }
                  ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
                colors = riviaTextFieldColors(),
              )

              HorizontalDivider(color = RiviaBorder)
              Text(
                text = "Temperature, Top-P & Top-K",
                style = MaterialTheme.typography.titleSmall,
                color = RiviaText,
              )
              if (!samplingSupported) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = RiviaRaisedSurface,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp),
                  ) {
                    StatusShape(status = StatusType.WARNING, size = 8.dp)
                    Text(
                      text = "Ignored by this model",
                      style = MaterialTheme.typography.bodyMedium,
                      fontFamily = ChivoMonoFontFamily,
                      color = RiviaWarningAmber,
                    )
                  }
                }
              } else {
                val tempVal = currentSettings.temperature ?: 1.0f
                Text(
                  text =
                    "Temperature: ${"%.2f".format(tempVal)} (max ${"%.1f".format(maxTempLimit)})",
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = ChivoMonoFontFamily,
                  color = RiviaText,
                )
                Slider(
                  value = tempVal.coerceIn(0f, maxTempLimit),
                  onValueChange = { updateSettings(buildMergedSettings().copy(temperature = it)) },
                  valueRange = 0f..maxTempLimit,
                  colors =
                    SliderDefaults.colors(
                      thumbColor = RiviaTextPrimary,
                      activeTrackColor = RiviaTextSecondary,
                      inactiveTrackColor = RiviaBorder,
                    ),
                )

                val topPVal = currentSettings.topP ?: 0.95f
                Text(
                  text = "Top-P: ${"%.2f".format(topPVal)}",
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = ChivoMonoFontFamily,
                  color = RiviaText,
                )
                Slider(
                  value = topPVal.coerceIn(0f, 1f),
                  onValueChange = { updateSettings(buildMergedSettings().copy(topP = it)) },
                  valueRange = 0f..1f,
                  colors =
                    SliderDefaults.colors(
                      thumbColor = RiviaTextPrimary,
                      activeTrackColor = RiviaTextSecondary,
                      inactiveTrackColor = RiviaBorder,
                    ),
                )

                OutlinedTextField(
                  value = topKDraft,
                  onValueChange = { txt -> topKDraft = txt.filter { it.isDigit() } },
                  label = { Text("Top-K") },
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Number,
                      imeAction = ImeAction.Done,
                    ),
                  keyboardActions =
                    KeyboardActions(
                      onDone = {
                        commitDraftsIfChanged()
                        focusManager.clearFocus()
                      }
                    ),
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
                  colors = riviaTextFieldColors(),
                )
              }

              HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))
              Text(
                text = "Tool choice policy",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                listOf("auto", "any", "none", "validated").forEach { tc ->
                  OptionChip(
                    label = tc,
                    selected = currentSettings.toolChoice == tc,
                    onClick = { updateSettings(buildMergedSettings().copy(toolChoice = tc)) },
                  )
                }
              }
            }

            // 6b. Structured Output
            val validation =
              remember(
                schemaDraft,
                currentSettings.hasAnyToolEnabled(),
                modelDraft,
              ) {
                RunSettings.validateSchema(
                  schemaJson = schemaDraft,
                  hasToolsEnabled = currentSettings.hasAnyToolEnabled(),
                  modelId = modelDraft,
                )
              }

            SettingsCardSection(
              title = "Structured Output",
              onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("structured_output") },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text("Enforce JSON Schema", color = RiviaText)
                  Text(
                    "Constrain replies to a custom JSON schema",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiviaMutedText,
                  )
                }
                Switch(
                  checked = currentSettings.structuredOutputEnabled,
                  onCheckedChange = {
                    updateSettings(buildMergedSettings().copy(structuredOutputEnabled = it))
                  },
                  colors = riviaSwitchColors(),
                )
              }

              if (currentSettings.structuredOutputEnabled) {
                OutlinedTextField(
                  value = schemaDraft,
                  onValueChange = { schemaDraft = it },
                  label = { Text("JSON Schema") },
                  minLines = 3,
                  maxLines = 8,
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
                  colors = riviaTextFieldColors(),
                )
                Text(
                  text =
                    "Note: minLength, maxLength and uniqueItems are ignored. Combining a schema with tools works only on Gemini 3+ models.",
                  style = MaterialTheme.typography.bodySmall,
                  color = RiviaMutedText,
                )
                if (!validation.isValid && validation.error != null) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    StatusShape(status = StatusType.ERROR, size = 10.dp)
                    Text(
                      text = validation.error,
                      style = MaterialTheme.typography.bodySmall,
                      color = RiviaErrorRed,
                    )
                  }
                }
                validation.warnings.forEach { warn ->
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    StatusShape(status = StatusType.WARNING, size = 10.dp)
                    Text(
                      text = warn,
                      style = MaterialTheme.typography.bodySmall,
                      color = RiviaWarningAmber,
                    )
                  }
                }
              }
            }

            // 6c. Safety Settings
            val thresholds =
              listOf(
                "model_default",
                "off",
                "block_only_high",
                "block_medium_and_above",
                "block_low_and_above",
              )

            SettingsCardSection(
              title = "Safety Settings",
              onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("safety_settings") },
            ) {
              Text("Method", style = MaterialTheme.typography.bodySmall, color = RiviaMutedText)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("severity", "probability").forEach { method ->
                  OptionChip(
                    label = method,
                    selected = currentSettings.safetyMethod == method,
                    onClick = { updateSettings(buildMergedSettings().copy(safetyMethod = method)) },
                  )
                }
              }

              SafetyCategorySelector(
                title = "Hate speech",
                selected = currentSettings.safetyHateSpeech,
                options = thresholds,
                onSelect = {
                  updateSettings(buildMergedSettings().copy(safetyHateSpeech = it))
                },
              )
              SafetyCategorySelector(
                title = "Dangerous content",
                selected = currentSettings.safetyDangerousContent,
                options = thresholds,
                onSelect = {
                  updateSettings(buildMergedSettings().copy(safetyDangerousContent = it))
                },
              )
              SafetyCategorySelector(
                title = "Harassment",
                selected = currentSettings.safetyHarassment,
                options = thresholds,
                onSelect = {
                  updateSettings(buildMergedSettings().copy(safetyHarassment = it))
                },
              )
              SafetyCategorySelector(
                title = "Sexually explicit",
                selected = currentSettings.safetySexuallyExplicit,
                options = thresholds,
                onSelect = {
                  updateSettings(buildMergedSettings().copy(safetySexuallyExplicit = it))
                },
              )
              SafetyCategorySelector(
                title = "Jailbreak",
                selected = currentSettings.safetyJailbreak,
                options = thresholds,
                onSelect = { updateSettings(buildMergedSettings().copy(safetyJailbreak = it)) },
              )
            }

            // 6d. Service Tier & Storage
            SettingsCardSection(
              title = "Service Tier & Storage",
              onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("service_tier") },
            ) {
              Text(
                text =
                  "standard · flex (discounted, asynchronous) · priority (low latency)",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("standard", "flex", "priority").forEach { tier ->
                  OptionChip(
                    label = tier,
                    selected = currentSettings.serviceTier == tier,
                    onClick = { updateSettings(buildMergedSettings().copy(serviceTier = tier)) },
                  )
                }
              }

              HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text("Store on server", color = RiviaText)
                  Text(
                    "Chains turns on the server and enables background mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiviaMutedText,
                  )
                }
                Switch(
                  checked = currentSettings.store,
                  onCheckedChange = {
                    updateSettings(
                      buildMergedSettings().copy(
                        store = it,
                        background = if (!it) false else currentSettings.background,
                      )
                    )
                  },
                  colors = riviaSwitchColors(),
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text(
                    "Background execution (typed chat)",
                    color = if (currentSettings.store) RiviaText else RiviaMutedText,
                  )
                  Text(
                    "Polls long-running turns in the background until completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiviaMutedText,
                  )
                }
                Switch(
                  checked = currentSettings.store && currentSettings.background,
                  enabled = currentSettings.store,
                  onCheckedChange = {
                    updateSettings(buildMergedSettings().copy(background = it))
                  },
                  colors = riviaSwitchColors(),
                )
              }
            }

            // 6e. Labels & Extra JSON
            SettingsCardSection(
              title = "Labels & Extra JSON",
              onHelpClick = { activeHelpTopic = RiviaSettingsGuide.findTopic("advanced_json") },
            ) {
              Text(
                text = "Custom metadata labels",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
              if (currentSettings.labels.isNotEmpty()) {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  currentSettings.labels.forEach { (k, v) ->
                    RemovableChip(
                      label = "$k: $v",
                      onRemove = {
                        updateSettings(
                          buildMergedSettings().copy(labels = currentSettings.labels - k)
                        )
                      },
                    )
                  }
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                OutlinedTextField(
                  value = newLabelKey,
                  onValueChange = { newLabelKey = it },
                  label = { Text("Key") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f),
                  colors = riviaTextFieldColors(),
                )
                OutlinedTextField(
                  value = newLabelValue,
                  onValueChange = { newLabelValue = it },
                  label = { Text("Value") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f),
                  colors = riviaTextFieldColors(),
                )
                IconButton(
                  onClick = {
                    if (newLabelKey.isNotBlank()) {
                      val nextMap = currentSettings.labels.toMutableMap()
                      nextMap[newLabelKey.trim()] = newLabelValue.trim()
                      updateSettings(buildMergedSettings().copy(labels = nextMap))
                      newLabelKey = ""
                      newLabelValue = ""
                    }
                  },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add label",
                    tint = RiviaIconDefault,
                  )
                }
              }

              HorizontalDivider(color = RiviaBorder)

              OutlinedTextField(
                value = extraJsonDraft,
                onValueChange = { extraJsonDraft = it },
                label = { Text("Extra JSON (merged into request body)") },
                placeholder = { Text("{\"custom_field\": true}", color = RiviaTextTertiary) },
                minLines = 2,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
                colors = riviaTextFieldColors(),
              )
            }
          }
        }
      }

      // 7. Always-accessible Request JSON Preview Button
      item {
        Button(
          onClick = {
            val merged = buildMergedSettings()
            if (merged != currentSettings) {
              updateSettings(merged)
            }
            scope.launch {
              previewJsonText = viewModel.buildRedactedRequestPreview(merged)
            }
          },
          shape = RoundedCornerShape(16.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaMint,
              contentColor = RiviaOnAccent,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("preview_request_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = "Preview request JSON",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Preview Request Payload (Keys Redacted)", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }

  // Contextual Help & Examples Bottom Sheet
  if (activeHelpTopic != null || showFullGuideSheet) {
    SettingHelpBottomSheet(
      topic = activeHelpTopic,
      allTopics = if (showFullGuideSheet) RiviaSettingsGuide.runSettingsTopics else null,
      sheetTitle = "Run Settings Guide & Examples",
      onDismiss = {
        activeHelpTopic = null
        showFullGuideSheet = false
      },
    )
  }

  // Model Picker Dialog
  showModelPickerForLive?.let { isForLive ->
    AlertDialog(
      onDismissRequest = { showModelPickerForLive = null },
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
      title = {
        Text(if (isForLive) "Select Live Voice Model" else "Select Typed Chat Model")
      },
      text = {
        if (cachedModels.isEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              "No cached models yet. Make sure your Gemini key is set and tap Refresh.",
              color = RiviaTextSecondary,
            )
            Button(
              onClick = { viewModel.refreshGeminiModels() },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = RiviaMint,
                  contentColor = RiviaOnAccent,
                ),
            ) {
              Text("Refresh Models Now")
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.height(360.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(cachedModels, key = { it.id }) { m ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = RiviaSurface2,
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                      if (isForLive) {
                        liveModelDraft = m.id
                        updateSettings(buildMergedSettings().copy(liveModel = m.id))
                      } else {
                        modelDraft = m.id
                        updateSettings(
                          buildMergedSettings().copy(model = m.id, activeModeName = "Custom")
                        )
                      }
                      showModelPickerForLive = null
                    },
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text = m.displayName,
                    color = RiviaTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = m.id,
                    color = RiviaTextSecondary,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 12.sp,
                  )
                  Text(
                    text = "In: ${m.inputTokenLimit} · Out: ${m.outputTokenLimit}",
                    color = RiviaTextTertiary,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 11.sp,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showModelPickerForLive = null }) {
          Text("Close", color = RiviaTextSecondary)
        }
      },
    )
  }

  // Preview Request Dialog (Redacted JSON)
  previewJsonText?.let { json ->
    AlertDialog(
      onDismissRequest = { previewJsonText = null },
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
      title = { Text("Request Preview (Redacted)") },
      text = {
        Column(
          modifier =
            Modifier.height(380.dp)
              .fillMaxWidth()
              .background(RiviaBackground, RoundedCornerShape(12.dp))
              .padding(12.dp)
              .verticalScroll(rememberScrollState())
              .horizontalScroll(rememberScrollState())
        ) {
          Text(
            text = json,
            fontFamily = ChivoMonoFontFamily,
            fontSize = 12.sp,
            color = RiviaTextPrimary,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { copyToClipboard(context, "Request JSON", json) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaMint,
              contentColor = RiviaOnAccent,
            ),
        ) {
          Text("Copy JSON")
        }
      },
      dismissButton = {
        TextButton(onClick = { previewJsonText = null }) {
          Text("Close", color = RiviaTextSecondary)
        }
      },
    )
  }
}

@Composable
fun commitOnFocusLostModifier(onCommit: () -> Unit): Modifier {
  var hadFocus by remember { mutableStateOf(false) }
  return Modifier.onFocusChanged { focusState ->
    if (focusState.isFocused) {
      hadFocus = true
    } else if (hadFocus) {
      hadFocus = false
      onCommit()
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SafetyCategorySelector(
  title: String,
  selected: String,
  options: List<String>,
  onSelect: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = RiviaText,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      options.forEach { opt ->
        OptionChip(
          label = if (opt == "model_default") "Model default" else opt,
          selected = selected == opt,
          onClick = { onSelect(opt) },
        )
      }
    }
  }
}

@Composable
fun SettingsCardSection(
  title: String,
  onHelpClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = RiviaSurface2,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = RiviaTextPrimary,
          modifier = Modifier.weight(1f),
        )
        if (onHelpClick != null) {
          InfoHelpIconButton(
            contentDescription = "Explain $title and show examples",
            onClick = onHelpClick,
          )
        }
      }
      content()
    }
  }
}

@Composable
fun OptionChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (selected) RiviaMint else RiviaSurface2,
    border = BorderStroke(1.dp, if (selected) RiviaMint else RiviaBorder),
    modifier =
      Modifier.clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick),
  ) {
    Text(
      text = label,
      color = if (selected) RiviaOnAccent else RiviaTextPrimary,
      fontFamily = ChivoMonoFontFamily,
      fontSize = 12.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
  }
}

@Composable
fun RemovableChip(
  label: String,
  onRemove: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = RiviaSurface2,
    border = BorderStroke(1.dp, RiviaBorder),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        color = RiviaTextPrimary,
        fontFamily = ChivoMonoFontFamily,
        fontSize = 12.sp,
      )
      IconButton(
        onClick = onRemove,
        modifier = Modifier.size(28.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Remove $label",
          tint = RiviaIconDefault,
          modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

@Composable
fun riviaSwitchColors() =
  SwitchDefaults.colors(
    checkedThumbColor = RiviaOnAccent,
    checkedTrackColor = RiviaMint,
    checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    uncheckedThumbColor = RiviaTextTertiary,
    uncheckedTrackColor = RiviaBorder,
    uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledCheckedThumbColor = RiviaOnAccent,
    disabledCheckedTrackColor = RiviaMint,
    disabledUncheckedThumbColor = RiviaTextTertiary,
    disabledUncheckedTrackColor = RiviaBorder,
  )
