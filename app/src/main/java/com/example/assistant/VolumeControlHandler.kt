package com.example.assistant

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

sealed class VolumeControlResult {
    data class Success(val message: String) : VolumeControlResult()
}

object VolumeControlHandler {
    private const val TAG = "VolumeControlHandler"

    fun isVolumeCommand(queryLower: String): Boolean {
        return queryLower.contains("volume") ||
                queryLower.contains("aawaaz") ||
                queryLower.contains("aawaz") ||
                queryLower.contains("sound") ||
                queryLower.contains("mute") ||
                queryLower.contains("unmute") ||
                queryLower.contains("louder") ||
                queryLower.contains("quieter")
    }

    fun processVolumeCommand(context: Context, queryLower: String): VolumeControlResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return VolumeControlResult.Success("Unable to access Audio Manager on this device.")

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        if (maxVolume == 0) {
            return VolumeControlResult.Success("Volume control is unavailable on this device.")
        }

        if (queryLower.contains("mute") && !queryLower.contains("unmute")) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            Log.d(TAG, "Muted stream volume")
            return VolumeControlResult.Success("Volume muted, Boss.")
        }

        if (queryLower.contains("unmute")) {
            val targetVol = (maxVolume * 0.3).roundToInt().coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            Log.d(TAG, "Unmuted stream volume to $targetVol")
            return VolumeControlResult.Success("Volume unmuted and set to 30%, Boss.")
        }

        if (queryLower.contains("max") || queryLower.contains("full") || queryLower.contains("100%")) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI)
            Log.d(TAG, "Set volume to max $maxVolume")
            return VolumeControlResult.Success("Volume set to maximum (100%), Boss!")
        }

        // Check for percentage command e.g. "set volume to 50 percent", "volume 80%", "volume 50", "set volume to 75"
        val percentRegex = "(?:volume(?: to)?|set volume to|aawaz|aawaaz|sound)\\s*(\\d+)\\s*%?".toRegex()
        val percentMatch = percentRegex.find(queryLower) ?: "(\\d+)\\s*%".toRegex().find(queryLower)

        if (percentMatch != null) {
            val numStr = percentMatch.groupValues[1]
            val percentValue = numStr.toIntOrNull()
            if (percentValue != null) {
                val clampedPercent = percentValue.coerceIn(0, 100)
                val targetVol = ((clampedPercent / 100.0) * maxVolume).roundToInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                Log.d(TAG, "Set volume to $clampedPercent% -> $targetVol/$maxVolume")
                return VolumeControlResult.Success("Volume set to $clampedPercent%, Boss.")
            }
        }

        // Relative up / down commands
        if (queryLower.contains("up") || queryLower.contains("increase") || queryLower.contains("badhao") ||
            queryLower.contains("raise") || queryLower.contains("louder") || queryLower.contains("tez")
        ) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val newPercent = ((newVol.toDouble() / maxVolume) * 100).roundToInt()
            Log.d(TAG, "Increased volume to $newPercent%")
            return VolumeControlResult.Success("Volume increased to $newPercent%, Boss.")
        }

        if (queryLower.contains("down") || queryLower.contains("decrease") || queryLower.contains("kam") ||
            queryLower.contains("lower") || queryLower.contains("quieter") || queryLower.contains("dheemi") || queryLower.contains("dhimi")
        ) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val newPercent = ((newVol.toDouble() / maxVolume) * 100).roundToInt()
            Log.d(TAG, "Decreased volume to $newPercent%")
            return VolumeControlResult.Success("Volume decreased to $newPercent%, Boss.")
        }

        // Fallback default volume report
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentPercent = ((currentVolume.toDouble() / maxVolume) * 100).roundToInt()
        return VolumeControlResult.Success("Current volume is $currentPercent%, Boss.")
    }
}
