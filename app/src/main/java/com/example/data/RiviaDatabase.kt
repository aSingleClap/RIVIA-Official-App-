package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
  entities =
    [
      ChatEntity::class,
      MessageEntity::class,
      CachedModelEntity::class,
      RequestLogEntity::class,
      DefaultSettingsEntity::class,
    ],
  version = 1,
  exportSchema = true,
)
abstract class RiviaDatabase : RoomDatabase() {
  abstract fun riviaDao(): RiviaDao

  companion object {
    @Volatile private var INSTANCE: RiviaDatabase? = null

    // Future schema changes must add explicit Migration(from, to) objects here;
    // fallbackToDestructiveMigration is never used so chats are never wiped.
    private val ALL_MIGRATIONS: Array<Migration> = emptyArray()

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
