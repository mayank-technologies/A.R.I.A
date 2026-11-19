package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Room Repository managing ARC's persistent Long-Term Memory facts.
 * Provides reactive Flow updates for the Settings UI and prompt context snapshots for AI calls.
 */
class MemoryRepository(private val ariaDao: AriaDao) {

    val allMemories: Flow<List<MemoryFactEntity>> = ariaDao.getAllMemoryFacts()

    suspend fun getAllMemoriesOnce(): List<MemoryFactEntity> {
        return ariaDao.getAllMemoryFactsOnce()
    }

    /**
     * Inserts or updates a fact matching the same factKey (case-insensitive) to avoid duplicates.
     */
    suspend fun saveOrUpdateFact(
        factKey: String,
        factValue: String,
        category: String = "Preference",
        source: String = "Auto"
    ): Long {
        val cleanKey = factKey.trim()
        val cleanValue = factValue.trim()
        if (cleanKey.isBlank() || cleanValue.isBlank()) return -1L

        val now = System.currentTimeMillis()
        val existing = ariaDao.findMemoryByKey(cleanKey)
        val entity = if (existing != null) {
            existing.copy(
                factValue = cleanValue,
                category = category,
                source = source,
                updatedAt = now
            )
        } else {
            MemoryFactEntity(
                factKey = cleanKey,
                factValue = cleanValue,
                category = category,
                source = source,
                createdAt = now,
                updatedAt = now
            )
        }
        return ariaDao.insertMemoryFact(entity)
    }

    suspend fun deleteFact(id: Long) {
        ariaDao.deleteMemoryFactById(id)
    }

    suspend fun clearAllFacts() {
        ariaDao.clearAllMemoryFacts()
    }

    /**
     * Builds a concise bullet summary of saved user facts for injection into AI system prompts.
     */
    suspend fun buildPromptMemoryContext(maxItems: Int = 12): String {
        val facts = ariaDao.getAllMemoryFactsOnce().take(maxItems)
        if (facts.isEmpty()) return ""
        return buildString {
            append("SAVED LONG-TERM USER MEMORY (use subtly when relevant):\n")
            facts.forEach { fact ->
                append("- ${fact.factKey}: ${fact.factValue}\n")
            }
        }.trim()
    }
}
