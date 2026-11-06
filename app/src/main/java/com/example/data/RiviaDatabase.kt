package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities =
    [
      ChatEntity::class,
      MessageEntity::class,
      CachedModelEntity::class,
      RequestLogEntity::class,
      DefaultSettingsEntity::class,
      LayerDraftEntity::class,
      LayerLockEntity::class,
      LayerTestRunEntity::class,
      RuleEventEntity::class,
      LayerSettingsEntity::class,
      VoiceEmotionLogEntity::class,
      TurnDispatchLogEntity::class,
    ],
  version = 7,
  exportSchema = true,
)
abstract class RiviaDatabase : RoomDatabase() {
  abstract fun riviaDao(): RiviaDao

  companion object {
    @Volatile private var INSTANCE: RiviaDatabase? = null

    val MIGRATION_1_2: Migration =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          // 1. Alter chats and turn every existing chat into a Lab chat with Run settings untouched
          db.execSQL("ALTER TABLE `chats` ADD COLUMN `isLabChat` INTEGER NOT NULL DEFAULT 0")
          db.execSQL("UPDATE `chats` SET `isLabChat` = 1")
          db.execSQL(
            "ALTER TABLE `chats` ADD COLUMN `riviaProfile` TEXT NOT NULL DEFAULT 'balanced'"
          )
          db.execSQL("ALTER TABLE `chats` ADD COLUMN `pendingH7Notice` TEXT")

          // 2. Alter messages and mark existing messages as Lab
          db.execSQL("ALTER TABLE `messages` ADD COLUMN `chatKind` TEXT NOT NULL DEFAULT 'Rivia'")
          db.execSQL("UPDATE `messages` SET `chatKind` = 'Lab'")
          db.execSQL("ALTER TABLE `messages` ADD COLUMN `profile` TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE `messages` ADD COLUMN `channel` TEXT NOT NULL DEFAULT 'text'")
          db.execSQL("ALTER TABLE `messages` ADD COLUMN `fingerprint` TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            "ALTER TABLE `messages` ADD COLUMN `ruleEventsSummary` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL("ALTER TABLE `messages` ADD COLUMN `h7TrimmedFrom` INTEGER")
          db.execSQL("ALTER TABLE `messages` ADD COLUMN `h7TrimmedTo` INTEGER")

          // 3. Alter request_logs
          db.execSQL(
            "ALTER TABLE `request_logs` ADD COLUMN `chatKind` TEXT NOT NULL DEFAULT 'Lab'"
          )
          db.execSQL("ALTER TABLE `request_logs` ADD COLUMN `profile` TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE `request_logs` ADD COLUMN `channel` TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            "ALTER TABLE `request_logs` ADD COLUMN `fingerprint` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL("ALTER TABLE `request_logs` ADD COLUMN `ruleEvents` TEXT NOT NULL DEFAULT ''")

          // 4. Create new layer tables
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `layer_drafts` (`layerKey` TEXT NOT NULL, `jsonContent` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`layerKey`))"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `layer_locks` (`layerKey` TEXT NOT NULL, `pendingCanonicalSha256` TEXT, `lastSeenBundledSha256` TEXT, `mismatchWarning` TEXT, `exportedJson` TEXT, PRIMARY KEY(`layerKey`))"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `layer_test_runs` (`id` TEXT NOT NULL, `testId` TEXT NOT NULL, `profile` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `passed` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `layerVersionsJson` TEXT NOT NULL, `detailsJson` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rule_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `requestId` TEXT NOT NULL, `chatId` TEXT NOT NULL, `ruleId` TEXT NOT NULL, `eventType` TEXT NOT NULL, `detail` TEXT NOT NULL)"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `layer_settings` (`id` INTEGER NOT NULL, `spokenMaxSentences` INTEGER, `h11WatchWordsJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
          )
        }
      }

    val MIGRATION_2_3: Migration =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `voice_emotion_logs` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `requestId` TEXT NOT NULL,
              `mode` TEXT NOT NULL,
              `recognizerOk` INTEGER NOT NULL,
              `recognizerErrorCode` INTEGER,
              `recognizerPackage` TEXT NOT NULL,
              `clipLengthSec` REAL NOT NULL,
              `rms` REAL NOT NULL,
              `peak` REAL NOT NULL,
              `silentCapture` INTEGER NOT NULL,
              `arousal` REAL,
              `dominance` REAL,
              `valence` REAL,
              `inferenceMs` INTEGER,
              `captureStatus` TEXT NOT NULL,
              `wavFilePath` TEXT,
              `errorMessage` TEXT,
              `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_voice_emotion_logs_requestId` ON `voice_emotion_logs` (`requestId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_voice_emotion_logs_timestamp` ON `voice_emotion_logs` (`timestamp`)"
          )
        }
      }

    val MIGRATION_3_4: Migration =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `audioRecordStarted` INTEGER NOT NULL DEFAULT 1"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `pipeBytesWritten` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `partialResultsCount` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `callbacksFired` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `usedPartialFallback` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `vadSpeechDetected` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `vadEndedTurn` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `vadMaxProb` REAL NOT NULL DEFAULT 0.0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `transcriptPreview` TEXT NOT NULL DEFAULT ''"
          )
        }
      }

    val MIGRATION_4_5: Migration =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `finalText` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `pipeCloseToFinalMs` INTEGER"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `lastPartialText` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `transcriptSource` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `audioSource` TEXT NOT NULL DEFAULT 'VOICE_RECOGNITION'"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `inputDevice` TEXT NOT NULL DEFAULT 'unknown'"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `speechDurationSec` REAL NOT NULL DEFAULT 0.0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `wordCount` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `speakingRateWps` REAL"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `isShortSpeech` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `scoredWindowsCount` INTEGER NOT NULL DEFAULT 1"
          )
        }
      }

    val MIGRATION_5_6: Migration =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `turn_dispatch_logs` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `request_id` TEXT NOT NULL,
              `created_at` INTEGER NOT NULL,
              `payload_json` TEXT NOT NULL,
              `triage_json` TEXT,
              `corrected_json` TEXT,
              `tool_name` TEXT,
              `tool_args_json` TEXT,
              `tool_result_json` TEXT,
              `reply_text` TEXT,
              `status` TEXT NOT NULL,
              `error` TEXT,
              `timings_json` TEXT NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_turn_dispatch_logs_request_id` ON `turn_dispatch_logs` (`request_id`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_turn_dispatch_logs_created_at` ON `turn_dispatch_logs` (`created_at`)"
          )
        }
      }

    val MIGRATION_6_7: Migration =
      object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `calibration_label` TEXT"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `rms_after_gain` REAL"
          )
          db.execSQL(
            "ALTER TABLE `voice_emotion_logs` ADD COLUMN `applied_gain_db` REAL"
          )
        }
      }

    // Future schema changes must add explicit Migration(from, to) objects here;
    // fallbackToDestructiveMigration is never used so chats are never wiped.
    private val ALL_MIGRATIONS: Array<Migration> =
      arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
      )

    fun getInstance(context: Context): RiviaDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                RiviaDatabase::class.java,
                "rivia_database.db",
              )
              .addMigrations(*ALL_MIGRATIONS)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
