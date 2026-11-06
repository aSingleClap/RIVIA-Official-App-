package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class BaselineExcludedCounts(
  val excludedModeACount: Int = 0,
  val excludedShortCount: Int = 0,
  val excludedSilentCount: Int = 0,
  val excludedCalibratedCount: Int = 0,
)

data class BackupImportPayload(
  val chats: List<ChatEntity> = emptyList(),
  val messages: List<MessageEntity> = emptyList(),
  val layerDrafts: List<LayerDraftEntity> = emptyList(),
  val layerLocks: List<LayerLockEntity> = emptyList(),
  val layerTestRuns: List<LayerTestRunEntity> = emptyList(),
  val ruleEvents: List<RuleEventEntity> = emptyList(),
  val requestLogs: List<RequestLogEntity> = emptyList(),
  val voiceEmotionLogs: List<VoiceEmotionLogEntity> = emptyList(),
  val turnDispatchLogs: List<TurnDispatchLogEntity> = emptyList(),
  val layerSettings: LayerSettingsEntity? = null,
)

@Dao
interface RiviaDao {
  // Chats
  @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
  fun observeAllChats(): Flow<List<ChatEntity>>

  @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
  suspend fun getAllChats(): List<ChatEntity>

  @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
  suspend fun getChatById(chatId: String): ChatEntity?

