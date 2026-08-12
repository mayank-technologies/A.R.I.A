package com.example.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("data/2.5/weather")
    suspend fun getWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): OpenWeatherResponse
}

data class OpenWeatherResponse(
    val name: String?,
    val main: MainTemp?,
    val weather: List<WeatherDesc>?
)

data class MainTemp(
    val temp: Double?,
    val feels_like: Double?,
    val humidity: Int?
)

data class WeatherDesc(
    val main: String?,
    val description: String?
)

object WeatherClient {
    val service: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    val geoService: OpenMeteoGeocodingService by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenMeteoGeocodingService::class.java)
    }

    val forecastService: OpenMeteoForecastService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenMeteoForecastService::class.java)
    }
}

interface OpenMeteoGeocodingService {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") city: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): OpenMeteoGeoResponse
}

interface OpenMeteoForecastService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") currentWeather: Boolean = true
    ): OpenMeteoForecastResponse
}

data class OpenMeteoGeoResponse(
    val results: List<OpenMeteoGeoResult>?
)

data class OpenMeteoGeoResult(
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val country: String?
)

data class OpenMeteoForecastResponse(
    val current_weather: CurrentWeatherInfo?
)

data class CurrentWeatherInfo(
    val temperature: Double?,
    val windspeed: Double?,
    val weathercode: Int?
)

fun mapWeatherCode(code: Int?): String {
    return when (code) {
        0 -> "clear sky"
        1, 2, 3 -> "partly cloudy"
        45, 48 -> "foggy"
        51, 53, 55 -> "light drizzle"
        61, 63, 65 -> "rainy"
        71, 73, 75 -> "snowy"
        80, 81, 82 -> "rain showers"
        95, 96, 99 -> "thunderstorm"
        else -> "clear sky"
    }
}

