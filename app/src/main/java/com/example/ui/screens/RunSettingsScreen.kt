package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.RunSettings
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaText
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

  // Fix #6: Keep typed text in local state and save only when the field loses focus,
  // on IME Done, or when leaving the screen. Never round-trip keystrokes through Room/DataStore.
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

  // Sync local drafts when currentSettings updates from Room/Reset/Presets
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
    val modeName =
      if (trimmedModel != base.model) "Custom" else base.activeModeName

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

  DisposableEffect(Unit) {
    onDispose { currentCommitRef() }
  }

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

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    // Top bar
    Surface(color = RiviaSurface, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            text = if (isEditingDefaultsOnly) "Default Run Settings" else "Run Settings",
            style = MaterialTheme.typography.titleLarge,
            color = RiviaText,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (!isEditingDefaultsOnly) {
            TextButton(
              onClick = {
                val merged = buildMergedSettings()
                updateSettings(merged)
                viewModel.saveCurrentSettingsAsDefault(merged)
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("save_as_default_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = "Save as default for new chats",
                tint = RiviaMint,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Save as default", color = RiviaMint, fontSize = 12.sp)
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
            modifier = Modifier.minimumInteractiveComponentSize().testTag("reset_to_defaults_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Restore,
              contentDescription = "Reset to defaults",
              tint = RiviaMutedText,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset", color = RiviaMutedText, fontSize = 12.sp)
          }
        }
      }
    }

    HorizontalDivider(color = RiviaBorder)

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // 1. Mode Chip Presets (Fast, Balanced, Deep; all editable)
      item {
        SettingsCardSection(title = "Mode Presets (Editable)") {
          Text(
            text = "Fast = gemini-3.5-flash-lite (minimal) · Balanced = gemini-3.8-flash (medium) · Deep = gemini-4-argon (high)",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            modePresets.forEach { preset ->
              val isSelected = currentSettings.activeModeName == preset.name
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) RiviaMint else RiviaSurface,
                modifier =
                  Modifier.border(1.dp, RiviaBorder, RoundedCornerShape(10.dp))
                    .clickable {
                      modelDraft = preset.model
                      updateSettings(
                        buildMergedSettings().copy(
                          model = preset.model,
                          thinkingLevel = preset.thinkingLevel,
                          activeModeName = preset.name,
                        )
                      )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
              ) {
                Column {
                  Text(
                    text = preset.name,
                    color = if (isSelected) RiviaBackground else RiviaText,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "${preset.model} · ${preset.thinkingLevel}",
                    color = if (isSelected) RiviaBackground else RiviaMutedText,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 11.sp,
                  )
                }
              }
            }
          }
        }
      }

      // 2. Models (Typed Chat model + separate Live Mode model + Refresh button)
      item {
        SettingsCardSection(title = "Models") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (cachedModels.isEmpty()) "No cached models yet — tap Refresh"
                else "${cachedModels.size} generateContent models cached",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
            OutlinedButton(
              onClick = { viewModel.refreshGeminiModels() },
              enabled = !isLoadingModels,
              border = androidx.compose.foundation.BorderStroke(1.dp, RiviaMint),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh model list",
                tint = RiviaMint,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isLoadingModels) "Refreshing..." else "Refresh models",
                color = RiviaMint,
                fontSize = 12.sp,
              )
            }
          }

          OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            label = { Text("Typed Chat Model ID") },
            trailingIcon = {
              TextButton(onClick = { showModelPickerForLive = false }) {
                Text("Pick", color = RiviaMint)
              }
            },
            singleLine = true,
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
              color = RiviaMint,
            )
          }

          OutlinedTextField(
            value = liveModelDraft,
            onValueChange = { liveModelDraft = it },
            label = { Text("Live Mode Model ID") },
            trailingIcon = {
              TextButton(onClick = { showModelPickerForLive = true }) {
                Text("Pick", color = RiviaMint)
              }
            },
            singleLine = true,
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

      // 3. System Instructions & Channel Styles
      item {
        SettingsCardSection(title = "System Instructions & Styles") {
          OutlinedTextField(
            value = systemInstructionDraft,
            onValueChange = { systemInstructionDraft = it },
            label = { Text("System instructions") },
            minLines = 2,
            maxLines = 6,
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )
          OutlinedTextField(
            value = styleTypedDraft,
            onValueChange = { styleTypedDraft = it },
            label = { Text("Style for typed chat (appended)") },
            minLines = 2,
            maxLines = 5,
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )
          OutlinedTextField(
            value = styleLiveDraft,
            onValueChange = { styleLiveDraft = it },
            label = { Text("Style for Live mode (appended)") },
            minLines = 2,
            maxLines = 6,
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )
        }
      }

      // 4. Thinking Level, Live Thinking Level & Thought Summaries (Fix #7)
      item {
        val allowedThinking =
          remember(modelDraft) {
            RunSettings.allowedThinkingLevels(modelDraft)
          }
        val allowedLiveThinking =
          remember(liveModelDraft) {
            RunSettings.allowedThinkingLevels(liveModelDraft)
          }
        SettingsCardSection(title = "Thinking") {
          Text(
            text = "Thinking level (filtered for $modelDraft)",
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

          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Live thinking level (filtered for $liveModelDraft · default minimal)",
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
                  updateSettings(
                    buildMergedSettings().copy(liveThinkingLevel = level)
                  )
                },
              )
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Thought summaries (generation_config.thinking_summaries)",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

      // 5. Max Output Tokens, Stop Sequences, Seed, Temperature / Top-P / Top-K
      item {
        val samplingSupported =
          remember(modelDraft) {
            RunSettings.isSamplingSupported(modelDraft)
          }

        SettingsCardSection(title = "Output & Sampling") {
          OutlinedTextField(
            value = maxTokensDraft,
            onValueChange = { txt ->
              maxTokensDraft = txt.filter { it.isDigit() }
            },
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
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )

          // Stop sequences as chips
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
                tint = RiviaMint,
              )
            }
          }

          // Seed
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
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )

          // Temperature, top-p and top-k: only for models older than Gemini 3.6.
          // For Gemini 3.6 and later and for Gemini 4, show "Ignored by this model" instead.
          HorizontalDivider(color = RiviaBorder)
          Text(
            text = "Temperature, Top-P & Top-K",
            style = MaterialTheme.typography.titleSmall,
            color = RiviaText,
          )
          if (!samplingSupported) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .background(RiviaSurface, RoundedCornerShape(8.dp))
                  .border(1.dp, RiviaBorder, RoundedCornerShape(8.dp))
                  .padding(12.dp),
            ) {
              StatusShape(status = StatusType.WARNING, size = 8.dp)
              Text(
                text = "Ignored by this model",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaWarningAmber,
              )
            }
          } else {
            val tempVal = currentSettings.temperature ?: 1.0f
            Text(
              text = "Temperature: ${"%.2f".format(tempVal)} (max ${"%.1f".format(maxTempLimit)})",
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
                  thumbColor = RiviaMint,
                  activeTrackColor = RiviaMint,
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
                  thumbColor = RiviaMint,
                  activeTrackColor = RiviaMint,
                ),
            )

            OutlinedTextField(
              value = topKDraft,
              onValueChange = { txt ->
                topKDraft = txt.filter { it.isDigit() }
              },
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
              modifier =
                Modifier.fillMaxWidth()
                  .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
              colors = riviaTextFieldColors(),
            )
          }
        }
      }

      // 6. Tools & Tool Choice
      item {
        SettingsCardSection(title = "Tools & Tool Choice") {
          // Google Search (web or image)
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Google Search ({\"type\":\"google_search\"})", color = RiviaText)
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
            Text("URL Context ({\"type\":\"url_context\"})", color = RiviaText)
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
            Text("Code Execution ({\"type\":\"code_execution\"})", color = RiviaText)
            Switch(
              checked = currentSettings.codeExecutionEnabled,
              onCheckedChange = {
                updateSettings(buildMergedSettings().copy(codeExecutionEnabled = it))
              },
              colors = riviaSwitchColors(),
            )
          }

          // Google Maps (latitude and longitude)
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Google Maps ({\"type\":\"google_maps\"})", color = RiviaText)
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

          // File search (store names)
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("File Search ({\"type\":\"file_search\"})", color = RiviaText)
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
                  tint = RiviaMint,
                )
              }
            }
          }

          HorizontalDivider(color = RiviaBorder)
          Text(
            text = "Tool choice (generation_config.tool_choice)",
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
      }

      // 7. Structured Output (response_format JSON schema with validator & warnings)
      item {
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

        SettingsCardSection(title = "Structured Output") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "JSON response_format schema",
              color = RiviaText,
            )
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
              modifier =
                Modifier.fillMaxWidth()
                  .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
              colors = riviaTextFieldColors(),
            )
            Text(
              text =
                "Note: minLength, maxLength and uniqueItems are ignored. Combining a schema with tools works only on Gemini 3 models.",
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
      }

      // 8. Safety Settings
      item {
        val thresholds =
          listOf(
            "model_default",
            "off",
            "block_only_high",
            "block_medium_and_above",
            "block_low_and_above",
          )

        SettingsCardSection(title = "Safety Settings") {
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
            title = "hate_speech",
            selected = currentSettings.safetyHateSpeech,
            options = thresholds,
            onSelect = { updateSettings(buildMergedSettings().copy(safetyHateSpeech = it)) },
          )
          SafetyCategorySelector(
            title = "dangerous_content",
            selected = currentSettings.safetyDangerousContent,
            options = thresholds,
            onSelect = { updateSettings(buildMergedSettings().copy(safetyDangerousContent = it)) },
          )
          SafetyCategorySelector(
            title = "harassment",
            selected = currentSettings.safetyHarassment,
            options = thresholds,
            onSelect = { updateSettings(buildMergedSettings().copy(safetyHarassment = it)) },
          )
          SafetyCategorySelector(
            title = "sexually_explicit",
            selected = currentSettings.safetySexuallyExplicit,
            options = thresholds,
            onSelect = { updateSettings(buildMergedSettings().copy(safetySexuallyExplicit = it)) },
          )
          SafetyCategorySelector(
            title = "jailbreak",
            selected = currentSettings.safetyJailbreak,
            options = thresholds,
            onSelect = { updateSettings(buildMergedSettings().copy(safetyJailbreak = it)) },
          )
        }
      }

      // 9. Service Tier & Storage
      item {
        SettingsCardSection(title = "Service Tier & Storage") {
          Text(
            text =
              "standard · flex (half price, can take 1 to 15 minutes, may be dropped) · priority (costs more, may fall back to standard)",
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

          HorizontalDivider(color = RiviaBorder)
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Store on server (store)", color = RiviaText)
              Text(
                "Off disables server-side history and background mode",
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
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "Background execution (background · typed chat only)",
                color = if (currentSettings.store) RiviaText else RiviaMutedText,
              )
              Text(
                "Polls GET /v1beta/interactions/{id} until completed",
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
      }

      // 10. Labels (key-value pairs) & Advanced (Extra JSON + Preview request)
      item {
        SettingsCardSection(title = "Labels & Advanced") {
          Text(
            text = "Labels (key-value pairs)",
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
              modifier = Modifier.weight(1f),
              colors = riviaTextFieldColors(),
            )
            OutlinedTextField(
              value = newLabelValue,
              onValueChange = { newLabelValue = it },
              label = { Text("Value") },
              singleLine = true,
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
                tint = RiviaMint,
              )
            }
          }

          HorizontalDivider(color = RiviaBorder)

          OutlinedTextField(
            value = extraJsonDraft,
            onValueChange = { extraJsonDraft = it },
            label = { Text("Extra JSON (merged into request body)") },
            placeholder = { Text("{\"custom_field\": true}", color = RiviaMutedText) },
            minLines = 2,
            maxLines = 6,
            modifier =
              Modifier.fillMaxWidth()
                .then(commitOnFocusLostModifier { commitDraftsIfChanged() }),
            colors = riviaTextFieldColors(),
          )

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
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaMint,
                contentColor = RiviaBackground,
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
            Text("Preview request (keys redacted)", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }

  // Model Picker Dialog
  showModelPickerForLive?.let { isForLive ->
    AlertDialog(
      onDismissRequest = { showModelPickerForLive = null },
      containerColor = RiviaRaisedSurface,
      titleContentColor = RiviaText,
      title = {
        Text(if (isForLive) "Select Live Mode Model" else "Select Typed Chat Model")
      },
      text = {
        if (cachedModels.isEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              "No cached models yet. Make sure your Gemini key is set and tap Refresh.",
              color = RiviaMutedText,
            )
            Button(
              onClick = { viewModel.refreshGeminiModels() },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = RiviaMint,
                  contentColor = RiviaBackground,
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
                shape = RoundedCornerShape(8.dp),
                color = RiviaSurface,
                modifier =
                  Modifier.fillMaxWidth()
                    .border(1.dp, RiviaBorder, RoundedCornerShape(8.dp))
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
                    }
                    .padding(10.dp),
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = m.displayName,
                    color = RiviaText,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = m.id,
                    color = RiviaMint,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 12.sp,
                  )
                  Text(
                    text = "In: ${m.inputTokenLimit} · Out: ${m.outputTokenLimit}",
                    color = RiviaMutedText,
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
          Text("Close", color = RiviaMutedText)
        }
      },
    )
  }

  // Preview Request Dialog (Redacted JSON)
  previewJsonText?.let { json ->
    AlertDialog(
      onDismissRequest = { previewJsonText = null },
      containerColor = RiviaRaisedSurface,
      titleContentColor = RiviaText,
      title = { Text("Request Preview (Redacted)") },
      text = {
        Column(
          modifier =
            Modifier.height(380.dp)
              .fillMaxWidth()
              .background(RiviaBackground, RoundedCornerShape(8.dp))
              .border(1.dp, RiviaBorder, RoundedCornerShape(8.dp))
              .padding(10.dp)
              .verticalScroll(rememberScrollState())
              .horizontalScroll(rememberScrollState())
        ) {
          Text(
            text = json,
            fontFamily = ChivoMonoFontFamily,
            fontSize = 12.sp,
            color = RiviaText,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { copyToClipboard(context, "Request JSON", json) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaMint,
              contentColor = RiviaBackground,
            ),
        ) {
          Text("Copy JSON")
        }
      },
      dismissButton = {
        TextButton(onClick = { previewJsonText = null }) {
          Text("Close", color = RiviaMutedText)
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
      fontFamily = ChivoMonoFontFamily,
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
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = RiviaSurface,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, RiviaBorder, RoundedCornerShape(14.dp))
        .padding(14.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = RiviaMint,
      )
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
    shape = RoundedCornerShape(8.dp),
    color = if (selected) RiviaMint else RiviaRaisedSurface,
    modifier =
      Modifier.border(
          1.dp,
          if (selected) RiviaMint else RiviaBorder,
          RoundedCornerShape(8.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 7.dp),
  ) {
    Text(
      text = label,
      color = if (selected) RiviaBackground else RiviaText,
      fontFamily = ChivoMonoFontFamily,
      fontSize = 12.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
    color = RiviaRaisedSurface,
    modifier =
      Modifier.border(1.dp, RiviaBorder, RoundedCornerShape(16.dp))
        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = label,
        color = RiviaText,
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
          tint = RiviaMutedText,
          modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

@Composable
fun riviaSwitchColors() =
  SwitchDefaults.colors(
    checkedThumbColor = RiviaBackground,
    checkedTrackColor = RiviaMint,
    uncheckedThumbColor = RiviaMutedText,
    uncheckedTrackColor = RiviaRaisedSurface,
    uncheckedBorderColor = RiviaBorder,
  )
