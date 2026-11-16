package com.example.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class WakeWordState {
    OFF,
    STANDBY_LISTENING,
    TRIGGERED,
    ACTIVE_COMMAND,
    PERMISSION_REQUIRED,
    UNAVAILABLE
}

/**
 * Advanced ARIA Speech Engine featuring:
 * 1. ElevenLabs Neural Human Voice Synthesis (Adam / Warm Deep Male Voice)
 * 2. Voice-scoped MD5 Disk Audio Caching for fast playback on repeated sentences
 * 3. Android MediaPlayer for smooth high-fidelity audio output
 * 4. Automatic Seamless Fallback to Native Android TextToSpeech (Male) when offline or on API failure
 * 5. Latency/Synthesizing state indication for UI
 * 6. JARVIS-grade continuous Wake-Word Listener ('Hey ARC', 'ARC', 'JARVIS') with RMS & partial feedback
 */
class AriaSpeechEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())

    // Native Android TTS Fallback
    private var nativeTts: TextToSpeech? = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null

    private var isNativeTtsInitialized = false
    private var pendingTextToSpeak: String? = null

    // ElevenLabs Configuration (Male Voices)
    // Adam:   "pNInz6obpgDQGcFmaJgB" (Deep, Natural, Warm Male Voice)
    // Antoni: "ErXwobaYiN019PkySvjV" (Warm Well-Rounded Male Voice)
    // Josh:   "TxGEqnHWrfWFTfGW9XjX" (Deep Narrative Male Voice)
    private fun isPlaceholderValue(value: String): Boolean {
        val v = value.trim()
        return v.isBlank() ||
            v.equals("your_key_here", ignoreCase = true) ||
            v.equals("your_voice_id_here", ignoreCase = true) ||
            v.equals("MY_GEMINI_API_KEY", ignoreCase = true) ||
            v.startsWith("YOUR_", ignoreCase = true)
    }

    private var voiceId: String = run {
        val prefs = context.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
        val savedVoiceId = prefs.getString("elevenlabs_voice_id", "") ?: ""
        val buildConfigVoiceId = BuildConfig.ELEVENLABS_VOICE_ID.trim()
        val legacyFemaleIds = setOf("21m00Tcm4TlvDq8ikWAM", "EXAVITQu4vr4xnSDxMaL")
        when {
            !isPlaceholderValue(buildConfigVoiceId) && buildConfigVoiceId !in legacyFemaleIds -> buildConfigVoiceId
            !isPlaceholderValue(savedVoiceId) && savedVoiceId !in legacyFemaleIds -> savedVoiceId.trim()
            else -> {
                prefs.edit().putString("elevenlabs_voice_id", DEFAULT_MALE_VOICE_ID).apply()
                DEFAULT_MALE_VOICE_ID
            }
        }
    }

    private var apiKey: String = BuildConfig.ELEVENLABS_API_KEY.trim().let { raw ->
        if (isPlaceholderValue(raw)) "" else raw
    }

    fun getActiveVoiceId(): String = voiceId

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private val audioCacheDir = File(context.cacheDir, "aria_audio_cache").apply { if (!exists()) mkdirs() }

    // State Flows
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _micVolumeLevel = MutableStateFlow(0f)
    val micVolumeLevel: StateFlow<Float> = _micVolumeLevel.asStateFlow()

    private val _isWakeWordModeEnabled = MutableStateFlow(false)
    val isWakeWordModeEnabled: StateFlow<Boolean> = _isWakeWordModeEnabled.asStateFlow()

    private val _wakeWordState = MutableStateFlow(WakeWordState.OFF)
    val wakeWordState: StateFlow<WakeWordState> = _wakeWordState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _lastWakePhrase = MutableStateFlow<String?>(null)
    val lastWakePhrase: StateFlow<String?> = _lastWakePhrase.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _activeVoiceDebugInfo = MutableStateFlow("SpeechEngine: Initializing (ElevenLabs=$voiceId)")
    val activeVoiceDebugInfo: StateFlow<String> = _activeVoiceDebugInfo.asStateFlow()

    private var onSpeechResultListener: ((String) -> Unit)? = null
    private var onWakeWordDetectedListener: ((WakeWordParseResult, String) -> Unit)? = null
    private var wakeWordJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isManualCommandListening = false

    @Volatile
    private var isExternalMicBusy = false

    @Volatile
    private var partialWakeMatchedInSession = false

    @Volatile
    private var lastMaleTtsSelection: MaleTtsSelection? = null

    init {
        initSpeechRecognizer()
        val elevenKeyPresent = getApiKey().isNotBlank()
        Log.d(
            VOICE_DEBUG_TAG,
            "AriaSpeechEngine init | BuildConfig.ELEVENLABS_VOICE_ID='${BuildConfig.ELEVENLABS_VOICE_ID}' -> resolvedVoiceId='$voiceId' (Adam Male) | ElevenLabsKeyConfigured=$elevenKeyPresent"
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isNativeTtsInitialized = true
            val selection = applyStrictMaleTtsVoice(
                tts = nativeTts,
                speechRate = _speechRate.value,
                tag = VOICE_DEBUG_TAG
            )
            lastMaleTtsSelection = selection
            _activeVoiceDebugInfo.value = "Android TTS Ready: voice=${selection.voiceName}, verifiedMale=${selection.isVerifiedMale}, pitch=${selection.appliedPitch}"

            nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (_isWakeWordModeEnabled.value) {
                        scheduleWakeWordLoop(1000L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (_isWakeWordModeEnabled.value) {
                        scheduleWakeWordLoop(1000L)
                    }
                }
            })

            pendingTextToSpeak?.let { text ->
                pendingTextToSpeak = null
                speak(text)
            }
        } else {
            Log.e(VOICE_DEBUG_TAG, "Native TextToSpeech initialization failed: $status")
            _activeVoiceDebugInfo.value = "Android TTS init failed ($status)"
        }
    }

    /**
     * Main Speak function.
     * Attempts ElevenLabs Neural TTS with local disk cache first.
     * Fallbacks to native Android TTS if offline or if ElevenLabs fails.
     */
    fun speak(text: String) {
        if (text.isBlank() || _isMuted.value) return

        stopSpeaking()

        engineScope.launch {
            val key = getApiKey()
            val fallbackReason: String
            if (key.isNotBlank()) {
                val cachedFile = getCachedAudioFile(text)
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    val info = "Engine=ELEVENLABS_CACHE | VoiceID=$voiceId (Male)"
                    _activeVoiceDebugInfo.value = info
                    Log.d(VOICE_DEBUG_TAG, "$info | Query='${text.take(40)}'")
                    playAudioFile(cachedFile)
                    return@launch
                }

                // ElevenLabs API Call
                _isSynthesizing.value = true
                val generatedFile = fetchElevenLabsAudio(text, key)
                _isSynthesizing.value = false

                if (generatedFile != null && generatedFile.exists()) {
                    val info = "Engine=ELEVENLABS_API | VoiceID=$voiceId (Male) | Model=eleven_multilingual_v2"
                    _activeVoiceDebugInfo.value = info
                    Log.d(VOICE_DEBUG_TAG, "$info | Query='${text.take(40)}'")
                    playAudioFile(generatedFile)
                    return@launch
                }
                fallbackReason = "ElevenLabs API request failed"
            } else {
                fallbackReason = "ELEVENLABS_API_KEY missing/placeholder"
            }

            // Fallback to Native Android TextToSpeech (strict male selector)
            withContext(Dispatchers.Main) {
                Log.w(VOICE_DEBUG_TAG, "Fallback -> Android Native TTS ($fallbackReason) | Query='${text.take(40)}'")
                speakNative(text, fallbackReason)
            }
        }
    }

    private fun getApiKey(): String {
        if (!isPlaceholderValue(apiKey)) return apiKey
        val prefs = context.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("elevenlabs_api_key", "") ?: ""
        return if (isPlaceholderValue(saved)) "" else saved.trim()
    }

    fun setApiKey(key: String) {
        this.apiKey = key.trim()
        val prefs = context.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("elevenlabs_api_key", this.apiKey).apply()
    }

    fun setVoiceId(customVoiceId: String) {
        if (customVoiceId.isNotBlank()) {
            this.voiceId = customVoiceId.trim()
            val prefs = context.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("elevenlabs_voice_id", this.voiceId).apply()
            Log.d("AriaSpeechEngine", "Custom Voice ID updated to: ${this.voiceId}")
        }
    }

    /**
     * OPTION 1: Pre-recorded audio playback from assets folder (e.g., "audio/greeting.wav").
     * Ideal for fixed phrases like greetings, error messages, and canned responses.
     */
    fun playPreRecordedAudioFromAsset(assetPath: String) {
        stopSpeaking()
        engineScope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                val descriptor = context.assets.openFd(assetPath)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    descriptor.close()
                    setOnPreparedListener {
                        _isSpeaking.value = true
                        start()
                    }
                    setOnCompletionListener {
                        _isSpeaking.value = false
                        release()
                        mediaPlayer = null
                    }
                    setOnErrorListener { _, _, _ ->
                        _isSpeaking.value = false
                        release()
                        mediaPlayer = null
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("AriaSpeechEngine", "Error playing pre-recorded asset audio '$assetPath': ${e.message}")
                _isSpeaking.value = false
            }
        }
    }

    /**
     * OPTION 2: Free Open-Source Voice Cloning Server (Coqui TTS / XTTS v2 / Piper).
     * Connects to a self-hosted Python FastAPI server (e.g. running on Google Colab or local PC).
     */
    fun speakWithCustomServer(text: String, serverUrl: String) {
        if (text.isBlank() || _isMuted.value) return
        stopSpeaking()

        engineScope.launch {
            _isSynthesizing.value = true
            try {
                val jsonBody = JSONObject().apply {
                    put("text", text)
                    put("language", "en") // or "hi" for Hindi
                }

                val request = Request.Builder()
                    .url(serverUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                _isSynthesizing.value = false

                if (response.isSuccessful && response.body != null) {
                    val tempFile = File(context.cacheDir, "coqui_tts_temp_${System.currentTimeMillis()}.wav")
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(response.body!!.bytes())
                    }
                    playAudioFile(tempFile)
                } else {
                    Log.e("AriaSpeechEngine", "Coqui TTS Server error: ${response.code}")
                    withContext(Dispatchers.Main) { speakNative(text) }
                }
            } catch (e: Exception) {
                _isSynthesizing.value = false
                Log.e("AriaSpeechEngine", "Failed to connect to Coqui TTS server: ${e.message}")
                withContext(Dispatchers.Main) { speakNative(text) }
            }
        }
    }

    /**
     * Calls ElevenLabs Text-to-Speech API (v1/text-to-speech) using BuildConfig.ELEVENLABS_VOICE_ID (Adam male voice).
     * Uses eleven_multilingual_v2 model for realistic natural speech in Hindi/English.
     */
    private suspend fun fetchElevenLabsAudio(text: String, key: String): File? = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2") // Great for English + Hindi/Hinglish
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                })
            }

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
                .addHeader("xi-api-key", key)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/mpeg")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val outputFile = getCachedAudioFile(text)
                val bytes = response.body!!.bytes()
                FileOutputStream(outputFile).use { fos ->
                    fos.write(bytes)
                }
                Log.d("AriaSpeechEngine", "ElevenLabs audio fetched & cached successfully (${bytes.size} bytes)")
                return@withContext outputFile
            } else {
                Log.e("AriaSpeechEngine", "ElevenLabs API error: Code ${response.code} - ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("AriaSpeechEngine", "Exception during ElevenLabs API call: ${e.message}")
        }
        return@withContext null
    }

    private fun playAudioFile(file: File) {
        engineScope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    setOnPreparedListener {
                        _isSpeaking.value = true
                        start()
                    }
                    setOnCompletionListener {
                        _isSpeaking.value = false
                        release()
                        mediaPlayer = null
                        if (_isWakeWordModeEnabled.value) {
                            scheduleWakeWordLoop(1000L)
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        _isSpeaking.value = false
                        release()
                        mediaPlayer = null
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                _isSpeaking.value = false
                Log.e("AriaSpeechEngine", "Error playing MP3 file: ${e.message}")
            }
        }
    }

    private fun speakNative(text: String, reason: String = "Direct Native TTS") {
        if (!isNativeTtsInitialized) {
            pendingTextToSpeak = text
            return
        }
        nativeTts?.stop()
        // Re-enforce strict male voice & baritone pitch right before speaking in case Android TTS reset it
        val selection = applyStrictMaleTtsVoice(
            tts = nativeTts,
            speechRate = _speechRate.value,
            tag = VOICE_DEBUG_TAG
        )
        lastMaleTtsSelection = selection
        val info = "Engine=ANDROID_NATIVE_TTS | Voice=${selection.voiceName} | VerifiedMale=${selection.isVerifiedMale} | Pitch=${selection.appliedPitch} | Reason=$reason"
        _activeVoiceDebugInfo.value = info
        Log.d(VOICE_DEBUG_TAG, info)

        val utteranceId = "ARIA_UTTERANCE_${System.currentTimeMillis()}"
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        _isSpeaking.value = true
        nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun getCachedAudioFile(text: String): File {
        val hash = md5("${voiceId}_${text.trim().lowercase(Locale.ROOT)}")
        return File(audioCacheDir, "$hash.mp3")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            stopSpeaking()
        }
    }

    fun toggleMute(): Boolean {
        val newMutedState = !_isMuted.value
        setMuted(newMutedState)
        return newMutedState
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        nativeTts?.setSpeechRate(rate)
    }

    fun stopSpeaking() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignore
        }
        nativeTts?.stop()
        _isSpeaking.value = false
        _isSynthesizing.value = false
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setOnWakeWordDetectedListener(listener: (WakeWordParseResult, String) -> Unit) {
        onWakeWordDetectedListener = listener
    }

    /**
     * Coordinates hardware microphone ownership with Gemini Live AudioRecord streaming
     * so SpeechRecognizer does not collide when Gemini Live is actively recording.
     */
    fun setExternalMicBusy(busy: Boolean) {
        if (isExternalMicBusy == busy) return
        isExternalMicBusy = busy
        if (busy) {
            wakeWordJob?.cancel()
            if (!isManualCommandListening) {
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }
                _micVolumeLevel.value = 0f
                if (_isWakeWordModeEnabled.value) {
                    _wakeWordState.value = WakeWordState.STANDBY_LISTENING
                }
            }
        } else if (_isWakeWordModeEnabled.value && !isManualCommandListening) {
            scheduleWakeWordLoop(450L)
        }
    }

    fun setWakeWordModeEnabled(enabled: Boolean) {
        _isWakeWordModeEnabled.value = enabled
        if (enabled) {
            when {
                !hasAudioPermission() -> {
                    _wakeWordState.value = WakeWordState.PERMISSION_REQUIRED
                }
                !SpeechRecognizer.isRecognitionAvailable(context) -> {
                    _wakeWordState.value = WakeWordState.UNAVAILABLE
                }
                else -> {
                    _wakeWordState.value = WakeWordState.STANDBY_LISTENING
                    scheduleWakeWordLoop(300L)
                }
            }
        } else {
            wakeWordJob?.cancel()
            partialWakeMatchedInSession = false
            _partialTranscript.value = ""
            if (!isManualCommandListening) {
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }
                _isListening.value = false
                _micVolumeLevel.value = 0f
            }
            _wakeWordState.value = WakeWordState.OFF
        }
    }

    fun toggleWakeWordMode(): Boolean {
        val newState = !_isWakeWordModeEnabled.value
        setWakeWordModeEnabled(newState)
        return newState
    }

    fun refreshWakeWordAvailability() {
        if (_isWakeWordModeEnabled.value) {
            setWakeWordModeEnabled(true)
        }
    }

    fun scheduleWakeWordLoop(delayMs: Long = 800L) {
        if (!_isWakeWordModeEnabled.value) return
        wakeWordJob?.cancel()
        wakeWordJob = engineScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(delayMs)
            if (_isWakeWordModeEnabled.value &&
                !isManualCommandListening &&
                !isExternalMicBusy &&
                !_isSpeaking.value &&
                !_isSynthesizing.value
            ) {
                startWakeWordListening()
            }
        }
    }

    private fun startWakeWordListening() {
        if (!hasAudioPermission()) {
            _wakeWordState.value = WakeWordState.PERMISSION_REQUIRED
            _isListening.value = false
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _wakeWordState.value = WakeWordState.UNAVAILABLE
            _isListening.value = false
            return
        }
        if (isExternalMicBusy || isManualCommandListening || _isSpeaking.value || _isSynthesizing.value) {
            return
        }

        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        partialWakeMatchedInSession = false
        _partialTranscript.value = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for 'Hey ARC' or 'JARVIS'...")
        }
        try {
            _wakeWordState.value = WakeWordState.STANDBY_LISTENING
            _isListening.value = false
            _micVolumeLevel.value = 0f
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _micVolumeLevel.value = 0f
            Log.e("AriaSpeechEngine", "Error starting wake word listener: ${e.message}")
            scheduleWakeWordLoop(1800L)
        }
    }

    private fun initSpeechRecognizer() {
        val initBlock = {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            initBlock()
        } else {
            mainHandler.post { initBlock() }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isManualCommandListening) {
                    _isListening.value = true
                    if (_isWakeWordModeEnabled.value) {
                        _wakeWordState.value = WakeWordState.ACTIVE_COMMAND
                    }
                } else if (_isWakeWordModeEnabled.value) {
                    _wakeWordState.value = WakeWordState.STANDBY_LISTENING
                }
                _micVolumeLevel.value = 0f
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _micVolumeLevel.value = (_micVolumeLevel.value * 0.3f + normalized * 0.7f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (isManualCommandListening) {
                    _isListening.value = false
                }
                _micVolumeLevel.value = 0f
            }

            override fun onError(error: Int) {
                _micVolumeLevel.value = 0f
                val wasManual = isManualCommandListening
                isManualCommandListening = false
                _isListening.value = false

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    _wakeWordState.value = WakeWordState.PERMISSION_REQUIRED
                    return
                }

                if (wasManual) {
                    onSpeechResultListener?.invoke("")
                }

                // If partial speech already detected a bare wake word before timeout/no-match, trigger ARC cleanly
                val partialSnap = _partialTranscript.value
                if (!wasManual && partialWakeMatchedInSession && partialSnap.isNotBlank()) {
                    partialWakeMatchedInSession = false
                    _partialTranscript.value = ""
                    val parsed = parseWakeWord(partialSnap)
                    if (parsed.hasWakeWord) {
                        _lastWakePhrase.value = parsed.matchedWakeWord
                        _wakeWordState.value = WakeWordState.TRIGGERED
                        _lastRecognizedText.value = partialSnap
                        onWakeWordDetectedListener?.invoke(parsed, partialSnap)
                        return
                    }
                }

                partialWakeMatchedInSession = false
                _partialTranscript.value = ""

                if (_isWakeWordModeEnabled.value && !_isSpeaking.value && !isExternalMicBusy) {
                    val retryDelay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1400L
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 650L
                        else -> 1200L
                    }
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        initSpeechRecognizer()
                    }
                    _wakeWordState.value = WakeWordState.STANDBY_LISTENING
                    scheduleWakeWordLoop(retryDelay)
                } else if (!_isWakeWordModeEnabled.value) {
                    _wakeWordState.value = WakeWordState.OFF
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                processRecognizedSpeech(text = text, isPartial = false)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    processRecognizedSpeech(text = text, isPartial = true)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * Handles both partial and final microphone transcripts cleanly for manual commands and
     * continuous JARVIS/ARC wake-word detection. Also directly testable in JVM unit tests.
     */
    fun processRecognizedSpeech(text: String, isPartial: Boolean) {
        val trimmed = text.trim()

        if (isManualCommandListening) {
            if (isPartial) {
                if (trimmed.isNotBlank()) {
                    _partialTranscript.value = trimmed
                }
                return
            }

            isManualCommandListening = false
            _isListening.value = false
            _micVolumeLevel.value = 0f
            _partialTranscript.value = ""

            if (trimmed.isNotBlank()) {
                _lastRecognizedText.value = trimmed
                onSpeechResultListener?.invoke(trimmed)
            } else {
                onSpeechResultListener?.invoke("")
            }

            if (_isWakeWordModeEnabled.value && !_isSpeaking.value && !isExternalMicBusy) {
                _wakeWordState.value = WakeWordState.STANDBY_LISTENING
                scheduleWakeWordLoop(1200L)
            } else if (!_isWakeWordModeEnabled.value) {
                _wakeWordState.value = WakeWordState.OFF
            }
            return
        }

        if (!_isWakeWordModeEnabled.value) return

        if (isPartial) {
            if (trimmed.isNotBlank()) {
                _partialTranscript.value = trimmed
                val parsed = parseWakeWord(trimmed)
                if (parsed.hasWakeWord && !partialWakeMatchedInSession) {
                    partialWakeMatchedInSession = true
                    _lastWakePhrase.value = parsed.matchedWakeWord
                    _wakeWordState.value = WakeWordState.TRIGGERED
                    playWakeActivationChime()
                }
            }
            return
        }

        // Final wake-standby result
        val alreadyChimed = partialWakeMatchedInSession
        partialWakeMatchedInSession = false
        _micVolumeLevel.value = 0f
        _partialTranscript.value = ""

        if (trimmed.isNotBlank()) {
            val parsed = parseWakeWord(trimmed)
            if (parsed.hasWakeWord) {
                _lastWakePhrase.value = parsed.matchedWakeWord
                _wakeWordState.value = WakeWordState.TRIGGERED
                _lastRecognizedText.value = trimmed
                if (!alreadyChimed) {
                    playWakeActivationChime()
                }
                onWakeWordDetectedListener?.invoke(parsed, trimmed)
                return
            }
        }

        if (!_isSpeaking.value && !isExternalMicBusy) {
            _wakeWordState.value = WakeWordState.STANDBY_LISTENING
            scheduleWakeWordLoop(500L)
        }
    }

    /**
     * Marks wake state as actively listening for a follow-up command after wake word activation.
     */
    fun startListening(onResult: (String) -> Unit) {
        this.onSpeechResultListener = onResult
        if (!hasAudioPermission()) {
            _isListening.value = false
            _wakeWordState.value = WakeWordState.PERMISSION_REQUIRED
            return
        }
        wakeWordJob?.cancel()
        isManualCommandListening = true
        partialWakeMatchedInSession = false
        _partialTranscript.value = ""
        _isListening.value = true
        _micVolumeLevel.value = 0f
        if (_isWakeWordModeEnabled.value) {
            _wakeWordState.value = WakeWordState.ACTIVE_COMMAND
        }

        val startBlock = {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for ARC command...")
                }
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    isManualCommandListening = false
                    _isListening.value = false
                    _micVolumeLevel.value = 0f
                    Log.e("AriaSpeechEngine", "Error starting speech recognition: ${e.message}")
                }
            } else {
                isManualCommandListening = false
                _isListening.value = false
                _wakeWordState.value = WakeWordState.UNAVAILABLE
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            startBlock()
        } else {
            mainHandler.post { startBlock() }
        }
    }

    fun stopListening() {
        wakeWordJob?.cancel()
        isManualCommandListening = false
        partialWakeMatchedInSession = false
        _partialTranscript.value = ""
        _isListening.value = false
        _micVolumeLevel.value = 0f
        if (!_isWakeWordModeEnabled.value) {
            _wakeWordState.value = WakeWordState.OFF
        }
        val stopBlock = {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopBlock()
        } else {
            mainHandler.post { stopBlock() }
        }
    }

    private fun playWakeActivationChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
            mainHandler.postDelayed({
                try {
                    toneGen.release()
                } catch (_: Exception) {
                }
            }, 220L)
        } catch (_: Throwable) {
            // Ignore in headless/Robolectric environments without audio hardware
        }
    }

    fun destroy() {
        wakeWordJob?.cancel()
        stopSpeaking()
        _micVolumeLevel.value = 0f
        _partialTranscript.value = ""
        _wakeWordState.value = WakeWordState.OFF
        nativeTts?.shutdown()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val VOICE_DEBUG_TAG = "ARC_VOICE_DEBUG"
        const val DEFAULT_MALE_VOICE_ID = "pNInz6obpgDQGcFmaJgB" // Adam (Male)

        data class MaleTtsSelection(
            val voiceName: String,
            val isVerifiedMale: Boolean,
            val appliedPitch: Float,
            val detail: String
        )

        // Verified female voice names & Google TTS female variants that must NEVER be selected
        private val knownFemaleVoiceTokens = listOf(
            "female", "woman", "girl",
            "hi-in-x-hia", "hi-in-x-hic", "hi-in-x-hid",
            "en-in-x-ena", "en-in-x-enb", "en-in-x-enc",
            "en-us-x-sfg", "en-us-x-tpc", "en-us-x-tpf", "en-us-x-iog",
            "en-gb-x-gba", "en-gb-x-gbb", "en-gb-x-gbc", "en-gb-x-rja",
            "veena", "lekha", "aditi", "raveena", "samantha", "karen", "moira", "tessa", "zira", "hazel"
        )

        // Ordered by ARC priority: Hindi Male -> Indian English Male -> US/UK Male -> Generic Male tags
        private val prioritizedMaleVoiceTokens = listOf(
            "hi-in-x-hie",
            "en-in-x-end",
            "en-in-x-ene",
            "en-us-x-iom",
            "en-us-x-tpd",
            "en-us-x-iol",
            "en-gb-x-rjs",
            "en-gb-x-fis",
            "en-gb-x-gbd",
            "-male",
            "_male",
            "#male",
            " male",
            "rishi",
            "daniel",
            "alex",
            "david",
            "mark",
            "george",
            "arthur"
        )

        fun isKnownFemaleVoiceName(voiceName: String): Boolean {
            val lower = voiceName.lowercase(Locale.ROOT)
            return knownFemaleVoiceTokens.any { token -> lower.contains(token) }
        }

        fun isKnownMaleVoiceName(voiceName: String): Boolean {
            val lower = voiceName.lowercase(Locale.ROOT)
            if (isKnownFemaleVoiceName(lower)) return false
            return prioritizedMaleVoiceTokens.any { token -> lower.contains(token) } ||
                Regex("(^|[^a-z])male([^a-z]|$)").containsMatchIn(lower)
        }

        fun pickBestMaleVoice(voices: Collection<Voice>): Voice? {
            if (voices.isEmpty()) return null
            val candidates = voices.filter { voice ->
                isKnownMaleVoiceName(voice.name)
            }
            if (candidates.isEmpty()) return null

            return candidates.minWithOrNull(
                compareBy<Voice>(
                    // Prefer offline local voices so Android never falls back to default female on slow network
                    { if (it.isNetworkConnectionRequired) 1 else 0 },
                    // Then follow exact ARC male priority (hi-in-x-hie -> en-in-x-end/ene -> en-us-x-iom/tpd/iol)
                    { voice ->
                        val lower = voice.name.lowercase(Locale.ROOT)
                        val idx = prioritizedMaleVoiceTokens.indexOfFirst { lower.contains(it) }
                        if (idx >= 0) idx else prioritizedMaleVoiceTokens.size
                    },
                    { it.name }
                )
            )
        }

        /**
         * Enforces a verified male Android TTS voice (when present) and baritone pitch.
         * Always calls setLanguage BEFORE setVoice so setLanguage never resets the chosen male voice.
         */
        fun applyStrictMaleTtsVoice(
            tts: TextToSpeech?,
            speechRate: Float = 1.0f,
            tag: String = VOICE_DEBUG_TAG
        ): MaleTtsSelection {
            if (tts == null) {
                return MaleTtsSelection("unavailable", false, 0.72f, "TextToSpeech null")
            }

            tts.setSpeechRate(speechRate)
            return try {
                val availableVoices = tts.voices ?: emptySet()
                val maleVoice = pickBestMaleVoice(availableVoices)

                if (maleVoice != null) {
                    // Set matching language first, then bind exact male voice afterwards
                    tts.setLanguage(maleVoice.locale)
                    tts.voice = maleVoice
                    val pitch = 0.88f
                    tts.setPitch(pitch)
                    Log.d(
                        tag,
                        "Selected VERIFIED MALE Android TTS voice='${maleVoice.name}' locale=${maleVoice.locale} network=${maleVoice.isNetworkConnectionRequired} pitch=$pitch (from ${availableVoices.size} voices)"
                    )
                    MaleTtsSelection(
                        voiceName = maleVoice.name,
                        isVerifiedMale = true,
                        appliedPitch = pitch,
                        detail = "Verified male voice ${maleVoice.name}"
                    )
                } else {
                    val langRes = tts.setLanguage(Locale("hi", "IN"))
                    if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.US)
                    }
                    // No verified male voice installed: shift pitch cleanly down to deep baritone register
                    val pitch = 0.72f
                    tts.setPitch(pitch)
                    val currentVoiceName = tts.voice?.name ?: "system-default"
                    Log.w(
                        tag,
                        "No verified male Android TTS voice found among ${availableVoices.size} voices (active='$currentVoiceName'). Applying deep baritone pitch=$pitch"
                    )
                    MaleTtsSelection(
                        voiceName = currentVoiceName,
                        isVerifiedMale = false,
                        appliedPitch = pitch,
                        detail = "Deep baritone fallback pitch=$pitch on $currentVoiceName"
                    )
                }
            } catch (e: Exception) {
                tts.setPitch(0.72f)
                Log.e(tag, "Error selecting male TTS voice: ${e.message}")
                MaleTtsSelection("error-fallback", false, 0.72f, e.message ?: "error")
            }
        }

        data class WakeWordParseResult(
            val hasWakeWord: Boolean,
            val isOnlyWakeWord: Boolean,
            val command: String,
            val matchedWakeWord: String = ""
        )

        private val wakePrefixes = listOf(
            "hey jarvis", "ok jarvis", "hi jarvis", "hello jarvis", "jarvis", "j.a.r.v.i.s.",
            "hey arc", "ok arc", "hi arc", "hello arc", "namaste arc", "listen arc",
            "hey a.r.c.", "ok a.r.c.", "hi a.r.c.", "a.r.c.", "arc",
            "hey ark", "ok ark", "hi ark", "hello ark", "ark",
            "hey aria", "ok aria", "hi aria", "hello aria", "namaste aria", "listen aria",
            "hey area", "ok area", "hi area", "hello area",
            "hey a.r.i.a.", "ok a.r.i.a.", "hi a.r.i.a.",
            "aria", "area", "a.r.i.a."
        )

        fun parseWakeWord(input: String): WakeWordParseResult {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) {
                return WakeWordParseResult(hasWakeWord = false, isOnlyWakeWord = false, command = "")
            }
            val lower = trimmed.lowercase(Locale.ROOT)

            for (prefix in wakePrefixes) {
                if (lower == prefix) {
                    return WakeWordParseResult(
                        hasWakeWord = true,
                        isOnlyWakeWord = true,
                        command = "",
                        matchedWakeWord = prefix
                    )
                }
            }

            for (prefix in wakePrefixes) {
                if (lower.startsWith("$prefix ") || lower.startsWith("$prefix,") || lower.startsWith("$prefix.")) {
                    val cleanCmd = trimmed.substring(prefix.length).trim().trimStart(',', '.', ':', ' ').trim()
                    return WakeWordParseResult(
                        hasWakeWord = true,
                        isOnlyWakeWord = cleanCmd.isEmpty(),
                        command = cleanCmd,
                        matchedWakeWord = prefix
                    )
                }
            }

            // Word-boundary inline match so words like "arcade" or "search" never trigger "arc"
            for (prefix in wakePrefixes) {
                val regex = Regex("(?<!\\p{L})${Regex.escape(prefix)}(?!\\p{L})")
                val match = regex.find(lower)
                if (match != null) {
                    val remaining = trimmed.substring(match.range.last + 1)
                        .trim()
                        .trimStart(',', '.', ':', ' ')
                        .trim()
                    return WakeWordParseResult(
                        hasWakeWord = true,
                        isOnlyWakeWord = remaining.isEmpty(),
                        command = remaining,
                        matchedWakeWord = prefix
                    )
                }
            }

            return WakeWordParseResult(hasWakeWord = false, isOnlyWakeWord = false, command = trimmed)
        }
    }
}

