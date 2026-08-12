package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReminderEntity::class, VoiceHistoryEntity::class], version = 1, exportSchema = false)
abstract class AriaDatabase : RoomDatabase() {
    abstract fun ariaDao(): AriaDao

    companion object {
        @Volatile
        private var INSTANCE: AriaDatabase? = null

        fun getDatabase(context: Context): AriaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AriaDatabase::class.java,
                    "aria_assistant_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
