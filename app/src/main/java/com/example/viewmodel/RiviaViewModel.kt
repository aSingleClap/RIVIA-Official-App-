package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.audio.EmotionModelManager
import com.example.audio.EmotionModelState
import com.example.audio.MicCaptureResult
import com.example.audio.MicSharingRecorder
import com.example.audio.SileroVadManager
import com.example.audio.SileroVadModelState
import com.example.data.BackupImportPayload
import com.example.data.BaselineExcludedCounts
import com.example.data.CachedModelEntity
import com.example.data.ChatEntity
import com.example.data.DefaultSettingsEntity
import com.example.data.LayerDraftEntity
import com.example.data.LayerLockEntity
import com.example.data.LayerSettingsEntity
import com.example.data.LayerTestRunEntity
import com.example.data.MessageEntity
import com.example.data.RequestLogEntity
import com.example.data.RiviaDao
import com.example.data.RiviaDatabase
import com.example.data.RuleEventEntity
import com.example.data.TurnDispatchLogEntity
import com.example.data.VoiceEmotionLogEntity
import com.example.model.EngineLayerConfig
import com.example.model.HardRulesLayerConfig
import com.example.model.LayerCardUiMeta
import com.example.model.LayerConfigManager
import com.example.model.LayerStatus
import com.example.model.LoadedLayersBundle
import com.example.model.ModePreset
import com.example.model.PersonaLayerConfig
import com.example.model.ProfileTestState
import com.example.model.RunSettings
import com.example.model.StyleLayerConfig
import com.example.network.ElevenLabsClient
import com.example.network.ElevenLabsModel
import com.example.network.ElevenLabsVoice
import com.example.network.GeminiInteractionsClient
import com.example.network.GeminiStreamEvent
import com.example.plugin.PluginRegistry
import com.example.plugin.RiviaBridgeHolder
import com.example.plugin.RiviaChannel
import com.example.plugin.RiviaInternalBridge
import com.example.security.LabMicTestPreferences
import com.example.security.SecureKeyRepository
import com.example.security.VoicePreferences
import com.example.service.LiveSessionManager
import com.example.service.TurnRecognizerDiagnostics
import com.example.ui.theme.AppUiMode
import com.example.util.TextAudioTagProcessor
import com.example.util.TurnAcousticData
import com.example.util.TurnPayloadBuilder
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import org.json.JSONObject

data class EmotionBaselineSummary(
  val eligibleCount: Int = 0,
  val excludedModeACount: Int = 0,
  val excludedShortCount: Int = 0,
  val excludedSilentCount: Int = 0,
  val excludedCalibratedCount: Int = 0,
  val windowDays: Int = RiviaViewModel.BASELINE_WINDOW_DAYS,
  val windowStart: Long = 0L,
  val windowEnd: Long = 0L,
  val resetAtTimestamp: Long = 0L,
  val meanArousal: Float? = null,
  val meanDominance: Float? = null,
  val meanValence: Float? = null,
  val meanSpeakingRateWps: Float? = null,
  val latestTagSummary: String = "",
)

class RiviaViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    const val MIN_BASELINE_SPEECH_SEC = 1.5f
    const val BASELINE_WINDOW_DAYS = 14
    const val BASELINE_MAX_ROWS = 500
    const val BASELINE_WINDOW_MS = BASELINE_WINDOW_DAYS * 24L * 60L * 60L * 1000L
    val CALIBRATION_LABELS = listOf("excited", "calm", "irritated", "sad", "anxious", "other")

    /**
     * Counts spoken words in [text] (tokens containing at least one letter or digit).
     */
    fun countWords(text: String): Int {
      val trimmed = text.trim()
      if (trimmed.isEmpty()) return 0
      return trimmed.split(Regex("\\s+")).count { token ->
        token.any { it.isLetterOrDigit() }
      }
    }

    /**
     * Requirement 3: Speaking rate per turn = words / seconds of speech (Silero speech time, not clip length).
     */
    fun computeSpeakingRateWps(wordCount: Int, speechDurationSec: Float): Float? {
      if (speechDurationSec <= 0f) return null
      return wordCount.toFloat() / speechDurationSec
    }

    /**
     * Requirement 4: Flag clips with under 1.5 s of speech as "short" (store, but keep out of baselines).
     */
    fun isShortSpeechClip(speechDurationSec: Float): Boolean {
      return speechDurationSec < MIN_BASELINE_SPEECH_SEC
    }

    /**
     * Queries the database for the baseline using the dedicated eligible-row query
     * (Mode B, not short, not silent, A/D/V not null, within the last [BASELINE_WINDOW_DAYS] days
     * and after [resetAtMs], newest first, capped at [BASELINE_MAX_ROWS] rows) and the separate
     * excluded-counts query over the same window.
     */
    suspend fun queryAndComputeEmotionBaseline(
      dao: RiviaDao,
      resetAtMs: Long = 0L,
      nowMs: Long = System.currentTimeMillis(),
      excludeRequestId: String = "",
      excludeAtOrAfterNow: Boolean = false,
    ): EmotionBaselineSummary {
      val windowEnd = nowMs
      val queryEnd = if (excludeAtOrAfterNow) (nowMs - 1L) else nowMs
      val windowStart = maxOf(resetAtMs, windowEnd - BASELINE_WINDOW_MS)
      val eligible =
        dao.getEligibleBaselineLogs(
          windowStart = windowStart,
          windowEnd = queryEnd,
          minSpeechSec = MIN_BASELINE_SPEECH_SEC,
          maxRows = BASELINE_MAX_ROWS,
          excludeRequestId = excludeRequestId,
        )
      val excluded =
        dao.getBaselineExcludedCounts(
          windowStart = windowStart,
          windowEnd = queryEnd,
          minSpeechSec = MIN_BASELINE_SPEECH_SEC,
          excludeRequestId = excludeRequestId,
        )
      return summarizeBaseline(
        eligible = eligible,
        excluded = excluded,
        resetAtMs = resetAtMs,
        windowStart = windowStart,
        windowEnd = windowEnd,
      )
    }

    fun summarizeBaseline(
      eligible: List<VoiceEmotionLogEntity>,
      excluded: BaselineExcludedCounts,
      resetAtMs: Long,
      windowStart: Long,
      windowEnd: Long,
    ): EmotionBaselineSummary {
      if (eligible.isEmpty()) {
        return EmotionBaselineSummary(
          eligibleCount = 0,
          excludedModeACount = excluded.excludedModeACount,
          excludedShortCount = excluded.excludedShortCount,
          excludedSilentCount = excluded.excludedSilentCount,
          excludedCalibratedCount = excluded.excludedCalibratedCount,
          windowDays = BASELINE_WINDOW_DAYS,
          windowStart = windowStart,
          windowEnd = windowEnd,
          resetAtTimestamp = resetAtMs,
          latestTagSummary = "",
        )
      }

      val meanA = eligible.mapNotNull { it.arousal?.toDouble() }.average().toFloat()
      val meanD = eligible.mapNotNull { it.dominance?.toDouble() }.average().toFloat()
      val meanV = eligible.mapNotNull { it.valence?.toDouble() }.average().toFloat()
      val rates = eligible.mapNotNull { it.speakingRateWps?.toDouble() }
      val meanRate = if (rates.isNotEmpty()) rates.average().toFloat() else null
      val latest = eligible.first()
      val tag = "Mode ${latest.mode} · ${latest.audioSource} · ${latest.inputDevice}"

      return EmotionBaselineSummary(
        eligibleCount = eligible.size,
        excludedModeACount = excluded.excludedModeACount,
        excludedShortCount = excluded.excludedShortCount,
        excludedSilentCount = excluded.excludedSilentCount,
        excludedCalibratedCount = excluded.excludedCalibratedCount,
        windowDays = BASELINE_WINDOW_DAYS,
        windowStart = windowStart,
        windowEnd = windowEnd,
        resetAtTimestamp = resetAtMs,
        meanArousal = meanA,
        meanDominance = meanD,
        meanValence = meanV,
        meanSpeakingRateWps = meanRate,
        latestTagSummary = tag,
      )
    }

    /**
     * In-memory baseline computation matching the 14-day window ([BASELINE_WINDOW_DAYS])
     * and [BASELINE_MAX_ROWS] cap.
     */
    fun computeEmotionBaseline(
      logs: List<VoiceEmotionLogEntity>,
      resetAtMs: Long = 0L,
      nowMs: Long = logs.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
    ): EmotionBaselineSummary {
      val windowEnd = nowMs
      val windowStart = maxOf(resetAtMs, windowEnd - BASELINE_WINDOW_MS)
      val inWindow =
        logs
          .filter { it.timestamp in windowStart..windowEnd }
          .sortedByDescending { it.timestamp }
      val modeACount = inWindow.count { it.mode != "B" }
      val modeBLogs = inWindow.filter { it.mode == "B" }
      val shortCount =
        modeBLogs.count { it.isShortSpeech || it.speechDurationSec < MIN_BASELINE_SPEECH_SEC }
      val silentCount = modeBLogs.count { it.silentCapture }
      val calibratedCount = modeBLogs.count { !it.calibrationLabel.isNullOrBlank() }
      val eligible =
        modeBLogs
          .filter {
            !it.isShortSpeech &&
              it.speechDurationSec >= MIN_BASELINE_SPEECH_SEC &&
              !it.silentCapture &&
              it.arousal != null &&
              it.dominance != null &&
              it.valence != null &&
              it.calibrationLabel.isNullOrBlank()
          }
          .take(BASELINE_MAX_ROWS)

      return summarizeBaseline(
        eligible = eligible,
        excluded =
          BaselineExcludedCounts(
            excludedModeACount = modeACount,
            excludedShortCount = shortCount,
            excludedSilentCount = silentCount,
            excludedCalibratedCount = calibratedCount,
          ),
        resetAtMs = resetAtMs,
        windowStart = windowStart,
        windowEnd = windowEnd,
      )
    }

    /**
     * Parses a backup JSON string into a [BackupImportPayload] ready for [RiviaDao.importBackupInTransaction].
     */
    fun parseBackupJsonToPayload(jsonString: String): BackupImportPayload {
      val root = JSONObject(jsonString)
      val backupVersion = root.optInt("version", 1)
      val chatsArr = root.optJSONArray("chats") ?: JSONArray()
      val chats = mutableListOf<ChatEntity>()
      val messages = mutableListOf<MessageEntity>()

      for (i in 0 until chatsArr.length()) {
        val cObj = chatsArr.optJSONObject(i) ?: continue
        val chatId = cObj.optString("id").ifBlank { UUID.randomUUID().toString() }
        val isLab =
          if (cObj.has("isLabChat")) cObj.optBoolean("isLabChat", true)
          else backupVersion < 2
        chats.add(
          ChatEntity(
            id = chatId,
            title = cObj.optString("title", "Imported chat"),
            createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = cObj.optLong("updatedAt", System.currentTimeMillis()),
            runSettingsJson = cObj.optString("runSettingsJson", RunSettings().toJsonString()),
            lastInteractionId =
              cObj.optString("lastInteractionId").takeIf {
                it.isNotBlank() && it != "null"
              },
            isLabChat = isLab,
            riviaProfile = cObj.optString("riviaProfile", "balanced").ifBlank { "balanced" },
            pendingH7Notice =
              cObj.optString("pendingH7Notice").takeIf {
                it.isNotBlank() && it != "null"
              },
          )
        )

        val msgsArr = cObj.optJSONArray("messages") ?: JSONArray()
        for (j in 0 until msgsArr.length()) {
          val mObj = msgsArr.optJSONObject(j) ?: continue
          messages.add(
            MessageEntity(
              id = mObj.optString("id").ifBlank { UUID.randomUUID().toString() },
              chatId = chatId,
              role = mObj.optString("role", "user"),
              content = mObj.optString("content", ""),
              thoughtSummary = mObj.optString("thoughtSummary", ""),
              requestId =
                mObj.optString("requestId").takeIf { it.isNotBlank() && it != "null" }
                  ?: TextAudioTagProcessor.generateRequestId(),
              interactionId =
                mObj.optString("interactionId").takeIf {
                  it.isNotBlank() && it != "null"
                },
              previousInteractionId =
                mObj.optString("previousInteractionId").takeIf {
                  it.isNotBlank() && it != "null"
                },
              model = mObj.optString("model", "gemini-3.8-flash"),
              serviceTier = mObj.optString("serviceTier", "standard"),
              inputTokens = mObj.optInt("inputTokens", 0),
              outputTokens = mObj.optInt("outputTokens", 0),
              thoughtTokens = mObj.optInt("thoughtTokens", 0),
              ttftMs = mObj.optLong("ttftMs", 0L),
              totalTimeMs = mObj.optLong("totalTimeMs", 0L),
              status = mObj.optString("status", "completed"),
              errorMessage =
                mObj.optString("errorMessage").takeIf {
                  it.isNotBlank() && it != "null"
                },
              voiceLabel =
                mObj.optString("voiceLabel").takeIf {
                  it.isNotBlank() && it != "null"
                },
              rawStepsJson = mObj.optString("rawStepsJson", "[]"),
              createdAt = mObj.optLong("createdAt", System.currentTimeMillis()),
              chatKind = mObj.optString("chatKind", if (isLab) "lab" else "rivia"),
              profile =
                mObj.optString("profile").takeIf { it.isNotBlank() && it != "null" } ?: "",
              channel = mObj.optString("channel", "text"),
              fingerprint =
                mObj.optString("fingerprint").takeIf { it.isNotBlank() && it != "null" } ?: "",
              ruleEventsSummary =
                mObj.optString("ruleEventsSummary").takeIf {
                  it.isNotBlank() && it != "null"
                } ?: "",
              h7TrimmedFrom =
                if (mObj.has("h7TrimmedFrom") && !mObj.isNull("h7TrimmedFrom"))
                  mObj.optInt("h7TrimmedFrom")
                else null,
              h7TrimmedTo =
                if (mObj.has("h7TrimmedTo") && !mObj.isNull("h7TrimmedTo"))
                  mObj.optInt("h7TrimmedTo")
                else null,
            )
          )
        }
      }

      val layerDrafts = mutableListOf<LayerDraftEntity>()
      root.optJSONArray("layerDrafts")?.let { arr ->
        for (i in 0 until arr.length()) {
          val dObj = arr.optJSONObject(i) ?: continue
          val key = dObj.optString("layerKey")
          val content = dObj.optString("jsonContent")
          if (key.isNotBlank() && content.isNotBlank()) {
            layerDrafts.add(
              LayerDraftEntity(
                layerKey = key,
                jsonContent = content,
                updatedAt = dObj.optLong("updatedAt", System.currentTimeMillis()),
              )
            )
          }
        }
      }

      val layerLocks = mutableListOf<LayerLockEntity>()
      root.optJSONArray("layerLocks")?.let { arr ->
        for (i in 0 until arr.length()) {
          val lObj = arr.optJSONObject(i) ?: continue
          val key = lObj.optString("layerKey")
          if (key.isNotBlank()) {
            layerLocks.add(
              LayerLockEntity(
                layerKey = key,
                pendingCanonicalSha256 =
                  lObj.optString("pendingCanonicalSha256").takeIf {
                    it.isNotBlank() && it != "null"
                  },
                lastSeenBundledSha256 =
                  lObj.optString("lastSeenBundledSha256").takeIf {
                    it.isNotBlank() && it != "null"
                  },
                exportedJson =
                  lObj.optString("exportedJson").takeIf {
                    it.isNotBlank() && it != "null"
                  },
                mismatchWarning =
                  lObj.optString("mismatchWarning").takeIf {
                    it.isNotBlank() && it != "null"
                  },
              )
            )
          }
        }
      }

      val layerTestRuns = mutableListOf<LayerTestRunEntity>()
      root.optJSONArray("layerTestRuns")?.let { arr ->
        for (i in 0 until arr.length()) {
          val trObj = arr.optJSONObject(i) ?: continue
          layerTestRuns.add(
            LayerTestRunEntity(
              id = trObj.optString("id").ifBlank { UUID.randomUUID().toString() },
              testId = trObj.optString("testId"),
              profile = LayerConfigManager.normalizeProfileKey(trObj.optString("profile")),
              fingerprint = trObj.optString("fingerprint"),
              passed = trObj.optBoolean("passed", false),
              detailsJson = trObj.optString("detailsJson", "{}"),
              timestamp = trObj.optLong("timestamp", System.currentTimeMillis()),
            )
          )
        }
      }

      val ruleEvents = mutableListOf<RuleEventEntity>()
      root.optJSONArray("ruleEvents")?.let { arr ->
        for (i in 0 until arr.length()) {
          val reObj = arr.optJSONObject(i) ?: continue
          val reqId =
            if (reObj.isNull("requestId")) ""
            else reObj.optString("requestId").takeIf { it != "null" }?.trim().orEmpty()
          ruleEvents.add(
            RuleEventEntity(
              chatId = reObj.optString("chatId"),
              requestId = reqId,
              ruleId = reObj.optString("ruleId"),
              eventType = reObj.optString("eventType", reObj.optString("channel", "text")),
              detail = reObj.optString("detail", ""),
              timestamp = reObj.optLong("timestamp", System.currentTimeMillis()),
            )
          )
        }
      }

      val requestLogs = mutableListOf<RequestLogEntity>()
      root.optJSONArray("requestLogs")?.let { arr ->
        for (i in 0 until arr.length()) {
          val rlObj = arr.optJSONObject(i) ?: continue
          val reqId =
            if (rlObj.isNull("requestId")) ""
            else rlObj.optString("requestId").takeIf { it != "null" }?.trim().orEmpty()
          requestLogs.add(
            RequestLogEntity(
              timestamp = rlObj.optLong("timestamp", System.currentTimeMillis()),
              requestId = reqId,
              model = rlObj.optString("model"),
              status = rlObj.optString("status", "ok"),
              serviceTier = rlObj.optString("serviceTier", "standard"),
              inputTokens = rlObj.optInt("inputTokens", 0),
              outputTokens = rlObj.optInt("outputTokens", 0),
              thoughtTokens = rlObj.optInt("thoughtTokens", 0),
              latencyMs = rlObj.optLong("latencyMs", 0L),
              error =
                rlObj.optString("error").takeIf { it.isNotBlank() && it != "null" },
              chatKind = rlObj.optString("chatKind", "Lab"),
              profile = rlObj.optString("profile", ""),
              channel = rlObj.optString("channel", ""),
              fingerprint = rlObj.optString("fingerprint", ""),
              ruleEvents = rlObj.optString("ruleEvents", ""),
            )
          )
        }
      }

      val voiceEmotionLogs = mutableListOf<VoiceEmotionLogEntity>()
      root.optJSONArray("voiceEmotionLogs")?.let { arr ->
        for (i in 0 until arr.length()) {
          val veObj = arr.optJSONObject(i) ?: continue
          val reqId =
            if (veObj.isNull("requestId")) ""
            else veObj.optString("requestId").takeIf { it != "null" }?.trim().orEmpty()
          voiceEmotionLogs.add(
            VoiceEmotionLogEntity(
              requestId = reqId,
              mode = veObj.optString("mode", "B"),
              recognizerOk = veObj.optBoolean("recognizerOk", true),
              recognizerErrorCode =
                if (veObj.has("recognizerErrorCode") && !veObj.isNull("recognizerErrorCode"))
                  veObj.optInt("recognizerErrorCode")
                else null,
              recognizerPackage = veObj.optString("recognizerPackage", ""),
              clipLengthSec = veObj.optDouble("clipLengthSec", 0.0).toFloat(),
              rms = veObj.optDouble("rms", 0.0).toFloat(),
              peak = veObj.optDouble("peak", 0.0).toFloat(),
              silentCapture = veObj.optBoolean("silentCapture", false),
              arousal =
                if (veObj.has("arousal") && !veObj.isNull("arousal"))
                  veObj.optDouble("arousal").toFloat()
                else null,
              dominance =
                if (veObj.has("dominance") && !veObj.isNull("dominance"))
                  veObj.optDouble("dominance").toFloat()
                else null,
              valence =
                if (veObj.has("valence") && !veObj.isNull("valence"))
                  veObj.optDouble("valence").toFloat()
                else null,
              inferenceMs =
                if (veObj.has("inferenceMs") && !veObj.isNull("inferenceMs"))
                  veObj.optLong("inferenceMs")
                else null,
              captureStatus = veObj.optString("captureStatus", "OK"),
              errorMessage =
                veObj.optString("errorMessage").takeIf { it.isNotBlank() && it != "null" },
              timestamp = veObj.optLong("timestamp", System.currentTimeMillis()),
              audioRecordStarted = veObj.optBoolean("audioRecordStarted", true),
              pipeBytesWritten = veObj.optLong("pipeBytesWritten", 0L),
              partialResultsCount = veObj.optInt("partialResultsCount", 0),
              callbacksFired = veObj.optString("callbacksFired", ""),
              usedPartialFallback = veObj.optBoolean("usedPartialFallback", false),
              vadSpeechDetected = veObj.optBoolean("vadSpeechDetected", false),
              vadEndedTurn = veObj.optBoolean("vadEndedTurn", false),
              vadMaxProb = veObj.optDouble("vadMaxProb", 0.0).toFloat(),
              transcriptPreview = veObj.optString("transcriptPreview", ""),
              finalText = veObj.optString("finalText", ""),
              pipeCloseToFinalMs =
                if (veObj.has("pipeCloseToFinalMs") && !veObj.isNull("pipeCloseToFinalMs"))
                  veObj.optLong("pipeCloseToFinalMs")
                else null,
              lastPartialText = veObj.optString("lastPartialText", ""),
              transcriptSource = veObj.optString("transcriptSource", ""),
              audioSource = veObj.optString("audioSource", "VOICE_RECOGNITION"),
              inputDevice = veObj.optString("inputDevice", "unknown"),
              speechDurationSec = veObj.optDouble("speechDurationSec", 0.0).toFloat(),
              wordCount = veObj.optInt("wordCount", 0),
              speakingRateWps =
                if (veObj.has("speakingRateWps") && !veObj.isNull("speakingRateWps"))
                  veObj.optDouble("speakingRateWps").toFloat()
                else null,
              isShortSpeech = veObj.optBoolean("isShortSpeech", false),
              scoredWindowsCount = veObj.optInt("scoredWindowsCount", 1),
              calibrationLabel =
                if (veObj.has("calibrationLabel") && !veObj.isNull("calibrationLabel")) {
                  veObj.optString("calibrationLabel").takeIf { it.isNotBlank() && it != "null" }
                } else if (veObj.has("calibration_label") && !veObj.isNull("calibration_label")) {
                  veObj.optString("calibration_label").takeIf { it.isNotBlank() && it != "null" }
                } else null,
              rmsAfterGain =
                if (veObj.has("rmsAfterGain") && !veObj.isNull("rmsAfterGain"))
                  veObj.optDouble("rmsAfterGain").toFloat()
                else null,
              appliedGainDb =
                if (veObj.has("appliedGainDb") && !veObj.isNull("appliedGainDb"))
                  veObj.optDouble("appliedGainDb").toFloat()
                else null,
            )
          )
        }
      }

      val turnDispatchLogs = mutableListOf<TurnDispatchLogEntity>()
      root.optJSONArray("turnDispatchLogs")?.let { arr ->
        for (i in 0 until arr.length()) {
          val tdObj = arr.optJSONObject(i) ?: continue
          val reqId =
            if (tdObj.isNull("request_id")) {
              tdObj.optString("requestId").takeIf { it != "null" }?.trim().orEmpty()
            } else {
              tdObj.optString("request_id").takeIf { it != "null" }?.trim().orEmpty()
            }
          if (reqId.isNotBlank()) {
            turnDispatchLogs.add(
              TurnDispatchLogEntity(
                requestId = reqId,
                createdAt =
                  if (tdObj.has("created_at")) tdObj.optLong("created_at", System.currentTimeMillis())
                  else tdObj.optLong("createdAt", System.currentTimeMillis()),
                payloadJson =
                  if (tdObj.has("payload_json")) tdObj.optString("payload_json", "{}")
                  else tdObj.optString("payloadJson", "{}"),
                triageJson =
                  tdObj.optString("triage_json").takeIf { it.isNotBlank() && it != "null" },
                correctedJson =
                  tdObj.optString("corrected_json").takeIf { it.isNotBlank() && it != "null" },
                toolName =
                  tdObj.optString("tool_name").takeIf { it.isNotBlank() && it != "null" },
                toolArgsJson =
                  tdObj.optString("tool_args_json").takeIf { it.isNotBlank() && it != "null" },
                toolResultJson =
                  tdObj.optString("tool_result_json").takeIf { it.isNotBlank() && it != "null" },
                replyText =
                  tdObj.optString("reply_text").takeIf { it.isNotBlank() && it != "null" },
                status = tdObj.optString("status", "completed"),
                error = tdObj.optString("error").takeIf { it.isNotBlank() && it != "null" },
                timingsJson =
                  if (tdObj.has("timings_json")) tdObj.optString("timings_json", "{}")
                  else tdObj.optString("timingsJson", "{}"),
              )
            )
          }
        }
      }

      val layerSettings =
        root.optJSONObject("layerSettings")?.let { lsObj ->
          val spoken = lsObj.optInt("spokenMaxSentences", 2).coerceIn(1, 4)
          val wordsJson = lsObj.optString("h11WatchWordsJson", "[]")
          LayerSettingsEntity(
            id = 1,
            spokenMaxSentences = spoken,
            h11WatchWordsJson = wordsJson,
          )
        }

      return BackupImportPayload(
        chats = chats,
        messages = messages,
        layerDrafts = layerDrafts,
        layerLocks = layerLocks,
        layerTestRuns = layerTestRuns,
        ruleEvents = ruleEvents,
        requestLogs = requestLogs,
        voiceEmotionLogs = voiceEmotionLogs,
        turnDispatchLogs = turnDispatchLogs,
        layerSettings = layerSettings,
      )
    }

    /**
     * Persists or updates a turn's `voice_emotion_logs` row:
     * - Fix 1: When the 300 ms barrier timed out and background scoring completes later (`emotion_late`),
     *   writes `arousal`/`dominance`/`valence` to that turn's `voice_emotion_logs` row and marks it
     *   `"emotion_late"` with the total ms (`emotion_late(${emotionWaitMs}ms)`).
     * - Fix 4: Runs WAV saving (`saveWavClipBlock`) strictly AFTER the `voice_emotion_logs` row and
     *   its emotion scores are persisted in Room, never inside the 300 ms barrier.
     */
    suspend fun persistVoiceEmotionTurnLog(
      dao: RiviaDao,
      requestId: String,
      recognizerOk: Boolean,
      recognizerErrorCode: Int?,
      recognizedText: String,
      diagnostics: TurnRecognizerDiagnostics,
      captureResult: MicCaptureResult,
      precomputedEmotionResult: Result<com.example.audio.EmotionPrediction>? = null,
      emotionWaitMs: Long? = null,
      emotionBarrierFailureReason: String? = null,
      fallbackScoreBlock: (suspend (MicCaptureResult) -> Result<com.example.audio.EmotionPrediction>)? = null,
      saveWavClipBlock: (() -> java.io.File?)? = null,
    ): VoiceEmotionLogEntity {
      val speechSec = captureResult.speechDurationSec
      val words = countWords(recognizedText)
      val speakingRate = computeSpeakingRateWps(words, speechSec)
      val isShort = isShortSpeechClip(speechSec)

      val chosenSourceLabel =
        diagnostics.transcriptSource.ifBlank {
          if (diagnostics.usedPartialFallback) "used partial" else "onResults"
        }

      val isLateCompletion = emotionBarrierFailureReason == LiveSessionManager.EMOTION_REASON_LATE
      val isModeAGoogleCapture = captureResult.mode == "A" && !captureResult.audioRecordStarted

      val initialStatus =
        when {
          isModeAGoogleCapture && !recognizerOk -> "REC ERR (${recognizerErrorCode ?: "none"})"
          isModeAGoogleCapture -> "OK (Mode A · no_scores)"
          !captureResult.captureError.isNullOrBlank() -> "CAPTURE ERROR"
          captureResult.silentCapture -> "SILENT CAPTURE"
          isLateCompletion && isShort -> "SHORT · emotion_late (${emotionWaitMs ?: 0L}ms)"
          isLateCompletion -> "OK · emotion_late (${emotionWaitMs ?: 0L}ms)"
          isShort && diagnostics.usedPartialFallback -> "SHORT · OK (used partial)"
          isShort -> "SHORT (<1.5s speech)"
          diagnostics.usedPartialFallback -> "OK (used partial)"
          !recognizerOk -> "REC ERR (${recognizerErrorCode ?: "none"})"
          else -> "OK ($chosenSourceLabel)"
        }

      val callbacksWithBarrier =
        buildString {
          append(diagnostics.callbacksFired)
          if (emotionWaitMs != null) {
            if (isNotEmpty()) append(", ")
            val statusTag = emotionBarrierFailureReason ?: "emotion_ok"
            append("$statusTag(${emotionWaitMs}ms)")
          }
        }

      val existingRow = dao.getVoiceEmotionLogByRequestId(requestId)
      val savedRow =
        if (existingRow != null) {
          val updatedBase =
            existingRow.copy(
              captureStatus = initialStatus,
              callbacksFired = callbacksWithBarrier,
            )
          dao.updateVoiceEmotionLog(updatedBase)
          updatedBase
        } else {
          val initialEntity =
            VoiceEmotionLogEntity(
              requestId = requestId,
              mode = captureResult.mode,
              recognizerOk = recognizerOk,
              recognizerErrorCode = recognizerErrorCode,
              recognizerPackage = captureResult.recognizerPackage,
              clipLengthSec = captureResult.clipLengthSec,
              rms = captureResult.rms,
              peak = captureResult.peak,
              silentCapture = captureResult.silentCapture,
              arousal = null,
              dominance = null,
              valence = null,
              inferenceMs = null,
              captureStatus = initialStatus,
              wavFilePath = null,
              errorMessage = captureResult.captureError,
              timestamp = System.currentTimeMillis(),
              audioRecordStarted = captureResult.audioRecordStarted,
              pipeBytesWritten = captureResult.pipeBytesWritten,
              partialResultsCount = diagnostics.partialResultsCount,
              callbacksFired = callbacksWithBarrier,
              usedPartialFallback = diagnostics.usedPartialFallback,
              vadSpeechDetected = captureResult.vadSpeechDetected,
              vadEndedTurn = captureResult.vadEndedTurn,
              vadMaxProb = captureResult.vadMaxProb,
              transcriptPreview = recognizedText.take(240),
              finalText = diagnostics.finalText.take(240),
              pipeCloseToFinalMs = diagnostics.pipeCloseToFinalMs,
              lastPartialText = diagnostics.lastPartialText.take(240),
              transcriptSource = chosenSourceLabel,
              audioSource = captureResult.audioSource,
              inputDevice = captureResult.inputDevice,
              speechDurationSec = speechSec,
              wordCount = words,
              speakingRateWps = if (isModeAGoogleCapture) null else speakingRate,
              isShortSpeech = if (isModeAGoogleCapture) false else isShort,
              scoredWindowsCount =
                if (isModeAGoogleCapture) 0 else maxOf(1, captureResult.preScoredWindows.size),
              calibrationLabel = null,
              rmsAfterGain = if (captureResult.mode == "B") captureResult.rmsAfterGain else null,
              appliedGainDb = if (captureResult.mode == "B") captureResult.appliedGainDb else null,
            )
          val insertedId = dao.logVoiceEmotionAndPrune(initialEntity)
          initialEntity.copy(id = insertedId)
        }

      if (isModeAGoogleCapture) {
        return savedRow
      }

      if (captureResult.samples16kMono.isEmpty()) {
        val emptyErr =
          listOfNotNull(
              emotionBarrierFailureReason,
              captureResult.captureError ?: "AudioRecord captured 0 samples",
            )
            .joinToString(": ")
        val emptyRow =
          savedRow.copy(
            errorMessage = emptyErr,
            captureStatus = if (captureResult.silentCapture) "SILENT CAPTURE" else "EMPTY CLIP",
          )
        dao.updateVoiceEmotionLog(emptyRow)
        return emptyRow
      }

      val inferenceResult =
        precomputedEmotionResult
          ?: fallbackScoreBlock?.invoke(captureResult)

      var finalRow = savedRow
      if (inferenceResult != null) {
        inferenceResult.fold(
          onSuccess = { pred ->
            finalRow =
              savedRow.copy(
                arousal = pred.arousal,
                dominance = pred.dominance,
                valence = pred.valence,
                inferenceMs = pred.inferenceMs,
                scoredWindowsCount = pred.scoredWindowsCount,
                errorMessage =
                  if (isLateCompletion) "emotion_late (${emotionWaitMs ?: 0L}ms)"
                  else savedRow.errorMessage,
              )
            dao.updateVoiceEmotionLog(finalRow)
          },
          onFailure = { err ->
            val combinedErr =
              listOfNotNull(
                  emotionBarrierFailureReason,
                  captureResult.captureError,
                  err.message ?: err.javaClass.simpleName,
                )
                .distinct()
                .joinToString(" · ")
            finalRow =
              savedRow.copy(
                errorMessage = combinedErr,
                captureStatus =
                  if (
                    savedRow.captureStatus.startsWith("OK") ||
                      savedRow.captureStatus.startsWith("SHORT")
                  )
                    "MODEL ERROR"
                  else savedRow.captureStatus,
              )
            dao.updateVoiceEmotionLog(finalRow)
          },
        )
      }

      // Fix 4: WAV save runs strictly outside the 300 ms barrier and after emotion scores are persisted
      val wavFile = saveWavClipBlock?.invoke()
      if (wavFile != null) {
        finalRow = finalRow.copy(wavFilePath = wavFile.absolutePath)
        dao.updateVoiceEmotionLog(finalRow)
      }

      return finalRow
    }
  }

  private val db = RiviaDatabase.getInstance(application)
  private val dao = db.riviaDao()
  val keyRepository = SecureKeyRepository(application)
  val geminiClient = GeminiInteractionsClient()
  val elevenLabsClient = ElevenLabsClient()
  val audioPlayerManager = AudioPlayerManager(application, keyRepository, elevenLabsClient)
  val emotionModelManager = EmotionModelManager(application)
  val sileroVadManager = SileroVadManager(application)

  // Raw bundled config files read at startup
  private val _bundledRawFiles = MutableStateFlow<Map<String, String>>(emptyMap())

  // Layer load / validation error (non-null fails closed on Rivia chats)
  private val _layersLoadError = MutableStateFlow<String?>(null)
  val layersLoadError: StateFlow<String?> = _layersLoadError.asStateFlow()

  // Loaded & validated layers bundle (bundled + Room drafts)
  private val _loadedLayersBundle = MutableStateFlow<LoadedLayersBundle?>(null)
  val loadedLayersBundle: StateFlow<LoadedLayersBundle?> = _loadedLayersBundle.asStateFlow()

  // Spoken sentence limit (H7 tunable 1..4, default 2)
  private val _spokenMaxSentences = MutableStateFlow(2)
  val spokenMaxSentences: StateFlow<Int> = _spokenMaxSentences.asStateFlow()

  // H11 watch words list
  private val _h11WatchWords = MutableStateFlow<List<String>>(emptyList())
  val h11WatchWords: StateFlow<List<String>> = _h11WatchWords.asStateFlow()

  // Per-profile 8-char SHA-256 fingerprints (fast, balanced, deep, live)
  private val _profileFingerprints = MutableStateFlow<Map<String, String>>(emptyMap())
  val profileFingerprints: StateFlow<Map<String, String>> = _profileFingerprints.asStateFlow()

  // Per-profile test states (fast, balanced, deep, live)
  private val _profileTestStates = MutableStateFlow<Map<String, ProfileTestState>>(emptyMap())
  val profileTestStates: StateFlow<Map<String, ProfileTestState>> = _profileTestStates.asStateFlow()

  // Per-layer UI metadata (persona, style, engine, hard_rules)
  private val _layerCardMetas = MutableStateFlow<Map<String, LayerCardUiMeta>>(emptyMap())
  val layerCardMetas: StateFlow<Map<String, LayerCardUiMeta>> = _layerCardMetas.asStateFlow()

  // Whether any layer currently has an active phone draft
  private val _hasAnyLayerDraft = MutableStateFlow(false)
  val hasAnyLayerDraft: StateFlow<Boolean> = _hasAnyLayerDraft.asStateFlow()

  // Test runs & recent rule events
  val layerTestRuns: StateFlow<List<LayerTestRunEntity>> =
    dao
      .observeLayerTestRuns()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentRuleEvents: StateFlow<List<RuleEventEntity>> =
    dao
      .observeRecentRuleEvents()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Active UI Mode: RIVIA (Mint consumer), DEV (Red Rivia internals), LABS (Amber sandbox)
  private val _appUiMode = MutableStateFlow(AppUiMode.RIVIA)
  val appUiMode: StateFlow<AppUiMode> = _appUiMode.asStateFlow()

  private fun scopedLayerKey(layerKey: String, mode: AppUiMode = _appUiMode.value): String {
    return if (mode == AppUiMode.LABS) "labs:$layerKey" else layerKey
  }

  private fun scopedSettingsId(mode: AppUiMode = _appUiMode.value): Int {
    return if (mode == AppUiMode.LABS) 2 else 1
  }

  fun setAppUiMode(mode: AppUiMode) {
    val previousMode = _appUiMode.value
    _appUiMode.value = mode
    val targetIsLab = mode == AppUiMode.LABS
    val prevWasLab = previousMode == AppUiMode.LABS

    viewModelScope.launch {
      keyRepository.setUiMode(mode.name)
      // When switching between Rivia/Dev (isLabChat=false) and Labs (isLabChat=true),
      // switch the active chat context to the matching mode's chat list
      if (targetIsLab != prevWasLab || isActiveChatLab.value != targetIsLab) {
        stopStreaming()
        val allChats = dao.getAllChats()
        val matchingChat = allChats.firstOrNull { it.isLabChat == targetIsLab }
        if (matchingChat != null) {
          _activeChatId.value = matchingChat.id
          _pendingNewChatIsLab.value = targetIsLab
          _pendingNewChatSettings.value = null
        } else {
          if (targetIsLab) {
            startNewLabChat()
          } else {
            startNewRiviaChat()
          }
        }
      }
    }
  }

  // Search query in drawer
  private val _drawerSearchQuery = MutableStateFlow("")
  val drawerSearchQuery: StateFlow<String> = _drawerSearchQuery.asStateFlow()

  // All chats filtered by active mode (Rivia/Dev share Rivia chats; Labs has isolated Lab chats) and search query
  val chats: StateFlow<List<ChatEntity>> =
    combine(dao.observeAllChats(), _drawerSearchQuery, _appUiMode) { list, query, mode ->
        val wantLab = mode == AppUiMode.LABS
        val modeFiltered = list.filter { it.isLabChat == wantLab }
        if (query.isBlank()) {
          modeFiltered
        } else {
          modeFiltered.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Active chat ID (null when in a fresh unsaved empty chat state)
  private val _activeChatId = MutableStateFlow<String?>(null)
  val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val activeChat: StateFlow<ChatEntity?> =
    _activeChatId
      .flatMapLatest { id -> if (id == null) flowOf(null) else dao.observeChatById(id) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  // Pending chat type & Rivia profile when in an unsaved new chat
  private val _pendingNewChatIsLab = MutableStateFlow(false)
  private val _pendingRiviaProfile = MutableStateFlow("live")

  val isActiveChatLab: StateFlow<Boolean> =
    combine(activeChat, _pendingNewChatIsLab) { chat, pendingLab -> chat?.isLabChat ?: pendingLab }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  val activeRiviaProfile: StateFlow<String> =
    combine(activeChat, _pendingRiviaProfile, _loadedLayersBundle, _appUiMode) {
        chat,
        pendingProf,
        bundle,
        mode ->
        if (mode == AppUiMode.RIVIA) {
          "live"
        } else {
          val fallback = bundle?.engine?.default_profile ?: "live"
          val raw =
            when {
              chat != null && !chat.isLabChat -> chat.riviaProfile
              else -> pendingProf
            }
          raw.ifBlank { fallback }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "live")

  // In-memory streaming overlay so active turns update immediately without DB thrashing
  private val _streamingMessageOverlay = MutableStateFlow<MessageEntity?>(null)

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val activeMessages: StateFlow<List<MessageEntity>> =
    combine(
        _activeChatId.flatMapLatest { id ->
          if (id == null) flowOf(emptyList()) else dao.observeMessagesForChat(id)
        },
        _streamingMessageOverlay,
      ) { dbMessages, overlay ->
        if (overlay == null) {
          dbMessages
        } else {
          val idx = dbMessages.indexOfFirst { it.id == overlay.id }
          if (idx >= 0) {
            dbMessages.toMutableList().apply { this[idx] = overlay }
          } else if (overlay.chatId == _activeChatId.value) {
            dbMessages + overlay
          } else {
            dbMessages
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Default RunSettings for new Lab chats
  val defaultRunSettings: StateFlow<RunSettings> =
    dao
      .observeDefaultSettings()
      .combine(flowOf(Unit)) { entity, _ ->
        entity?.let { RunSettings.fromJsonString(it.runSettingsJson) } ?: RunSettings()
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RunSettings())

  // Pending RunSettings when in a new Lab chat that hasn't sent its first message yet
  private val _pendingNewChatSettings = MutableStateFlow<RunSettings?>(null)

  // Current effective RunSettings for the active chat
  val currentRunSettings: StateFlow<RunSettings> =
    combine(activeChat, _pendingNewChatSettings, defaultRunSettings) { chat, pending, def ->
        when {
          chat != null -> RunSettings.fromJsonString(chat.runSettingsJson)
          pending != null -> pending
          else -> def
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RunSettings())

  // Mode presets for Lab chats (Fast, Balanced, Deep)
  val modePresets: StateFlow<List<ModePreset>> =
    keyRepository.presetsJsonFlow
      .combine(flowOf(Unit)) { json, _ -> ModePreset.listFromJson(json) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ModePreset.defaultPresets(),
      )

  // Keys & Voice preferences
  val geminiKey: StateFlow<String> =
    keyRepository.geminiKeyFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      "",
    )

  val elevenLabsKey: StateFlow<String> =
    keyRepository.elevenLabsKeyFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      "",
    )

  val voicePreferences: StateFlow<VoicePreferences> =
    keyRepository.voicePreferencesFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      VoicePreferences(),
    )

  // Pass 2: Lab Mic & Emotion test preferences and state flows
  val labMicTestPreferences: StateFlow<LabMicTestPreferences> =
    keyRepository.labMicTestPreferencesFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      LabMicTestPreferences(),
    )

  val emotionModelState: StateFlow<EmotionModelState> = emotionModelManager.modelState
  val sileroVadModelState: StateFlow<SileroVadModelState> = sileroVadManager.modelState

  val voiceEmotionLogs: StateFlow<List<VoiceEmotionLogEntity>> =
    dao
      .observeVoiceEmotionLogs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val totalVoiceEmotionLogsCount: StateFlow<Int> =
    dao
      .observeTotalVoiceEmotionLogsCount()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  val emotionBaselineSummary: StateFlow<EmotionBaselineSummary> =
    combine(voiceEmotionLogs, totalVoiceEmotionLogsCount, labMicTestPreferences) { _, _, prefs ->
        queryAndComputeEmotionBaseline(dao, prefs.emotionBaselineResetAt)
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        EmotionBaselineSummary(),
      )

  fun resetEmotionBaseline() {
    val now = System.currentTimeMillis()
    viewModelScope.launch {
      keyRepository.updateLabMicTestPreferences { it.copy(emotionBaselineResetAt = now) }
      showNotice("Emotion baseline reset. Future Mode B turns (>= 1.5 s speech) will build the new baseline.")
    }
  }

  fun observeVoiceEmotionLogForRequest(requestId: String): Flow<VoiceEmotionLogEntity?> {
    if (requestId.isBlank()) return flowOf(null)
    return dao.observeVoiceEmotionLogByRequestId(requestId)
  }

  fun setVoiceEmotionCalibrationLabel(logId: Long, label: String?) {
    val clean = label?.trim()?.lowercase(Locale.US)?.takeIf { it in CALIBRATION_LABELS }
    viewModelScope.launch(Dispatchers.IO) {
      dao.updateVoiceEmotionCalibrationLabel(logId, clean)
      showNotice(
        if (clean != null) "Calibration label set to \"$clean\" (excluded from baseline)."
        else "Calibration label cleared (turn eligible for baseline if Mode B >= 1.5 s)."
      )
    }
  }

  fun updateLabMicTestPrefs(transform: (LabMicTestPreferences) -> LabMicTestPreferences) {
    viewModelScope.launch { keyRepository.updateLabMicTestPreferences(transform) }
  }

  fun downloadEmotionModel(forceRedownload: Boolean = false) {
    emotionModelManager.startDownload(forceRedownload = forceRedownload)
  }

  fun downloadSileroVadModel(forceRedownload: Boolean = false) {
    sileroVadManager.ensureDownloadedAsync(forceRedownload = forceRedownload)
  }

  // Cached Gemini models
  val cachedModels: StateFlow<List<CachedModelEntity>> =
    dao
      .observeCachedModels()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Request logs (display query keeps LIMIT 100)
  val requestLogs: StateFlow<List<RequestLogEntity>> =
    dao
      .observeRequestLogs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val totalRequestLogsCount: StateFlow<Int> =
    dao
      .observeTotalRequestLogsCount()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  // Streaming state
  private val _isStreaming = MutableStateFlow(false)
  val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

  private var streamingJob: Job? = null
  private var activeTurnCompletionJob: Job? = null

  // ElevenLabs voices & models state for Settings > Voice
  private val _personalVoices = MutableStateFlow<List<ElevenLabsVoice>>(emptyList())
  val personalVoices: StateFlow<List<ElevenLabsVoice>> = _personalVoices.asStateFlow()

  private val _elevenModels = MutableStateFlow<List<ElevenLabsModel>>(emptyList())
  val elevenModels: StateFlow<List<ElevenLabsModel>> = _elevenModels.asStateFlow()

  // Plain API error / status banner state
  private val _statusBannerMessage = MutableStateFlow<String?>(null)
  val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

  private val _isErrorBanner = MutableStateFlow(false)
  val isErrorBanner: StateFlow<Boolean> = _isErrorBanner.asStateFlow()

  private val _isLoadingModels = MutableStateFlow(false)
  val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

  private val _isLoadingVoices = MutableStateFlow(false)
  val isLoadingVoices: StateFlow<Boolean> = _isLoadingVoices.asStateFlow()

  init {
    // 1. Load bundled layer configs and reconcile lock hashes before observing state
    initializeLayersAndLocks(application)

    // 2. Register internal entry point for Section 7 Plug-ins / Tasker bridge
    RiviaBridgeHolder.bridge =
      object : RiviaInternalBridge {
        override suspend fun askRivia(
          text: String,
          channel: RiviaChannel,
          requestId: String?,
        ): String {
          return executeDirectAsk(text, channel, requestId)
        }

        override suspend fun speakLine(text: String) {
          audioPlayerManager.speakMarkdownMessage(
            messageId = null,
            rawMarkdown = text,
            isLiveChannel = false,
          )
        }
      }

    // Ensure Silero VAD (~2 MB) is downloaded/loaded in background
    sileroVadManager.ensureDownloadedAsync(forceRedownload = false)

    // 3. Wire LiveSessionManager to save each Live turn in the active chat with "voice · m:ss"
    // and enforce H7 sentence limit & H9 spoken fallback in Rivia chats
    LiveSessionManager.attachDependencies(
      context = application,
      playerManager = audioPlayerManager,
      keyRepo = keyRepository,
      vadManager = sileroVadManager,
      emotionManager = emotionModelManager,
      h7LimitProvider = {
        val isLab = isActiveChatLab.value
        val bundle = _loadedLayersBundle.value
        val h7Active =
          bundle?.hardRules?.rules?.any { it.id == "H7" && it.active } ?: false
        if (!isLab && h7Active) _spokenMaxSentences.value else null
      },
      isRiviaTurnProvider = { !isActiveChatLab.value },
      ensureEmotionModelReady = { emotionModelManager.ensureDownloadedAsync() },
      liveTtsFirstAudioHandler = { reqId, epochMs ->
        viewModelScope.launch(Dispatchers.IO) {
          updateTurnDispatchTimings(reqId, ttsFirstAudioMs = epochMs)
        }
      },
      liveTurnPlaybackDoneHandler = { reqId, epochMs ->
        viewModelScope.launch(Dispatchers.IO) {
          updateTurnDispatchTimings(reqId, playbackDoneMs = epochMs)
        }
      },
      micCaptureCompletedHandler =
        {
          reqId,
          recOk,
          recErrCode,
          recognizedText,
          diagnostics,
          captureResult,
          precomputedEmotionResult,
          emotionWaitMs,
          emotionBarrierFailureReason ->
          handleLiveMicCaptureAndEmotionLogging(
            requestId = reqId,
            recognizerOk = recOk,
            recognizerErrorCode = recErrCode,
            recognizedText = recognizedText,
            diagnostics = diagnostics,
            captureResult = captureResult,
            precomputedEmotionResult = precomputedEmotionResult,
            emotionWaitMs = emotionWaitMs,
            emotionBarrierFailureReason = emotionBarrierFailureReason,
          )
        },
      liveTurnHandler = { userText, voiceLabel, turnReqId, _, onReplyDelta, onTurnComplete, onTurnError ->
        executeLiveVoiceTurnInChat(
          userText = userText,
          voiceDurationLabel = voiceLabel,
          turnRequestId = turnReqId,
          onReplyDelta = onReplyDelta,
          onTurnComplete = onTurnComplete,
          onTurnError = onTurnError,
        )
      },
    )

    // 4. Restore persisted UI mode and auto-select latest matching chat on launch
    viewModelScope.launch {
      val persistedMode =
        runCatching { AppUiMode.fromKey(keyRepository.uiModeFlow.first()) }
          .getOrDefault(AppUiMode.RIVIA)
      _appUiMode.value = persistedMode
      val wantLab = persistedMode == AppUiMode.LABS
      _pendingNewChatIsLab.value = wantLab
      if (wantLab) {
        _pendingNewChatSettings.value = defaultRunSettings.value
      }

      val existing = dao.getAllChats().filter { it.isLabChat == wantLab }
      if (existing.isNotEmpty() && _activeChatId.value == null) {
        _activeChatId.value = existing.first().id
      }
    }
  }

  private fun initializeLayersAndLocks(application: Application) {
    val bundledResult = LayerConfigManager.readBundledAssetFiles(application)
    val rawFiles =
      bundledResult.getOrElse { err ->
        _layersLoadError.value = "Layers didn't load: ${err.message}"
        return
      }
    _bundledRawFiles.value = rawFiles

    // Validate bundled files immediately at startup
    val initialValidation = LayerConfigManager.loadAndValidateBundle(rawFiles, emptyList())
    initialValidation.onFailure { err ->
      _layersLoadError.value = "Layers didn't load: ${err.message}"
      return
    }
    val initialBundle = initialValidation.getOrThrow()
    _pendingRiviaProfile.value = initialBundle.engine.default_profile

    viewModelScope.launch {
      // Check lock state against bundled canonical SHA-256 on startup
      try {
        val existingLocks = dao.getLayerLocks().associateBy { it.layerKey }
        for (layerKey in listOf("persona", "style", "engine", "hard_rules")) {
          val bundledRaw = rawFiles[layerKey] ?: continue
          val bundledCanonicalSha =
            runCatching { LayerConfigManager.canonicalJsonSha256(bundledRaw) }.getOrDefault("")
          val lock = existingLocks[layerKey]
          if (lock != null && !lock.pendingCanonicalSha256.isNullOrBlank()) {
            if (bundledCanonicalSha == lock.pendingCanonicalSha256) {
              // Lock landed! Drop the phone draft and clear lock pending state
              dao.deleteLayerDraft(layerKey)
              dao.saveLayerLock(
                lock.copy(
                  pendingCanonicalSha256 = null,
                  lastSeenBundledSha256 = bundledCanonicalSha,
                  exportedJson = null,
                  mismatchWarning = null,
                )
              )
            } else if (
              !lock.lastSeenBundledSha256.isNullOrBlank() &&
                bundledCanonicalSha != lock.lastSeenBundledSha256
            ) {
              // Bundled file changed since lock was initiated, and does not match the locked SHA
              dao.saveLayerLock(
                lock.copy(
                  lastSeenBundledSha256 = bundledCanonicalSha,
                  mismatchWarning = "Lock didn't land: the built file differs",
                )
              )
            }
          } else if (lock == null) {
            dao.saveLayerLock(
              LayerLockEntity(
                layerKey = layerKey,
                pendingCanonicalSha256 = null,
                lastSeenBundledSha256 = bundledCanonicalSha,
                exportedJson = null,
                mismatchWarning = null,
              )
            )
          }
        }
      } catch (_: Throwable) {}

      // Ensure default LayerSettingsEntity rows exist for Rivia/Dev (id=1) and Labs sandbox (id=2)
      try {
        val h7Default =
          initialBundle.hardRules.rules.firstOrNull { it.id == "H7" }?.default ?: 2
        val existingSettings = dao.getLayerSettingsById(1)
        if (existingSettings == null) {
          dao.saveLayerSettings(
            LayerSettingsEntity(
              id = 1,
              spokenMaxSentences = h7Default.coerceIn(1, 4),
              h11WatchWordsJson = "[]",
            )
          )
        }
        val existingLabsSettings = dao.getLayerSettingsById(2)
        if (existingLabsSettings == null) {
          dao.saveLayerSettings(
            LayerSettingsEntity(
              id = 2,
              spokenMaxSentences =
                (existingSettings?.spokenMaxSentences ?: h7Default).coerceIn(1, 4),
              h11WatchWordsJson = existingSettings?.h11WatchWordsJson ?: "[]",
            )
          )
        }
      } catch (_: Throwable) {}

      // Observe drafts, locks, settings, test runs, and active UI mode so Rivia/Dev vs Labs sandbox stay isolated
      val settingsFlow =
        combine(
          dao.observeLayerSettingsById(1),
          dao.observeLayerSettingsById(2),
          _appUiMode,
        ) { riviaSettings, labsSettings, mode ->
          val effectiveSettings =
            if (mode == AppUiMode.LABS) (labsSettings ?: riviaSettings) else riviaSettings
          Pair(mode, effectiveSettings)
        }

      combine(
          dao.observeLayerDrafts(),
          dao.observeLayerLocks(),
          settingsFlow,
          dao.observeLayerTestRuns(),
        ) { allDrafts, allLocks, (mode, settingsEntity), testRuns ->
          val isLabs = mode == AppUiMode.LABS
          val scopedDrafts =
            if (isLabs) {
              allDrafts
                .filter { it.layerKey.startsWith("labs:") }
                .map { it.copy(layerKey = it.layerKey.removePrefix("labs:")) }
            } else {
              allDrafts.filter { !it.layerKey.startsWith("labs:") }
            }
          val scopedLocks =
            if (isLabs) {
              allLocks
                .filter { it.layerKey.startsWith("labs:") }
                .map { it.copy(layerKey = it.layerKey.removePrefix("labs:")) }
            } else {
              allLocks.filter { !it.layerKey.startsWith("labs:") }
            }
          recomputeLayerState(rawFiles, scopedDrafts, scopedLocks, settingsEntity, testRuns)
        }
        .collect {}
    }
  }

  private fun recomputeLayerState(
    rawFiles: Map<String, String>,
    drafts: List<LayerDraftEntity>,
    locks: List<LayerLockEntity>,
    settingsEntity: LayerSettingsEntity?,
    testRuns: List<LayerTestRunEntity>,
  ) {
    val validation = LayerConfigManager.loadAndValidateBundle(rawFiles, drafts)
    val bundle =
      validation.getOrElse { err ->
        _layersLoadError.value = "Layers didn't load: ${err.message}"
        _loadedLayersBundle.value = null
        return
      }

    _layersLoadError.value = null
    _loadedLayersBundle.value = bundle
    _hasAnyLayerDraft.value = bundle.draftKeys.isNotEmpty()

    // Update H2 write-tool guard in PluginRegistry
    val h2Active = bundle.hardRules.rules.any { it.id == "H2" && it.active }
    PluginRegistry.updateH2State(
      h2Active && "H2" in LayerConfigManager.SUPPORTED_CODE_RULE_HANDLERS
    )

    // Spoken max sentences from H7 rule bounds
    val h7Rule = bundle.hardRules.rules.firstOrNull { it.id == "H7" }
    val h7Default = h7Rule?.default ?: 2
    val h7Ceiling = h7Rule?.ceiling ?: 4
    val spokenMax = (settingsEntity?.spokenMaxSentences ?: h7Default).coerceIn(1, h7Ceiling)
    _spokenMaxSentences.value = spokenMax

    // Parse H11 watch words
    val watchWords = mutableListOf<String>()
    runCatching {
      val arr = JSONArray(settingsEntity?.h11WatchWordsJson ?: "[]")
      for (i in 0 until arr.length()) {
        val w = arr.optString(i).trim()
        if (w.isNotEmpty()) watchWords.add(w)
      }
    }
    _h11WatchWords.value = watchWords

    // Compute fingerprints for fast, balanced, deep, live
    val fps = mutableMapOf<String, String>()
    for (prof in LayerConfigManager.REQUIRED_PROFILES) {
      fps[prof] =
        LayerConfigManager.computeProfileFingerprint(
          profileName = prof,
          persona = bundle.persona,
          style = bundle.style,
          engine = bundle.engine,
          hardRules = bundle.hardRules,
          spokenMaxSentences = spokenMax,
        )
    }
    _profileFingerprints.value = fps

    // Compute test state per profile
    val testStates = mutableMapOf<String, ProfileTestState>()
    for (prof in LayerConfigManager.REQUIRED_PROFILES) {
      val fp = fps[prof] ?: ""
      testStates[prof] =
        LayerConfigManager.computeProfileTestState(
          profile = prof,
          currentFingerprint = fp,
          testsConfig = bundle.tests,
          testRuns = testRuns,
        )
    }
    _profileTestStates.value = testStates

    // Compute card metadata for persona, style, engine, hard_rules
    val lockMap = locks.associateBy { it.layerKey }
    val metas = mutableMapOf<String, LayerCardUiMeta>()
    for (layerKey in listOf("persona", "style", "engine", "hard_rules")) {
      val fileName = LayerConfigManager.LAYER_FILES[layerKey] ?: "$layerKey.json"
      val hasDraft = layerKey in bundle.draftKeys
      val (version, updated, bundledLocked) =
        when (layerKey) {
          "persona" ->
            Triple(bundle.persona.version, bundle.persona.updated, bundle.bundledPersona.locked)
          "style" ->
            Triple(bundle.style.version, bundle.style.updated, bundle.bundledStyle.locked)
          "engine" ->
            Triple(bundle.engine.version, bundle.engine.updated, bundle.bundledEngine.locked)
          "hard_rules" ->
            Triple(
              bundle.hardRules.version,
              bundle.hardRules.updated,
              bundle.bundledHardRules.locked,
            )
          else -> Triple(1, "", false)
        }
      val status =
        LayerConfigManager.computeLayerStatus(
          layerKey = layerKey,
          bundle = bundle,
          fingerprints = fps,
          testRuns = testRuns,
        )
      val lockRow = lockMap[layerKey]
      metas[layerKey] =
        LayerCardUiMeta(
          layerKey = layerKey,
          fileName = fileName,
          version = version,
          updated = updated,
          status = status,
          sourceSubtitle = if (hasDraft) "edited on phone" else "from build",
          hasDraft = hasDraft,
          bundledLocked = bundledLocked,
          lockPendingSha256 = lockRow?.pendingCanonicalSha256,
          lockMismatchWarning = lockRow?.mismatchWarning,
          exportedLockJson = lockRow?.exportedJson,
        )
    }
    _layerCardMetas.value = metas
  }

  // --- Layer Editing, Drafts, Locking, Tunables, and Test Logging ---

  fun savePersonaDraft(newText: String): Result<Unit> {
    val bundle = _loadedLayersBundle.value ?: return Result.failure(Exception("Layers not loaded"))
    val updated = bundle.persona.copy(locked = false, text = newText)
    val rawJson = LayerConfigManager.prettyJson.encodeToString(PersonaLayerConfig.serializer(), updated)
    return saveRawLayerDraft("persona", rawJson)
  }

  fun saveStyleDraft(newText: String, newVoice: String): Result<Unit> {
    val bundle = _loadedLayersBundle.value ?: return Result.failure(Exception("Layers not loaded"))
    val updated = bundle.style.copy(locked = false, text = newText, voice = newVoice)
    val rawJson = LayerConfigManager.prettyJson.encodeToString(StyleLayerConfig.serializer(), updated)
    return saveRawLayerDraft("style", rawJson)
  }

  fun saveEngineDraft(updatedEngine: EngineLayerConfig): Result<Unit> {
    val updated = updatedEngine.copy(locked = false)
    val rawJson = LayerConfigManager.prettyJson.encodeToString(EngineLayerConfig.serializer(), updated)
    return saveRawLayerDraft("engine", rawJson)
  }

  fun saveHardRulesDraft(updatedHardRules: HardRulesLayerConfig): Result<Unit> {
    val updated = updatedHardRules.copy(locked = false)
    val rawJson =
      LayerConfigManager.prettyJson.encodeToString(HardRulesLayerConfig.serializer(), updated)
    return saveRawLayerDraft("hard_rules", rawJson)
  }

  fun saveRawLayerDraft(layerKey: String, rawJson: String): Result<Unit> {
    val rawFiles = _bundledRawFiles.value
    // Validate before saving so invalid drafts are rejected
    val validation =
      when (layerKey) {
        "persona" -> LayerConfigManager.validateAndParsePersona(rawJson)
        "style" -> LayerConfigManager.validateAndParseStyle(rawJson)
        "engine" -> LayerConfigManager.validateAndParseEngine(rawJson)
        "hard_rules" -> LayerConfigManager.validateAndParseHardRules(rawJson)
        else -> Result.failure(IllegalArgumentException("Unknown layer $layerKey"))
      }
    validation.onFailure { return Result.failure(it) }

    // If engine changed, also verify tests.json against it
    if (layerKey == "engine") {
      val engineCfg = validation.getOrThrow() as EngineLayerConfig
      val testsRaw = rawFiles["tests"] ?: ""
      LayerConfigManager.validateAndParseTests(testsRaw, engineCfg).onFailure {
        return Result.failure(it)
      }
    }

    val dbLayerKey = scopedLayerKey(layerKey)
    viewModelScope.launch {
      dao.saveLayerDraft(
        LayerDraftEntity(
          layerKey = dbLayerKey,
          jsonContent = rawJson,
          updatedAt = System.currentTimeMillis(),
        )
      )
      // Clear any previous lock mismatch warning when user intentionally edits draft
      val existingLock = dao.getLayerLock(dbLayerKey)
      if (existingLock != null && existingLock.mismatchWarning != null) {
        dao.saveLayerLock(existingLock.copy(mismatchWarning = null))
      }
    }
    return Result.success(Unit)
  }

  fun startDraftFromLocked(layerKey: String) {
    val bundle = _loadedLayersBundle.value ?: return
    when (layerKey) {
      "persona" -> savePersonaDraft(bundle.persona.text)
      "style" -> saveStyleDraft(bundle.style.text, bundle.style.voice)
      "engine" -> saveEngineDraft(bundle.engine)
      "hard_rules" -> saveHardRulesDraft(bundle.hardRules)
    }
  }

  fun discardLayerDraft(layerKey: String) {
    val dbLayerKey = scopedLayerKey(layerKey)
    viewModelScope.launch {
      dao.deleteLayerDraft(dbLayerKey)
      val existingLock = dao.getLayerLock(dbLayerKey)
      if (existingLock != null) {
        dao.saveLayerLock(
          existingLock.copy(
            pendingCanonicalSha256 = null,
            exportedJson = null,
            mismatchWarning = null,
          )
        )
      }
      showNotice("Reverted $layerKey to the bundled file.")
    }
  }

  /**
   * Produces the Lock it export JSON for a layer ONLY when its status is Tested,
   * stores its canonical SHA-256 in layer_locks, and returns the exported JSON string.
   */
  suspend fun prepareLayerLockExport(layerKey: String): String? {
    val bundle = _loadedLayersBundle.value ?: return null
    val testRuns = dao.getLayerTestRuns()
    val exportedJson =
      LayerConfigManager.tryCreatePendingLock(
        dao = dao,
        layerKey = layerKey,
        bundle = bundle,
        fingerprints = _profileFingerprints.value,
        testRuns = testRuns,
      )
    if (exportedJson == null) {
      showError("Lock it requires the layer status to be Tested.")
    }
    return exportedJson
  }

  /**
   * Deletes the pending lock record for layerKey and leaves the phone draft untouched.
   */
  fun cancelPendingLayerLock(layerKey: String) {
    viewModelScope.launch {
      LayerConfigManager.cancelPendingLock(dao, layerKey)
      showNotice("Cancelled pending lock for $layerKey.")
    }
  }

  fun setSpokenMaxSentences(newLimit: Int) {
    val bundle = _loadedLayersBundle.value
    val ceiling = bundle?.hardRules?.rules?.firstOrNull { it.id == "H7" }?.ceiling ?: 4
    val bounded = newLimit.coerceIn(1, ceiling)
    _spokenMaxSentences.value = bounded
    val targetId = scopedSettingsId()
    viewModelScope.launch {
      val cur =
        dao.getLayerSettingsById(targetId)
          ?: LayerSettingsEntity(
            id = targetId,
            spokenMaxSentences = bounded,
            h11WatchWordsJson = "[]",
          )
      dao.saveLayerSettings(cur.copy(spokenMaxSentences = bounded))
    }
  }

  fun addH11WatchWord(word: String) {
    val clean = word.trim()
    if (clean.isEmpty()) return
    val current = _h11WatchWords.value.toMutableList()
    if (current.none { it.equals(clean, ignoreCase = true) }) {
      current.add(clean)
      saveWatchWordsList(current)
    }
  }

  fun removeH11WatchWord(word: String) {
    val current = _h11WatchWords.value.filterNot { it.equals(word, ignoreCase = true) }
    saveWatchWordsList(current)
  }

  private fun saveWatchWordsList(words: List<String>) {
    _h11WatchWords.value = words
    val targetId = scopedSettingsId()
    viewModelScope.launch {
      val arr = JSONArray()
      words.forEach { arr.put(it) }
      val cur =
        dao.getLayerSettingsById(targetId)
          ?: LayerSettingsEntity(
            id = targetId,
            spokenMaxSentences = _spokenMaxSentences.value,
            h11WatchWordsJson = "[]",
          )
      dao.saveLayerSettings(cur.copy(h11WatchWordsJson = arr.toString()))
    }
  }

  /**
   * Logs a test run in layer_test_runs on the profile's current fingerprint.
   * Clarification #1: "live_typed" always maps to engine profile "live".
   */
  fun logLayerTestRun(
    testId: String,
    profile: String,
    passed: Boolean,
    detailsJson: String,
  ) {
    val normProfile = LayerConfigManager.normalizeProfileKey(profile)
    val fp = _profileFingerprints.value[normProfile] ?: return
    viewModelScope.launch {
      dao.insertLayerTestRun(
        LayerTestRunEntity(
          id = UUID.randomUUID().toString(),
          testId = testId,
          profile = normProfile,
          fingerprint = fp,
          passed = passed,
          detailsJson = detailsJson,
          timestamp = System.currentTimeMillis(),
        )
      )
      showNotice(
        "Logged $testId on ${LayerConfigManager.displayProfileLabel(normProfile)} ($fp): ${if (passed) "Passed" else "Failed"}"
      )
    }
  }

  fun clearStatusBanner() {
    _statusBannerMessage.value = null
    audioPlayerManager.clearError()
  }

  fun setDrawerSearchQuery(query: String) {
    _drawerSearchQuery.value = query
  }

  /**
   * "+ New chat" creates a Rivia chat starting on engine.default_profile ("balanced").
   */
  fun startNewChat() {
    if (_appUiMode.value == AppUiMode.LABS) {
      startNewLabChat()
    } else {
      startNewRiviaChat()
    }
  }

  fun startNewRiviaChat() {
    stopStreaming()
    _activeChatId.value = null
    _pendingNewChatIsLab.value = false
    val defaultProf =
      if (_appUiMode.value == AppUiMode.RIVIA) {
        "live"
      } else {
        _loadedLayersBundle.value?.engine?.default_profile ?: "live"
      }
    _pendingRiviaProfile.value = defaultProf
    _pendingNewChatSettings.value = null
  }

  /**
   * "New Lab chat" creates a Lab chat with Lab defaults.
   */
  fun startNewLabChat() {
    stopStreaming()
    _activeChatId.value = null
    _pendingNewChatIsLab.value = true
    _pendingNewChatSettings.value = defaultRunSettings.value
  }

  fun selectRiviaProfile(profile: String) {
    val clean = profile.trim().lowercase()
    if (clean !in listOf("fast", "balanced", "deep", "live_typed", "live")) return
    _pendingRiviaProfile.value = clean
    viewModelScope.launch {
      val chatId = _activeChatId.value ?: return@launch
      val chat = dao.getChatById(chatId) ?: return@launch
      if (!chat.isLabChat) {
        dao.updateChat(chat.copy(riviaProfile = clean))
      }
    }
  }

  fun selectChat(chatId: String) {
    if (_activeChatId.value == chatId) return
    stopStreaming()
    _activeChatId.value = chatId
    _pendingNewChatSettings.value = null
  }

  fun renameChat(chatId: String, newTitle: String) {
    val trimmed = newTitle.trim()
    if (trimmed.isEmpty()) return
    viewModelScope.launch {
      val chat = dao.getChatById(chatId) ?: return@launch
      dao.updateChat(chat.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
    }
  }

  fun deleteChat(chatId: String) {
    viewModelScope.launch {
      if (_activeChatId.value == chatId) {
        stopStreaming()
      }
      dao.deleteChatById(chatId)
      if (_activeChatId.value == chatId) {
        val wantLab = _appUiMode.value == AppUiMode.LABS
        val remaining = dao.getAllChats().filter { it.isLabChat == wantLab }
        _activeChatId.value = remaining.firstOrNull()?.id
      }
    }
  }

  /**
   * Updates the RunSettings for the current Lab chat.
   */
  fun updateCurrentChatSettings(updated: RunSettings) {
    viewModelScope.launch {
      val chatId = _activeChatId.value
      if (chatId != null) {
        val chat = dao.getChatById(chatId)
        if (chat != null) {
          dao.updateChat(chat.copy(runSettingsJson = updated.toJsonString()))
          return@launch
        }
      }
      _pendingNewChatSettings.value = updated
    }
  }

  fun applyModePreset(preset: ModePreset) {
    val current = currentRunSettings.value
    val updated =
      current.copy(
        model = preset.model,
        thinkingLevel = preset.thinkingLevel,
        activeModeName = preset.name,
      )
    updateCurrentChatSettings(updated)
  }

  fun updateModePresetDefinition(updatedPreset: ModePreset) {
    viewModelScope.launch {
      val currentList = modePresets.value.toMutableList()
      val idx = currentList.indexOfFirst { it.name == updatedPreset.name }
      if (idx >= 0) {
        currentList[idx] = updatedPreset
        keyRepository.savePresetsJson(ModePreset.listToJson(currentList))
        if (isActiveChatLab.value && currentRunSettings.value.activeModeName == updatedPreset.name) {
          applyModePreset(updatedPreset)
        }
      }
    }
  }

  fun toggleAutoReadAloud() {
    val cur = currentRunSettings.value
    updateCurrentChatSettings(cur.copy(autoReadAloud = !cur.autoReadAloud))
  }

  fun saveCurrentSettingsAsDefault(explicitSettings: RunSettings? = null) {
    viewModelScope.launch {
      val cur = explicitSettings ?: currentRunSettings.value
      dao.saveDefaultSettings(DefaultSettingsEntity(id = 1, runSettingsJson = cur.toJsonString()))
      showNotice("Saved as Lab default for new Lab chats.")
    }
  }

  fun updateDefaultSettings(settings: RunSettings) {
    viewModelScope.launch {
      dao.saveDefaultSettings(
        DefaultSettingsEntity(id = 1, runSettingsJson = settings.toJsonString())
      )
      if (_activeChatId.value == null && _pendingNewChatIsLab.value) {
        _pendingNewChatSettings.value = settings
      }
    }
  }

  fun resetCurrentSettingsToDefault() {
    viewModelScope.launch {
      val def = dao.getDefaultSettings()?.let { RunSettings.fromJsonString(it.runSettingsJson) }
        ?: RunSettings()
      updateCurrentChatSettings(def)
      showNotice("Run settings reset to Lab defaults.")
    }
  }

  fun stopStreaming() {
    geminiClient.cancelActiveRequest()
    streamingJob?.cancel()
    streamingJob = null
    _isStreaming.value = false
  }

  /**
   * Sends a new typed chat turn.
   */
  fun sendUserMessage(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || _isStreaming.value) return

    // Fail closed if this is a Rivia chat and layers didn't load
    val willBeLab = activeChat.value?.isLabChat ?: _pendingNewChatIsLab.value
    if (!willBeLab && _layersLoadError.value != null) {
      showError(_layersLoadError.value!!)
      return
    }

    streamingJob =
      viewModelScope.launch {
        activeTurnCompletionJob?.join()
        val settings = currentRunSettings.value
        val chat = ensureActiveChatForFirstMessage(trimmed, settings)
        val priorMessages = dao.getMessagesForChat(chat.id)

        // Interaction ID before this turn
        val prevInteractionId =
          priorMessages
            .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
            ?.interactionId
            ?: chat.lastInteractionId

        val reqId = TextAudioTagProcessor.generateRequestId()
        val now = System.currentTimeMillis()
        val isRivia = !chat.isLabChat
        val bundle = _loadedLayersBundle.value
        val effectiveRiviaProfile =
          if (_appUiMode.value == AppUiMode.RIVIA) "live" else chat.riviaProfile
        val normProf = LayerConfigManager.normalizeProfileKey(effectiveRiviaProfile)
        val userModel =
          if (isRivia && bundle != null) {
            bundle.engine.profiles[normProf]?.model ?: settings.model
          } else {
            settings.model
          }
        val userTier =
          if (isRivia && bundle != null) bundle.engine.service_tier else settings.serviceTier

        val userMsg =
          MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chat.id,
            role = "user",
            content = trimmed,
            requestId = reqId,
            previousInteractionId = prevInteractionId,
            model = userModel,
            serviceTier = userTier,
            status = "completed",
            createdAt = now,
            chatKind = if (isRivia) "Rivia" else "Lab",
            profile = if (isRivia) normProf else "",
            channel =
              if (isRivia) LayerConfigManager.channelForProfile(effectiveRiviaProfile) else "text",
            fingerprint = if (isRivia) (_profileFingerprints.value[normProf] ?: "") else "",
          )
        dao.insertMessage(userMsg)

        executeAssistantTurn(
          chat = chat,
          settings = settings,
          channel = RiviaChannel.TYPED_CHAT,
          priorMessagesBeforeUserTurn = priorMessages,
          userText = trimmed,
          previousInteractionId = prevInteractionId,
          requestId = reqId,
        )
      }
  }

  /**
   * Section 2 & 3: Edit on my message resends from that point and replaces the later turns.
   * Branches from the interaction BEFORE the replaced turn, never the latest one!
   */
  fun editUserMessageAndResend(messageId: String, newText: String) {
    val trimmed = newText.trim()
    if (trimmed.isEmpty() || _isStreaming.value) return

    streamingJob =
      viewModelScope.launch {
        activeTurnCompletionJob?.join()
        val targetMsg = dao.getMessageById(messageId) ?: return@launch
        val chat = dao.getChatById(targetMsg.chatId) ?: return@launch
        if (!chat.isLabChat && _layersLoadError.value != null) {
          showError(_layersLoadError.value!!)
          return@launch
        }
        val settings = RunSettings.fromJsonString(chat.runSettingsJson)

        val allMessages = dao.getMessagesForChat(chat.id)
        val targetIdx = allMessages.indexOfFirst { it.id == messageId }
        if (targetIdx < 0) return@launch

        // Messages strictly before the edited user turn
        val messagesBeforeEditedTurn = allMessages.subList(0, targetIdx)
        // Branch interaction ID is the interaction strictly before this turn!
        val branchPreviousInteractionId =
          targetMsg.previousInteractionId
            ?: messagesBeforeEditedTurn
              .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
              ?.interactionId

        // Delete the edited message and all subsequent turns
        dao.deleteMessagesFromTimestamp(chat.id, targetMsg.createdAt)

        // Update chat title if editing the very first message
        val updatedChat =
          if (messagesBeforeEditedTurn.isEmpty()) {
            chat.copy(
              title = TextAudioTagProcessor.computeChatTitle(trimmed),
              lastInteractionId = branchPreviousInteractionId,
              updatedAt = System.currentTimeMillis(),
            )
          } else {
            chat.copy(
              lastInteractionId = branchPreviousInteractionId,
              updatedAt = System.currentTimeMillis(),
            )
          }
        dao.updateChat(updatedChat)

        val reqId = TextAudioTagProcessor.generateRequestId()
        val isRivia = !updatedChat.isLabChat
        val bundle = _loadedLayersBundle.value
        val effectiveRiviaProfile =
          if (_appUiMode.value == AppUiMode.RIVIA) "live" else updatedChat.riviaProfile
        val normProf = LayerConfigManager.normalizeProfileKey(effectiveRiviaProfile)
        val userModel =
          if (isRivia && bundle != null) {
            bundle.engine.profiles[normProf]?.model ?: settings.model
          } else {
            settings.model
          }
        val userTier =
          if (isRivia && bundle != null) bundle.engine.service_tier else settings.serviceTier

        val newUserMsg =
          MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chat.id,
            role = "user",
            content = trimmed,
            requestId = reqId,
            previousInteractionId = branchPreviousInteractionId,
            model = userModel,
            serviceTier = userTier,
            status = "completed",
            createdAt = System.currentTimeMillis(),
            chatKind = if (isRivia) "Rivia" else "Lab",
            profile = if (isRivia) normProf else "",
            channel =
              if (isRivia) LayerConfigManager.channelForProfile(effectiveRiviaProfile)
              else "text",
            fingerprint = if (isRivia) (_profileFingerprints.value[normProf] ?: "") else "",
          )
        dao.insertMessage(newUserMsg)

        executeAssistantTurn(
          chat = updatedChat,
          settings = settings,
          channel = RiviaChannel.TYPED_CHAT,
          priorMessagesBeforeUserTurn = messagesBeforeEditedTurn,
          userText = trimmed,
          previousInteractionId = branchPreviousInteractionId,
          requestId = reqId,
        )
      }
  }

  /**
   * Section 2 & 3: Regenerate (last reply only) branches from the interaction BEFORE the replaced turn!
   */
  fun regenerateLastReply() {
    if (_isStreaming.value) return
    val chatId = _activeChatId.value ?: return

    streamingJob =
      viewModelScope.launch {
        activeTurnCompletionJob?.join()
        val chat = dao.getChatById(chatId) ?: return@launch
        if (!chat.isLabChat && _layersLoadError.value != null) {
          showError(_layersLoadError.value!!)
          return@launch
        }
        val settings = RunSettings.fromJsonString(chat.runSettingsJson)
        val allMessages = dao.getMessagesForChat(chatId)
        val lastAssistantIdx = allMessages.indexOfLast { it.role == "assistant" }
        if (lastAssistantIdx < 0) return@launch

        val lastAssistantMsg = allMessages[lastAssistantIdx]
        val precedingUserIdx =
          allMessages.subList(0, lastAssistantIdx).indexOfLast { it.role == "user" }
        if (precedingUserIdx < 0) return@launch

        val userMsg = allMessages[precedingUserIdx]
        val priorMessagesBeforeUserTurn = allMessages.subList(0, precedingUserIdx)

        // Branch from the interaction BEFORE the replaced turn, never the latest one!
        val branchPreviousInteractionId =
          lastAssistantMsg.previousInteractionId
            ?: userMsg.previousInteractionId
            ?: priorMessagesBeforeUserTurn
              .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
              ?.interactionId

        // Delete the last assistant reply
        dao.deleteMessagesFromTimestamp(chatId, lastAssistantMsg.createdAt)
        val updatedChat = chat.copy(lastInteractionId = branchPreviousInteractionId)
        dao.updateChat(updatedChat)

        val newReqId = TextAudioTagProcessor.generateRequestId()
        executeAssistantTurn(
          chat = updatedChat,
          settings = settings,
          channel = RiviaChannel.TYPED_CHAT,
          priorMessagesBeforeUserTurn = priorMessagesBeforeUserTurn,
          userText = userMsg.content,
          previousInteractionId = branchPreviousInteractionId,
          requestId = newReqId,
        )
      }
  }

  private suspend fun ensureActiveChatForFirstMessage(
    firstUserMessage: String,
    settings: RunSettings,
  ): ChatEntity {
    val currentId = _activeChatId.value
    if (currentId != null) {
      val existing = dao.getChatById(currentId)
      if (existing != null) return existing
    }
    val newChatId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    val isLab = _pendingNewChatIsLab.value
    val defaultProf =
      if (_appUiMode.value == AppUiMode.RIVIA) {
        "live"
      } else {
        _loadedLayersBundle.value?.engine?.default_profile ?: "live"
      }
    val riviaProf =
      if (_appUiMode.value == AppUiMode.RIVIA) {
        "live"
      } else {
        _pendingRiviaProfile.value.ifBlank { defaultProf }
      }
    val chat =
      ChatEntity(
        id = newChatId,
        title = TextAudioTagProcessor.computeChatTitle(firstUserMessage),
        createdAt = now,
        updatedAt = now,
        runSettingsJson = settings.toJsonString(),
        lastInteractionId = null,
        isLabChat = isLab,
        riviaProfile = riviaProf,
        pendingH7Notice = null,
      )
    dao.insertChat(chat)
    _activeChatId.value = newChatId
    _pendingNewChatSettings.value = null
    return chat
  }

  private suspend fun executeAssistantTurn(
    chat: ChatEntity,
    settings: RunSettings,
    channel: RiviaChannel,
    priorMessagesBeforeUserTurn: List<MessageEntity>,
    userText: String,
    previousInteractionId: String?,
    requestId: String,
    onDeltaCallback: ((String) -> Unit)? = null,
    onCompleteCallback: (() -> Unit)? = null,
    onErrorCallback: ((String, Boolean) -> Unit)? = null,
  ) {
    val isRivia = !chat.isLabChat
    val bundle = _loadedLayersBundle.value
    if (isRivia && (bundle == null || _layersLoadError.value != null)) {
      val err = _layersLoadError.value ?: "Layers didn't load"
      showError(err)
      onErrorCallback?.invoke(err, false)
      return
    }

    _isStreaming.value = true
    val currentJob = kotlin.coroutines.coroutineContext[Job]
    activeTurnCompletionJob = currentJob
    val startTimeMs = System.currentTimeMillis()
    var firstTokenTimeMs: Long? = null
    var lastDbWriteMs = startTimeMs

    // Clarification #1 & #2:
    // - Live mode in a Rivia chat always uses the "live" profile, whatever the mode chip shows.
    // - In Rivia mode, the permanent mode is "live".
    // - "live_typed" always maps to engine profile "live" for fingerprint, test-state chip, Log a run, Chat info, Request log.
    val rawSelectedProfile =
      if (channel == RiviaChannel.LIVE_VOICE || _appUiMode.value == AppUiMode.RIVIA) {
        "live"
      } else {
        chat.riviaProfile
      }
    val normalizedProfile = LayerConfigManager.normalizeProfileKey(rawSelectedProfile)
    val layerChannel =
      if (channel == RiviaChannel.LIVE_VOICE || rawSelectedProfile == "live_typed" || normalizedProfile == "live") {
        "voice"
      } else {
        "text"
      }
    val turnFingerprint =
      if (isRivia && bundle != null) {
        _profileFingerprints.value[normalizedProfile]
          ?: LayerConfigManager.computeProfileFingerprint(
            profileName = normalizedProfile,
            persona = bundle.persona,
            style = bundle.style,
            engine = bundle.engine,
            hardRules = bundle.hardRules,
            spokenMaxSentences = _spokenMaxSentences.value,
          )
      } else {
        ""
      }

    val chosenModel =
      if (isRivia && bundle != null) {
        bundle.engine.profiles[normalizedProfile]?.model ?: "gemini-3.8-flash"
      } else {
        if (channel == RiviaChannel.LIVE_VOICE) settings.liveModel else settings.model
      }

    val chosenServiceTier =
      if (isRivia && bundle != null) bundle.engine.service_tier else settings.serviceTier

    val isBackground =
      if (isRivia) {
        false
      } else {
        channel != RiviaChannel.LIVE_VOICE && settings.store && settings.background
      }

    val assistantMsgId = UUID.randomUUID().toString()
    var assistantMsg =
      MessageEntity(
        id = assistantMsgId,
        chatId = chat.id,
        role = "assistant",
        content = "",
        thoughtSummary = "",
        requestId = requestId,
        interactionId = null,
        previousInteractionId = previousInteractionId,
        model = chosenModel,
        serviceTier = chosenServiceTier,
        status = if (isBackground) "polling" else "streaming",
        createdAt = startTimeMs + 1L,
        chatKind = if (isRivia) "Rivia" else "Lab",
        profile = if (isRivia) normalizedProfile else "",
        channel = if (isRivia) layerChannel else (if (channel == RiviaChannel.LIVE_VOICE) "voice" else "text"),
        fingerprint = turnFingerprint,
      )
    dao.insertMessage(assistantMsg)
    _streamingMessageOverlay.value = assistantMsg

    val apiKey = keyRepository.getGeminiKey()

    // Read current pendingH7Notice from chat before starting turn
    val latestChatAtStart = dao.getChatById(chat.id) ?: chat
    val pendingH7NoticeToSend =
      if (isRivia) latestChatAtStart.pendingH7Notice else null

    val customBodyBuilder: ((JSONArray, String?, Boolean) -> JSONObject)? =
      if (isRivia && bundle != null) {
        { inputSteps, prevId, stream ->
          val sysInstruction =
            LayerConfigManager.assembleSystemInstruction(
              channel = layerChannel,
              persona = bundle.persona,
              style = bundle.style,
              hardRules = bundle.hardRules,
              spokenMaxSentences = _spokenMaxSentences.value,
              pendingH7Notice = pendingH7NoticeToSend,
            )
          geminiClient.buildRiviaRequestBodyJson(
            engine = bundle.engine,
            profileName = normalizedProfile,
            assembledSystemInstruction = sysInstruction,
            inputSteps = inputSteps,
            previousInteractionId = prevId,
            stream = stream,
          )
        }
      } else {
        null
      }

    // In Labs mode, if the user edited Labs sandbox Layers and hasn't overridden systemInstruction
    // in RunSettings, assemble the system instruction from the Labs sandbox bundle so Labs chats
    // test both Labs Layers and Labs RunSettings seamlessly.
    val effectiveSettings =
      if (!isRivia && bundle != null && settings.systemInstruction.isBlank() && _hasAnyLayerDraft.value) {
        val labsLayerSysInstruction =
          LayerConfigManager.assembleSystemInstruction(
            channel = layerChannel,
            persona = bundle.persona,
            style = bundle.style,
            hardRules = bundle.hardRules,
            spokenMaxSentences = _spokenMaxSentences.value,
            pendingH7Notice = null,
          )
        settings.copy(systemInstruction = labsLayerSysInstruction)
      } else {
        settings
      }

    try {
      geminiClient.sendTurn(
        apiKey = apiKey,
        settings = effectiveSettings,
        channel = channel,
        priorMessages = priorMessagesBeforeUserTurn,
        newUserText = userText,
        previousInteractionId = previousInteractionId,
        customBodyBuilder = customBodyBuilder,
        customStore = if (isRivia && bundle != null) bundle.engine.store else null,
        customServiceTier = if (isRivia && bundle != null) bundle.engine.service_tier else null,
      ) { event ->
        when (event) {
          is GeminiStreamEvent.RequestDispatched -> {
            if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
              updateTurnDispatchTimings(
                requestId = requestId,
                requestSentMs = event.requestSentEpochMs,
                systemInstructionChars = event.systemInstructionChars,
              )
            }
          }
          is GeminiStreamEvent.FirstByteReceived -> {
            if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
              updateTurnDispatchTimings(
                requestId = requestId,
                firstByteMs = event.firstByteEpochMs,
              )
            }
          }
          is GeminiStreamEvent.InteractionCreated -> {
            // Clarification #4: Clear pendingH7Notice only after the next turn's interaction.created arrives,
            // not when it's sent, so a failed send keeps it.
            if (isRivia && !pendingH7NoticeToSend.isNullOrBlank()) {
              val curChat = dao.getChatById(chat.id)
              if (curChat != null && curChat.pendingH7Notice != null) {
                dao.updateChat(curChat.copy(pendingH7Notice = null))
              }
            }

            assistantMsg =
              assistantMsg.copy(
                interactionId = event.interactionId,
                serviceTier = event.serviceTierHeader ?: assistantMsg.serviceTier,
              )
            _streamingMessageOverlay.value = assistantMsg
            dao.updateStreamingProgress(
              id = assistantMsg.id,
              content = assistantMsg.content,
              thoughtSummary = assistantMsg.thoughtSummary,
              interactionId = assistantMsg.interactionId,
              serviceTier = assistantMsg.serviceTier,
              ttftMs = assistantMsg.ttftMs,
              status = assistantMsg.status,
            )
            lastDbWriteMs = System.currentTimeMillis()
          }
          is GeminiStreamEvent.TextDelta -> {
            val now = System.currentTimeMillis()
            if (firstTokenTimeMs == null) {
              firstTokenTimeMs = now - startTimeMs
              if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
                updateTurnDispatchTimings(requestId, replyFirstTokenMs = now)
              }
            }
            assistantMsg =
              assistantMsg.copy(
                content = assistantMsg.content + event.text,
                ttftMs = firstTokenTimeMs ?: 0L,
              )
            _streamingMessageOverlay.value = assistantMsg
            if (now - lastDbWriteMs >= 300L) {
              dao.updateStreamingProgress(
                id = assistantMsg.id,
                content = assistantMsg.content,
                thoughtSummary = assistantMsg.thoughtSummary,
                interactionId = assistantMsg.interactionId,
                serviceTier = assistantMsg.serviceTier,
                ttftMs = assistantMsg.ttftMs,
                status = assistantMsg.status,
              )
              lastDbWriteMs = now
            }
            onDeltaCallback?.invoke(event.text)
          }
          is GeminiStreamEvent.ThoughtSummaryDelta -> {
            val now = System.currentTimeMillis()
            if (firstTokenTimeMs == null) {
              firstTokenTimeMs = now - startTimeMs
            }
            assistantMsg =
              assistantMsg.copy(
                thoughtSummary = assistantMsg.thoughtSummary + event.thoughtText,
                ttftMs = firstTokenTimeMs ?: 0L,
              )
            _streamingMessageOverlay.value = assistantMsg
            if (now - lastDbWriteMs >= 300L) {
              dao.updateStreamingProgress(
                id = assistantMsg.id,
                content = assistantMsg.content,
                thoughtSummary = assistantMsg.thoughtSummary,
                interactionId = assistantMsg.interactionId,
                serviceTier = assistantMsg.serviceTier,
                ttftMs = assistantMsg.ttftMs,
                status = assistantMsg.status,
              )
              lastDbWriteMs = now
            }
          }
          is GeminiStreamEvent.ReplaceReplyText -> {
            val now = System.currentTimeMillis()
            if (firstTokenTimeMs == null) {
              firstTokenTimeMs = now - startTimeMs
              if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
                updateTurnDispatchTimings(requestId, replyFirstTokenMs = now)
              }
            }
            assistantMsg =
              assistantMsg.copy(
                content = event.fullText,
                thoughtSummary =
                  event.fullThought.ifBlank { assistantMsg.thoughtSummary },
                ttftMs = firstTokenTimeMs ?: 0L,
              )
            _streamingMessageOverlay.value = assistantMsg
            dao.updateStreamingProgress(
              id = assistantMsg.id,
              content = assistantMsg.content,
              thoughtSummary = assistantMsg.thoughtSummary,
              interactionId = assistantMsg.interactionId,
              serviceTier = assistantMsg.serviceTier,
              ttftMs = assistantMsg.ttftMs,
              status = assistantMsg.status,
            )
            lastDbWriteMs = now
          }
          is GeminiStreamEvent.StatusUpdate -> {
            assistantMsg = assistantMsg.copy(status = "polling (${event.status})")
            _streamingMessageOverlay.value = assistantMsg
            val now = System.currentTimeMillis()
            if (now - lastDbWriteMs >= 300L) {
              dao.updateStreamingProgress(
                id = assistantMsg.id,
                content = assistantMsg.content,
                thoughtSummary = assistantMsg.thoughtSummary,
                interactionId = assistantMsg.interactionId,
                serviceTier = assistantMsg.serviceTier,
                ttftMs = assistantMsg.ttftMs,
                status = assistantMsg.status,
              )
              lastDbWriteMs = now
            }
          }
          is GeminiStreamEvent.StepCompleted -> {
            // Raw steps are saved in Completed
          }
          is GeminiStreamEvent.Completed -> {
            val totalMs = System.currentTimeMillis() - startTimeMs
            val finalInteractionId = event.interactionId ?: assistantMsg.interactionId
            val fullUntrimmedReply = assistantMsg.content
            val ruleEventsList = mutableListOf<String>()

            // Section 6: H9 (no silent failure) on every user-started turn (Rivia and Lab)
            val isReplyEmpty =
              fullUntrimmedReply.isBlank() ||
                (event.completionStatus.equals("incomplete", ignoreCase = true) &&
                  fullUntrimmedReply.isBlank())

            if (isReplyEmpty) {
              val configuredMaxTokens =
                if (isRivia && bundle != null) bundle.engine.max_output_tokens
                else settings.maxOutputTokens
              val h9Notice =
                LayerConfigManager.formatH9EmptyReplyReason(
                  finishReason = event.finishReason,
                  safetyBlockReason = event.safetyBlockReason,
                  completionStatus = event.completionStatus,
                  thoughtTokens = event.thoughtTokens,
                  outputTokens = event.outputTokens,
                  configuredMaxOutputTokens = configuredMaxTokens,
                )
              val h9Active =
                isRivia &&
                  (bundle?.hardRules?.rules?.any { it.id == "H9" && it.active } ?: false)

              if (h9Active) {
                ruleEventsList.add("H9: $h9Notice")
                dao.insertRuleEvent(
                  RuleEventEntity(
                    chatId = chat.id,
                    requestId = requestId,
                    ruleId = "H9",
                    eventType = layerChannel,
                    detail = h9Notice,
                    timestamp = System.currentTimeMillis(),
                  )
                )
              }

              val ruleEventsSummary =
                ruleEventsList.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: ""

              assistantMsg =
                assistantMsg.copy(
                  interactionId = finalInteractionId,
                  serviceTier = event.serviceTier,
                  inputTokens = event.inputTokens,
                  outputTokens = event.outputTokens,
                  thoughtTokens = event.thoughtTokens,
                  ttftMs = firstTokenTimeMs ?: totalMs,
                  totalTimeMs = totalMs,
                  status = "error",
                  errorMessage = h9Notice,
                  rawStepsJson = event.rawSteps.toString(),
                  ruleEventsSummary = ruleEventsSummary,
                )
              dao.updateMessage(assistantMsg)
              _streamingMessageOverlay.value = null

              if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
                updateTurnDispatchCompletion(
                  requestId = requestId,
                  replyText = null,
                  status = "error",
                  error = h9Notice,
                )
              }

              dao.logRequestAndPrune(
                RequestLogEntity(
                  timestamp = System.currentTimeMillis(),
                  requestId = requestId,
                  model = chosenModel,
                  status = "empty",
                  serviceTier = event.serviceTier,
                  inputTokens = event.inputTokens,
                  outputTokens = event.outputTokens,
                  thoughtTokens = event.thoughtTokens,
                  latencyMs = totalMs,
                  error = h9Notice,
                  chatKind = if (isRivia) "Rivia" else "Lab",
                  profile = if (isRivia) normalizedProfile else "",
                  channel = if (isRivia) layerChannel else (if (channel == RiviaChannel.LIVE_VOICE) "voice" else "text"),
                  fingerprint = turnFingerprint,
                  ruleEvents = ruleEventsSummary,
                )
              )
              onErrorCallback?.invoke(h9Notice, h9Active)
              return@sendTurn
            }

            // Section 6: H7 spoken sentence limit enforcement on Rivia voice channel (Live and Live (typed))
            var displayedContent = fullUntrimmedReply
            var trimmedFrom: Int? = null
            var trimmedTo: Int? = null
            var newPendingH7Notice: String? = null

            val h7Active =
              isRivia &&
                layerChannel == "voice" &&
                (bundle?.hardRules?.rules?.any { it.id == "H7" && it.active } ?: false)

            if (h7Active) {
              val limit = _spokenMaxSentences.value
              val sentences = TextAudioTagProcessor.splitIntoSentences(fullUntrimmedReply)
              if (sentences.size > limit) {
                val keptText = sentences.take(limit).joinToString(" ")
                displayedContent = keptText
                trimmedFrom = sentences.size
                trimmedTo = limit
                val h7Detail = "H7: trimmed ${sentences.size} → $limit"
                ruleEventsList.add(h7Detail)
                newPendingH7Notice =
                  "Your last spoken reply was cut after $limit sentences. Jordan heard only: «$keptText»"
                dao.insertRuleEvent(
                  RuleEventEntity(
                    chatId = chat.id,
                    requestId = requestId,
                    ruleId = "H7",
                    eventType = "voice",
                    detail = "trimmed ${sentences.size} → $limit",
                    timestamp = System.currentTimeMillis(),
                  )
                )
              }
            }

            // Section 6: H11 watch words audit on Rivia replies
            val h11Active =
              isRivia &&
                (bundle?.hardRules?.rules?.any { it.id == "H11" && it.active } ?: false)
            if (h11Active && _h11WatchWords.value.isNotEmpty()) {
              val matched =
                _h11WatchWords.value.filter { word ->
                  fullUntrimmedReply.contains(word, ignoreCase = true)
                }
              if (matched.isNotEmpty()) {
                ruleEventsList.add("H11 review")
                dao.insertRuleEvent(
                  RuleEventEntity(
                    chatId = chat.id,
                    requestId = requestId,
                    ruleId = "H11 review",
                    eventType = layerChannel,
                    detail = "Matched watch words: ${matched.joinToString(", ")}",
                    timestamp = System.currentTimeMillis(),
                  )
                )
              }
            }

            val ruleEventsSummary =
              ruleEventsList.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: ""

            // Preserve full untrimmed reply inside rawStepsJson so Details > Raw Steps always has the full reply
            val rawStepsArr = event.rawSteps
            if (trimmedFrom != null) {
              rawStepsArr.put(
                JSONObject().apply {
                  put("type", "h7_full_untrimmed_reply")
                  put("full_text", fullUntrimmedReply)
                  put("displayed_text", displayedContent)
                  put("trimmed_from_sentences", trimmedFrom)
                  put("trimmed_to_sentences", trimmedTo)
                }
              )
            }

            assistantMsg =
              assistantMsg.copy(
                content = displayedContent,
                interactionId = finalInteractionId,
                serviceTier = event.serviceTier,
                inputTokens = event.inputTokens,
                outputTokens = event.outputTokens,
                thoughtTokens = event.thoughtTokens,
                ttftMs = firstTokenTimeMs ?: totalMs,
                totalTimeMs = totalMs,
                status = "completed",
                rawStepsJson = rawStepsArr.toString(),
                ruleEventsSummary = ruleEventsSummary,
                h7TrimmedFrom = trimmedFrom,
                h7TrimmedTo = trimmedTo,
              )
            dao.updateMessage(assistantMsg)
            _streamingMessageOverlay.value = null

            if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
              updateTurnDispatchCompletion(
                requestId = requestId,
                replyText = displayedContent,
                status = "completed",
                error = null,
              )
            }

            val latestChat = dao.getChatById(chat.id)
            if (latestChat != null) {
              dao.updateChat(
                latestChat.copy(
                  lastInteractionId = finalInteractionId,
                  updatedAt = System.currentTimeMillis(),
                  pendingH7Notice = newPendingH7Notice ?: latestChat.pendingH7Notice,
                )
              )
            }

            // Log request to Request Log
            dao.logRequestAndPrune(
              RequestLogEntity(
                timestamp = System.currentTimeMillis(),
                requestId = requestId,
                model = chosenModel,
                status = "ok",
                serviceTier = event.serviceTier,
                inputTokens = event.inputTokens,
                outputTokens = event.outputTokens,
                thoughtTokens = event.thoughtTokens,
                latencyMs = totalMs,
                error = null,
                chatKind = if (isRivia) "Rivia" else "Lab",
                profile = if (isRivia) normalizedProfile else "",
                channel = if (isRivia) layerChannel else (if (channel == RiviaChannel.LIVE_VOICE) "voice" else "text"),
                fingerprint = turnFingerprint,
                ruleEvents = ruleEventsSummary,
              )
            )

            // If auto read-aloud is enabled in typed chat, speak the reply
            if (
              channel == RiviaChannel.TYPED_CHAT &&
                settings.autoReadAloud &&
                assistantMsg.content.isNotBlank()
            ) {
              audioPlayerManager.speakMarkdownMessage(
                messageId = assistantMsg.id,
                rawMarkdown = assistantMsg.content,
                isLiveChannel = rawSelectedProfile == "live_typed",
              )
            }
            onCompleteCallback?.invoke()
          }
          is GeminiStreamEvent.Error -> {
            val totalMs = System.currentTimeMillis() - startTimeMs
            val redactedErr = keyRepository.redact(event.message)
            val h9Active =
              isRivia &&
                (bundle?.hardRules?.rules?.any { it.id == "H9" && it.active } ?: false)
            val ruleEventsSummary = if (h9Active) "H9" else ""
            if (h9Active) {
              dao.insertRuleEvent(
                RuleEventEntity(
                  chatId = chat.id,
                  requestId = requestId,
                  ruleId = "H9",
                  eventType = layerChannel,
                  detail = redactedErr,
                  timestamp = System.currentTimeMillis(),
                )
              )
            }

            assistantMsg =
              assistantMsg.copy(
                status = "error",
                errorMessage = redactedErr,
                totalTimeMs = totalMs,
                ruleEventsSummary = ruleEventsSummary,
              )
            dao.updateMessage(assistantMsg)
            _streamingMessageOverlay.value = null

            if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
              updateTurnDispatchCompletion(
                requestId = requestId,
                replyText = null,
                status = "error",
                error = redactedErr,
              )
            }

            dao.logRequestAndPrune(
              RequestLogEntity(
                timestamp = System.currentTimeMillis(),
                requestId = requestId,
                model = chosenModel,
                status = "error",
                serviceTier = assistantMsg.serviceTier,
                inputTokens = assistantMsg.inputTokens,
                outputTokens = assistantMsg.outputTokens,
                thoughtTokens = assistantMsg.thoughtTokens,
                latencyMs = totalMs,
                error = redactedErr,
                chatKind = if (isRivia) "Rivia" else "Lab",
                profile = if (isRivia) normalizedProfile else "",
                channel = if (isRivia) layerChannel else (if (channel == RiviaChannel.LIVE_VOICE) "voice" else "text"),
                fingerprint = turnFingerprint,
                ruleEvents = ruleEventsSummary,
              )
            )
            onErrorCallback?.invoke(redactedErr, h9Active)
          }
        }
      }
    } catch (e: CancellationException) {
      val totalMs = System.currentTimeMillis() - startTimeMs
      assistantMsg =
        if (assistantMsg.content.isBlank()) {
          assistantMsg.copy(
            status = "error",
            errorMessage = "Request stopped before reply arrived (${totalMs / 1000}s).",
            totalTimeMs = totalMs,
          )
        } else {
          assistantMsg.copy(
            status = "completed",
            totalTimeMs = totalMs,
          )
        }
      runCatching { dao.updateMessage(assistantMsg) }
      _streamingMessageOverlay.value = null
      throw e
    } catch (e: Throwable) {
      val totalMs = System.currentTimeMillis() - startTimeMs
      val redactedErr =
        keyRepository.redact(e.message ?: e.javaClass.simpleName ?: "Connection error")
      val h9Active =
        isRivia &&
          (bundle?.hardRules?.rules?.any { it.id == "H9" && it.active } ?: false)
      val ruleEventsSummary = if (h9Active) "H9" else ""
      if (h9Active) {
        runCatching {
          dao.insertRuleEvent(
            RuleEventEntity(
              chatId = chat.id,
              requestId = requestId,
              ruleId = "H9",
              eventType = layerChannel,
              detail = redactedErr,
              timestamp = System.currentTimeMillis(),
            )
          )
        }
      }
      assistantMsg =
        assistantMsg.copy(
          status = "error",
          errorMessage = redactedErr,
          totalTimeMs = totalMs,
          ruleEventsSummary = ruleEventsSummary,
        )
      runCatching { dao.updateMessage(assistantMsg) }
      _streamingMessageOverlay.value = null
      if (isRivia && channel == RiviaChannel.LIVE_VOICE) {
        runCatching {
          updateTurnDispatchCompletion(
            requestId = requestId,
            replyText = null,
            status = "error",
            error = redactedErr,
          )
        }
      }
      runCatching {
        dao.logRequestAndPrune(
          RequestLogEntity(
            timestamp = System.currentTimeMillis(),
            requestId = requestId,
            model = chosenModel,
            status = "error",
            serviceTier = assistantMsg.serviceTier,
            inputTokens = assistantMsg.inputTokens,
            outputTokens = assistantMsg.outputTokens,
            thoughtTokens = assistantMsg.thoughtTokens,
            latencyMs = totalMs,
            error = redactedErr,
            chatKind = if (isRivia) "Rivia" else "Lab",
            profile = if (isRivia) normalizedProfile else "",
            channel = if (isRivia) layerChannel else (if (channel == RiviaChannel.LIVE_VOICE) "voice" else "text"),
            fingerprint = turnFingerprint,
            ruleEvents = ruleEventsSummary,
          )
        )
      }
      onErrorCallback?.invoke(redactedErr, h9Active)
    } finally {
      _streamingMessageOverlay.value = null
      _isStreaming.value = false
    }
  }

  private suspend fun updateTurnDispatchTimings(
    requestId: String,
    vadEndMs: Long? = null,
    transcriptReadyMs: Long? = null,
    emotionReadyMs: Long? = null,
    emotionTimeoutMs: Long? = null,
    requestSentMs: Long? = null,
    firstByteMs: Long? = null,
    systemInstructionChars: Int? = null,
    replyFirstTokenMs: Long? = null,
    ttsFirstAudioMs: Long? = null,
    playbackDoneMs: Long? = null,
  ) {
    runCatching {
      val row = dao.getTurnDispatchLogByRequestId(requestId) ?: return
      val updatedTimings =
        TurnPayloadBuilder.mergeTimingsJson(
          existingTimingsJson = row.timingsJson,
          vadEndMs = vadEndMs,
          transcriptReadyMs = transcriptReadyMs,
          emotionReadyMs = emotionReadyMs,
          emotionTimeoutMs = emotionTimeoutMs,
          requestSentMs = requestSentMs,
          firstByteMs = firstByteMs,
          systemInstructionChars = systemInstructionChars,
          replyFirstTokenMs = replyFirstTokenMs,
          ttsFirstAudioMs = ttsFirstAudioMs,
          playbackDoneMs = playbackDoneMs,
        )
      if (updatedTimings != row.timingsJson) {
        dao.updateTurnDispatchLog(row.copy(timingsJson = updatedTimings))
      }
    }
  }

  private suspend fun updateTurnDispatchCompletion(
    requestId: String,
    replyText: String?,
    status: String,
    error: String?,
  ) {
    runCatching {
      val row = dao.getTurnDispatchLogByRequestId(requestId) ?: return
      dao.updateTurnDispatchLog(
        row.copy(
          replyText = replyText ?: row.replyText,
          status = status,
          error = error,
        )
      )
    }
  }

  /**
   * Pass 2 & Step 1: Logs mic-sharing capture metrics and emotion scores to Room,
   * optionally saves the clip as a 16 kHz mono WAV (keeping the last 10 clips) strictly outside
   * the 300 ms barrier and after emotion score persistence.
   * Never blocks Live mode; any failure is caught, logged, and shown.
   */
  private fun handleLiveMicCaptureAndEmotionLogging(
    requestId: String,
    recognizerOk: Boolean,
    recognizerErrorCode: Int?,
    recognizedText: String,
    diagnostics: TurnRecognizerDiagnostics,
    captureResult: MicCaptureResult,
    precomputedEmotionResult: Result<com.example.audio.EmotionPrediction>? = null,
    emotionWaitMs: Long? = null,
    emotionBarrierFailureReason: String? = null,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val app = getApplication<Application>()
        val saveWavs = labMicTestPreferences.value.saveWavClips
        persistVoiceEmotionTurnLog(
          dao = dao,
          requestId = requestId,
          recognizerOk = recognizerOk,
          recognizerErrorCode = recognizerErrorCode,
          recognizedText = recognizedText,
          diagnostics = diagnostics,
          captureResult = captureResult,
          precomputedEmotionResult = precomputedEmotionResult,
          emotionWaitMs = emotionWaitMs,
          emotionBarrierFailureReason = emotionBarrierFailureReason,
          fallbackScoreBlock = { cap ->
            emotionModelManager.scoreTurnWithWindows(
              rawSamples16kMono = cap.samples16kMono,
              preScoredWindows = cap.preScoredWindows,
              remainderSamples = cap.remainderWindowSamples,
            )
          },
          saveWavClipBlock = {
            if (saveWavs && captureResult.pcm16Bytes.isNotEmpty()) {
              MicSharingRecorder.saveWavClipAndPrune(
                context = app,
                requestId = requestId,
                pcm16LeBytes = captureResult.pcm16Bytes,
              )
            } else {
              null
            }
          },
        )
      } catch (_: Throwable) {
        // Never crash or disrupt Live mode
      }
    }
  }

  /**
   * Executes a Live voice turn and saves both user ("voice · m:ss") and Rivia turns in the chat.
   * Step 2: For Rivia live voice turns, builds `payload_json` via [TurnPayloadBuilder] and inserts
   * the `turn_dispatch_logs` row BEFORE the Gemini request starts.
   */
  private suspend fun executeLiveVoiceTurnInChat(
    userText: String,
    voiceDurationLabel: String,
    turnRequestId: String,
    onReplyDelta: (String) -> Unit,
    onTurnComplete: () -> Unit,
    onTurnError: (String, Boolean) -> Unit,
  ) {
    activeTurnCompletionJob?.join()
    val settings = currentRunSettings.value
    val chat = ensureActiveChatForFirstMessage(userText, settings)
    val isRivia = !chat.isLabChat
    val bundle = _loadedLayersBundle.value
    if (isRivia && (bundle == null || _layersLoadError.value != null)) {
      val err = _layersLoadError.value ?: "Layers didn't load"
      onTurnError(err, false)
      return
    }

    val priorMessages = dao.getMessagesForChat(chat.id)
    val prevInteractionId =
      priorMessages
        .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
        ?.interactionId
        ?: chat.lastInteractionId

    val reqId = turnRequestId.ifBlank { TextAudioTagProcessor.generateRequestId() }
    val turnCreatedAtMs = System.currentTimeMillis()
    val liveModel =
      if (isRivia && bundle != null) {
        bundle.engine.profiles["live"]?.model ?: settings.liveModel
      } else {
        settings.liveModel
      }
    val liveTier =
      if (isRivia && bundle != null) bundle.engine.service_tier else settings.serviceTier

    val userMsg =
      MessageEntity(
        id = UUID.randomUUID().toString(),
        chatId = chat.id,
        role = "user",
        content = userText,
        requestId = reqId,
        previousInteractionId = prevInteractionId,
        model = liveModel,
        serviceTier = liveTier,
        status = "completed",
        voiceLabel = voiceDurationLabel,
        createdAt = turnCreatedAtMs,
        chatKind = if (isRivia) "Rivia" else "Lab",
        profile = if (isRivia) "live" else "",
        channel = "voice",
        fingerprint = if (isRivia) (_profileFingerprints.value["live"] ?: "") else "",
      )
    dao.insertMessage(userMsg)

    // Step 2: Insert turn_dispatch_logs row with payload_json BEFORE the Gemini request starts (Rivia live voice turns only)
    if (isRivia) {
      runCatching {
        val dispatchCtx = LiveSessionManager.getTurnDispatchContext(reqId)
        val barrierRes =
          dispatchCtx?.barrierResult
            ?: LiveSessionManager.lastTurnEmotionBarrierResult.value?.takeIf {
              it.turnRequestId == reqId
            }
        val existingEmotionRow = dao.getVoiceEmotionLogByRequestId(reqId)
        val transcriptSource =
          dispatchCtx?.transcriptSource?.takeIf { it.isNotBlank() }
            ?: existingEmotionRow?.transcriptSource?.takeIf { it.isNotBlank() }
            ?: "onResults"
        val isModeATurn =
          barrierRes?.captureResult?.mode == "A" ||
            barrierRes?.failureReason == LiveSessionManager.EMOTION_REASON_NO_SCORES ||
            existingEmotionRow?.mode == "A"
        val acoustic =
          if (isModeATurn) {
            null
          } else if (barrierRes?.prediction != null) {
            val speechSec =
              barrierRes.captureResult?.speechDurationSec
                ?: existingEmotionRow?.speechDurationSec
                ?: 0f
            TurnAcousticData.fromPrediction(
              prediction = barrierRes.prediction,
              transcript = userText,
              speechDurationSec = speechSec,
            )
          } else {
            TurnAcousticData.fromLogOrNull(existingEmotionRow)
          }

        val resetAtMs = labMicTestPreferences.value.emotionBaselineResetAt
        val baseline =
          queryAndComputeEmotionBaseline(
            dao = dao,
            resetAtMs = resetAtMs,
            nowMs = turnCreatedAtMs,
            excludeRequestId = reqId,
            excludeAtOrAfterNow = true,
          )
        val payloadJson =
          TurnPayloadBuilder.buildPayloadJson(
            timestampMs = turnCreatedAtMs,
            requestId = reqId,
            transcript = userText,
            transcriptSource = transcriptSource,
            acoustic = acoustic,
            baseline = baseline,
            recentMessages = priorMessages,
          )
        val initialTimingsJson =
          TurnPayloadBuilder.mergeTimingsJson(
            existingTimingsJson = null,
            vadEndMs = dispatchCtx?.vadEndEpochMs,
            transcriptReadyMs = dispatchCtx?.transcriptReadyEpochMs ?: turnCreatedAtMs,
            emotionReadyMs = dispatchCtx?.emotionReadyEpochMs,
            emotionTimeoutMs = dispatchCtx?.emotionTimeoutEpochMs,
          )
        dao.insertTurnDispatchLog(
          TurnDispatchLogEntity(
            requestId = reqId,
            createdAt = turnCreatedAtMs,
            payloadJson = payloadJson,
            triageJson = null,
            correctedJson = null,
            toolName = null,
            toolArgsJson = null,
            toolResultJson = null,
            replyText = null,
            status = "dispatched",
            error = null,
            timingsJson = initialTimingsJson,
          )
        )
      }
    }

    executeAssistantTurn(
      chat = chat,
      settings = settings,
      channel = RiviaChannel.LIVE_VOICE,
      priorMessagesBeforeUserTurn = priorMessages,
      userText = userText,
      previousInteractionId = prevInteractionId,
      requestId = reqId,
      onDeltaCallback = onReplyDelta,
      onCompleteCallback = onTurnComplete,
      onErrorCallback = onTurnError,
    )
  }

  private suspend fun executeDirectAsk(
    text: String,
    channel: RiviaChannel,
    customReqId: String?,
  ): String {
    activeTurnCompletionJob?.join()
    val settings = currentRunSettings.value
    val chat = ensureActiveChatForFirstMessage(text, settings)
    val priorMessages = dao.getMessagesForChat(chat.id)
    val prevInteractionId = chat.lastInteractionId
    val reqId = customReqId ?: TextAudioTagProcessor.generateRequestId()
    val sb = StringBuilder()

    executeAssistantTurn(
      chat = chat,
      settings = settings,
      channel = channel,
      priorMessagesBeforeUserTurn = priorMessages,
      userText = text,
      previousInteractionId = prevInteractionId,
      requestId = reqId,
      onDeltaCallback = { sb.append(it) },
    )
    return sb.toString()
  }

  /**
   * Section 3 & 8: Refreshes Gemini model list and shows plain API result or error.
   */
  fun refreshGeminiModels(customKeyToSave: String? = null) {
    viewModelScope.launch {
      _isLoadingModels.value = true
      if (customKeyToSave != null) {
        keyRepository.saveGeminiKey(customKeyToSave)
      }
      val key = customKeyToSave?.trim() ?: keyRepository.getGeminiKey()
      val result = geminiClient.fetchModels(key)
      _isLoadingModels.value = false

      result.fold(
        onSuccess = { models ->
          dao.replaceCachedModels(models)
          showNotice("Loaded ${models.size} Gemini models.")
        },
        onFailure = { err ->
          val redacted = keyRepository.redact(err.message ?: "Failed to fetch Gemini models")
          showError(redacted)
        },
      )
    }
  }

  /**
   * Section 5 & 8: Refreshes ElevenLabs personal voices and TTS models.
   */
  fun refreshElevenLabsVoicesAndModels(customKeyToSave: String? = null) {
    viewModelScope.launch {
      _isLoadingVoices.value = true
      if (customKeyToSave != null) {
        keyRepository.saveElevenLabsKey(customKeyToSave)
      }
      val key = customKeyToSave?.trim() ?: keyRepository.getElevenLabsKey()
      val voicesRes = elevenLabsClient.fetchPersonalVoices(key)
      val modelsRes = elevenLabsClient.fetchTtsModels(key)
      _isLoadingVoices.value = false

      voicesRes.fold(
        onSuccess = { voices ->
          _personalVoices.value = voices
          modelsRes.onSuccess { models -> _elevenModels.value = models }
          val curPrefs = voicePreferences.value
          if (curPrefs.voiceId.isBlank() && voices.isNotEmpty()) {
            selectElevenLabsVoice(voices.first())
          }
          showNotice("Loaded ${voices.size} personal voices from ElevenLabs.")
        },
        onFailure = { err ->
          val redacted = keyRepository.redact(err.message ?: "Failed to fetch ElevenLabs voices")
          showError(redacted)
        },
      )
    }
  }

  fun selectElevenLabsVoice(voice: ElevenLabsVoice) {
    viewModelScope.launch {
      val isProClone = voice.category.equals("professional", ignoreCase = true)
      keyRepository.updateVoicePreferences {
        it.copy(
          voiceId = voice.voiceId,
          voiceName = voice.name,
          voiceCategory = voice.category,
          isProfessionalClone = isProClone,
        )
      }
      val key = keyRepository.getElevenLabsKey()
      if (key.isNotBlank()) {
        elevenLabsClient.fetchVoiceSettings(key, voice.voiceId).onSuccess { dto ->
          keyRepository.updateVoicePreferences { cur ->
            cur.copy(
              stability = dto.stability,
              similarityBoost = dto.similarityBoost,
              useSpeakerBoost = dto.useSpeakerBoost,
              style = dto.style,
              speed = dto.speed,
              cloneStrength = dto.cloneStrength ?: cur.cloneStrength,
            )
          }
        }
      }
    }
  }

  fun setCustomVoiceId(voiceId: String) {
    viewModelScope.launch {
      val trimmed = voiceId.trim()
      keyRepository.updateVoicePreferences {
        it.copy(voiceId = trimmed, voiceName = if (trimmed.isNotEmpty()) "Custom ($trimmed)" else "")
      }
      val key = keyRepository.getElevenLabsKey()
      if (key.isNotBlank() && trimmed.isNotEmpty()) {
        elevenLabsClient.fetchVoiceSettings(key, trimmed).onSuccess { dto ->
          keyRepository.updateVoicePreferences { cur ->
            cur.copy(
              stability = dto.stability,
              similarityBoost = dto.similarityBoost,
              useSpeakerBoost = dto.useSpeakerBoost,
              style = dto.style,
              speed = dto.speed,
              cloneStrength = dto.cloneStrength ?: cur.cloneStrength,
              isProfessionalClone = dto.cloneStrength != null,
            )
          }
        }
      }
    }
  }

  fun updateVoicePrefs(transform: (VoicePreferences) -> VoicePreferences) {
    viewModelScope.launch { keyRepository.updateVoicePreferences(transform) }
  }

  fun viewModelScopeSaveGeminiKey(rawKey: String) {
    viewModelScope.launch { keyRepository.saveGeminiKey(rawKey) }
  }

  fun viewModelScopeSaveElevenKey(rawKey: String) {
    viewModelScope.launch { keyRepository.saveElevenLabsKey(rawKey) }
  }

  fun testElevenLabsSpeakLine(customVoiceId: String? = null) {
    viewModelScope.launch {
      val trimmed = customVoiceId?.trim()
      if (!trimmed.isNullOrEmpty() && trimmed != voicePreferences.value.voiceId) {
        keyRepository.updateVoicePreferences {
          it.copy(voiceId = trimmed, voiceName = "Custom ($trimmed)")
        }
      }
      val line =
        "[warmly] Hi Jordan, I'm Rivia. My voice stream and sentence pipeline are ready."
      audioPlayerManager.speakMarkdownMessage(
        messageId = "voice_test_line",
        rawMarkdown = line,
        isLiveChannel = false,
      )
    }
  }

  /**
   * Builds redacted JSON preview for "Preview request" in Lab Run Settings.
   */
  suspend fun buildRedactedRequestPreview(settings: RunSettings): String {
    val gKey = keyRepository.getGeminiKey()
    val eKey = keyRepository.getElevenLabsKey()
    val prevId = activeChat.value?.lastInteractionId
    return geminiClient.buildRedactedPreview(
      settings = settings,
      channel = RiviaChannel.TYPED_CHAT,
      previousInteractionId = prevId,
      geminiKey = gKey,
      elevenKey = eKey,
    )
  }

  /**
   * Builds redacted JSON preview for "Preview request" in Rivia Chat Info dialog (with Text / Voice switch).
   */
  suspend fun buildRiviaRedactedRequestPreview(channelChoice: String): String {
    val bundle = _loadedLayersBundle.value ?: return "{ \"error\": \"Layers not loaded\" }"
    val gKey = keyRepository.getGeminiKey()
    val eKey = keyRepository.getElevenLabsKey()
    val chat = activeChat.value
    val prevId = chat?.lastInteractionId
    val pendingH7 = chat?.pendingH7Notice
    val selectedProfile = activeRiviaProfile.value
    val profileForChannel =
      if (channelChoice == "voice") "live"
      else LayerConfigManager.normalizeProfileKey(selectedProfile)

    val sysInstruction =
      LayerConfigManager.assembleSystemInstruction(
        channel = channelChoice,
        persona = bundle.persona,
        style = bundle.style,
        hardRules = bundle.hardRules,
        spokenMaxSentences = _spokenMaxSentences.value,
        pendingH7Notice = pendingH7,
      )

    return geminiClient.buildRiviaRedactedPreview(
      engine = bundle.engine,
      profileName = profileForChannel,
      assembledSystemInstruction = sysInstruction,
      previousInteractionId = prevId,
      geminiKey = gKey,
      elevenKey = eKey,
    )
  }

  /**
   * Section 7 & 8: Export all chats (and their messages + raw steps) AND layer drafts, locks,
   * test runs, rule events, and layer settings to a JSON string.
   */
  suspend fun exportAllChatsToJson(): String {
    val allChats = dao.getAllChats()
    val chatsArray = JSONArray()
    for (chat in allChats) {
      val msgs = dao.getMessagesForChat(chat.id)
      val msgsArray = JSONArray()
      for (m in msgs) {
        msgsArray.put(
          JSONObject().apply {
            put("id", m.id)
            put("chatId", m.chatId)
            put("role", m.role)
            put("content", m.content)
            put("thoughtSummary", m.thoughtSummary)
            put("requestId", m.requestId)
            put("interactionId", m.interactionId ?: JSONObject.NULL)
            put("previousInteractionId", m.previousInteractionId ?: JSONObject.NULL)
            put("model", m.model)
            put("serviceTier", m.serviceTier)
            put("inputTokens", m.inputTokens)
            put("outputTokens", m.outputTokens)
            put("thoughtTokens", m.thoughtTokens)
            put("ttftMs", m.ttftMs)
            put("totalTimeMs", m.totalTimeMs)
            put("status", m.status)
            put("errorMessage", m.errorMessage ?: JSONObject.NULL)
            put("voiceLabel", m.voiceLabel ?: JSONObject.NULL)
            put("rawStepsJson", m.rawStepsJson)
            put("createdAt", m.createdAt)
            put("chatKind", m.chatKind)
            put("profile", m.profile)
            put("channel", m.channel)
            put("fingerprint", m.fingerprint)
            put("ruleEventsSummary", m.ruleEventsSummary)
            put("h7TrimmedFrom", m.h7TrimmedFrom ?: JSONObject.NULL)
            put("h7TrimmedTo", m.h7TrimmedTo ?: JSONObject.NULL)
          }
        )
      }
      chatsArray.put(
        JSONObject().apply {
          put("id", chat.id)
          put("title", chat.title)
          put("createdAt", chat.createdAt)
          put("updatedAt", chat.updatedAt)
          put("runSettingsJson", chat.runSettingsJson)
          put("lastInteractionId", chat.lastInteractionId ?: JSONObject.NULL)
          put("isLabChat", chat.isLabChat)
          put("riviaProfile", chat.riviaProfile)
          put("pendingH7Notice", chat.pendingH7Notice ?: JSONObject.NULL)
          put("messages", msgsArray)
        }
      )
    }

    val draftsArray = JSONArray()
    for (d in dao.getLayerDrafts()) {
      draftsArray.put(
        JSONObject().apply {
          put("layerKey", d.layerKey)
          put("jsonContent", d.jsonContent)
          put("updatedAt", d.updatedAt)
        }
      )
    }

    val locksArray = JSONArray()
    for (l in dao.getLayerLocks()) {
      locksArray.put(
        JSONObject().apply {
          put("layerKey", l.layerKey)
          put("pendingCanonicalSha256", l.pendingCanonicalSha256 ?: JSONObject.NULL)
          put("lastSeenBundledSha256", l.lastSeenBundledSha256 ?: JSONObject.NULL)
          put("exportedJson", l.exportedJson ?: JSONObject.NULL)
          put("mismatchWarning", l.mismatchWarning ?: JSONObject.NULL)
        }
      )
    }

    val testRunsArray = JSONArray()
    for (tr in dao.getLayerTestRuns()) {
      testRunsArray.put(
        JSONObject().apply {
          put("id", tr.id)
          put("testId", tr.testId)
          put("profile", tr.profile)
          put("fingerprint", tr.fingerprint)
          put("passed", tr.passed)
          put("detailsJson", tr.detailsJson)
          put("timestamp", tr.timestamp)
        }
      )
    }

    val ruleEventsArray = JSONArray()
    for (re in dao.getAllRuleEventsForExport()) {
      ruleEventsArray.put(
        JSONObject().apply {
          put("id", re.id)
          put("chatId", re.chatId)
          put("requestId", re.requestId)
          put("ruleId", re.ruleId)
          put("eventType", re.eventType)
          put("detail", re.detail)
          put("timestamp", re.timestamp)
        }
      )
    }

    val layerSettingsObj =
      dao.getLayerSettings()?.let { ls ->
        JSONObject().apply {
          put("spokenMaxSentences", ls.spokenMaxSentences)
          put("h11WatchWordsJson", ls.h11WatchWordsJson)
        }
      }

    val voiceEmotionArray = buildVoiceEmotionLogsJsonArray(dao.getAllVoiceEmotionLogsForExport())
    val turnDispatchArray = buildTurnDispatchLogsJsonArray(dao.getAllTurnDispatchLogsForExport())

    val requestLogsArray = JSONArray()
    for (rl in dao.getAllRequestLogsForExport()) {
      requestLogsArray.put(
        JSONObject().apply {
          put("id", rl.id)
          put("timestamp", rl.timestamp)
          put("requestId", rl.requestId)
          put("model", rl.model)
          put("status", rl.status)
          put("serviceTier", rl.serviceTier)
          put("inputTokens", rl.inputTokens)
          put("outputTokens", rl.outputTokens)
          put("thoughtTokens", rl.thoughtTokens)
          put("latencyMs", rl.latencyMs)
          put("error", rl.error ?: JSONObject.NULL)
          put("chatKind", rl.chatKind)
          put("profile", rl.profile)
          put("channel", rl.channel)
          put("fingerprint", rl.fingerprint)
          put("ruleEvents", rl.ruleEvents)
        }
      )
    }

    return JSONObject()
      .apply {
        put("version", 2)
        put("exportedAt", System.currentTimeMillis())
        put("chats", chatsArray)
        put("layerDrafts", draftsArray)
        put("layerLocks", locksArray)
        put("layerTestRuns", testRunsArray)
        put("ruleEvents", ruleEventsArray)
        put("requestLogs", requestLogsArray)
        put("voiceEmotionLogs", voiceEmotionArray)
        put("turnDispatchLogs", turnDispatchArray)
        if (layerSettingsObj != null) {
          put("layerSettings", layerSettingsObj)
        }
      }
      .toString(2)
  }

  private fun buildTurnDispatchLogsJsonArray(logs: List<TurnDispatchLogEntity>): JSONArray {
    val array = JSONArray()
    for (log in logs) {
      array.put(
        JSONObject().apply {
          put("id", log.id)
          put("request_id", log.requestId)
          put("created_at", log.createdAt)
          put("payload_json", log.payloadJson)
          put("triage_json", log.triageJson ?: JSONObject.NULL)
          put("corrected_json", log.correctedJson ?: JSONObject.NULL)
          put("tool_name", log.toolName ?: JSONObject.NULL)
          put("tool_args_json", log.toolArgsJson ?: JSONObject.NULL)
          put("tool_result_json", log.toolResultJson ?: JSONObject.NULL)
          put("reply_text", log.replyText ?: JSONObject.NULL)
          put("status", log.status)
          put("error", log.error ?: JSONObject.NULL)
          put("timings_json", log.timingsJson)
        }
      )
    }
    return array
  }

  private fun buildVoiceEmotionLogsJsonArray(logs: List<VoiceEmotionLogEntity>): JSONArray {
    val array = JSONArray()
    for (log in logs) {
      array.put(
        JSONObject().apply {
          put("id", log.id)
          put("requestId", log.requestId)
          put("timestamp", log.timestamp)
          put("mode", log.mode)
          put("audioSource", log.audioSource)
          put("inputDevice", log.inputDevice)
          put("recognizerOk", log.recognizerOk)
          put("recognizerErrorCode", log.recognizerErrorCode ?: JSONObject.NULL)
          put("recognizerPackage", log.recognizerPackage)
          put("audioRecordStarted", log.audioRecordStarted)
          put("pipeBytesWritten", log.pipeBytesWritten)
          put("partialResultsCount", log.partialResultsCount)
          put("callbacksFired", log.callbacksFired)
          put("transcriptSource", log.transcriptSource)
          put("usedPartialFallback", log.usedPartialFallback)
          put("finalText", log.finalText)
          put("pipeCloseToFinalMs", log.pipeCloseToFinalMs ?: JSONObject.NULL)
          put("lastPartialText", log.lastPartialText)
          put("transcriptPreview", log.transcriptPreview)
          put("vadSpeechDetected", log.vadSpeechDetected)
          put("vadEndedTurn", log.vadEndedTurn)
          put("vadMaxProb", log.vadMaxProb.toDouble())
          put("speechDurationSec", log.speechDurationSec.toDouble())
          put("wordCount", log.wordCount)
          put("speakingRateWps", log.speakingRateWps?.toDouble() ?: JSONObject.NULL)
          put("isShortSpeech", log.isShortSpeech)
          put("clipLengthSec", log.clipLengthSec.toDouble())
          put("rms", log.rms.toDouble())
          put("rmsAfterGain", log.rmsAfterGain?.toDouble() ?: JSONObject.NULL)
          put("appliedGainDb", log.appliedGainDb?.toDouble() ?: JSONObject.NULL)
          put("peak", log.peak.toDouble())
          put("silentCapture", log.silentCapture)
          put("arousal", log.arousal?.toDouble() ?: JSONObject.NULL)
          put("dominance", log.dominance?.toDouble() ?: JSONObject.NULL)
          put("valence", log.valence?.toDouble() ?: JSONObject.NULL)
          put("scoredWindowsCount", log.scoredWindowsCount)
          put("inferenceMs", log.inferenceMs ?: JSONObject.NULL)
          put("calibrationLabel", log.calibrationLabel ?: JSONObject.NULL)
          put("captureStatus", log.captureStatus)
          put("errorMessage", log.errorMessage ?: JSONObject.NULL)
        }
      )
    }
    return array
  }

  /**
   * Exports all Live VAD / emotion / mic-sharing results (plus current Lab VAD settings,
   * computed Mode B baseline, and turnDispatchLogs) as a formatted JSON string.
   */
  suspend fun exportVoiceEmotionLogsToJson(): String {
    val logs = dao.getAllVoiceEmotionLogsForExport()
    val dispatchLogs = dao.getAllTurnDispatchLogsForExport()
    val prefs = labMicTestPreferences.value
    val baseline = queryAndComputeEmotionBaseline(dao, prefs.emotionBaselineResetAt)
    val vadConfigObj =
      JSONObject().apply {
        put("recordAlongsideRecognizer", prefs.recordAlongsideRecognizer)
        put("micTestMode", prefs.micTestMode)
        put("saveWavClips", prefs.saveWavClips)
        put("callStopListeningOnPipeClose", prefs.callStopListeningOnPipeClose)
        put("vadStartThreshold", prefs.vadStartThreshold.toDouble())
        put("vadStartDurationMs", prefs.vadStartDurationMs)
        put("vadEndThreshold", prefs.vadEndThreshold.toDouble())
        put("vadEndSilenceMs", prefs.vadEndSilenceMs)
        put("emotionBaselineResetAt", prefs.emotionBaselineResetAt)
      }
    val baselineObj =
      JSONObject().apply {
        put("windowDays", baseline.windowDays)
        put("windowStart", baseline.windowStart)
        put("windowEnd", baseline.windowEnd)
        put("n", baseline.eligibleCount)
        put("eligibleModeBCount", baseline.eligibleCount)
        put("excludedModeACount", baseline.excludedModeACount)
        put("excludedShortSpeechCount", baseline.excludedShortCount)
        put("excludedSilentCount", baseline.excludedSilentCount)
        put("excludedCalibratedCount", baseline.excludedCalibratedCount)
        put("resetAtTimestamp", baseline.resetAtTimestamp)
        put("meanArousal", baseline.meanArousal?.toDouble() ?: JSONObject.NULL)
        put("meanDominance", baseline.meanDominance?.toDouble() ?: JSONObject.NULL)
        put("meanValence", baseline.meanValence?.toDouble() ?: JSONObject.NULL)
        put("meanSpeakingRateWps", baseline.meanSpeakingRateWps?.toDouble() ?: JSONObject.NULL)
        put("latestTagSummary", baseline.latestTagSummary)
      }
    return JSONObject()
      .apply {
        put("exportedAt", System.currentTimeMillis())
        put("count", logs.size)
        put("labMicAndVadSettings", vadConfigObj)
        put("emotionBaseline", baselineObj)
        put("turns", buildVoiceEmotionLogsJsonArray(logs))
        put("turnDispatchLogs", buildTurnDispatchLogsJsonArray(dispatchLogs))
      }
      .toString(2)
  }

  /**
   * Exports all Live VAD / emotion / mic-sharing results as a CSV string.
   */
  suspend fun exportVoiceEmotionLogsToCsv(): String {
    val logs = dao.getAllVoiceEmotionLogsForExport()
    fun csvEscape(value: String): String {
      val escaped = value.replace("\"", "\"\"")
      return "\"$escaped\""
    }
    val sb = StringBuilder()
    sb.appendLine(
      "request_id,timestamp,mode,audio_source,input_device,recognizer_ok,recognizer_error_code,recognizer_package,audio_record_started,pipe_bytes_written,partial_results_count,callbacks_fired,transcript_source,used_partial_fallback,pipe_close_to_final_ms,final_text,last_partial_text,vad_speech_detected,vad_ended_turn,vad_max_prob,speech_duration_sec,word_count,speaking_rate_wps,is_short_speech,clip_length_sec,rms,rms_after_gain,applied_gain_db,peak,silent_capture,arousal,dominance,valence,scored_windows_count,inference_ms,calibration_label,capture_status,transcript_preview,error_message"
    )
    for (log in logs) {
      sb.append(csvEscape(log.requestId)).append(',')
      sb.append(log.timestamp).append(',')
      sb.append(csvEscape(log.mode)).append(',')
      sb.append(csvEscape(log.audioSource)).append(',')
      sb.append(csvEscape(log.inputDevice)).append(',')
      sb.append(log.recognizerOk).append(',')
      sb.append(log.recognizerErrorCode?.toString() ?: "").append(',')
      sb.append(csvEscape(log.recognizerPackage)).append(',')
      sb.append(log.audioRecordStarted).append(',')
      sb.append(log.pipeBytesWritten).append(',')
      sb.append(log.partialResultsCount).append(',')
      sb.append(csvEscape(log.callbacksFired)).append(',')
      sb.append(csvEscape(log.transcriptSource)).append(',')
      sb.append(log.usedPartialFallback).append(',')
      sb.append(log.pipeCloseToFinalMs?.toString() ?: "").append(',')
      sb.append(csvEscape(log.finalText)).append(',')
      sb.append(csvEscape(log.lastPartialText)).append(',')
      sb.append(log.vadSpeechDetected).append(',')
      sb.append(log.vadEndedTurn).append(',')
      sb.append("%.4f".format(Locale.US, log.vadMaxProb)).append(',')
      sb.append("%.2f".format(Locale.US, log.speechDurationSec)).append(',')
      sb.append(log.wordCount).append(',')
      sb.append(log.speakingRateWps?.let { "%.2f".format(Locale.US, it) } ?: "").append(',')
      sb.append(log.isShortSpeech).append(',')
      sb.append("%.2f".format(Locale.US, log.clipLengthSec)).append(',')
      sb.append("%.4f".format(Locale.US, log.rms)).append(',')
      sb.append(log.rmsAfterGain?.let { "%.4f".format(Locale.US, it) } ?: "").append(',')
      sb.append(log.appliedGainDb?.let { "%.1f".format(Locale.US, it) } ?: "").append(',')
      sb.append("%.4f".format(Locale.US, log.peak)).append(',')
      sb.append(log.silentCapture).append(',')
      sb.append(log.arousal?.let { "%.4f".format(Locale.US, it) } ?: "").append(',')
      sb.append(log.dominance?.let { "%.4f".format(Locale.US, it) } ?: "").append(',')
      sb.append(log.valence?.let { "%.4f".format(Locale.US, it) } ?: "").append(',')
      sb.append(log.scoredWindowsCount).append(',')
      sb.append(log.inferenceMs?.toString() ?: "").append(',')
      sb.append(csvEscape(log.calibrationLabel ?: "")).append(',')
      sb.append(csvEscape(log.captureStatus)).append(',')
      sb.append(csvEscape(log.transcriptPreview)).append(',')
      sb.appendLine(csvEscape(log.errorMessage ?: ""))
    }
    return sb.toString()
  }

  /**
   * Section 7 & 8: Import chats, layer state, and logs from a JSON string in a single transaction.
   * Existing rows are skipped (OnConflictStrategy.IGNORE or natural-key dedup) and never deleted.
   */
  fun importChatsFromJson(jsonString: String) {
    viewModelScope.launch {
      try {
        val payload = parseBackupJsonToPayload(jsonString)
        dao.importBackupInTransaction(payload)

        if (_activeChatId.value == null) {
          val firstChat = dao.getAllChats().firstOrNull()
          if (firstChat != null) {
            _activeChatId.value = firstChat.id
          }
        }
        showNotice("Imported backup (${payload.chats.size} chats in file).")
      } catch (e: Exception) {
        showError("Import failed: ${e.message}")
      }
    }
  }

  fun showError(message: String) {
    _isErrorBanner.value = true
    _statusBannerMessage.value = message
  }

  fun showNotice(message: String) {
    _isErrorBanner.value = false
    _statusBannerMessage.value = message
  }

  val turnDispatchLogs: StateFlow<List<TurnDispatchLogEntity>> =
    dao
      .observeTurnDispatchLogs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _stepTestResults =
    MutableStateFlow<Map<String, StepTestVerificationResult>>(emptyMap())
  val stepTestResults: StateFlow<Map<String, StepTestVerificationResult>> =
    _stepTestResults.asStateFlow()

  private val _runningStepTestId = MutableStateFlow<String?>(null)
  val runningStepTestId: StateFlow<String?> = _runningStepTestId.asStateFlow()

  /**
   * Ensures a Rivia chat (non-Lab) is currently active so Rivia live voice turn pipeline tests
   * run against the Rivia channel, and applies the exact [LabMicTestPreferences] required by a test.
   */
  fun applyExactStepTestSettings(
    testTitle: String,
    recordAlongsideRecognizer: Boolean = true,
    micTestMode: String = "B",
    saveWavClips: Boolean = false,
    vadStartThreshold: Float = 0.50f,
    vadStartDurationMs: Int = 250,
    vadEndThreshold: Float = 0.35f,
    vadEndSilenceMs: Int = 700,
    ensureRiviaChat: Boolean = true,
    onReady: (() -> Unit)? = null,
  ) {
    viewModelScope.launch {
      keyRepository.updateLabMicTestPreferences { current ->
        current.copy(
          recordAlongsideRecognizer = recordAlongsideRecognizer,
          micTestMode = micTestMode,
          saveWavClips = saveWavClips,
          vadStartThreshold = vadStartThreshold,
          vadStartDurationMs = vadStartDurationMs,
          vadEndThreshold = vadEndThreshold,
          vadEndSilenceMs = vadEndSilenceMs,
        )
      }

      if (ensureRiviaChat && isActiveChatLab.value) {
        val existingRivia = dao.getAllChats().firstOrNull { !it.isLabChat }
        if (existingRivia != null) {
          selectChat(existingRivia.id)
        } else {
          startNewRiviaChat()
        }
      }

      sileroVadManager.ensureDownloadedAsync(forceRedownload = false)
      if (emotionModelManager.modelState.value is EmotionModelState.NotDownloaded) {
        emotionModelManager.startDownload(forceRedownload = false)
      }

      showNotice(
        "Applied exact settings for $testTitle: Rivia Chat · Mode $micTestMode · Record=${if (recordAlongsideRecognizer) "ON" else "OFF"} · SaveClips=${if (saveWavClips) "ON" else "OFF"} · VAD=${"%.2f".format(Locale.US, vadStartThreshold)}/${vadStartDurationMs}ms/${"%.2f".format(Locale.US, vadEndThreshold)}/${vadEndSilenceMs}ms"
      )
      onReady?.invoke()
    }
  }

  fun runStepTestVerification(testId: String) {
    if (_runningStepTestId.value != null) return
    _runningStepTestId.value = testId
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val result =
          when (testId) {
            "S0_T1" -> verifyStep0Test1Retention()
            "S0_T2" -> verifyStep0Test2Baseline()
            "S0_T3" -> verifyStep0Test3BackupDedup()
            "S1_T1" -> verifyStep1Test1SilenceTrimmer()
            "S1_T2" -> verifyStep1Test2RecognizerRingBuffer()
            "S1_T3" -> verifyStep1Test3WindowSplitting()
            "S1_T4" -> verifyStep1Test4BarrierAndLateUpdate()
            else ->
              StepTestVerificationResult(
                testId = testId,
                passed = false,
                headline = "Unknown test ID: $testId",
                details = "",
                timestampMs = System.currentTimeMillis(),
              )
          }
        _stepTestResults.value = _stepTestResults.value + (testId to result)
      } catch (e: Exception) {
        _stepTestResults.value =
          _stepTestResults.value +
            (testId to
              StepTestVerificationResult(
                testId = testId,
                passed = false,
                headline = "Error: ${e.message ?: e.javaClass.simpleName}",
                details = e.stackTraceToString().take(500),
                timestampMs = System.currentTimeMillis(),
              ))
      } finally {
        _runningStepTestId.value = null
      }
    }
  }

  private suspend fun verifyStep0Test1Retention(): StepTestVerificationResult {
    keyRepository.updateLabMicTestPreferences {
      it.copy(
        recordAlongsideRecognizer = true,
        micTestMode = "B",
        saveWavClips = false,
      )
    }
    val allEmotion = dao.getAllVoiceEmotionLogsForExport()
    val displayEmotion = voiceEmotionLogs.value
    val allRequests = dao.getAllRequestLogsForExport()
    val displayRequests = requestLogs.value
    val allRules = dao.getAllRuleEventsForExport()
    val clipsDir = java.io.File(getApplication<Application>().filesDir, "emotion_clips")
    val wavCount =
      clipsDir.listFiles { f -> f.isFile && f.name.endsWith(".wav") }?.size ?: 0
    val expectedDisplayEmotion = minOf(50, allEmotion.size)
    val expectedDisplayReq = minOf(100, allRequests.size)
    val passed =
      displayEmotion.size <= 50 &&
        displayRequests.size <= 100 &&
        allEmotion.size >= displayEmotion.size &&
        allRequests.size >= displayRequests.size &&
        wavCount <= 10

    return StepTestVerificationResult(
      testId = "S0_T1",
      passed = passed,
      headline =
        if (passed)
          "PASS · All rows kept in DB; display queries capped at 50/100; WAVs=$wavCount/10"
        else "FAIL · Count mismatch detected",
      details =
        buildString {
          appendLine("• Settings applied: Record=ON, Mode=B, Save clips=OFF (default)")
          appendLine(
            "• voice_emotion_logs: Export query=${allEmotion.size} rows · UI display=${displayEmotion.size} (expected <= $expectedDisplayEmotion, label: \"Latest 50 of ${allEmotion.size} turns\")"
          )
          appendLine(
            "• request_logs: Export query=${allRequests.size} rows · UI display=${displayRequests.size} (expected <= $expectedDisplayReq, label: \"Latest 100 of ${allRequests.size} requests\")"
          )
          appendLine("• rule_events: Export query=${allRules.size} rows")
          appendLine("• Saved WAV clips in files/emotion_clips: $wavCount (max cap = 10)")
        }.trim(),
      timestampMs = System.currentTimeMillis(),
    )
  }

  private suspend fun verifyStep0Test2Baseline(): StepTestVerificationResult {
    keyRepository.updateLabMicTestPreferences {
      it.copy(
        recordAlongsideRecognizer = true,
        micTestMode = "B",
        vadStartThreshold = 0.50f,
        vadStartDurationMs = 250,
        vadEndThreshold = 0.35f,
        vadEndSilenceMs = 700,
      )
    }
    val resetAt = labMicTestPreferences.value.emotionBaselineResetAt
    val nowMs = System.currentTimeMillis()
    val computed = queryAndComputeEmotionBaseline(dao = dao, resetAtMs = resetAt, nowMs = nowMs)
    val exportedJson = JSONObject(exportVoiceEmotionLogsToJson())
    val exportBaseline = exportedJson.getJSONObject("emotionBaseline")
    val hasRequiredFields =
      exportBaseline.has("windowDays") &&
        exportBaseline.has("windowStart") &&
        exportBaseline.has("windowEnd") &&
        exportBaseline.has("n")
    val exportedWindowDays = exportBaseline.optInt("windowDays", -1)
    val exportedN = exportBaseline.optInt("n", -1)
    val passed =
      hasRequiredFields &&
        exportedWindowDays == BASELINE_WINDOW_DAYS &&
        computed.windowDays == BASELINE_WINDOW_DAYS &&
        exportedN == computed.eligibleCount &&
        computed.eligibleCount <= BASELINE_MAX_ROWS

    return StepTestVerificationResult(
      testId = "S0_T2",
      passed = passed,
      headline =
        if (passed)
          "PASS · ${computed.windowDays}d window (max $BASELINE_MAX_ROWS) · n=${computed.eligibleCount} eligible Mode B turns"
        else "FAIL · Baseline window or export block mismatch",
      details =
        buildString {
          appendLine(
            "• Constants: BASELINE_WINDOW_DAYS=$BASELINE_WINDOW_DAYS, BASELINE_MAX_ROWS=$BASELINE_MAX_ROWS, MIN_BASELINE_SPEECH_SEC=${MIN_BASELINE_SPEECH_SEC}s"
          )
          appendLine(
            "• Eligible Mode B turns in 14d window: n=${computed.eligibleCount} (Export JSON n=$exportedN)"
          )
          appendLine(
            "• Excluded in 14d window: Mode A=${computed.excludedModeACount} · Short (<1.5s)=${computed.excludedShortCount} · Silent=${computed.excludedSilentCount}"
          )
          appendLine(
            "• Mean A/D/V: A=${computed.meanArousal?.let { "%.3f".format(Locale.US, it) } ?: "—"} · D=${computed.meanDominance?.let { "%.3f".format(Locale.US, it) } ?: "—"} · V=${computed.meanValence?.let { "%.3f".format(Locale.US, it) } ?: "—"} · Rate=${computed.meanSpeakingRateWps?.let { "%.2f w/s".format(Locale.US, it) } ?: "—"}"
          )
          appendLine(
            "• Export JSON emotionBaseline keys verified: windowDays=$exportedWindowDays, windowStart=${exportBaseline.optLong("windowStart")}, windowEnd=${exportBaseline.optLong("windowEnd")}, n=$exportedN"
          )
        }.trim(),
      timestampMs = System.currentTimeMillis(),
    )
  }

  private suspend fun verifyStep0Test3BackupDedup(): StepTestVerificationResult {
    val chatsBefore = dao.getAllChats().size
    val emotionBefore = dao.getAllVoiceEmotionLogsForExport().size
    val reqBefore = dao.getAllRequestLogsForExport().size
    val rulesBefore = dao.getAllRuleEventsForExport().size
    val dispatchBefore = dao.getAllTurnDispatchLogsForExport().size

    val backupJson = exportAllChatsToJson()
    val payload = parseBackupJsonToPayload(backupJson)

    // Re-import the exact same backup twice in a row into the live DB
    dao.importBackupInTransaction(payload)
    dao.importBackupInTransaction(payload)

    val chatsAfter = dao.getAllChats().size
    val emotionAfter = dao.getAllVoiceEmotionLogsForExport().size
    val reqAfter = dao.getAllRequestLogsForExport().size
    val rulesAfter = dao.getAllRuleEventsForExport().size
    val dispatchAfter = dao.getAllTurnDispatchLogsForExport().size

    val passed =
      chatsBefore == chatsAfter &&
        emotionBefore == emotionAfter &&
        reqBefore == reqAfter &&
        rulesBefore == rulesAfter &&
        dispatchBefore == dispatchAfter

    return StepTestVerificationResult(
      testId = "S0_T3",
      passed = passed,
      headline =
        if (passed)
          "PASS · Imported backup twice with 0 duplicates and 0 deleted rows"
        else "FAIL · Row counts changed after duplicate backup import",
      details =
        buildString {
          appendLine("• Executed exportAllChatsToJson() -> importBackupInTransaction() x2:")
          appendLine("• chats: $chatsBefore -> $chatsAfter (OnConflictStrategy.IGNORE)")
          appendLine("• voice_emotion_logs: $emotionBefore -> $emotionAfter (natural-key dedup)")
          appendLine("• request_logs: $reqBefore -> $reqAfter (natural-key dedup)")
          appendLine("• rule_events: $rulesBefore -> $rulesAfter (natural-key dedup)")
          appendLine("• turn_dispatch_logs: $dispatchBefore -> $dispatchAfter (request_id dedup)")
        }.trim(),
      timestampMs = System.currentTimeMillis(),
    )
  }

  private suspend fun verifyStep1Test1SilenceTrimmer(): StepTestVerificationResult {
    keyRepository.updateLabMicTestPreferences {
      it.copy(
        recordAlongsideRecognizer = true,
        micTestMode = "B",
        saveWavClips = true,
        vadStartThreshold = 0.50f,
        vadStartDurationMs = 250,
        vadEndThreshold = 0.35f,
        vadEndSilenceMs = 700,
      )
    }

    val config =
      com.example.audio.SileroVadConfig(
        startProbThreshold = 0.50f,
        startSpeechMs = 250,
        endProbThreshold = 0.35f,
        endSilenceMs = 700,
        sampleRateHz = 16000,
        chunkSamples = 512,
      )
    val tracker = com.example.audio.SileroVadTurnTracker(config)
    val trimmer = com.example.audio.EdgeSilenceTrimmer()

    // 30 chunks (960 ms) leading silence: first 12,160 samples = 0.01f, last 3,200 samples (200 ms pre-roll) = 0.02f
    val earlyLeading = 30 * 512 - MicSharingRecorder.PRE_ROLL_SAMPLES
    for (c in 0 until 30) {
      val chunk = FloatArray(512) { i -> if (c * 512 + i < earlyLeading) 0.01f else 0.02f }
      trimmer.onChunk512(chunk, tracker.onChunkProbability(0.05f))
    }
    // 40 chunks (1,280 ms) speech 1 = 0.50f
    repeat(40) { trimmer.onChunk512(FloatArray(512) { 0.50f }, tracker.onChunkProbability(0.85f)) }
    // 10 chunks (320 ms) mid-turn pause = 0.03f (must be preserved)
    repeat(10) { trimmer.onChunk512(FloatArray(512) { 0.03f }, tracker.onChunkProbability(0.15f)) }
    // 30 chunks (960 ms) speech 2 = 0.60f
    repeat(30) { trimmer.onChunk512(FloatArray(512) { 0.60f }, tracker.onChunkProbability(0.80f)) }
    // 22 chunks (704 ms) trailing silence: first 3,200 samples (200 ms post-roll) = 0.04f, remaining 8,064 samples = 0.05f
    for (c in 0 until 22) {
      val chunk =
        FloatArray(512) { i ->
          if (c * 512 + i < MicSharingRecorder.POST_ROLL_SAMPLES) 0.04f else 0.05f
        }
      trimmer.onChunk512(chunk, tracker.onChunkProbability(0.10f))
    }

    val trimmed = trimmer.finishTurn()
    val expectedTotal = 3200 + 20480 + 5120 + 15360 + 3200 // 47,360 samples
    val preRollOk = kotlin.math.abs(trimmed.trimmedSamples.first() - 0.02f) < 1e-3f
    val midPauseOk = kotlin.math.abs(trimmed.trimmedSamples[3200 + 20480] - 0.03f) < 1e-3f
    val postRollOk = kotlin.math.abs(trimmed.trimmedSamples.last() - 0.04f) < 1e-3f
    val passed =
      trimmed.trimmedSamples.size == expectedTotal && preRollOk && midPauseOk && postRollOk

    return StepTestVerificationResult(
      testId = "S1_T1",
      passed = passed,
      headline =
        if (passed)
          "PASS · Kept 200ms pre-roll (3,200), 320ms mid-pause, and 200ms post-roll (3,200)"
        else "FAIL · Trimmer sample verification failed",
      details =
        buildString {
          appendLine("• Settings applied: Mode B, Save clips=ON, VAD=0.50/250ms/0.35/700ms")
          appendLine(
            "• Total trimmed samples: ${trimmed.trimmedSamples.size} (expected $expectedTotal = 2.96s @ 16kHz)"
          )
          appendLine("• Leading 760ms silence dropped; 200ms pre-roll (3,200 samples) kept: $preRollOk")
          appendLine("• Mid-turn 320ms pause (5,120 samples) preserved untouched: $midPauseOk")
          appendLine(
            "• Trailing 704ms silence: first 200ms post-roll (3,200 samples) kept & last 504ms dropped: $postRollOk"
          )
        }.trim(),
      timestampMs = System.currentTimeMillis(),
    )
  }

  private suspend fun verifyStep1Test2RecognizerRingBuffer(): StepTestVerificationResult {
    val config =
      com.example.audio.SileroVadConfig(
        startProbThreshold = 0.50f,
        startSpeechMs = 250,
        endProbThreshold = 0.35f,
        endSilenceMs = 700,
        sampleRateHz = 16000,
        chunkSamples = 512,
      )

    // Case A: earliest chunk with prob >= 0.35 minus 200 ms (3,200 samples) pre-roll
    val trackerA = com.example.audio.SileroVadTurnTracker(config)
    val trimmerA = com.example.audio.EdgeSilenceTrimmer()
    for (idx in 0 until 40) {
      val v = if (idx < 25) 0.11f else 0.12f
      trimmerA.onChunk512(FloatArray(512) { v }, trackerA.onChunkProbability(0.10f))
    }
    trimmerA.onChunk512(FloatArray(512) { 0.35f }, trackerA.onChunkProbability(0.40f))
    repeat(5) {
      trimmerA.onChunk512(FloatArray(512) { 0.36f }, trackerA.onChunkProbability(0.25f))
    }
    trackerA.notifyExternalSpeechConfirmed()
    val snapTrigger = trackerA.onChunkProbability(0.60f)
    trimmerA.onChunk512(FloatArray(512) { 0.70f }, snapTrigger)
    val resA = trimmerA.finishTurn()
    val caseAOk =
      snapTrigger.speechStartedByExternalConfirm &&
        resA.trimmedSamples.size == (3200 + 512 + 2560 + 512) &&
        kotlin.math.abs(resA.trimmedSamples[0] - 0.12f) < 1e-3f &&
        kotlin.math.abs(resA.trimmedSamples[3200] - 0.35f) < 1e-3f

    // Case B: no buffered chunk >= 0.35 -> keeps full 1.5s (24,000 samples) ring buffer
    val trackerB = com.example.audio.SileroVadTurnTracker(config)
    val trimmerB = com.example.audio.EdgeSilenceTrimmer()
    repeat(60) {
      trimmerB.onChunk512(FloatArray(512) { 0.22f }, trackerB.onChunkProbability(0.10f))
    }
    trimmerB.onRecognizerTriggeredStart()
    val resB = trimmerB.finishTurn()
    val caseBOk = resB.trimmedSamples.size == MicSharingRecorder.RECOGNIZER_RING_BUFFER_SAMPLES

    val passed = caseAOk && caseBOk
    return StepTestVerificationResult(
      testId = "S1_T2",
      passed = passed,
      headline =
        if (passed)
          "PASS · 1.5s (24,000-sample) ring buffer starts at earliest prob>=0.35 minus 200ms pre-roll"
        else "FAIL · Recognizer ring buffer verification failed",
      details =
        buildString {
          appendLine(
            "• Case A (chunk with prob=0.40 >= 0.35 in 1.5s buffer): kept 3,200 pre-roll + 3,584 speech = ${resA.trimmedSamples.size} samples (pass=$caseAOk)"
          )
          appendLine(
            "• Case B (no chunk with prob >= 0.35 in 60-chunk stream): kept full 1.5s buffer = ${resB.trimmedSamples.size} samples (expected ${MicSharingRecorder.RECOGNIZER_RING_BUFFER_SAMPLES}, pass=$caseBOk)"
          )
        }.trim(),
      timestampMs = System.currentTimeMillis(),
    )
  }

  private suspend fun verifyStep1Test3WindowSplitting(): StepTestVerificationResult {
    keyRepository.updateLabMicTestPreferences {
      it.copy(
        recordAlongsideRecognizer = true,
        micTestMode = "B",
        saveWavClips = false,
        vadStartThreshold = 0.50f,
        vadStartDurationMs = 250,
        vadEndThreshold = 0.35f,
        vadEndSilenceMs = 700,
      )
    }

    val windows10s = EmotionModelManager.splitInto4sWindows(FloatArray(160_000) { 0.1f })
    val windows8_5s = EmotionModelManager.splitInto4sWindows(FloatArray(136_000) { 0.1f })
    val averaged =
      EmotionModelManager.averageWindowPredictions(
        listOf(
          com.example.audio.EmotionPrediction(0.30f, 0.40f, 0.50f, 100L),
          com.example.audio.EmotionPrediction(0.50f, 0.60f, 0.70f, 120L),
          com.example.audio.EmotionPrediction(0.70f, 0.80f, 0.90f, 110L),
        )
      )
    val passed =
      windows10s.size == 3 &&
        windows8_5s.size == 2 &&
        averaged != null &&
        averaged.scoredWindowsCount == 3 &&
        kotlin.math.abs(averaged.arousal - 0.50f) < 1e-4f

    return StepTestVerificationResult(
      testId = "S1_T3",
      passed = passed,
      headline =
        if (passed)
          "PASS · 10.0s -> 3 windows (4s+4s+2s); 8.5s -> 2 windows (0.5s < 1.5s dropped); A/D/V averaged"
        else "FAIL · 4s window splitting or averaging mismatch",
      details =
        buildString {
          appendLine(
            "• 10.0s (160,000 samples): ${windows10s.size} windows [${windows10s.joinToString { "${it.size}" }}]"
          )
          appendLine(
            "• 8.5s (136,000 samples): ${windows8_5s.size} windows [${windows8_5s.joinToString { "${it.size}" }}] (0.5s remainder < 1.5s dropped)"
          )
          appendLine(
            "• Averaged 3 windows: A=${"%.2f".format(Locale.US, averaged?.arousal ?: 0f)}, D=${"%.2f".format(Locale.US, averaged?.dominance ?: 0f)}, V=${"%.2f".format(Locale.US, averaged?.valence ?: 0f)}, windows=${averaged?.scoredWindowsCount}, totalInferenceMs=${averaged?.inferenceMs}ms"
          )
        }.trim(),
      timestampMs = System.currentTimeMillis(),
    )
  }

  private suspend fun verifyStep1Test4BarrierAndLateUpdate(): StepTestVerificationResult {
    keyRepository.updateLabMicTestPreferences {
      it.copy(
        recordAlongsideRecognizer = true,
        micTestMode = "B",
        saveWavClips = false,
      )
    }

    val dummyCapture =
      MicCaptureResult(
        mode = "B",
        samples16kMono = FloatArray(32000) { 0.2f },
        pcm16Bytes = ByteArray(64000),
        clipLengthSec = 2.0f,
        rms = 0.2f,
        peak = 0.2f,
        silentCapture = false,
        recognizerPackage = "pkg",
        speechDurationSec = 1.8f,
      )

    // 1. Fast scoring (< 300 ms)
    val fastRes =
      LiveSessionManager.awaitEmotionScoresWithBarrier(
        turnRequestId = "r-selftest-fast",
        timeoutMs = 300L,
        finishCaptureBlock = { dummyCapture },
        scoreTurnBlock = {
          kotlinx.coroutines.delay(25L)
          Result.success(com.example.audio.EmotionPrediction(0.44f, 0.55f, 0.66f, 22L))
        },
      )

    // 2. Slow scoring (> 300 ms): times out at ~300 ms, then completes in background with "emotion_late" and totalMs
    val lateDeferred =
      kotlinx.coroutines.CompletableDeferred<Triple<String?, Long, com.example.audio.EmotionPrediction?>>()
    val slowRes =
      LiveSessionManager.awaitEmotionScoresWithBarrier(
        turnRequestId = "r-selftest-late",
        timeoutMs = 300L,
        finishCaptureBlock = { dummyCapture },
        scoreTurnBlock = {
          kotlinx.coroutines.delay(460L)
          Result.success(com.example.audio.EmotionPrediction(0.52f, 0.62f, 0.72f, 450L))
        },
        onCaptureAndScoreCompletedAsync = { _, scoreRes, totalMs, reason ->
          lateDeferred.complete(Triple(reason, totalMs, scoreRes.getOrNull()))
        },
      )

    val (lateReason, lateTotalMs, latePred) = lateDeferred.await()
    val passed =
      fastRes.prediction != null &&
        fastRes.failureReason == null &&
        slowRes.prediction == null &&
        slowRes.failureReason == LiveSessionManager.EMOTION_REASON_TIMEOUT &&
        lateReason == LiveSessionManager.EMOTION_REASON_LATE &&
        lateTotalMs >= 300L &&
        latePred != null

    return StepTestVerificationResult(
      testId = "S1_T4",
      passed = passed,
      headline =
        if (passed)
          "PASS · Fast=${fastRes.waitMs}ms (ok) · Slow released at ${slowRes.waitMs}ms (emotion_timeout) & updated at ${lateTotalMs}ms (emotion_late)"
        else "FAIL · Barrier or emotion_late callback check failed",
      details =
        buildString {
          appendLine(
            "• Fast path (<300ms): waitMs=${fastRes.waitMs}ms, status=ok, A=${fastRes.prediction?.arousal}"
          )
          appendLine(
            "• Slow path (>300ms): barrier released reply at waitMs=${slowRes.waitMs}ms with reason=${slowRes.failureReason}"
          )
          appendLine(
            "• Background completion: status=$lateReason, totalMs=${lateTotalMs}ms (>=300ms), A/D/V=${latePred?.arousal}/${latePred?.dominance}/${latePred?.valence}"
          )
        }.trim(),
      timestampMs = System.currentTimeMillis(),
    )
  }
}

data class StepTestVerificationResult(
  val testId: String,
  val passed: Boolean,
  val headline: String,
  val details: String,
  val timestampMs: Long,
)

