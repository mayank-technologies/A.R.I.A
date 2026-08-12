package com.example.assistant

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.example.data.AriaDao
import com.example.data.ReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * FEATURE 2: Calendar Date-Marking Logic Handler
 * 
 * Voice Command Examples:
 * - "open calendar and mark this date"
 * - "mark 15 august in calendar"
 * - "mark today in calendar"
 * - "open calendar and mark 25 december as holiday"
 * 
 * Yeh class:
 * 1. Voice query se target date aur event title extract karti hai.
 * 2. Event ko local Room Database (`AriaDao`) me reminder ke roop me save karti hai.
 * 3. Device ke native Calendar app me Intent (`CalendarContract.Events`) ke dwara event add karti hai.
 * 4. ARIA confirm karti hai: "Maine [date] ko mark kar diya hai".
 */
object CalendarVoiceHandler {

    private const val TAG = "ARIA_CALENDAR_HANDLER"

    /**
     * Checks if the voice command pertains to calendar date marking or events.
     */
    fun isCalendarCommand(rawQuery: String): Boolean {
        val q = rawQuery.lowercase().trim()
        return (q.contains("calendar") || q.contains("calender")) &&
                (q.contains("mark") || q.contains("add") || q.contains("save") || q.contains("open") || q.contains("set"))
    }

    /**
     * Processes calendar voice query, inserts into local DB and launches native Android Calendar Intent.
     */
    suspend fun processCalendarCommand(
        context: Context,
        ariaDao: AriaDao,
        rawQuery: String
    ): CalendarActionResult = withContext(Dispatchers.IO) {
        val (dateLabel, eventTitle, calendarMillis) = parseDateAndTitle(rawQuery)

        // 1. Save event into Local Room Database (`ariaDao`)
        try {
            ariaDao.insertReminder(
                ReminderEntity(
                    title = eventTitle,
                    timeString = dateLabel,
                    isCompleted = false
                )
            )
            Log.d(TAG, "Successfully inserted calendar event '$eventTitle' on $dateLabel in Room DB")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving calendar event to Room DB: ${e.message}")
        }

        // 2. Add event to native Android Calendar app via Intent (`CalendarContract.Events.CONTENT_URI`)
        val nativeCalendarSuccess = launchNativeCalendarInsertIntent(
            context = context,
            title = eventTitle,
            startTimeMillis = calendarMillis
        )

        // 3. Construct ARIA confirmation response as requested in specs
        val responseText = "Maine $dateLabel ko mark kar diya hai ($eventTitle)."

        Log.d(TAG, "Calendar Date Marked -> Date: '$dateLabel', Title: '$eventTitle', Native: $nativeCalendarSuccess")

        return@withContext CalendarActionResult(
            message = responseText,
            dateLabel = dateLabel,
            eventTitle = eventTitle
        )
    }

    /**
     * Extracts date label, event title, and epoch milliseconds from natural query.
     */
    private fun parseDateAndTitle(rawQuery: String): Triple<String, String, Long> {
        val q = rawQuery.lowercase(Locale.ROOT).trim()

        val calendar = Calendar.getInstance()
        var dateLabel = ""
        var title = "Marked Date"

        // Detect "today", "this date", "aaj"
        if (q.contains("this date") || q.contains("today") || q.contains("aaj")) {
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            dateLabel = sdf.format(calendar.time)
            title = extractTitle(rawQuery, listOf("this date", "today", "aaj"))
        } else if (q.contains("tomorrow") || q.contains("kal")) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            dateLabel = sdf.format(calendar.time)
            title = extractTitle(rawQuery, listOf("tomorrow", "kal"))
        } else {
            // Attempt parsing explicit dates like "15 august", "25 december"
            val monthNames = listOf(
                "january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"
            )

            var parsedMonth = -1
            var parsedDay = -1

            for ((index, month) in monthNames.withIndex()) {
                if (q.contains(month)) {
                    parsedMonth = index
                    // Regex for extracting day number before or after month name (e.g., "15 august" or "august 15")
                    val dayRegex = "(\\d{1,2})".toRegex()
                    val match = dayRegex.find(q)
                    if (match != null) {
                        parsedDay = match.value.toIntOrNull() ?: 1
                    }
                    break
                }
            }

            if (parsedMonth != -1 && parsedDay != -1) {
                calendar.set(Calendar.MONTH, parsedMonth)
                calendar.set(Calendar.DAY_OF_MONTH, parsedDay)
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                dateLabel = sdf.format(calendar.time)
                title = extractTitle(rawQuery, listOf(dateLabel, "15 august", "25 december"))
            } else {
                // Default to current date if specific date parsing yields no match
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                dateLabel = sdf.format(calendar.time)
                title = "Marked Date"
            }
        }

        if (title.isBlank()) title = "Marked Date Event"

        return Triple(dateLabel, title, calendar.timeInMillis)
    }

    private fun extractTitle(rawQuery: String, removeTerms: List<String>): String {
        var clean = rawQuery
            .replace("open calendar and mark", "", ignoreCase = true)
            .replace("open calendar and", "", ignoreCase = true)
            .replace("open calendar", "", ignoreCase = true)
            .replace("mark this date in calendar", "", ignoreCase = true)
            .replace("mark in calendar", "", ignoreCase = true)
            .replace("mark date in calendar", "", ignoreCase = true)
            .replace("mark date", "", ignoreCase = true)
            .replace("mark this date", "", ignoreCase = true)
            .replace("mark", "", ignoreCase = true)
            .replace("calendar", "", ignoreCase = true)
            .trim()

        for (term in removeTerms) {
            clean = clean.replace(term, "", ignoreCase = true).trim()
        }

        clean = clean.replace(" as ", " ")
            .replace(" for ", " ")
            .replace(" in ", " ")
            .trim()

        return if (clean.isNotBlank()) clean.replaceFirstChar { it.uppercase() } else "Important Event"
    }

    /**
     * Launches native Android Calendar app event insertion screen (`CalendarContract.Events`).
     */
    private fun launchNativeCalendarInsertIntent(
        context: Context,
        title: String,
        startTimeMillis: Long
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTimeMillis + (60 * 60 * 1000)) // 1 hour duration
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch native calendar insert intent: ${e.message}")
            false
        }
    }
}

/** Result Model for Calendar Operations */
data class CalendarActionResult(
    val message: String,
    val dateLabel: String,
    val eventTitle: String
)
