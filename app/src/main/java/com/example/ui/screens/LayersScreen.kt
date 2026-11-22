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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import com.example.model.LayerCardUiMeta
import com.example.model.LayerConfigManager
import com.example.model.LayerStatus
import com.example.model.LoadedLayersBundle
import com.example.model.ProfileTestState
import com.example.model.RunSettings
import com.example.model.TestDefConfig
import com.example.network.MoodCommandResult
import com.example.network.MoodDialMeta
import com.example.network.MoodLayerEntry
import com.example.network.MoodLayersSnapshot
import com.example.network.MoodLockInfo
import com.example.network.MoodManualInfo
import com.example.network.MoodPostCommand
import com.example.network.MoodResult
import com.example.network.MoodState
import com.example.network.TabletMoodClient
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
import com.example.ui.theme.RiviaModeBlue
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
  var vadExpanded by remember { mutableStateOf(true) }
  var layer2Expanded by remember { mutableStateOf(true) }
  val emotionPanelState = remember {
    EmotionPanelState(
      viewModel = viewModel,
      coroutineScope = coroutineScope,
    )
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
          val anyExpanded = vadExpanded || layer2Expanded
          TextButton(
            onClick = {
              val nextExpanded = !anyExpanded
              vadExpanded = nextExpanded
              layer2Expanded = nextExpanded
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

      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          VadEngineLayerCard(
            expanded = vadExpanded,
            onToggleExpanded = { vadExpanded = !vadExpanded },
            viewModel = viewModel,
            panelState = emotionPanelState,
          )
        }
        item {
          MoodBaselineLayerCard(
            expanded = layer2Expanded,
            onToggleExpanded = { layer2Expanded = !layer2Expanded },
            panelState = emotionPanelState,
          )
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

private data class VadSubStepSpec(
  val testTag: String,
  val title: String,
  val description: String,
)

private val VAD_SUB_STEPS =
  listOf(
    VadSubStepSpec(
      testTag = "vad_step_1_1",
      title = "1.1 Mood storage",
      description = "A place on the tablet to store her mood. It starts at calm.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_1_2",
      title = "1.2 Mood limits",
      description = "A guardrail so her mood can't go past the max or min.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_1_3",
      title = "1.3 Emotion cheat sheet",
      description = "A cheat sheet of what angry, happy, sad and scared look like as numbers.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_1_4",
      title = "1.4 Mood label",
      description = "Turns her numbers into a word, like \"mildly annoyed\".",
    ),
    VadSubStepSpec(
      testTag = "vad_step_1_5",
      title = "1.5 Manual mood dial",
      description = "A dial to set or bump her mood by hand.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_1_6",
      title = "1.6 Mood line for Gemini",
      description = "Writes the line that would tell Gemini her mood, but only saves it.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_1_7",
      title = "1.7 Mood diary",
      description = "A diary of every mood change: what, why and when.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_1_8",
      title = "1.8 Self-check",
      description = "Checks that the guardrails and labels work.",
    ),
  )

@Composable
private fun VadEngineLayerCard(
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  viewModel: RiviaViewModel,
  panelState: EmotionPanelState,
) {
  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_vad_engine"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .testTag("layer_header_vad_engine"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 1: VAD Engine",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          Text(
            text =
              "Rivia's behind-the-scenes mood, kept as three numbers: how good she feels, how wound up she is, and how in control she feels. Right now it only keeps track of her mood. It doesn't change her replies yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
            modifier = Modifier.testTag("vad_description"),
          )
        }

        IconButton(
          onClick = onToggleExpanded,
          modifier = Modifier.testTag("toggle_layer_vad_engine"),
        ) {
          Icon(
            imageVector =
              if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription =
              if (expanded) "Collapse Layer 1: VAD Engine" else "Expand Layer 1: VAD Engine",
            tint = RiviaSecondaryText,
          )
        }
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("vad_sublayers_container"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (step in VAD_SUB_STEPS) {
            if (step.testTag == "vad_step_1_5") {
              ManualMoodDialPill(
                step = step,
                viewModel = viewModel,
                panelState = panelState,
              )
            } else {
              Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiviaBorder),
                modifier = Modifier.fillMaxWidth().testTag(step.testTag),
              ) {
                Column(
                  modifier = Modifier.padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = step.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = RiviaTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
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
}

private val MOOD_BASELINE_SUB_STEPS =
  listOf(
    VadSubStepSpec(
      testTag = "vad_step_2_1",
      title = "2.1 Three layers",
      description = "Splits her mood into emotion (fast), mood (slow) and baseline (her personality).",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_2",
      title = "2.2 Fading",
      description = "Emotion fades back toward mood over about 10 minutes, and mood fades toward baseline over hours.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_3",
      title = "2.3 Her normal reads neutral",
      description = "Her mood word is read against her baseline, so being her normal self reads as neutral.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_4",
      title = "2.4 Mood & baseline controls",
      description = "Locked controls to set or nudge her mood and baseline by hand, for testing.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_5",
      title = "2.5 Fading in the diary",
      description = "Fading isn't logged second by second. The next real change records how much she faded since the last one.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_6",
      title = "2.6 Measuring her baseline",
      description = "Runs fixed prompts on Gemini to measure where she naturally rests. Runs from a computer, not the app.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_7",
      title = "2.7 Drift limits",
      description = "Caps how far her baseline can drift: 0.20 from her anchor, 0.02 a day, 0.05 a week.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_8",
      title = "2.8 Weekly update check",
      description = "Checks weekly whether a Gemini update moved her personality, and only speaks up when something needs a decision. Off until it's switched on.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_2_9",
      title = "2.9 Self-check",
      description = "Tests that check all of Step 2 works together.",
    ),
  )

@Composable
private fun MoodBaselineLayerCard(
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  panelState: EmotionPanelState,
) {
  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_mood_baseline"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .testTag("layer_header_mood_baseline"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 2: Mood & Baseline",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          Text(
            text =
              "Her mood in three layers: a quick emotion, a slower mood, and her baseline, which is her normal. Emotion fades back to mood, and mood fades back to baseline. It doesn't change her replies yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
            modifier = Modifier.testTag("mood_baseline_description"),
          )
        }

        IconButton(
          onClick = onToggleExpanded,
          modifier = Modifier.testTag("toggle_layer_mood_baseline"),
        ) {
          Icon(
            imageVector =
              if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription =
              if (expanded) "Collapse Layer 2: Mood & Baseline"
              else "Expand Layer 2: Mood & Baseline",
            tint = RiviaSecondaryText,
          )
        }
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("mood_baseline_sublayers_container"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (step in MOOD_BASELINE_SUB_STEPS) {
            if (step.testTag == "vad_step_2_4") {
              MoodBaselineControlsPill(
                step = step,
                panelState = panelState,
              )
            } else {
              Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiviaBorder),
                modifier = Modifier.fillMaxWidth().testTag(step.testTag),
              ) {
                Column(
                  modifier = Modifier.padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = step.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = RiviaTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
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
}

private class EmotionPanelState(
  private val viewModel: RiviaViewModel,
  val coroutineScope: CoroutineScope,
) {
  val moodClient = TabletMoodClient()

  // Shared single-flight guard across every button, slider, hold gesture, and dialog in 1.5 and 2.4
  var isLoading by mutableStateOf(false)

  // Shared server snapshot & lock state
  var currentState by mutableStateOf<MoodState?>(null)
  var layersSnapshot by mutableStateOf<MoodLayersSnapshot?>(null)
  var dialMeta by mutableStateOf<MoodDialMeta?>(null)
  var lockInfo by mutableStateOf<MoodLockInfo?>(null)
  var isServerLocked by mutableStateOf(true)
  var countdownSeconds by mutableIntStateOf(0)
  var countdownEpoch by mutableIntStateOf(0)
  var lastAutoZeroEpoch by mutableIntStateOf(-1)
  var loadVersion by mutableIntStateOf(0)

  // Scoped status feedback ("1.5" or "2.4")
  var statusMessage by mutableStateOf<String?>(null)
  var isStatusError by mutableStateOf(false)
  var statusOwner by mutableStateOf<String?>(null)

  fun applyLoadedSuccess(res: MoodResult.Success) {
    currentState = res.state
    if (res.layers != null) {
      layersSnapshot = res.layers
    }
    if (res.dial != null) {
      dialMeta = res.dial
    }
    if (res.lock != null) {
      lockInfo = res.lock
      isServerLocked = res.lock.locked
      countdownSeconds = if (res.lock.locked) 0 else res.lock.secondsLeft.coerceAtLeast(0)
      countdownEpoch++
    } else {
      isServerLocked = true
      countdownSeconds = 0
    }
    loadVersion++
  }

  fun triggerLoad(
    owner: String,
    preserveStatusOnSuccess: Boolean = false,
  ) {
    if (isLoading) return
    isLoading = true
    coroutineScope.launch {
      try {
        when (val res = viewModel.loadMoodDial()) {
          is MoodResult.Success -> {
            applyLoadedSuccess(res)
            if (!preserveStatusOnSuccess && statusOwner == owner) {
              statusMessage = null
              isStatusError = false
            }
          }
          is MoodResult.Failure -> {
            statusOwner = owner
            statusMessage = res.message
            isStatusError = true
          }
        }
      } finally {
        isLoading = false
      }
    }
  }

  fun triggerGeneralCommand(
    owner: String,
    command: MoodPostCommand,
    successNotice: (clamped: List<String>) -> String,
    onPostSucceeded: () -> Unit = {},
  ) {
    if (isLoading) return
    isLoading = true
    coroutineScope.launch {
      try {
        val serverUrl = viewModel.keyRepository.getTabletServerUrl()
        val appToken = viewModel.keyRepository.getTabletAppToken()
        val postRes =
          if (appToken.isBlank()) {
            MoodCommandResult.Failure(message = "Sign in first")
          } else {
            moodClient.postCommand(
              serverUrl = serverUrl,
              appToken = appToken,
              command = command,
            )
          }

        if (postRes is MoodCommandResult.Success) {
          onPostSucceeded()
        }

        // After every press, success or error, GET /app/emotion and redraw all 3 rows, badges & lock
        val getRes = viewModel.loadMoodDial()
        if (getRes is MoodResult.Success) {
          applyLoadedSuccess(getRes)
        }

        // Ensure 401 on POST signs out even if the follow-up GET hit a network error
        if (postRes is MoodCommandResult.Failure && postRes.errorCode == "http_401") {
          if (viewModel.keyRepository.getTabletAppToken().isNotBlank()) {
            viewModel.signOutGoogleAccount()
            viewModel.showError("Signed out, sign in again")
          }
        }

        statusOwner = owner
        when (postRes) {
          is MoodCommandResult.Failure -> {
            statusMessage = postRes.message
            isStatusError = true
          }
          is MoodCommandResult.Success -> {
            if (getRes is MoodResult.Failure) {
              statusMessage = getRes.message
              isStatusError = true
            } else {
              statusMessage = successNotice(postRes.clamped)
              isStatusError = false
            }
          }
        }
      } finally {
        isLoading = false
      }
    }
  }
}

private data class PendingBaselineDraft(
  val isSet: Boolean,
  val v: Double,
  val a: Double,
  val d: Double,
  val note: String,
)

private fun snapVadSetValue(value: Double): Float {
  val clamped = value.coerceIn(-1.0, 1.0)
  val snapped = Math.round(clamped * 20.0) / 20.0
  return if (snapped == 0.0) 0f else snapped.toFloat()
}

private fun snapVadNudgeValue(value: Float): Float {
  val clamped = value.toDouble().coerceIn(-0.5, 0.5)
  val snapped = Math.round(clamped * 20.0) / 20.0
  return if (snapped == 0.0) 0f else snapped.toFloat()
}

private fun formatVad2(value: Double): String {
  val normalized = if (Math.abs(value) < 0.005) 0.0 else value
  return String.format(Locale.US, "%.2f", normalized)
}

private fun formatMoodLayerPhrase(label: String, intensity: String): String {
  val cleanLabel = label.trim()
  val cleanIntensity = intensity.trim()
  if (cleanLabel.isEmpty() && cleanIntensity.isEmpty()) return "—"
  if (cleanLabel.equals("neutral", ignoreCase = true)) return "neutral"
  return listOf(cleanIntensity, cleanLabel).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun formatIsoTimestampLocal(raw: String): String {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return ""
  val localZone = ZoneId.systemDefault()
  val zoned: ZonedDateTime? =
    runCatching { Instant.parse(trimmed).atZone(localZone) }.getOrNull()
      ?: runCatching { OffsetDateTime.parse(trimmed).atZoneSameInstant(localZone) }.getOrNull()
      ?: runCatching { ZonedDateTime.parse(trimmed).withZoneSameInstant(localZone) }.getOrNull()

  if (zoned != null) {
    val today = LocalDate.now(localZone)
    val pattern = if (zoned.toLocalDate() == today) "HH:mm" else "MMM d HH:mm"
    return zoned.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
  }
  return trimmed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualMoodDialPill(
  step: VadSubStepSpec,
  viewModel: RiviaViewModel,
  panelState: EmotionPanelState,
) {
  var pillExpanded by rememberSaveable { mutableStateOf(false) }
  var hasLoadedOnce by rememberSaveable { mutableStateOf(false) }
  var seenLoadVersion by rememberSaveable { mutableIntStateOf(0) }

  // Existing Emotion dial state (sends no layer and no tag)
  var isSetMode by rememberSaveable { mutableStateOf(false) } // default is Nudge
  var sliderV by rememberSaveable { mutableFloatStateOf(0f) }
  var sliderA by rememberSaveable { mutableFloatStateOf(0f) }
  var sliderD by rememberSaveable { mutableFloatStateOf(0f) }
  var noteText by rememberSaveable { mutableStateOf("") }

  // While in Set mode, snap sliders to freshly loaded values whenever loadVersion advances
  LaunchedEffect(panelState.loadVersion) {
    if (panelState.loadVersion != seenLoadVersion) {
      seenLoadVersion = panelState.loadVersion
      if (isSetMode) {
        val emo = panelState.layersSnapshot?.emotion
        val st = panelState.currentState
        if (emo != null || st != null) {
          val emoV = emo?.v ?: st!!.v
          val emoA = emo?.a ?: st!!.a
          val emoD = emo?.d ?: st!!.d
          sliderV = snapVadSetValue(emoV)
          sliderA = snapVadSetValue(emoA)
          sliderD = snapVadSetValue(emoD)
        }
      }
    }
  }

  val isAllZeroNudge =
    !isSetMode &&
      Math.abs(sliderV) < 0.005f &&
      Math.abs(sliderA) < 0.005f &&
      Math.abs(sliderD) < 0.005f

  val emotionEntry = panelState.layersSnapshot?.emotion

  Surface(
    color = MaterialTheme.colorScheme.background,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth().testTag(step.testTag),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              val nextExpanded = !pillExpanded
              pillExpanded = nextExpanded
              if (nextExpanded && !hasLoadedOnce) {
                hasLoadedOnce = true
                panelState.triggerLoad(owner = "1.5")
              }
            }
            .testTag("vad_step_1_5_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = step.title,
          style = MaterialTheme.typography.labelLarge,
          color = RiviaTextPrimary,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector =
            if (pillExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription =
            if (pillExpanded) "Collapse ${step.title}" else "Expand ${step.title}",
          tint = RiviaSecondaryText,
          modifier = Modifier.size(18.dp),
        )
      }

      Text(
        text = step.description,
        style = MaterialTheme.typography.bodySmall,
        color = RiviaSecondaryText,
      )

      AnimatedVisibility(
        visible = pillExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Top header: Current Emotion readout + Refresh button
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            val state = panelState.currentState
            val currentMoodText =
              if (state == null && emotionEntry == null) {
                if (panelState.isLoading) "Loading..." else "No mood loaded"
              } else {
                val phrase =
                  if (emotionEntry != null) {
                    formatMoodLayerPhrase(emotionEntry.label, emotionEntry.intensity)
                  } else {
                    formatMoodLayerPhrase(state!!.label, state.intensity)
                  }
                val vNum = emotionEntry?.v ?: state!!.v
                val aNum = emotionEntry?.a ?: state!!.a
                val dNum = emotionEntry?.d ?: state!!.d
                val updatedPart =
                  if (!state?.updated.isNullOrBlank()) " · ${state!!.updated}" else ""
                "$phrase · V ${formatVad2(vNum)} A ${formatVad2(aNum)} D ${formatVad2(dNum)}$updatedPart"
              }

            Text(
              text = currentMoodText,
              style = MaterialTheme.typography.bodySmall,
              color = RiviaTextPrimary,
              modifier = Modifier.weight(1f).testTag("mood_dial_current"),
            )

            IconButton(
              onClick = { panelState.triggerLoad(owner = "1.5") },
              enabled = !panelState.isLoading,
              modifier =
                Modifier.size(36.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("mood_dial_refresh"),
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh mood",
                tint = RiviaSecondaryText,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          // Status line for 1.5 only when statusOwner == "1.5"
          if (panelState.statusOwner == "1.5") {
            panelState.statusMessage?.let { msg ->
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (panelState.isStatusError) RiviaDestructive else RiviaSecondaryText,
                modifier = Modifier.testTag("mood_dial_message"),
              )
            }
          }

          // Emotion dial (no layer, no tag)
          Text(
            text = "Emotion dial",
            style = MaterialTheme.typography.labelLarge,
            color = RiviaTextPrimary,
            fontWeight = FontWeight.SemiBold,
          )

          VadLayerDialControls(
            tagPrefix = "mood_dial",
            isSetMode = isSetMode,
            onModeChange = { targetSet ->
              isSetMode = targetSet
              if (targetSet) {
                val st = panelState.currentState
                sliderV = (emotionEntry?.v ?: st?.v)?.let { snapVadSetValue(it) } ?: 0f
                sliderA = (emotionEntry?.a ?: st?.a)?.let { snapVadSetValue(it) } ?: 0f
                sliderD = (emotionEntry?.d ?: st?.d)?.let { snapVadSetValue(it) } ?: 0f
              } else {
                sliderV = 0f
                sliderA = 0f
                sliderD = 0f
              }
            },
            sliderV = sliderV,
            onSliderVChange = { raw ->
              sliderV = if (isSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
            },
            sliderA = sliderA,
            onSliderAChange = { raw ->
              sliderA = if (isSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
            },
            sliderD = sliderD,
            onSliderDChange = { raw ->
              sliderD = if (isSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
            },
            noteText = noteText,
            onNoteChange = { noteText = it.take(200) },
            controlsEnabled = !panelState.isLoading,
            hasLoadedLayerValues = panelState.currentState != null,
            isAllZeroNudge = isAllZeroNudge,
            isLoading = panelState.isLoading,
            unloadedSetHint = "Load the current mood first (tap Refresh)",
            unloadedHintTag = "mood_dial_set_hint",
            applyLabel = "Apply",
            format2 = ::formatVad2,
            onApply = {
              if (panelState.isLoading) return@VadLayerDialControls
              if (isSetMode && panelState.currentState == null) return@VadLayerDialControls
              panelState.isLoading = true
              panelState.coroutineScope.launch {
                try {
                  val vVal = Math.round(sliderV.toDouble() * 100.0) / 100.0
                  val aVal = Math.round(sliderA.toDouble() * 100.0) / 100.0
                  val dVal = Math.round(sliderD.toDouble() * 100.0) / 100.0
                  val postRes =
                    viewModel.applyMoodDial(
                      isSet = isSetMode,
                      v = vVal,
                      a = aVal,
                      d = dVal,
                      note = noteText,
                    )

                  if (postRes is MoodResult.Success) {
                    panelState.currentState = postRes.state
                    if (!isSetMode) {
                      sliderV = 0f
                      sliderA = 0f
                      sliderD = 0f
                    } else {
                      sliderV = snapVadSetValue(postRes.state.v)
                      sliderA = snapVadSetValue(postRes.state.a)
                      sliderD = snapVadSetValue(postRes.state.d)
                    }
                    noteText = ""
                  }

                  // Always GET /app/emotion after every press (success or error) to redraw all rows, badges & lock
                  val getRes = viewModel.loadMoodDial()
                  if (getRes is MoodResult.Success) {
                    panelState.applyLoadedSuccess(getRes)
                  }

                  panelState.statusOwner = "1.5"
                  when (postRes) {
                    is MoodResult.Failure -> {
                      panelState.statusMessage = postRes.message
                      panelState.isStatusError = true
                    }
                    is MoodResult.Success -> {
                      if (getRes is MoodResult.Failure) {
                        panelState.statusMessage = getRes.message
                        panelState.isStatusError = true
                      } else {
                        panelState.statusMessage =
                          if (postRes.clamped.isNotEmpty()) {
                            "Mood updated (hit a limit)"
                          } else {
                            "Mood updated"
                          }
                        panelState.isStatusError = false
                      }
                    }
                  }
                } finally {
                  panelState.isLoading = false
                }
              }
            },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MoodBaselineControlsPill(
  step: VadSubStepSpec,
  panelState: EmotionPanelState,
) {
  val localScope = rememberCoroutineScope()

  var pillExpanded by rememberSaveable { mutableStateOf(false) }
  var hasLoadedOnce by rememberSaveable { mutableStateOf(false) }
  var seenLoadVersion by rememberSaveable { mutableIntStateOf(0) }
  var holdProgress by remember { mutableFloatStateOf(0f) }

  // Mood layer dial state (tag is always "test")
  var moodIsSetMode by rememberSaveable { mutableStateOf(false) }
  var moodSliderV by rememberSaveable { mutableFloatStateOf(0f) }
  var moodSliderA by rememberSaveable { mutableFloatStateOf(0f) }
  var moodSliderD by rememberSaveable { mutableFloatStateOf(0f) }
  var moodNoteText by rememberSaveable { mutableStateOf("") }

  // Baseline layer dial state (asks "Test or Calibration?", Calibration has 2nd confirm)
  var baselineIsSetMode by rememberSaveable { mutableStateOf(false) }
  var baselineSliderV by rememberSaveable { mutableFloatStateOf(0f) }
  var baselineSliderA by rememberSaveable { mutableFloatStateOf(0f) }
  var baselineSliderD by rememberSaveable { mutableFloatStateOf(0f) }
  var baselineNoteText by rememberSaveable { mutableStateOf("") }
  var pendingBaselineDraft by remember { mutableStateOf<PendingBaselineDraft?>(null) }
  var showBaselineTagDialog by remember { mutableStateOf(false) }
  var showBaselineCalibrationConfirmDialog by remember { mutableStateOf(false) }

  // While in Set mode, snap sliders to freshly loaded values whenever loadVersion advances
  LaunchedEffect(panelState.loadVersion) {
    if (panelState.loadVersion != seenLoadVersion) {
      seenLoadVersion = panelState.loadVersion
      if (moodIsSetMode) {
        panelState.layersSnapshot?.mood?.let { m ->
          moodSliderV = snapVadSetValue(m.v)
          moodSliderA = snapVadSetValue(m.a)
          moodSliderD = snapVadSetValue(m.d)
        }
      }
      if (baselineIsSetMode) {
        panelState.layersSnapshot?.baseline?.let { b ->
          baselineSliderV = snapVadSetValue(b.v)
          baselineSliderA = snapVadSetValue(b.a)
          baselineSliderD = snapVadSetValue(b.d)
        }
      }
    }
  }

  // Local ticking countdown while unlocked and 2.4 is expanded; when it hits 0, GET /app/emotion right away
  LaunchedEffect(panelState.isServerLocked, panelState.countdownEpoch, pillExpanded) {
    if (!panelState.isServerLocked && pillExpanded) {
      while (panelState.countdownSeconds > 0) {
        delay(1000L)
        panelState.countdownSeconds = (panelState.countdownSeconds - 1).coerceAtLeast(0)
      }
      if (!panelState.isServerLocked && panelState.lastAutoZeroEpoch != panelState.countdownEpoch) {
        panelState.lastAutoZeroEpoch = panelState.countdownEpoch
        panelState.triggerLoad(owner = "2.4", preserveStatusOnSuccess = true)
      }
    }
  }

  val moodEntry = panelState.layersSnapshot?.mood
  val baselineEntry = panelState.layersSnapshot?.baseline
  val emotionEntry = panelState.layersSnapshot?.emotion

  val moodNeedsLock = panelState.dialMeta?.requiresLock("mood") ?: true
  val baselineNeedsLock = panelState.dialMeta?.requiresLock("baseline") ?: true
  val moodMovable = panelState.dialMeta?.isMovable("mood") ?: true
  val baselineMovable = panelState.dialMeta?.isMovable("baseline") ?: true

  val isMoodLockedOut = (moodNeedsLock && panelState.isServerLocked) || !moodMovable
  val isBaselineLockedOut = (baselineNeedsLock && panelState.isServerLocked) || !baselineMovable

  val isMoodAllZeroNudge =
    !moodIsSetMode &&
      Math.abs(moodSliderV) < 0.005f &&
      Math.abs(moodSliderA) < 0.005f &&
      Math.abs(moodSliderD) < 0.005f

  val isBaselineAllZeroNudge =
    !baselineIsSetMode &&
      Math.abs(baselineSliderV) < 0.005f &&
      Math.abs(baselineSliderA) < 0.005f &&
      Math.abs(baselineSliderD) < 0.005f

  val latestIsLoading by rememberUpdatedState(panelState.isLoading)
  val latestIsServerLocked by rememberUpdatedState(panelState.isServerLocked)

  Surface(
    color = MaterialTheme.colorScheme.background,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth().testTag(step.testTag),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              val nextExpanded = !pillExpanded
              pillExpanded = nextExpanded
              if (nextExpanded && !hasLoadedOnce) {
                hasLoadedOnce = true
                panelState.triggerLoad(owner = "2.4")
              }
            }
            .testTag("vad_step_2_4_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = step.title,
          style = MaterialTheme.typography.labelLarge,
          color = RiviaTextPrimary,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector =
            if (pillExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription =
            if (pillExpanded) "Collapse ${step.title}" else "Expand ${step.title}",
          tint = RiviaSecondaryText,
          modifier = Modifier.size(18.dp),
        )
      }

      Text(
        text = step.description,
        style = MaterialTheme.typography.bodySmall,
        color = RiviaSecondaryText,
      )

      AnimatedVisibility(
        visible = pillExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Top Refresh row in 2.4
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Current layers",
              style = MaterialTheme.typography.labelMedium,
              color = RiviaSecondaryText,
            )

            IconButton(
              onClick = { panelState.triggerLoad(owner = "2.4") },
              enabled = !panelState.isLoading,
              modifier =
                Modifier.size(36.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("mood_baseline_refresh"),
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh mood and baseline",
                tint = RiviaSecondaryText,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          // Three rows: Emotion, Mood, Baseline (with manual badges & End test button)
          Surface(
            color = RiviaPanelSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.fillMaxWidth().testTag("mood_dial_layers_summary"),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              val fallbackEmotionEntry =
                emotionEntry
                  ?: panelState.currentState?.let {
                    MoodLayerEntry(
                      v = it.v,
                      a = it.a,
                      d = it.d,
                      label = it.label,
                      intensity = it.intensity,
                      manual = null,
                    )
                  }

              MoodLayerSummaryRow(
                rowTitle = "Emotion",
                layerKey = "emotion",
                entry = fallbackEmotionEntry,
                isLoading = panelState.isLoading,
                format2 = ::formatVad2,
                showBadges = false,
                onEndTest = null,
              )

              HorizontalDivider(color = RiviaBorder)

              MoodLayerSummaryRow(
                rowTitle = "Mood",
                layerKey = "mood",
                entry = moodEntry,
                isLoading = panelState.isLoading,
                format2 = ::formatVad2,
                showBadges = true,
                onEndTest = {
                  panelState.triggerGeneralCommand(
                    owner = "2.4",
                    command = MoodPostCommand.EndTest("mood"),
                    successNotice = { "Mood test ended" },
                  )
                },
              )

              HorizontalDivider(color = RiviaBorder)

              MoodLayerSummaryRow(
                rowTitle = "Baseline",
                layerKey = "baseline",
                entry = baselineEntry,
                isLoading = panelState.isLoading,
                format2 = ::formatVad2,
                showBadges = true,
                onEndTest = {
                  panelState.triggerGeneralCommand(
                    owner = "2.4",
                    command = MoodPostCommand.EndTest("baseline"),
                    successNotice = { "Baseline test ended" },
                  )
                },
              )
            }
          }

          // Status line for 2.4 only when statusOwner == "2.4"
          if (panelState.statusOwner == "2.4") {
            panelState.statusMessage?.let { msg ->
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (panelState.isStatusError) RiviaDestructive else RiviaSecondaryText,
                modifier = Modifier.testTag("mood_baseline_message"),
              )
            }
          }

          // Padlock above Mood & Baseline
          if (panelState.isServerLocked) {
            Surface(
              color = RiviaPanelSurface,
              shape = RoundedCornerShape(10.dp),
              border =
                BorderStroke(
                  1.dp,
                  if (holdProgress > 0.01f) RiviaAmber else RiviaBorder,
                ),
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .alpha(if (panelState.isLoading) 0.5f else 1f)
                  .pointerInput(Unit) {
                    detectTapGestures(
                      onPress = {
                        if (latestIsLoading || !latestIsServerLocked) return@detectTapGestures
                        val holdJob =
                          localScope.launch {
                            val startMs = android.os.SystemClock.uptimeMillis()
                            val requiredHoldMs = 1000L
                            while (true) {
                              if (latestIsLoading || !latestIsServerLocked) {
                                holdProgress = 0f
                                break
                              }
                              val elapsed = android.os.SystemClock.uptimeMillis() - startMs
                              holdProgress =
                                (elapsed.toFloat() / requiredHoldMs.toFloat()).coerceIn(0f, 1f)
                              if (elapsed >= requiredHoldMs) {
                                holdProgress = 0f
                                panelState.triggerGeneralCommand(
                                  owner = "2.4",
                                  command = MoodPostCommand.Unlock,
                                  successNotice = { "Mood & baseline unlocked" },
                                )
                                break
                              }
                              delay(25L)
                            }
                          }
                        try {
                          tryAwaitRelease()
                        } finally {
                          holdJob.cancel()
                          holdProgress = 0f
                        }
                      }
                    )
                  }
                  .testTag("mood_dial_padlock"),
            ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked. Hold 1 second to unlock Mood and Baseline",
                    tint = if (holdProgress > 0.01f) RiviaAmber else RiviaSecondaryText,
                    modifier = Modifier.size(18.dp),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Mood & Baseline locked",
                      style = MaterialTheme.typography.labelLarge,
                      color = RiviaTextPrimary,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text =
                        if (holdProgress > 0.01f) "Keep holding to unlock…"
                        else "Press and hold ~1 second to unlock",
                      style = MaterialTheme.typography.bodySmall,
                      color = RiviaSecondaryText,
                    )
                  }
                }
                if (holdProgress > 0.01f) {
                  LinearProgressIndicator(
                    progress = { holdProgress },
                    color = RiviaAmber,
                    trackColor = RiviaBorder,
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                  )
                }
              }
            }
          } else {
            Surface(
              color = RiviaPanelSurface,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, RiviaAmber.copy(alpha = 0.65f)),
              modifier = Modifier.fillMaxWidth().testTag("mood_dial_padlock_unlocked"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Unlocked",
                    tint = RiviaAmber,
                    modifier = Modifier.size(18.dp),
                  )
                  Column {
                    Text(
                      text = "Unlocked",
                      style = MaterialTheme.typography.labelLarge,
                      color = RiviaTextPrimary,
                      fontWeight = FontWeight.SemiBold,
                    )
                    val mins = panelState.countdownSeconds / 60
                    val secs = panelState.countdownSeconds % 60
                    val formattedLeft =
                      if (mins > 0) String.format(Locale.US, "%d:%02d left", mins, secs)
                      else "${panelState.countdownSeconds}s left"
                    Text(
                      text = formattedLeft,
                      style = MaterialTheme.typography.bodySmall,
                      fontFamily = MonoFontFamily,
                      color = RiviaAmber,
                      modifier = Modifier.testTag("mood_dial_countdown"),
                    )
                  }
                }

                OutlinedButton(
                  onClick = {
                    panelState.triggerGeneralCommand(
                      owner = "2.4",
                      command = MoodPostCommand.Lock,
                      successNotice = { "Mood & baseline locked" },
                    )
                  },
                  enabled = !panelState.isLoading,
                  border = BorderStroke(1.dp, RiviaBorder),
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("mood_dial_lock_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = RiviaTextPrimary,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Lock", color = RiviaTextPrimary, style = MaterialTheme.typography.labelMedium)
                }
              }
            }
          }

          // Mood controls card (tag = "test")
          Surface(
            color = RiviaPanelSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier =
              Modifier.fillMaxWidth()
                .alpha(if (isMoodLockedOut) 0.42f else 1f)
                .testTag("mood_layer_controls_card"),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Mood",
                  style = MaterialTheme.typography.labelLarge,
                  color = RiviaTextPrimary,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "tag: test",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = MonoFontFamily,
                  color = RiviaSecondaryText,
                )
              }

              VadLayerDialControls(
                tagPrefix = "mood_layer_dial",
                isSetMode = moodIsSetMode,
                onModeChange = { targetSet ->
                  moodIsSetMode = targetSet
                  if (targetSet) {
                    val m = moodEntry
                    moodSliderV = m?.let { snapVadSetValue(it.v) } ?: 0f
                    moodSliderA = m?.let { snapVadSetValue(it.a) } ?: 0f
                    moodSliderD = m?.let { snapVadSetValue(it.d) } ?: 0f
                  } else {
                    moodSliderV = 0f
                    moodSliderA = 0f
                    moodSliderD = 0f
                  }
                },
                sliderV = moodSliderV,
                onSliderVChange = { raw ->
                  moodSliderV =
                    if (moodIsSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
                },
                sliderA = moodSliderA,
                onSliderAChange = { raw ->
                  moodSliderA =
                    if (moodIsSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
                },
                sliderD = moodSliderD,
                onSliderDChange = { raw ->
                  moodSliderD =
                    if (moodIsSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
                },
                noteText = moodNoteText,
                onNoteChange = { moodNoteText = it.take(200) },
                controlsEnabled = !panelState.isLoading && !isMoodLockedOut,
                hasLoadedLayerValues = moodEntry != null,
                isAllZeroNudge = isMoodAllZeroNudge,
                isLoading = panelState.isLoading,
                unloadedSetHint = "Load the current mood layers first (tap Refresh)",
                unloadedHintTag = "mood_layer_dial_set_hint",
                applyLabel = "Apply Mood (Test)",
                format2 = ::formatVad2,
                onApply = {
                  if (panelState.isLoading || isMoodLockedOut) return@VadLayerDialControls
                  if (moodIsSetMode && moodEntry == null) return@VadLayerDialControls
                  val vVal = Math.round(moodSliderV.toDouble() * 100.0) / 100.0
                  val aVal = Math.round(moodSliderA.toDouble() * 100.0) / 100.0
                  val dVal = Math.round(moodSliderD.toDouble() * 100.0) / 100.0
                  val cmd =
                    if (moodIsSetMode) {
                      MoodPostCommand.SetLayer(
                        v = vVal,
                        a = aVal,
                        d = dVal,
                        layer = "mood",
                        tag = "test",
                        note = moodNoteText,
                      )
                    } else {
                      MoodPostCommand.NudgeLayer(
                        dv = vVal,
                        da = aVal,
                        dd = dVal,
                        layer = "mood",
                        tag = "test",
                        note = moodNoteText,
                      )
                    }
                  panelState.triggerGeneralCommand(
                    owner = "2.4",
                    command = cmd,
                    successNotice = { clamped ->
                      if (clamped.isNotEmpty()) "Mood layer updated (hit a limit)"
                      else "Mood layer updated"
                    },
                    onPostSucceeded = {
                      if (!moodIsSetMode) {
                        moodSliderV = 0f
                        moodSliderA = 0f
                        moodSliderD = 0f
                      }
                      moodNoteText = ""
                    },
                  )
                },
              )
            }
          }

          // Baseline controls card (Test or Calibration)
          Surface(
            color = RiviaPanelSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier =
              Modifier.fillMaxWidth()
                .alpha(if (isBaselineLockedOut) 0.42f else 1f)
                .testTag("baseline_layer_controls_card"),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Baseline",
                  style = MaterialTheme.typography.labelLarge,
                  color = RiviaTextPrimary,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "test / calibration",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = MonoFontFamily,
                  color = RiviaSecondaryText,
                )
              }

              VadLayerDialControls(
                tagPrefix = "baseline_layer_dial",
                isSetMode = baselineIsSetMode,
                onModeChange = { targetSet ->
                  baselineIsSetMode = targetSet
                  if (targetSet) {
                    val b = baselineEntry
                    baselineSliderV = b?.let { snapVadSetValue(it.v) } ?: 0f
                    baselineSliderA = b?.let { snapVadSetValue(it.a) } ?: 0f
                    baselineSliderD = b?.let { snapVadSetValue(it.d) } ?: 0f
                  } else {
                    baselineSliderV = 0f
                    baselineSliderA = 0f
                    baselineSliderD = 0f
                  }
                },
                sliderV = baselineSliderV,
                onSliderVChange = { raw ->
                  baselineSliderV =
                    if (baselineIsSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
                },
                sliderA = baselineSliderA,
                onSliderAChange = { raw ->
                  baselineSliderA =
                    if (baselineIsSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
                },
                sliderD = baselineSliderD,
                onSliderDChange = { raw ->
                  baselineSliderD =
                    if (baselineIsSetMode) snapVadSetValue(raw.toDouble()) else snapVadNudgeValue(raw)
                },
                noteText = baselineNoteText,
                onNoteChange = { baselineNoteText = it.take(200) },
                controlsEnabled = !panelState.isLoading && !isBaselineLockedOut,
                hasLoadedLayerValues = baselineEntry != null,
                isAllZeroNudge = isBaselineAllZeroNudge,
                isLoading = panelState.isLoading,
                unloadedSetHint = "Load the current baseline first (tap Refresh)",
                unloadedHintTag = "baseline_layer_dial_set_hint",
                applyLabel = "Apply Baseline…",
                format2 = ::formatVad2,
                onApply = {
                  if (panelState.isLoading || isBaselineLockedOut) return@VadLayerDialControls
                  if (baselineIsSetMode && baselineEntry == null) return@VadLayerDialControls
                  val vVal = Math.round(baselineSliderV.toDouble() * 100.0) / 100.0
                  val aVal = Math.round(baselineSliderA.toDouble() * 100.0) / 100.0
                  val dVal = Math.round(baselineSliderD.toDouble() * 100.0) / 100.0
                  pendingBaselineDraft =
                    PendingBaselineDraft(
                      isSet = baselineIsSetMode,
                      v = vVal,
                      a = aVal,
                      d = dVal,
                      note = baselineNoteText,
                    )
                  showBaselineTagDialog = true
                },
              )
            }
          }
        }
      }
    }
  }

  // Baseline Step 1 Dialog: "Test or Calibration?"
  if (showBaselineTagDialog && pendingBaselineDraft != null) {
    val draft = pendingBaselineDraft!!
    AlertDialog(
      onDismissRequest = {
        if (!panelState.isLoading) {
          showBaselineTagDialog = false
          pendingBaselineDraft = null
        }
      },
      containerColor = RiviaSurface1,
      title = {
        Text(
          text = "Test or Calibration?",
          style = MaterialTheme.typography.titleMedium,
          color = RiviaTextPrimary,
        )
      },
      text = {
        Text(
          text =
            "Test temporarily overrides her baseline and can be undone anytime with End test. Calibration permanently changes her baseline.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaSecondaryText,
        )
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              if (panelState.isLoading) return@OutlinedButton
              showBaselineTagDialog = false
              showBaselineCalibrationConfirmDialog = true
            },
            enabled = !panelState.isLoading,
            border = BorderStroke(1.dp, RiviaModeBlue.copy(alpha = 0.7f)),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("baseline_choice_calibration"),
          ) {
            Text("Calibration", color = RiviaModeBlue)
          }
          Button(
            onClick = {
              if (panelState.isLoading) return@Button
              showBaselineTagDialog = false
              pendingBaselineDraft = null
              val cmd =
                if (draft.isSet) {
                  MoodPostCommand.SetLayer(
                    v = draft.v,
                    a = draft.a,
                    d = draft.d,
                    layer = "baseline",
                    tag = "test",
                    note = draft.note,
                  )
                } else {
                  MoodPostCommand.NudgeLayer(
                    dv = draft.v,
                    da = draft.a,
                    dd = draft.d,
                    layer = "baseline",
                    tag = "test",
                    note = draft.note,
                  )
                }
              panelState.triggerGeneralCommand(
                owner = "2.4",
                command = cmd,
                successNotice = { clamped ->
                  if (clamped.isNotEmpty()) "Baseline test updated (hit a limit)"
                  else "Baseline test updated"
                },
                onPostSucceeded = {
                  if (!baselineIsSetMode) {
                    baselineSliderV = 0f
                    baselineSliderA = 0f
                    baselineSliderD = 0f
                  }
                  baselineNoteText = ""
                },
              )
            },
            enabled = !panelState.isLoading,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaTeal,
                contentColor = RiviaOnAccent,
              ),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("baseline_choice_test"),
          ) {
            Text("Test")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showBaselineTagDialog = false
            pendingBaselineDraft = null
          },
          enabled = !panelState.isLoading,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("baseline_choice_cancel"),
        ) {
          Text("Cancel", color = RiviaSecondaryText)
        }
      },
    )
  }

  // Baseline Step 2 Confirm Dialog for Calibration: "This permanently changes her baseline."
  if (showBaselineCalibrationConfirmDialog && pendingBaselineDraft != null) {
    val draft = pendingBaselineDraft!!
    AlertDialog(
      onDismissRequest = {
        if (!panelState.isLoading) {
          showBaselineCalibrationConfirmDialog = false
          pendingBaselineDraft = null
        }
      },
      containerColor = RiviaSurface1,
      title = {
        Text(
          text = "Confirm calibration",
          style = MaterialTheme.typography.titleMedium,
          color = RiviaTextPrimary,
        )
      },
      text = {
        Text(
          text = "This permanently changes her baseline.",
          style = MaterialTheme.typography.bodyMedium,
          color = RiviaTextPrimary,
          modifier = Modifier.testTag("baseline_calibration_warning"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (panelState.isLoading) return@Button
            showBaselineCalibrationConfirmDialog = false
            pendingBaselineDraft = null
            val cmd =
              if (draft.isSet) {
                MoodPostCommand.SetLayer(
                  v = draft.v,
                  a = draft.a,
                  d = draft.d,
                  layer = "baseline",
                  tag = "calibration",
                  note = draft.note,
                )
              } else {
                MoodPostCommand.NudgeLayer(
                  dv = draft.v,
                  da = draft.a,
                  dd = draft.d,
                  layer = "baseline",
                  tag = "calibration",
                  note = draft.note,
                )
              }
            panelState.triggerGeneralCommand(
              owner = "2.4",
              command = cmd,
              successNotice = { clamped ->
                if (clamped.isNotEmpty()) "Baseline calibrated (hit a limit)"
                else "Baseline calibrated"
              },
              onPostSucceeded = {
                if (!baselineIsSetMode) {
                  baselineSliderV = 0f
                  baselineSliderA = 0f
                  baselineSliderD = 0f
                }
                baselineNoteText = ""
              },
            )
          },
          enabled = !panelState.isLoading,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaModeBlue,
              contentColor = RiviaOnAccent,
            ),
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("baseline_calibration_confirm"),
        ) {
          Text("Confirm")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showBaselineCalibrationConfirmDialog = false
            pendingBaselineDraft = null
          },
          enabled = !panelState.isLoading,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("baseline_calibration_cancel"),
        ) {
          Text("Cancel", color = RiviaSecondaryText)
        }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodLayerSummaryRow(
  rowTitle: String,
  layerKey: String,
  entry: MoodLayerEntry?,
  isLoading: Boolean,
  format2: (Double) -> String,
  showBadges: Boolean,
  onEndTest: (() -> Unit)?,
) {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("mood_layer_row_$layerKey"),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = rowTitle,
        style = MaterialTheme.typography.labelMedium,
        color = RiviaTextPrimary,
        fontWeight = FontWeight.SemiBold,
      )
      val valueSummary =
        if (entry == null) {
          if (isLoading) "Loading…" else "Not loaded"
        } else {
          val phrase = formatMoodLayerPhrase(entry.label, entry.intensity)
          "$phrase · V ${format2(entry.v)} A ${format2(entry.a)} D ${format2(entry.d)}"
        }
      Text(
        text = valueSummary,
        style = MaterialTheme.typography.bodySmall,
        color = if (entry == null) RiviaSecondaryText else RiviaTextPrimary,
        modifier = Modifier.testTag("mood_layer_value_$layerKey"),
      )
    }

    val manual: MoodManualInfo? = if (showBadges) entry?.manual else null
    if (manual != null) {
      when {
        manual.tag.equals("test", ignoreCase = true) -> {
          val localSince = formatIsoTimestampLocal(manual.since.ifBlank { manual.at })
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(vertical = 2.dp),
            ) {
              Surface(
                color = RiviaAmber.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, RiviaAmber.copy(alpha = 0.7f)),
                modifier = Modifier.testTag("mood_layer_badge_$layerKey"),
              ) {
                Text(
                  text = "TEST",
                  color = RiviaAmber,
                  fontFamily = MonoFontFamily,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
              if (localSince.isNotEmpty()) {
                Text(
                  text = "since $localSince",
                  style = MaterialTheme.typography.labelSmall,
                  color = RiviaSecondaryText,
                  modifier = Modifier.testTag("mood_layer_since_$layerKey"),
                )
              }
            }

            if (onEndTest != null) {
              OutlinedButton(
                onClick = onEndTest,
                enabled = !isLoading,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, RiviaAmber.copy(alpha = 0.65f)),
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("mood_layer_end_test_$layerKey"),
              ) {
                Text(
                  text = "End test",
                  color = RiviaAmber,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
        manual.tag.equals("calibration", ignoreCase = true) -> {
          val localAt = formatIsoTimestampLocal(manual.at.ifBlank { manual.since })
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Surface(
              color = RiviaModeBlue.copy(alpha = 0.16f),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, RiviaModeBlue.copy(alpha = 0.7f)),
              modifier = Modifier.testTag("mood_layer_badge_$layerKey"),
            ) {
              Text(
                text = "SET BY HAND",
                color = RiviaModeBlue,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
            if (localAt.isNotEmpty()) {
              Text(
                text = localAt,
                style = MaterialTheme.typography.labelSmall,
                color = RiviaSecondaryText,
                modifier = Modifier.testTag("mood_layer_at_$layerKey"),
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VadLayerDialControls(
  tagPrefix: String,
  isSetMode: Boolean,
  onModeChange: (Boolean) -> Unit,
  sliderV: Float,
  onSliderVChange: (Float) -> Unit,
  sliderA: Float,
  onSliderAChange: (Float) -> Unit,
  sliderD: Float,
  onSliderDChange: (Float) -> Unit,
  noteText: String,
  onNoteChange: (String) -> Unit,
  controlsEnabled: Boolean,
  hasLoadedLayerValues: Boolean,
  isAllZeroNudge: Boolean,
  isLoading: Boolean,
  unloadedSetHint: String,
  unloadedHintTag: String,
  applyLabel: String,
  format2: (Double) -> String,
  onApply: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val modeOptions = listOf("Set", "Nudge")
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_mode"),
    ) {
      modeOptions.forEachIndexed { index, label ->
        val selected = if (index == 0) isSetMode else !isSetMode
        SegmentedButton(
          shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size),
          selected = selected,
          enabled = controlsEnabled,
          onClick = {
            val targetSet = (index == 0)
            if (targetSet != isSetMode) {
              onModeChange(targetSet)
            }
          },
          colors =
            SegmentedButtonDefaults.colors(
              activeContainerColor = RiviaTeal,
              activeContentColor = RiviaOnAccent,
              activeBorderColor = RiviaTeal,
              inactiveContainerColor = MaterialTheme.colorScheme.background,
              inactiveContentColor = RiviaSecondaryText,
              inactiveBorderColor = RiviaBorder,
            ),
          label = {
            Text(
              text = label,
              style = MaterialTheme.typography.labelMedium,
            )
          },
        )
      }
    }

    val sliderRange = if (isSetMode) -1f..1f else -0.5f..0.5f
    val sliderSteps = if (isSetMode) 39 else 19
    val sliderColors =
      SliderDefaults.colors(
        thumbColor = RiviaTeal,
        activeTrackColor = RiviaTeal,
        inactiveTrackColor = RiviaBorder,
        activeTickColor = RiviaBorder,
        inactiveTickColor = RiviaBorder,
      )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Valence (feel good)",
          style = MaterialTheme.typography.labelMedium,
          color = RiviaTextPrimary,
        )
        Text(
          text = format2(sliderV.toDouble()),
          style = MaterialTheme.typography.labelMedium,
          color = RiviaSecondaryText,
        )
      }
      Slider(
        value = sliderV,
        onValueChange = onSliderVChange,
        valueRange = sliderRange,
        steps = sliderSteps,
        enabled = controlsEnabled,
        colors = sliderColors,
        modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_v"),
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Arousal (wound up)",
          style = MaterialTheme.typography.labelMedium,
          color = RiviaTextPrimary,
        )
        Text(
          text = format2(sliderA.toDouble()),
          style = MaterialTheme.typography.labelMedium,
          color = RiviaSecondaryText,
        )
      }
      Slider(
        value = sliderA,
        onValueChange = onSliderAChange,
        valueRange = sliderRange,
        steps = sliderSteps,
        enabled = controlsEnabled,
        colors = sliderColors,
        modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_a"),
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Dominance (in control)",
          style = MaterialTheme.typography.labelMedium,
          color = RiviaTextPrimary,
        )
        Text(
          text = format2(sliderD.toDouble()),
          style = MaterialTheme.typography.labelMedium,
          color = RiviaSecondaryText,
        )
      }
      Slider(
        value = sliderD,
        onValueChange = onSliderDChange,
        valueRange = sliderRange,
        steps = sliderSteps,
        enabled = controlsEnabled,
        colors = sliderColors,
        modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_d"),
      )
    }

    OutlinedTextField(
      value = noteText,
      onValueChange = onNoteChange,
      singleLine = true,
      enabled = controlsEnabled,
      label = { Text("Note") },
      modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_note"),
    )

    Button(
      onClick = onApply,
      enabled = controlsEnabled && !isAllZeroNudge && !(isSetMode && !hasLoadedLayerValues),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = RiviaTeal,
          contentColor = RiviaOnAccent,
        ),
      modifier =
        Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("${tagPrefix}_apply"),
    ) {
      if (isLoading) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 2.dp,
          color = RiviaOnAccent,
        )
        Spacer(modifier = Modifier.width(8.dp))
      }
      Text(applyLabel)
    }

    if (isSetMode && !hasLoadedLayerValues) {
      Text(
        text = unloadedSetHint,
        style = MaterialTheme.typography.bodySmall,
        color = RiviaSecondaryText,
        modifier = Modifier.testTag(unloadedHintTag),
      )
    }
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
