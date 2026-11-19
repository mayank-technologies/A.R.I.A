package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.example.assistant.DeviceClockHelper
import com.example.speech.ArcVoiceGender
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

interface GeminiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContentFallback(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

/**
 * Safe runtime diagnostic audit for BuildConfig / .env API keys.
 * Never logs raw secret keys — reports only presence, length, placeholder status, and short prefix.
 */
data class ApiKeyAudit(
    val keyName: String,
    val isValid: Boolean,
    val isPlaceholder: Boolean,
    val length: Int,
    val maskedPreview: String,
    val statusLabel: String
)

data class GeminiTextQueryResult(
    val replyText: String,
    val isSuccess: Boolean,
    val errorDetail: String? = null
)

object GeminiClient {
    const val CONN_DEBUG_TAG = "ARC_CONN_DEBUG"
    const val PRIMARY_TEXT_MODEL = "gemini-2.5-flash"
    const val FALLBACK_TEXT_MODEL = "gemini-2.0-flash"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiService::class.java)
    }

    fun inspectApiKey(keyName: String, rawValue: String): ApiKeyAudit {
        val trimmed = rawValue.trim()
        val isPlaceholder = trimmed.isEmpty() ||
            trimmed.equals("your_key_here", ignoreCase = true) ||
            trimmed.equals("your_voice_id_here", ignoreCase = true) ||
            trimmed.equals("MY_GEMINI_API_KEY", ignoreCase = true) ||
            trimmed.equals("MY_ANTHROPIC_API_KEY", ignoreCase = true) ||
            trimmed.startsWith("YOUR_", ignoreCase = true)

        val preview = when {
            trimmed.isEmpty() -> "<empty>"
            isPlaceholder -> trimmed
            trimmed.length <= 8 -> "***(${trimmed.length} chars)"
            else -> "${trimmed.take(4)}...${trimmed.takeLast(2)}"
        }
        val status = when {
            trimmed.isEmpty() -> "EMPTY"
            isPlaceholder -> "PLACEHOLDER('$trimmed')"
            else -> "VALID_FORMAT(len=${trimmed.length}, preview=$preview)"
        }
        return ApiKeyAudit(
            keyName = keyName,
            isValid = !isPlaceholder,
            isPlaceholder = isPlaceholder,
            length = trimmed.length,
            maskedPreview = preview,
            statusLabel = status
        )
    }

    fun auditRuntimeKeys(): Map<String, ApiKeyAudit> {
        val gemini = inspectApiKey("GEMINI_API_KEY", BuildConfig.GEMINI_API_KEY)
        val anthropic = inspectApiKey("ANTHROPIC_API_KEY", BuildConfig.ANTHROPIC_API_KEY)
        val eleven = inspectApiKey("ELEVENLABS_API_KEY", BuildConfig.ELEVENLABS_API_KEY)
        val msg = "Runtime Key Audit -> GEMINI=${gemini.statusLabel} | ANTHROPIC=${anthropic.statusLabel} | ELEVENLABS=${eleven.statusLabel}"
        Log.d(CONN_DEBUG_TAG, msg)
        println("[$CONN_DEBUG_TAG] $msg")
        return mapOf(
            "GEMINI_API_KEY" to gemini,
            "ANTHROPIC_API_KEY" to anthropic,
            "ELEVENLABS_API_KEY" to eleven
        )
    }

    suspend fun queryAriaAiDetailed(
        prompt: String,
        apiKeyOverride: String? = null,
        memoryContext: String = "",
        voiceGender: ArcVoiceGender = ArcVoiceGender.MALE
    ): GeminiTextQueryResult {
        val rawKey = apiKeyOverride ?: BuildConfig.GEMINI_API_KEY
        val audit = inspectApiKey("GEMINI_API_KEY", rawKey)

        if (!audit.isValid) {
            val errMsg = "Connection failed: GEMINI_API_KEY is missing or placeholder (${audit.statusLabel}). Configure GEMINI_API_KEY in Secrets and rebuild."
            Log.e(CONN_DEBUG_TAG, "Gemini Text Chat CONNECTION_FAILURE | reason=$errMsg")
            println("[$CONN_DEBUG_TAG] Gemini Text Chat CONNECTION_FAILURE | reason=$errMsg")
            return GeminiTextQueryResult(
                replyText = errMsg,
                isSuccess = false,
                errorDetail = errMsg
            )
        }

        val apiKey = rawKey.trim()
        val clockInfo = DeviceClockHelper.getCurrentDeviceClockInfo(logDebug = false, source = "GeminiTextAI")
        val isFemale = voiceGender == ArcVoiceGender.FEMALE
        val genderWord = if (isFemale) "female" else "male"
        val grammarCue = if (isFemale) {
            "use feminine Hindi/Hinglish grammar when replying in Hindi/Hinglish (e.g., kar rahi hu, bata rahi hu)"
        } else {
            "use masculine Hindi/Hinglish grammar when replying in Hindi/Hinglish (e.g., kar raha hu, bata raha hu)"
        }
        val memoryBlock = if (memoryContext.isNotBlank()) "\n\n$memoryContext" else ""

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "You are ARC (Autonomous Reactive Core), a warm, JARVIS-inspired $genderWord voice assistant. Your name is ARC. Current device local time: ${clockInfo.formattedTime}, ${clockInfo.formattedDate} (${clockInfo.timeZoneId}). Speak naturally with subtle warmth and $grammarCue. Keep answers concise, helpful, and friendly (2-3 sentences max unless explaining code).$memoryBlock"
                    )
                )
            )
        )

        Log.d(CONN_DEBUG_TAG, "Gemini Text Chat CONNECTION_ATTEMPT | model=$PRIMARY_TEXT_MODEL | key=${audit.statusLabel}")
        println("[$CONN_DEBUG_TAG] Gemini Text Chat CONNECTION_ATTEMPT | model=$PRIMARY_TEXT_MODEL")

        return try {
            val res = try {
                service.generateContent(apiKey, request)
            } catch (httpEx: HttpException) {
                if (httpEx.code() == 404) {
                    Log.w(CONN_DEBUG_TAG, "Primary model $PRIMARY_TEXT_MODEL returned HTTP 404, retrying with $FALLBACK_TEXT_MODEL")
                    service.generateContentFallback(apiKey, request)
                } else {
                    throw httpEx
                }
            }
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (!text.isNullOrBlank()) {
                Log.d(CONN_DEBUG_TAG, "Gemini Text Chat CONNECTION_SUCCESS | replyChars=${text.length}")
                println("[$CONN_DEBUG_TAG] Gemini Text Chat CONNECTION_SUCCESS | replyChars=${text.length}")
                GeminiTextQueryResult(replyText = text, isSuccess = true)
            } else {
                val emptyErr = "Gemini responded with empty content. Please try asking again."
                Log.w(CONN_DEBUG_TAG, "Gemini Text Chat WARNING | $emptyErr")
                GeminiTextQueryResult(replyText = emptyErr, isSuccess = false, errorDetail = emptyErr)
            }
        } catch (httpEx: HttpException) {
            val errBody = try { httpEx.response()?.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
            val userMsg = when (httpEx.code()) {
                400, 401, 403 -> "AI connection rejected (HTTP ${httpEx.code()}). Check your GEMINI_API_KEY."
                429 -> "AI rate limit reached (HTTP 429). Please wait a moment and try again."
                else -> "AI server error (HTTP ${httpEx.code()}). Please try again."
            }
            Log.e(CONN_DEBUG_TAG, "Gemini Text Chat CONNECTION_FAILURE | http=${httpEx.code()} | body=$errBody", httpEx)
            println("[$CONN_DEBUG_TAG] Gemini Text Chat CONNECTION_FAILURE | http=${httpEx.code()} | msg=$userMsg")
            GeminiTextQueryResult(replyText = userMsg, isSuccess = false, errorDetail = "$userMsg ${errBody.orEmpty()}".trim())
        } catch (ioEx: IOException) {
            val netMsg = "AI connection failed, check internet (${ioEx.localizedMessage ?: "Network unreachable"})."
            Log.e(CONN_DEBUG_TAG, "Gemini Text Chat CONNECTION_FAILURE (Network) | ${ioEx.message}", ioEx)
            println("[$CONN_DEBUG_TAG] Gemini Text Chat CONNECTION_FAILURE (Network) | $netMsg")
            GeminiTextQueryResult(replyText = netMsg, isSuccess = false, errorDetail = netMsg)
        } catch (e: Exception) {
            val errMsg = "AI connection error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.e(CONN_DEBUG_TAG, "Gemini Text Chat CONNECTION_FAILURE | $errMsg", e)
            println("[$CONN_DEBUG_TAG] Gemini Text Chat CONNECTION_FAILURE | $errMsg")
            GeminiTextQueryResult(replyText = errMsg, isSuccess = false, errorDetail = errMsg)
        }
    }

    suspend fun queryAriaAi(prompt: String): String {
        return queryAriaAiDetailed(prompt).replyText
    }
}
