package com.example.data

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
  val status: String, // "ok", "warning", "error" or HTTP code
  val serviceTier: String,
  val inputTokens: Int,
  val outputTokens: Int,
  val thoughtTokens: Int,
  val latencyMs: Long,
  val error: String? = null,
)

@Entity(tableName = "default_settings")
data class DefaultSettingsEntity(
  @PrimaryKey val id: Int = 1,
  val runSettingsJson: String,
)
