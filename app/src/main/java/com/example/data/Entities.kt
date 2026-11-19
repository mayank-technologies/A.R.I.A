package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timeString: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_history")
data class VoiceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val response: String,
    val category: String, // WEATHER, WIKIPEDIA, REMINDER, BROWSER, APP, TIME, AI
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "long_term_memories")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val factKey: String,
    val factValue: String,
    val category: String = "Preference",
    val source: String = "Auto",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

