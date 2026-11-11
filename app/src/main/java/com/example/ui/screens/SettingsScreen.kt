package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.clip
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
import com.example.audio.EmotionModelState
import com.example.audio.MicSharingRecorder
import com.example.audio.SileroVadModelState
import com.example.data.RequestLogEntity
import com.example.data.VoiceEmotionLogEntity
import com.example.network.ElevenLabsClient
import com.example.plugin.PluginRegistry
import com.example.ui.components.GuideHeaderButton
import com.example.ui.components.RiviaSettingsGuide
import com.example.ui.components.SettingHelpBottomSheet
import com.example.ui.components.SettingHelpTopic
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.theme.AppUiMode
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaIconActive
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaSurface3
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaTextTertiary
import com.example.ui.theme.RiviaWarningAmber
import com.example.viewmodel.RiviaViewModel
import java.io.File
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
  val focusManager = LocalFocusManager.current
  val appUiMode by viewModel.appUiMode.collectAsStateWithLifecycle()

  BackHandler {
    focusManager.clearFocus()
    onBack()
  }

  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  val tabs =
    remember(appUiMode) {
      when (appUiMode) {
        AppUiMode.RIVIA -> listOf("Voice", "Backup")
        AppUiMode.LABS ->
          listOf("Keys & Data", "Voice (Labs)", "Plug-ins", "Labs & VAD", "Request Log")
        AppUiMode.DEV ->
          listOf("Keys & Data", "Voice", "Plug-ins", "Mic & VAD", "Request Log")
      }
    }
  val safeSelectedTab = selectedTab.coerceIn(0, tabs.lastIndex)
  LaunchedEffect(tabs.size) {
    if (selectedTab >= tabs.size) {
      selectedTab = 0
    }
  }

  val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
  val isErrorBanner by viewModel.isErrorBanner.collectAsStateWithLifecycle()
  val audioError by viewModel.audioPlayerManager.lastAudioError.collectAsStateWithLifecycle()
  val audioNotice by viewModel.audioPlayerManager.lastAudioNotice.collectAsStateWithLifecycle()

  var activeHelpTopic by remember { mutableStateOf<SettingHelpTopic?>(null) }
  var showFullGuideSheet by remember { mutableStateOf(false) }

  val modeBadgeLabel =
    when (appUiMode) {
      AppUiMode.RIVIA -> "RIVIA"
      AppUiMode.DEV -> "DEV · RIVIA INTERNALS"
      AppUiMode.LABS -> "LABS · SANDBOX"
    }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    // Top bar (clean header with mode badge and Guide button)
    Surface(color = RiviaBackground, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
          Surface(
            shape = RoundedCornerShape(50),
            color = RiviaSurface2,
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.testTag("settings_mode_badge"),
          ) {
            Text(
              text = modeBadgeLabel,
              color = RiviaTextSecondary,
              fontFamily = ChivoMonoFontFamily,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
          }
        }

        GuideHeaderButton(
          onClick = { showFullGuideSheet = true },
          modifier = Modifier.padding(end = 8.dp),
        )
      }
    }

    // Tabs: Voice + Backup in Rivia mode; full 5-tab bar in Dev & Labs
    ScrollableTabRow(
      selectedTabIndex = safeSelectedTab,
      containerColor = RiviaBackground,
      contentColor = RiviaTextPrimary,
      edgePadding = 12.dp,
      indicator = { tabPositions ->
        if (safeSelectedTab < tabPositions.size) {
          TabRowDefaults.SecondaryIndicator(
            modifier =
              Modifier.tabIndicatorOffset(tabPositions[safeSelectedTab])
                .testTag("settings_selected_tab_indicator"),
            color = RiviaMint,
          )
        }
      },
      divider = { HorizontalDivider(color = RiviaBorder) },
    ) {
      tabs.forEachIndexed { idx, title ->
        Tab(
          selected = safeSelectedTab == idx,
          onClick = {
            focusManager.clearFocus()
            selectedTab = idx
          },
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("settings_tab_${title.lowercase().replace(" ", "_")}"),
          text = {
            Text(
              text = title,
              color = if (safeSelectedTab == idx) RiviaTextPrimary else RiviaTextSecondary,
              fontWeight = if (safeSelectedTab == idx) FontWeight.SemiBold else FontWeight.Normal,
            )
          },
        )
      }
    }

    // Plain API Error / Status Banner
    val activeBanner = audioError ?: audioNotice ?: statusBanner
    if (!activeBanner.isNullOrBlank()) {
      val err = audioError != null || (audioNotice == null && isErrorBanner)
      Surface(
        color = RiviaSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
      ) {
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
    }

    if (appUiMode == AppUiMode.RIVIA) {
      when (safeSelectedTab) {
        0 ->
          VoiceSettingsTab(
            viewModel = viewModel,
            onShowHelp = { activeHelpTopic = RiviaSettingsGuide.findTopic(it) },
          )
        1 ->
          RiviaBackupTab(
            viewModel = viewModel,
            onShowHelp = { activeHelpTopic = RiviaSettingsGuide.findTopic(it) },
          )
      }
    } else {
      when (safeSelectedTab) {
        0 ->
          KeysAndBackupTab(
            viewModel = viewModel,
            onShowHelp = { activeHelpTopic = RiviaSettingsGuide.findTopic(it) },
          )
        1 ->
          VoiceSettingsTab(
            viewModel = viewModel,
            onShowHelp = { activeHelpTopic = RiviaSettingsGuide.findTopic(it) },
          )
        2 -> PluginsTab()
        3 ->
          DefaultsLauncherTab(
            viewModel = viewModel,
            onOpenDefaultRunSettings = onOpenDefaultRunSettings,
            onShowHelp = { activeHelpTopic = RiviaSettingsGuide.findTopic(it) },
          )
        4 -> RequestLogTab(viewModel = viewModel)
      }
    }
  }

  if (activeHelpTopic != null || showFullGuideSheet) {
    SettingHelpBottomSheet(
      topic = activeHelpTopic,
      allTopics = if (showFullGuideSheet) RiviaSettingsGuide.appSettingsTopics else null,
      sheetTitle = "Settings Guide & Examples",
      onDismiss = {
        activeHelpTopic = null
        showFullGuideSheet = false
      },
    )
  }
}

@Composable
private fun RiviaBackupTab(
  viewModel: RiviaViewModel,
  onShowHelp: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

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
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SettingsCardSection(
        title = "Chat History Backup (JSON)",
        onHelpClick = { onShowHelp("chat_backup") },
      ) {
        Text(
          text =
            "Export all chats, messages, raw interaction steps, rule events, request logs, and voice emotion turns to a portable JSON file, or import one back.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { exportLauncher.launch("rivia_chats_export.json") },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("export_chats_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Download,
              contentDescription = "Export chats to JSON",
              tint = RiviaIconDefault,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export JSON", color = RiviaTextPrimary)
          }

          OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("import_chats_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Upload,
              contentDescription = "Import chats from JSON",
              tint = RiviaIconDefault,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Import JSON", color = RiviaTextPrimary)
          }
        }
      }
    }
  }
}


