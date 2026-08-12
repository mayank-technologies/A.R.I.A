package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.assistant.AriaCommandProcessor
import com.example.assistant.AriaCommandResult
import com.example.data.AriaDatabase
import com.example.data.ReminderEntity
import com.example.data.VoiceHistoryEntity
import com.example.speech.AriaSpeechEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AssistantStatus {
    IDLE, LISTENING, PROCESSING, SPEAKING, SLEEP
}

data class ActivityLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val icon: String = "⚡",
    val title: String,
    val detail: String = "",
    val timeAgo: String = "Just now"
)

data class MemoryFactItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val category: String = "General"
)

class AriaViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)

    private val db = AriaDatabase.getDatabase(application)
    private val dao = db.ariaDao()
    private val commandProcessor = AriaCommandProcessor(application, dao)
    val speechEngine = AriaSpeechEngine(application)

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(false)
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete.asStateFlow()

    private val _isPermissionsGranted = MutableStateFlow(false)
    val isPermissionsGranted: StateFlow<Boolean> = _isPermissionsGranted.asStateFlow()

    val isMuted: StateFlow<Boolean> = speechEngine.isMuted
    val speechRate: StateFlow<Float> = speechEngine.speechRate
    val isSpeaking: StateFlow<Boolean> = speechEngine.isSpeaking
    val isSynthesizing: StateFlow<Boolean> = speechEngine.isSynthesizing
    val isWakeWordModeEnabled: StateFlow<Boolean> = speechEngine.isWakeWordModeEnabled

    fun setElevenLabsApiKey(key: String) {
        speechEngine.setApiKey(key)
    }

    fun toggleWakeWordMode(): Boolean {
        val newState = speechEngine.toggleWakeWordMode()
        prefs.edit().putBoolean("wake_word_enabled", newState).apply()
        if (newState) {
            _latestResponse.value = "Wake-Word Detection Active 🎙️ Say 'Hey ARIA' anytime hands-free!"
            speakResponse("Wake word detection active, Boss! Say Hey ARIA anytime.")
        } else {
            _latestResponse.value = "Wake-Word Detection Paused."
        }
        return newState
    }

    val reminders: StateFlow<List<ReminderEntity>> = dao.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceHistory: StateFlow<List<VoiceHistoryEntity>> = dao.getVoiceHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _assistantStatus = MutableStateFlow(AssistantStatus.IDLE)
    val assistantStatus: StateFlow<AssistantStatus> = _assistantStatus.asStateFlow()

    private val _latestResponse = MutableStateFlow("Namaste Boss! Mai ARIA hu, aapka AI Voice Assistant.")
    val latestResponse: StateFlow<String> = _latestResponse.asStateFlow()

    private val _lastQuery = MutableStateFlow("")
    val lastQuery: StateFlow<String> = _lastQuery.asStateFlow()

    private val _pendingWebUrl = MutableStateFlow<String?>(null)
    val pendingWebUrl: StateFlow<String?> = _pendingWebUrl.asStateFlow()

    private val _activeWebUrl = MutableStateFlow<String?>(null)
    val activeWebUrl: StateFlow<String?> = _activeWebUrl.asStateFlow()

    private val _activeAppDialog = MutableStateFlow<String?>(null)
    val activeAppDialog: StateFlow<String?> = _activeAppDialog.asStateFlow()

    private val _activityLogs = MutableStateFlow<List<ActivityLogItem>>(
        listOf(
            ActivityLogItem(icon = "💬", title = "Response generated", detail = "ARIA core ready", timeAgo = "Just now"),
            ActivityLogItem(icon = "🎙️", title = "User spoke", detail = "Standing by for prompt", timeAgo = "1m ago"),
            ActivityLogItem(icon = "⚙️", title = "Planning response", detail = "Gemini LLM pipeline ready", timeAgo = "2m ago"),
            ActivityLogItem(icon = "🟢", title = "ARIA online", detail = "Realtime link established", timeAgo = "3m ago"),
            ActivityLogItem(icon = "🟢", title = "ARIA core online", detail = "All systems nominal", timeAgo = "4m ago")
        )
    )
    val activityLogs: StateFlow<List<ActivityLogItem>> = _activityLogs.asStateFlow()

    private val _memoryFacts = MutableStateFlow<List<MemoryFactItem>>(
        listOf(
            MemoryFactItem(key = "Operator Name", value = prefs.getString("user_name", "Boss") ?: "Boss", category = "Identity"),
            MemoryFactItem(key = "Wake-Word State", value = "Hey ARIA (Hands-free active)", category = "Settings"),
            MemoryFactItem(key = "Primary Voice", value = "ElevenLabs Neural Engine", category = "Speech"),
            MemoryFactItem(key = "Default Navigation", value = "Google Maps", category = "Apps"),
            MemoryFactItem(key = "System Preference", value = "Hinglish + English Bilingual", category = "Language")
        )
    )
    val memoryFacts: StateFlow<List<MemoryFactItem>> = _memoryFacts.asStateFlow()

    init {
        checkOnboardingStatus()
    }

    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val savedName = prefs.getString("user_name", "") ?: ""
            val hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)
            val hasCompletedPermissions = prefs.getBoolean("has_completed_permissions", false)

            val logMsg = "ARIA_ONBOARDING: Startup Check -> saved user_name='$savedName', onboarding=$hasCompletedOnboarding, permissions=$hasCompletedPermissions"
            android.util.Log.d("ARIA_ONBOARDING", logMsg)
            println("[$logMsg]")

            val isComplete = hasCompletedOnboarding && savedName.isNotBlank()
            _userName.value = savedName
            _isOnboardingComplete.value = isComplete
            _isPermissionsGranted.value = hasCompletedPermissions

            if (isComplete) {
                _latestResponse.value = "Namaste $savedName! Mai ARIA hu, aapka AI Voice Assistant."
            } else {
                _latestResponse.value = "Namaste Boss! Mai ARIA hu, aapka AI Voice Assistant."
            }

            _isInitializing.value = false

            if (isComplete && hasCompletedPermissions) {
                triggerBriefingSummary()
            }
        }
    }

    fun triggerBriefingSummary() {
        viewModelScope.launch {
            _assistantStatus.value = AssistantStatus.PROCESSING
            val briefingText = commandProcessor.generateBriefingSummary(_userName.value)
            _latestResponse.value = briefingText
            _assistantStatus.value = AssistantStatus.IDLE
            speakResponse(briefingText)
        }
    }

    fun completeOnboarding(name: String) {
        val trimmedName = name.trim()
        prefs.edit()
            .putString("user_name", trimmedName)
            .putBoolean("has_completed_onboarding", true)
            .apply()

        _userName.value = trimmedName
        _isOnboardingComplete.value = true

        val logMsg = "ARIA_ONBOARDING: Complete Onboarding -> name='$trimmedName'"
        android.util.Log.d("ARIA_ONBOARDING", logMsg)
        println("[$logMsg]")
    }

    fun completePermissionsSetup() {
        prefs.edit()
            .putBoolean("has_completed_permissions", true)
            .apply()

        _isPermissionsGranted.value = true
        triggerBriefingSummary()

        val logMsg = "ARIA_PERMISSIONS: Permissions setup marked complete in SharedPreferences"
        android.util.Log.d("ARIA_PERMISSIONS", logMsg)
        println("[$logMsg]")
    }

    fun resetOnboarding() {
        prefs.edit()
            .remove("user_name")
            .remove("has_completed_onboarding")
            .remove("has_completed_permissions")
            .putBoolean("has_completed_onboarding", false)
            .putBoolean("has_completed_permissions", false)
            .apply()

        _userName.value = ""
        _isOnboardingComplete.value = false
        _isPermissionsGranted.value = false

        val logMsg = "ARIA_ONBOARDING: Reset Onboarding -> cleared name and reset permissions state"
        android.util.Log.d("ARIA_ONBOARDING", logMsg)
        println("[$logMsg]")
    }

    fun submitTextCommand(query: String) {
        if (query.isBlank()) return

        addActivityLog("🎙️", "User spoke", "\"$query\"")

        val parseResult = AriaSpeechEngine.parseWakeWord(query)
        if (parseResult.hasWakeWord && parseResult.isOnlyWakeWord) {
            _lastQuery.value = query
            _assistantStatus.value = AssistantStatus.PROCESSING
            val reply = "Haan Boss! Mai sun rahi hu, boliye kya karna hai?"
            _latestResponse.value = reply
            addActivityLog("💬", "Response generated", reply)
            speakResponse(reply)
            viewModelScope.launch {
                kotlinx.coroutines.delay(1800)
                startListening()
            }
            return
        }

        val actualQuery = if (parseResult.hasWakeWord && parseResult.command.isNotBlank()) parseResult.command else query
        _lastQuery.value = query
        _assistantStatus.value = AssistantStatus.PROCESSING
        addActivityLog("⚙️", "Planning response", "Processing intent: \"$actualQuery\"")

        viewModelScope.launch {
            val result = commandProcessor.processCommand(actualQuery)
            when (result) {
                is AriaCommandResult.TextResponse -> {
                    _latestResponse.value = result.response
                    addActivityLog("💬", "Response generated", result.response.take(60) + if (result.response.length > 60) "..." else "")
                    if (result.openUrl != null) {
                        _pendingWebUrl.value = result.openUrl
                        _activeWebUrl.value = result.openUrl
                        addActivityLog("🌐", "Web URL opened", result.openUrl)
                    }
                    speakResponse(result.response)
                }
                is AriaCommandResult.LaunchApp -> {
                    _latestResponse.value = result.response
                    _activeAppDialog.value = result.appType
                    addActivityLog("📱", "App launched", result.appType)
                    speakResponse(result.response)
                }
                is AriaCommandResult.CloseBrowser -> {
                    _latestResponse.value = result.response
                    closeWebBrowser()
                    addActivityLog("✖️", "Browser closed", "Returned to Voice HUD")
                    speakResponse(result.response)
                }
            }
        }
    }

    fun startListening() {
        if (_assistantStatus.value == AssistantStatus.SLEEP) {
            _assistantStatus.value = AssistantStatus.IDLE
        }
        _assistantStatus.value = AssistantStatus.LISTENING
        speechEngine.startListening { recognizedText ->
            if (recognizedText.isNotBlank()) {
                submitTextCommand(recognizedText)
            } else {
                _assistantStatus.value = AssistantStatus.IDLE
            }
        }
    }

    fun enterSleepMode() {
        if (_assistantStatus.value == AssistantStatus.LISTENING) {
            speechEngine.stopListening()
        }
        speechEngine.stopSpeaking()
        _assistantStatus.value = AssistantStatus.SLEEP
        _latestResponse.value = "ARIA in low-power Standby Mode. Tap 'Wake Up' or Mic button to activate."
    }

    fun wakeUp() {
        _assistantStatus.value = AssistantStatus.IDLE
        speakResponse("ARIA active and ready, Boss!")
    }

    fun toggleSleepMode() {
        if (_assistantStatus.value == AssistantStatus.SLEEP) {
            wakeUp()
        } else {
            enterSleepMode()
        }
    }

    fun stopSpeaking() {
        speechEngine.stopSpeaking()
        _assistantStatus.value = AssistantStatus.IDLE
    }

    fun toggleTtsMute(): Boolean {
        return speechEngine.toggleMute()
    }

    fun setSpeechRate(rate: Float) {
        speechEngine.setSpeechRate(rate)
    }

    fun replaySpeech() {
        if (_latestResponse.value.isNotBlank()) {
            speakResponse(_latestResponse.value)
        }
    }

    private fun speakResponse(text: String) {
        if (speechEngine.isMuted.value) {
            _assistantStatus.value = AssistantStatus.IDLE
            return
        }
        _assistantStatus.value = AssistantStatus.SPEAKING
        speechEngine.speak(text)
        viewModelScope.launch {
            // Monitor speaking status
            speechEngine.isSpeaking.collect { isSpeaking ->
                if (!isSpeaking && _assistantStatus.value == AssistantStatus.SPEAKING) {
                    _assistantStatus.value = AssistantStatus.IDLE
                }
            }
        }
    }

    fun openWebUrlInApp(url: String) {
        com.example.assistant.SmartAppOpener.launchExternalBrowser(getApplication(), url)
    }

    fun closeWebBrowser() {
        _activeWebUrl.value = null
        _pendingWebUrl.value = null
        speechEngine.stopSpeaking()
        if (_assistantStatus.value == AssistantStatus.SPEAKING) {
            _assistantStatus.value = AssistantStatus.IDLE
        }
    }

    fun clearWebUrl() {
        _pendingWebUrl.value = null
    }

    fun closeAppDialog() {
        _activeAppDialog.value = null
    }

    fun addReminder(title: String, timeStr: String) {
        viewModelScope.launch {
            val id = dao.insertReminder(ReminderEntity(title = title, timeString = timeStr))
            com.example.notification.AriaNotificationScheduler.scheduleReminderNotification(
                context = getApplication(),
                reminderId = id,
                title = title,
                timeString = timeStr
            )
        }
    }

    fun toggleReminderCompletion(reminder: ReminderEntity) {
        viewModelScope.launch {
            val updated = reminder.copy(isCompleted = !reminder.isCompleted)
            dao.updateReminder(updated)
            if (updated.isCompleted) {
                com.example.notification.AriaNotificationScheduler.cancelReminderNotification(
                    context = getApplication(),
                    reminderId = reminder.id
                )
            } else {
                com.example.notification.AriaNotificationScheduler.scheduleReminderNotification(
                    context = getApplication(),
                    reminderId = reminder.id,
                    title = reminder.title,
                    timeString = reminder.timeString
                )
            }
        }
    }

    fun snoozeReminder(reminder: ReminderEntity, minutes: Int) {
        viewModelScope.launch {
            val newTimeString = com.example.notification.AriaNotificationScheduler.scheduleReminderNotificationInMinutes(
                context = getApplication(),
                reminderId = reminder.id,
                title = reminder.title,
                minutes = minutes
            )
            val updated = reminder.copy(timeString = newTimeString, isCompleted = false)
            dao.updateReminder(updated)
            val response = "Snoozed '${reminder.title}' for $minutes minutes until $newTimeString ⏰"
            _latestResponse.value = response
            speakResponse(response)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            dao.deleteReminderById(id)
            com.example.notification.AriaNotificationScheduler.cancelReminderNotification(
                context = getApplication(),
                reminderId = id
            )
        }
    }

    fun addActivityLog(icon: String, title: String, detail: String = "") {
        val newItem = ActivityLogItem(icon = icon, title = title, detail = detail, timeAgo = "Just now")
        _activityLogs.value = listOf(newItem) + _activityLogs.value.take(20)
    }

    fun addMemoryFact(key: String, value: String, category: String = "Custom") {
        if (key.isBlank() || value.isBlank()) return
        val newFact = MemoryFactItem(key = key.trim(), value = value.trim(), category = category)
        _memoryFacts.value = listOf(newFact) + _memoryFacts.value
        addActivityLog("🧠", "Memory saved", "$key: $value")
    }

    fun removeMemoryFact(id: String) {
        _memoryFacts.value = _memoryFacts.value.filter { it.id != id }
        addActivityLog("🗑️", "Memory removed", "Fact deleted")
    }

    fun clearVoiceHistory() {
        viewModelScope.launch {
            dao.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.destroy()
    }
}
