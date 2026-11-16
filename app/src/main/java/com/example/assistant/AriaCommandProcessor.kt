package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.api.GeminiClient
import com.example.api.WeatherClient
import com.example.api.WikipediaClient
import com.example.assistant.AriaScreenLockManager
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.claude.ClaudeReasoningEngine
import com.example.claude.ClaudeReasoningResult
import com.example.data.AriaDao
import com.example.data.ReminderEntity
import com.example.data.VoiceHistoryEntity
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AriaCommandResult {
    data class TextResponse(
        val response: String,
        val category: String,
        val openUrl: String? = null,
        val routineAction: String? = null
    ) : AriaCommandResult()
    data class LaunchApp(val response: String, val appType: String) : AriaCommandResult()
    data class CloseBrowser(val response: String) : AriaCommandResult()
}

class AriaCommandProcessor(
    private val context: Context,
    private val ariaDao: AriaDao
) {

    suspend fun processCommand(userQuery: String, userName: String = "Boss"): AriaCommandResult = withContext(Dispatchers.IO) {
        val queryLower = userQuery.lowercase(Locale.ROOT).trim()
        val displayName = userName.ifBlank { "Boss" }

        // 0.4 Custom Routines ("Good Morning ARC", "Good Night ARC", "office jaana hai")
        if (RoutineManager.isRoutineCommand(queryLower)) {
            val routineMgr = RoutineManager(context, ariaDao)
            val res = routineMgr.executeRoutine(userQuery, displayName)
            when (res) {
                is RoutineResult.Success -> {
                    saveHistory(userQuery, res.speechResponse, "ROUTINE")
                    return@withContext AriaCommandResult.TextResponse(
                        response = res.speechResponse,
                        category = "ROUTINE",
                        routineAction = res.routineName
                    )
                }
            }
        }

        // 0.405 Ride Booking Assist (Uber / Ola / Rapido) & Pending Bike/4-seater/6-seater Reply
        if (RideBookingAssistHandler.isPendingVehicleReply(queryLower) ||
            RideBookingAssistHandler.isRideBookingCommand(queryLower)
        ) {
            val rideRes = RideBookingAssistHandler.processRideCommandWithLocation(context, userQuery)
            val (reply, url) = when (rideRes) {
                is RideBookingResult.NeedsVehicleSelection -> Pair(rideRes.message, null)
                is RideBookingResult.Launched -> Pair(rideRes.message, rideRes.fallbackWebUrl)
                is RideBookingResult.Cancelled -> Pair(rideRes.message, null)
                is RideBookingResult.Failure -> Pair(rideRes.message, null)
            }
            saveHistory(userQuery, reply, "RIDE_ASSIST")
            return@withContext AriaCommandResult.TextResponse(reply, "RIDE_ASSIST", openUrl = url)
        }

        // 0.406 Shopping Search Assist (Flipkart / Flipkart Minutes / Blinkit / Zepto / Amazon / Instamart)
        if (ShoppingAssistHandler.isShoppingOrderCommand(queryLower)) {
            val shopRes = ShoppingAssistHandler.processShoppingCommand(context, userQuery)
            val (reply, url) = when (shopRes) {
                is ShoppingAssistResult.OpenedWithSearch -> Pair(shopRes.message, shopRes.openedUri.takeIf { it.startsWith("http") })
                is ShoppingAssistResult.OpenedAppOnly -> Pair(shopRes.message, shopRes.fallbackSearchUrl)
                is ShoppingAssistResult.Failure -> Pair(shopRes.message, null)
            }
            saveHistory(userQuery, reply, "SHOPPING_ASSIST")
            return@withContext AriaCommandResult.TextResponse(reply, "SHOPPING_ASSIST", openUrl = url)
        }

        // 0.40 Anti-Theft Siren Guard Commands ("ARC, security on karo", "security off karo", "siren guard")
        if (queryLower.contains("security on") || queryLower.contains("security arm") || queryLower.contains("siren on")) {
            val reply = com.example.assistant.security.AntiTheftSirenManager.armSecurity(context)
            saveHistory(userQuery, reply, "SECURITY")
            return@withContext AriaCommandResult.TextResponse(reply, "SECURITY")
        } else if (queryLower.contains("security off") || queryLower.contains("security disarm") || queryLower.contains("siren off")) {
            val reply = com.example.assistant.security.AntiTheftSirenManager.disarmSecurity(context)
            saveHistory(userQuery, reply, "SECURITY")
            return@withContext AriaCommandResult.TextResponse(reply, "SECURITY")
        }

        // 0.41 Hardware Controls: Flashlight, Volume (up/down/mute/full), Brightness, WiFi, Bluetooth, Battery
        if (queryLower.contains("flashlight on") || queryLower.contains("torch on") || queryLower.contains("torch jalao")) {
            val res = com.example.assistant.hardware.AndroidHardwareController.setTorchMode(context, true)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("flashlight off") || queryLower.contains("torch off") || queryLower.contains("torch band")) {
            val res = com.example.assistant.hardware.AndroidHardwareController.setTorchMode(context, false)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("volume mute") || queryLower.contains("awaaz band") || queryLower.contains("mute volume")) {
            val res = com.example.assistant.hardware.AndroidHardwareController.setVolumeExtreme(context, mute = true)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("full volume") || queryLower.contains("max volume") || queryLower.contains("volume full")) {
            val res = com.example.assistant.hardware.AndroidHardwareController.setVolumeExtreme(context, mute = false)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("volume badhao") || queryLower.contains("volume up") || queryLower.contains("awaaz badhao")) {
            val res = com.example.assistant.hardware.AndroidHardwareController.adjustVolume(context, true)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("volume kam") || queryLower.contains("volume down") || queryLower.contains("awaaz kam")) {
            val res = com.example.assistant.hardware.AndroidHardwareController.adjustVolume(context, false)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("brightness") || queryLower.contains("roshni") || queryLower.contains("screen light")) {
            val pctMatch = Regex("(\\d{1,3})\\s*%?").find(queryLower)?.groupValues?.get(1)?.toIntOrNull()
            val res = when {
                pctMatch != null && pctMatch in 1..100 ->
                    com.example.assistant.hardware.AndroidHardwareController.adjustBrightness(context, targetPercent = pctMatch)
                queryLower.contains("kam") || queryLower.contains("down") || queryLower.contains("low") || queryLower.contains("reduce") ->
                    com.example.assistant.hardware.AndroidHardwareController.adjustBrightness(context, directionUp = false)
                queryLower.contains("badhao") || queryLower.contains("up") || queryLower.contains("high") || queryLower.contains("full") ->
                    com.example.assistant.hardware.AndroidHardwareController.adjustBrightness(
                        context,
                        directionUp = if (queryLower.contains("full")) null else true,
                        targetPercent = if (queryLower.contains("full")) 100 else null
                    )
                else -> com.example.assistant.hardware.AndroidHardwareController.adjustBrightness(context, directionUp = true)
            }
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("battery percentage") || queryLower.contains("battery kitni hai") || queryLower.contains("battery status")) {
            val msg = com.example.assistant.hardware.AndroidHardwareController.getBatteryTelemetry(context)
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("wifi") || queryLower.contains("wi-fi")) {
            val enableTarget = when {
                queryLower.contains(" on") || queryLower.contains("chalu") || queryLower.contains("enable") -> true
                queryLower.contains(" off") || queryLower.contains("band") || queryLower.contains("disable") -> false
                else -> null
            }
            val res = com.example.assistant.hardware.AndroidHardwareController.openWifiControl(context, enableTarget)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        } else if (queryLower.contains("bluetooth")) {
            val enableTarget = when {
                queryLower.contains(" on") || queryLower.contains("chalu") || queryLower.contains("enable") -> true
                queryLower.contains(" off") || queryLower.contains("band") || queryLower.contains("disable") -> false
                else -> null
            }
            val res = com.example.assistant.hardware.AndroidHardwareController.openBluetoothControl(context, enableTarget)
            val msg = when (res) { is com.example.assistant.hardware.HardwareActionResult.Success -> res.message; is com.example.assistant.hardware.HardwareActionResult.Failure -> res.message }
            saveHistory(userQuery, msg, "HARDWARE")
            return@withContext AriaCommandResult.TextResponse(msg, "HARDWARE")
        }

        // 0.415 WhatsApp Voice Note Recording / Sending & Unread Notification Reader
        if (queryLower.contains("voice note") || queryLower.contains("audio message") || queryLower.contains("voice message")) {
            val contactHint = extractVoiceNoteRecipient(userQuery)
            val isRecordingNow = com.example.assistant.whatsapp.WhatsAppAutomationManager.isRecordingActive()

            if (isRecordingNow && (queryLower.contains("stop") || queryLower.contains("send") || queryLower.contains("bhej") || contactHint.isNotBlank())) {
                val targetName = contactHint.ifBlank { pendingVoiceNoteRecipient.ifBlank { "WhatsApp" } }
                pendingVoiceNoteRecipient = ""
                val vnResult = com.example.assistant.whatsapp.WhatsAppAutomationManager.stopAndSendVoiceNote(context, targetName)
                val reply = when (vnResult) {
                    is WhatsAppActionResult.Success -> vnResult.message
                    is WhatsAppActionResult.GeneralFailure -> vnResult.message
                    else -> "Voice note WhatsApp par share kiya gaya."
                }
                saveHistory(userQuery, reply, "WHATSAPP")
                return@withContext AriaCommandResult.TextResponse(reply, "WHATSAPP")
            } else if (!isRecordingNow) {
                if (contactHint.isNotBlank()) pendingVoiceNoteRecipient = contactHint
                val started = com.example.assistant.whatsapp.WhatsAppAutomationManager.startVoiceNoteRecording(context)
                val reply = if (started) {
                    if (pendingVoiceNoteRecipient.isNotBlank()) {
                        "Voice note recording chalu ho gayi hai ($pendingVoiceNoteRecipient ke liye) 🎙️. Boliye aur khatam hone par 'send voice note' kahiye!"
                    } else {
                        "Voice note recording chalu ho gayi hai 🎙️. Boliye aur bhejne ke liye 'send voice note to <name>' kahiye!"
                    }
                } else {
                    "Microphone se voice note record nahi ho saka — Record Audio permission check kijiye."
                }
                saveHistory(userQuery, reply, "WHATSAPP")
                return@withContext AriaCommandResult.TextResponse(reply, "WHATSAPP")
            }
        }

        if (queryLower.contains("whatsapp messages padho") || queryLower.contains("read whatsapp") || queryLower.contains("messages sunao")) {
            if (!com.example.assistant.whatsapp.WhatsAppAutomationManager.isNotificationAccessGranted(context)) {
                com.example.assistant.whatsapp.WhatsAppAutomationManager.openNotificationAccessSettings(context)
                val permMsg = "Boss, WhatsApp messages padhne ke liye Notification Access permission enable kijiye. Maine settings screen khol di hai."
                saveHistory(userQuery, permMsg, "WHATSAPP")
                return@withContext AriaCommandResult.TextResponse(permMsg, "WHATSAPP")
            }
            val summary = com.example.assistant.whatsapp.WhatsAppAutomationManager.getUnreadWhatsAppMessagesSummary()
            saveHistory(userQuery, summary, "WHATSAPP")
            return@withContext AriaCommandResult.TextResponse(summary, "WHATSAPP")
        }

        // 0.416 Screen Vision: Read Screen Content (Accessibility + ML Kit OCR), On-Device Translate, & Auto-Tap
        if (queryLower.contains("translate screen") || queryLower.contains("screen translate") || queryLower.contains("screen anuvaad")) {
            val isEnabled = com.example.assistant.accessibility.AriaAccessibilityGestureService.checkAccessibilityEnabled(context) ||
                com.example.assistant.accessibility.AriaAccessibilityGestureService.isServiceRunning()
            if (!isEnabled) {
                com.example.assistant.accessibility.AriaAccessibilityGestureService.openAccessibilitySettings(context)
                val errMsg = "Screen translate karne ke liye Accessibility Service ON kijiye. Maine Accessibility settings khol di hai."
                saveHistory(userQuery, errMsg, "SCREEN_VISION")
                return@withContext AriaCommandResult.TextResponse(errMsg, "SCREEN_VISION")
            }
            val targetLang = detectMlKitTargetLanguage(queryLower)
            val translated = com.example.assistant.accessibility.AriaAccessibilityGestureService.translateScreenContentSuspend(targetLang)
            saveHistory(userQuery, translated, "SCREEN_VISION")
            return@withContext AriaCommandResult.TextResponse(translated, "SCREEN_VISION")
        } else if (queryLower.contains("read screen") || queryLower.contains("screen padho") || queryLower.contains("screen pe kya likha hai") || queryLower.contains("ocr")) {
            val isEnabled = com.example.assistant.accessibility.AriaAccessibilityGestureService.checkAccessibilityEnabled(context) ||
                com.example.assistant.accessibility.AriaAccessibilityGestureService.isServiceRunning()
            if (!isEnabled) {
                com.example.assistant.accessibility.AriaAccessibilityGestureService.openAccessibilitySettings(context)
                val errMsg = "Screen padhne ke liye ARC Accessibility Service ON kijiye. Maine Accessibility settings khol di hai."
                saveHistory(userQuery, errMsg, "SCREEN_VISION")
                return@withContext AriaCommandResult.TextResponse(errMsg, "SCREEN_VISION")
            }
            val forceOcr = queryLower.contains("ocr") || queryLower.contains("image") || queryLower.contains("photo")
            val screenResult = com.example.assistant.accessibility.AriaAccessibilityGestureService.readScreenContentSuspend(forceOcr)
            saveHistory(userQuery, screenResult, "SCREEN_VISION")
            return@withContext AriaCommandResult.TextResponse(screenResult, "SCREEN_VISION")
        } else if (queryLower.contains("button dabao") || queryLower.contains("tap button") || queryLower.contains("click ")) {
            val isEnabled = com.example.assistant.accessibility.AriaAccessibilityGestureService.checkAccessibilityEnabled(context) ||
                com.example.assistant.accessibility.AriaAccessibilityGestureService.isServiceRunning()
            if (!isEnabled) {
                com.example.assistant.accessibility.AriaAccessibilityGestureService.openAccessibilitySettings(context)
                val errMsg = "Auto-tap ke liye ARC Accessibility Service ON kijiye. Maine Accessibility settings khol di hai."
                saveHistory(userQuery, errMsg, "SCREEN_VISION")
                return@withContext AriaCommandResult.TextResponse(errMsg, "SCREEN_VISION")
            }
            val targetBtn = userQuery.replace("button dabao", "", ignoreCase = true)
                .replace("tap button", "", ignoreCase = true)
                .replace("click", "", ignoreCase = true).trim()
            val tapRes = com.example.assistant.accessibility.AriaAccessibilityGestureService.autoTapButtonByNameSuspend(targetBtn)
            val tapMsg = when (tapRes) {
                is com.example.assistant.accessibility.GestureResult.Success -> tapRes.message
                is com.example.assistant.accessibility.GestureResult.Failure -> tapRes.message
            }
            saveHistory(userQuery, tapMsg, "SCREEN_VISION")
            return@withContext AriaCommandResult.TextResponse(tapMsg, "SCREEN_VISION")
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
                val errMsg = "Boss, automated screen gestures ke liye ARC ki Accessibility Service enable honi chahiye. Settings > Accessibility me jakar 'A.R.I.A. Voice Gestures' ko ON kijiye."
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
            val manualMorningOverride = if (queryLower.contains("good morning") || queryLower.contains("morning")) {
                "Good morning"
            } else {
                null
            }
            val briefingText = generateBriefingSummary(displayName, overrideGreeting = manualMorningOverride)
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

        // 0.998 Battery Saver & Power Optimization Commands
        if (queryLower.contains("battery saver") || queryLower.contains("power saver") ||
            queryLower.contains("battery status") || queryLower.contains("battery percentage") ||
            queryLower.contains("battery kitna") || queryLower.contains("battery kitni") ||
            queryLower.contains("battery level") || queryLower.contains("save battery") ||
            queryLower.contains("power mode")
        ) {
            val reply = if (queryLower.contains("on") || queryLower.contains("enable") || queryLower.contains("chalu") || queryLower.contains("start")) {
                AriaBatterySaverManager.setManualOverride(true, context)
                "Boss, Battery Saver mode ON kar diya gaya hai! 🔋⚡ Background sync frequency limit ho gayi hai aur UI/HUD animations pause ho chuki hain."
            } else if (queryLower.contains("off") || queryLower.contains("disable") || queryLower.contains("band") || queryLower.contains("stop")) {
                AriaBatterySaverManager.setManualOverride(false, context)
                "Battery Saver mode OFF kar diya gaya hai. Background sync and full reactive animations normal chal rahe hain, Boss! ⚡"
            } else if (queryLower.contains("auto")) {
                AriaBatterySaverManager.resetToAutoMode(context)
                "Battery Saver AUTO mode par switch kar diya hai. Battery 20% se kam hone par automatically activate ho jayega. 🔋"
            } else {
                AriaBatterySaverManager.getStatusSummary()
            }
            saveHistory(userQuery, reply, "BATTERY")
            return@withContext AriaCommandResult.TextResponse(reply, "BATTERY")
        }

        // 1. Time & Date commands
        if (queryLower.contains("time") || queryLower.contains("samay") || queryLower.contains("waqt") ||
            queryLower.contains("kitne baje") || queryLower.contains("kitna baja") || queryLower.contains("kya baje")
        ) {
            val clockInfo = DeviceClockHelper.getCurrentDeviceClockInfo(
                context = context,
                respect24HourSetting = false,
                logDebug = true,
                source = "VoiceTimeCommand"
            )
            val reply = "Boss, abhi ${clockInfo.formattedTime} hua hai."
            saveHistory(userQuery, reply, "TIME")
            return@withContext AriaCommandResult.TextResponse(reply, "TIME")
        }

        if (queryLower.contains("date") || queryLower.contains("tarikh") || queryLower.contains("taarikh") || queryLower.contains("aaj kaun sa din")) {
            val clockInfo = DeviceClockHelper.getCurrentDeviceClockInfo(
                context = context,
                logDebug = true,
                source = "VoiceDateCommand"
            )
            val reply = "Aaj ki date hai: ${clockInfo.formattedDate}."
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
                val reply = "Calculator open kar raha hu."
                saveHistory(userQuery, reply, "APP")
                return@withContext AriaCommandResult.LaunchApp(reply, "CALCULATOR")
            } else if (queryLower.contains("notepad") || queryLower.contains("notes")) {
                val reply = "ARC Notepad open kar raha hu."
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
        if (queryLower.contains("weather") || queryLower.contains("mausam") || queryLower.contains("temperature") || queryLower.contains("barish")) {
            val city = extractCity(userQuery)
            val weatherReply = tryGetWeather(city)
            saveHistory(userQuery, weatherReply, "WEATHER")
            return@withContext AriaCommandResult.TextResponse(weatherReply, "WEATHER")
        }

        // 7. Conversational & Multi-Step Reasoning Brain: Claude (Anthropic) primary -> Gemini API fallback
        val claudeResult = ClaudeReasoningEngine.reasonAndExecute(
            context = context,
            userTranscript = userQuery,
            userName = displayName,
            executeTools = true
        )
        if (claudeResult is ClaudeReasoningResult.Success) {
            saveHistory(userQuery, claudeResult.spokenResponse, "CLAUDE_BRAIN")
            return@withContext AriaCommandResult.TextResponse(claudeResult.spokenResponse, "CLAUDE_BRAIN")
        }

        // Seamless fallback to Gemini if Claude API key is missing or Anthropic endpoint fails
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

    internal fun extractCity(query: String): String? {
        val clean = query
            .replace(Regex("(?i)\\b(weather in|weather of|weather at|weather for|mausam in|mausam of|mausam kaisa hai|aaj ka mausam|weather update|weather today|live weather|current location weather|local weather|temperature|weather|mausam|barish|today|aaj|ka|ki|ke|kaisa|hai|hogi|kya|batao|batayein|dikhao|check|show|me|please|my|current|location)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return clean.takeIf { it.isNotBlank() }
    }

    data class WeatherSnapshot(
        val tempDisplay: String,
        val condition: String,
        val city: String,
        val summaryLine: String,
        val isCurrentLocation: Boolean = false
    )

    suspend fun fetchWeatherSnapshot(
        city: String? = null,
        forceLocationRefresh: Boolean = false
    ): WeatherSnapshot {
        val requestedCity = city?.trim()?.takeIf { it.isNotBlank() }

        // If no specific city was named, automatically resolve current GPS coordinates & city
        if (requestedCity == null) {
            val gpsLoc = ArcLocationManager.getCurrentLocationInfo(context, forceRefresh = forceLocationRefresh)
            if (gpsLoc != null) {
                val cityName = gpsLoc.city ?: gpsLoc.locality ?: "Your Location"
                val label = gpsLoc.displayLabel()
                return try {
                    val forecast = com.example.api.WeatherClient.forecastService.getForecast(
                        lat = gpsLoc.latitude,
                        lon = gpsLoc.longitude
                    )
                    val cw = forecast.current_weather
                    val temp = cw?.temperature?.toInt() ?: 26
                    val condition = com.example.api.mapWeatherCode(cw?.weathercode)
                    val wind = cw?.windspeed?.toInt() ?: 10
                    WeatherSnapshot(
                        tempDisplay = "${temp}°C",
                        condition = condition,
                        city = cityName,
                        summaryLine = "$label: $condition, $temp°C, Wind $wind km/h",
                        isCurrentLocation = true
                    )
                } catch (_: Exception) {
                    WeatherSnapshot(
                        tempDisplay = "26°C",
                        condition = "Clear sky",
                        city = cityName,
                        summaryLine = "$label: Clear sky, 26°C",
                        isCurrentLocation = true
                    )
                }
            }
        }

        val fallbackCity = requestedCity ?: "Delhi"
        return try {
            val geoRes = com.example.api.WeatherClient.geoService.searchCity(fallbackCity)
            val location = geoRes.results?.firstOrNull()

            if (location != null && location.latitude != null && location.longitude != null) {
                val forecast = com.example.api.WeatherClient.forecastService.getForecast(
                    lat = location.latitude,
                    lon = location.longitude
                )
                val cw = forecast.current_weather
                val temp = cw?.temperature?.toInt() ?: 26
                val condition = com.example.api.mapWeatherCode(cw?.weathercode)
                val wind = cw?.windspeed?.toInt() ?: 10
                val cityName = location.name ?: fallbackCity
                val country = location.country ?: ""
                val locationLabel = if (country.isNotBlank()) "$cityName, $country" else cityName

                WeatherSnapshot(
                    tempDisplay = "${temp}°C",
                    condition = condition,
                    city = cityName,
                    summaryLine = "$locationLabel: $condition, $temp°C, Wind $wind km/h",
                    isCurrentLocation = false
                )
            } else {
                WeatherSnapshot(
                    tempDisplay = "26°C",
                    condition = "Clear sky",
                    city = fallbackCity,
                    summaryLine = "$fallbackCity: Clear sky, 26°C",
                    isCurrentLocation = false
                )
            }
        } catch (e: Exception) {
            WeatherSnapshot(
                tempDisplay = "26°C",
                condition = "Clear sky",
                city = fallbackCity,
                summaryLine = "$fallbackCity: Weather 26°C, Clear sky",
                isCurrentLocation = false
            )
        }
    }

    suspend fun tryGetWeather(city: String? = null): String {
        return fetchWeatherSnapshot(city).summaryLine
    }

    /**
     * Cohesive Auto-Greeting briefing:
     * Time-aware greeting ("Hello [Name]! Good morning/afternoon/evening/night"),
     * today's local GPS weather (or Delhi fallback), and today's tasks/reminders.
     *
     * @param overrideGreeting Optional manual override (e.g. "Good morning" from "Good Morning ARC" routine).
     * When null, automatically resolves greeting from fresh live system clock & timezone.
     */
    suspend fun generateBriefingSummary(
        userName: String,
        overrideGreeting: String? = null
    ): String = withContext(Dispatchers.IO) {
        val displayName = userName.ifBlank { "Boss" }

        val greetingDecision = DeviceClockHelper.evaluateTimeBasedGreeting(
            context = context,
            source = if (overrideGreeting != null) "ManualMorningRoutine" else "AutoBriefingGreeting",
            overrideGreeting = overrideGreeting,
            logDebug = true
        )
        val timeGreeting = greetingDecision.greeting

        val weather = fetchWeatherSnapshot(city = null)

        val allRemindersList = try {
            ariaDao.getAllReminders().first()
        } catch (e: Exception) {
            emptyList()
        }
        val pendingReminders = allRemindersList.filter { !it.isCompleted }

        val briefingText = buildString {
            append("Hello $displayName! $timeGreeting.\n")
            append("${weather.city} ka weather aaj ${weather.tempDisplay}, ${weather.condition.lowercase(Locale.ROOT)} hai.\n")

            if (pendingReminders.isNotEmpty()) {
                append("Aaj aapke ${pendingReminders.size} tasks pending hain: ")
                append(pendingReminders.take(3).joinToString(", ") { "${it.title} at ${it.timeString}" })
                if (pendingReminders.size > 3) {
                    append(", aur ${pendingReminders.size - 3} aur tasks")
                }
                append(".")
            } else {
                append("Aaj aapka koi task nahi hai.")
            }
        }

        saveHistory("ARC Auto Briefing", briefingText, "BRIEFING")
        briefingText
    }

    private var pendingVoiceNoteRecipient: String = ""

    private fun extractVoiceNoteRecipient(query: String): String {
        val cleaned = query
            .replace("record", "", ignoreCase = true)
            .replace("start", "", ignoreCase = true)
            .replace("stop", "", ignoreCase = true)
            .replace("send", "", ignoreCase = true)
            .replace("whatsapp", "", ignoreCase = true)
            .replace("voice note", "", ignoreCase = true)
            .replace("audio message", "", ignoreCase = true)
            .replace("voice message", "", ignoreCase = true)
            .replace("bhejo", "", ignoreCase = true)
            .replace("karo", "", ignoreCase = true)
            .replace("for", "", ignoreCase = true)
            .replace("to", "", ignoreCase = true)
            .replace("ko", "", ignoreCase = true)
            .replace("ka", "", ignoreCase = true)
            .trim()
        return if (cleaned.length >= 2) cleaned else ""
    }

    private fun detectMlKitTargetLanguage(queryLower: String): String {
        return when {
            queryLower.contains("english") || queryLower.contains("angrezi") -> TranslateLanguage.ENGLISH
            queryLower.contains("marathi") -> TranslateLanguage.MARATHI
            queryLower.contains("gujarati") -> TranslateLanguage.GUJARATI
            queryLower.contains("bengali") || queryLower.contains("bangla") -> TranslateLanguage.BENGALI
            queryLower.contains("tamil") -> TranslateLanguage.TAMIL
            queryLower.contains("telugu") -> TranslateLanguage.TELUGU
            queryLower.contains("kannada") -> TranslateLanguage.KANNADA
            queryLower.contains("urdu") -> TranslateLanguage.URDU
            else -> TranslateLanguage.HINDI
        }
    }
}
