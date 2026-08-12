package com.example.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface WikipediaApiService {
    @GET("w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&format=json&redirects=1")
    suspend fun getSummary(@Query("titles") title: String): WikiResponse
}

data class WikiResponse(
    val query: WikiQuery?
)

data class WikiQuery(
    val pages: Map<String, WikiPage>?
)

data class WikiPage(
    val pageid: Long?,
    val title: String?,
    val extract: String?
)

object WikipediaClient {
    val service: WikipediaApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://en.wikipedia.org/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(WikipediaApiService::class.java)
    }
}
