package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.EmotionModelState
import com.example.audio.SileroVadModelState
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
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
import com.example.viewmodel.StepTestVerificationResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class StepTestCaseSpec(
  val id: String,
  val stepBadge: String,
  val title: String,
  val exactSettingsSummary: String,
  val instructions: List<String>,
  val recordAlongsideRecognizer: Boolean = true,
  val micTestMode: String = "B",
  val saveWavClips: Boolean = false,
  val vadStartThreshold: Float = 0.50f,
  val vadStartDurationMs: Int = 250,
  val vadEndThreshold: Float = 0.35f,
  val vadEndSilenceMs: Int = 700,
  val supportsLiveVoiceLaunch: Boolean = true,
  val liveLaunchButtonLabel: String = "Apply Exact Settings & Run in Live Mode",
  val verifyButtonLabel: String = "Apply Exact Settings & Run Check",
)

val STEP_0_AND_1_TEST_SPECS: List<StepTestCaseSpec> =
  listOf(
    StepTestCaseSpec(
      id = "S0_T1",
      stepBadge = "STEP 0 · TEST 1",
      title = "History Retention (No Pruning on Logs, Keep Last 10 WAVs)",
      exactSettingsSummary =
        "Chat: Rivia (non-Lab) · Record alongside recognizer: ON · Mode: B · Save clips: OFF (default; keeps last 10 WAVs when ON)",
      instructions =
        listOf(
          "1. Tap \"Apply Exact Settings & Run Check\" below to set Record = ON, Mode = B, Save clips = OFF and verify that logVoiceEmotionAndPrune() and logRequestAndPrune() keep every row in Room.",
          "2. Check the verification output: UI display queries cap at 50 turns (\"Latest 50 of N turns\") and 100 requests (\"Latest 100 of N requests\"), while Export JSON, Export CSV, and Full Backup read all N rows via ...ForExport() queries.",
          "3. Optional: Tap \"Apply Exact Settings & Run in Live Mode\" to record a new Rivia live turn and confirm total turns (N) increases without pruning older rows.",
        ),
      recordAlongsideRecognizer = true,
      micTestMode = "B",
      saveWavClips = false,
      supportsLiveVoiceLaunch = true,
      liveLaunchButtonLabel = "Apply Settings & Run Live Turn",
      verifyButtonLabel = "Apply Exact Settings & Verify Counts",
    ),
    StepTestCaseSpec(
      id = "S0_T2",
      stepBadge = "STEP 0 · TEST 2",
      title = "Rolling Emotion Baseline (14-Day Window, 500-Row Cap & Excluded Counts)",
      exactSettingsSummary =
        "Chat: Rivia · Mode: B · Speech >= 1.5s · Not silent · A/D/V not null · Window: BASELINE_WINDOW_DAYS = 14, BASELINE_MAX_ROWS = 500 · VAD: 0.50 / 250ms / 0.35 / 700ms",
      instructions =
        listOf(
          "1. Tap \"Apply Exact Settings & Verify Baseline\" to apply standard Mode B settings and verify that both the Lab baseline summary and exported emotionBaseline JSON block match on windowDays=14, windowStart, windowEnd, n, and excluded counts (Mode A, short <1.5s, silent).",
          "2. Eligible Turn Test (>= 1.5s speech): Tap \"Apply Settings & Run Live Turn\", speak continuously for 2–4 seconds, then return here and re-run the check to confirm eligible count (n) increased by 1.",
          "3. Short-Clip Exclusion Test (< 1.5s speech): Tap \"Apply Settings & Run Live Turn\", say a single short word (e.g. \"Hello\"), then return here to confirm excludedShortCount increased by 1 while n stayed unchanged.",
        ),
      recordAlongsideRecognizer = true,
      micTestMode = "B",
      saveWavClips = false,
      supportsLiveVoiceLaunch = true,
      liveLaunchButtonLabel = "Apply Settings & Run Live Turn",
      verifyButtonLabel = "Apply Exact Settings & Verify Baseline",
    ),
    StepTestCaseSpec(
      id = "S0_T3",
      stepBadge = "STEP 0 · TEST 3",
      title = "Non-Destructive Backup Import & Natural-Key Deduplication",
      exactSettingsSummary =
        "UUID/Keyed tables: OnConflictStrategy.IGNORE · Log tables (voice_emotion_logs, request_logs, rule_events, turn_dispatch_logs): Natural-key deduplication in single @Transaction",
      instructions =
        listOf(
          "1. Tap \"Run Duplicate Backup Import Check\" below: this exports your current live database to backup JSON in memory and runs importBackupInTransaction() twice in a row.",
          "2. Confirm that row counts across chats, messages, voice_emotion_logs, request_logs, rule_events, and turn_dispatch_logs remain 100% unchanged (0 duplicates created, and 0 newer messages deleted by CASCADE).",
          "3. Even log rows with a null/blank requestId are deduplicated by timestamp + model (request_logs), timestamp + mode (voice_emotion_logs), and timestamp + ruleId + eventType (rule_events).",
        ),
      recordAlongsideRecognizer = true,
      micTestMode = "B",
      saveWavClips = false,
      supportsLiveVoiceLaunch = false,
      verifyButtonLabel = "Run Duplicate Backup Import Check",
    ),
    StepTestCaseSpec(
      id = "S1_T1",
      stepBadge = "STEP 1 · TEST 1 (FIXES 3 & 4)",
      title = "Edge Silence Trimming: 200ms Pre-Roll, Mid-Turn Pause & 200ms Post-Roll",
      exactSettingsSummary =
        "Chat: Rivia · Mode: B · Record: ON · Save clips: ON · VAD: 0.50 / 250ms / 0.35 / 700ms · Pre-roll: 200ms (3,200 samples) · Post-roll: 200ms (3,200 samples) of 700ms trailing silence · WAV saved outside 300ms barrier",
      instructions =
        listOf(
          "1. Tap \"Apply Exact Settings & Run in Live Mode (Save Clips ON)\" to enable Save clips = ON, set Mode B and VAD defaults (0.50 / 250ms / 0.35 / 700ms), and launch Live Mode.",
          "2. When Listening starts, stay silent for ~1.5 seconds (leading silence), speak a sentence with a brief ~0.3s pause in the middle (e.g. \"Testing edge silence... with a mid-turn pause\"), then wait ~1s until the turn ends.",
          "3. Return here and tap \"Play Latest WAV\" on the top telemetry card: verify the 1.5s leading silence was dropped (keeping only a 200ms pre-roll), the mid-sentence pause was preserved, and the final word ending has a clean 200ms post-roll (dropping the remaining ~500ms of trailing silence).",
          "4. Tap \"Apply Settings & Run Trimmer Check\" to also run the deterministic sample-accurate verification of EdgeSilenceTrimmer on-device.",
        ),
      recordAlongsideRecognizer = true,
      micTestMode = "B",
      saveWavClips = true,
      vadStartThreshold = 0.50f,
      vadStartDurationMs = 250,
      vadEndThreshold = 0.35f,
      vadEndSilenceMs = 700,
      supportsLiveVoiceLaunch = true,
      liveLaunchButtonLabel = "Apply Exact Settings & Run in Live Mode (Save Clips ON)",
      verifyButtonLabel = "Apply Settings & Run Trimmer Check",
    ),
    StepTestCaseSpec(
      id = "S1_T2",
      stepBadge = "STEP 1 · TEST 2 (FIX 2)",
      title = "Recognizer-Triggered Speech Start with 1.5s Ring Buffer",
      exactSettingsSummary =
        "Chat: Rivia · Mode: B · Save clips: ON · Ring Buffer: 1.5s (24,000 samples) · Starts at earliest buffered chunk with prob >= 0.35 minus 200ms pre-roll (or full 1.5s buffer if none >= 0.35) · Live Stress VAD: startProb=0.75, startMs=600ms",
      instructions =
        listOf(
          "1. Tap \"Run 1.5s Ring Buffer Check\" to verify both Fix 2 paths on-device: (a) when notifyRecognizerHeardSpeech() triggers speech start, the clip starts at the earliest chunk in the 1.5s buffer with prob >= 0.35 minus 200ms pre-roll, and (b) if no chunk had prob >= 0.35, the entire 1.5s (24,000-sample) buffer is kept.",
          "2. To test live with your microphone: tap \"Apply Recognizer-Start Stress Settings & Run Live\" (sets vadStartThreshold=0.75 and vadStartDurationMs=600ms with Save clips=ON so SpeechRecognizer confirms speech before VAD's onset timer).",
          "3. Speak a sentence starting softly, then return here and play back the latest WAV clip to confirm your first word wasn't clipped. (Tap any other test's Apply button afterward to restore VAD 0.50 / 250ms.)",
        ),
      recordAlongsideRecognizer = true,
      micTestMode = "B",
      saveWavClips = true,
      vadStartThreshold = 0.75f,
      vadStartDurationMs = 600,
      vadEndThreshold = 0.35f,
      vadEndSilenceMs = 700,
      supportsLiveVoiceLaunch = true,
      liveLaunchButtonLabel = "Apply Recognizer-Start Stress Settings & Run Live",
      verifyButtonLabel = "Run 1.5s Ring Buffer Check",
    ),
    StepTestCaseSpec(
      id = "S1_T3",
      stepBadge = "STEP 1 · TEST 3",
      title = "4-Second Sliding Window Scoring & Remainder Averaging",
      exactSettingsSummary =
        "Chat: Rivia (Lab chats skipped) · Mode: B · Record: ON · Save clips: OFF · VAD: 0.50 / 250ms / 0.35 / 700ms · 4.0s (64,000 samples) background windows + remainder >= 1.5s (24,000 samples), averaged A/D/V",
      instructions =
        listOf(
          "1. Tap \"Apply Exact Settings & Run Long Turn in Live Mode\" to set standard Mode B settings (Save clips = OFF, VAD 0.50 / 250ms / 0.35 / 700ms) and launch Live Mode in a Rivia chat.",
          "2. Speak continuously for 6 to 10 seconds (2–3 full sentences without pausing longer than 0.5s).",
          "3. Return here and inspect the \"Latest Live Voice Turn\" card at the top: confirm 4s Windows (scoredWindowsCount) is 2 (for ~5.5–8s speech) or 3 (for ~9.5–12s speech) and A/D/V shows the averaged emotion scores.",
          "4. Tap \"Apply Settings & Run 4s Window Check\" to verify 10.0s -> 3 windows, 8.5s -> 2 windows, and A/D/V averaging.",
        ),
      recordAlongsideRecognizer = true,
      micTestMode = "B",
      saveWavClips = false,
      vadStartThreshold = 0.50f,
      vadStartDurationMs = 250,
      vadEndThreshold = 0.35f,
      vadEndSilenceMs = 700,
      supportsLiveVoiceLaunch = true,
      liveLaunchButtonLabel = "Apply Exact Settings & Run Long Turn in Live Mode",
      verifyButtonLabel = "Apply Settings & Run 4s Window Check",
    ),
    StepTestCaseSpec(
      id = "S1_T4",
      stepBadge = "STEP 1 · TEST 4 (FIX 1)",
      title = "300ms Emotion Sync Barrier & Late Score Recovery (emotion_late)",
      exactSettingsSummary =
        "Chat: Rivia · Mode: B · Record: ON · Barrier Timeout: 300ms before Gemini request · On timeout: reply proceeds immediately (emotion_timeout) while background scoring writes A/D/V to voice_emotion_logs marked emotion_late (<totalMs>ms)",
      instructions =
        listOf(
          "1. Tap \"Apply Settings & Run 300ms Barrier Check\" to execute both the fast path (< 300ms -> emotion_ok) and the slow timeout path (> 300ms -> barrier releases reply at ~300ms with emotion_timeout, then background completion updates A/D/V with emotion_late and totalMs).",
          "2. Tap \"Apply Exact Settings & Run in Live Mode\" to speak a live turn on your phone, then inspect \"Latest Live Voice Turn\" at the top of this screen:",
          "   • If scoring finished within 300ms: Callbacks shows emotion_ok(<waitMs>ms) and A/D/V was ready before Gemini started.",
          "   • If scoring took > 300ms: Gemini started immediately at ~300ms, and voice_emotion_logs was updated with A/D/V and marked \"OK · emotion_late (<totalMs>ms)\".",
        ),
      recordAlongsideRecognizer = true,
      micTestMode = "B",
      saveWavClips = false,
      vadStartThreshold = 0.50f,
      vadStartDurationMs = 250,
      vadEndThreshold = 0.35f,
      vadEndSilenceMs = 700,
      supportsLiveVoiceLaunch = true,
      liveLaunchButtonLabel = "Apply Exact Settings & Run in Live Mode",
      verifyButtonLabel = "Apply Settings & Run 300ms Barrier Check",
    ),
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestsScreen(
  viewModel: RiviaViewModel,
  onOpenLiveMode: () -> Unit,
  onOpenLabSettings: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  val isActiveChatLab by viewModel.isActiveChatLab.collectAsStateWithLifecycle()
  val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
  val labMicPrefs by viewModel.labMicTestPreferences.collectAsStateWithLifecycle()
  val emotionModelState by viewModel.emotionModelState.collectAsStateWithLifecycle()
  val vadModelState by viewModel.sileroVadModelState.collectAsStateWithLifecycle()
  val emotionLogs by viewModel.voiceEmotionLogs.collectAsStateWithLifecycle()
  val totalEmotionCount by viewModel.totalVoiceEmotionLogsCount.collectAsStateWithLifecycle()
  val totalRequestCount by viewModel.totalRequestLogsCount.collectAsStateWithLifecycle()
  val baselineSummary by viewModel.emotionBaselineSummary.collectAsStateWithLifecycle()
  val turnDispatchLogs by viewModel.turnDispatchLogs.collectAsStateWithLifecycle()
  val testResults by viewModel.stepTestResults.collectAsStateWithLifecycle()
  val runningTestId by viewModel.runningStepTestId.collectAsStateWithLifecycle()
  val isSpeaking by viewModel.audioPlayerManager.isSpeaking.collectAsStateWithLifecycle()
  val speakingMsgId by viewModel.audioPlayerManager.speakingMessageId.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
  val isErrorBanner by viewModel.isErrorBanner.collectAsStateWithLifecycle()

  val scope = rememberCoroutineScope()
  val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

  val latestTurnLog = emotionLogs.firstOrNull()
  val latestDispatchLog = turnDispatchLogs.firstOrNull()

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    // Top bar
    Surface(color = RiviaBackground, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("tests_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to chat",
              tint = RiviaText,
            )
          }
          Column {
            Text(
              text = "Step 0 & Step 1 Tests",
              style = MaterialTheme.typography.titleMedium,
              color = RiviaText,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Instructions, exact settings & 1-tap test runners",
              style = MaterialTheme.typography.labelSmall,
              color = RiviaMutedText,
            )
          }
        }

        Button(
          onClick = {
            scope.launch {
              for (spec in STEP_0_AND_1_TEST_SPECS) {
                viewModel.runStepTestVerification(spec.id)
                delay(120L)
                while (viewModel.runningStepTestId.value != null) {
                  delay(60L)
                }
              }
              // Restore standard Mode B settings (Save clips OFF, VAD 0.50/250/0.35/700) after batch run
              viewModel.applyExactStepTestSettings(
                testTitle = "Standard Step 0 & 1 Defaults",
                recordAlongsideRecognizer = true,
                micTestMode = "B",
                saveWavClips = false,
                vadStartThreshold = 0.50f,
                vadStartDurationMs = 250,
                vadEndThreshold = 0.35f,
                vadEndSilenceMs = 700,
              )
            }
          },
          enabled = runningTestId == null,
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaMint,
              contentColor = RiviaBackground,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .padding(end = 6.dp)
              .testTag("run_all_step_tests_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Science,
            contentDescription = "Run all checks",
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Run All 7 Checks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
      }
    }

    HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))

    // Status banner if active
    if (!statusBanner.isNullOrBlank()) {
      Surface(
        color = RiviaSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
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
            StatusShape(
              status = if (isErrorBanner) StatusType.ERROR else StatusType.OK,
              size = 10.dp,
            )
            Text(
              text = statusBanner!!,
              style = MaterialTheme.typography.bodySmall,
              color = if (isErrorBanner) RiviaErrorRed else RiviaText,
            )
          }
          TextButton(onClick = { viewModel.clearStatusBanner() }) {
            Text("Dismiss", color = RiviaMutedText, fontSize = 12.sp)
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Top Card: Live Settings Snapshot & Latest Voice Turn Telemetry
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = RiviaSurface,
          border = BorderStroke(1.dp, RiviaMint.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "ACTIVE TEST ENVIRONMENT & LATEST TURN",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChivoMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = RiviaMint,
              )
              OutlinedButton(
                onClick = onOpenLabSettings,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiviaBorder),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Settings,
                  contentDescription = "Open Settings & Logs",
                  tint = RiviaMint,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lab & Logs", color = RiviaText, fontSize = 11.sp)
              }
            }

            // Current Settings Readout
            val chatModeLabel =
              if (isActiveChatLab) "LAB CHAT (Emotion pipeline skipped!)"
              else "RIVIA CHAT (${activeChat?.title ?: "New Rivia chat"})"
            Text(
              text =
                "Chat: $chatModeLabel\n" +
                  "Mic: Mode ${labMicPrefs.micTestMode} · Record=${if (labMicPrefs.recordAlongsideRecognizer) "ON" else "OFF"} · Save clips=${if (labMicPrefs.saveWavClips) "ON" else "OFF"}\n" +
                  "Silero VAD: start >= ${"%.2f".format(Locale.US, labMicPrefs.vadStartThreshold)} for ${labMicPrefs.vadStartDurationMs}ms · end < ${"%.2f".format(Locale.US, labMicPrefs.vadEndThreshold)} for ${labMicPrefs.vadEndSilenceMs}ms",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = ChivoMonoFontFamily,
              color = if (isActiveChatLab) RiviaWarningAmber else RiviaText,
            )

            val vadReady = vadModelState is SileroVadModelState.Ready
            val emotionReady = emotionModelState is EmotionModelState.Ready
            Text(
              text =
                "Models: Silero VAD=${if (vadReady) "Ready" else vadModelState.javaClass.simpleName} · audEERING Emotion=${if (emotionReady) "Ready" else emotionModelState.javaClass.simpleName}\n" +
                  "DB Totals: $totalEmotionCount voice turns · $totalRequestCount requests · Baseline n=${baselineSummary.eligibleCount} (${baselineSummary.windowDays}d window)",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaMutedText,
            )

            if (isActiveChatLab) {
              OutlinedButton(
                onClick = { viewModel.startNewRiviaChat() },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiviaWarningAmber),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
              ) {
                Text(
                  text = "Switch to Rivia Chat (Required for Rivia Live Pipeline)",
                  color = RiviaWarningAmber,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }

            HorizontalDivider(color = RiviaBorder.copy(alpha = 0.5f))

            if (latestTurnLog == null) {
              Text(
                text = "Latest Live Voice Turn: None recorded yet. Tap any test's \"Apply Exact Settings & Run in Live Mode\" button below to speak a test turn.",
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
              )
            } else {
              val wavExists =
                remember(latestTurnLog.wavFilePath, emotionLogs) {
                  !latestTurnLog.wavFilePath.isNullOrBlank() &&
                    File(latestTurnLog.wavFilePath).exists()
                }
              val isPlayingWav =
                isSpeaking &&
                  !latestTurnLog.wavFilePath.isNullOrBlank() &&
                  speakingMsgId == "wav:${latestTurnLog.wavFilePath}"

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text =
                      "Latest Turn: ${latestTurnLog.requestId} (${timeFormat.format(Date(latestTurnLog.timestamp))}) · ${latestTurnLog.captureStatus}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChivoMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = RiviaMint,
                  )
                  val advLabel =
                    if (
                      latestTurnLog.arousal != null &&
                        latestTurnLog.dominance != null &&
                        latestTurnLog.valence != null
                    ) {
                      "A=${"%.2f".format(Locale.US, latestTurnLog.arousal)} D=${"%.2f".format(Locale.US, latestTurnLog.dominance)} V=${"%.2f".format(Locale.US, latestTurnLog.valence)}"
                    } else {
                      "A/D/V=null"
                    }
                  val rateLabel =
                    latestTurnLog.speakingRateWps?.let { "${"%.2f".format(Locale.US, it)} w/s" }
                      ?: "—"
                  Text(
                    text =
                      "Clip=${"%.2f".format(Locale.US, latestTurnLog.clipLengthSec)}s · Speech=${"%.2f".format(Locale.US, latestTurnLog.speechDurationSec)}s · Rate=$rateLabel · 4s Windows=${latestTurnLog.scoredWindowsCount}\n" +
                        "$advLabel · Callbacks: ${latestTurnLog.callbacksFired.ifBlank { "none" }}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChivoMonoFontFamily,
                    color = RiviaText,
                  )
                  if (latestDispatchLog != null) {
                    Text(
                      text =
                        "Dispatch Log (${latestDispatchLog.requestId}): status=${latestDispatchLog.status} · timings=${latestDispatchLog.timingsJson}",
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = ChivoMonoFontFamily,
                      color = RiviaMutedText,
                    )
                  }
                }

                if (wavExists) {
                  Spacer(modifier = Modifier.width(8.dp))
                  OutlinedButton(
                    onClick = {
                      if (!latestTurnLog.wavFilePath.isNullOrBlank()) {
                        viewModel.audioPlayerManager.playLocalWavFile(latestTurnLog.wavFilePath)
                      }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RiviaMint),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier =
                      Modifier.minimumInteractiveComponentSize()
                        .testTag("play_latest_test_wav_button"),
                  ) {
                    Icon(
                      imageVector = if (isPlayingWav) Icons.Default.Stop else Icons.Default.PlayArrow,
                      contentDescription = "Play or stop latest WAV",
                      tint = RiviaMint,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = if (isPlayingWav) "Stop WAV" else "Play WAV",
                      color = RiviaMint,
                      fontFamily = ChivoMonoFontFamily,
                      fontSize = 11.sp,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Section Header: STEP 0
      item {
        Text(
          text = "STEP 0 TESTS — HISTORY RETENTION, 14D BASELINE & BACKUP DEDUP",
          style = MaterialTheme.typography.labelLarge,
          fontFamily = ChivoMonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = RiviaMint,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      items(
        STEP_0_AND_1_TEST_SPECS.filter { it.id.startsWith("S0_") },
        key = { it.id },
      ) { spec ->
        StepTestCard(
          spec = spec,
          verificationResult = testResults[spec.id],
          isRunning = runningTestId == spec.id,
          onRunVerification = { viewModel.runStepTestVerification(spec.id) },
          onApplyAndLaunchLive = {
            viewModel.applyExactStepTestSettings(
              testTitle = spec.title,
              recordAlongsideRecognizer = spec.recordAlongsideRecognizer,
              micTestMode = spec.micTestMode,
              saveWavClips = spec.saveWavClips,
              vadStartThreshold = spec.vadStartThreshold,
              vadStartDurationMs = spec.vadStartDurationMs,
              vadEndThreshold = spec.vadEndThreshold,
              vadEndSilenceMs = spec.vadEndSilenceMs,
              ensureRiviaChat = true,
              onReady = onOpenLiveMode,
            )
          },
        )
      }

      // Section Header: STEP 1
      item {
        Text(
          text = "STEP 1 TESTS — SILENCE TRIMMING, 1.5S RING BUFFER, 4S WINDOWS & 300MS BARRIER",
          style = MaterialTheme.typography.labelLarge,
          fontFamily = ChivoMonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = RiviaMint,
          modifier = Modifier.padding(top = 8.dp),
        )
      }

      items(
        STEP_0_AND_1_TEST_SPECS.filter { it.id.startsWith("S1_") },
        key = { it.id },
      ) { spec ->
        StepTestCard(
          spec = spec,
          verificationResult = testResults[spec.id],
          isRunning = runningTestId == spec.id,
          onRunVerification = { viewModel.runStepTestVerification(spec.id) },
          onApplyAndLaunchLive = {
            viewModel.applyExactStepTestSettings(
              testTitle = spec.title,
              recordAlongsideRecognizer = spec.recordAlongsideRecognizer,
              micTestMode = spec.micTestMode,
              saveWavClips = spec.saveWavClips,
              vadStartThreshold = spec.vadStartThreshold,
              vadStartDurationMs = spec.vadStartDurationMs,
              vadEndThreshold = spec.vadEndThreshold,
              vadEndSilenceMs = spec.vadEndSilenceMs,
              ensureRiviaChat = true,
              onReady = onOpenLiveMode,
            )
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StepTestCard(
  spec: StepTestCaseSpec,
  verificationResult: StepTestVerificationResult?,
  isRunning: Boolean,
  onRunVerification: () -> Unit,
  onApplyAndLaunchLive: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = RiviaSurface,
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth().testTag("test_card_${spec.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Badge + Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = RiviaMint.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, RiviaMint.copy(alpha = 0.55f)),
        ) {
          Text(
            text = spec.stepBadge,
            fontFamily = ChivoMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = RiviaMint,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }

        if (verificationResult != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            StatusShape(
              status = if (verificationResult.passed) StatusType.OK else StatusType.ERROR,
              size = 10.dp,
            )
            Text(
              text = if (verificationResult.passed) "PASSED" else "FAILED",
              fontFamily = ChivoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = if (verificationResult.passed) RiviaMint else RiviaErrorRed,
              fontSize = 11.sp,
              modifier = Modifier.testTag("step_test_badge_${spec.id}"),
            )
          }
        }
      }

      Text(
        text = spec.title,
        style = MaterialTheme.typography.titleMedium,
        color = RiviaText,
        fontWeight = FontWeight.SemiBold,
      )

      // Exact Settings Box
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = RiviaRaisedSurface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = "EXACT SETTINGS FOR THIS TEST",
            fontFamily = ChivoMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = RiviaMint,
            fontSize = 10.sp,
          )
          Text(
            text = spec.exactSettingsSummary,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaText,
            fontSize = 11.sp,
          )
        }
      }

      // Instructions list
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
          text = "HOW TO TEST ON YOUR PHONE",
          fontFamily = ChivoMonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = RiviaMutedText,
          fontSize = 10.sp,
        )
        spec.instructions.forEach { stepText ->
          Text(
            text = stepText,
            style = MaterialTheme.typography.bodySmall,
            color = RiviaText,
          )
        }
      }

      // Verification Output Box (if run)
      if (verificationResult != null) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color =
            if (verificationResult.passed) RiviaMint.copy(alpha = 0.10f)
            else RiviaErrorRed.copy(alpha = 0.12f),
          border =
            BorderStroke(
              1.dp,
              if (verificationResult.passed) RiviaMint.copy(alpha = 0.5f)
              else RiviaErrorRed.copy(alpha = 0.6f),
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = verificationResult.headline,
              fontFamily = ChivoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = if (verificationResult.passed) RiviaMint else RiviaErrorRed,
              fontSize = 11.sp,
            )
            if (verificationResult.details.isNotBlank()) {
              Text(
                text = verificationResult.details,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaText,
                fontSize = 11.sp,
              )
            }
          }
        }
      }

      // Action Buttons
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Button(
          onClick = onRunVerification,
          enabled = !isRunning,
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = RiviaMint,
              contentColor = RiviaBackground,
            ),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("verify_button_${spec.id}"),
        ) {
          Icon(
            imageVector =
              if (verificationResult?.passed == true) Icons.Outlined.CheckCircle
              else Icons.Outlined.Tune,
            contentDescription = spec.verifyButtonLabel,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isRunning) "Running..." else spec.verifyButtonLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }

        if (spec.supportsLiveVoiceLaunch) {
          OutlinedButton(
            onClick = onApplyAndLaunchLive,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RiviaMint.copy(alpha = 0.7f)),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("launch_live_button_${spec.id}"),
          ) {
            Icon(
              imageVector = Icons.Default.GraphicEq,
              contentDescription = spec.liveLaunchButtonLabel,
              tint = RiviaMint,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = spec.liveLaunchButtonLabel,
              color = RiviaMint,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}
