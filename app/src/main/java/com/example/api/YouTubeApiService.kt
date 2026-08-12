package com.example.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * FEATURE 1: YouTube Data API v3 Retrofit Service
 * 
 * Yeh interface YouTube Data API v3 ke 'search' endpoint ko call karta hai.
 * Standard request parameters:
 * - part = snippet
 * - type = video
 * - q = search query (e.g. "mr beast channel")
 * - maxResults = 1 (top video result nikaalne ke liye)
 * - key = Google Cloud Console se generate hui YouTube Data API key
 */
interface YouTubeApiService {
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 1,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

/** Data models for parsing YouTube Data API JSON response */
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem>?
)

data class YouTubeSearchItem(
    val id: YouTubeResourceId?,
    val snippet: YouTubeSnippet?
)

data class YouTubeResourceId(
    val kind: String?,
    val videoId: String?
)

data class YouTubeSnippet(
    val title: String?,
    val channelTitle: String?,
    val description: String?
)

/** Singleton Client instance for YouTube API calls */
object YouTubeClient {
    val service: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(YouTubeApiService::class.java)
    }
}
