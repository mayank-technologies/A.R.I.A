package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed class MusicMoodResult {
    data class Success(val message: String, val openUrl: String? = null) : MusicMoodResult()
    data class Failure(val message: String) : MusicMoodResult()
}

/**
 * Mood-Based Music Suggestion Handler for ARIA.
 * Maps moods (chill, energetic, workout, sad, romantic, focus, happy, party, relaxing)
 * to Spotify deep links (spotify:search:<query>) or YouTube Music search.
 */
object MusicMoodVoiceHandler {

    private const val TAG = "MusicMoodVoiceHandler"

    fun isMusicMoodCommand(queryLower: String): Boolean {
        return queryLower.contains("mood") ||
                queryLower.contains("gaana sunao") ||
                queryLower.contains("gana sunao") ||
                queryLower.contains("gaana bajao") ||
                queryLower.contains("gana bajao") ||
                queryLower.contains("suggest song") ||
                queryLower.contains("play some music") ||
                queryLower.contains("music suggest") ||
                queryLower.contains("song suggest") ||
                queryLower.contains("chill song") ||
                queryLower.contains("sad song") ||
                queryLower.contains("romantic song") ||
                queryLower.contains("party song") ||
                queryLower.contains("gym song") ||
                queryLower.contains("workout song")
    }

    fun processCommand(context: Context, rawQuery: String): MusicMoodResult {
        val q = rawQuery.lowercase().trim()
        Log.d(TAG, "Processing Mood Music command: $q")

        val (mood, queryKeyword) = when {
            q.contains("chill") || q.contains("relax") || q.contains("shant") ->
                Pair("Chill", "chill lofi beats relaxing songs")
            q.contains("sad") || q.contains("broken") || q.contains("dard") || q.contains("udas") || q.contains("dukhi") ->
                Pair("Melancholic / Sad", "emotional soulful sad hindi acoustic songs")
            q.contains("romantic") || q.contains("love") || q.contains("pyaar") || q.contains("ishq") ->
                Pair("Romantic", "best romantic hindi love songs playlist")
            q.contains("workout") || q.contains("gym") || q.contains("exercise") || q.contains("energetic") || q.contains("josh") ->
                Pair("High Energy / Workout", "high energy gym motivational workout booster songs")
            q.contains("party") || q.contains("dance") || q.contains("nach") || q.contains("bhangra") ->
                Pair("Party & Dance", "nonstop bollywood party dance club tracks")
            q.contains("focus") || q.contains("study") || q.contains("padhai") || q.contains("work") ->
                Pair("Deep Focus", "deep focus alpha waves study instrumental music")
            q.contains("happy") || q.contains("khush") || q.contains("joy") ->
                Pair("Happy & Upbeat", "feel good upbeat happy vibe songs")
            else ->
                Pair("Pleasant", "trending top acoustic hit melodies")
        }

        // Try launching Spotify first via spotify:search URI scheme
        val spotifySearchUri = "spotify:search:${Uri.encode(queryKeyword)}"
        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifySearchUri)).apply {
            setPackage("com.spotify.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            if (isAppInstalled(context, "com.spotify.music")) {
                context.startActivity(spotifyIntent)
                MusicMoodResult.Success("Aapke $mood mood ke liye Spotify par best tracks play kar rahi hu, Boss! 🎵")
            } else {
                // Fallback to YouTube / YouTube Music search
                val ytUrl = "https://www.youtube.com/results?search_query=${Uri.encode(queryKeyword)}"
                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(ytIntent)
                MusicMoodResult.Success("Aapke $mood mood ke hisaab se YouTube Music par playlist load kar di hai! 🎶", ytUrl)
            }
        } catch (e: Exception) {
            val webUrl = "https://open.spotify.com/search/${Uri.encode(queryKeyword)}"
            MusicMoodResult.Success("Aapke $mood mood ke gaane search kar diye hain, Boss!", webUrl)
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
