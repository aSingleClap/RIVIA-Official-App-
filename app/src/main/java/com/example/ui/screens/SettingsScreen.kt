package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.isAvailableOnDevice
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RequestLogEntity
import com.example.network.ElevenLabsClient
import com.example.plugin.PluginRegistry
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.GoogleFontProvider
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaText
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  viewModel: RiviaViewModel,
  onOpenDefaultRunSettings: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current

  BackHandler {
    focusManager.clearFocus()
    onBack()
  }

  // Fix #15: Show "Font provider: OK" or "Font provider: failed" in Settings,
  // from GoogleFontProvider.isAvailableOnDevice(context)
  val fontProviderOk =
    remember(context) {
      runCatching { GoogleFontProvider.isAvailableOnDevice(context) }.getOrDefault(false)
    }

  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  val tabs = listOf("Keys & Data", "Voice", "Plug-ins", "Defaults", "Request Log")

  val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
  val isErrorBanner by viewModel.isErrorBanner.collectAsStateWithLifecycle()
  val audioError by viewModel.audioPlayerManager.lastAudioError.collectAsStateWithLifecycle()
  val audioNotice by viewModel.audioPlayerManager.lastAudioNotice.collectAsStateWithLifecycle()

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
              focusManager.clearFocus()
              onBack()
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to chat",
              tint = RiviaText,
            )
          }
          Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = RiviaText,
          )
        }

        // Fix #15: Font provider status badge
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier =
            Modifier.padding(end = 8.dp)
              .background(RiviaRaisedSurface, RoundedCornerShape(8.dp))
              .border(1.dp, RiviaBorder, RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("font_provider_status"),
        ) {
          StatusShape(
            status = if (fontProviderOk) StatusType.OK else StatusType.WARNING,
            size = 7.dp,
          )
          Text(
            text = if (fontProviderOk) "Font provider: OK" else "Font provider: failed",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChivoMonoFontFamily,
            color = if (fontProviderOk) RiviaMint else RiviaMutedText,
          )
        }
      }
    }

    // Tabs
    ScrollableTabRow(
      selectedTabIndex = selectedTab,
      containerColor = RiviaSurface,
      contentColor = RiviaMint,
      edgePadding = 12.dp,
      divider = { HorizontalDivider(color = RiviaBorder) },
    ) {
      tabs.forEachIndexed { idx, title ->
        Tab(
          selected = selectedTab == idx,
          onClick = {
            focusManager.clearFocus()
            selectedTab = idx
          },
          modifier = Modifier.minimumInteractiveComponentSize(),
          text = {
            Text(
              text = title,
              color = if (selectedTab == idx) RiviaMint else RiviaMutedText,
              fontWeight = if (selectedTab == idx) FontWeight.SemiBold else FontWeight.Normal,
            )
          },
        )
      }
    }

    // Plain API Error / Status Banner
    val activeBanner = audioError ?: audioNotice ?: statusBanner
    if (!activeBanner.isNullOrBlank()) {
      val err = audioError != null || (audioNotice == null && isErrorBanner)
      Surface(color = RiviaRaisedSurface, modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            StatusShape(
              status = if (err) StatusType.ERROR else StatusType.OK,
              size = 10.dp,
            )
            Text(
              text = activeBanner,
              style = MaterialTheme.typography.bodySmall,
              color = if (err) RiviaErrorRed else RiviaText,
            )
          }
          TextButton(onClick = { viewModel.clearStatusBanner() }) {
            Text("Dismiss", color = RiviaMutedText, fontSize = 12.sp)
          }
        }
      }
      HorizontalDivider(color = RiviaBorder)
    }

    when (selectedTab) {
      0 -> KeysAndBackupTab(viewModel = viewModel, fontProviderOk = fontProviderOk)
      1 -> VoiceSettingsTab(viewModel = viewModel)
      2 -> PluginsTab()
      3 ->
        DefaultsLauncherTab(
          onOpenDefaultRunSettings = onOpenDefaultRunSettings,
          fontProviderOk = fontProviderOk,
        )
      4 -> RequestLogTab(viewModel = viewModel)
    }
  }
}

