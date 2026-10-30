package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
           '[]' AS rawStepsJson, createdAt
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

  // Request Logs (keep last 100)
  @Query("SELECT * FROM request_logs ORDER BY timestamp DESC LIMIT 100")
  fun observeRequestLogs(): Flow<List<RequestLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRequestLog(log: RequestLogEntity)

  @Query(
    "DELETE FROM request_logs WHERE id NOT IN (SELECT id FROM request_logs ORDER BY timestamp DESC LIMIT 100)"
  )
  suspend fun pruneOldRequestLogs()

  @Transaction
  suspend fun logRequestAndPrune(log: RequestLogEntity) {
    insertRequestLog(log)
    pruneOldRequestLogs()
  }

  // Default Run Settings
  @Query("SELECT * FROM default_settings WHERE id = 1 LIMIT 1")
  fun observeDefaultSettings(): Flow<DefaultSettingsEntity?>

  @Query("SELECT * FROM default_settings WHERE id = 1 LIMIT 1")
  suspend fun getDefaultSettings(): DefaultSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveDefaultSettings(entity: DefaultSettingsEntity)
}
