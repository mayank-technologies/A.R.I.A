package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.api.WeatherClient
import com.example.api.mapWeatherCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AriaWeatherNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Triggering daily morning weather notification receiver")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Fetch current weather based on user location or fallback
                val weatherSummary = fetchWeatherForLocation(context)

                // 2. Display the notification
                showWeatherNotification(context, weatherSummary)

                // 3. Reschedule for next morning at 8:00 AM
                AriaNotificationScheduler.scheduleDailyMorningWeatherNotification(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating daily weather notification: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fetchWeatherForLocation(context: Context): String {
        val locInfo = com.example.assistant.ArcLocationManager.getCurrentLocationInfo(context)
        return if (locInfo != null) {
            val lat = locInfo.latitude
            val lon = locInfo.longitude
            try {
                val forecast = WeatherClient.forecastService.getForecast(lat, lon)
                val cw = forecast.current_weather
                val temp = cw?.temperature?.toInt() ?: 25
                val condition = mapWeatherCode(cw?.weathercode)
                val wind = cw?.windspeed?.toInt() ?: 10

                val locationName = locInfo.city ?: locInfo.locality ?: "Your Location"
                "☀️ $locationName: $condition, $temp°C, Wind $wind km/h"
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching forecast for coordinates: ${e.message}", e)
                val fallbackLabel = locInfo.city ?: locInfo.locality ?: "Your Location"
                "☀️ $fallbackLabel: Clear sky, 25°C"
            }
        } else {
            // Fallback to default city search if location permission missing or location unavailable
            fetchWeatherByCityName("Delhi")
        }
    }

    private suspend fun fetchWeatherByCityName(city: String): String {
        return try {
            val geoRes = WeatherClient.geoService.searchCity(city)
            val loc = geoRes.results?.firstOrNull()
            if (loc != null && loc.latitude != null && loc.longitude != null) {
                val forecast = WeatherClient.forecastService.getForecast(loc.latitude, loc.longitude)
                val cw = forecast.current_weather
                val temp = cw?.temperature?.toInt() ?: 25
                val condition = mapWeatherCode(cw?.weathercode)
                val wind = cw?.windspeed?.toInt() ?: 10
                val cName = loc.name ?: city
                "☀️ $cName: $condition, $temp°C, Wind $wind km/h"
            } else {
                "☀️ $city: Clear sky, 25°C"
            }
        } catch (e: Exception) {
            "☀️ $city: Clear sky, 25°C"
        }
    }

    private fun showWeatherNotification(context: Context, weatherSummary: String) {
        AriaNotificationScheduler.createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            WEATHER_NOTIFICATION_ID,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, AriaNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("☀️ Morning Weather Briefing")
            .setContentText(weatherSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$weatherSummary\n\nHave a wonderful and productive day ahead!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(WEATHER_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "AriaWeatherReceiver"
        const val WEATHER_NOTIFICATION_ID = 8801
    }
}
