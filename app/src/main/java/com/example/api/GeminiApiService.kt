package com.example.api

import com.example.BuildConfig
import com.example.assistant.DeviceClockHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
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
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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

    suspend fun queryAriaAi(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "your_key_here") {
            return "ARC AI mode active: I can help answer questions, calculate math, check weather, set reminders, and explain Python code!"
        }
        val clockInfo = DeviceClockHelper.getCurrentDeviceClockInfo(logDebug = false, source = "GeminiTextAI")
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are ARC (Autonomous Reactive Core), a warm, JARVIS-inspired male voice assistant. Your name is ARC. Current device local time: ${clockInfo.formattedTime}, ${clockInfo.formattedDate} (${clockInfo.timeZoneId}). Speak naturally with subtle warmth and use masculine Hindi/Hinglish grammar when replying in Hindi/Hinglish (e.g., kar raha hu, bata raha hu). Keep answers concise, helpful, and friendly (2-3 sentences max unless explaining code).")))
        )
        return try {
            val res = service.generateContent(apiKey, request)
            res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I processed your request, Boss."
        } catch (e: Exception) {
            "ARC AI: ${e.localizedMessage ?: "Connection error. Reverting to local ARIA modules."}"
        }
    }
}
