package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LayerTestRunEntity
import com.example.data.RuleEventEntity
import com.example.model.EngineLayerConfig
import com.example.model.EngineProfileConfig
import com.example.model.HardRuleConfig
import com.example.model.HardRulesLayerConfig
import com.example.model.LayerCardUiMeta
import com.example.model.LayerConfigManager
import com.example.model.LayerStatus
import com.example.model.LoadedLayersBundle
import com.example.model.ProfileTestState
import com.example.model.RunSettings
import com.example.model.TestDefConfig
import androidx.compose.material3.TabRowDefaults
import com.example.ui.components.InfoHelpIconButton
import com.example.ui.components.RiviaSettingsGuide
import com.example.ui.components.SettingHelpBottomSheet
import com.example.ui.theme.AppUiMode
import com.example.ui.theme.ChivoMonoFontFamily as MonoFontFamily
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed as RiviaDestructive
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMint as RiviaSuccess
import com.example.ui.theme.RiviaMint as RiviaTeal
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2 as RiviaPanelSurface
import com.example.ui.theme.RiviaSurface3
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextTertiary
import com.example.ui.theme.RiviaWarningAmber as RiviaAmber
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

private val RiviaErrorBg = Color(0xFF2B1215)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LayersScreen(
  viewModel: RiviaViewModel,
  initialFocusLayer: String? = null,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()

  val appUiMode by viewModel.appUiMode.collectAsStateWithLifecycle()
  val layersLoadError by viewModel.layersLoadError.collectAsStateWithLifecycle()
  val bundle by viewModel.loadedLayersBundle.collectAsStateWithLifecycle()
  val spokenMaxSentences by viewModel.spokenMaxSentences.collectAsStateWithLifecycle()
  val h11WatchWords by viewModel.h11WatchWords.collectAsStateWithLifecycle()
  val fingerprints by viewModel.profileFingerprints.collectAsStateWithLifecycle()
  val testStates by viewModel.profileTestStates.collectAsStateWithLifecycle()
  val cardMetas by viewModel.layerCardMetas.collectAsStateWithLifecycle()
  val testRuns by viewModel.layerTestRuns.collectAsStateWithLifecycle()
  val recentRuleEvents by viewModel.recentRuleEvents.collectAsStateWithLifecycle()
  val cachedModels by viewModel.cachedModels.collectAsStateWithLifecycle()
  val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
  val isErrorBanner by viewModel.isErrorBanner.collectAsStateWithLifecycle()

  val listState = rememberLazyListState()

  val allLayerKeys =
    remember(appUiMode) {
      if (appUiMode == AppUiMode.DEV) {
        listOf("persona", "style", "engine", "hard_rules", "tests")
      } else {
        listOf("persona", "style", "engine", "hard_rules")
      }
    }
  val expandedSections = remember {
    mutableStateMapOf(
      "persona" to true,
      "style" to true,
      "engine" to true,
      "hard_rules" to true,
      "tests" to true,
    )
  }
  val anyExpanded = allLayerKeys.any { expandedSections[it] == true }

  // Scroll to focused card (e.g. "engine" when opened via mode chip pencil icon)
  LaunchedEffect(initialFocusLayer, bundle) {
    if (initialFocusLayer != null && bundle != null) {
      expandedSections[initialFocusLayer] = true
      if (initialFocusLayer == "engine") {
        listState.animateScrollToItem(3) // 0=summary, 1=persona, 2=style, 3=engine
      }
    }
  }

  // Dialog & help sheet states
  var viewFileDialogLayer by remember { mutableStateOf<String?>(null) }
  var logRunTestDef by remember { mutableStateOf<TestDefConfig?>(null) }
  var activeHelpTopicId by remember { mutableStateOf<String?>(null) }

  val availableModels =
    remember(cachedModels) {
      val defaults = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon")
      val fromCache = cachedModels.map { it.id }
      (defaults + fromCache).distinct()
    }

  val modeBadgeText =
    when (appUiMode) {
      AppUiMode.LABS -> "LABS SANDBOX"
      AppUiMode.DEV -> "DEV · RIVIA"
      AppUiMode.RIVIA -> "RIVIA"
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Layers",
              style = MaterialTheme.typography.titleMedium,
              color = RiviaTextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
            Surface(
              shape = RoundedCornerShape(50),
              color = RiviaPanelSurface,
              border = BorderStroke(1.dp, RiviaBorder),
              modifier = Modifier.testTag("layers_mode_badge"),
            ) {
              Text(
                text = modeBadgeText,
                color = RiviaSecondaryText,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
              )
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("layers_back_button"),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to chat",
              tint = RiviaTextPrimary,
            )
          }
        },
        actions = {
          TextButton(
            onClick = {
              val targetState = !anyExpanded
              for (k in allLayerKeys) {
                expandedSections[k] = targetState
              }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = RiviaTextPrimary),
            modifier = Modifier.testTag("layers_toggle_all_button"),
          ) {
            Icon(
              imageVector =
                if (anyExpanded) Icons.Default.KeyboardArrowUp
                else Icons.Default.KeyboardArrowDown,
              contentDescription = null,
              tint = RiviaIconDefault,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (anyExpanded) "Collapse all" else "Expand all",
              style = MaterialTheme.typography.labelMedium,
              color = RiviaTextPrimary,
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    }
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .background(MaterialTheme.colorScheme.background)
    ) {
      if (layersLoadError != null) {
        Surface(
          color = RiviaErrorBg,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, RiviaDestructive),
        ) {
          Text(
            text = layersLoadError ?: "",
            color = RiviaDestructive,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = MonoFontFamily,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      if (statusBannerMessage != null) {
        Surface(
          color = if (isErrorBanner) RiviaErrorBg else RiviaPanelSurface,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          shape = RoundedCornerShape(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = statusBannerMessage ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = if (isErrorBanner) RiviaDestructive else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.clearStatusBanner() }) {
              Text("Dismiss", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }

      val loaded = bundle
      if (loaded != null) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // 0. Top summary bar
          item {
            TopFingerprintSummaryBar(
              appUiMode = appUiMode,
              fingerprints = fingerprints,
              testStates = testStates,
            )
          }

          // 1. Persona Card
          item {
            val meta = cardMetas["persona"]
            if (meta != null) {
              PersonaLayerCard(
                meta = meta,
                bundle = loaded,
                expanded = expandedSections["persona"] ?: true,
                onToggleExpanded = {
                  expandedSections["persona"] = !(expandedSections["persona"] ?: true)
                },
                onShowInfo = { activeHelpTopicId = "layer_persona" },
                onToggleEnabled = { viewModel.setLayerEnabled("persona", it) },
                onToggleLocked = { viewModel.setLayerLocked("persona", it) },
                onSaveDraft = { newText ->
                  viewModel
                    .savePersonaDraft(newText)
                    .onSuccess { viewModel.showNotice("Saved Persona layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Invalid persona") }
                },
                onClearLayer = {
                  viewModel
                    .clearLayer("persona")
                    .onSuccess { viewModel.showNotice("Cleared Persona layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Failed to clear Persona") }
                },
                onRevertLayer = { viewModel.discardLayerDraft("persona") },
                onViewFile = { viewFileDialogLayer = "persona" },
              )
            }
          }

          // 2. Style Card
          item {
            val meta = cardMetas["style"]
            if (meta != null) {
              StyleLayerCard(
                meta = meta,
                bundle = loaded,
                expanded = expandedSections["style"] ?: true,
                onToggleExpanded = {
                  expandedSections["style"] = !(expandedSections["style"] ?: true)
                },
                onShowInfo = { activeHelpTopicId = "layer_style" },
                onToggleEnabled = { viewModel.setLayerEnabled("style", it) },
                onToggleLocked = { viewModel.setLayerLocked("style", it) },
                onSaveDraft = { newText, newVoice ->
                  viewModel
                    .saveStyleDraft(newText, newVoice)
                    .onSuccess { viewModel.showNotice("Saved Style layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Invalid style") }
                },
                onClearLayer = {
                  viewModel
                    .clearLayer("style")
                    .onSuccess { viewModel.showNotice("Cleared Style layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Failed to clear Style") }
                },
                onRevertLayer = { viewModel.discardLayerDraft("style") },
                onViewFile = { viewFileDialogLayer = "style" },
              )
            }
          }

          // 3. Engine Card
          item {
            val meta = cardMetas["engine"]
            if (meta != null) {
              EngineLayerCard(
                appUiMode = appUiMode,
                meta = meta,
                bundle = loaded,
                availableModels = availableModels,
                expanded = expandedSections["engine"] ?: true,
                onToggleExpanded = {
                  expandedSections["engine"] = !(expandedSections["engine"] ?: true)
                },
                onShowInfo = { activeHelpTopicId = "layer_engine" },
                onToggleEnabled = { viewModel.setLayerEnabled("engine", it) },
                onToggleLocked = { viewModel.setLayerLocked("engine", it) },
                onSaveDraft = { newEngine ->
                  viewModel
                    .saveEngineDraft(newEngine)
                    .onSuccess { viewModel.showNotice("Saved Engine layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Invalid engine config") }
                },
                onClearLayer = {
                  viewModel
                    .clearLayer("engine")
                    .onSuccess { viewModel.showNotice("Cleared Engine layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Failed to clear Engine") }
                },
                onRevertLayer = { viewModel.discardLayerDraft("engine") },
                onViewFile = { viewFileDialogLayer = "engine" },
                onValidationError = { msg -> viewModel.showError(msg) },
              )
            }
          }

          // 4. Hard Rules Card
          item {
            val meta = cardMetas["hard_rules"]
            if (meta != null) {
              HardRulesLayerCard(
                meta = meta,
                bundle = loaded,
                spokenMaxSentences = spokenMaxSentences,
                h11WatchWords = h11WatchWords,
                recentRuleEvents = recentRuleEvents,
                expanded = expandedSections["hard_rules"] ?: true,
                onToggleExpanded = {
                  expandedSections["hard_rules"] = !(expandedSections["hard_rules"] ?: true)
                },
                onShowInfo = { activeHelpTopicId = "layer_hard_rules" },
                onToggleEnabled = { viewModel.setLayerEnabled("hard_rules", it) },
                onToggleLocked = { viewModel.setLayerLocked("hard_rules", it) },
                onSpokenMaxChange = { viewModel.setSpokenMaxSentences(it) },
                onAddWatchWord = { viewModel.addH11WatchWord(it) },
                onRemoveWatchWord = { viewModel.removeH11WatchWord(it) },
                onSaveDraft = { updatedRules ->
                  viewModel
                    .saveHardRulesDraft(updatedRules)
                    .onSuccess { viewModel.showNotice("Saved Hard rules layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Invalid hard rules") }
                },
                onClearLayer = {
                  viewModel
                    .clearLayer("hard_rules")
                    .onSuccess { viewModel.showNotice("Cleared Hard rules layer.") }
                    .onFailure { viewModel.showError(it.message ?: "Failed to clear Hard rules") }
                },
                onRevertLayer = { viewModel.discardLayerDraft("hard_rules") },
                onViewFile = { viewFileDialogLayer = "hard_rules" },
              )
            }
          }

          // 5. Tests Card (H10 pushback, H7 spoken length stay in Dev only)
          if (appUiMode == AppUiMode.DEV) {
            item {
              val testsMeta =
                cardMetas["tests"]
                  ?: LayerCardUiMeta(
                    layerKey = "tests",
                    fileName = "tests.json",
                    version = loaded.tests.version,
                    updated = loaded.tests.updated,
                    status = LayerStatus.LOCKED,
                    sourceSubtitle = "from build",
                    hasDraft = false,
                    bundledLocked = true,
                    enabled = true,
                    isLocked = true,
                  )
              TestsLayerCard(
                meta = testsMeta,
                bundle = loaded,
                fingerprints = fingerprints,
                testRuns = testRuns,
                expanded = expandedSections["tests"] ?: true,
                onToggleExpanded = {
                  expandedSections["tests"] = !(expandedSections["tests"] ?: true)
                },
                onShowInfo = { activeHelpTopicId = "layer_tests" },
                onToggleEnabled = { viewModel.setLayerEnabled("tests", it) },
                onToggleLocked = { viewModel.setLayerLocked("tests", it) },
                onClearLayer = { viewModel.clearLayer("tests") },
                onSaveLayer = { viewModel.showNotice("Saved Tests layer.") },
                onRevertLayer = { viewModel.discardLayerDraft("tests") },
                onOpenLogRun = { testDef -> logRunTestDef = testDef },
                onViewFile = { viewFileDialogLayer = "tests" },
              )
            }
          }
        }
      }
    }
  }

  // Layer Info Help Sheet
  if (activeHelpTopicId != null) {
    SettingHelpBottomSheet(
      topic = RiviaSettingsGuide.findTopic(activeHelpTopicId!!),
      onDismiss = { activeHelpTopicId = null },
    )
  }

  // View file dialog
  viewFileDialogLayer?.let { layerKey ->
    val rawJson = bundle?.effectiveRawJson?.get(layerKey) ?: "{}"
    val fileName = LayerConfigManager.LAYER_FILES[layerKey] ?: "$layerKey.json"
    AlertDialog(
      onDismissRequest = { viewFileDialogLayer = null },
      containerColor = RiviaSurface1,
      title = {
        Text(
          text = "Running file: $fileName",
          style = MaterialTheme.typography.titleSmall,
          fontFamily = MonoFontFamily,
          color = RiviaTextPrimary,
        )
      },
      text = {
        Surface(
          color = RiviaPanelSurface,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, RiviaBorder),
          modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        ) {
          SelectionContainer {
            Text(
              text = rawJson,
              fontFamily = MonoFontFamily,
              fontSize = 11.sp,
              color = RiviaTextPrimary,
              modifier =
                Modifier.padding(12.dp)
                  .verticalScroll(rememberScrollState())
                  .horizontalScroll(rememberScrollState()),
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            clipboardManager.setText(AnnotatedString(rawJson))
            viewModel.showNotice("Copied $fileName to clipboard.")
          }
        ) {
          Icon(
            Icons.Default.ContentCopy,
            contentDescription = null,
            tint = RiviaIconDefault,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy JSON", color = RiviaTextPrimary)
        }
      },
      dismissButton = {
        TextButton(onClick = { viewFileDialogLayer = null }) {
          Text("Close", color = RiviaSecondaryText)
        }
      },
    )
  }

  // Log a run dialog for Tests card
  logRunTestDef?.let { testDef ->
    LogTestRunDialog(
      appUiMode = appUiMode,
      testDef = testDef,
      fingerprints = fingerprints,
      onDismiss = { logRunTestDef = null },
      onSaveRun = { profile, passed, detailsJson ->
        viewModel.logLayerTestRun(
          testId = testDef.id,
          profile = profile,
          passed = passed,
          detailsJson = detailsJson,
        )
        logRunTestDef = null
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopFingerprintSummaryBar(
  appUiMode: AppUiMode = AppUiMode.LABS,
  fingerprints: Map<String, String>,
  testStates: Map<String, ProfileTestState>,
) {
  val profilesToShow =
    if (appUiMode == AppUiMode.LABS) {
      LayerConfigManager.REQUIRED_PROFILES
    } else {
      listOf("live")
    }
  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layers_fingerprint_summary"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text =
          if (appUiMode == AppUiMode.LABS) "Profile Fingerprints & Test State"
          else "Rivia Fingerprint & Test State",
        style = MaterialTheme.typography.labelMedium,
        color = RiviaSecondaryText,
      )
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for (prof in profilesToShow) {
          val fp = fingerprints[prof] ?: "--------"
          val state = testStates[prof] ?: ProfileTestState.NOT_RUN
          val (symbol, tint) = profileTestIndicator(state)
          Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = symbol,
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("profile_dot_$prof"),
              )
              Column {
                Text(
                  text =
                    if (appUiMode == AppUiMode.LABS) LayerConfigManager.displayProfileLabel(prof)
                    else "Rivia",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "$fp · ${state.label}",
                  fontFamily = MonoFontFamily,
                  fontSize = 11.sp,
                  color = RiviaSecondaryText,
                )
              }
            }
          }
        }
      }
    }
  }
}

