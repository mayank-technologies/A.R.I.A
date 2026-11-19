package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AriaDao {
    // Reminders
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, createdAt DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders WHERE isCompleted = 1")
    suspend fun clearCompletedReminders()

    // Voice History
    @Query("SELECT * FROM voice_history ORDER BY timestamp DESC LIMIT 50")
    fun getVoiceHistory(): Flow<List<VoiceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceHistory(history: VoiceHistoryEntity)

    @Query("DELETE FROM voice_history")
    suspend fun clearHistory()

    // Long-Term Memory Facts
    @Query("SELECT * FROM long_term_memories ORDER BY updatedAt DESC")
    fun getAllMemoryFacts(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM long_term_memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoryFactsOnce(): List<MemoryFactEntity>

    @Query("SELECT * FROM long_term_memories WHERE LOWER(factKey) = LOWER(:factKey) LIMIT 1")
    suspend fun findMemoryByKey(factKey: String): MemoryFactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryFact(fact: MemoryFactEntity): Long

    @Query("DELETE FROM long_term_memories WHERE id = :id")
    suspend fun deleteMemoryFactById(id: Long)

    @Query("DELETE FROM long_term_memories")
    suspend fun clearAllMemoryFacts()
}
