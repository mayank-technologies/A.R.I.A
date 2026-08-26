package com.example.assistant.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.assistant.AriaCommandProcessor
import com.example.assistant.AriaCommandResult
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.assistant.glyph.AriaGlyphHardwareManager
import com.example.assistant.overlay.AriaEdgeGlowOverlayManager
import com.example.assistant.overlay.AriaEdgeGlowView
import com.example.data.AriaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Foreground Service for Background Always-Listening Wake Word & Command Execution.
 * Runs continuously even when the app is minimized / in background.
 * Triggers the Software Screen Edge Glow Overlay when listening or speaking.
 */
class AriaBackgroundWakeService : Service(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var commandProcessor: AriaCommandProcessor? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isProcessingCommand = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeWakeWord = "hey aria"
    private val wakeWordAliases = listOf("hey aria", "aria", "computer", "jarvis", "execute", "assistant")

    companion object {
        const val CHANNEL_ID = "aria_background_wake_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.example.aria.action.START_BACKGROUND_WAKE"
        const val ACTION_STOP = "com.example.aria.action.STOP_BACKGROUND_WAKE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AriaBackgroundWakeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AriaBackgroundWakeService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        textToSpeech = TextToSpeech(this, this)
        val dao = AriaDatabase.getDatabase(this).ariaDao()
        commandProcessor = AriaCommandProcessor(this, dao)
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundNotification()
                _isRunning.value = true
                startListeningLoop()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ARIA Background Wake Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors for wake word 'Hey ARIA' and runs voice commands in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AriaBackgroundWakeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A.R.I.A. Background Voice Engine Active")
            .setContentText("Listening for 'Hey ARIA' • Edge Glow Visual Active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Engine", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14+ specific foreground service type for microphone
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } catch (e: Throwable) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
        }
    }

    private fun startListeningLoop() {
        if (isListening || isProcessingCommand) return

        mainHandler.post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                isListening = false
                restartListeningAfterDelay(1500)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                // Silence timeout error (error 7 or 6) is normal in continuous background loop
                restartListeningAfterDelay(1000)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].lowercase().trim()
                    handleRecognizedText(text)
                } else {
                    restartListeningAfterDelay(600)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].lowercase().trim()
                    // Early wake word check in partial speech
                    if (wakeWordAliases.any { text.contains(it) } && !isProcessingCommand) {
                        AriaEdgeGlowOverlayManager.showEdgeGlow(applicationContext, AriaEdgeGlowView.GlowState.LISTENING)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleRecognizedText(text: String) {
        val matchedAlias = wakeWordAliases.firstOrNull { text.contains(it) }

        if (matchedAlias != null) {
            // WAKE WORD DETECTED!
            isProcessingCommand = true

            // 1. Show Screen Edge Glow in LISTENING mode
            AriaEdgeGlowOverlayManager.showEdgeGlow(applicationContext, AriaEdgeGlowView.GlowState.LISTENING)
            AriaGlyphHardwareManager.triggerGlyphPattern(applicationContext, "BREATHE")

            // 2. Extract command after wake word
            val command = if (text.startsWith(matchedAlias)) {
                text.substringAfter(matchedAlias).trim()
            } else {
                text.substringAfter(matchedAlias).trim()
            }

            if (command.isNotBlank()) {
                executeVoiceCommand(command)
            } else {
                // User just said "Hey ARIA", respond with polite JARVIS greeting
                speakResponse("At your service, Boss. How may I assist you?")
            }
        } else {
            // Normal ambient noise, continue listening
            restartListeningAfterDelay(500)
        }
    }

    private fun executeVoiceCommand(command: String) {
        AriaEdgeGlowOverlayManager.updateGlowState(AriaEdgeGlowView.GlowState.PROCESSING)

        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    commandProcessor?.processCommand(command)
                }
                val responseText = when (result) {
                    is AriaCommandResult.TextResponse -> result.response
                    is AriaCommandResult.LaunchApp -> result.response
                    is AriaCommandResult.CloseBrowser -> result.response
                    null -> "Understood, Boss."
                }
                mainHandler.post {
                    AriaEdgeGlowOverlayManager.updateGlowState(AriaEdgeGlowView.GlowState.SPEAKING)
                    speakResponse(responseText)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    speakResponse("Action processed, Boss.")
                }
            }
        }
    }

    private fun speakResponse(text: String) {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                AriaEdgeGlowOverlayManager.updateGlowState(AriaEdgeGlowView.GlowState.SPEAKING)
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    // Turn off Edge Glow & Glyph when speech is finished!
                    AriaEdgeGlowOverlayManager.hideEdgeGlow()
                    AriaGlyphHardwareManager.turnOffGlyph()
                    isProcessingCommand = false
                    restartListeningAfterDelay(1000)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    AriaEdgeGlowOverlayManager.hideEdgeGlow()
                    AriaGlyphHardwareManager.turnOffGlyph()
                    isProcessingCommand = false
                    restartListeningAfterDelay(1000)
                }
            }
        })

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aria_bg_speech_${System.currentTimeMillis()}")
    }

    private fun restartListeningAfterDelay(delayMs: Long) {
        if (!_isRunning.value || isProcessingCommand) return
        mainHandler.removeCallbacksAndMessages(null)
        val effectiveDelay = AriaBatterySaverManager.getBackgroundWakeDelay(delayMs)
        mainHandler.postDelayed({
            startListeningLoop()
        }, effectiveDelay)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
            textToSpeech?.setPitch(1.0f)
            textToSpeech?.setSpeechRate(1.0f)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        _isRunning.value = false
        isListening = false
        isProcessingCommand = false

        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        AriaEdgeGlowOverlayManager.hideEdgeGlow()
        AriaGlyphHardwareManager.turnOffGlyph()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
