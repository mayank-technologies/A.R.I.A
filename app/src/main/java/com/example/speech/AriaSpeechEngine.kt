package com.example.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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

/**
 * Advanced ARIA Speech Engine featuring:
 * 1. ElevenLabs Neural Human Voice Synthesis (Rachel / Warm Female Voice)
 * 2. MD5 Disk Audio Caching for fast, zero-quota playback on repeated sentences
 * 3. Android MediaPlayer for smooth high-fidelity audio output
 * 4. Automatic Seamless Fallback to Native Android TextToSpeech when offline or on API failure
 * 5. Latency/Synthesizing state indication for UI
 */
class AriaSpeechEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())

    // Native Android TTS Fallback
    private var nativeTts: TextToSpeech? = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null

    private var isNativeTtsInitialized = false
    private var pendingTextToSpeak: String? = null

    // ElevenLabs Configuration
    // Rachel: "21m00Tcm4TlvDq8ikWAM" (Natural, Warm, Clear Female Voice)
    // Sarah:  "EXAVITQu4vr4xnSDxMaL"
    private var voiceId = try {
        val prefs = context.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
        val savedVoiceId = prefs.getString("elevenlabs_voice_id", "")
        if (!savedVoiceId.isNullOrBlank()) savedVoiceId else "21m00Tcm4TlvDq8ikWAM"
    } catch (e: Exception) {
        "21m00Tcm4TlvDq8ikWAM"
    }
    private var apiKey = try { BuildConfig::class.java.getField("ELEVENLABS_API_KEY").get(null) as? String ?: "" } catch (e: Exception) { "" }

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

    private val _isWakeWordModeEnabled = MutableStateFlow(false)
    val isWakeWordModeEnabled: StateFlow<Boolean> = _isWakeWordModeEnabled.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private var onSpeechResultListener: ((String) -> Unit)? = null
    private var wakeWordJob: Job? = null

    init {
        initSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isNativeTtsInitialized = true
            val result = nativeTts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                nativeTts?.setLanguage(Locale.US)
            }
            nativeTts?.setSpeechRate(_speechRate.value)
            nativeTts?.setPitch(1.10f)

            // Select Permanent Female Voice if available on device
            try {
                val availableVoices = nativeTts?.voices
                if (availableVoices != null) {
                    val femaleVoice = availableVoices.find { voice ->
                        voice.name.contains("female", ignoreCase = true) ||
                        voice.name.contains("hi-in-x-fie", ignoreCase = true) ||
                        voice.name.contains("hi-in-x-hic", ignoreCase = true) ||
                        voice.name.contains("en-us-x-sfg", ignoreCase = true) ||
                        voice.name.contains("en-in-x-eef", ignoreCase = true)
                    }
                    if (femaleVoice != null) {
                        nativeTts?.voice = femaleVoice
                        Log.d("AriaSpeechEngine", "Selected permanent female voice: ${femaleVoice.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("AriaSpeechEngine", "Could not set custom female voice: ${e.message}")
            }

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
            Log.e("AriaSpeechEngine", "Native TextToSpeech initialization failed: $status")
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
            if (key.isNotBlank()) {
                val cachedFile = getCachedAudioFile(text)
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    Log.d("AriaSpeechEngine", "Cache hit! Playing cached audio for query: $text")
                    playAudioFile(cachedFile)
                    return@launch
                }

                // ElevenLabs API Call
                _isSynthesizing.value = true
                val generatedFile = fetchElevenLabsAudio(text, key)
                _isSynthesizing.value = false

                if (generatedFile != null && generatedFile.exists()) {
                    playAudioFile(generatedFile)
                    return@launch
                }
            }

            // Fallback to Native Android TextToSpeech
            withContext(Dispatchers.Main) {
                Log.w("AriaSpeechEngine", "Using Native Android TTS Fallback for: $text")
                speakNative(text)
            }
        }
    }

    private fun getApiKey(): String {
        if (apiKey.isNotBlank()) return apiKey
        val prefs = context.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
        return prefs.getString("elevenlabs_api_key", "") ?: ""
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
     * Calls ElevenLabs Text-to-Speech API (v1/text-to-speech) with Rachel Neural Voice.
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

    private fun speakNative(text: String) {
        if (!isNativeTtsInitialized) {
            pendingTextToSpeak = text
            return
        }
        nativeTts?.stop()
        val utteranceId = "ARIA_UTTERANCE_${System.currentTimeMillis()}"
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        _isSpeaking.value = true
        nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun getCachedAudioFile(text: String): File {
        val hash = md5(text.trim().lowercase(Locale.ROOT))
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

    fun setWakeWordModeEnabled(enabled: Boolean) {
        _isWakeWordModeEnabled.value = enabled
        if (enabled) {
            scheduleWakeWordLoop(300L)
        } else {
            wakeWordJob?.cancel()
            stopListening()
        }
    }

    fun toggleWakeWordMode(): Boolean {
        val newState = !_isWakeWordModeEnabled.value
        setWakeWordModeEnabled(newState)
        return newState
    }

    fun scheduleWakeWordLoop(delayMs: Long = 1000L) {
        if (!_isWakeWordModeEnabled.value) return
        wakeWordJob?.cancel()
        wakeWordJob = engineScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(delayMs)
            if (_isWakeWordModeEnabled.value && !_isSpeaking.value && !_isSynthesizing.value) {
                startWakeWordListening()
            }
        }
    }

    private fun startWakeWordListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        if (_isSpeaking.value || _isSynthesizing.value) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for 'Hey ARIA'...")
        }
        try {
            _isListening.value = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            Log.e("AriaSpeechEngine", "Error starting wake word listener: ${e.message}")
            scheduleWakeWordLoop(2000L)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    if (_isWakeWordModeEnabled.value && !_isSpeaking.value) {
                        scheduleWakeWordLoop(1200L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        _lastRecognizedText.value = text
                        onSpeechResultListener?.invoke(text)
                    }
                    if (_isWakeWordModeEnabled.value && !_isSpeaking.value) {
                        scheduleWakeWordLoop(1500L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        this.onSpeechResultListener = onResult
        _isListening.value = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for ARIA command...")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            Log.e("AriaSpeechEngine", "Error starting speech recognition: ${e.message}")
        }
    }

    fun stopListening() {
        wakeWordJob?.cancel()
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    fun destroy() {
        wakeWordJob?.cancel()
        stopSpeaking()
        nativeTts?.shutdown()
        speechRecognizer?.destroy()
    }

    companion object {
        data class WakeWordParseResult(
            val hasWakeWord: Boolean,
            val isOnlyWakeWord: Boolean,
            val command: String
        )

        fun parseWakeWord(input: String): WakeWordParseResult {
            val trimmed = input.trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            val wakePrefixes = listOf(
                "hey aria", "ok aria", "hi aria", "hello aria", "namaste aria", "listen aria",
                "hey area", "ok area", "hi area", "hello area",
                "hey a.r.i.a.", "ok a.r.i.a.", "hi a.r.i.a.",
                "aria", "area", "a.r.i.a."
            )

            for (prefix in wakePrefixes) {
                if (lower == prefix) {
                    return WakeWordParseResult(hasWakeWord = true, isOnlyWakeWord = true, command = "")
                }
            }

            for (prefix in wakePrefixes) {
                if (lower.startsWith("$prefix ") || lower.startsWith("$prefix,") || lower.startsWith("$prefix.")) {
                    val cleanCmd = trimmed.substring(prefix.length).trim().trimStart(',', '.', ':', ' ').trim()
                    return WakeWordParseResult(
                        hasWakeWord = true,
                        isOnlyWakeWord = cleanCmd.isEmpty(),
                        command = cleanCmd
                    )
                }
            }

            for (prefix in listOf("hey aria", "ok aria", "hi aria", "hey area", "ok area")) {
                val idx = lower.indexOf(prefix)
                if (idx != -1) {
                    val remaining = trimmed.substring(idx + prefix.length).trim().trimStart(',', '.', ':', ' ').trim()
                    return WakeWordParseResult(
                        hasWakeWord = true,
                        isOnlyWakeWord = remaining.isEmpty(),
                        command = remaining
                    )
                }
            }

            return WakeWordParseResult(hasWakeWord = false, isOnlyWakeWord = false, command = trimmed)
        }
    }
}

