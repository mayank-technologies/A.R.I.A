package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.api.GeminiClient
import com.example.api.WeatherClient
import com.example.api.WikipediaClient
import com.example.data.AriaDao
import com.example.data.ReminderEntity
import com.example.data.VoiceHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AriaCommandResult {
    data class TextResponse(val response: String, val category: String, val openUrl: String? = null) : AriaCommandResult()
    data class LaunchApp(val response: String, val appType: String) : AriaCommandResult()
    data class CloseBrowser(val response: String) : AriaCommandResult()
}

class AriaCommandProcessor(
    private val context: Context,
    private val ariaDao: AriaDao
) {

    suspend fun processCommand(userQuery: String): AriaCommandResult = withContext(Dispatchers.IO) {
        val queryLower = userQuery.lowercase(Locale.ROOT).trim()

        // 0.4 Custom Routines ("Good Morning ARIA", "Good Night ARIA", "office jaana hai")
        if (RoutineManager.isRoutineCommand(queryLower)) {
            val routineMgr = RoutineManager(context, ariaDao)
            val res = routineMgr.executeRoutine(userQuery, "Boss")
            when (res) {
                is RoutineResult.Success -> {
                    saveHistory(userQuery, res.speechResponse, "ROUTINE")
                    return@withContext AriaCommandResult.TextResponse(res.speechResponse, "ROUTINE")
                }
            }
        }

        // 0.42 Voice Gesture Automation ("scroll this reel", "pause this video", "next short", "play video")
        if (queryLower.contains("scroll") || queryLower.contains("reel") || queryLower.contains("shorts") ||
            queryLower.contains("pause this") || queryLower.contains("play this") || queryLower.contains("pause video") ||
            queryLower.contains("play video") || queryLower.contains("video roko") || queryLower.contains("video chalao") ||
            queryLower.contains("next video") || queryLower.contains("previous video") || queryLower.contains("double tap") ||
            queryLower.contains("like this reel") || queryLower.contains("like this video")
        ) {
            val isEnabled = com.example.assistant.accessibility.AriaAccessibilityGestureService.checkAccessibilityEnabled(context) ||
                    com.example.assistant.accessibility.AriaAccessibilityGestureService.isServiceRunning()

            if (!isEnabled) {
                val errMsg = "Boss, automated screen gestures ke liye ARIA ki Accessibility Service enable honi chahiye. Settings > Accessibility me jakar 'A.R.I.A. Voice Gestures' ko ON kijiye."
                saveHistory(userQuery, errMsg, "GESTURE")
                return@withContext AriaCommandResult.TextResponse(errMsg, "GESTURE")
            }

            if (queryLower.contains("previous") || queryLower.contains("pichli") || queryLower.contains("scroll up")) {
                var responseText = "Pichli reel scroll kar di hai! 📱"
                com.example.assistant.accessibility.AriaAccessibilityGestureService.scrollPreviousVideo { res ->
                    if (res is com.example.assistant.accessibility.GestureResult.Failure) {
                        responseText = res.message
                    }
                }
                saveHistory(userQuery, responseText, "GESTURE")
                return@withContext AriaCommandResult.TextResponse(responseText, "GESTURE")
            } else if (queryLower.contains("pause") || queryLower.contains("play") || queryLower.contains("resume") ||
                queryLower.contains("roko") || queryLower.contains("chalao") || queryLower.contains("toggle")
            ) {
                var responseText = "Video play/pause toggle kar diya hai! ⏯️"
                com.example.assistant.accessibility.AriaAccessibilityGestureService.togglePlayPauseVideo { res ->
                    if (res is com.example.assistant.accessibility.GestureResult.Failure) {
                        responseText = res.message
                    }
                }
                saveHistory(userQuery, responseText, "GESTURE")
                return@withContext AriaCommandResult.TextResponse(responseText, "GESTURE")
            } else if (queryLower.contains("like") || queryLower.contains("double tap")) {
                var responseText = "Video like (double tap) kar diya hai! ❤️"
                com.example.assistant.accessibility.AriaAccessibilityGestureService.doubleTapLikeVideo { res ->
                    if (res is com.example.assistant.accessibility.GestureResult.Failure) {
                        responseText = res.message
                    }
                }
                saveHistory(userQuery, responseText, "GESTURE")
                return@withContext AriaCommandResult.TextResponse(responseText, "GESTURE")
            } else if (queryLower.contains("scroll") || queryLower.contains("next") || queryLower.contains("agla") || queryLower.contains("badlo")) {
                var responseText = "Shorts/Reels scroll kar diya hai! 📱✨"
                com.example.assistant.accessibility.AriaAccessibilityGestureService.scrollNextVideo { res ->
                    if (res is com.example.assistant.accessibility.GestureResult.Failure) {
                        responseText = res.message
                    }
                }
                saveHistory(userQuery, responseText, "GESTURE")
                return@withContext AriaCommandResult.TextResponse(responseText, "GESTURE")
            }
        }

        // 0.45 Alarm & Timer Commands ("7 baje ka alarm set karo", "10 minute ka timer lagao", "cancel alarm")
        if (AlarmTimerVoiceHandler.isAlarmTimerCommand(queryLower)) {
            val alarmResult = AlarmTimerVoiceHandler.processCommand(context, userQuery)
            when (alarmResult) {
                is AlarmTimerResult.Success -> {
                    saveHistory(userQuery, alarmResult.message, "ALARM_TIMER")
                    return@withContext AriaCommandResult.TextResponse(alarmResult.message, "ALARM_TIMER")
                }
                is AlarmTimerResult.Failure -> {
                    saveHistory(userQuery, alarmResult.message, "ALARM_TIMER")
                    return@withContext AriaCommandResult.TextResponse(alarmResult.message, "ALARM_TIMER")
                }
            }
        }

        // 0.46 Mood-Based Music Suggestion ("mood chill hai gaana bajao", "sad songs", "gym workout music")
        if (MusicMoodVoiceHandler.isMusicMoodCommand(queryLower)) {
            val moodRes = MusicMoodVoiceHandler.processCommand(context, userQuery)
            when (moodRes) {
                is MusicMoodResult.Success -> {
                    saveHistory(userQuery, moodRes.message, "MUSIC")
                    return@withContext AriaCommandResult.TextResponse(moodRes.message, "MUSIC", openUrl = moodRes.openUrl)
                }
                is MusicMoodResult.Failure -> {
                    saveHistory(userQuery, moodRes.message, "MUSIC")
                    return@withContext AriaCommandResult.TextResponse(moodRes.message, "MUSIC")
                }
            }
        }

        // 0.5 Morning Briefing & Daily Summary command
        if (queryLower.contains("good morning") || queryLower.contains("briefing") ||
            queryLower.contains("daily summary") || queryLower.contains("morning update") ||
            queryLower.contains("subah ki update") || queryLower.contains("daily report") ||
            queryLower.contains("aaj ki report") || queryLower.contains("morning summary")
        ) {
            val briefingText = generateBriefingSummary("")
            return@withContext AriaCommandResult.TextResponse(briefingText, "BRIEFING")
        }

        // 0.8 Snooze Reminder Command
        if (queryLower.contains("snooze") || queryLower.contains("delay reminder")) {
            val snoozeMinutes = when {
                queryLower.contains("15") -> 15
                queryLower.contains("10") -> 10
                else -> 5
            }
            val pendingReminders = ariaDao.getAllReminders().first().filter { !it.isCompleted }
            if (pendingReminders.isNotEmpty()) {
                val target = pendingReminders.first()
                val newTimeString = com.example.notification.AriaNotificationScheduler.scheduleReminderNotificationInMinutes(
                    context = context,
                    reminderId = target.id,
                    title = target.title,
                    minutes = snoozeMinutes
                )
                ariaDao.updateReminder(target.copy(timeString = newTimeString, isCompleted = false))
                val reply = "Boss, '${target.title}' is snoozed for $snoozeMinutes minutes until $newTimeString ⏰"
                saveHistory(userQuery, reply, "REMINDER")
                return@withContext AriaCommandResult.TextResponse(reply, "REMINDER")
            } else {
                val reply = "Boss, there are no active pending reminders to snooze."
                saveHistory(userQuery, reply, "REMINDER")
                return@withContext AriaCommandResult.TextResponse(reply, "REMINDER")
            }
        }

        // 0.9 Volume Control Commands
        if (VolumeControlHandler.isVolumeCommand(queryLower)) {
            val volResult = VolumeControlHandler.processVolumeCommand(context, queryLower)
            when (volResult) {
                is VolumeControlResult.Success -> {
                    saveHistory(userQuery, volResult.message, "VOLUME")
                    return@withContext AriaCommandResult.TextResponse(volResult.message, "VOLUME")
                }
            }
        }

        // 0.95 Screen Lock Commands
        if (AriaScreenLockManager.isLockCommand(queryLower)) {
            Log.d("AriaCommandProcessor", "Lock screen command triggered by query: '$userQuery'")
            val locked = AriaScreenLockManager.lockScreenDeviceAdmin(context)
            val reply = if (locked) {
                "Locking screen now, Boss."
            } else {
                Log.d("AriaCommandProcessor", "Device Admin not active. Attempting to trigger ACTION_ADD_DEVICE_ADMIN intent...")
                try {
                    val intent = AriaScreenLockManager.getAdminActivationIntent(context)
                    Log.d("AriaCommandProcessor", "Starting Activity with Device Admin Activation Intent: $intent")
                    context.startActivity(intent)
                    Log.d("AriaCommandProcessor", "Successfully started Device Admin Activation Settings Activity.")
                } catch (e: Exception) {
                    Log.e("AriaCommandProcessor", "Error launching Device Admin activation screen: ${e.message}", e)
                }
                "Screen lock requires Device Admin activation. Please tap Activate on the Settings screen."
            }
            saveHistory(userQuery, reply, "DEVICE_CONTROL")
            return@withContext AriaCommandResult.TextResponse(reply, "DEVICE_CONTROL")
        }

        // 0.96 Cloud Translation Commands
        if (CloudTranslationVoiceHandler.isTranslationCommand(queryLower)) {
            val transRes = CloudTranslationVoiceHandler.processTranslationCommand(userQuery)
            when (transRes) {
                is TranslationActionResult.Success -> {
                    saveHistory(userQuery, transRes.message, "TRANSLATION")
                    return@withContext AriaCommandResult.TextResponse(transRes.message, "TRANSLATION")
                }
                is TranslationActionResult.Failure -> {
                    saveHistory(userQuery, transRes.message, "TRANSLATION")
                    return@withContext AriaCommandResult.TextResponse(transRes.message, "TRANSLATION")
                }
            }
        }

        // 0.97 Google Maps Commands
        if (GoogleMapsVoiceHandler.isMapsCommand(queryLower)) {
            val mapsRes = GoogleMapsVoiceHandler.processMapsCommand(context, userQuery)
            when (mapsRes) {
                is MapsActionResult.Success -> {
                    saveHistory(userQuery, mapsRes.message, "MAPS")
                    return@withContext AriaCommandResult.TextResponse(mapsRes.message, "MAPS", openUrl = mapsRes.openUrl)
                }
                is MapsActionResult.Failure -> {
                    saveHistory(userQuery, mapsRes.message, "MAPS")
                    return@withContext AriaCommandResult.TextResponse(mapsRes.message, "MAPS")
                }
            }
        }

        // 0.98 Google Drive Commands
        if (GoogleDriveVoiceHandler.isDriveCommand(queryLower)) {
            val driveRes = GoogleDriveVoiceHandler.processDriveCommand(context, userQuery)
            when (driveRes) {
                is DriveActionResult.Success -> {
                    saveHistory(userQuery, driveRes.message, "DRIVE")
                    return@withContext AriaCommandResult.TextResponse(driveRes.message, "DRIVE", openUrl = driveRes.openUrl)
                }
                is DriveActionResult.Failure -> {
                    saveHistory(userQuery, driveRes.message, "DRIVE")
                    return@withContext AriaCommandResult.TextResponse(driveRes.message, "DRIVE")
                }
            }
        }

        // 0.99 Gmail Commands
        if (GmailVoiceHandler.isGmailCommand(queryLower)) {
            val gmailRes = GmailVoiceHandler.processGmailCommand(context, userQuery)
            when (gmailRes) {
                is GmailActionResult.Success -> {
                    saveHistory(userQuery, gmailRes.message, "GMAIL")
                    return@withContext AriaCommandResult.TextResponse(gmailRes.message, "GMAIL", openUrl = gmailRes.openUrl)
                }
                is GmailActionResult.Failure -> {
                    saveHistory(userQuery, gmailRes.message, "GMAIL")
                    return@withContext AriaCommandResult.TextResponse(gmailRes.message, "GMAIL")
                }
            }
        }

        // 0.995 Google Contacts Commands
        if (GoogleContactsVoiceHandler.isContactsCommand(queryLower)) {
            val contactsRes = GoogleContactsVoiceHandler.processContactsCommand(context, userQuery)
            when (contactsRes) {
                is ContactsActionResult.Success -> {
                    saveHistory(userQuery, contactsRes.message, "CONTACTS")
                    return@withContext AriaCommandResult.TextResponse(contactsRes.message, "CONTACTS")
                }
                is ContactsActionResult.Failure -> {
                    saveHistory(userQuery, contactsRes.message, "CONTACTS")
                    return@withContext AriaCommandResult.TextResponse(contactsRes.message, "CONTACTS")
                }
            }
        }

        // 1. Time & Date commands
        if (queryLower.contains("time") || queryLower.contains("samay") || queryLower.contains("waqt") || queryLower.contains("kitne baje")) {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val currentTime = timeFormat.format(Date())
            val reply = "Boss, abhi $currentTime hua hai."
            saveHistory(userQuery, reply, "TIME")
            return@withContext AriaCommandResult.TextResponse(reply, "TIME")
        }

        if (queryLower.contains("date") || queryLower.contains("tarikh") || queryLower.contains("taarikh") || queryLower.contains("aaj kaun sa din")) {
            val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
            val currentDate = dateFormat.format(Date())
            val reply = "Aaj ki date hai: $currentDate."
            saveHistory(userQuery, reply, "DATE")
            return@withContext AriaCommandResult.TextResponse(reply, "DATE")
        }

        // 1.5 Stop / Close YouTube or Web Browser commands
        if (queryLower.contains("close youtube") || queryLower.contains("stop youtube") ||
            queryLower.contains("youtube band") || queryLower.contains("youtube stop") ||
            queryLower.contains("close browser") || queryLower.contains("stop browser") ||
            queryLower.contains("close video") || queryLower.contains("stop video") ||
            queryLower.contains("browser band") || queryLower.contains("video band") ||
            queryLower.contains("band karo youtube") || queryLower.contains("band karo browser")
        ) {
            val reply = "YouTube aur Browser stop kar diya gaya hai, Boss."
            saveHistory(userQuery, reply, "BROWSER")
            return@withContext AriaCommandResult.CloseBrowser(reply)
        }

        // 2. ARIA Internal Tools (Notepad & Calculator)
        if (queryLower.contains("open notepad") || queryLower.contains("calculator") || queryLower.contains("kholo calculator") || queryLower.contains("notes")) {
            if (queryLower.contains("calculator") || queryLower.contains("calc")) {
                val reply = "Calculator open kar rahi hu."
                saveHistory(userQuery, reply, "APP")
                return@withContext AriaCommandResult.LaunchApp(reply, "CALCULATOR")
            } else if (queryLower.contains("notepad") || queryLower.contains("notes")) {
                val reply = "ARIA Notepad open kar rahi hu."
                saveHistory(userQuery, reply, "APP")
                return@withContext AriaCommandResult.LaunchApp(reply, "NOTEPAD")
            }
        }

        // 2.5 WhatsApp Contact Matching, Deep-linking, Messaging & Call Handling
        val waResult = WhatsAppContactResolver.processWhatsAppCommand(context, userQuery)
        if (waResult != null) {
            when (waResult) {
                is WhatsAppActionResult.Success -> {
                    saveHistory(userQuery, waResult.message, "WHATSAPP")
                    return@withContext AriaCommandResult.TextResponse(waResult.message, "WHATSAPP", openUrl = waResult.openUrl)
                }
                is WhatsAppActionResult.CallAdvice -> {
                    saveHistory(userQuery, waResult.message, "WHATSAPP")
                    return@withContext AriaCommandResult.TextResponse(waResult.message, "WHATSAPP", openUrl = waResult.openUrl)
                }
                is WhatsAppActionResult.ContactNotFound -> {
                    saveHistory(userQuery, waResult.message, "WHATSAPP")
                    return@withContext AriaCommandResult.TextResponse(waResult.message, "WHATSAPP")
                }
                is WhatsAppActionResult.Disambiguation -> {
                    saveHistory(userQuery, waResult.message, "WHATSAPP")
                    return@withContext AriaCommandResult.TextResponse(waResult.message, "WHATSAPP")
                }
                is WhatsAppActionResult.GeneralFailure -> {
                    saveHistory(userQuery, waResult.message, "WHATSAPP")
                    return@withContext AriaCommandResult.TextResponse(waResult.message, "WHATSAPP")
                }
            }
        }

        // FEATURE 3: Direct Phone Call to Contact (e.g., "call Rahul", "call 9876543210")
        if (ContactCallHandler.isDirectCallCommand(queryLower)) {
            val callResult = ContactCallHandler.processCallCommand(context, userQuery)
            when (callResult) {
                is ContactCallResult.Success -> {
                    saveHistory(userQuery, callResult.message, "CALL")
                    return@withContext AriaCommandResult.TextResponse(callResult.message, "CALL")
                }
                is ContactCallResult.Failure -> {
                    saveHistory(userQuery, callResult.message, "CALL")
                    return@withContext AriaCommandResult.TextResponse(callResult.message, "CALL")
                }
            }
        }

        // FEATURE 1: YouTube Data API Search & Auto-Play Top Video (e.g., "open youtube and search mr beast channel and play popular video")
        if (YouTubeVoiceHandler.isYouTubeCommand(queryLower)) {
            val ytResult = YouTubeVoiceHandler.processYouTubeCommand(context, userQuery)
            when (ytResult) {
                is YouTubeResult.Success -> {
                    saveHistory(userQuery, ytResult.message, "YOUTUBE")
                    return@withContext AriaCommandResult.TextResponse(ytResult.message, "YOUTUBE", openUrl = ytResult.openUrl)
                }
                is YouTubeResult.Failure -> {
                    saveHistory(userQuery, ytResult.message, "YOUTUBE")
                    return@withContext AriaCommandResult.TextResponse(ytResult.message, "YOUTUBE")
                }
            }
        }

        // FEATURE 2: Calendar Date Marking & Event Insertion (e.g., "open calendar and mark this date", "mark 15 august in calendar")
        if (CalendarVoiceHandler.isCalendarCommand(queryLower)) {
            val calResult = CalendarVoiceHandler.processCalendarCommand(context, ariaDao, userQuery)
            saveHistory(userQuery, calResult.message, "CALENDAR")
            return@withContext AriaCommandResult.TextResponse(calResult.message, "CALENDAR")
        }

        // 3. Smart App Launcher & Multi-step Search Processor (WhatsApp, YouTube, Flipkart, Chrome, Google, etc.)
        if (queryLower.contains("open") || queryLower.contains("kholo") ||
            queryLower.contains("launch") || queryLower.contains("chalao") ||
            queryLower.contains("search") || queryLower.contains("khojo") ||
            queryLower.contains("dhoondho") || queryLower.contains("find")
        ) {
            val launchResult = SmartAppOpener.processVoiceCommand(context, userQuery)
            when (launchResult) {
                is AppLaunchResult.NativeAppOpened -> {
                    saveHistory(userQuery, launchResult.message, "APP_LAUNCH")
                    return@withContext AriaCommandResult.TextResponse(launchResult.message, "APP_LAUNCH")
                }
                is AppLaunchResult.ExternalBrowserOpened -> {
                    saveHistory(userQuery, launchResult.message, "BROWSER")
                    return@withContext AriaCommandResult.TextResponse(launchResult.message, "BROWSER")
                }
                is AppLaunchResult.SearchExecuted -> {
                    saveHistory(userQuery, launchResult.message, "SEARCH")
                    return@withContext AriaCommandResult.TextResponse(launchResult.message, "SEARCH")
                }
            }
        }

        // 4. Reminders set command
        if (queryLower.contains("remind me") || queryLower.contains("reminder") || queryLower.contains("yaad dilana") || queryLower.contains("set reminder")) {
            val (task, timeStr) = extractReminderTaskAndTime(userQuery)
            val insertedId = ariaDao.insertReminder(
                ReminderEntity(
                    title = task,
                    timeString = timeStr
                )
            )
            com.example.notification.AriaNotificationScheduler.scheduleReminderNotification(
                context = context,
                reminderId = insertedId,
                title = task,
                timeString = timeStr
            )
            val logMsg = "ARIA_REMINDER: Voice reminder created with ID=$insertedId -> title='$task', time='$timeStr' saved in Room DB and scheduled local push notification"
            android.util.Log.d("ARIA_REMINDER", logMsg)
            println("[$logMsg]")

            val reply = "Ji Boss! Reminder set kar diya hai: '$task' at $timeStr."
            saveHistory(userQuery, reply, "REMINDER")
            return@withContext AriaCommandResult.TextResponse(reply, "REMINDER")
        }

        // 5. Wikipedia Search command
        if (queryLower.contains("wikipedia") || queryLower.contains("who is") || queryLower.contains("what is") || queryLower.contains("batayo") || queryLower.contains("kaun hai")) {
            val wikiTopic = extractWikiTopic(userQuery)
            if (wikiTopic.isNotBlank()) {
                try {
                    val wikiRes = WikipediaClient.service.getSummary(wikiTopic)
                    val pages = wikiRes.query?.pages
                    val extract = pages?.values?.firstOrNull()?.extract
                    if (!extract.isNullOrBlank()) {
                        val shortExtract = extract.take(250) + "..."
                        val reply = "Wikipedia se milne wali jankari: $shortExtract"
                        saveHistory(userQuery, reply, "WIKIPEDIA")
                        return@withContext AriaCommandResult.TextResponse(reply, "WIKIPEDIA", openUrl = "https://en.wikipedia.org/wiki/$wikiTopic")
                    }
                } catch (e: Exception) {
                    // Fallthrough to AI if wikipedia endpoint fails
                }
            }
        }

        // 6. Weather command
        if (queryLower.contains("weather") || queryLower.contains("mausam") || queryLower.contains("temperature") || queryLower.contains("ba بارش") || queryLower.contains("barish")) {
            val city = extractCity(userQuery)
            val weatherReply = tryGetWeather(city)
            saveHistory(userQuery, weatherReply, "WEATHER")
            return@withContext AriaCommandResult.TextResponse(weatherReply, "WEATHER")
        }

        // 7. Conversational AI fallback via Gemini API
        val aiReply = GeminiClient.queryAriaAi(userQuery)
        saveHistory(userQuery, aiReply, "AI")
        return@withContext AriaCommandResult.TextResponse(aiReply, "AI")
    }

    private suspend fun saveHistory(query: String, response: String, category: String) {
        ariaDao.insertVoiceHistory(
            VoiceHistoryEntity(
                query = query,
                response = response,
                category = category
            )
        )
    }

    private fun extractReminderTaskAndTime(query: String): Pair<String, String> {
        var clean = query.replace("remind me to", "", ignoreCase = true)
            .replace("remind me", "", ignoreCase = true)
            .replace("set reminder for", "", ignoreCase = true)
            .replace("set reminder to", "", ignoreCase = true)
            .replace("add reminder", "", ignoreCase = true)
            .replace("yaad dilana ki", "", ignoreCase = true)
            .replace("yaad dilana", "", ignoreCase = true)
            .trim()

        var extractedTime = ""
        // Check if query contains time patterns like "at 5 PM", "at 10:30 AM", "at 6pm"
        val timeRegex = "(?i)\\b(at|by)\\s+([0-1]?[0-9](:[0-5][0-9])?\\s*(am|pm))".toRegex()
        val match = timeRegex.find(clean)
        if (match != null) {
            extractedTime = match.groupValues[2].uppercase(Locale.ROOT)
            clean = clean.replace(match.value, "").trim()
        }

        if (clean.isBlank()) clean = "Important Task"
        val formattedTitle = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

        val finalTime = if (extractedTime.isNotBlank()) {
            extractedTime
        } else {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + 3600000))
        }

        return Pair(formattedTitle, finalTime)
    }

    private fun extractWikiTopic(query: String): String {
        return query.replace("wikipedia search", "", ignoreCase = true)
            .replace("wikipedia", "", ignoreCase = true)
            .replace("who is", "", ignoreCase = true)
            .replace("what is", "", ignoreCase = true)
            .replace("tell me about", "", ignoreCase = true)
            .replace("ke bare me batao", "", ignoreCase = true)
            .trim()
    }

    private fun extractCity(query: String): String {
        var clean = query.replace("weather in", "", ignoreCase = true)
            .replace("weather of", "", ignoreCase = true)
            .replace("mausam in", "", ignoreCase = true)
            .replace("mausam kaisa hai", "", ignoreCase = true)
            .replace("weather", "", ignoreCase = true)
            .trim()
        if (clean.isBlank()) clean = "Delhi"
        return clean
    }

    suspend fun tryGetWeather(city: String): String {
        return try {
            // First search city coordinates using Open-Meteo public geocoding API
            val geoRes = com.example.api.WeatherClient.geoService.searchCity(city)
            val location = geoRes.results?.firstOrNull()

            if (location != null && location.latitude != null && location.longitude != null) {
                val forecast = com.example.api.WeatherClient.forecastService.getForecast(
                    lat = location.latitude,
                    lon = location.longitude
                )
                val cw = forecast.current_weather
                val temp = cw?.temperature?.toInt() ?: 25
                val condition = com.example.api.mapWeatherCode(cw?.weathercode)
                val wind = cw?.windspeed?.toInt() ?: 10
                val cityName = location.name ?: city
                val country = location.country ?: ""
                val locationLabel = if (country.isNotBlank()) "$cityName, $country" else cityName

                "$locationLabel: $condition, $temp°C, Wind $wind km/h"
            } else {
                "$city: Clear sky, 27°C"
            }
        } catch (e: Exception) {
            "$city: Weather 26°C, Clear sky"
        }
    }

    /**
     * Aggregates upcoming reminders and local weather data into a 'Good morning' briefing summary.
     */
    suspend fun generateBriefingSummary(userName: String): String = withContext(Dispatchers.IO) {
        val displayName = userName.ifBlank { "Boss" }

        // Time-aware greeting
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }

        // Local weather information
        val weatherData = tryGetWeather("Delhi")

        // Fetch pending reminders
        val allRemindersList = try {
            ariaDao.getAllReminders().first()
        } catch (e: Exception) {
            emptyList()
        }
        val pendingReminders = allRemindersList.filter { !it.isCompleted }

        val briefingText = buildString {
            append("$timeGreeting $displayName! ☀️\n")
            append("Here is your launch briefing summary:\n\n")
            append("🌤️ Weather: $weatherData\n\n")

            if (pendingReminders.isNotEmpty()) {
                append("📌 Upcoming Reminders (${pendingReminders.size}):\n")
                pendingReminders.take(4).forEach { item ->
                    append("• ${item.title} at ${item.timeString}\n")
                }
                if (pendingReminders.size > 4) {
                    append("• ...and ${pendingReminders.size - 4} more\n")
                }
            } else {
                append("📌 Reminders: You have no pending tasks scheduled. All clear!\n")
            }

            append("\nHave a productive and fantastic day ahead!")
        }

        saveHistory("Launch Briefing", briefingText, "BRIEFING")
        briefingText
    }
}
