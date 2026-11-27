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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.alpha
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
import com.example.network.LayerSwitches
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
  var vadExpanded by rememberSaveable { mutableStateOf(true) }
  var layer2Expanded by rememberSaveable { mutableStateOf(true) }
  var layer3Expanded by rememberSaveable { mutableStateOf(true) }
  var layer4Expanded by rememberSaveable { mutableStateOf(true) }
  var layer5Expanded by rememberSaveable { mutableStateOf(true) }
  var layer6Expanded by rememberSaveable { mutableStateOf(true) }
  var layer7Expanded by rememberSaveable { mutableStateOf(true) }
  var layer8Expanded by rememberSaveable { mutableStateOf(true) }
  LayerSwitchesHost(viewModel)
  val emotionPanelState = remember {
    EmotionPanelState(
      viewModel = viewModel,
      coroutineScope = coroutineScope,
    )
  }

  // Dialog & help sheet states
  var viewFileDialogLayer by remember { mutableStateOf<String?>(null) }
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
          val anyExpanded = vadExpanded || layer2Expanded || layer3Expanded || layer4Expanded || layer5Expanded || layer6Expanded ||
            layer7Expanded || layer8Expanded
          TextButton(
            onClick = {
              val nextExpanded = !anyExpanded
              vadExpanded = nextExpanded
              layer2Expanded = nextExpanded
              layer3Expanded = nextExpanded
              layer4Expanded = nextExpanded
              layer5Expanded = nextExpanded
              layer6Expanded = nextExpanded
              layer7Expanded = nextExpanded
              layer8Expanded = nextExpanded
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
        item {
          AppraisalLayerCard(
            expanded = layer3Expanded,
            onToggleExpanded = { layer3Expanded = !layer3Expanded },
            viewModel = viewModel,
          )
        }
        item {
          BodyHealthLayerCard(
            expanded = layer4Expanded,
            onToggleExpanded = { layer4Expanded = !layer4Expanded },
          )
        }
        item {
          ProgressTrendLayerCard(
            expanded = layer5Expanded,
            onToggleExpanded = { layer5Expanded = !layer5Expanded },
            viewModel = viewModel,
          )
        }
        item {
          TrustLayerCard(
            expanded = layer6Expanded,
            onToggleExpanded = { layer6Expanded = !layer6Expanded },
            viewModel = viewModel,
          )
        }
        item {
          ReadingLayerCard(
            expanded = layer7Expanded,
            onToggleExpanded = { layer7Expanded = !layer7Expanded },
            viewModel = viewModel,
          )
        }
        item {
          HowSheSoundsLayerCard(
            expanded = layer8Expanded,
            onToggleExpanded = { layer8Expanded = !layer8Expanded },
            viewModel = viewModel,
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
      description = "A cheat sheet of what angry, happy, sad and distressed look like as numbers.",
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
          LayerSwitchLine(1)
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
            if (step.testTag == "vad_step_1_5" && LayerSwitches.phoneOn(LayerSwitches.PHONE_DIAL)) {
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
                  PieceSwitches(step.testTag)
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
          LayerSwitchLine(2)
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
            if (step.testTag == "vad_step_2_4" && LayerSwitches.phoneOn(LayerSwitches.PHONE_CONTROLS)) {
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
                  PieceSwitches(step.testTag)
                }
              }
            }
          }
        }
      }
    }
  }
}

private val BODY_HEALTH_SUB_STEPS =
  listOf(
    VadSubStepSpec(
      testTag = "vad_step_4_1",
      title = "4.1 Health signals",
      description =
        "The things she can feel about herself, like errors, slow replies, battery and whether the tunnel is up.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_2",
      title = "4.2 Collector",
      description =
        "Reads those signals once a minute and saves them. Ignores errors from while it was switched off.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_3",
      title = "4.3 Her own normal",
      description =
        "Learns what normal looks like over a 15-minute window, with a floor per signal. A slow slide that makes her run worse never counts as normal.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_4",
      title = "4.4 Stress dial",
      description =
        "Rises when things go wrong. She can feel annoyed, or worried when things run slow and she doesn't know why. One problem only counts once.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_5",
      title = "4.5 Confidence dial",
      description =
        "Drops for errors she causes, like a bad tool setting. Small errors make her more careful, not annoyed.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_6",
      title = "4.6 Caps and moods",
      description =
        "Limits how far the dials can move. Irritable means drier, more focused humor, never silly. She can be distressed or annoyed, never scared.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_7",
      title = "4.7 Never push harder",
      description =
        "Stress never makes her take bigger risks. Read-only checks keep running; anything that changes things asks you first and repeats your words back.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_8",
      title = "4.8 Health check tool",
      description =
        "A read-only health_status tool, so you can ask her how things are running. It stays internal.",
    ),
    VadSubStepSpec(
      testTag = "vad_step_4_9",
      title = "4.9 Shadow mode",
      description =
        "Everything is logged without changing her replies yet, so the dials can be checked before they go live.",
    ),
  )

