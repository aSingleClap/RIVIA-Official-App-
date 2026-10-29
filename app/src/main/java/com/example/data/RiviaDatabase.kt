package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
  exportSchema = false,
)
abstract class RiviaDatabase : RoomDatabase() {
  abstract fun riviaDao(): RiviaDao

  companion object {
    @Volatile private var INSTANCE: RiviaDatabase? = null

    fun getInstance(context: Context): RiviaDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                RiviaDatabase::class.java,
                "rivia_database.db",
              )
              .fallbackToDestructiveMigration(true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
