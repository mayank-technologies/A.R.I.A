package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.api.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * FEATURE 1: YouTube Search & Auto-Play Top Video Handler
 * 
 * Handles voice commands such as:
 * - "open youtube and search chill song and play the song" (Search + Play)
 * - "open youtube and search kotlin tutorial" (Search Only)
 * - "play mr beast on youtube" (Search + Play)
 * 
 * Uses YouTube Data API v3 (or web parser fallback) to fetch top videoId
 * and launches direct watch URL or search results list accordingly.
 */
object YouTubeVoiceHandler {

    private const val TAG = "ARIA_YOUTUBE_HANDLER"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    /**
     * Checks if user command pertains to YouTube operations.
     */
    fun isYouTubeCommand(rawQuery: String): Boolean {
        val q = rawQuery.lowercase().trim()
        val containsYouTube = q.contains("youtube") || q.contains("you tube")
        val containsAction = q.contains("search") || q.contains("play") || q.contains("chalao") ||
                q.contains("dhoondho") || q.contains("khojo") || q.contains("baja") || q.contains("open")
        return containsYouTube && containsAction
    }

    /**
     * Detects whether the voice command explicitly requests video playback ("search + play")
     * or only search results ("search only").
     */
    fun hasPlayKeyword(rawQuery: String): Boolean {
        val q = rawQuery.lowercase().trim()
        return q.contains("play") ||
                q.contains("chalao") ||
                q.contains("baja") ||
                q.contains("sunao") ||
                q.contains("watch") ||
                q.contains("stream")
    }