@Composable
private fun BodyHealthLayerCard(
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
) {
  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_body_health"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .testTag("layer_header_body_health"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 4: Body & Health",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          LayerSwitchLine(4)
          Text(
            text =
              "How she feels about how she's running: the tablet tracks its own health, learns what normal looks like, and turns problems into stress and confidence dials. Shadow mode: the dials are logged but don't change her replies yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
            modifier = Modifier.testTag("body_health_description"),
          )
        }

        IconButton(
          onClick = onToggleExpanded,
          modifier = Modifier.testTag("toggle_layer_body_health"),
        ) {
          Icon(
            imageVector =
              if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription =
              if (expanded) "Collapse Layer 4: Body & Health"
              else "Expand Layer 4: Body & Health",
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
          modifier = Modifier.fillMaxWidth().testTag("body_health_sublayers_container"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (step in BODY_HEALTH_SUB_STEPS) {
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
                PieceSwitches(step.testTag)
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

  // Shared single-flight guard across every button, slider, switch, and dialog in 1.5 and 2.4
  var isLoading by mutableStateOf(false)

  // Per-instance first-load flags (reset when EmotionPanelState recreates on rotation so open pills reload)
  var hasLoaded15Once by mutableStateOf(false)
  var hasLoaded24Once by mutableStateOf(false)

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
  var seenLoadVersion by remember(panelState) { mutableIntStateOf(panelState.loadVersion) }

  // Existing Emotion dial state (sends no layer and no tag)
  var isSetMode by rememberSaveable { mutableStateOf(false) } // default is Nudge
  var sliderV by rememberSaveable { mutableFloatStateOf(0f) }
  var sliderA by rememberSaveable { mutableFloatStateOf(0f) }
  var sliderD by rememberSaveable { mutableFloatStateOf(0f) }
  var noteText by rememberSaveable { mutableStateOf("") }

  // Reload automatically if 1.5 reopens expanded after a screen rotation / configuration recreation
  LaunchedEffect(pillExpanded, panelState) {
    if (pillExpanded && !panelState.hasLoaded15Once) {
      panelState.hasLoaded15Once = true
      panelState.triggerLoad(owner = "1.5")
    }
  }

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
              if (nextExpanded && !panelState.hasLoaded15Once) {
                panelState.hasLoaded15Once = true
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
      PieceSwitches(step.testTag)

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
  var pillExpanded by rememberSaveable { mutableStateOf(false) }
  var seenLoadVersion by remember(panelState) { mutableIntStateOf(panelState.loadVersion) }

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

  // Reload automatically if 2.4 reopens expanded after a screen rotation / configuration recreation
  LaunchedEffect(pillExpanded, panelState) {
    if (pillExpanded && !panelState.hasLoaded24Once) {
      panelState.hasLoaded24Once = true
      panelState.triggerLoad(owner = "2.4")
    }
  }

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
              if (nextExpanded && !panelState.hasLoaded24Once) {
                panelState.hasLoaded24Once = true
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
      PieceSwitches(step.testTag)

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

          // Lock / Unlock toggle switch above Mood & Baseline
          val isUnlocked = !panelState.isServerLocked
          Surface(
            color = RiviaPanelSurface,
            shape = RoundedCornerShape(10.dp),
            border =
              BorderStroke(
                1.dp,
                if (isUnlocked) RiviaAmber.copy(alpha = 0.65f) else RiviaBorder,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .testTag(if (isUnlocked) "mood_dial_padlock_unlocked" else "mood_dial_padlock"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                  contentDescription = if (isUnlocked) "Unlocked" else "Locked",
                  tint = if (isUnlocked) RiviaAmber else RiviaSecondaryText,
                  modifier = Modifier.size(18.dp),
                )
                Column {
                  Text(
                    text = if (isUnlocked) "Unlocked" else "Mood & Baseline locked",
                    style = MaterialTheme.typography.labelLarge,
                    color = RiviaTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                  )
                  if (isUnlocked) {
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
                  } else {
                    Text(
                      text = "Toggle switch to unlock",
                      style = MaterialTheme.typography.bodySmall,
                      color = RiviaSecondaryText,
                    )
                  }
                }
              }

              Switch(
                checked = isUnlocked,
                onCheckedChange = { shouldUnlock ->
                  if (!panelState.isLoading) {
                    if (shouldUnlock && panelState.isServerLocked) {
                      panelState.triggerGeneralCommand(
                        owner = "2.4",
                        command = MoodPostCommand.Unlock,
                        successNotice = { "Mood & baseline unlocked" },
                      )
                    } else if (!shouldUnlock && !panelState.isServerLocked) {
                      panelState.triggerGeneralCommand(
                        owner = "2.4",
                        command = MoodPostCommand.Lock,
                        successNotice = { "Mood & baseline locked" },
                      )
                    }
                  }
                },
                enabled = !panelState.isLoading,
                colors = riviaSwitchColors(),
                modifier = Modifier.testTag("mood_dial_lock_switch"),
              )
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
