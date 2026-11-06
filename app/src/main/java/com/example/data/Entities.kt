package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
  @PrimaryKey val id: String,
  val title: String,
  val createdAt: Long,
  val updatedAt: Long,
  val runSettingsJson: String,
  val lastInteractionId: String? = null,
  @ColumnInfo(defaultValue = "0") val isLabChat: Boolean = false,
  @ColumnInfo(defaultValue = "'balanced'") val riviaProfile: String = "balanced",
  val pendingH7Notice: String? = null,
)

@Entity(
  tableName = "messages",
  foreignKeys =
    [
      ForeignKey(
        entity = ChatEntity::class,
        parentColumns = ["id"],
        childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("chatId"), Index("createdAt")],
)
data class MessageEntity(
  @PrimaryKey val id: String,
  val chatId: String,
  val role: String, // "user" or "assistant"
  val content: String,
  val thoughtSummary: String = "",
  val requestId: String = "", // e.g., r-8c41
  val interactionId: String? = null,
  val previousInteractionId: String? = null,
  val model: String = "",
  val serviceTier: String = "standard",
  val inputTokens: Int = 0,
  val outputTokens: Int = 0,
  val thoughtTokens: Int = 0,
  val ttftMs: Long = 0L,
  val totalTimeMs: Long = 0L,
  val status: String = "completed", // "streaming", "polling", "completed", "error"
  val errorMessage: String? = null,
  val voiceLabel: String? = null, // e.g. "voice · 0:04" in Live mode
  val rawStepsJson: String = "[]", // Exact raw SSE steps (including thought_signature)
  val createdAt: Long = System.currentTimeMillis(),
  @ColumnInfo(defaultValue = "'Rivia'") val chatKind: String = "Rivia",
  @ColumnInfo(defaultValue = "''") val profile: String = "",
  @ColumnInfo(defaultValue = "'text'") val channel: String = "text",
  @ColumnInfo(defaultValue = "''") val fingerprint: String = "",
  @ColumnInfo(defaultValue = "''") val ruleEventsSummary: String = "",
  val h7TrimmedFrom: Int? = null,
  val h7TrimmedTo: Int? = null,
)

@Entity(tableName = "cached_models")
data class CachedModelEntity(
  @PrimaryKey val id: String,
  val displayName: String,
  val inputTokenLimit: Int,
  val outputTokenLimit: Int,
  val maxTemperature: Float? = null,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "request_logs")
data class RequestLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val requestId: String,
  val model: String,
  val status: String, // "ok", "warning", "error", "empty" or HTTP code
  val serviceTier: String,
  val inputTokens: Int,
  val outputTokens: Int,
  val thoughtTokens: Int,
  val latencyMs: Long,
  val error: String? = null,
  @ColumnInfo(defaultValue = "'Lab'") val chatKind: String = "Lab",
  @ColumnInfo(defaultValue = "''") val profile: String = "",
  @ColumnInfo(defaultValue = "''") val channel: String = "",
  @ColumnInfo(defaultValue = "''") val fingerprint: String = "",
  @ColumnInfo(defaultValue = "''") val ruleEvents: String = "",
)

@Entity(tableName = "default_settings")
data class DefaultSettingsEntity(
  @PrimaryKey val id: Int = 1,
  val runSettingsJson: String,
)

@Entity(tableName = "layer_drafts")
data class LayerDraftEntity(
  @PrimaryKey val layerKey: String,
  val jsonContent: String,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "layer_locks")
data class LayerLockEntity(
  @PrimaryKey val layerKey: String,
  val pendingCanonicalSha256: String? = null,
  val lastSeenBundledSha256: String? = null,
  val mismatchWarning: String? = null,
  val exportedJson: String? = null,
)

@Entity(tableName = "layer_test_runs")
data class LayerTestRunEntity(
  @PrimaryKey val id: String,
  val testId: String,
  val profile: String,
  val fingerprint: String,
  val passed: Boolean,
  val timestamp: Long = System.currentTimeMillis(),
  val layerVersionsJson: String = "{}",
  val detailsJson: String = "{}",
  val notes: String = "",
)

