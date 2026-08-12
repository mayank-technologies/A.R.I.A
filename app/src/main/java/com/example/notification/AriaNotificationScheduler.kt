package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AriaNotificationScheduler {

    const val CHANNEL_ID = "aria_reminders_channel"
    private const val CHANNEL_NAME = "A.R.I.A. Scheduled Reminders"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications for A.R.I.A. voice and manual reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminderNotification(
        context: Context,
        reminderId: Long,
        title: String,
        timeString: String
    ) {
        val triggerAtMillis = parseTimeToMillis(timeString)
        scheduleReminderNotificationAtMillis(
            context = context,
            reminderId = reminderId,
            title = title,
            timeString = timeString,
            triggerAtMillis = triggerAtMillis
        )
    }

    fun scheduleReminderNotificationInMinutes(
        context: Context,
        reminderId: Long,
        title: String,
        minutes: Int
    ): String {
        val triggerAtMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val newTimeString = sdf.format(Date(triggerAtMillis))
        scheduleReminderNotificationAtMillis(
            context = context,
            reminderId = reminderId,
            title = title,
            timeString = newTimeString,
            triggerAtMillis = triggerAtMillis
        )
        return newTimeString
    }

    fun scheduleReminderNotificationAtMillis(
        context: Context,
        reminderId: Long,
        title: String,
        timeString: String,
        triggerAtMillis: Long
    ) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AriaReminderReceiver::class.java).apply {
            putExtra(AriaReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(AriaReminderReceiver.EXTRA_REMINDER_TITLE, title)
            putExtra(AriaReminderReceiver.EXTRA_REMINDER_TIME, timeString)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d("ARIA_NOTIFICATION", "Scheduled reminder ID=$reminderId for title='$title' at millis=$triggerAtMillis ($timeString)")
        } catch (e: SecurityException) {
            Log.e("ARIA_NOTIFICATION", "Exact alarm permission missing, fallback to setAndAllowWhileIdle", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    fun cancelReminderNotification(context: Context, reminderId: Long) {
        val intent = Intent(context, AriaReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("ARIA_NOTIFICATION", "Cancelled reminder notification ID=$reminderId")
        }
    }

    fun parseTimeToMillis(timeString: String): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val patterns = listOf(
            "hh:mm a",
            "h:mm a",
            "hh:mma",
            "h:mma",
            "h a",
            "ha",
            "HH:mm",
            "H:mm"
        )

        val cleanTime = timeString
            .replace("Today", "", ignoreCase = true)
            .replace("Tomorrow", "", ignoreCase = true)
            .trim()

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val parsedDate = sdf.parse(cleanTime)
                if (parsedDate != null) {
                    val parsedCal = Calendar.getInstance().apply { time = parsedDate }
                    calendar.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                    calendar.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    // Handle "Tomorrow" keyword explicitly
                    if (timeString.contains("Tomorrow", ignoreCase = true)) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    } else if (calendar.timeInMillis <= now) {
                        // If time has already passed today, schedule for tomorrow
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return calendar.timeInMillis
                }
            } catch (_: Exception) {
                // Try next pattern
            }
        }

        // Default fallback: schedule 1 minute in the future for immediate testability
        return now + 60_000L
    }

    /**
     * Schedules a daily recurring morning alarm for weather notification briefing (e.g. at 8:00 AM every day).
     */
    fun scheduleDailyMorningWeatherNotification(context: Context, hourOfDay: Int = 8, minute: Int = 0) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time has passed for today, set for tomorrow morning
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AriaWeatherNotificationReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AriaWeatherNotificationReceiver.WEATHER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("ARIA_NOTIFICATION", "Scheduled daily morning weather notification for millis=${calendar.timeInMillis} (${calendar.time})")
        } catch (e: Exception) {
            Log.e("ARIA_NOTIFICATION", "Failed to schedule daily weather notification", e)
        }
    }

    /**
     * Triggers an immediate weather notification broadcast for testing/instant verification.
     */
    fun triggerImmediateWeatherNotification(context: Context) {
        val intent = Intent(context, AriaWeatherNotificationReceiver::class.java)
        context.sendBroadcast(intent)
    }
}

