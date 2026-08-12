package com.example.assistant

import android.util.Log
import com.example.api.GeminiClient

sealed class TranslationActionResult {
    data class Success(val message: String, val translatedText: String, val sourceLang: String, val targetLang: String) : TranslationActionResult()
    data class Failure(val message: String) : TranslationActionResult()
}

object CloudTranslationVoiceHandler {

    private const val TAG = "CloudTranslationVoiceHandler"

    fun isTranslationCommand(queryLower: String): Boolean {
        return queryLower.contains("translate") ||
                queryLower.contains("translation") ||
                queryLower.contains("anuvad") ||
                queryLower.contains("meaning in hindi") ||
                queryLower.contains("meaning in english") ||
                queryLower.contains("ko hindi mein") ||
                queryLower.contains("ko english mein") ||
                queryLower.contains("in spanish") ||
                queryLower.contains("in french") ||
                queryLower.contains("in german")
    }

    suspend fun processTranslationCommand(rawQuery: String): TranslationActionResult {
        Log.d(TAG, "Processing Cloud Translation command: $rawQuery")

        return try {
            val translationPrompt = """
                You are ARIA's Cloud Translation Engine.
                The user asks: "$rawQuery"
                
                Please perform the requested language translation accurately.
                If target language is not explicitly stated, infer it logically (e.g. Hindi to English or English to Hindi).
                
                Format your response clearly as:
                Translated Text: <translation>
                Response: <short conversational explanation in Hindi/Hinglish e.g. "'Hello' ka Hindi anuvad hai: 'नमस्ते'">
            """.trimIndent()

            val aiResponse = GeminiClient.queryAriaAi(translationPrompt)

            TranslationActionResult.Success(
                message = aiResponse,
                translatedText = aiResponse,
                sourceLang = "Auto-detected",
                targetLang = "Target"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Translation error: ${e.message}", e)
            TranslationActionResult.Failure("Translation failed: ${e.localizedMessage}")
        }
    }
}