@Entity(tableName = "rule_events")
data class RuleEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val requestId: String,
  val chatId: String,
  val ruleId: String,
  val eventType: String,
  val detail: String,
)

@Entity(tableName = "layer_settings")
data class LayerSettingsEntity(
  @PrimaryKey val id: Int = 1,
  val spokenMaxSentences: Int? = null,
  val h11WatchWordsJson: String = "[]",
)

@Entity(
  tableName = "voice_emotion_logs",
  indices = [Index("requestId"), Index("timestamp")],
)
data class VoiceEmotionLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val requestId: String,
  val mode: String, // "A" or "B"
  val recognizerOk: Boolean,
  val recognizerErrorCode: Int? = null,
  val recognizerPackage: String,
  val clipLengthSec: Float,
  val rms: Float,
  val peak: Float,
  val silentCapture: Boolean,
  val arousal: Float? = null,
  val dominance: Float? = null,
  val valence: Float? = null,
  val inferenceMs: Long? = null,
  val captureStatus: String = "OK",
  val wavFilePath: String? = null,
  val errorMessage: String? = null,
  val timestamp: Long = System.currentTimeMillis(),
  // Pass 2 Mode B Fix & Silero VAD diagnostics
  val audioRecordStarted: Boolean = true,
  val pipeBytesWritten: Long = 0L,
  val partialResultsCount: Int = 0,
  val callbacksFired: String = "",
  val usedPartialFallback: Boolean = false,
  val vadSpeechDetected: Boolean = false,
  val vadEndedTurn: Boolean = false,
  val vadMaxProb: Float = 0f,
  val transcriptPreview: String = "",
  // Pass 2 Mode B Refinement: final vs partial timing, device/source tags, speaking rate, short flag, 4s windows
  val finalText: String = "",
  val pipeCloseToFinalMs: Long? = null,
  val lastPartialText: String = "",
  val transcriptSource: String = "", // "onResults", "onSegmentResults", "used partial", "none"
  val audioSource: String = "VOICE_RECOGNITION",
  val inputDevice: String = "unknown", // getRoutedDevice type + name
  val speechDurationSec: Float = 0f, // Silero speech time in seconds
  val wordCount: Int = 0,
  val speakingRateWps: Float? = null, // words / seconds of Silero speech
  val isShortSpeech: Boolean = false, // flagged "short" when < 1.5 s of speech
  val scoredWindowsCount: Int = 1, // number of 4 s windows averaged
  @ColumnInfo(name = "calibration_label") val calibrationLabel: String? = null,
  @ColumnInfo(name = "rms_after_gain") val rmsAfterGain: Float? = null,
  @ColumnInfo(name = "applied_gain_db") val appliedGainDb: Float? = null,
)

/**
 * Step 2: Turn dispatch log table for Rivia live voice turns.
 * Columns: id, request_id, created_at, payload_json, triage_json, corrected_json,
 * tool_name, tool_args_json, tool_result_json, reply_text, status, error, timings_json.
 */
@Entity(
  tableName = "turn_dispatch_logs",
  indices = [Index(value = ["request_id"]), Index(value = ["created_at"])],
)
data class TurnDispatchLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  @ColumnInfo(name = "request_id") val requestId: String,
  @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "payload_json") val payloadJson: String,
  @ColumnInfo(name = "triage_json") val triageJson: String? = null,
  @ColumnInfo(name = "corrected_json") val correctedJson: String? = null,
  @ColumnInfo(name = "tool_name") val toolName: String? = null,
  @ColumnInfo(name = "tool_args_json") val toolArgsJson: String? = null,
  @ColumnInfo(name = "tool_result_json") val toolResultJson: String? = null,
  @ColumnInfo(name = "reply_text") val replyText: String? = null,
  @ColumnInfo(name = "status") val status: String = "pending",
  @ColumnInfo(name = "error") val error: String? = null,
  @ColumnInfo(name = "timings_json") val timingsJson: String = "{}",
)