  @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
  fun observeChatById(chatId: String): Flow<ChatEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChat(chat: ChatEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertChatIgnore(chat: ChatEntity): Long

  @Update
  suspend fun updateChat(chat: ChatEntity)

  @Query("DELETE FROM chats WHERE id = :chatId")
  suspend fun deleteChatById(chatId: String)

  @Query("DELETE FROM chats")
  suspend fun deleteAllChats()

  // Messages — Fix #12: Don't load rawStepsJson in the chat's message list query
  @Query(
    """
    SELECT id, chatId, role, content, thoughtSummary, requestId, interactionId,
           previousInteractionId, model, serviceTier, inputTokens, outputTokens,
           thoughtTokens, ttftMs, totalTimeMs, status, errorMessage, voiceLabel,
           '[]' AS rawStepsJson, createdAt,
           chatKind, profile, channel, fingerprint, ruleEventsSummary, h7TrimmedFrom, h7TrimmedTo
    FROM messages
    WHERE chatId = :chatId
    ORDER BY createdAt ASC
    """
  )
  fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>>

  // Full messages query including rawStepsJson for history reconstruction and JSON export
  @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
  suspend fun getMessagesForChat(chatId: String): List<MessageEntity>

  @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
  suspend fun getMessageById(messageId: String): MessageEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: MessageEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessages(messages: List<MessageEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertMessagesIgnore(messages: List<MessageEntity>): List<Long>

  @Update
  suspend fun updateMessage(message: MessageEntity)

  @Query(
    """
    UPDATE messages
    SET content = :content,
        thoughtSummary = :thoughtSummary,
        interactionId = :interactionId,
        serviceTier = :serviceTier,
        ttftMs = :ttftMs,
        status = :status
    WHERE id = :id
    """
  )
  suspend fun updateStreamingProgress(
    id: String,
    content: String,
    thoughtSummary: String,
    interactionId: String?,
    serviceTier: String,
    ttftMs: Long,
    status: String,
  )

  @Query("DELETE FROM messages WHERE chatId = :chatId AND createdAt >= :fromTimestamp")
  suspend fun deleteMessagesFromTimestamp(chatId: String, fromTimestamp: Long)

  // Cached Models
  @Query("SELECT * FROM cached_models ORDER BY id ASC")
  fun observeCachedModels(): Flow<List<CachedModelEntity>>

  @Query("SELECT * FROM cached_models ORDER BY id ASC")
  suspend fun getCachedModels(): List<CachedModelEntity>

  @Query("DELETE FROM cached_models")
  suspend fun clearCachedModels()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCachedModels(models: List<CachedModelEntity>)

  @Transaction
  suspend fun replaceCachedModels(models: List<CachedModelEntity>) {
    clearCachedModels()
    insertCachedModels(models)
  }

  // Request Logs (display query keeps LIMIT 100; export query returns all rows; no pruning)
  @Query("SELECT * FROM request_logs ORDER BY timestamp DESC LIMIT 100")
  fun observeRequestLogs(): Flow<List<RequestLogEntity>>

  @Query("SELECT COUNT(*) FROM request_logs")
  fun observeTotalRequestLogsCount(): Flow<Int>

  @Query("SELECT * FROM request_logs ORDER BY timestamp DESC")
  suspend fun getAllRequestLogsForExport(): List<RequestLogEntity>

  @Query(
    """
    SELECT EXISTS(
      SELECT 1 FROM request_logs
      WHERE timestamp = :timestamp
        AND (
          (:requestId != '' AND requestId = :requestId)
          OR (:requestId = '' AND requestId = '' AND model = :model)
        )
    )
    """
  )
  suspend fun existsRequestLog(requestId: String, timestamp: Long, model: String): Boolean

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRequestLog(log: RequestLogEntity)

  @Transaction
  suspend fun logRequestAndPrune(log: RequestLogEntity) {
    insertRequestLog(log)
  }

  // Default Run Settings (Lab defaults)
  @Query("SELECT * FROM default_settings WHERE id = 1 LIMIT 1")
  fun observeDefaultSettings(): Flow<DefaultSettingsEntity?>

  @Query("SELECT * FROM default_settings WHERE id = 1 LIMIT 1")
  suspend fun getDefaultSettings(): DefaultSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveDefaultSettings(entity: DefaultSettingsEntity)

  // Layer Drafts
  @Query("SELECT * FROM layer_drafts")
  fun observeLayerDrafts(): Flow<List<LayerDraftEntity>>

  @Query("SELECT * FROM layer_drafts")
  suspend fun getLayerDrafts(): List<LayerDraftEntity>

  @Query("SELECT * FROM layer_drafts WHERE layerKey = :layerKey LIMIT 1")
  suspend fun getLayerDraft(layerKey: String): LayerDraftEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveLayerDraft(draft: LayerDraftEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertLayerDraftIgnore(draft: LayerDraftEntity): Long

  @Query("DELETE FROM layer_drafts WHERE layerKey = :layerKey")
  suspend fun deleteLayerDraft(layerKey: String)

  @Query("DELETE FROM layer_drafts")
  suspend fun clearAllLayerDrafts()

  // Layer Locks
  @Query("SELECT * FROM layer_locks")
  fun observeLayerLocks(): Flow<List<LayerLockEntity>>

  @Query("SELECT * FROM layer_locks")
  suspend fun getLayerLocks(): List<LayerLockEntity>

  @Query("SELECT * FROM layer_locks WHERE layerKey = :layerKey LIMIT 1")
  suspend fun getLayerLock(layerKey: String): LayerLockEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveLayerLock(lock: LayerLockEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertLayerLockIgnore(lock: LayerLockEntity): Long

  @Query("DELETE FROM layer_locks WHERE layerKey = :layerKey")
  suspend fun deleteLayerLock(layerKey: String)

  // Layer Test Runs
  @Query("SELECT * FROM layer_test_runs ORDER BY timestamp DESC")
  fun observeLayerTestRuns(): Flow<List<LayerTestRunEntity>>

  @Query("SELECT * FROM layer_test_runs ORDER BY timestamp DESC")
  suspend fun getLayerTestRuns(): List<LayerTestRunEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLayerTestRun(run: LayerTestRunEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertLayerTestRunsIgnore(runs: List<LayerTestRunEntity>): List<Long>

  // Rule Events (display query keeps LIMIT 20; export query returns all rows)
  @Query("SELECT * FROM rule_events ORDER BY timestamp DESC LIMIT 20")
  fun observeRecentRuleEvents(): Flow<List<RuleEventEntity>>

  @Query("SELECT * FROM rule_events ORDER BY timestamp DESC")
  suspend fun getAllRuleEventsForExport(): List<RuleEventEntity>

  @Query(
    """
    SELECT EXISTS(
      SELECT 1 FROM rule_events
      WHERE timestamp = :timestamp
        AND ruleId = :ruleId
        AND eventType = :eventType
        AND (
          (:requestId != '' AND requestId = :requestId)
          OR (:requestId = '' AND requestId = '')
        )
    )
    """
  )
  suspend fun existsRuleEvent(
    requestId: String,
    timestamp: Long,
    ruleId: String,
    eventType: String,
  ): Boolean

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRuleEvent(event: RuleEventEntity)

  // Layer Settings (spoken_max_sentences + H11 watch words; id=1 for Rivia/Dev, id=2 for Labs sandbox)
  @Query("SELECT * FROM layer_settings WHERE id = 1 LIMIT 1")
  fun observeLayerSettings(): Flow<LayerSettingsEntity?>

  @Query("SELECT * FROM layer_settings WHERE id = :id LIMIT 1")
  fun observeLayerSettingsById(id: Int): Flow<LayerSettingsEntity?>

  @Query("SELECT * FROM layer_settings WHERE id = 1 LIMIT 1")
  suspend fun getLayerSettings(): LayerSettingsEntity?

  @Query("SELECT * FROM layer_settings WHERE id = :id LIMIT 1")
  suspend fun getLayerSettingsById(id: Int): LayerSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveLayerSettings(settings: LayerSettingsEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertLayerSettingsIgnore(settings: LayerSettingsEntity): Long

  // Voice Emotion & Mic-Sharing Logs (display query keeps LIMIT 50; export query returns all rows; no pruning)
  @Query("SELECT * FROM voice_emotion_logs ORDER BY timestamp DESC LIMIT 50")
  fun observeVoiceEmotionLogs(): Flow<List<VoiceEmotionLogEntity>>

  @Query("SELECT COUNT(*) FROM voice_emotion_logs")
  fun observeTotalVoiceEmotionLogsCount(): Flow<Int>

  @Query("SELECT * FROM voice_emotion_logs ORDER BY timestamp DESC")
  suspend fun getAllVoiceEmotionLogsForExport(): List<VoiceEmotionLogEntity>

  @Query(
    """
    SELECT * FROM voice_emotion_logs
    WHERE timestamp >= :windowStart AND timestamp <= :windowEnd
      AND (:excludeRequestId = '' OR requestId != :excludeRequestId)
      AND mode = 'B'
      AND isShortSpeech = 0
      AND speechDurationSec >= :minSpeechSec
      AND silentCapture = 0
      AND arousal IS NOT NULL
      AND dominance IS NOT NULL
      AND valence IS NOT NULL
      AND (calibration_label IS NULL OR calibration_label = '')
    ORDER BY timestamp DESC
    LIMIT :maxRows
    """
  )
  suspend fun getEligibleBaselineLogs(
    windowStart: Long,
    windowEnd: Long,
    minSpeechSec: Float = 1.5f,
    maxRows: Int = 500,
    excludeRequestId: String = "",
  ): List<VoiceEmotionLogEntity>

  @Query(
    """
    SELECT
      COALESCE(SUM(CASE WHEN mode != 'B' THEN 1 ELSE 0 END), 0) AS excludedModeACount,
      COALESCE(SUM(CASE WHEN mode = 'B' AND (isShortSpeech != 0 OR speechDurationSec < :minSpeechSec) THEN 1 ELSE 0 END), 0) AS excludedShortCount,
      COALESCE(SUM(CASE WHEN mode = 'B' AND silentCapture != 0 THEN 1 ELSE 0 END), 0) AS excludedSilentCount,
      COALESCE(SUM(CASE WHEN mode = 'B' AND calibration_label IS NOT NULL AND calibration_label != '' THEN 1 ELSE 0 END), 0) AS excludedCalibratedCount
    FROM voice_emotion_logs
    WHERE timestamp >= :windowStart AND timestamp <= :windowEnd
      AND (:excludeRequestId = '' OR requestId != :excludeRequestId)
    """
  )
  suspend fun getBaselineExcludedCounts(
    windowStart: Long,
    windowEnd: Long,
    minSpeechSec: Float = 1.5f,
    excludeRequestId: String = "",
  ): BaselineExcludedCounts

  @Query(
    """
    SELECT EXISTS(
      SELECT 1 FROM voice_emotion_logs
      WHERE timestamp = :timestamp
        AND (
          (:requestId != '' AND requestId = :requestId)
          OR (:requestId = '' AND requestId = '' AND mode = :mode)
        )
    )
    """
  )
  suspend fun existsVoiceEmotionLog(requestId: String, timestamp: Long, mode: String): Boolean

  @Query("SELECT * FROM voice_emotion_logs WHERE requestId = :requestId ORDER BY timestamp DESC LIMIT 1")
  fun observeVoiceEmotionLogByRequestId(requestId: String): Flow<VoiceEmotionLogEntity?>

  @Query("SELECT * FROM voice_emotion_logs WHERE requestId = :requestId ORDER BY timestamp DESC LIMIT 1")
  suspend fun getVoiceEmotionLogByRequestId(requestId: String): VoiceEmotionLogEntity?

  @Query("UPDATE voice_emotion_logs SET calibration_label = :calibrationLabel WHERE id = :id")
  suspend fun updateVoiceEmotionCalibrationLabel(id: Long, calibrationLabel: String?)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertVoiceEmotionLog(log: VoiceEmotionLogEntity): Long

  @Update
  suspend fun updateVoiceEmotionLog(log: VoiceEmotionLogEntity)

  @Transaction
  suspend fun logVoiceEmotionAndPrune(log: VoiceEmotionLogEntity): Long {
    return insertVoiceEmotionLog(log)
  }

  // Turn Dispatch Logs (Step 2: no pruning; export query has no LIMIT; backup import deduplicates by request_id)
  @Query("SELECT * FROM turn_dispatch_logs ORDER BY created_at DESC LIMIT 50")
  fun observeTurnDispatchLogs(): Flow<List<TurnDispatchLogEntity>>

  @Query("SELECT * FROM turn_dispatch_logs ORDER BY created_at DESC")
  suspend fun getAllTurnDispatchLogsForExport(): List<TurnDispatchLogEntity>

  @Query("SELECT * FROM turn_dispatch_logs WHERE request_id = :requestId ORDER BY created_at DESC LIMIT 1")
  suspend fun getTurnDispatchLogByRequestId(requestId: String): TurnDispatchLogEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM turn_dispatch_logs WHERE request_id = :requestId)")
  suspend fun existsTurnDispatchLog(requestId: String): Boolean

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTurnDispatchLog(log: TurnDispatchLogEntity): Long

  @Update
  suspend fun updateTurnDispatchLog(log: TurnDispatchLogEntity)

  /**
   * Runs the entire backup import inside a single SQLite transaction:
   * - Uses OnConflictStrategy.IGNORE for chats, messages, layer_drafts, layer_locks, layer_test_runs, layer_settings
   *   so existing rows are never overwritten or deleted (and messages are never cascade-deleted).
   * - Deduplicates rule_events, request_logs, voice_emotion_logs, and turn_dispatch_logs by natural key.
   * Returns true if any new layer_drafts row was inserted.
   */
  @Transaction
  suspend fun importBackupInTransaction(payload: BackupImportPayload): Boolean {
    for (chat in payload.chats) {
      insertChatIgnore(chat)
    }
    if (payload.messages.isNotEmpty()) {
      insertMessagesIgnore(payload.messages)
    }
    var anyDraftInserted = false
    for (draft in payload.layerDrafts) {
      if (insertLayerDraftIgnore(draft) != -1L) {
        anyDraftInserted = true
      }
    }
    for (lock in payload.layerLocks) {
      insertLayerLockIgnore(lock)
    }
    if (payload.layerTestRuns.isNotEmpty()) {
      insertLayerTestRunsIgnore(payload.layerTestRuns)
    }
    for (re in payload.ruleEvents) {
      if (!existsRuleEvent(re.requestId, re.timestamp, re.ruleId, re.eventType)) {
        insertRuleEvent(re)
      }
    }
    for (rl in payload.requestLogs) {
      if (!existsRequestLog(rl.requestId, rl.timestamp, rl.model)) {
        insertRequestLog(rl)
      }
    }
    for (ve in payload.voiceEmotionLogs) {
      if (!existsVoiceEmotionLog(ve.requestId, ve.timestamp, ve.mode)) {
        insertVoiceEmotionLog(ve)
      }
    }
    for (td in payload.turnDispatchLogs) {
      if (td.requestId.isNotBlank() && !existsTurnDispatchLog(td.requestId)) {
        insertTurnDispatchLog(td)
      }
    }
    if (payload.layerSettings != null) {
      insertLayerSettingsIgnore(payload.layerSettings)
    }
    return anyDraftInserted
  }
}
