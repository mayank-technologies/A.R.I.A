package com.example.speech

import android.content.Context

/**
 * Supported voice gender modes for ARC across Gemini Live, ElevenLabs, and Android Native TTS.
 * - MALE   -> Gemini Live "Charon" (warm lower-pitch JARVIS style) + ElevenLabs Adam
 * - FEMALE -> Gemini Live "Aoede"  (soft, warm, natural conversational female) + ElevenLabs Rachel
 */
enum class ArcVoiceGender {
    MALE,
    FEMALE
}

/**
 * Persists and resolves user's Voice Gender choice in SharedPreferences ("aria_prefs")
 * so the choice survives app restarts and switches seamlessly at runtime.
 */
object VoicePreferencesManager {

    const val PREFS_NAME = "aria_prefs"
    const val KEY_VOICE_GENDER = "arc_voice_gender"

    const val GEMINI_MALE_VOICE = "Charon"
    // Gemini Live prebuilt warm, soft, expressive female voice ("Aoede" — melodic & natural)
    const val GEMINI_FEMALE_VOICE = "Aoede"

    const val ELEVENLABS_MALE_VOICE_ID = "pNInz6obpgDQGcFmaJgB"   // Adam (Male)
    const val ELEVENLABS_FEMALE_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel (Calm, Soft, Natural Female)

    fun getVoiceGender(context: Context): ArcVoiceGender {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VOICE_GENDER, ArcVoiceGender.MALE.name).orEmpty()
        return if (raw.equals(ArcVoiceGender.FEMALE.name, ignoreCase = true)) {
            ArcVoiceGender.FEMALE
        } else {
            ArcVoiceGender.MALE
        }
    }

    fun setVoiceGender(context: Context, gender: ArcVoiceGender) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_VOICE_GENDER, gender.name)
            .putString("elevenlabs_voice_id", elevenLabsVoiceIdFor(gender))
            .apply()
    }

    fun geminiVoiceNameFor(gender: ArcVoiceGender): String {
        return when (gender) {
            ArcVoiceGender.MALE -> GEMINI_MALE_VOICE
            ArcVoiceGender.FEMALE -> GEMINI_FEMALE_VOICE
        }
    }

    fun elevenLabsVoiceIdFor(gender: ArcVoiceGender): String {
        return when (gender) {
            ArcVoiceGender.MALE -> ELEVENLABS_MALE_VOICE_ID
            ArcVoiceGender.FEMALE -> ELEVENLABS_FEMALE_VOICE_ID
        }
    }
}