@Composable
private fun KeysAndBackupTab(
  viewModel: RiviaViewModel,
  onShowHelp: (String) -> Unit,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val scope = rememberCoroutineScope()
  val appUiMode by viewModel.appUiMode.collectAsStateWithLifecycle()
  val savedGeminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
  val savedElevenKey by viewModel.elevenLabsKey.collectAsStateWithLifecycle()
  val savedTabletUrl by viewModel.tabletServerUrl.collectAsStateWithLifecycle()
  val savedTabletToken by viewModel.tabletAppToken.collectAsStateWithLifecycle()
  val tabletTestRunning by viewModel.tabletTestRunning.collectAsStateWithLifecycle()
  val tabletTestResultText by viewModel.tabletTestResultText.collectAsStateWithLifecycle()
  val tabletTestIsError by viewModel.tabletTestIsError.collectAsStateWithLifecycle()
  val cachedModels by viewModel.cachedModels.collectAsStateWithLifecycle()
  val personalVoices by viewModel.personalVoices.collectAsStateWithLifecycle()
  val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
  val isLoadingVoices by viewModel.isLoadingVoices.collectAsStateWithLifecycle()

  var geminiDraft by remember { mutableStateOf(savedGeminiKey) }
  var elevenDraft by remember { mutableStateOf(savedElevenKey) }
  var tabletUrlDraft by remember { mutableStateOf(savedTabletUrl) }
  var tabletTokenDraft by remember { mutableStateOf(savedTabletToken) }
  var showGeminiPlain by remember { mutableStateOf(false) }
  var showElevenPlain by remember { mutableStateOf(false) }
  var showTabletTokenPlain by remember { mutableStateOf(false) }

  LaunchedEffect(savedGeminiKey) { geminiDraft = savedGeminiKey }
  LaunchedEffect(savedElevenKey) { elevenDraft = savedElevenKey }
  LaunchedEffect(savedTabletUrl) { tabletUrlDraft = savedTabletUrl }
  LaunchedEffect(savedTabletToken) { tabletTokenDraft = savedTabletToken }

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

  fun commitTabletIfChanged() {
    val cleanUrl = com.example.security.SecureKeyRepository.normalizeNoWhitespace(tabletUrlDraft)
    val cleanToken =
      com.example.security.SecureKeyRepository.normalizeNoWhitespace(tabletTokenDraft)
    if (cleanUrl != savedTabletUrl || cleanToken != savedTabletToken) {
      viewModel.saveTabletSettings(cleanUrl, cleanToken)
    }
  }

  val commitBothRef by rememberUpdatedState {
    commitGeminiKeyIfChanged()
    commitElevenKeyIfChanged()
    if (appUiMode == AppUiMode.DEV) {
      commitTabletIfChanged()
    }
  }

  DisposableEffect(Unit) { onDispose { commitBothRef() } }

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
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Gemini API Key
    item {
      SettingsCardSection(
        title = "Gemini API Key",
        onHelpClick = { onShowHelp("api_keys") },
      ) {
        Text(
          text =
            "Encrypted with Android Keystore AES-256. Excluded from backups and redacted in logs.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        OutlinedTextField(
          value = geminiDraft,
          onValueChange = { geminiDraft = it },
          label = { Text("Gemini API Key") },
          shape = RoundedCornerShape(14.dp),
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
            modifier = Modifier.weight(1f).padding(end = 12.dp),
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

          OutlinedButton(
            onClick = {
              focusManager.clearFocus()
              viewModel.refreshGeminiModels(geminiDraft)
            },
            enabled = !isLoadingModels,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("test_gemini_key_button"),
          ) {
            Text(
              if (isLoadingModels) "Testing..." else "Test (List Models)",
              color = RiviaTextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // ElevenLabs API Key
    item {
      SettingsCardSection(
        title = "ElevenLabs API Key",
        onHelpClick = { onShowHelp("api_keys") },
      ) {
        Text(
          text =
            "Encrypted with Android Keystore AES-256. Used for custom voice read-aloud and Live voice streaming.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        OutlinedTextField(
          value = elevenDraft,
          onValueChange = { elevenDraft = it },
          label = { Text("ElevenLabs API Key") },
          shape = RoundedCornerShape(14.dp),
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
            modifier = Modifier.weight(1f).padding(end = 12.dp),
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

          OutlinedButton(
            onClick = {
              focusManager.clearFocus()
              viewModel.refreshElevenLabsVoicesAndModels(elevenDraft)
            },
            enabled = !isLoadingVoices,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("test_elevenlabs_key_button"),
          ) {
            Text(
              if (isLoadingVoices) "Testing..." else "Test (List Voices)",
              color = RiviaTextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // Bots Pass 1 Section 1: Tablet (Dev mode only)
    if (appUiMode == AppUiMode.DEV) {
      item {
        SettingsCardSection(
          title = "Tablet",
        ) {
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
                .then(commitOnFocusLostModifier { commitTabletIfChanged() })
                .testTag("tablet_server_url_input"),
            colors = riviaTextFieldColors(),
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
                .then(commitOnFocusLostModifier { commitTabletIfChanged() })
                .testTag("tablet_app_token_input"),
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
                modifier = Modifier.testTag("tablet_test_inline_result"),
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
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("test_tablet_connection_button"),
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
    }

    // Export / Import Chats JSON
    item {
      SettingsCardSection(
        title = "Chat History Backup (JSON)",
        onHelpClick = { onShowHelp("chat_backup") },
      ) {
        Text(
          text =
            "Export all chats, messages, raw interaction steps, rule events, request logs, and voice emotion turns to a portable JSON file, or import one back.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { exportLauncher.launch("rivia_chats_export.json") },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("export_chats_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Download,
              contentDescription = "Export chats to JSON",
              tint = RiviaIconDefault,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export JSON", color = RiviaTextPrimary)
          }

          OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("import_chats_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Upload,
              contentDescription = "Import chats from JSON",
              tint = RiviaIconDefault,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Import JSON", color = RiviaTextPrimary)
          }
        }
      }
    }

    // App signature SHA-256
    item {
      val appSignatureSha256 = remember(context) { computeInstalledAppSignatureSha256(context) }
      SettingsCardSection(
        title = "App signature SHA-256",
      ) {
        androidx.compose.foundation.text.selection.SelectionContainer {
          Text(
            text = appSignatureSha256,
            fontFamily = ChivoMonoFontFamily,
            fontSize = 12.sp,
            color = RiviaText,
            modifier = Modifier.fillMaxWidth().testTag("app_signature_sha256_text"),
          )
        }
      }
    }
  }
}

fun computeInstalledAppSignatureSha256(context: android.content.Context): String {
  return try {
    val pkgInfo =
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
          context.packageName,
          android.content.pm.PackageManager.PackageInfoFlags.of(
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong()
          ),
        )
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(
          context.packageName,
          android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
        )
      }
    val signingInfo = pkgInfo.signingInfo
    val sig =
      if (signingInfo != null) {
        if (signingInfo.hasMultipleSigners()) {
          signingInfo.apkContentsSigners?.firstOrNull()
        } else {
          signingInfo.signingCertificateHistory?.firstOrNull()
            ?: signingInfo.apkContentsSigners?.firstOrNull()
        }
      } else {
        null
      }
    if (sig == null) {
      "UNAVAILABLE"
    } else {
      val digest = java.security.MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
      digest.joinToString(":") { "%02X".format(it) }
    }
  } catch (_: Throwable) {
    "UNAVAILABLE"
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSettingsTab(
  viewModel: RiviaViewModel,
  onShowHelp: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val appUiMode by viewModel.appUiMode.collectAsStateWithLifecycle()
  val isDevOrLabs = appUiMode != AppUiMode.RIVIA
  val voicePrefs by viewModel.voicePreferences.collectAsStateWithLifecycle()
  val personalVoices by viewModel.personalVoices.collectAsStateWithLifecycle()
  val elevenModels by viewModel.elevenModels.collectAsStateWithLifecycle()
  val isLoadingVoices by viewModel.isLoadingVoices.collectAsStateWithLifecycle()

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

  DisposableEffect(Unit) { onDispose { commitVoiceDraftsRef() } }

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

  val showStyleAndSpeed = !isReadAloudV4OrTurbo || !isLiveV4OrTurbo
  val showCloneStrength =
    voicePrefs.isProfessionalClone &&
      (voicePrefs.readAloudModelId == "eleven_v4" || voicePrefs.liveModelId == "eleven_v4")

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Personal Voice Picker & Voice ID Paste
    item {
      SettingsCardSection(
        title = "Custom ElevenLabs Voice",
        onHelpClick = { onShowHelp("custom_voice") },
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (personalVoices.isEmpty()) "Load your personal voices from ElevenLabs"
              else "${personalVoices.size} personal voices available",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
          )
          OutlinedButton(
            onClick = { viewModel.refreshElevenLabsVoicesAndModels() },
            enabled = !isLoadingVoices,
            shape = RoundedCornerShape(14.dp),
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
              contentDescription = "Refresh personal voices",
              tint = RiviaIconDefault,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              if (isLoadingVoices) "Loading..." else "Load Voices",
              color = RiviaTextPrimary,
              fontSize = 12.sp,
            )
          }
        }

        if (personalVoices.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            personalVoices.forEach { v ->
              val isSelected = v.voiceId == voicePrefs.voiceId
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) RiviaSurface3 else RiviaSurface2,
                border = BorderStroke(1.dp, RiviaBorder),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                      voiceIdDraft = v.voiceId
                      viewModel.selectElevenLabsVoice(v)
                    },
              ) {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                      text = v.name,
                      color = RiviaTextPrimary,
                      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                      text = "${v.category} · ${v.voiceId}",
                      color = RiviaTextTertiary,
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
                        tint = if (isSelected) RiviaIconActive else RiviaIconDefault,
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
          label = { Text("Voice ID") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
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
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Professional voice clone", color = RiviaText)
            Text(
              "Enables clone strength tuning on eleven_v4",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
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

    // 2. Model Pickers for Read-Aloud and Live (Dev / Labs only)
    if (isDevOrLabs) {
      item {
        val availableModelIds =
          remember(elevenModels) {
            val fromApi = elevenModels.map { it.modelId }
            (listOf(
                "eleven_v4",
                "eleven_v4_turbo",
                "eleven_flash_v2_5",
                "eleven_multilingual_v2",
              ) + fromApi)
              .distinct()
          }

        SettingsCardSection(
          title = "ElevenLabs TTS Models",
          onHelpClick = { onShowHelp("tts_models") },
        ) {
          Text(
            text = "Read-aloud model (default eleven_v4)",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
            modifier = Modifier.testTag("el_models_section"),
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
    }

    // 3. Voice Settings
    item {
      SettingsCardSection(
        title = "Voice Settings",
        onHelpClick = { onShowHelp("voice_tuning") },
      ) {
        if (isDevOrLabs) {
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
                thumbColor = RiviaTextPrimary,
                activeTrackColor = RiviaTextSecondary,
                inactiveTrackColor = RiviaBorder,
              ),
            modifier = Modifier.testTag("voice_stability_slider"),
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
                thumbColor = RiviaTextPrimary,
                activeTrackColor = RiviaTextSecondary,
                inactiveTrackColor = RiviaBorder,
              ),
            modifier = Modifier.testTag("voice_similarity_slider"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Speaker Boost",
            color = RiviaText,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
          )
          Switch(
            checked = voicePrefs.useSpeakerBoost,
            onCheckedChange = { b ->
              viewModel.updateVoicePrefs { it.copy(useSpeakerBoost = b) }
            },
            colors = riviaSwitchColors(),
          )
        }

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
                thumbColor = RiviaTextPrimary,
                activeTrackColor = RiviaTextSecondary,
                inactiveTrackColor = RiviaBorder,
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
                thumbColor = RiviaTextPrimary,
                activeTrackColor = RiviaTextSecondary,
                inactiveTrackColor = RiviaBorder,
              ),
          )
        } else {
          Text(
            text = "Style and speed are managed automatically by eleven_v4 and eleven_v4_turbo.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
        }

        if (showCloneStrength) {
          HorizontalDivider(color = RiviaBorder)
          Text(
            text = "Clone Strength: ${"%.2f".format(voicePrefs.cloneStrength)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaTextPrimary,
          )
          Slider(
            value = voicePrefs.cloneStrength.coerceIn(0f, 1f),
            onValueChange = { v ->
              viewModel.updateVoicePrefs { it.copy(cloneStrength = v) }
            },
            valueRange = 0f..1f,
            colors =
              SliderDefaults.colors(
                thumbColor = RiviaTextPrimary,
                activeTrackColor = RiviaTextSecondary,
                inactiveTrackColor = RiviaBorder,
              ),
          )
        }

        if (!isDevOrLabs) {
          Button(
            onClick = {
              commitVoiceIdIfChanged()
              viewModel.testElevenLabsSpeakLine(voiceIdDraft)
            },
            shape = RoundedCornerShape(14.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaMint,
                contentColor = RiviaOnAccent,
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
    }

    // 4. Normalization, Output Format & Audio Tags (Dev / Labs only)
    if (isDevOrLabs) {
      item {
        SettingsCardSection(
          title = "Format, Normalization & Audio Tags",
          onHelpClick = { onShowHelp("audio_tags_format") },
        ) {
          Text(
            text = "Text normalization",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
            modifier = Modifier.testTag("format_normalization_audio_tags_section"),
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
            text = "Output audio quality",
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
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("Send expressive audio tags", color = RiviaText)
              Text(
                "Performs [softly], [laughs] vocally while hiding them on screen",
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
              modifier = Modifier.testTag("send_audio_tags_switch"),
            )
          }

          Button(
            onClick = {
              commitVoiceIdIfChanged()
              viewModel.testElevenLabsSpeakLine(voiceIdDraft)
            },
            shape = RoundedCornerShape(14.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaMint,
                contentColor = RiviaOnAccent,
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
    }

    // 5. Speech-to-Text & Live Mode Options
    item {
      SettingsCardSection(
        title = "Speech-to-Text & Live Mode",
        onHelpClick = { onShowHelp("stt_live_options") },
      ) {
        OutlinedTextField(
          value = sttLanguageDraft,
          onValueChange = { sttLanguageDraft = it },
          label = { Text("Speech-to-text language (default en-US)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
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
          Text(
            text = "Prefer on-device recognition",
            color = RiviaText,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
          )
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
          Text(
            text = "Keep screen on in Live",
            color = RiviaText,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
          )
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
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Use earbud mic in Live", color = RiviaText)
            Text(
              "Off by default so earbuds receive full-quality media audio",
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
  val refusedWriteTools by PluginRegistry.refusedWriteTools.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SettingsCardSection(title = "Plug-ins") {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = RiviaRaisedSurface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).padding(end = 12.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Extension,
              contentDescription = "Tasker plug-in",
              tint = RiviaIconDefault,
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

      if (refusedWriteTools.isNotEmpty()) {
        HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))
        Text(
          text = "Refused write-tool registrations (H2 guard)",
          style = MaterialTheme.typography.labelMedium,
          color = RiviaErrorRed,
        )
        refusedWriteTools.forEach { reason ->
          Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaErrorRed,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultsLauncherTab(
  viewModel: RiviaViewModel,
  onOpenDefaultRunSettings: () -> Unit,
  onShowHelp: (String) -> Unit,
) {
  val context = LocalContext.current
  val modelState by viewModel.emotionModelState.collectAsStateWithLifecycle()
  val vadModelState by viewModel.sileroVadModelState.collectAsStateWithLifecycle()
  val labMicPrefs by viewModel.labMicTestPreferences.collectAsStateWithLifecycle()
  val emotionLogs by viewModel.voiceEmotionLogs.collectAsStateWithLifecycle()
  val totalEmotionLogsCount by viewModel.totalVoiceEmotionLogsCount.collectAsStateWithLifecycle()
  val baselineSummary by viewModel.emotionBaselineSummary.collectAsStateWithLifecycle()
  val isSpeaking by viewModel.audioPlayerManager.isSpeaking.collectAsStateWithLifecycle()
  val speakingMsgId by viewModel.audioPlayerManager.speakingMessageId.collectAsStateWithLifecycle()
  val modeBRuntimeNote by MicSharingRecorder.modeBRuntimeNote.collectAsStateWithLifecycle()
  val timeFormat = remember { SimpleDateFormat("MMM d HH:mm:ss", Locale.US) }

  val scope = rememberCoroutineScope()
  val modeBSupported = remember { MicSharingRecorder.isModeBApiSupported() }
  val modeBDescription =
    remember(context, modeBRuntimeNote) { MicSharingRecorder.getModeBSupportDescription(context) }

  val exportVadJsonLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      if (uri != null) {
        scope.launch {
          try {
            val json = viewModel.exportVoiceEmotionLogsToJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
              out.write(json.toByteArray(Charsets.UTF_8))
            }
            viewModel.showNotice("Exported $totalEmotionLogsCount Live VAD/emotion turns to JSON.")
          } catch (e: Exception) {
            viewModel.showError("VAD JSON export failed: ${e.message}")
          }
        }
      }
    }

  val exportVadCsvLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
      if (uri != null) {
        scope.launch {
          try {
            val csv = viewModel.exportVoiceEmotionLogsToCsv()
            context.contentResolver.openOutputStream(uri)?.use { out ->
              out.write(csv.toByteArray(Charsets.UTF_8))
            }
            viewModel.showNotice("Exported $totalEmotionLogsCount Live VAD/emotion turns to CSV.")
          } catch (e: Exception) {
            viewModel.showError("VAD CSV export failed: ${e.message}")
          }
        }
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. MODEL: audEERING wav2vec2-large-robust-12-ft-emotion-msp-dim (Zenodo 6221127)
    item {
      SettingsCardSection(
        title = "Emotion Reader Model (ONNX)",
      ) {
        Text(
          text =
            "audEERING wav2vec2-large-robust-12-ft-emotion-msp-dim (Zenodo record 6221127). Input: 16 kHz mono float32 [1, samples]. Scores long turns in 4 s windows as they arrive and averages A/D/V.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )

        when (val st = modelState) {
          is EmotionModelState.NotDownloaded -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StatusShape(status = StatusType.WARNING, size = 10.dp)
              Text(
                text = "Not downloaded yet (will download automatically on first Live turn)",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaText,
              )
            }
            OutlinedButton(
              onClick = { viewModel.downloadEmotionModel(forceRedownload = false) },
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
                  .testTag("download_emotion_model_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "Download emotion model",
                tint = RiviaIconDefault,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Download Model Now", color = RiviaTextPrimary, fontWeight = FontWeight.SemiBold)
            }
          }
          is EmotionModelState.Downloading -> {
            val mbDown = st.downloadedBytes.toDouble() / (1024.0 * 1024.0)
            val mbTotal =
              if (st.totalBytes > 0L) st.totalBytes.toDouble() / (1024.0 * 1024.0) else -1.0
            val pct = if (st.progress >= 0f) (st.progress * 100f).toInt() else -1

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StatusShape(status = StatusType.WARNING, size = 10.dp)
              Text(
                text =
                  if (mbTotal > 0.0 && pct >= 0) {
                    "Downloading Zenodo 6221127... $pct% (${"%.1f".format(mbDown)} / ${"%.1f".format(mbTotal)} MB)"
                  } else {
                    "Downloading Zenodo 6221127... (${"%.1f".format(mbDown)} MB)"
                  },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaTextPrimary,
              )
            }

            if (st.progress >= 0f) {
              LinearProgressIndicator(
                progress = { st.progress.coerceIn(0f, 1f) },
                color = RiviaTextPrimary,
                trackColor = RiviaBorder,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("emotion_model_progress_bar"),
              )
            } else {
              LinearProgressIndicator(
                color = RiviaTextPrimary,
                trackColor = RiviaBorder,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("emotion_model_progress_bar"),
              )
            }
          }
          is EmotionModelState.Extracting -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StatusShape(status = StatusType.WARNING, size = 10.dp)
              Text(
                text = "Extracting .onnx and .yaml into app-private storage...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaTextPrimary,
              )
            }
            LinearProgressIndicator(
              color = RiviaTextPrimary,
              trackColor = RiviaBorder,
              modifier =
                Modifier.fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .testTag("emotion_model_progress_bar"),
            )
          }
          is EmotionModelState.Ready -> {
            val sizeMb = st.onnxSizeBytes.toDouble() / (1024.0 * 1024.0)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StatusShape(status = StatusType.OK, size = 10.dp)
              Column {
                Text(
                  text = "Ready · ${st.onnxFile.name} (${"%.1f".format(sizeMb)} MB)",
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = ChivoMonoFontFamily,
                  fontWeight = FontWeight.SemiBold,
                  color = RiviaTextPrimary,
                )
                Text(
                  text = st.yamlSummary,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = ChivoMonoFontFamily,
                  color = RiviaMutedText,
                )
              }
            }
          }
          is EmotionModelState.Error -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StatusShape(status = StatusType.ERROR, size = 10.dp)
              Text(
                text = st.message,
                style = MaterialTheme.typography.bodySmall,
                color = RiviaErrorRed,
              )
            }
            OutlinedButton(
              onClick = { viewModel.downloadEmotionModel(forceRedownload = true) },
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
                  .testTag("retry_emotion_model_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Retry model download",
                tint = RiviaIconDefault,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Retry Model Download", color = RiviaTextPrimary)
            }
          }
        }

        HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))

        // Emotion Baseline Summary (excludes Mode A and short clips < 1.5s of speech)
        Text(
          text = "Emotion Baseline (Mode B, >= 1.5s speech)",
          style = MaterialTheme.typography.labelMedium,
          color = RiviaText,
        )

        val baselineAdvText =
          if (
            baselineSummary.eligibleCount > 0 &&
              baselineSummary.meanArousal != null &&
              baselineSummary.meanDominance != null &&
              baselineSummary.meanValence != null
          ) {
            val ratePart =
              baselineSummary.meanSpeakingRateWps?.let {
                " · Rate: ${"%.2f".format(Locale.US, it)} w/s"
              } ?: ""
            "Mean A: ${"%.2f".format(Locale.US, baselineSummary.meanArousal)} · D: ${"%.2f".format(Locale.US, baselineSummary.meanDominance)} · V: ${"%.2f".format(Locale.US, baselineSummary.meanValence)}$ratePart (n=${baselineSummary.eligibleCount})"
          } else {
            "No eligible Mode B turns (>= 1.5s speech) since baseline reset."
          }

        Text(
          text = baselineAdvText,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = ChivoMonoFontFamily,
          fontWeight = FontWeight.SemiBold,
          color = RiviaTextPrimary,
        )

        Text(
          text =
            "Excluded (${baselineSummary.windowDays}d window): ${baselineSummary.excludedModeACount} Mode A · ${baselineSummary.excludedShortCount} short (<1.5s speech) · ${baselineSummary.excludedSilentCount} silent · ${baselineSummary.excludedCalibratedCount} calibrated" +
              if (baselineSummary.resetAtTimestamp > 0L) {
                " · Reset: ${timeFormat.format(Date(baselineSummary.resetAtTimestamp))}"
              } else {
                ""
              },
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaMutedText,
        )

        if (baselineSummary.latestTagSummary.isNotBlank()) {
          Text(
            text = "Latest tag: ${baselineSummary.latestTagSummary}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaMutedText,
          )
        }

        OutlinedButton(
          onClick = { viewModel.resetEmotionBaseline() },
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, RiviaBorder),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = RiviaSurface2,
              contentColor = RiviaTextPrimary,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("reset_emotion_baseline_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "Reset emotion baseline",
            tint = RiviaIconDefault,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Reset emotion baseline",
            color = RiviaTextPrimary,
            fontFamily = ChivoMonoFontFamily,
            fontSize = 12.sp,
          )
        }
      }
    }

    // 2. MIC TEST: "Record alongside recognizer" (default ON), Mode A / Mode B (default B), and "Save clips"
    item {
      SettingsCardSection(
        title = "Mic-Sharing Test",
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Record alongside recognizer", color = RiviaText, fontWeight = FontWeight.SemiBold)
            Text(
              "Capture 16 kHz mono audio during Live SpeechRecognizer turns (log only; never alters prompts, replies, or voice)",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
          Switch(
            checked = labMicPrefs.recordAlongsideRecognizer,
            onCheckedChange = { enabled ->
              viewModel.updateLabMicTestPrefs { it.copy(recordAlongsideRecognizer = enabled) }
            },
            colors = riviaSwitchColors(),
            modifier = Modifier.testTag("record_alongside_recognizer_switch"),
          )
        }

        HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))

        Text(
          text = "Mic capture mode (Live default: Mode B)",
          style = MaterialTheme.typography.labelMedium,
          color = RiviaText,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OptionChip(
            label = "Mode A (Google capture · no AudioRecord)",
            selected = labMicPrefs.micTestMode != "B",
            onClick = { viewModel.updateLabMicTestPrefs { it.copy(micTestMode = "A") } },
          )
          OptionChip(
            label =
              if (modeBSupported) "Mode B (EXTRA_AUDIO_SOURCE pipe · Default)"
              else "Mode B (Unsupported on API < 33)",
            selected = labMicPrefs.micTestMode == "B",
            onClick = {
              if (modeBSupported) {
                viewModel.updateLabMicTestPrefs { it.copy(micTestMode = "B") }
              } else {
                viewModel.showNotice(
                  "Mode B requires Android 13 (API 33+) for RecognizerIntent.EXTRA_AUDIO_SOURCE."
                )
              }
            },
          )
        }

        Text(
          text =
            if (labMicPrefs.micTestMode == "B") {
              "Mode B (Default): Records with AudioRecord (AudioSource.VOICE_RECOGNITION, 16 kHz mono), applies automatic gain (-20 dBFS target, max +20 dB, limiter) ONLY to the SpeechRecognizer pipe while VAD & emotion model keep raw audio.\n$modeBDescription"
            } else {
              "Mode A (Google capture · excluded from emotion baseline): Does not start the app's AudioRecord at all, so SpeechRecognizer gets the mic directly. Turns have no voice scores (acoustic = null, words_vs_scores = \"no_scores\")."
            },
          style = MaterialTheme.typography.bodySmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaMutedText,
        )

        HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Save clips", color = RiviaText, fontWeight = FontWeight.SemiBold)
            Text(
              "Keeps the last 10 Live mic clips as 16 kHz mono WAV in app-private storage for playback",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
          Switch(
            checked = labMicPrefs.saveWavClips,
            onCheckedChange = { save ->
              viewModel.updateLabMicTestPrefs { it.copy(saveWavClips = save) }
            },
            colors = riviaSwitchColors(),
            modifier = Modifier.testTag("save_wav_clips_switch"),
          )
        }

        HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
              "Call stopListening() after pipe close",
              color = RiviaText,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              "OFF (default): lets pipe EOF finish recognition without triggering client abort onError(5). Turn ON to compare with explicit stopListening().",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
          Switch(
            checked = labMicPrefs.callStopListeningOnPipeClose,
            onCheckedChange = { callStop ->
              viewModel.updateLabMicTestPrefs {
                it.copy(callStopListeningOnPipeClose = callStop)
              }
            },
            colors = riviaSwitchColors(),
            modifier = Modifier.testTag("call_stop_listening_on_pipe_close_switch"),
          )
        }
      }
    }

    // 2b. SILERO VAD (End of Turn): silero_vad.onnx + exposed threshold/duration numbers
    item {
      SettingsCardSection(
        title = "Silero VAD (End of Turn)",
      ) {
        Text(
          text =
            "Runs silero_vad.onnx (MIT, ~2 MB) on the same ONNX Runtime on 16 kHz audio in 512-sample (32 ms) chunks, keeping state between chunks. When Silero ends the turn: stops writing, closes pipe write end, waits up to 3 s for a non-empty final result from onResults/onSegmentResults and prefers it over the partial, or falls back to last partial (\"used partial\").",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaMutedText,
        )

        when (val vst = vadModelState) {
          is SileroVadModelState.Ready -> {
            val mb = vst.sizeBytes.toDouble() / (1024.0 * 1024.0)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StatusShape(status = StatusType.OK, size = 10.dp)
              Text(
                text = "Ready · ${vst.onnxFile.name} (${"%.2f".format(Locale.US, mb)} MB)",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChivoMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = RiviaTextPrimary,
              )
            }
          }
          is SileroVadModelState.Downloading -> {
            val mbDown = vst.downloadedBytes.toDouble() / (1024.0 * 1024.0)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StatusShape(status = StatusType.WARNING, size = 10.dp)
              Text(
                text = "Downloading silero_vad.onnx... (${"%.2f".format(Locale.US, mbDown)} MB)",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaTextPrimary,
              )
            }
          }
          is SileroVadModelState.NotDownloaded -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "silero_vad.onnx not downloaded yet",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaWarningAmber,
              )
              OutlinedButton(
                onClick = { viewModel.downloadSileroVadModel(forceRedownload = true) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiviaBorder),
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    containerColor = RiviaSurface2,
                    contentColor = RiviaTextPrimary,
                  ),
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Text("Download VAD", color = RiviaTextPrimary, fontSize = 12.sp)
              }
            }
          }
          is SileroVadModelState.Error -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = vst.message,
                style = MaterialTheme.typography.bodySmall,
                color = RiviaErrorRed,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
              )
              OutlinedButton(
                onClick = { viewModel.downloadSileroVadModel(forceRedownload = true) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiviaBorder),
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    containerColor = RiviaSurface2,
                    contentColor = RiviaTextPrimary,
                  ),
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Text("Retry VAD", color = RiviaTextPrimary, fontSize = 12.sp)
              }
            }
          }
        }

        HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))

        // Summary line of the 4 VAD numbers
        Text(
          text =
            "Speech starts at prob >= ${"%.2f".format(Locale.US, labMicPrefs.vadStartThreshold)} for ${labMicPrefs.vadStartDurationMs} ms · Turn ends after ${labMicPrefs.vadEndSilenceMs} ms below ${"%.2f".format(Locale.US, labMicPrefs.vadEndThreshold)}",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextSecondary,
        )

        // 1. Speech start prob threshold (default 0.50)
        Text(
          text = "Speech start probability threshold: ${"%.2f".format(Locale.US, labMicPrefs.vadStartThreshold)}",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaText,
        )
        Slider(
          value = labMicPrefs.vadStartThreshold.coerceIn(0.10f, 0.90f),
          onValueChange = { v ->
            val rounded = (kotlin.math.round(v * 20f) / 20f).coerceIn(0.10f, 0.90f)
            viewModel.updateLabMicTestPrefs { it.copy(vadStartThreshold = rounded) }
          },
          valueRange = 0.10f..0.90f,
          colors =
            SliderDefaults.colors(
              thumbColor = RiviaTextPrimary,
              activeTrackColor = RiviaTextSecondary,
              inactiveTrackColor = RiviaBorder,
            ),
          modifier = Modifier.testTag("vad_start_prob_slider"),
        )

        // 2. Speech start duration ms (default 250 ms)
        Text(
          text = "Speech start duration: ${labMicPrefs.vadStartDurationMs} ms",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaText,
        )
        Slider(
          value = labMicPrefs.vadStartDurationMs.toFloat().coerceIn(64f, 1000f),
          onValueChange = { v ->
            val stepped = ((v / 10f).toInt() * 10).coerceIn(64, 1000)
            viewModel.updateLabMicTestPrefs { it.copy(vadStartDurationMs = stepped) }
          },
          valueRange = 64f..1000f,
          colors =
            SliderDefaults.colors(
              thumbColor = RiviaTextPrimary,
              activeTrackColor = RiviaTextSecondary,
              inactiveTrackColor = RiviaBorder,
            ),
          modifier = Modifier.testTag("vad_start_duration_slider"),
        )

        // 3. Turn end prob threshold (default 0.35)
        Text(
          text = "Turn end probability threshold: ${"%.2f".format(Locale.US, labMicPrefs.vadEndThreshold)}",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaText,
        )
        Slider(
          value = labMicPrefs.vadEndThreshold.coerceIn(0.05f, 0.80f),
          onValueChange = { v ->
            val rounded = (kotlin.math.round(v * 20f) / 20f).coerceIn(0.05f, 0.80f)
            viewModel.updateLabMicTestPrefs { it.copy(vadEndThreshold = rounded) }
          },
          valueRange = 0.05f..0.80f,
          colors =
            SliderDefaults.colors(
              thumbColor = RiviaTextPrimary,
              activeTrackColor = RiviaTextSecondary,
              inactiveTrackColor = RiviaBorder,
            ),
          modifier = Modifier.testTag("vad_end_prob_slider"),
        )

        // 4. Turn end silence duration ms (default 700 ms)
        Text(
          text = "Turn end silence duration: ${labMicPrefs.vadEndSilenceMs} ms",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaText,
        )
        Slider(
          value = labMicPrefs.vadEndSilenceMs.toFloat().coerceIn(200f, 2500f),
          onValueChange = { v ->
            val stepped = ((v / 50f).toInt() * 50).coerceIn(200, 2500)
            viewModel.updateLabMicTestPrefs { it.copy(vadEndSilenceMs = stepped) }
          },
          valueRange = 200f..2500f,
          colors =
            SliderDefaults.colors(
              thumbColor = RiviaTextPrimary,
              activeTrackColor = RiviaTextSecondary,
              inactiveTrackColor = RiviaBorder,
            ),
          modifier = Modifier.testTag("vad_end_silence_slider"),
        )

        OutlinedButton(
          onClick = {
            viewModel.updateLabMicTestPrefs {
              it.copy(
                vadStartThreshold = 0.50f,
                vadStartDurationMs = 250,
                vadEndThreshold = 0.35f,
                vadEndSilenceMs = 700,
              )
            }
          },
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, RiviaBorder),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = RiviaSurface2,
              contentColor = RiviaTextPrimary,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("reset_vad_defaults_button"),
        ) {
          Text(
            text = "Reset VAD Numbers (0.50 / 250 ms / 0.35 / 700 ms)",
            fontFamily = ChivoMonoFontFamily,
            fontSize = 11.sp,
            color = RiviaTextPrimary,
          )
        }
      }
    }

    // 3. Lab Defaults for New Lab Chats
    item {
      SettingsCardSection(
        title = "Lab Defaults for New Lab Chats",
        onHelpClick = { onShowHelp("defaults_logs") },
      ) {
        Text(
          text =
            "Configure the default Gemini model, Live model, thinking level, system instructions, tools, safety filters, and service tier applied to every newly created Lab chat. Mode presets here apply to Lab chats only.",
          style = MaterialTheme.typography.bodyMedium,
          color = RiviaMutedText,
        )
        OutlinedButton(
          onClick = onOpenDefaultRunSettings,
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
              .testTag("open_default_run_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = "Configure Lab defaults",
            tint = RiviaIconDefault,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Edit Lab Default Run Settings", color = RiviaTextPrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    }

    // 4. LOGGING: Latest 50 of N turns + Export JSON / CSV / Copy JSON
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Latest 50 of $totalEmotionLogsCount turns",
          style = MaterialTheme.typography.titleMedium,
          color = RiviaText,
          modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
              exportVadJsonLauncher.launch("rivia_vad_turns_$stamp.json")
            },
            enabled = totalEmotionLogsCount > 0,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
                disabledContainerColor = RiviaSurface2,
                disabledContentColor = RiviaTextTertiary,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("export_vad_json_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Download,
              contentDescription = "Export VAD results as JSON",
              tint = if (totalEmotionLogsCount > 0) RiviaIconDefault else RiviaTextTertiary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export JSON", fontSize = 12.sp)
          }

          OutlinedButton(
            onClick = {
              val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
              exportVadCsvLauncher.launch("rivia_vad_turns_$stamp.csv")
            },
            enabled = totalEmotionLogsCount > 0,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
                disabledContainerColor = RiviaSurface2,
                disabledContentColor = RiviaTextTertiary,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("export_vad_csv_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Download,
              contentDescription = "Export VAD results as CSV",
              tint = if (totalEmotionLogsCount > 0) RiviaIconDefault else RiviaTextTertiary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export CSV", fontSize = 12.sp)
          }

          OutlinedButton(
            onClick = {
              scope.launch {
                try {
                  val json = viewModel.exportVoiceEmotionLogsToJson()
                  val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                  clipboard?.setPrimaryClip(ClipData.newPlainText("Rivia VAD Turns JSON", json))
                  viewModel.showNotice("Copied $totalEmotionLogsCount VAD/emotion turns (JSON) to clipboard.")
                } catch (e: Exception) {
                  viewModel.showError("Copy failed: ${e.message}")
                }
              }
            },
            enabled = totalEmotionLogsCount > 0,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
                disabledContainerColor = RiviaSurface2,
                disabledContentColor = RiviaTextTertiary,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("copy_vad_json_button"),
          ) {
            Text("Copy JSON", fontSize = 12.sp)
          }
        }
      }
    }

    if (emotionLogs.isEmpty()) {
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = RiviaSurface2,
          border = BorderStroke(1.dp, RiviaBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "No Live mic-sharing or emotion turns logged yet.",
              style = MaterialTheme.typography.bodyMedium,
              color = RiviaText,
            )
            Text(
              text =
                "Speak a turn in Live mode with 'Record alongside recognizer' enabled to log RMS, peak, SILENT CAPTURE detection, recognizer package/status, speaking rate, and arousal/dominance/valence.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
        }
      }
    } else {
      items(emotionLogs, key = { it.id }) { entry ->
        val wavExists =
          remember(entry.wavFilePath, emotionLogs) {
            !entry.wavFilePath.isNullOrBlank() && File(entry.wavFilePath).exists()
          }
        val isPlayingThisWav =
          isSpeaking &&
            !entry.wavFilePath.isNullOrBlank() &&
            speakingMsgId == "wav:${entry.wavFilePath}"

        VoiceEmotionLogCard(
          log = entry,
          formattedTime = timeFormat.format(Date(entry.timestamp)),
          wavExists = wavExists,
          isPlayingWav = isPlayingThisWav,
          onPlayOrStopWav = {
            if (!entry.wavFilePath.isNullOrBlank()) {
              viewModel.audioPlayerManager.playLocalWavFile(entry.wavFilePath)
            }
          },
          onSelectCalibrationLabel = { newLabel ->
            viewModel.setVoiceEmotionCalibrationLabel(entry.id, newLabel)
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceEmotionLogCard(
  log: VoiceEmotionLogEntity,
  formattedTime: String,
  wavExists: Boolean,
  isPlayingWav: Boolean,
  onPlayOrStopWav: () -> Unit,
  onSelectCalibrationLabel: (String?) -> Unit,
) {
  var showCalibrationPicker by remember { mutableStateOf(false) }

  val statusType =
    when {
      log.silentCapture -> StatusType.WARNING
      !log.recognizerOk || !log.errorMessage.isNullOrBlank() -> StatusType.ERROR
      else -> StatusType.OK
    }

  val resolvedTranscriptSource =
    log.transcriptSource.ifBlank {
      if (log.usedPartialFallback) "used partial" else "onResults"
    }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = RiviaSurface2,
    border = BorderStroke(1.dp, RiviaBorder),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable { showCalibrationPicker = !showCalibrationPicker }
        .testTag("voice_emotion_log_card_${log.requestId}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.weight(1f).padding(end = 8.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            StatusShape(status = statusType, size = 10.dp)
            Text(
              text = log.requestId,
              fontFamily = ChivoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = RiviaTextPrimary,
              fontSize = 13.sp,
            )
          }
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = RiviaSurface3,
          ) {
            Text(
              text = "Mode ${log.mode}",
              fontFamily = ChivoMonoFontFamily,
              color = RiviaText,
              fontSize = 11.sp,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          if (!log.calibrationLabel.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = RiviaSurface3,
              border = BorderStroke(1.dp, RiviaBorder),
            ) {
              Text(
                text = "CAL: ${log.calibrationLabel}",
                fontFamily = ChivoMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = RiviaTextPrimary,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          if (log.isShortSpeech) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = RiviaWarningAmber.copy(alpha = 0.18f),
              border = BorderStroke(1.dp, RiviaWarningAmber.copy(alpha = 0.6f)),
            ) {
              Text(
                text = "SHORT (<1.5s)",
                fontFamily = ChivoMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = RiviaWarningAmber,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          if (log.usedPartialFallback || resolvedTranscriptSource == "used partial") {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = RiviaWarningAmber.copy(alpha = 0.16f),
              border = BorderStroke(1.dp, RiviaWarningAmber.copy(alpha = 0.6f)),
            ) {
              Text(
                text = "USED PARTIAL",
                fontFamily = ChivoMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = RiviaWarningAmber,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          } else {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = RiviaSurface3,
              border = BorderStroke(1.dp, RiviaBorder),
            ) {
              Text(
                text = "FINAL ($resolvedTranscriptSource)",
                fontFamily = ChivoMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = RiviaTextPrimary,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          if (log.silentCapture) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = RiviaWarningAmber.copy(alpha = 0.18f),
              border = BorderStroke(1.dp, RiviaWarningAmber.copy(alpha = 0.6f)),
            ) {
              Text(
                text = "SILENT CAPTURE",
                fontFamily = ChivoMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = RiviaWarningAmber,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }
        Text(
          text = formattedTime,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextTertiary,
          fontSize = 11.sp,
        )
      }

      // Requirement 2: Capture mode, audio source, and input device (getRoutedDevice type + name)
      Text(
        text =
          "Tag: Mode ${log.mode} · Src: ${log.audioSource.ifBlank { "VOICE_RECOGNITION" }} · Dev: ${log.inputDevice.ifBlank { "unknown" }}",
        fontFamily = ChivoMonoFontFamily,
        color = RiviaMutedText,
        fontSize = 11.sp,
      )

      // Per-turn diagnosis: AudioRecord started (yes/no), bytes written to pipe, partial count, callbacks fired
      val callbacksLabel = log.callbacksFired.ifBlank { "none" }
      Text(
        text =
          "AudioRecord: ${if (log.audioRecordStarted) "yes" else "no"} · Pipe: ${log.pipeBytesWritten} B · Partials: ${log.partialResultsCount} · Callbacks: $callbacksLabel",
        fontFamily = ChivoMonoFontFamily,
        color = RiviaText,
        fontSize = 11.sp,
      )

      // Requirement 1: Final text from onResults/onSegmentResults, ms from pipe close to final result, last partial, and which one was used
      val pipeToFinalLabel =
        if (log.pipeCloseToFinalMs != null) "${log.pipeCloseToFinalMs} ms" else "—"
      Text(
        text =
          "Transcript used: $resolvedTranscriptSource · Pipe→Final: $pipeToFinalLabel",
        fontFamily = ChivoMonoFontFamily,
        color = RiviaTextSecondary,
        fontSize = 11.sp,
      )
      Text(
        text =
          "Final: \"${log.finalText}\" · Last partial: \"${log.lastPartialText}\"",
        fontFamily = ChivoMonoFontFamily,
        color = RiviaMutedText,
        fontSize = 11.sp,
      )

      // Recognizer status + package + Silero VAD status
      val recText =
        if (log.recognizerOk) {
          "Recognizer: OK ($resolvedTranscriptSource) · Pkg: ${log.recognizerPackage}"
        } else {
          "Recognizer: ERR (code ${log.recognizerErrorCode ?: "none"}) · Pkg: ${log.recognizerPackage}"
        }
      val vadText =
        "VAD: peak ${"%.2f".format(Locale.US, log.vadMaxProb)} · ended=${if (log.vadEndedTurn) "yes" else "no"}"
      Text(
        text = "$recText · $vadText",
        fontFamily = ChivoMonoFontFamily,
        color = if (log.recognizerOk) RiviaMutedText else RiviaErrorRed,
        fontSize = 11.sp,
      )

      // Clip length, Silero speech duration, word count, speaking rate, and 4s window count
      val rateStr =
        if (log.speakingRateWps != null) "${"%.2f".format(Locale.US, log.speakingRateWps)} w/s"
        else "—"
      Text(
        text =
          "Clip: ${"%.2f".format(Locale.US, log.clipLengthSec)}s · Speech (Silero): ${"%.2f".format(Locale.US, log.speechDurationSec)}s${if (log.isShortSpeech) " (short)" else ""} · Words: ${log.wordCount} · Rate: $rateStr · 4s Windows: ${log.scoredWindowsCount}",
        fontFamily = ChivoMonoFontFamily,
        color = if (log.isShortSpeech) RiviaWarningAmber else RiviaText,
        fontSize = 11.sp,
      )

      val rmsGainSummary =
        if (log.rmsAfterGain != null && log.appliedGainDb != null) {
          "RMS raw: ${"%.4f".format(Locale.US, log.rms)} → Pipe AGC: ${"%.4f".format(Locale.US, log.rmsAfterGain)} (+${"%.1f".format(Locale.US, log.appliedGainDb)} dB)"
        } else {
          "RMS: ${"%.4f".format(Locale.US, log.rms)}"
        }
      Text(
        text =
          "$rmsGainSummary · Peak: ${"%.4f".format(Locale.US, log.peak)} · Silent: ${if (log.silentCapture) "YES" else "NO"}",
        fontFamily = ChivoMonoFontFamily,
        color = if (log.silentCapture) RiviaWarningAmber else RiviaMutedText,
        fontSize = 11.sp,
      )

      // Arousal, dominance, valence (to 2 decimals) + speaking rate + inference ms + WAV playback button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val advText =
          if (log.arousal != null && log.dominance != null && log.valence != null) {
            "A: ${"%.2f".format(Locale.US, log.arousal)} · D: ${"%.2f".format(Locale.US, log.dominance)} · V: ${"%.2f".format(Locale.US, log.valence)} · Rate: $rateStr · ${log.inferenceMs ?: 0L} ms"
          } else {
            "A/D/V: — · ${log.captureStatus}"
          }
        Text(
          text = advText,
          fontFamily = ChivoMonoFontFamily,
          fontWeight = FontWeight.SemiBold,
          color = RiviaTextPrimary,
          fontSize = 12.sp,
          modifier = Modifier.weight(1f),
        )

        if (wavExists) {
          OutlinedButton(
            onClick = onPlayOrStopWav,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface3,
                contentColor = RiviaTextPrimary,
              ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = if (isPlayingWav) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = if (isPlayingWav) "Stop WAV clip" else "Play WAV clip",
              tint = if (isPlayingWav) RiviaIconActive else RiviaIconDefault,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isPlayingWav) "Stop WAV" else "Play WAV",
              color = RiviaTextPrimary,
              fontFamily = ChivoMonoFontFamily,
              fontSize = 11.sp,
            )
          }
        }
      }

      // Calibration label selector (tap turn card or tap a chip directly)
      if (showCalibrationPicker || !log.calibrationLabel.isNullOrBlank()) {
        HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))
        Text(
          text =
            "Calibration label (excludes turn from baseline): ${log.calibrationLabel ?: "none"}",
          fontFamily = ChivoMonoFontFamily,
          color = RiviaMutedText,
          fontSize = 11.sp,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          RiviaViewModel.CALIBRATION_LABELS.forEach { labelOption ->
            val isSelected = log.calibrationLabel == labelOption
            OptionChip(
              label = labelOption,
              selected = isSelected,
              onClick = {
                onSelectCalibrationLabel(if (isSelected) null else labelOption)
              },
            )
          }
          if (!log.calibrationLabel.isNullOrBlank()) {
            OptionChip(
              label = "clear",
              selected = false,
              onClick = { onSelectCalibrationLabel(null) },
            )
          }
        }
      } else {
        Text(
          text = "Tap turn to set calibration label (excited, calm, irritated, sad, anxious, other)",
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextTertiary,
          fontSize = 10.sp,
        )
      }

      if (!log.errorMessage.isNullOrBlank()) {
        Text(
          text = log.errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = RiviaErrorRed,
        )
      }
    }
  }
}

@Composable
private fun RequestLogTab(viewModel: RiviaViewModel) {
  val logs by viewModel.requestLogs.collectAsStateWithLifecycle()
  val totalRequestsCount by viewModel.totalRequestLogsCount.collectAsStateWithLifecycle()
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
        tint = RiviaIconDefault,
        modifier = Modifier.size(40.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Latest 100 of $totalRequestsCount requests",
        style = MaterialTheme.typography.titleMedium,
        color = RiviaText,
      )
      Text(
        text = "No requests logged yet.",
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
      item {
        Text(
          text = "Latest 100 of $totalRequestsCount requests",
          style = MaterialTheme.typography.titleMedium,
          color = RiviaText,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
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
    shape = RoundedCornerShape(14.dp),
    color = RiviaSurface2,
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
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
          StatusShape(status = statusType, size = 10.dp)
          Text(
            text = log.requestId,
            fontFamily = ChivoMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = RiviaTextPrimary,
            fontSize = 13.sp,
          )
          Text(
            text = log.model,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaTextSecondary,
            fontSize = 12.sp,
          )
        }
        Text(
          text = formattedTime,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextTertiary,
          fontSize = 11.sp,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text =
            "Kind: ${log.chatKind}" +
              (if (!log.profile.isNullOrBlank()) " · Profile: ${log.profile}" else "") +
              " · Ch: ${log.channel}" +
              (if (!log.fingerprint.isNullOrBlank()) " · FP: ${log.fingerprint}" else ""),
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

      if (!log.ruleEvents.isNullOrBlank()) {
        Text(
          text = "Rules: ${log.ruleEvents}",
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextSecondary,
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
