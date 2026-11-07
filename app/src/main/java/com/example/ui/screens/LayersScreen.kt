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
import com.example.model.LayerCardUiMeta
import com.example.model.LayerConfigManager
import com.example.model.LayerStatus
import com.example.model.LoadedLayersBundle
import com.example.model.ProfileTestState
import com.example.model.RunSettings
import com.example.model.TestDefConfig
import androidx.compose.material3.TabRowDefaults
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

  // Dialog states
  var viewFileDialogLayer by remember { mutableStateOf<String?>(null) }
  var startDraftConfirmLayer by remember { mutableStateOf<String?>(null) }
  var lockExportDialogData by remember { mutableStateOf<Pair<String, String>?>(null) }
  var logRunTestDef by remember { mutableStateOf<TestDefConfig?>(null) }

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
          // 0. Top summary bar: 8-char fingerprint and test state for fast, balanced, deep, live
          item {
            TopFingerprintSummaryBar(
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
                hasFailingTests = testStates.values.any { it == ProfileTestState.FAILED },
                onSaveDraft = { newText ->
                  viewModel.savePersonaDraft(newText).onFailure { viewModel.showError(it.message ?: "Invalid persona") }
                },
                onRequestStartDraft = { startDraftConfirmLayer = "persona" },
                onDiscardDraft = { viewModel.discardLayerDraft("persona") },
                onCancelPendingLock = { viewModel.cancelPendingLayerLock("persona") },
                onLockIt = {
                  coroutineScope.launch {
                    val exported = viewModel.prepareLayerLockExport("persona")
                    if (exported != null) {
                      lockExportDialogData = "persona.json" to exported
                    }
                  }
                },
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
                hasFailingTests = testStates.values.any { it == ProfileTestState.FAILED },
                onSaveDraft = { newText, newVoice ->
                  viewModel.saveStyleDraft(newText, newVoice).onFailure {
                    viewModel.showError(it.message ?: "Invalid style")
                  }
                },
                onRequestStartDraft = { startDraftConfirmLayer = "style" },
                onDiscardDraft = { viewModel.discardLayerDraft("style") },
                onCancelPendingLock = { viewModel.cancelPendingLayerLock("style") },
                onLockIt = {
                  coroutineScope.launch {
                    val exported = viewModel.prepareLayerLockExport("style")
                    if (exported != null) {
                      lockExportDialogData = "style.json" to exported
                    }
                  }
                },
                onViewFile = { viewFileDialogLayer = "style" },
              )
            }
          }

          // 3. Engine Card
          item {
            val meta = cardMetas["engine"]
            if (meta != null) {
              EngineLayerCard(
                meta = meta,
                bundle = loaded,
                availableModels = availableModels,
                expanded = expandedSections["engine"] ?: true,
                onToggleExpanded = {
                  expandedSections["engine"] = !(expandedSections["engine"] ?: true)
                },
                hasFailingTests = testStates.values.any { it == ProfileTestState.FAILED },
                onSaveDraft = { newEngine ->
                  viewModel.saveEngineDraft(newEngine).onFailure {
                    viewModel.showError(it.message ?: "Invalid engine config")
                  }
                },
                onRequestStartDraft = { startDraftConfirmLayer = "engine" },
                onDiscardDraft = { viewModel.discardLayerDraft("engine") },
                onCancelPendingLock = { viewModel.cancelPendingLayerLock("engine") },
                onLockIt = {
                  coroutineScope.launch {
                    val exported = viewModel.prepareLayerLockExport("engine")
                    if (exported != null) {
                      lockExportDialogData = "engine.json" to exported
                    }
                  }
                },
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
                hasFailingTests = testStates.values.any { it == ProfileTestState.FAILED },
                onSpokenMaxChange = { viewModel.setSpokenMaxSentences(it) },
                onAddWatchWord = { viewModel.addH11WatchWord(it) },
                onRemoveWatchWord = { viewModel.removeH11WatchWord(it) },
                onRequestStartDraft = { startDraftConfirmLayer = "hard_rules" },
                onDiscardDraft = { viewModel.discardLayerDraft("hard_rules") },
                onCancelPendingLock = { viewModel.cancelPendingLayerLock("hard_rules") },
                onLockIt = {
                  coroutineScope.launch {
                    val exported = viewModel.prepareLayerLockExport("hard_rules")
                    if (exported != null) {
                      lockExportDialogData = "hard_rules.json" to exported
                    }
                  }
                },
                onViewFile = { viewFileDialogLayer = "hard_rules" },
              )
            }
          }

          // 5. Tests Card (H10 pushback, H7 spoken length stay in Dev only)
          if (appUiMode == AppUiMode.DEV) {
            item {
              TestsLayerCard(
                bundle = loaded,
                fingerprints = fingerprints,
                testRuns = testRuns,
                expanded = expandedSections["tests"] ?: true,
                onToggleExpanded = {
                  expandedSections["tests"] = !(expandedSections["tests"] ?: true)
                },
                onOpenLogRun = { testDef -> logRunTestDef = testDef },
                onViewFile = { viewFileDialogLayer = "tests" },
              )
            }
          }
        }
      }
    }
  }

  // Start draft confirmation dialog when a layer is Locked
  startDraftConfirmLayer?.let { layerKey ->
    AlertDialog(
      onDismissRequest = { startDraftConfirmLayer = null },
      containerColor = RiviaSurface1,
      title = { Text("Start a draft for $layerKey?", color = RiviaTextPrimary) },
      text = {
        Text(
          "This layer is currently Locked in the build. Starting a draft lets you edit it on the phone until you discard the draft or lock a new version.",
          color = RiviaSecondaryText,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.startDraftFromLocked(layerKey)
            startDraftConfirmLayer = null
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaTeal,
              contentColor = RiviaOnAccent,
            ),
        ) {
          Text("Start draft", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { startDraftConfirmLayer = null }) {
          Text("Cancel", color = RiviaSecondaryText)
        }
      },
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

  // Lock it export dialog (Clarification #5: Copy button copies instruction line + JSON together)
  lockExportDialogData?.let { (fileName, exportedJson) ->
    val instructionLine = "Replace app/src/main/assets/config/$fileName with exactly this JSON, verbatim:"
    val combinedPayload = LayerConfigManager.formatLockCopyPayload(fileName, exportedJson)

    AlertDialog(
      onDismissRequest = { lockExportDialogData = null },
      containerColor = RiviaSurface1,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = RiviaIconDefault,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Lock $fileName", style = MaterialTheme.typography.titleMedium, color = RiviaTextPrimary)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = instructionLine,
            style = MaterialTheme.typography.bodySmall,
            color = RiviaAmber,
            fontWeight = FontWeight.SemiBold,
          )
          Surface(
            color = RiviaPanelSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
          ) {
            SelectionContainer {
              Text(
                text = exportedJson,
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
          Text(
            text = "Your phone draft stays active until the next build with this exact JSON lands.",
            style = MaterialTheme.typography.labelSmall,
            color = RiviaSecondaryText,
          )
        }
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "Lock $fileName")
                  putExtra(Intent.EXTRA_TEXT, combinedPayload)
                }
              context.startActivity(Intent.createChooser(sendIntent, "Share $fileName"))
            },
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaPanelSurface,
                contentColor = RiviaTextPrimary,
              ),
            modifier = Modifier.testTag("lock_share_button"),
          ) {
            Icon(
              Icons.Default.Share,
              contentDescription = null,
              tint = RiviaIconDefault,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share", color = RiviaTextPrimary)
          }
          Button(
            onClick = {
              clipboardManager.setText(AnnotatedString(combinedPayload))
              viewModel.showNotice("Copied instruction and $fileName JSON.")
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaTeal,
                contentColor = RiviaOnAccent,
              ),
            modifier = Modifier.testTag("lock_copy_button"),
          ) {
            Icon(
              Icons.Default.ContentCopy,
              contentDescription = null,
              tint = RiviaOnAccent,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { lockExportDialogData = null }) {
          Text("Done", color = RiviaSecondaryText)
        }
      },
    )
  }

  // Log a run dialog for Tests card
  logRunTestDef?.let { testDef ->
    LogTestRunDialog(
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
  fingerprints: Map<String, String>,
  testStates: Map<String, ProfileTestState>,
) {
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
        text = "Profile Fingerprints & Test State",
        style = MaterialTheme.typography.labelMedium,
        color = RiviaSecondaryText,
      )
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for (prof in LayerConfigManager.REQUIRED_PROFILES) {
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
                  text = LayerConfigManager.displayProfileLabel(prof),
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
  hasFailingTests: Boolean,
  onRequestStartDraft: () -> Unit,
  onDiscardDraft: () -> Unit,
  onCancelPendingLock: () -> Unit,
  onLockIt: () -> Unit,
  onViewFile: () -> Unit,
) {
  val (statusSymbol, statusColor) = layerStatusIndicator(meta.status)
  val canLock = meta.status == LayerStatus.TESTED && !hasFailingTests
  val isLockPending = !meta.lockPendingSha256.isNullOrBlank()

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Surface(
            color = statusColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
          ) {
            Text(
              text = "$statusSymbol ${meta.status.label}",
              color = statusColor,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
          if (isLockPending) {
            Surface(
              color = RiviaAmber.copy(alpha = 0.14f),
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, RiviaAmber.copy(alpha = 0.5f)),
              modifier = Modifier.testTag("lock_pending_badge_${meta.layerKey}"),
            ) {
              Text(
                text = "◆ Lock pending",
                color = RiviaAmber,
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
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (meta.status == LayerStatus.LOCKED) {
          OutlinedButton(
            onClick = onRequestStartDraft,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaPanelSurface,
                contentColor = RiviaTextPrimary,
              ),
            modifier = Modifier.testTag("start_draft_${meta.layerKey}"),
          ) {
            Text("Start draft", style = MaterialTheme.typography.labelSmall, color = RiviaTextPrimary)
          }
        }
        if (meta.hasDraft) {
          OutlinedButton(
            onClick = onDiscardDraft,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaPanelSurface,
                contentColor = RiviaTextPrimary,
              ),
            modifier = Modifier.testTag("discard_draft_${meta.layerKey}"),
          ) {
            Text("Discard draft", style = MaterialTheme.typography.labelSmall, color = RiviaTextPrimary)
          }
        }
        if (isLockPending) {
          OutlinedButton(
            onClick = onCancelPendingLock,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, RiviaAmber.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RiviaAmber),
            modifier = Modifier.testTag("cancel_pending_lock_${meta.layerKey}"),
          ) {
            Text("Cancel pending lock", style = MaterialTheme.typography.labelSmall)
          }
        }
        OutlinedButton(
          onClick = onLockIt,
          enabled = canLock,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = RiviaPanelSurface,
              contentColor = RiviaTextPrimary,
              disabledContainerColor = RiviaPanelSurface,
              disabledContentColor = RiviaTextTertiary,
            ),
          border = BorderStroke(1.dp, RiviaBorder),
          modifier = Modifier.testTag("lock_it_${meta.layerKey}"),
        ) {
          Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = if (canLock) RiviaIconDefault else RiviaTextTertiary,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Lock it", style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun PersonaLayerCard(
  meta: LayerCardUiMeta,
  bundle: LoadedLayersBundle,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  hasFailingTests: Boolean,
  onSaveDraft: (String) -> Unit,
  onRequestStartDraft: () -> Unit,
  onDiscardDraft: () -> Unit,
  onCancelPendingLock: () -> Unit,
  onLockIt: () -> Unit,
  onViewFile: () -> Unit,
) {
  var textValue by remember(bundle.persona.text) { mutableStateOf(bundle.persona.text) }
  val isReadOnly = meta.status == LayerStatus.LOCKED
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
        hasFailingTests = hasFailingTests,
        onRequestStartDraft = onRequestStartDraft,
        onDiscardDraft = onDiscardDraft,
        onCancelPendingLock = onCancelPendingLock,
        onLockIt = onLockIt,
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
            onValueChange = { textValue = it },
            readOnly = isReadOnly,
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
            if (!isReadOnly && textValue != bundle.persona.text) {
              Button(
                onClick = { onSaveDraft(textValue) },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = RiviaTeal,
                    contentColor = RiviaOnAccent,
                  ),
              ) {
                Text(
                  "Save draft",
                  style = MaterialTheme.typography.labelSmall,
                  color = RiviaOnAccent,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
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
  hasFailingTests: Boolean,
  onSaveDraft: (String, String) -> Unit,
  onRequestStartDraft: () -> Unit,
  onDiscardDraft: () -> Unit,
  onCancelPendingLock: () -> Unit,
  onLockIt: () -> Unit,
  onViewFile: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var typedStyleText by remember(bundle.style.text) { mutableStateOf(bundle.style.text) }
  var voiceStyleText by remember(bundle.style.voice) { mutableStateOf(bundle.style.voice) }
  val isReadOnly = meta.status == LayerStatus.LOCKED

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
        hasFailingTests = hasFailingTests,
        onRequestStartDraft = onRequestStartDraft,
        onDiscardDraft = onDiscardDraft,
        onCancelPendingLock = onCancelPendingLock,
        onLockIt = onLockIt,
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
              onValueChange = { typedStyleText = it },
              readOnly = isReadOnly,
              label = { Text("Typed style (style.text)") },
              modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).testTag("style_text_input"),
            )
          } else {
            OutlinedTextField(
              value = voiceStyleText,
              onValueChange = { voiceStyleText = it },
              readOnly = isReadOnly,
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
            if (
              !isReadOnly &&
                (typedStyleText != bundle.style.text || voiceStyleText != bundle.style.voice)
            ) {
              Button(
                onClick = { onSaveDraft(typedStyleText, voiceStyleText) },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = RiviaTeal,
                    contentColor = RiviaOnAccent,
                  ),
              ) {
                Text(
                  "Save draft",
                  style = MaterialTheme.typography.labelSmall,
                  color = RiviaOnAccent,
                  fontWeight = FontWeight.SemiBold,
                )
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
private fun EngineLayerCard(
  meta: LayerCardUiMeta,
  bundle: LoadedLayersBundle,
  availableModels: List<String>,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  hasFailingTests: Boolean,
  onSaveDraft: (EngineLayerConfig) -> Unit,
  onRequestStartDraft: () -> Unit,
  onDiscardDraft: () -> Unit,
  onCancelPendingLock: () -> Unit,
  onLockIt: () -> Unit,
  onViewFile: () -> Unit,
  onValidationError: (String) -> Unit,
) {
  val engine = bundle.engine
  val isReadOnly = meta.status == LayerStatus.LOCKED
  var maxTokensInput by
    remember(engine.max_output_tokens) {
      mutableStateOf(engine.max_output_tokens?.toString() ?: "")
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
        hasFailingTests = hasFailingTests,
        onRequestStartDraft = onRequestStartDraft,
        onDiscardDraft = onDiscardDraft,
        onCancelPendingLock = onCancelPendingLock,
        onLockIt = onLockIt,
        onViewFile = onViewFile,
      )

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          // Profile rows: fast, balanced, deep, live
          for (profKey in LayerConfigManager.REQUIRED_PROFILES) {
            val profCfg =
              engine.profiles[profKey]
                ?: EngineProfileConfig(model = "gemini-3.8-flash", thinking_level = "low")
            EngineProfileEditorRow(
              profileKey = profKey,
              profileConfig = profCfg,
              availableModels = availableModels,
              readOnly = isReadOnly,
              onUpdateProfile = { updatedProf ->
                val updatedMap = engine.profiles.toMutableMap().apply { put(profKey, updatedProf) }
                onSaveDraft(engine.copy(profiles = updatedMap))
              },
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

          // Default profile selector
          Text(
            text = "Default profile",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaSecondaryText,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (profKey in LayerConfigManager.REQUIRED_PROFILES) {
              val isSelected = engine.default_profile == profKey
              FilterChip(
                selected = isSelected,
                onClick = {
                  if (!isReadOnly) {
                    onSaveDraft(engine.copy(default_profile = profKey))
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

          // Thought summaries, Service tier, Store
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Thought summaries", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              for (opt in listOf("auto", "none")) {
                val isSelected = engine.thought_summaries == opt
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    if (!isReadOnly) onSaveDraft(engine.copy(thought_summaries = opt))
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
                val isSelected = engine.service_tier == tier
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    if (!isReadOnly) onSaveDraft(engine.copy(service_tier = tier))
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
              checked = engine.store,
              onCheckedChange = { if (!isReadOnly) onSaveDraft(engine.copy(store = it)) },
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
              onValueChange = { maxTokensInput = it },
              readOnly = isReadOnly,
              singleLine = true,
              label = { Text("max_output_tokens (blank = null)") },
              modifier = Modifier.weight(1f),
            )
            if (!isReadOnly && maxTokensInput != (engine.max_output_tokens?.toString() ?: "")) {
              OutlinedButton(
                onClick = {
                  val trimmed = maxTokensInput.trim()
                  if (trimmed.isEmpty()) {
                    onSaveDraft(engine.copy(max_output_tokens = null))
                  } else {
                    val parsed = trimmed.toIntOrNull()
                    if (parsed == null || parsed < 1) {
                      onValidationError("engine.json: max_output_tokens must be null or a positive whole number")
                    } else {
                      onSaveDraft(engine.copy(max_output_tokens = parsed))
                    }
                  }
                },
                border = BorderStroke(1.dp, RiviaBorder),
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    containerColor = RiviaPanelSurface,
                    contentColor = RiviaTextPrimary,
                  ),
              ) {
                Text("Apply", color = RiviaTextPrimary)
              }
            }
          }

          // Safety mode summary ("model_default" vs custom list)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Safety", style = MaterialTheme.typography.bodySmall)
            val isModelDefault =
              engine.safety is JsonPrimitive && engine.safety.content == "model_default"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = isModelDefault,
                onClick = {
                  if (!isReadOnly) {
                    onSaveDraft(engine.copy(safety = JsonPrimitive("model_default")))
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
                    onSaveDraft(engine.copy(safety = defaultCustom))
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
          text = LayerConfigManager.displayProfileLabel(profileKey),
          style = MaterialTheme.typography.labelLarge,
          color = RiviaTextPrimary,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.align(Alignment.CenterVertically),
        )
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
          Surface(
            color = RiviaPanelSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier =
              Modifier.clickable(enabled = !readOnly) { modelMenuExpanded = true },
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
  hasFailingTests: Boolean,
  onSpokenMaxChange: (Int) -> Unit,
  onAddWatchWord: (String) -> Unit,
  onRemoveWatchWord: (String) -> Unit,
  onRequestStartDraft: () -> Unit,
  onDiscardDraft: () -> Unit,
  onCancelPendingLock: () -> Unit,
  onLockIt: () -> Unit,
  onViewFile: () -> Unit,
) {
  var newWatchWord by remember { mutableStateOf("") }
  val sortedRules = remember(bundle.hardRules.rules) { bundle.hardRules.rules.sortedBy { it.priority } }
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
        hasFailingTests = hasFailingTests,
        onRequestStartDraft = onRequestStartDraft,
        onDiscardDraft = onDiscardDraft,
        onCancelPendingLock = onCancelPendingLock,
        onLockIt = onLockIt,
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
                    onClick = { onSpokenMaxChange(n) },
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

          // Read-only list of all rules in priority order
          Text(
            text = "Invariants in priority order (1 first)",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaSecondaryText,
          )
          for (rule in sortedRules) {
            HardRuleItemRow(rule = rule, spokenMaxSentences = spokenMaxSentences)
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
              onValueChange = { newWatchWord = it },
              singleLine = true,
              label = { Text("Add watch word or phrase") },
              modifier = Modifier.weight(1f).testTag("h11_watch_word_input"),
            )
            IconButton(
              onClick = {
                if (newWatchWord.isNotBlank()) {
                  onAddWatchWord(newWatchWord)
                  newWatchWord = ""
                }
              },
              modifier = Modifier.testTag("h11_add_watch_word_button"),
            ) {
              Icon(
                Icons.Default.Add,
                contentDescription = "Add watch word",
                tint = RiviaIconDefault,
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
        )
        Text(
          text =
            if (rule.active) "active (${rule.enforced_by})"
            else "inactive (${rule.status.ifBlank { "deferred" }})",
          fontFamily = MonoFontFamily,
          fontSize = 10.sp,
          color = if (rule.active) RiviaTextPrimary else RiviaSecondaryText,
        )
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
  bundle: LoadedLayersBundle,
  fingerprints: Map<String, String>,
  testRuns: List<LayerTestRunEntity>,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
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
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .testTag("layer_header_tests"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Tests",
            style = MaterialTheme.typography.titleMedium,
            color = RiviaTextPrimary,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "v${bundle.tests.version} · ${bundle.tests.updated}",
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            color = RiviaSecondaryText,
          )
        }
        IconButton(
          onClick = onToggleExpanded,
          modifier = Modifier.testTag("toggle_layer_tests"),
        ) {
          Icon(
            imageVector =
              if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse Tests" else "Expand Tests",
            tint = RiviaSecondaryText,
          )
        }
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
              onClick = onViewFile,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              colors = ButtonDefaults.textButtonColors(contentColor = RiviaTextPrimary),
              modifier = Modifier.testTag("view_file_tests"),
            ) {
              Text("View file", style = MaterialTheme.typography.labelSmall, color = RiviaTextPrimary)
            }
          }

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
  testDef: TestDefConfig,
  fingerprints: Map<String, String>,
  onDismiss: () -> Unit,
  onSaveRun: (profile: String, passed: Boolean, detailsJson: String) -> Unit,
) {
  val initialProf = testDef.required_profiles.firstOrNull() ?: "balanced"
  var selectedProfile by remember { mutableStateOf(initialProf) }
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