    /**
     * Parses the command, extracts the search query, calls YouTube API v3 (or fallback),
     * and either directly plays the top video or opens search results list.
     */
    suspend fun processYouTubeCommand(context: Context, rawQuery: String): YouTubeResult = withContext(Dispatchers.IO) {
        val isPlayCommand = hasPlayKeyword(rawQuery)
        val commandModeStr = if (isPlayCommand) "SEARCH_AND_PLAY (Search + Play)" else "SEARCH_ONLY (Search Only)"

        Log.d(TAG, "==========================================================")
        Log.d(TAG, "[COMMAND DETECTED] Mode: $commandModeStr | Raw Query: '$rawQuery'")
        println("[ARIA YOUTUBE LOG] Mode Detected: $commandModeStr | Command: '$rawQuery'")

        // 1. Extract clean search query from command
        val searchQuery = extractSearchQuery(rawQuery)

        if (searchQuery.isBlank()) {
            Log.d(TAG, "[FALLBACK] Search query is blank. Launching YouTube main app.")
            SmartAppOpener.smartLaunchApp(context, "youtube")
            return@withContext YouTubeResult.Success("YouTube open kar rahi hu, Boss!")
        }

        Log.d(TAG, "[PARSED SEARCH QUERY] Extracted Topic: '$searchQuery'")

        // 2. Logic Branch: "SEARCH AND PLAY" vs "SEARCH ONLY"
        if (isPlayCommand) {
            Log.d(TAG, "[ACTION] Executing Search + Play mode for topic: '$searchQuery'")
            println("[ARIA YOUTUBE LOG] Action: Searching top video for '$searchQuery' to play directly...")

            var videoId: String? = null
            var videoTitle: String? = null

            // Step A: Attempt YouTube Data API v3 if API key is present
            val apiKey = getYouTubeApiKey()
            if (!apiKey.isNullOrBlank()) {
                try {
                    Log.d(TAG, "[API CALL] Requesting YouTube Data API v3 for: '$searchQuery'")
                    val response = YouTubeClient.service.searchVideos(
                        query = searchQuery,
                        apiKey = apiKey,
                        maxResults = 1
                    )
                    val topItem = response.items?.firstOrNull()
                    videoId = topItem?.id?.videoId
                    videoTitle = topItem?.snippet?.title
                    if (!videoId.isNullOrBlank()) {
                        Log.d(TAG, "[API SUCCESS] Found videoId via YouTube API: $videoId (Title: '$videoTitle')")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[API ERROR] YouTube Data API v3 failed: ${e.message}. Trying scraper fallback...")
                }
            }

            // Step B: Web scraping parser fallback if API key is missing or failed
            if (videoId.isNullOrBlank()) {
                Log.d(TAG, "[FALLBACK] Fetching video ID via web search result parser...")
                videoId = fetchVideoIdViaWebScraping(searchQuery)
                if (!videoId.isNullOrBlank()) {
                    Log.d(TAG, "[SCRAPER SUCCESS] Found videoId via web parser: $videoId")
                }
            }

            // Step C: Launch direct video playback if videoId obtained
            if (!videoId.isNullOrBlank()) {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val success = launchYouTubeVideoUrl(context, watchUrl, videoId)

                if (success) {
                    val replyTitle = videoTitle ?: searchQuery
                    val msg = "Playing '$replyTitle' on YouTube, Boss!"
                    Log.d(TAG, "[DECISION] DIRECT PLAY SUCCESS -> Video ID: $videoId | URL: $watchUrl")
                    println("[ARIA YOUTUBE DECISION] DIRECTLY PLAYING VIDEO -> ID: $videoId | URL: $watchUrl")
                    return@withContext YouTubeResult.Success(msg, watchUrl)
                }
            }

            // Step D: Error handling if video could not be found or launched
            val failMsg = "Sorry, mujhe wo video nahi mila"
            Log.e(TAG, "[DECISION] FAILED -> Video not found for query: '$searchQuery'")
            println("[ARIA YOUTUBE DECISION] VIDEO NOT FOUND -> Responding: '$failMsg'")
            return@withContext YouTubeResult.Failure(failMsg)

        } else {
            // "SEARCH ONLY" Mode: Just launch YouTube search results page
            Log.d(TAG, "[ACTION] Executing Search Only mode for topic: '$searchQuery'")
            println("[ARIA YOUTUBE LOG] Action: Opening YouTube search results list for '$searchQuery'...")

            val searchUrl = "https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}"
            val launched = launchYouTubeSearchUrl(context, searchUrl)

            if (launched) {
                val msg = "Opening YouTube search results for '$searchQuery', Boss!"
                Log.d(TAG, "[DECISION] SEARCH RESULTS PAGE OPENED -> URL: $searchUrl")
                println("[ARIA YOUTUBE DECISION] OPENED SEARCH RESULTS PAGE -> URL: $searchUrl")
                return@withContext YouTubeResult.Success(msg, searchUrl)
            } else {
                val failMsg = "Sorry, mujhe wo video nahi mila"
                Log.e(TAG, "[DECISION] FAILED to launch YouTube search results page")
                println("[ARIA YOUTUBE DECISION] FAILED TO OPEN SEARCH PAGE -> Responding: '$failMsg'")
                return@withContext YouTubeResult.Failure(failMsg)
            }
        }
    }

    /**
     * Web Scraping Fallback: Fetches YouTube search HTML page and extracts top result's videoId using regex.
     */
    private fun fetchVideoIdViaWebScraping(query: String): String? {
        return try {
            val encodedQuery = Uri.encode(query)
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: return null

            val videoIdRegex = "(?:/watch\\?v=|\"videoId\":\")([a-zA-Z0-9_-]{11})".toRegex()
            val match = videoIdRegex.find(html)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Web scraping fallback error: ${e.message}", e)
            null
        }
    }

    /**
     * Extracts search keywords from natural language commands.
     * e.g., "open youtube and search chill song and play the song" -> "chill song"
     */
    private fun extractSearchQuery(rawQuery: String): String {
        var clean = rawQuery.lowercase().trim()

        val phrasesToRemove = listOf(
            "open youtube and search",
            "open youtube and play",
            "search on youtube and play",
            "search in youtube and play",
            "youtube par search karke play karo",
            "youtube par play karo",
            "youtube par search karo",
            "youtube par chalao",
            "youtube search and play",
            "open youtube",
            "search on youtube",
            "search in youtube",
            "search youtube for",
            "search youtube",
            "play on youtube",
            "play in youtube",
            "youtube video",
            "youtube channel",
            "youtube",
            "you tube",
            "and play the song",
            "and play popular video",
            "and play top video",
            "and play the video",
            "and play video",
            "and play song",
            "and play",
            "and search",
            "play the song",
            "play the video",
            "play video",
            "play song",
            "search for",
            "search in",
            "search on",
            "search",
            "play",
            "chalao",
            "dhoondho",
            "khojo",
            "baja",
            "sunao",
            "kholo"
        )

        for (phrase in phrasesToRemove) {
            clean = clean.replace(phrase, " ")
        }

        clean = clean.replace("\\s+".toRegex(), " ").trim()

        if (clean.startsWith("the ")) clean = clean.substring(4).trim()
        if (clean.endsWith(" the")) clean = clean.substring(0, clean.length - 4).trim()
        if (clean.startsWith("a ")) clean = clean.substring(2).trim()

        return clean
    }

    /**
     * Reads YOUTUBE_API_KEY from BuildConfig or environment variable.
     */
    private fun getYouTubeApiKey(): String? {
        return try {
            val keyField = BuildConfig::class.java.getField("YOUTUBE_API_KEY")
            val key = keyField.get(null) as? String
            if (!key.isNullOrBlank()) key else System.getenv("YOUTUBE_API_KEY")
        } catch (e: Exception) {
            System.getenv("YOUTUBE_API_KEY")
        }
    }

    /**
     * Launches direct YouTube video watch URL in YouTube app or external browser.
     */
    private fun launchYouTubeVideoUrl(context: Context, watchUrl: String, videoId: String): Boolean {
        return try {
            val appUri = Uri.parse("vnd.youtube:$videoId")
            val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.youtube")
            }
            context.startActivity(appIntent)
            Log.d(TAG, "Launched native YouTube app for video: $videoId")
            true
        } catch (e: Exception) {
            try {
                val newPipeUri = Uri.parse(watchUrl)
                val newPipeIntent = Intent(Intent.ACTION_VIEW, newPipeUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("org.schabi.newpipe")
                }
                context.startActivity(newPipeIntent)
                Log.d(TAG, "Launched NewPipe app for video: $videoId")
                true
            } catch (ex1: Exception) {
                try {
                    SmartAppOpener.launchExternalBrowser(context, watchUrl)
                } catch (ex2: Exception) {
                    Log.e(TAG, "Failed to launch video URL anywhere: $watchUrl", ex2)
                    false
                }
            }
        }
    }

    /**
     * Launches search results URL on YouTube.
     */
    private fun launchYouTubeSearchUrl(context: Context, searchUrl: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.youtube")
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched native YouTube app search results")
            true
        } catch (e: Exception) {
            try {
                SmartAppOpener.launchExternalBrowser(context, searchUrl)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch search URL: $searchUrl", ex)
                false
            }
        }
    }
}

/** Result model for YouTube Voice Operations */
sealed class YouTubeResult {
    data class Success(val message: String, val openUrl: String? = null) : YouTubeResult()
    data class Failure(val message: String) : YouTubeResult()
}

