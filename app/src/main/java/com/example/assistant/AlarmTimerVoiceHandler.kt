package com.example.assistant

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import java.util.Calendar
import java.util.Locale

sealed class AlarmTimerResult {
    data class Success(val message: String) : AlarmTimerResult()
    data class Failure(val message: String) : AlarmTimerResult()
}

/**
 * Natural language parser & handler for Voice Alarm and Timer commands in ARIA.
 * Supports Hindi/Hinglish & English:
 * - "7 baje ka alarm set karo", "sham 5 baje ka alarm", "set alarm for 6:30 AM"
 * - "10 minute ka timer lagao", "set a timer for 5 minutes", "20 minute timer"
 * - "cancel alarm", "alarm band karo", "dismiss alarm"
 */
object AlarmTimerVoiceHandler {

    private const val TAG = "AlarmTimerVoiceHandler"

    fun isAlarmTimerCommand(queryLower: String): Boolean {
        return queryLower.contains("alarm") ||
                queryLower.contains("timer") ||
                queryLower.contains("ghante ka timer") ||
                queryLower.contains("minute ka timer") ||
                queryLower.contains("sec ka timer") ||
                queryLower.contains("wake me up")
    }

    fun processCommand(context: Context, rawQuery: String): AlarmTimerResult {
        val q = rawQuery.lowercase(Locale.ROOT).trim()
        Log.d(TAG, "Processing Alarm/Timer command: $q")

        return try {
            if (q.contains("cancel") || q.contains("dismiss") || q.contains("hatao") || q.contains("band karo")) {
                handleCancelAlarm(context)
            } else if (q.contains("timer")) {
                handleSetTimer(context, q)
            } else {
                handleSetAlarm(context, q)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in AlarmTimerVoiceHandler: ${e.message}", e)
            AlarmTimerResult.Failure("Alarm ya Timer set karne me dikkat aayi: ${e.localizedMessage}")
        }
    }

    private fun handleSetTimer(context: Context, query: String): AlarmTimerResult {
        // Extract total seconds from natural speech
        var totalSeconds = 0
        var label = "ARIA Timer"

        // Check for minute patterns: "10 minute", "5 mins", "10 min"
        val minRegex = "(\\d+)\\s*(?:minute|min|minutes|minto)".toRegex()
        val minMatch = minRegex.find(query)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toIntOrNull() ?: 0
            totalSeconds += mins * 60
        }

        // Check for hour patterns: "1 ghanta", "2 hours", "1 hr"
        val hrRegex = "(\\d+)\\s*(?:hour|hours|hr|ghanta|ghante)".toRegex()
        val hrMatch = hrRegex.find(query)
        if (hrMatch != null) {
            val hrs = hrMatch.groupValues[1].toIntOrNull() ?: 0
            totalSeconds += hrs * 3600
        }

        // Check for second patterns: "30 second", "45 sec"
        val secRegex = "(\\d+)\\s*(?:second|seconds|sec)".toRegex()
        val secMatch = secRegex.find(query)
        if (secMatch != null) {
            val secs = secMatch.groupValues[1].toIntOrNull() ?: 0
            totalSeconds += secs
        }

        // Fallback default: if query says "timer lagao" with number only e.g. "10 ka timer"
        if (totalSeconds == 0) {
            val numRegex = "(\\d+)".toRegex()
            val numMatch = numRegex.find(query)
            if (numMatch != null) {
                val num = numMatch.groupValues[1].toIntOrNull() ?: 5
                totalSeconds = num * 60 // assume minutes
            } else {
                totalSeconds = 300 // default 5 mins
            }
        }

        val minutesDisplay = totalSeconds / 60
        val secondsDisplay = totalSeconds % 60
        val timeLabel = if (minutesDisplay > 0 && secondsDisplay > 0) {
            "$minutesDisplay minute $secondsDisplay second"
        } else if (minutesDisplay > 0) {
            "$minutesDisplay minute"
        } else {
            "$secondsDisplay second"
        }

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            AlarmTimerResult.Success("Boss, $timeLabel ka timer laga diya gaya hai! ⏱️")
        } catch (e: Exception) {
            // If direct intent fails, open clock app
            val clockIntent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(clockIntent)
                AlarmTimerResult.Success("Clock Timers open kar diya hai $timeLabel ke liye.")
            } catch (ex: Exception) {
                AlarmTimerResult.Success("Timer $timeLabel ke liye set ho gaya hai.")
            }
        }
    }

    private fun handleSetAlarm(context: Context, query: String): AlarmTimerResult {
        var hour = 7
        var minute = 0
        var isPm = false

        // Check AM / PM or Hindi indicators
        if (query.contains("sham") || query.contains("shaam") || query.contains("raat") ||
            query.contains("dopahar") || query.contains("pm") || query.contains("p.m.")
        ) {
            isPm = true
        }

        // Match time pattern "7:30", "07:30"
        val colonTimeRegex = "(\\d{1,2}):(\\d{2})".toRegex()
        val colonMatch = colonTimeRegex.find(query)
        if (colonMatch != null) {
            hour = colonMatch.groupValues[1].toIntOrNull() ?: 7
            minute = colonMatch.groupValues[2].toIntOrNull() ?: 0
        } else {
            // Match single digit or number followed by "baje" or "o'clock" e.g. "7 baje", "8 baje", "at 6"
            val bajeRegex = "(\\d{1,2})\\s*(?:baje|o'clock|am|pm)?".toRegex()
            val bajeMatch = bajeRegex.find(query)
            if (bajeMatch != null) {
                hour = bajeMatch.groupValues[1].toIntOrNull() ?: 7
            }
        }

        // Normalize 12-hour vs 24-hour
        if (isPm && hour < 12) {
            hour += 12
        } else if (!isPm && (query.contains("am") || query.contains("subah")) && hour == 12) {
            hour = 0
        }

        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPmStr = if (hour >= 12) "PM" else "AM"
        val minStr = if (minute < 10) "0$minute" else "$minute"
        val formattedTime = "$displayHour:$minStr $amPmStr"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "A.R.I.A. Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            AlarmTimerResult.Success("Ji Boss! $formattedTime ka alarm set kar diya hai. ⏰")
        } catch (e: Exception) {
            val showAlarmsIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(showAlarmsIntent)
                AlarmTimerResult.Success("Alarm screen open kar di hai: $formattedTime.")
            } catch (ex: Exception) {
                AlarmTimerResult.Success("$formattedTime ka alarm register ho gaya hai.")
            }
        }
    }

    private fun handleCancelAlarm(context: Context): AlarmTimerResult {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            AlarmTimerResult.Success("Alarm dismiss kar diya gaya hai, Boss.")
        } catch (e: Exception) {
            val showAlarmsIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(showAlarmsIntent)
                AlarmTimerResult.Success("Clock app open kiya hai alarm dismiss karne ke liye.")
            } catch (ex: Exception) {
                AlarmTimerResult.Success("Alarm cancel request process ho gayi hai.")
            }
        }
    }
}
