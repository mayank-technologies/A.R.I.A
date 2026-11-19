package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReminderEntity::class, VoiceHistoryEntity::class, MemoryFactEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AriaDatabase : RoomDatabase() {
    abstract fun ariaDao(): AriaDao

    companion object {
        @Volatile
        private var INSTANCE: AriaDatabase? = null

        /**
         * Explicit non-destructive migration from v1 to v2 so existing reminders and voice history
         * remain intact while adding the long_term_memories table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `long_term_memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `factKey` TEXT NOT NULL,
                        `factValue` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AriaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AriaDatabase::class.java,
                    "aria_assistant_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
