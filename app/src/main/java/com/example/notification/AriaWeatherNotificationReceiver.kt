package com.example.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.api.WeatherClient
import com.example.api.mapWeatherCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

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
        val coords = getDeviceLocation(context)
        return if (coords != null) {
            val (lat, lon) = coords
            try {
                val forecast = WeatherClient.forecastService.getForecast(lat, lon)
                val cw = forecast.current_weather
                val temp = cw?.temperature?.toInt() ?: 25
                val condition = mapWeatherCode(cw?.weathercode)
                val wind = cw?.windspeed?.toInt() ?: 10

                var locationName = getCityNameFromCoords(context, lat, lon) ?: "Your Location"

                "☀️ $locationName: $condition, $temp°C, Wind $wind km/h"
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching forecast for coordinates: ${e.message}", e)
                "☀️ Your Location: Clear sky, 25°C"
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

    private fun getDeviceLocation(context: Context): Pair<Double, Double>? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        try {
            val gpsLoc = if (hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val netLoc = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null

            val bestLoc = gpsLoc ?: netLoc
            if (bestLoc != null) {
                return Pair(bestLoc.latitude, bestLoc.longitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device location", e)
        }
        return null
    }

    private fun getCityNameFromCoords(context: Context, lat: Double, lon: Double): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val geocoder = Geocoder(context, Locale.getDefault())
                var resultName: String? = null
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val addr = addresses[0]
                        resultName = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    }
                }
                resultName
            } else {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    addr.locality ?: addr.subAdminArea ?: addr.adminArea
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder lookup failed: ${e.message}")
            null
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
