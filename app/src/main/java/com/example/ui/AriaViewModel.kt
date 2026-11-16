package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.assistant.AriaCommandProcessor
import com.example.assistant.AriaCommandResult
import com.example.assistant.DeviceClockHelper
import com.example.data.AriaDatabase
import com.example.data.ReminderEntity
import com.example.data.VoiceHistoryEntity
import com.example.speech.AriaSpeechEngine
import com.example.speech.WakeWordState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val geminiLiveManager = com.example.geminilive.GeminiLiveVoiceManager(application)

    private val _isGeminiLiveMode = MutableStateFlow(
        prefs.getBoolean("arc_gemini_live_mode", true)
    )
    val isGeminiLiveMode: StateFlow<Boolean> = _isGeminiLiveMode.asStateFlow()

    fun toggleGeminiLiveMode(): Boolean {
        val newState = !_isGeminiLiveMode.value
        _isGeminiLiveMode.value = newState
        prefs.edit().putBoolean("arc_gemini_live_mode", newState).apply()
        if (newState) {
            geminiLiveManager.startLiveSession()
        } else {
            geminiLiveManager.stopLiveSession()
        }
        return newState
    }

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
    val wakeWordState: StateFlow<WakeWordState> = speechEngine.wakeWordState
    val partialTranscript: StateFlow<String> = speechEngine.partialTranscript
    val lastWakePhrase: StateFlow<String?> = speechEngine.lastWakePhrase

    private val _needsMicPermission = MutableStateFlow(!speechEngine.hasAudioPermission())
    val needsMicPermission: StateFlow<Boolean> = _needsMicPermission.asStateFlow()

    private val _wakeFlashActive = MutableStateFlow(false)
    val wakeFlashActive: StateFlow<Boolean> = _wakeFlashActive.asStateFlow()
    private var wakeFlashJob: Job? = null

    val micVolumeLevel: StateFlow<Float> = combine(
        speechEngine.micVolumeLevel,
        geminiLiveManager.micVolumeLevel
    ) { speechVol, liveVol ->
        maxOf(speechVol, liveVol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val activeVoiceDebugInfo: StateFlow<String> = combine(
        geminiLiveManager.activeVoiceDebugInfo,
        speechEngine.activeVoiceDebugInfo,
        geminiLiveManager.sessionState
    ) { liveInfo, ttsInfo, liveState ->
        if (liveState == com.example.geminilive.GeminiLiveSessionState.CONNECTED ||
            liveState == com.example.geminilive.GeminiLiveSessionState.SPEAKING ||
            liveState == com.example.geminilive.GeminiLiveSessionState.LISTENING
        ) {
            liveInfo
        } else {
            "$liveInfo | $ttsInfo"
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "Voice Init..."
    )

    fun setElevenLabsApiKey(key: String) {
        speechEngine.setApiKey(key)
    }

    fun toggleWakeWordMode(): Boolean {
        val newState = speechEngine.toggleWakeWordMode()
        prefs.edit().putBoolean("wake_word_enabled", newState).apply()
        _needsMicPermission.value = !speechEngine.hasAudioPermission()
        if (newState) {
            if (!speechEngine.hasAudioPermission()) {
                _latestResponse.value = "Microphone permission needed for 'Hey ARC' / 'JARVIS' wake-word detection."
            } else {
                _latestResponse.value = "Wake-Word Listener Active 🎙️ Say 'Hey ARC' or 'JARVIS' anytime hands-free!"
                addActivityLog("🎙️", "Wake-Word Armed", "Listening for 'Hey ARC' & 'JARVIS'")
            }
        } else {
            _wakeFlashActive.value = false
            _latestResponse.value = "Wake-Word Detection Paused."
            addActivityLog("⏸️", "Wake-Word Paused", "Hands-free mic standby turned off")
        }
        return newState
    }

    fun onMicPermissionUpdated() {
        val granted = speechEngine.hasAudioPermission()
        _needsMicPermission.value = !granted
        if (granted) {
            addActivityLog("🎙️", "Microphone Active", "JARVIS / Hey ARC wake-word ready")
            if (_isArcPoweredOn.value && prefs.getBoolean("wake_word_enabled", true)) {
                speechEngine.setExternalMicBusy(geminiLiveManager.isMicCapturingAudio())
                speechEngine.setWakeWordModeEnabled(true)
            } else {
                speechEngine.refreshWakeWordAvailability()
            }
        }
    }

    private fun triggerWakeFlash() {
        wakeFlashJob?.cancel()
        _wakeFlashActive.value = true
        wakeFlashJob = viewModelScope.launch {
            delay(1700L)
            _wakeFlashActive.value = false
        }
    }

    fun handleWakeWordTrigger(
        parseResult: AriaSpeechEngine.Companion.WakeWordParseResult,
        rawTranscript: String
    ) {
        if (!parseResult.hasWakeWord) return
        val phraseLabel = parseResult.matchedWakeWord.ifBlank { "ARC" }.uppercase()
        if (!_isArcPoweredOn.value) {
            _isArcPoweredOn.value = true
        }
        triggerWakeFlash()
        addActivityLog("⚡", "Wake Word: $phraseLabel", "Triggered via voice: \"$rawTranscript\"")

        try {
            com.example.assistant.overlay.AriaEdgeGlowOverlayManager.showEdgeGlow(
                getApplication(),
                com.example.assistant.overlay.AriaEdgeGlowView.GlowState.LISTENING
            )
        } catch (_: Exception) {
        }

        if (!parseResult.isOnlyWakeWord && parseResult.command.isNotBlank()) {
            submitTextCommand(parseResult.command)
        } else {
            _latestResponse.value = "At your service, Boss. Listening..."
            startListening()
        }
    }

    val reminders: StateFlow<List<ReminderEntity>> = dao.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceHistory: StateFlow<List<VoiceHistoryEntity>> = dao.getVoiceHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _assistantStatus = MutableStateFlow(AssistantStatus.IDLE)
    val assistantStatus: StateFlow<AssistantStatus> = _assistantStatus.asStateFlow()

    /**
     * Unified ARC Power ON/OFF state shared across:
     * 1. Central circular Power-Button toggle
     * 2. Top-right ONLINE / STANDBY badge
     * 3. "Good Morning ARC" & "Good Night ARC" voice routines
     */
    private val _isArcPoweredOn = MutableStateFlow(true)
    val isArcPoweredOn: StateFlow<Boolean> = _isArcPoweredOn.asStateFlow()

    private val _weatherTemp = MutableStateFlow("26°C")
    val weatherTemp: StateFlow<String> = _weatherTemp.asStateFlow()

    private val _weatherCondition = MutableStateFlow("Clear Sky")
    val weatherCondition: StateFlow<String> = _weatherCondition.asStateFlow()

    private val _currentCity = MutableStateFlow("Delhi")
    val currentCity: StateFlow<String> = _currentCity.asStateFlow()

    private val _needsLocationPermission = MutableStateFlow(
        !com.example.assistant.ArcLocationManager.isLocationPermissionGranted(application)
    )
    val needsLocationPermission: StateFlow<Boolean> = _needsLocationPermission.asStateFlow()

    private var hasAutoGreeted = false

    private val _latestResponse = MutableStateFlow("Hello Boss! Mai ARC hu, aapka AI Voice Assistant.")
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
            ActivityLogItem(icon = "💬", title = "Response generated", detail = "ARC core ready", timeAgo = "Just now"),
            ActivityLogItem(icon = "🎙️", title = "User spoke", detail = "Standing by for prompt", timeAgo = "1m ago"),
            ActivityLogItem(icon = "⚙️", title = "Planning response", detail = "Gemini LLM pipeline ready", timeAgo = "2m ago"),
            ActivityLogItem(icon = "🟢", title = "ARC online", detail = "Realtime link established", timeAgo = "3m ago"),
            ActivityLogItem(icon = "🟢", title = "ARC core online", detail = "All systems nominal", timeAgo = "4m ago")
        )
    )
    val activityLogs: StateFlow<List<ActivityLogItem>> = _activityLogs.asStateFlow()

    private val _memoryFacts = MutableStateFlow<List<MemoryFactItem>>(
        listOf(
            MemoryFactItem(key = "Operator Name", value = prefs.getString("user_name", "Boss") ?: "Boss", category = "Identity"),
            MemoryFactItem(key = "Wake-Word State", value = "Hey ARC (Hands-free active)", category = "Settings"),
            MemoryFactItem(key = "Primary Voice", value = "Gemini Live Male (Charon) + Male TTS", category = "Speech"),
            MemoryFactItem(key = "Default Navigation", value = "Google Maps", category = "Apps"),
            MemoryFactItem(key = "System Preference", value = "Hinglish + English Bilingual", category = "Language")
        )
    )
    val memoryFacts: StateFlow<List<MemoryFactItem>> = _memoryFacts.asStateFlow()

    private var timeChangeReceiver: BroadcastReceiver? = null

    init {
        // Register timezone/time listener from ViewModel startup (even before VoiceHudScreen mounts)
        // so early auto-greeting and Gemini Live never read a stale cached JVM TimeZone.
        val appCtx = getApplication<Application>()
        val tzReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    DeviceClockHelper.onTimezoneChanged(intent.getStringExtra("time-zone"))
                    DeviceClockHelper.getCurrentDeviceClockInfo(
                        context = appCtx,
                        logDebug = true,
                        source = "ViewModelTimezoneChanged"
                    )
                }
            }
        }
        try {
            ContextCompat.registerReceiver(
                appCtx,
                tzReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            timeChangeReceiver = tzReceiver
        } catch (_: Exception) {
        }

        android.util.Log.d(
            AriaSpeechEngine.VOICE_DEBUG_TAG,
            "Startup Voice Audit | GeminiLiveDefault=${_isGeminiLiveMode.value} | GeminiKeyValid=${geminiLiveManager.hasValidApiKey()} | GeminiVoice=${com.example.geminilive.GeminiLiveVoiceManager.SELECTED_VOICE_NAME} | ElevenLabsVoice=${speechEngine.getActiveVoiceId()}"
        )
        geminiLiveManager.setOnFallbackSpeechListener { pendingText ->
            android.util.Log.w(
                AriaSpeechEngine.VOICE_DEBUG_TAG,
                "GeminiLive fallback listener triggered -> delegating to AriaSpeechEngine male TTS"
            )
            addActivityLog("🔊", "Voice Fallback Active", geminiLiveManager.activeVoiceDebugInfo.value)
            if (!speechEngine.isMuted.value) {
                speechEngine.speak(pendingText)
            }
        }
        speechEngine.setOnWakeWordDetectedListener { parseResult, rawTranscript ->
            handleWakeWordTrigger(parseResult, rawTranscript)
        }
        checkOnboardingStatus()
        // Sync local TTS speaking -> IDLE transition
        viewModelScope.launch {
            speechEngine.isSpeaking.collect { speaking ->
                if (!speaking && _isArcPoweredOn.value && _assistantStatus.value == AssistantStatus.SPEAKING &&
                    geminiLiveManager.sessionState.value != com.example.geminilive.GeminiLiveSessionState.SPEAKING
                ) {
                    _assistantStatus.value = AssistantStatus.IDLE
                }
            }
        }
        // Sync active SpeechRecognizer command listening -> HUD LISTENING status
        viewModelScope.launch {
            speechEngine.isListening.collect { listening ->
                if (_isArcPoweredOn.value) {
                    if (listening) {
                        _assistantStatus.value = AssistantStatus.LISTENING
                    } else if (_assistantStatus.value == AssistantStatus.LISTENING &&
                        geminiLiveManager.sessionState.value != com.example.geminilive.GeminiLiveSessionState.LISTENING
                    ) {
                        _assistantStatus.value = AssistantStatus.IDLE
                    }
                }
            }
        }
        // Flash HUD immediately on partial or final wake-word detection
        viewModelScope.launch {
            speechEngine.wakeWordState.collect { state ->
                if (state == WakeWordState.TRIGGERED && _isArcPoweredOn.value) {
                    triggerWakeFlash()
                } else if (state == WakeWordState.PERMISSION_REQUIRED) {
                    _needsMicPermission.value = true
                }
            }
        }
        // Sync Gemini Live speaking/listening state with HUD status & coordinate mic hardware
        viewModelScope.launch {
            geminiLiveManager.sessionState.collect { liveState ->
                speechEngine.setExternalMicBusy(geminiLiveManager.isMicCapturingAudio())
                if (_isArcPoweredOn.value) {
                    when (liveState) {
                        com.example.geminilive.GeminiLiveSessionState.SPEAKING -> {
                            _assistantStatus.value = AssistantStatus.SPEAKING
                        }
                        com.example.geminilive.GeminiLiveSessionState.LISTENING,
                        com.example.geminilive.GeminiLiveSessionState.DISCONNECTED,
                        com.example.geminilive.GeminiLiveSessionState.ERROR -> {
                            if (_assistantStatus.value == AssistantStatus.SPEAKING && !speechEngine.isSpeaking.value) {
                                _assistantStatus.value = AssistantStatus.IDLE
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
        // Highlight JARVIS / Hey ARC wake word if heard inside Gemini Live input transcription
        viewModelScope.launch {
            geminiLiveManager.userInputTranscript.collect { transcript ->
                if (_isArcPoweredOn.value && isWakeWordModeEnabled.value && transcript.isNotBlank()) {
                    val parsed = AriaSpeechEngine.parseWakeWord(transcript)
                    if (parsed.hasWakeWord) {
                        triggerWakeFlash()
                    }
                }
            }
        }
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
                _latestResponse.value = "Hello $savedName! Mai ARC hu, aapka AI Voice Assistant."
            } else {
                _latestResponse.value = "Hello Boss! Mai ARC hu, aapka AI Voice Assistant."
            }

            _isInitializing.value = false

            if (isComplete && hasCompletedPermissions) {
                if (_isGeminiLiveMode.value && geminiLiveManager.hasValidApiKey()) {
                    geminiLiveManager.startLiveSession()
                }
                if (prefs.getBoolean("wake_word_enabled", true)) {
                    speechEngine.setExternalMicBusy(geminiLiveManager.isMicCapturingAudio())
                    speechEngine.setWakeWordModeEnabled(true)
                }
                triggerBriefingSummary()
            } else {
                refreshWeatherBadge()
            }
        }
    }

    fun refreshWeatherBadge(forceLocationRefresh: Boolean = false) {
        viewModelScope.launch {
            val snap = commandProcessor.fetchWeatherSnapshot(city = null, forceLocationRefresh = forceLocationRefresh)
            _weatherTemp.value = snap.tempDisplay
            _weatherCondition.value = snap.condition
            _currentCity.value = snap.city
            _needsLocationPermission.value = !com.example.assistant.ArcLocationManager.isLocationPermissionGranted(getApplication())
        }
    }

    fun onLocationPermissionUpdated() {
        val granted = com.example.assistant.ArcLocationManager.refreshPermissionState(getApplication())
        _needsLocationPermission.value = !granted
        if (granted) {
            addActivityLog("📍", "GPS Location Active", "Local weather & ride pickup enabled")
            refreshWeatherBadge(forceLocationRefresh = true)
        }
    }

    fun requestLocalWeatherUpdate() {
        submitTextCommand("Weather update")
    }

    fun triggerBriefingSummary(listenAfter: Boolean = false) {
        viewModelScope.launch {
            _assistantStatus.value = AssistantStatus.PROCESSING
            val snap = commandProcessor.fetchWeatherSnapshot(city = null)
            _weatherTemp.value = snap.tempDisplay
            _weatherCondition.value = snap.condition
            _currentCity.value = snap.city

            val briefingText = commandProcessor.generateBriefingSummary(_userName.value)
            hasAutoGreeted = true
            _latestResponse.value = briefingText
            addActivityLog("☀️", "Auto Greeting & Briefing", "${snap.city} ${snap.tempDisplay}")
            speakResponse(briefingText)

            if (listenAfter && !speechEngine.isMuted.value) {
                kotlinx.coroutines.delay(500)
                val startWait = System.currentTimeMillis()
                while (speechEngine.isSpeaking.value && System.currentTimeMillis() - startWait < 12000L) {
                    kotlinx.coroutines.delay(250)
                }
                if (_assistantStatus.value != AssistantStatus.SLEEP) {
                    startListening()
                }
            }
        }
    }

    fun activateArcWithGreeting() {
        if (!_isArcPoweredOn.value || _assistantStatus.value == AssistantStatus.SLEEP) {
            powerOnArc()
            return
        }
        when (_assistantStatus.value) {
            AssistantStatus.LISTENING -> {
                speechEngine.stopListening()
                _assistantStatus.value = AssistantStatus.IDLE
            }
            AssistantStatus.SPEAKING -> {
                stopSpeaking()
            }
            else -> {
                if (!hasAutoGreeted) {
                    triggerBriefingSummary(listenAfter = true)
                } else {
                    startListening()
                }
            }
        }
    }

    /**
     * Unified Power Toggle used by the central MAX-style Power-Button and Standby badge.
     */
    fun toggleArcPower() {
        if (_isArcPoweredOn.value) {
            powerOffArc()
        } else {
            powerOnArc()
        }
    }

    /**
     * Turns ARC ON: activates glowing state, starts wake-word listening & Gemini Live (if enabled),
     * and speaks greeting/briefing.
     */
    fun powerOnArc() {
        _isArcPoweredOn.value = true
        _assistantStatus.value = AssistantStatus.IDLE

        val wantsLive = prefs.getBoolean("arc_gemini_live_mode", true)
        _isGeminiLiveMode.value = wantsLive
        if (wantsLive && geminiLiveManager.hasValidApiKey()) {
            geminiLiveManager.startLiveSession()
        }

        speechEngine.setWakeWordModeEnabled(true)
        prefs.edit().putBoolean("wake_word_enabled", true).apply()

        try {
            com.example.assistant.overlay.AriaEdgeGlowOverlayManager.updateGlowState(
                com.example.assistant.overlay.AriaEdgeGlowView.GlowState.SPEAKING
            )
        } catch (_: Exception) {
        }

        addActivityLog("⚡", "ARC Powered ON", "Wake-word active & briefing started")
        triggerBriefingSummary(listenAfter = true)
    }

    /**
     * Turns ARC OFF (same effect as "Good Night ARC" routine):
     * Stops wake-word & background listening, pauses Live session, dims avatar, and speaks farewell.
     */
    fun powerOffArc(customFarewell: String? = null) {
        val displayName = _userName.value.ifBlank { "Boss" }
        val farewell = customFarewell
            ?: DeviceClockHelper.resolveStandbyFarewell(
                context = getApplication(),
                displayName = displayName,
                isExplicitGoodNightCommand = false
            )

        _isArcPoweredOn.value = false
        _assistantStatus.value = AssistantStatus.SLEEP

        speechEngine.setWakeWordModeEnabled(false)
        speechEngine.stopListening()
        speechEngine.stopSpeaking()
        prefs.edit().putBoolean("wake_word_enabled", false).apply()

        geminiLiveManager.stopLiveSession()

        try {
            com.example.assistant.background.AriaBackgroundWakeService.stop(getApplication())
        } catch (_: Exception) {
        }

        try {
            com.example.assistant.overlay.AriaEdgeGlowOverlayManager.hideEdgeGlow()
        } catch (_: Exception) {
        }

        _latestResponse.value = farewell
        addActivityLog("🌙", "ARC Powered OFF", "Standby mode & wake-word paused")

        if (!speechEngine.isMuted.value) {
            // Speak farewell without switching UI status back out of SLEEP
            speechEngine.speak(farewell)
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
        _needsMicPermission.value = !speechEngine.hasAudioPermission()
        if (_isGeminiLiveMode.value && geminiLiveManager.hasValidApiKey()) {
            geminiLiveManager.startLiveSession()
        }
        if (prefs.getBoolean("wake_word_enabled", true)) {
            speechEngine.setExternalMicBusy(geminiLiveManager.isMicCapturingAudio())
            speechEngine.setWakeWordModeEnabled(true)
        }
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
            triggerBriefingSummary(listenAfter = true)
            return
        }

        val actualQuery = if (parseResult.hasWakeWord && parseResult.command.isNotBlank()) parseResult.command else query
        _lastQuery.value = query
        if (!_isArcPoweredOn.value) {
            _isArcPoweredOn.value = true
        }
        _assistantStatus.value = AssistantStatus.PROCESSING
        addActivityLog("⚙️", "Planning response", "Processing intent: \"$actualQuery\"")

        viewModelScope.launch {
            val result = commandProcessor.processCommand(actualQuery, _userName.value)
            when (result) {
                is AriaCommandResult.TextResponse -> {
                    when (result.routineAction) {
                        "GOOD_NIGHT" -> {
                            powerOffArc(customFarewell = result.response)
                        }
                        "GOOD_MORNING" -> {
                            _isArcPoweredOn.value = true
                            speechEngine.setWakeWordModeEnabled(true)
                            prefs.edit().putBoolean("wake_word_enabled", true).apply()
                            _latestResponse.value = result.response
                            addActivityLog("☀️", "Good Morning Routine", "ARC powered ON & wake-word active")
                            speakResponse(result.response)
                        }
                        else -> {
                            _latestResponse.value = result.response
                            addActivityLog("💬", "Response generated", result.response.take(60) + if (result.response.length > 60) "..." else "")
                            if (result.openUrl != null) {
                                _pendingWebUrl.value = result.openUrl
                                _activeWebUrl.value = result.openUrl
                                addActivityLog("🌐", "Web URL opened", result.openUrl)
                            }
                            speakResponse(result.response)
                        }
                    }
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
        if (!_isArcPoweredOn.value || _assistantStatus.value == AssistantStatus.SLEEP) {
            _isArcPoweredOn.value = true
            _assistantStatus.value = AssistantStatus.IDLE
        }
        _assistantStatus.value = AssistantStatus.LISTENING
        speechEngine.startListening { recognizedText ->
            if (recognizedText.isNotBlank()) {
                submitTextCommand(recognizedText)
            } else if (_isArcPoweredOn.value) {
                _assistantStatus.value = AssistantStatus.IDLE
            }
        }
    }

    fun enterSleepMode() {
        powerOffArc()
    }

    fun wakeUp() {
        powerOnArc()
    }

    fun toggleSleepMode() {
        toggleArcPower()
    }

    fun stopSpeaking() {
        speechEngine.stopSpeaking()
        geminiLiveManager.stopCurrentPlayback()
        if (_isArcPoweredOn.value) {
            _assistantStatus.value = AssistantStatus.IDLE
        }
    }

    fun toggleTtsMute(): Boolean {
        val muted = speechEngine.toggleMute()
        if (muted) {
            geminiLiveManager.stopCurrentPlayback()
        }
        return muted
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
            if (_isArcPoweredOn.value) {
                _assistantStatus.value = AssistantStatus.IDLE
            }
            return
        }
        if (_isArcPoweredOn.value) {
            _assistantStatus.value = AssistantStatus.SPEAKING
        }
        // When Gemini Live Voice WebSocket is enabled & key is present, connect/queue and speak
        // via Gemini Live's natural male voice (Charon) instead of overlapping legacy TTS.
        val liveEnabled = _isGeminiLiveMode.value
        val hasGeminiKey = geminiLiveManager.hasValidApiKey()
        android.util.Log.d(
            AriaSpeechEngine.VOICE_DEBUG_TAG,
            "speakResponse routing | liveEnabled=$liveEnabled | hasGeminiKey=$hasGeminiKey | liveState=${geminiLiveManager.sessionState.value}"
        )
        if (liveEnabled && hasGeminiKey) {
            if (geminiLiveManager.sessionState.value == com.example.geminilive.GeminiLiveSessionState.DISCONNECTED) {
                geminiLiveManager.startLiveSession()
            }
            if (geminiLiveManager.sendTextTurnForSpeech(text)) {
                speechEngine.stopSpeaking()
                return
            }
        }
        speechEngine.speak(text)
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
        timeChangeReceiver?.let { receiver ->
            try {
                getApplication<Application>().unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
        timeChangeReceiver = null
        speechEngine.destroy()
    }
}