fun profileTestIndicator(state: ProfileTestState): Pair<String, Color> =
  when (state) {
    ProfileTestState.PASSED -> "●" to RiviaSuccess
    ProfileTestState.NOT_RUN -> "○" to RiviaAmber
    ProfileTestState.FAILED -> "▲" to RiviaDestructive
  }

fun layerStatusIndicator(status: LayerStatus): Pair<String, Color> =
  when (status) {
    LayerStatus.DORMANT -> "◌" to RiviaSecondaryText
    LayerStatus.LOCKED -> "■" to RiviaTeal
    LayerStatus.TESTED -> "●" to RiviaSuccess
    LayerStatus.DRAFT -> "◆" to RiviaAmber
  }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerCardHeader(
  title: String,
  meta: LayerCardUiMeta,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onShowInfo: () -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
  onToggleLocked: (Boolean) -> Unit,
  onClear: () -> Unit,
  onSave: () -> Unit,
  onRevert: () -> Unit,
  onViewFile: () -> Unit,
) {
  val (statusSymbol, statusColor) = layerStatusIndicator(meta.status)
  val isEditable = !meta.isLocked && meta.enabled

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clickable(onClick = onToggleExpanded)
          .testTag("layer_header_${meta.layerKey}"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            InfoHelpIconButton(
              contentDescription = "About $title layer and examples",
              onClick = onShowInfo,
              modifier = Modifier.testTag("info_layer_${meta.layerKey}"),
            )
          }
          Surface(
            color = statusColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
            modifier = Modifier.align(Alignment.CenterVertically).testTag("status_badge_${meta.layerKey}"),
          ) {
            Text(
              text = "$statusSymbol ${meta.status.label}",
              color = statusColor,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
          if (!meta.enabled) {
            Surface(
              color = RiviaSurface3,
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, RiviaBorder),
              modifier = Modifier.align(Alignment.CenterVertically),
            ) {
              Text(
                text = "Disabled",
                color = RiviaSecondaryText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text =
            "v${meta.version}${if (meta.hasDraft) "*" else ""} · ${meta.sourceSubtitle}" +
              (if (meta.updated.isNotBlank()) " · ${meta.updated}" else ""),
          fontFamily = MonoFontFamily,
          fontSize = 11.sp,
          color = if (meta.hasDraft) RiviaAmber else RiviaSecondaryText,
        )
      }

      IconButton(
        onClick = onToggleExpanded,
        modifier = Modifier.testTag("toggle_layer_${meta.layerKey}"),
      ) {
        Icon(
          imageVector =
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription = if (expanded) "Collapse $title" else "Expand $title",
          tint = RiviaSecondaryText,
        )
      }
    }

    if (!meta.lockMismatchWarning.isNullOrBlank()) {
      Surface(
        color = RiviaErrorBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, RiviaDestructive),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = meta.lockMismatchWarning,
          color = RiviaDestructive,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }

    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Enable/Disable toggle & Locked/Unlocked toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.weight(1f),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (meta.enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.labelMedium,
                color = if (meta.enabled) RiviaTextPrimary else RiviaSecondaryText,
                fontWeight = FontWeight.SemiBold,
              )
              Switch(
                checked = meta.enabled,
                onCheckedChange = onToggleEnabled,
                colors = riviaSwitchColors(),
                modifier = Modifier.testTag("toggle_enable_${meta.layerKey}"),
              )
            }
          }

          Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.weight(1f),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (meta.isLocked) "Locked" else "Unlocked",
                style = MaterialTheme.typography.labelMedium,
                color = if (meta.isLocked) RiviaTeal else RiviaAmber,
                fontWeight = FontWeight.SemiBold,
              )
              Switch(
                checked = meta.isLocked,
                onCheckedChange = onToggleLocked,
                colors = riviaSwitchColors(),
                modifier = Modifier.testTag("toggle_lock_${meta.layerKey}"),
              )
            }
          }
        }

        // Clear, Save, Revert, and View file buttons
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedButton(
            onClick = onClear,
            enabled = isEditable,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaPanelSurface,
                contentColor = RiviaTextPrimary,
                disabledContainerColor = RiviaPanelSurface,
                disabledContentColor = RiviaTextTertiary,
              ),
            modifier = Modifier.testTag("clear_layer_${meta.layerKey}"),
          ) {
            Text(
              "Clear",
              style = MaterialTheme.typography.labelSmall,
              color = if (isEditable) RiviaTextPrimary else RiviaTextTertiary,
            )
          }

          Button(
            onClick = onSave,
            enabled = isEditable,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaTeal,
                contentColor = RiviaOnAccent,
                disabledContainerColor = RiviaSurface3,
                disabledContentColor = RiviaTextTertiary,
              ),
            modifier = Modifier.testTag("save_layer_${meta.layerKey}"),
          ) {
            Text(
              "Save",
              style = MaterialTheme.typography.labelSmall,
              color = if (isEditable) RiviaOnAccent else RiviaTextTertiary,
              fontWeight = FontWeight.SemiBold,
            )
          }

          OutlinedButton(
            onClick = onRevert,
            enabled = isEditable,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaPanelSurface,
                contentColor = RiviaTextPrimary,
                disabledContainerColor = RiviaPanelSurface,
                disabledContentColor = RiviaTextTertiary,
              ),
            modifier = Modifier.testTag("revert_layer_${meta.layerKey}"),
          ) {
            Text(
              "Revert",
              style = MaterialTheme.typography.labelSmall,
              color = if (isEditable) RiviaTextPrimary else RiviaTextTertiary,
            )
          }

          TextButton(
            onClick = onViewFile,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = RiviaTextPrimary),
            modifier = Modifier.testTag("view_file_${meta.layerKey}"),
          ) {
            Text("View file", style = MaterialTheme.typography.labelSmall, color = RiviaTextPrimary)
          }
        }
      }
    }
  }
}