@Composable
private fun KeysAndBackupTab(
  viewModel: RiviaViewModel,
  fontProviderOk: Boolean,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val scope = rememberCoroutineScope()
  val savedGeminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
  val savedElevenKey by viewModel.elevenLabsKey.collectAsStateWithLifecycle()
  val cachedModels by viewModel.cachedModels.collectAsStateWithLifecycle()
  val personalVoices by viewModel.personalVoices.collectAsStateWithLifecycle()
  val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
  val isLoadingVoices by viewModel.isLoadingVoices.collectAsStateWithLifecycle()

  // Fix #6: Keep typed text in local state and save only when the field loses focus,
  // on IME Done, or when leaving the screen. Never round-trip keystrokes through DataStore.
  var geminiDraft by remember { mutableStateOf(savedGeminiKey) }
  var elevenDraft by remember { mutableStateOf(savedElevenKey) }
  var showGeminiPlain by remember { mutableStateOf(false) }
  var showElevenPlain by remember { mutableStateOf(false) }

  LaunchedEffect(savedGeminiKey) { geminiDraft = savedGeminiKey }
  LaunchedEffect(savedElevenKey) { elevenDraft = savedElevenKey }

  fun commitGeminiKeyIfChanged() {
    if (geminiDraft.trim() != savedGeminiKey) {
      viewModel.viewModelScopeSaveGeminiKey(geminiDraft)
    }
  }

  fun commitElevenKeyIfChanged() {
    if (elevenDraft.trim() != savedElevenKey) {
      viewModel.viewModelScopeSaveElevenKey(elevenDraft)
    }
  }

  val commitBothRef by rememberUpdatedState {
    commitGeminiKeyIfChanged()
    commitElevenKeyIfChanged()
  }

  DisposableEffect(Unit) {
    onDispose { commitBothRef() }
  }

  // Export all chats to JSON file launcher
  val exportLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      if (uri != null) {
        scope.launch {
          try {
            val json = viewModel.exportAllChatsToJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
              out.write(json.toByteArray(Charsets.UTF_8))
            }
            viewModel.showNotice("Exported all chats to JSON.")
          } catch (e: Exception) {
            viewModel.showError("Export failed: ${e.message}")
          }
        }
      }
    }

  // Import chats from JSON file launcher
  val importLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        scope.launch {
          try {
            val text =
              context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
              } ?: ""
            if (text.isNotBlank()) {
              viewModel.importChatsFromJson(text)
            }
          } catch (e: Exception) {
            viewModel.showError("Import failed: ${e.message}")
          }
        }
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    // Gemini API Key
    item {
      SettingsCardSection(title = "Gemini API Key") {
        Text(
          text =
            "Encrypted with an AES-256 key held in the Android Keystore before saving to DataStore. Excluded from backups and redacted in previews and logs.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        OutlinedTextField(
          value = geminiDraft,
          onValueChange = { geminiDraft = it },
          label = { Text("Gemini API Key") },
          visualTransformation =
            if (showGeminiPlain) VisualTransformation.None
            else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(
              onClick = { showGeminiPlain = !showGeminiPlain },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector =
                  if (showGeminiPlain) Icons.Outlined.VisibilityOff
                  else Icons.Outlined.Visibility,
                contentDescription =
                  if (showGeminiPlain) "Hide Gemini key" else "Show Gemini key",
                tint = RiviaMutedText,
              )
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions =
            KeyboardActions(
              onDone = {
                commitGeminiKeyIfChanged()
                focusManager.clearFocus()
              }
            ),
          modifier =
            Modifier.fillMaxWidth()
              .then(commitOnFocusLostModifier { commitGeminiKeyIfChanged() })
              .testTag("gemini_api_key_input"),
          colors = riviaTextFieldColors(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            StatusShape(
              status =
                if (geminiDraft.isNotBlank()) StatusType.OK else StatusType.WARNING,
              size = 8.dp,
            )
            Text(
              text =
                if (cachedModels.isNotEmpty()) "${cachedModels.size} models verified"
                else if (geminiDraft.isNotBlank()) "Key saved in Keystore"
                else "No key entered",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaMutedText,
            )
          }

          Button(
            onClick = {
              focusManager.clearFocus()
              viewModel.refreshGeminiModels(geminiDraft)
            },
            enabled = !isLoadingModels,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaMint,
                contentColor = RiviaBackground,
              ),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("test_gemini_key_button"),
          ) {
            Text(
              if (isLoadingModels) "Testing..." else "Test (List Models)",
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // ElevenLabs API Key
    item {
      SettingsCardSection(title = "ElevenLabs API Key") {
        Text(
          text =
            "Encrypted with Android Keystore AES-256 and never logged. Used for custom voice read-aloud and Live voice streaming.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        OutlinedTextField(
          value = elevenDraft,
          onValueChange = { elevenDraft = it },
          label = { Text("ElevenLabs API Key") },
          visualTransformation =
            if (showElevenPlain) VisualTransformation.None
            else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(
              onClick = { showElevenPlain = !showElevenPlain },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector =
                  if (showElevenPlain) Icons.Outlined.VisibilityOff
                  else Icons.Outlined.Visibility,
                contentDescription =
                  if (showElevenPlain) "Hide ElevenLabs key" else "Show ElevenLabs key",
                tint = RiviaMutedText,
              )
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions =
            KeyboardActions(
              onDone = {
                commitElevenKeyIfChanged()
                focusManager.clearFocus()
              }
            ),
          modifier =
            Modifier.fillMaxWidth()
              .then(commitOnFocusLostModifier { commitElevenKeyIfChanged() })
              .testTag("elevenlabs_api_key_input"),
          colors = riviaTextFieldColors(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            StatusShape(
              status =
                if (elevenDraft.isNotBlank()) StatusType.OK else StatusType.WARNING,
              size = 8.dp,
            )
            Text(
              text =
                if (personalVoices.isNotEmpty()) "${personalVoices.size} personal voices loaded"
                else if (elevenDraft.isNotBlank()) "Key saved in Keystore"
                else "No key entered",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaMutedText,
            )
          }

          Button(
            onClick = {
              focusManager.clearFocus()
              viewModel.refreshElevenLabsVoicesAndModels(elevenDraft)
            },
            enabled = !isLoadingVoices,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaMint,
                contentColor = RiviaBackground,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("test_elevenlabs_key_button"),
          ) {
            Text(
              if (isLoadingVoices) "Testing..." else "Test (List Voices)",
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // Export / Import Chats JSON (Section 8)
    item {
      SettingsCardSection(title = "Chat History Backup (JSON)") {
        Text(
          text =
            "Export all chats, messages, and raw interaction steps to a portable JSON file, or import one back.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { exportLauncher.launch("rivia_chats_export.json") },
            border = androidx.compose.foundation.BorderStroke(1.dp, RiviaMint),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("export_chats_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Download,
              contentDescription = "Export chats to JSON",
              tint = RiviaMint,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export JSON", color = RiviaMint)
          }

          OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            border = androidx.compose.foundation.BorderStroke(1.dp, RiviaBorder),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("import_chats_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Upload,
              contentDescription = "Import chats from JSON",
              tint = RiviaText,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Import JSON", color = RiviaText)
          }
        }
      }
    }

    // Fix #15: Font Provider status card
    item {
      SettingsCardSection(title = "Typography & Font Provider") {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          StatusShape(
            status = if (fontProviderOk) StatusType.OK else StatusType.WARNING,
            size = 10.dp,
          )
          Text(
            text = if (fontProviderOk) "Font provider: OK" else "Font provider: failed",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChivoMonoFontFamily,
            color = if (fontProviderOk) RiviaMint else RiviaMutedText,
          )
        }
        Text(
          text =
            "Chivo (sans) and Chivo Mono (monospace) via Google Fonts provider with system fallback.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSettingsTab(viewModel: RiviaViewModel) {
  val focusManager = LocalFocusManager.current
  val voicePrefs by viewModel.voicePreferences.collectAsStateWithLifecycle()
  val personalVoices by viewModel.personalVoices.collectAsStateWithLifecycle()
  val elevenModels by viewModel.elevenModels.collectAsStateWithLifecycle()
  val isLoadingVoices by viewModel.isLoadingVoices.collectAsStateWithLifecycle()

  // Fix #6: Keep typed text in local state and save only when the field loses focus,
  // on IME Done, or when leaving the screen.
  var voiceIdDraft by remember { mutableStateOf(voicePrefs.voiceId) }
  var sttLanguageDraft by remember { mutableStateOf(voicePrefs.sttLanguage) }

  LaunchedEffect(voicePrefs.voiceId) { voiceIdDraft = voicePrefs.voiceId }
  LaunchedEffect(voicePrefs.sttLanguage) { sttLanguageDraft = voicePrefs.sttLanguage }

  fun commitVoiceIdIfChanged() {
    val trimmed = voiceIdDraft.trim()
    if (trimmed != voicePrefs.voiceId) {
      viewModel.setCustomVoiceId(trimmed)
    }
  }

  fun commitSttLanguageIfChanged() {
    val trimmed = sttLanguageDraft.trim().ifBlank { "en-US" }
    if (trimmed != voicePrefs.sttLanguage) {
      viewModel.updateVoicePrefs { it.copy(sttLanguage = trimmed) }
    }
  }

  val commitVoiceDraftsRef by rememberUpdatedState {
    commitVoiceIdIfChanged()
    commitSttLanguageIfChanged()
  }

  DisposableEffect(Unit) {
    onDispose { commitVoiceDraftsRef() }
  }

  // Load voices & models automatically if key is present and lists are empty
  LaunchedEffect(Unit) {
    if (personalVoices.isEmpty() && viewModel.elevenLabsKey.value.isNotBlank()) {
      viewModel.refreshElevenLabsVoicesAndModels()
    }
  }

  val isReadAloudV4OrTurbo =
    voicePrefs.readAloudModelId == "eleven_v4" ||
      voicePrefs.readAloudModelId == "eleven_v4_turbo"
  val isLiveV4OrTurbo =
    voicePrefs.liveModelId == "eleven_v4" || voicePrefs.liveModelId == "eleven_v4_turbo"

  // Section 5: Style and speed are hidden for eleven_v4 and eleven_v4_turbo.
  val showStyleAndSpeed = !isReadAloudV4OrTurbo || !isLiveV4OrTurbo
  // Section 5: clone_strength (0 to 1) appears for professional clones on v4.
  val showCloneStrength =
    voicePrefs.isProfessionalClone &&
      (voicePrefs.readAloudModelId == "eleven_v4" || voicePrefs.liveModelId == "eleven_v4")

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    // 1. Personal Voice Picker & Voice ID Paste
    item {
      SettingsCardSection(title = "Custom ElevenLabs Voice") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "GET /v2/voices?voice_type=personal",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaMutedText,
          )
          OutlinedButton(
            onClick = { viewModel.refreshElevenLabsVoicesAndModels() },
            enabled = !isLoadingVoices,
            border = androidx.compose.foundation.BorderStroke(1.dp, RiviaMint),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Outlined.Refresh,
              contentDescription = "Refresh personal voices",
              tint = RiviaMint,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              if (isLoadingVoices) "Loading..." else "Load Voices",
              color = RiviaMint,
              fontSize = 12.sp,
            )
          }
        }

        if (personalVoices.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            personalVoices.forEach { v ->
              val isSelected = v.voiceId == voicePrefs.voiceId
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) RiviaRaisedSurface else RiviaBackground,
                modifier =
                  Modifier.fillMaxWidth()
                    .border(
                      1.dp,
                      if (isSelected) RiviaMint else RiviaBorder,
                      RoundedCornerShape(10.dp),
                    )
                    .clickable {
                      voiceIdDraft = v.voiceId
                      viewModel.selectElevenLabsVoice(v)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = v.name,
                      color = RiviaText,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text = "${v.category} · ${v.voiceId}",
                      color = RiviaMutedText,
                      fontFamily = ChivoMonoFontFamily,
                      fontSize = 11.sp,
                    )
                  }
                  if (!v.previewUrl.isNullOrBlank()) {
                    IconButton(
                      onClick = { viewModel.audioPlayerManager.playPreviewUrl(v.previewUrl) },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Preview voice ${v.name}",
                        tint = RiviaMint,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = voiceIdDraft,
          onValueChange = { voiceIdDraft = it },
          label = { Text("Or paste Voice ID") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions =
            KeyboardActions(
              onDone = {
                commitVoiceIdIfChanged()
                focusManager.clearFocus()
              }
            ),
          modifier =
            Modifier.fillMaxWidth()
              .then(commitOnFocusLostModifier { commitVoiceIdIfChanged() })
              .testTag("voice_id_input"),
          colors = riviaTextFieldColors(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Professional voice clone (enables clone_strength on eleven_v4)",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = voicePrefs.isProfessionalClone,
            onCheckedChange = { isPro ->
              viewModel.updateVoicePrefs { it.copy(isProfessionalClone = isPro) }
            },
            colors = riviaSwitchColors(),
          )
        }
      }
    }

    // 2. Model Pickers for Read-Aloud (default eleven_v4) and Live (default eleven_v4_turbo)
    item {
      val availableModelIds =
        remember(elevenModels) {
          val fromApi = elevenModels.map { it.modelId }
          (listOf("eleven_v4", "eleven_v4_turbo", "eleven_flash_v2_5", "eleven_multilingual_v2") +
              fromApi)
            .distinct()
        }

      SettingsCardSection(title = "ElevenLabs TTS Models") {
        Text(
          text = "Read-aloud model (default eleven_v4)",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          availableModelIds.forEach { mId ->
            OptionChip(
              label = mId,
              selected = voicePrefs.readAloudModelId == mId,
              onClick = {
                viewModel.updateVoicePrefs { it.copy(readAloudModelId = mId) }
              },
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Live mode model (default eleven_v4_turbo)",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          availableModelIds.forEach { mId ->
            OptionChip(
              label = mId,
              selected = voicePrefs.liveModelId == mId,
              onClick = { viewModel.updateVoicePrefs { it.copy(liveModelId = mId) } },
            )
          }
        }
      }
    }

    // 3. Voice Settings (stability, similarity_boost, use_speaker_boost; conditional style/speed & clone_strength)
    item {
      SettingsCardSection(title = "Voice Settings") {
        Text(
          text = "Stability: ${"%.2f".format(voicePrefs.stability)}",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaText,
        )
        Slider(
          value = voicePrefs.stability.coerceIn(0f, 1f),
          onValueChange = { v -> viewModel.updateVoicePrefs { it.copy(stability = v) } },
          valueRange = 0f..1f,
          colors =
            SliderDefaults.colors(
              thumbColor = RiviaMint,
              activeTrackColor = RiviaMint,
            ),
        )

        Text(
          text = "Similarity Boost: ${"%.2f".format(voicePrefs.similarityBoost)}",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaText,
        )
        Slider(
          value = voicePrefs.similarityBoost.coerceIn(0f, 1f),
          onValueChange = { v ->
            viewModel.updateVoicePrefs { it.copy(similarityBoost = v) }
          },
          valueRange = 0f..1f,
          colors =
            SliderDefaults.colors(
              thumbColor = RiviaMint,
              activeTrackColor = RiviaMint,
            ),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Speaker Boost (use_speaker_boost)", color = RiviaText)
          Switch(
            checked = voicePrefs.useSpeakerBoost,
            onCheckedChange = { b ->
              viewModel.updateVoicePrefs { it.copy(useSpeakerBoost = b) }
            },
            colors = riviaSwitchColors(),
          )
        }

        // Style and speed are hidden for eleven_v4 and eleven_v4_turbo
        if (showStyleAndSpeed) {
          HorizontalDivider(color = RiviaBorder)
          Text(
            text = "Style: ${"%.2f".format(voicePrefs.style)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaText,
          )
          Slider(
            value = voicePrefs.style.coerceIn(0f, 1f),
            onValueChange = { v -> viewModel.updateVoicePrefs { it.copy(style = v) } },
            valueRange = 0f..1f,
            colors =
              SliderDefaults.colors(
                thumbColor = RiviaMint,
                activeTrackColor = RiviaMint,
              ),
          )

          Text(
            text = "Speed: ${"%.2f".format(voicePrefs.speed)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaText,
          )
          Slider(
            value = voicePrefs.speed.coerceIn(0.7f, 1.2f),
            onValueChange = { v -> viewModel.updateVoicePrefs { it.copy(speed = v) } },
            valueRange = 0.7f..1.2f,
            colors =
              SliderDefaults.colors(
                thumbColor = RiviaMint,
                activeTrackColor = RiviaMint,
              ),
          )
        } else {
          Text(
            text = "Style and speed are hidden for eleven_v4 and eleven_v4_turbo.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
        }

        // clone_strength (0 to 1) appears for professional clones on v4
        if (showCloneStrength) {
          HorizontalDivider(color = RiviaBorder)
          Text(
            text = "Clone Strength (eleven_v4 professional clone): ${"%.2f".format(voicePrefs.cloneStrength)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaMint,
          )
          Slider(
            value = voicePrefs.cloneStrength.coerceIn(0f, 1f),
            onValueChange = { v ->
              viewModel.updateVoicePrefs { it.copy(cloneStrength = v) }
            },
            valueRange = 0f..1f,
            colors =
              SliderDefaults.colors(
                thumbColor = RiviaMint,
                activeTrackColor = RiviaMint,
              ),
          )
        }
      }
    }

    // 4. Normalization, Output Format (mp3_ only), Send Audio Tags, and Test Button
    item {
      SettingsCardSection(title = "Format, Normalization & Audio Tags") {
        Text(
          text = "Text normalization (apply_text_normalization)",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("auto", "on", "off").forEach { norm ->
            OptionChip(
              label = norm,
              selected = voicePrefs.textNormalization == norm,
              onClick = {
                viewModel.updateVoicePrefs { it.copy(textNormalization = norm) }
              },
            )
          }
        }

        Text(
          text = "Output format (mp3_ formats only · default mp3_44100_128)",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ElevenLabsClient.SUPPORTED_MP3_FORMATS.forEach { fmt ->
            OptionChip(
              label = fmt,
              selected = voicePrefs.outputFormat == fmt,
              onClick = { viewModel.updateVoicePrefs { it.copy(outputFormat = fmt) } },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Send audio tags", color = RiviaText)
            Text(
              "Keeps [softly], [laughs] for eleven_v3, eleven_v4 & eleven_v4_turbo while hiding them on screen.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
          Switch(
            checked = voicePrefs.sendAudioTags,
            onCheckedChange = { b ->
              viewModel.updateVoicePrefs { it.copy(sendAudioTags = b) }
            },
            colors = riviaSwitchColors(),
          )
        }

        Button(
          onClick = {
            commitVoiceIdIfChanged()
            viewModel.testElevenLabsSpeakLine(voiceIdDraft)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaMint,
              contentColor = RiviaBackground,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("test_voice_speak_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = "Test voice playback",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Test Voice (Speak one line)", fontWeight = FontWeight.SemiBold)
        }
      }
    }

    // 5. Speech-to-Text & Live Mode Options
    item {
      SettingsCardSection(title = "Speech-to-Text & Live Mode") {
        OutlinedTextField(
          value = sttLanguageDraft,
          onValueChange = { sttLanguageDraft = it },
          label = { Text("Speech-to-text language (default en-US)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions =
            KeyboardActions(
              onDone = {
                commitSttLanguageIfChanged()
                focusManager.clearFocus()
              }
            ),
          modifier =
            Modifier.fillMaxWidth()
              .then(commitOnFocusLostModifier { commitSttLanguageIfChanged() }),
          colors = riviaTextFieldColors(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Prefer on-device recognition", color = RiviaText)
          Switch(
            checked = voicePrefs.preferOnDeviceRecognition,
            onCheckedChange = { b ->
              viewModel.updateVoicePrefs { it.copy(preferOnDeviceRecognition = b) }
            },
            colors = riviaSwitchColors(),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Keep screen on in Live", color = RiviaText)
          Switch(
            checked = voicePrefs.keepScreenOnInLive,
            onCheckedChange = { b ->
              viewModel.updateVoicePrefs { it.copy(keepScreenOnInLive = b) }
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
            Text("Use earbud mic in Live (call-quality audio)", color = RiviaText)
            Text(
              "Off by default so earbuds receive full-quality media audio.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
          Switch(
            checked = voicePrefs.useEarbudMicInLive,
            onCheckedChange = { b ->
              viewModel.updateVoicePrefs { it.copy(useEarbudMicInLive = b) }
            },
            colors = riviaSwitchColors(),
          )
        }
      }
    }
  }
}

@Composable
private fun PluginsTab() {
  val plugins = remember { PluginRegistry.getAllPlugins() }

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SettingsCardSection(title = "Plug-ins") {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(RiviaRaisedSurface, RoundedCornerShape(10.dp))
            .border(1.dp, RiviaBorder, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Extension,
            contentDescription = "Tasker plug-in",
            tint = RiviaMint,
          )
          Column {
            Text(
              text = "Tasker: coming soon",
              style = MaterialTheme.typography.titleMedium,
              color = RiviaText,
            )
            Text(
              text =
                "PluginRegistry & internal entry points (askRivia, speakLine) are wired and ready.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
        }
        StatusShape(status = StatusType.OK, size = 10.dp)
      }

      if (plugins.isNotEmpty()) {
        plugins.forEach { plugin ->
          Text(
            text = "${plugin.name} (${plugin.id})",
            color = RiviaText,
            fontFamily = ChivoMonoFontFamily,
          )
        }
      }
    }
  }
}

@Composable
private fun DefaultsLauncherTab(
  onOpenDefaultRunSettings: () -> Unit,
  fontProviderOk: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SettingsCardSection(title = "Default Run Settings for New Chats") {
      Text(
        text =
          "Configure the default Gemini model, Live model, Live thinking level, system instructions, thinking level, tools, safety filters, and service tier applied to every newly created chat.",
        style = MaterialTheme.typography.bodyMedium,
        color = RiviaMutedText,
      )
      Button(
        onClick = onOpenDefaultRunSettings,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = RiviaMint,
            contentColor = RiviaBackground,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("open_default_run_settings_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Tune,
          contentDescription = "Configure defaults",
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Edit Default Run Settings", fontWeight = FontWeight.SemiBold)
      }
    }

    SettingsCardSection(title = "System Diagnostics") {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusShape(
          status = if (fontProviderOk) StatusType.OK else StatusType.WARNING,
          size = 10.dp,
        )
        Text(
          text = if (fontProviderOk) "Font provider: OK" else "Font provider: failed",
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = ChivoMonoFontFamily,
          color = if (fontProviderOk) RiviaMint else RiviaMutedText,
        )
      }
    }
  }
}

@Composable
private fun RequestLogTab(viewModel: RiviaViewModel) {
  val logs by viewModel.requestLogs.collectAsStateWithLifecycle()
  val timeFormat = remember { SimpleDateFormat("MMM d HH:mm:ss", Locale.US) }

  if (logs.isEmpty()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.Outlined.History,
        contentDescription = "Empty request log",
        tint = RiviaMutedText,
        modifier = Modifier.size(40.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "No requests logged yet",
        style = MaterialTheme.typography.titleMedium,
        color = RiviaText,
      )
      Text(
        text = "The last 100 Gemini Interactions requests will appear here.",
        style = MaterialTheme.typography.bodySmall,
        color = RiviaMutedText,
      )
    }
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(logs, key = { it.id }) { log ->
        RequestLogItemCard(log = log, formattedTime = timeFormat.format(Date(log.timestamp)))
      }
    }
  }
}

@Composable
private fun RequestLogItemCard(
  log: RequestLogEntity,
  formattedTime: String,
) {
  val statusType =
    when (log.status.lowercase()) {
      "ok" -> StatusType.OK
      "warning" -> StatusType.WARNING
      else -> StatusType.ERROR
    }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = RiviaSurface,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, RiviaBorder, RoundedCornerShape(10.dp))
        .padding(12.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          StatusShape(status = statusType, size = 10.dp)
          Text(
            text = log.requestId,
            fontFamily = ChivoMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = RiviaMint,
            fontSize = 13.sp,
          )
          Text(
            text = log.model,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaText,
            fontSize = 12.sp,
          )
        }
        Text(
          text = formattedTime,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaMutedText,
          fontSize = 11.sp,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Tier: ${log.serviceTier} · Status: ${log.status}",
          fontFamily = ChivoMonoFontFamily,
          color = RiviaMutedText,
          fontSize = 11.sp,
        )
        Text(
          text =
            "In:${log.inputTokens} Out:${log.outputTokens} Th:${log.thoughtTokens} · ${log.latencyMs}ms",
          fontFamily = ChivoMonoFontFamily,
          color = RiviaText,
          fontSize = 11.sp,
        )
      }

      if (!log.error.isNullOrBlank()) {
        Text(
          text = log.error,
          style = MaterialTheme.typography.bodySmall,
          color = RiviaErrorRed,
        )
      }
    }
  }
}