@Composable
private fun PersonaLayerCard(
  meta: LayerCardUiMeta,
  bundle: LoadedLayersBundle,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onShowInfo: () -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
  onToggleLocked: (Boolean) -> Unit,
  onSaveDraft: (String) -> Unit,
  onClearLayer: () -> Unit,
  onRevertLayer: () -> Unit,
  onViewFile: () -> Unit,
) {
  var textValue by remember(bundle.persona.text) { mutableStateOf(bundle.persona.text) }
  val isReadOnly = meta.isLocked || !meta.enabled
  val wordsCount =
    remember(textValue) {
      val t = textValue.trim()
      if (t.isEmpty()) 0 else t.split(Regex("\\s+")).size
    }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_persona"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LayerCardHeader(
        title = "Persona",
        meta = meta,
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        onShowInfo = onShowInfo,
        onToggleEnabled = onToggleEnabled,
        onToggleLocked = onToggleLocked,
        onClear = {
          textValue = ""
          onClearLayer()
        },
        onSave = { onSaveDraft(textValue) },
        onRevert = {
          textValue = bundle.bundledPersona.text
          onRevertLayer()
        },
        onViewFile = onViewFile,
      )

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (textValue.trim().isEmpty() && !bundle.persona.note.isNullOrBlank()) {
            Text(
              text = bundle.persona.note,
              style = MaterialTheme.typography.bodySmall,
              color = RiviaSecondaryText,
            )
          }

          OutlinedTextField(
            value = textValue,
            onValueChange = { if (!isReadOnly) textValue = it },
            readOnly = isReadOnly,
            enabled = meta.enabled,
            label = { Text("Persona text") },
            placeholder = { Text("While empty, Persona is Dormant and adds nothing to the prompt.") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).testTag("persona_text_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "$wordsCount words · ${textValue.length} chars",
              fontFamily = MonoFontFamily,
              fontSize = 11.sp,
              color = RiviaSecondaryText,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleLayerCard(
  meta: LayerCardUiMeta,
  bundle: LoadedLayersBundle,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onShowInfo: () -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
  onToggleLocked: (Boolean) -> Unit,
  onSaveDraft: (String, String) -> Unit,
  onClearLayer: () -> Unit,
  onRevertLayer: () -> Unit,
  onViewFile: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var typedStyleText by remember(bundle.style.text) { mutableStateOf(bundle.style.text) }
  var voiceStyleText by remember(bundle.style.voice) { mutableStateOf(bundle.style.voice) }
  val isReadOnly = meta.isLocked || !meta.enabled

  val activeText = if (selectedTab == 0) typedStyleText else voiceStyleText
  val wordsCount =
    remember(activeText) {
      val t = activeText.trim()
      if (t.isEmpty()) 0 else t.split(Regex("\\s+")).size
    }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_style"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LayerCardHeader(
        title = "Style",
        meta = meta,
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        onShowInfo = onShowInfo,
        onToggleEnabled = onToggleEnabled,
        onToggleLocked = onToggleLocked,
        onClear = {
          typedStyleText = ""
          voiceStyleText = ""
          onClearLayer()
        },
        onSave = { onSaveDraft(typedStyleText, voiceStyleText) },
        onRevert = {
          typedStyleText = bundle.bundledStyle.text
          voiceStyleText = bundle.bundledStyle.voice
          onRevertLayer()
        },
        onViewFile = onViewFile,
      )

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = RiviaTextPrimary,
            indicator = {
              TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTab),
                color = RiviaTeal,
              )
            },
          ) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              selectedContentColor = RiviaTextPrimary,
              unselectedContentColor = RiviaSecondaryText,
              text = {
                Text(
                  "Text",
                  color = if (selectedTab == 0) RiviaTextPrimary else RiviaSecondaryText,
                )
              },
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              selectedContentColor = RiviaTextPrimary,
              unselectedContentColor = RiviaSecondaryText,
              text = {
                Text(
                  "Voice",
                  color = if (selectedTab == 1) RiviaTextPrimary else RiviaSecondaryText,
                )
              },
            )
          }

          if (selectedTab == 0) {
            OutlinedTextField(
              value = typedStyleText,
              onValueChange = { if (!isReadOnly) typedStyleText = it },
              readOnly = isReadOnly,
              enabled = meta.enabled,
              label = { Text("Typed style (style.text)") },
              modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).testTag("style_text_input"),
            )
          } else {
            OutlinedTextField(
              value = voiceStyleText,
              onValueChange = { if (!isReadOnly) voiceStyleText = it },
              readOnly = isReadOnly,
              enabled = meta.enabled,
              label = { Text("Voice style (style.voice)") },
              modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("style_voice_input"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "$wordsCount words · ${activeText.length} chars",
              fontFamily = MonoFontFamily,
              fontSize = 11.sp,
              color = RiviaSecondaryText,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineLayerCard(
  appUiMode: AppUiMode = AppUiMode.LABS,
  meta: LayerCardUiMeta,
  bundle: LoadedLayersBundle,
  availableModels: List<String>,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onShowInfo: () -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
  onToggleLocked: (Boolean) -> Unit,
  onSaveDraft: (EngineLayerConfig) -> Unit,
  onClearLayer: () -> Unit,
  onRevertLayer: () -> Unit,
  onViewFile: () -> Unit,
  onValidationError: (String) -> Unit,
) {
  var draftEngine by remember(bundle.engine) { mutableStateOf(bundle.engine) }
  val isReadOnly = meta.isLocked || !meta.enabled
  var maxTokensInput by
    remember(bundle.engine.max_output_tokens) {
      mutableStateOf(bundle.engine.max_output_tokens?.toString() ?: "")
    }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_engine"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LayerCardHeader(
        title = "Engine",
        meta = meta,
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        onShowInfo = onShowInfo,
        onToggleEnabled = onToggleEnabled,
        onToggleLocked = onToggleLocked,
        onClear = {
          maxTokensInput = ""
          draftEngine =
            bundle.bundledEngine.copy(
              locked = false,
              default_profile = "balanced",
              thought_summaries = "auto",
              max_output_tokens = null,
              service_tier = "standard",
              store = true,
              safety = JsonPrimitive("model_default"),
            )
          onClearLayer()
        },
        onSave = {
          val trimmed = maxTokensInput.trim()
          if (trimmed.isEmpty()) {
            val updated = draftEngine.copy(max_output_tokens = null)
            draftEngine = updated
            onSaveDraft(updated)
          } else {
            val parsed = trimmed.toIntOrNull()
            if (parsed == null || parsed < 1) {
              onValidationError("engine.json: max_output_tokens must be null or a positive whole number")
            } else {
              val updated = draftEngine.copy(max_output_tokens = parsed)
              draftEngine = updated
              onSaveDraft(updated)
            }
          }
        },
        onRevert = {
          draftEngine = bundle.bundledEngine
          maxTokensInput = bundle.bundledEngine.max_output_tokens?.toString() ?: ""
          onRevertLayer()
        },
        onViewFile = onViewFile,
      )

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          val profilesToEdit =
            if (appUiMode == AppUiMode.LABS) {
              LayerConfigManager.REQUIRED_PROFILES
            } else {
              listOf("live")
            }
          for (profKey in profilesToEdit) {
            val profCfg =
              draftEngine.profiles[profKey]
                ?: EngineProfileConfig(model = "gemini-3.8-flash", thinking_level = "low")
            EngineProfileEditorRow(
              profileKey = profKey,
              displayLabelOverride = if (appUiMode == AppUiMode.LABS) null else "Rivia model",
              profileConfig = profCfg,
              availableModels = availableModels,
              readOnly = isReadOnly,
              onUpdateProfile = { updatedProf ->
                val updatedMap = draftEngine.profiles.toMutableMap().apply { put(profKey, updatedProf) }
                val updated = draftEngine.copy(profiles = updatedMap)
                draftEngine = updated
                onSaveDraft(updated)
              },
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

          if (appUiMode == AppUiMode.LABS) {
            // Default profile selector (Labs only)
            Text(
              text = "Default profile",
              style = MaterialTheme.typography.labelMedium,
              color = RiviaSecondaryText,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              for (profKey in LayerConfigManager.REQUIRED_PROFILES) {
                val isSelected = draftEngine.default_profile == profKey
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    if (!isReadOnly) {
                      val updated = draftEngine.copy(default_profile = profKey)
                      draftEngine = updated
                      onSaveDraft(updated)
                    }
                  },
                  enabled = !isReadOnly,
                  colors =
                    FilterChipDefaults.filterChipColors(
                      containerColor = RiviaPanelSurface,
                      labelColor = RiviaTextPrimary,
                      selectedContainerColor = RiviaTeal,
                      selectedLabelColor = RiviaOnAccent,
                      disabledContainerColor = RiviaPanelSurface,
                      disabledLabelColor = RiviaTextTertiary,
                      disabledSelectedContainerColor = RiviaTeal,
                    ),
                  border =
                    BorderStroke(
                      width = 1.dp,
                      color = if (isSelected) RiviaTeal else RiviaBorder,
                    ),
                  label = {
                    Text(
                      profKey,
                      color = if (isSelected) RiviaOnAccent else RiviaTextPrimary,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                  },
                )
              }
            }
          }

          // Thought summaries, Service tier, Store
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Thought summaries", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              for (opt in listOf("auto", "none")) {
                val isSelected = draftEngine.thought_summaries == opt
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    if (!isReadOnly) {
                      val updated = draftEngine.copy(thought_summaries = opt)
                      draftEngine = updated
                      onSaveDraft(updated)
                    }
                  },
                  enabled = !isReadOnly,
                  colors =
                    FilterChipDefaults.filterChipColors(
                      containerColor = RiviaPanelSurface,
                      labelColor = RiviaTextPrimary,
                      selectedContainerColor = RiviaTeal,
                      selectedLabelColor = RiviaOnAccent,
                      disabledContainerColor = RiviaPanelSurface,
                      disabledLabelColor = RiviaTextTertiary,
                      disabledSelectedContainerColor = RiviaTeal,
                    ),
                  border =
                    BorderStroke(
                      width = 1.dp,
                      color = if (isSelected) RiviaTeal else RiviaBorder,
                    ),
                  label = {
                    Text(
                      opt,
                      color = if (isSelected) RiviaOnAccent else RiviaTextPrimary,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                  },
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Service tier", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              for (tier in listOf("standard", "priority")) {
                val isSelected = draftEngine.service_tier == tier
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    if (!isReadOnly) {
                      val updated = draftEngine.copy(service_tier = tier)
                      draftEngine = updated
                      onSaveDraft(updated)
                    }
                  },
                  enabled = !isReadOnly,
                  colors =
                    FilterChipDefaults.filterChipColors(
                      containerColor = RiviaPanelSurface,
                      labelColor = RiviaTextPrimary,
                      selectedContainerColor = RiviaTeal,
                      selectedLabelColor = RiviaOnAccent,
                      disabledContainerColor = RiviaPanelSurface,
                      disabledLabelColor = RiviaTextTertiary,
                      disabledSelectedContainerColor = RiviaTeal,
                    ),
                  border =
                    BorderStroke(
                      width = 1.dp,
                      color = if (isSelected) RiviaTeal else RiviaBorder,
                    ),
                  label = {
                    Text(
                      tier,
                      color = if (isSelected) RiviaOnAccent else RiviaTextPrimary,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                  },
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Store interactions on server", style = MaterialTheme.typography.bodySmall)
            Switch(
              checked = draftEngine.store,
              onCheckedChange = {
                if (!isReadOnly) {
                  val updated = draftEngine.copy(store = it)
                  draftEngine = updated
                  onSaveDraft(updated)
                }
              },
              enabled = !isReadOnly,
              colors = riviaSwitchColors(),
            )
          }

          // Max output tokens (null when blank, or positive whole number)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = maxTokensInput,
              onValueChange = { if (!isReadOnly) maxTokensInput = it },
              readOnly = isReadOnly,
              enabled = meta.enabled,
              singleLine = true,
              label = { Text("max_output_tokens (blank = null)") },
              modifier = Modifier.weight(1f),
            )
          }

          // Safety mode summary ("model_default" vs custom list)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Safety", style = MaterialTheme.typography.bodySmall)
            val isModelDefault =
              draftEngine.safety is JsonPrimitive &&
                (draftEngine.safety as JsonPrimitive).content == "model_default"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = isModelDefault,
                onClick = {
                  if (!isReadOnly) {
                    val updated = draftEngine.copy(safety = JsonPrimitive("model_default"))
                    draftEngine = updated
                    onSaveDraft(updated)
                  }
                },
                enabled = !isReadOnly,
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = RiviaPanelSurface,
                    labelColor = RiviaTextPrimary,
                    selectedContainerColor = RiviaTeal,
                    selectedLabelColor = RiviaOnAccent,
                    disabledContainerColor = RiviaPanelSurface,
                    disabledLabelColor = RiviaTextTertiary,
                    disabledSelectedContainerColor = RiviaTeal,
                  ),
                border =
                  BorderStroke(
                    width = 1.dp,
                    color = if (isModelDefault) RiviaTeal else RiviaBorder,
                  ),
                label = {
                  Text(
                    "model_default",
                    color = if (isModelDefault) RiviaOnAccent else RiviaTextPrimary,
                    fontWeight = if (isModelDefault) FontWeight.Bold else FontWeight.Normal,
                  )
                },
              )
              FilterChip(
                selected = !isModelDefault,
                onClick = {
                  if (!isReadOnly && isModelDefault) {
                    val defaultCustom =
                      JsonArray(
                        listOf(
                          kotlinx.serialization.json.JsonObject(
                            mapOf(
                              "type" to JsonPrimitive("HARM_CATEGORY_HARASSMENT"),
                              "threshold" to JsonPrimitive("BLOCK_ONLY_HIGH"),
                              "method" to JsonPrimitive("severity"),
                            )
                          )
                        )
                      )
                    val updated = draftEngine.copy(safety = defaultCustom)
                    draftEngine = updated
                    onSaveDraft(updated)
                  }
                },
                enabled = !isReadOnly,
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = RiviaPanelSurface,
                    labelColor = RiviaTextPrimary,
                    selectedContainerColor = RiviaTeal,
                    selectedLabelColor = RiviaOnAccent,
                    disabledContainerColor = RiviaPanelSurface,
                    disabledLabelColor = RiviaTextTertiary,
                    disabledSelectedContainerColor = RiviaTeal,
                  ),
                border =
                  BorderStroke(
                    width = 1.dp,
                    color = if (!isModelDefault) RiviaTeal else RiviaBorder,
                  ),
                label = {
                  Text(
                    "custom list",
                    color = if (!isModelDefault) RiviaOnAccent else RiviaTextPrimary,
                    fontWeight = if (!isModelDefault) FontWeight.Bold else FontWeight.Normal,
                  )
                },
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineProfileEditorRow(
  profileKey: String,
  displayLabelOverride: String? = null,
  profileConfig: EngineProfileConfig,
  availableModels: List<String>,
  readOnly: Boolean,
  onUpdateProfile: (EngineProfileConfig) -> Unit,
) {
  var modelMenuExpanded by remember { mutableStateOf(false) }
  val allowedThinking = RunSettings.allowedThinkingLevels(profileConfig.model)

  Surface(
    color = MaterialTheme.colorScheme.background,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = displayLabelOverride ?: LayerConfigManager.displayProfileLabel(profileKey),
          style = MaterialTheme.typography.labelLarge,
          color = RiviaTextPrimary,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.align(Alignment.CenterVertically),
        )
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
          Surface(
            color = RiviaPanelSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier =
              Modifier.clickable(enabled = !readOnly) {
                keyboardController?.hide()
                focusManager.clearFocus()
                modelMenuExpanded = true
              },
          ) {
            Text(
              text = profileConfig.model,
              fontFamily = MonoFontFamily,
              fontSize = 12.sp,
              color = RiviaTextPrimary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
          DropdownMenu(
            expanded = modelMenuExpanded,
            onDismissRequest = { modelMenuExpanded = false },
            containerColor = RiviaSurface1,
          ) {
            for (m in availableModels) {
              DropdownMenuItem(
                text = { Text(m, fontFamily = MonoFontFamily, fontSize = 12.sp, color = RiviaTextPrimary) },
                onClick = {
                  modelMenuExpanded = false
                  val newAllowed = RunSettings.allowedThinkingLevels(m)
                  val coercedThinking =
                    if (profileConfig.thinking_level in newAllowed) profileConfig.thinking_level
                    else newAllowed.first()
                  onUpdateProfile(EngineProfileConfig(model = m, thinking_level = coercedThinking))
                },
              )
            }
          }
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("thinking_chips_row_$profileKey"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Thinking:",
          style = MaterialTheme.typography.labelSmall,
          color = RiviaSecondaryText,
          modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp),
        )
        for (lvl in allowedThinking) {
          val isSelected = profileConfig.thinking_level == lvl
          FilterChip(
            selected = isSelected,
            onClick = {
              if (!readOnly) {
                onUpdateProfile(profileConfig.copy(thinking_level = lvl))
              }
            },
            enabled = !readOnly,
            leadingIcon =
              if (isSelected) {
                {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = RiviaOnAccent,
                    modifier = Modifier.size(14.dp),
                  )
                }
              } else {
                null
              },
            colors =
              FilterChipDefaults.filterChipColors(
                containerColor = RiviaPanelSurface,
                labelColor = RiviaTextPrimary,
                selectedContainerColor = RiviaTeal,
                selectedLabelColor = RiviaOnAccent,
                disabledContainerColor = RiviaPanelSurface,
                disabledSelectedContainerColor = RiviaTeal,
                disabledLabelColor =
                  if (isSelected) RiviaOnAccent else RiviaTextTertiary,
              ),
            border =
              BorderStroke(
                width = 1.dp,
                color = if (isSelected) RiviaTeal else RiviaBorder,
              ),
            label = {
              Text(
                text = lvl,
                fontSize = 11.sp,
                color = if (isSelected) RiviaOnAccent else RiviaTextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
              )
            },
            modifier = Modifier.testTag("thinking_chip_${profileKey}_$lvl"),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HardRulesLayerCard(
  meta: LayerCardUiMeta,
  bundle: LoadedLayersBundle,
  spokenMaxSentences: Int,
  h11WatchWords: List<String>,
  recentRuleEvents: List<RuleEventEntity>,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onShowInfo: () -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
  onToggleLocked: (Boolean) -> Unit,
  onSpokenMaxChange: (Int) -> Unit,
  onAddWatchWord: (String) -> Unit,
  onRemoveWatchWord: (String) -> Unit,
  onSaveDraft: (HardRulesLayerConfig) -> Unit,
  onClearLayer: () -> Unit,
  onRevertLayer: () -> Unit,
  onViewFile: () -> Unit,
) {
  var newWatchWord by remember { mutableStateOf("") }
  var draftRules by remember(bundle.hardRules.rules) { mutableStateOf(bundle.hardRules.rules) }
  val isReadOnly = meta.isLocked || !meta.enabled
  val sortedRules = remember(draftRules) { draftRules.sortedBy { it.priority } }
  val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_hard_rules"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LayerCardHeader(
        title = "Hard rules",
        meta = meta,
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        onShowInfo = onShowInfo,
        onToggleEnabled = onToggleEnabled,
        onToggleLocked = onToggleLocked,
        onClear = {
          newWatchWord = ""
          draftRules =
            draftRules.map { r ->
              if (r.enforced_by != "code" || r.id == "H2") r.copy(active = false) else r
            }
          onClearLayer()
        },
        onSave = {
          onSaveDraft(bundle.hardRules.copy(locked = false, rules = draftRules))
        },
        onRevert = {
          newWatchWord = ""
          draftRules = bundle.bundledHardRules.rules
          onRevertLayer()
        },
        onViewFile = onViewFile,
      )

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          // Spoken sentence limit picker (1..4, default 2) for tunable rule H7
          Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "H7 Spoken sentence limit",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "spoken_max_sentences (1..4, default 2)",
                  fontFamily = MonoFontFamily,
                  fontSize = 11.sp,
                  color = RiviaSecondaryText,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 1..4) {
                  val isSelected = spokenMaxSentences == n
                  FilterChip(
                    selected = isSelected,
                    onClick = { if (!isReadOnly) onSpokenMaxChange(n) },
                    enabled = !isReadOnly,
                    colors =
                      FilterChipDefaults.filterChipColors(
                        containerColor = RiviaPanelSurface,
                        labelColor = RiviaTextPrimary,
                        selectedContainerColor = RiviaTeal,
                        selectedLabelColor = RiviaOnAccent,
                        disabledContainerColor = RiviaPanelSurface,
                        disabledLabelColor = RiviaTextTertiary,
                        disabledSelectedContainerColor = RiviaTeal,
                      ),
                    border =
                      BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) RiviaTeal else RiviaBorder,
                      ),
                    label = {
                      Text(
                        n.toString(),
                        color = if (isSelected) RiviaOnAccent else RiviaTextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                      )
                    },
                    modifier = Modifier.testTag("spoken_max_$n"),
                  )
                }
              }
            }
          }

          // List of all rules in priority order
          Text(
            text = "Invariants in priority order (1 first)",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaSecondaryText,
          )
          for (rule in sortedRules) {
            val canToggleRule =
              !isReadOnly &&
                (rule.enforced_by != "code" || rule.id in LayerConfigManager.SUPPORTED_CODE_RULE_HANDLERS)
            HardRuleItemRow(
              rule = rule,
              spokenMaxSentences = spokenMaxSentences,
              canToggle = canToggleRule,
              onToggleActive = { newActive ->
                draftRules =
                  draftRules.map { r ->
                    if (r.id == rule.id) r.copy(active = newActive) else r
                  }
              },
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

          // H11 Watch Words list
          Text(
            text = "H11 Watch words (audit only — never blocks or rewrites)",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaSecondaryText,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = newWatchWord,
              onValueChange = { if (!isReadOnly) newWatchWord = it },
              readOnly = isReadOnly,
              enabled = meta.enabled,
              singleLine = true,
              label = { Text("Add watch word or phrase") },
              modifier = Modifier.weight(1f).testTag("h11_watch_word_input"),
            )
            IconButton(
              onClick = {
                if (!isReadOnly && newWatchWord.isNotBlank()) {
                  onAddWatchWord(newWatchWord)
                  newWatchWord = ""
                }
              },
              enabled = !isReadOnly,
              modifier = Modifier.testTag("h11_add_watch_word_button"),
            ) {
              Icon(
                Icons.Default.Add,
                contentDescription = "Add watch word",
                tint = if (!isReadOnly) RiviaIconDefault else RiviaTextTertiary,
              )
            }
          }
          if (h11WatchWords.isNotEmpty()) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              for (w in h11WatchWords) {
                Surface(
                  color = RiviaSurface3,
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(1.dp, RiviaBorder),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(w, style = MaterialTheme.typography.labelSmall, color = RiviaTextPrimary)
                    if (!isReadOnly) {
                      Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove $w",
                        tint = RiviaIconDefault,
                        modifier =
                          Modifier.size(14.dp).clickable { onRemoveWatchWord(w) },
                      )
                    }
                  }
                }
              }
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

          // Last 20 rule events
          Text(
            text = "Recent rule events (last 20)",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaSecondaryText,
          )
          if (recentRuleEvents.isEmpty()) {
            Text(
              text = "No rule events logged yet.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaSecondaryText,
            )
          } else {
            for (ev in recentRuleEvents) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "${ev.ruleId} (${ev.eventType}): ${ev.detail}",
                  fontFamily = MonoFontFamily,
                  fontSize = 11.sp,
                  color = RiviaAmber,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "${ev.requestId} · ${timeFormat.format(Date(ev.timestamp))}",
                  fontFamily = MonoFontFamily,
                  fontSize = 10.sp,
                  color = RiviaTextTertiary,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HardRuleItemRow(
  rule: HardRuleConfig,
  spokenMaxSentences: Int,
  canToggle: Boolean = false,
  onToggleActive: ((Boolean) -> Unit)? = null,
) {
  Surface(
    color = MaterialTheme.colorScheme.background,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "#${rule.priority} ${rule.id} · ${rule.name}",
          style = MaterialTheme.typography.labelLarge,
          color = RiviaTextPrimary,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text =
              if (rule.active) "active (${rule.enforced_by})"
              else "inactive (${rule.status.ifBlank { "deferred" }})",
            fontFamily = MonoFontFamily,
            fontSize = 10.sp,
            color = if (rule.active) RiviaTextPrimary else RiviaSecondaryText,
          )
          if (canToggle && onToggleActive != null) {
            Checkbox(
              checked = rule.active,
              onCheckedChange = onToggleActive,
              modifier = Modifier.size(20.dp).testTag("toggle_rule_${rule.id}"),
            )
          }
        }
      }
      Text(
        text = "channels: ${rule.channels.joinToString(", ")}",
        fontFamily = MonoFontFamily,
        fontSize = 10.sp,
        color = RiviaSecondaryText,
      )
      if (!rule.prompt.isNullOrBlank()) {
        Text(
          text = rule.prompt.replace("{spoken_max_sentences}", spokenMaxSentences.toString()),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      } else if (!rule.note.isNullOrBlank()) {
        Text(
          text = rule.note,
          style = MaterialTheme.typography.bodySmall,
          color = RiviaSecondaryText,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestsLayerCard(
  meta: LayerCardUiMeta,
  bundle: LoadedLayersBundle,
  fingerprints: Map<String, String>,
  testRuns: List<LayerTestRunEntity>,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onShowInfo: () -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
  onToggleLocked: (Boolean) -> Unit,
  onClearLayer: () -> Unit,
  onSaveLayer: () -> Unit,
  onRevertLayer: () -> Unit,
  onOpenLogRun: (TestDefConfig) -> Unit,
  onViewFile: () -> Unit,
) {
  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_tests"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LayerCardHeader(
        title = "Tests",
        meta = meta,
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        onShowInfo = onShowInfo,
        onToggleEnabled = onToggleEnabled,
        onToggleLocked = onToggleLocked,
        onClear = onClearLayer,
        onSave = onSaveLayer,
        onRevert = onRevertLayer,
        onViewFile = onViewFile,
      )

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

          for (testDef in bundle.tests.tests) {
            Surface(
              color = MaterialTheme.colorScheme.background,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, RiviaBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = testDef.name,
                      style = MaterialTheme.typography.labelLarge,
                      color = RiviaTextPrimary,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text = "${testDef.id} · kind: ${testDef.kind}",
                      fontFamily = MonoFontFamily,
                      fontSize = 11.sp,
                      color = RiviaSecondaryText,
                    )
                  }
                  OutlinedButton(
                    onClick = { onOpenLogRun(testDef) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, RiviaBorder),
                    colors =
                      ButtonDefaults.outlinedButtonColors(
                        containerColor = RiviaPanelSurface,
                        contentColor = RiviaTextPrimary,
                      ),
                    modifier = Modifier.testTag("log_run_${testDef.id}"),
                  ) {
                    Text("Log a run", style = MaterialTheme.typography.labelSmall, color = RiviaTextPrimary)
                  }
                }

                if (!testDef.pass.isNullOrBlank()) {
                  Text(
                    text = "Pass: ${testDef.pass}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiviaSecondaryText,
                  )
                }

                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  for (reqProf in testDef.required_profiles) {
                    val normProf = LayerConfigManager.normalizeProfileKey(reqProf)
                    val fp = fingerprints[normProf] ?: "--------"
                    val latestRun =
                      testRuns
                        .filter {
                          it.testId == testDef.id &&
                            LayerConfigManager.normalizeProfileKey(it.profile) == normProf &&
                            it.fingerprint == fp
                        }
                        .maxByOrNull { it.timestamp }
                    val state =
                      when {
                        latestRun == null -> ProfileTestState.NOT_RUN
                        latestRun.passed -> ProfileTestState.PASSED
                        else -> ProfileTestState.FAILED
                      }
                    val (symbol, tint) = profileTestIndicator(state)
                    Surface(
                      color = tint.copy(alpha = 0.12f),
                      shape = RoundedCornerShape(50),
                      border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
                    ) {
                      Text(
                        text = "$symbol $normProf ($fp): ${state.label}",
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogTestRunDialog(
  appUiMode: AppUiMode = AppUiMode.LABS,
  testDef: TestDefConfig,
  fingerprints: Map<String, String>,
  onDismiss: () -> Unit,
  onSaveRun: (profile: String, passed: Boolean, detailsJson: String) -> Unit,
) {
  val initialProf =
    if (appUiMode == AppUiMode.LABS) {
      testDef.required_profiles.firstOrNull() ?: "balanced"
    } else {
      "live"
    }
  var selectedProfile by remember(appUiMode) { mutableStateOf(initialProf) }
  val normProfile = LayerConfigManager.normalizeProfileKey(selectedProfile)
  val currentFp = fingerprints[normProfile] ?: "--------"

  // H10 fields
  var run1ViolationsText by remember { mutableStateOf("0") }
  var run2ViolationsText by remember { mutableStateOf("0") }
  var targetMet by remember { mutableStateOf(true) }

  // Checklist fields
  val checkedStates =
    remember(testDef.checks) {
      mutableStateListOf<Boolean>().apply { repeat(testDef.checks.size) { add(false) } }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = RiviaSurface1,
    title = {
      Text(
        "Log a run: ${testDef.name}",
        style = MaterialTheme.typography.titleMedium,
        color = RiviaTextPrimary,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (appUiMode == AppUiMode.LABS) {
          Text(
            text = "Select profile (saves on current fingerprint $currentFp):",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaSecondaryText,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val profileChoices =
              (testDef.required_profiles + listOf("live_typed")).distinct()
            for (profChoice in profileChoices) {
              val isSelected = selectedProfile == profChoice
              FilterChip(
                selected = isSelected,
                onClick = { selectedProfile = profChoice },
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = RiviaPanelSurface,
                    labelColor = RiviaTextPrimary,
                    selectedContainerColor = RiviaTeal,
                    selectedLabelColor = RiviaOnAccent,
                  ),
                border =
                  BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) RiviaTeal else RiviaBorder,
                  ),
                label = {
                  Text(
                    LayerConfigManager.displayProfileLabel(profChoice),
                    color = if (isSelected) RiviaOnAccent else RiviaTextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  )
                },
              )
            }
          }
        } else {
          Text(
            text = "Saves on current Rivia fingerprint $currentFp",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaSecondaryText,
          )
        }

        if (testDef.kind == "h10") {
          OutlinedTextField(
            value = run1ViolationsText,
            onValueChange = { run1ViolationsText = it },
            singleLine = true,
            label = { Text("Run 1 violations (0..24)") },
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = run2ViolationsText,
            onValueChange = { run2ViolationsText = it },
            singleLine = true,
            label = { Text("Run 2 violations (0..24)") },
            modifier = Modifier.fillMaxWidth(),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Target met (≤ 2 violations)",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaTextPrimary,
            )
            Switch(
              checked = targetMet,
              onCheckedChange = { targetMet = it },
              colors = riviaSwitchColors(),
            )
          }
        } else {
          Text(
            text = "Verify every check on $normProfile ($currentFp):",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaTextPrimary,
          )
          testDef.checks.forEachIndexed { idx, checkText ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { checkedStates[idx] = !checkedStates[idx] }
                  .padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = checkedStates[idx],
                onCheckedChange = { checkedStates[idx] = it },
              )
              Text(
                text = checkText,
                style = MaterialTheme.typography.bodySmall,
                color = RiviaTextPrimary,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (testDef.kind == "h10") {
            val r1 = (run1ViolationsText.toIntOrNull() ?: 0).coerceIn(0, 24)
            val r2 = (run2ViolationsText.toIntOrNull() ?: 0).coerceIn(0, 24)
            val passed = (r2 <= 2) && targetMet
            val details =
              JSONObject()
                .apply {
                  put("kind", "h10")
                  put("run1_violations", r1)
                  put("run2_violations", r2)
                  put("target_met", targetMet)
                }
                .toString()
            onSaveRun(normProfile, passed, details)
          } else {
            val allChecked = checkedStates.all { it }
            val arr = JSONArray()
            checkedStates.forEach { arr.put(it) }
            val details =
              JSONObject()
                .apply {
                  put("kind", "checklist")
                  put("checks", arr)
                }
                .toString()
            onSaveRun(normProfile, allChecked, details)
          }
        },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = RiviaTeal,
            contentColor = RiviaOnAccent,
          ),
      ) {
        Icon(
          Icons.Default.Check,
          contentDescription = null,
          tint = RiviaOnAccent,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Save run", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel", color = RiviaSecondaryText) }
    },
  )
}
