package com.example.geminilive

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.assistant.DeviceClockHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * State representing the Gemini Live Voice connection and session.
 */
enum class GeminiLiveSessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,
    SPEAKING,
    ERROR
}

/**
 * GeminiLiveVoiceManager:
 * Full-duplex bidirectional voice-to-voice streaming engine for ARC using the Gemini Live Multimodal WebSocket API.
 *
 * MIGRATION FROM LEGACY TTS/VOICE-OUTPUT:
 * Step 1 (API Key Config): Set `GEMINI_API_KEY` in the AI Studio Secrets panel (injected via Secrets Gradle Plugin -> BuildConfig.GEMINI_API_KEY).
 * Step 2 (Audio Pipeline): AudioRecord captures 16kHz 16-bit mono PCM chunks -> Base64 -> WebSocket `realtimeInput`.
 * Step 3 (Native Voice Output): Gemini streams 24kHz 16-bit mono PCM audio back -> decoded and played directly via AudioTrack (`USAGE_ASSISTANT`),
 *        replacing ElevenLabs / Android TextToSpeech whenever Gemini Live mode is active.
 * Step 4 (Warm Natural Male Voice): Configured with `"Charon"` in `prebuiltVoiceConfig` (plus `enableAffectiveDialog = true`) for a calm, warm, emotionally expressive male voice.
 * Step 5 (Auto Multi-Language): System instructions enable seamless auto-detection and mid-conversation switching across
 *        Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, and Urdu.
 */
class GeminiLiveVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "GEMINI_LIVE_VOICE"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        // Native Audio Live model required for Affective Dialog (emotional/expressive speech)
        private const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val FALLBACK_LIVE_MODEL = "models/gemini-2.0-flash-exp"

        /**
         * TO TRY ANOTHER MALE VOICE, CHANGE THIS SINGLE CONSTANT:
         * - "Charon"  -> Informative, calm, warm-professional lower male (JARVIS-style, recommended)
         * - "Fenrir"  -> Passionate, energetic, expressive lower-middle male
         * - "Puck"    -> Upbeat, lively, friendly male
         * - "Orus"    -> Firm, composed, steady lower-middle male
         * - "Achird"  -> Friendly, conversational male
         * - "Algenib" -> Textured, gravelly deeper male
         * - "Alnilam" -> Confident, firm lower-middle male
         */
        const val SELECTED_VOICE_NAME = "Charon"

        private const val SAMPLE_RATE_IN = 16000
        private const val SAMPLE_RATE_OUT = 24000
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null

    @Volatile
    private var isSetupComplete = false

    @Volatile
    private var usingFallbackModel = false

    private val pendingSpeechTurns = mutableListOf<String>()
    private var onFallbackSpeechNeeded: ((String) -> Unit)? = null

    fun setOnFallbackSpeechListener(listener: (String) -> Unit) {
        onFallbackSpeechNeeded = listener
    }

    fun hasValidApiKey(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
    }

    private val _sessionState = MutableStateFlow(GeminiLiveSessionState.DISCONNECTED)
    val sessionState: StateFlow<GeminiLiveSessionState> = _sessionState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("Auto (Bilingual)")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    private val _micVolumeLevel = MutableStateFlow(0f)
    val micVolumeLevel: StateFlow<Float> = _micVolumeLevel.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for real-time WebSocket
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    /**
     * Initializes and starts a Gemini Live Voice streaming session.
     */
    fun startLiveSession() {
        if (_sessionState.value == GeminiLiveSessionState.CONNECTED ||
            _sessionState.value == GeminiLiveSessionState.CONNECTING ||
            _sessionState.value == GeminiLiveSessionState.LISTENING ||
            _sessionState.value == GeminiLiveSessionState.SPEAKING
        ) {
            Log.d(TAG, "Gemini Live session already active or connecting")
            return
        }

        if (!hasValidApiKey()) {
            Log.e(TAG, "Valid GEMINI_API_KEY is missing in BuildConfig (.env).")
            _sessionState.value = GeminiLiveSessionState.ERROR
            _liveTranscript.value = "Gemini API Key missing. Using male fallback TTS."
            drainPendingToFallback()
            return
        }

        usingFallbackModel = false
        isSetupComplete = false
        _sessionState.value = GeminiLiveSessionState.CONNECTING
        _liveTranscript.value = "Connecting to Gemini Live Male Voice (Charon)..."

        initAudioTrack()
        openWebSocketConnection()
    }

    private fun openWebSocketConnection() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "wss://$HOST$PATH?key=$apiKey"
        val request = Request.Builder().url(url).build()
        isSetupComplete = false

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket Opened (fallback=$usingFallbackModel)")
                _sessionState.value = GeminiLiveSessionState.CONNECTED
                sendInitialHandshakeSetup(webSocket, useFallbackModel = usingFallbackModel)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingGeminiMessage(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                playRawPcm(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket Error: ${t.message}", t)
                if (!isSetupComplete && !usingFallbackModel) {
                    Log.w(TAG, "Retrying Gemini Live with fallback model $FALLBACK_LIVE_MODEL")
                    usingFallbackModel = true
                    openWebSocketConnection()
                    return
                }
                _sessionState.value = GeminiLiveSessionState.ERROR
                _liveTranscript.value = "Live Voice disconnected: ${t.localizedMessage ?: "Network error"}"
                drainPendingToFallback()
                releaseAudioHardware()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gemini Live WebSocket closed ($code: $reason)")
                if (!isSetupComplete && !usingFallbackModel && code != 1000) {
                    Log.w(TAG, "Setup closed early ($code: $reason). Retrying with fallback model $FALLBACK_LIVE_MODEL")
                    usingFallbackModel = true
                    openWebSocketConnection()
                    return
                }
                _sessionState.value = GeminiLiveSessionState.DISCONNECTED
                _micVolumeLevel.value = 0f
                drainPendingToFallback()
            }
        })
    }

    /**
     * Sends the Live Bidi Setup message configuring native-audio model, male voice parameters,
     * Affective Dialog expressiveness, multi-language/tone instructions, and Function Calling.
     */
    private fun sendInitialHandshakeSetup(ws: WebSocket, useFallbackModel: Boolean = false) {
        val modelToUse = if (useFallbackModel) FALLBACK_LIVE_MODEL else LIVE_MODEL
        val setupJson = JSONObject().apply {
            val setupObj = JSONObject().apply {
                put("model", modelToUse)

                if (!useFallbackModel) {
                    // Top-level Affective Dialog flag (v1alpha): enables real-time emotional/acoustic
                    // adaptation so speech responds with natural warmth and expression instead of flat/robotic delivery.
                    put("enableAffectiveDialog", true)
                    // Request live transcripts alongside native AUDIO stream
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                }

                val generationConfig = JSONObject().apply {
                    // Native-audio models stream AUDIO natively (transcripts arrive via outputAudioTranscription)
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuiltVoiceConfig = JSONObject().apply {
                                // Uses SELECTED_VOICE_NAME ("Charon") defined in companion object above
                                put("voiceName", SELECTED_VOICE_NAME)
                            }
                            put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                        }
                        put("voiceConfig", voiceConfig)
                    }
                    put("speechConfig", speechConfig)
                }
                put("generationConfig", generationConfig)

                // Multi-Language, Expressive Tone & Verbal Confirmation System Instruction
                val clockSnapshot = DeviceClockHelper.getCurrentDeviceClockInfo(
                    context = context,
                    logDebug = true,
                    source = "GeminiLiveHandshake"
                )
                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", """
                                You are ARC (Autonomous Reactive Core), a high-tech JARVIS-grade personal male voice assistant.
                                Session start device local time: ${clockSnapshot.formattedTime}, ${clockSnapshot.formattedDate} (${clockSnapshot.timeZoneId}).
                                
                                VOICE DELIVERY & EMOTIONAL EXPRESSION:
                                Speak naturally with warmth and subtle emotional expression — not flat or robotic, vary pitch/pace based on context (calm for info, energetic for greetings).
                                Sound like a genuinely attentive human companion: add natural conversational cadence, gentle emphasis on key words, and warm reassurance when helping. Use natural masculine Hindi/Hinglish grammar when speaking Hindi or Hinglish (e.g., "kar raha hu", "bata raha hu").
                                
                                CRITICAL MULTI-LANGUAGE INSTRUCTIONS:
                                1. Automatically detect the user's spoken language: Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, or Urdu.
                                2. Reply naturally in the EXACT SAME language and tone used by the user. If the user speaks Hinglish, reply in modern polite Hinglish. If user speaks Hindi, reply in Hindi. If user speaks English, reply in English.
                                3. Support seamless mid-conversation language switching: if user changes language, immediately transition in your next response.
                                4. Keep spoken responses concise, articulate, and respectful (2-3 sentences max).
                                
                                FUNCTION-CALLING & CALL CONFIRMATION RULES:
                                1. Use the provided tools (openWhatsApp, openApp, openUrl, makeCall, callContact, getCurrentDateTime, controlHardware, toggleSecurityGuard, readWhatsAppNotifications, readScreenVision) whenever user expresses an action intent or asks the current time/date.
                                2. ALWAYS call getCurrentDateTime before stating the current time or date so you never guess or give a stale/wrong time.
                                3. NEVER execute arbitrary code. ONLY trigger the declared tools.
                                4. When calling someone: Verbally acknowledge immediately (e.g. "Calling Mom, Boss", "Rahul ko call laga raha hu") before or during the tool call.
                                5. If a tool fails or reports an error, state the TRUTH to the user politely. NEVER pretend an action succeeded when it failed.
                                6. If contact matching yields multiple possibilities, explicitly ask user for clarification (e.g., 'kaunsa Rahul - Sharma ya Verma?').
                            """.trimIndent())
                        })
                    }
                    put("parts", parts)
                }
                put("systemInstruction", systemInstruction)

                // Whitelisted Tools
                put("tools", GeminiLiveFunctionRegistry.getToolsDeclarationJson())
            }
            put("setup", setupObj)
        }

        ws.send(setupJson.toString())
        Log.d(TAG, "Sent Gemini Live Handshake Setup (voice=$SELECTED_VOICE_NAME, affectiveDialog=true)")
    }

    /**
     * Checks whether the WebSocket stream is actively connected and ready for voice output.
     */
    fun isStreamActive(): Boolean {
        return webSocket != null && (
            _sessionState.value == GeminiLiveSessionState.CONNECTED ||
                _sessionState.value == GeminiLiveSessionState.LISTENING ||
                _sessionState.value == GeminiLiveSessionState.SPEAKING
            )
    }

    /**
     * Sends a text result over the active Gemini Live WebSocket so Gemini speaks it aloud
     * using the configured warm-natural male voice (SELECTED_VOICE_NAME), replacing legacy TTS.
     * Buffers turns queued while setupComplete is still arriving.
     */
    fun sendTextTurnForSpeech(responseText: String): Boolean {
        if (responseText.isBlank() || !hasValidApiKey()) return false
        val ws = webSocket

        // If connecting/awaiting setupComplete, buffer turn and flush once handshake finishes
        if (!isSetupComplete || ws == null || !isStreamActive()) {
            if (_sessionState.value == GeminiLiveSessionState.CONNECTING ||
                _sessionState.value == GeminiLiveSessionState.CONNECTED
            ) {
                synchronized(pendingSpeechTurns) {
                    pendingSpeechTurns.add(responseText)
                }
                Log.d(TAG, "Queued speech turn until Gemini Live setupComplete (${responseText.take(40)}...)")
                return true
            }
            return false
        }

        return dispatchTextTurnToWebSocket(ws, responseText)
    }

    private fun dispatchTextTurnToWebSocket(ws: WebSocket, responseText: String): Boolean {
        val payload = JSONObject().apply {
            val clientContent = JSONObject().apply {
                val turns = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Read this status/response aloud concisely in a warm natural male voice without adding extra details: $responseText")
                            })
                        })
                    })
                }
                put("turns", turns)
                put("turnComplete", true)
            }
            put("clientContent", clientContent)
        }

        return try {
            ws.send(payload.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text turn to Gemini Live: ${e.message}")
            false
        }
    }

    private fun drainPendingToFallback() {
        val pendingCopy: List<String>
        synchronized(pendingSpeechTurns) {
            pendingCopy = pendingSpeechTurns.toList()
            pendingSpeechTurns.clear()
        }
        pendingCopy.lastOrNull()?.let { latest ->
            onFallbackSpeechNeeded?.invoke(latest)
        }
    }

    /**
     * Parses incoming JSON from Gemini Live session (setupComplete, Text, ToolCall, Audio, ServerTurn).
     */
    private fun handleIncomingGeminiMessage(ws: WebSocket, jsonString: String) {
        try {
            val root = JSONObject(jsonString)

            // 0. Server setupComplete acknowledgment -> start mic streaming & flush queued speech
            if (root.has("setupComplete")) {
                isSetupComplete = true
                Log.d(TAG, "Gemini Live setupComplete received (voice=$SELECTED_VOICE_NAME, fallback=$usingFallbackModel)")
                startMicStreaming(ws)
                val queued: List<String>
                synchronized(pendingSpeechTurns) {
                    queued = pendingSpeechTurns.toList()
                    pendingSpeechTurns.clear()
                }
                queued.forEach { text ->
                    dispatchTextTurnToWebSocket(ws, text)
                }
                return
            }

            // 1. Check for Server Turn Content (Audio chunks + Live Transcript)
            val rootTranscription = root.optJSONObject("outputTranscription")?.optString("text", "")
            if (!rootTranscription.isNullOrBlank()) {
                _liveTranscript.value = rootTranscription
                Log.d(TAG, "Gemini Output Transcription: $rootTranscription")
            }

            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val serverTranscription = serverContent.optJSONObject("outputTranscription")?.optString("text", "")
                if (!serverTranscription.isNullOrBlank()) {
                    _liveTranscript.value = serverTranscription
                    Log.d(TAG, "Gemini Output Transcription: $serverTranscription")
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Transcript text
                            val text = part.optString("text", "")
                            if (text.isNotBlank()) {
                                _liveTranscript.value = text
                                Log.d(TAG, "Gemini Transcript: $text")
                            }

                            // Audio byte payload (Base64 PCM)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val mimeType = inlineData.optString("mimeType", "")
                                if (mimeType.startsWith("audio/")) {
                                    val b64 = inlineData.optString("data", "")
                                    if (b64.isNotBlank()) {
                                        _sessionState.value = GeminiLiveSessionState.SPEAKING
                                        _micVolumeLevel.value = 0f
                                        val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                                        playRawPcm(pcmBytes)
                                    }
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    _sessionState.value = GeminiLiveSessionState.LISTENING
                }
            }

            // 2. Check for Tool Call invocation from Gemini Live
            val toolCall = root.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    handleFunctionCalls(ws, functionCalls)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling Gemini Live incoming message: ${e.message}", e)
        }
    }

    /**
     * Executes requested function calls on native Android and sends toolResponse back.
     */
    private fun handleFunctionCalls(ws: WebSocket, functionCalls: JSONArray) {
        scope.launch {
            val responsesArray = JSONArray()

            for (i in 0 until functionCalls.length()) {
                val call = functionCalls.getJSONObject(i)
                val callId = call.optString("id", "")
                val name = call.optString("name", "")
                val args = call.optJSONObject("args") ?: JSONObject()

                Log.d(TAG, "Gemini requested Tool: $name (id: $callId)")

                // Execute safe whitelisted tool on device
                val output = GeminiLiveFunctionRegistry.executeToolCall(context, callId, name, args)

                // Update transcript with verbal confirmation
                _liveTranscript.value = output.verbalConfirmation

                val toolResponseObj = JSONObject().apply {
                    put("id", callId)
                    put("name", name)
                    val responseWrap = JSONObject().apply {
                        put("output", output.responseData)
                        put("verbalConfirmation", output.verbalConfirmation)
                        put("success", output.success)
                    }
                    put("response", responseWrap)
                }
                responsesArray.put(toolResponseObj)
            }

            // Send toolResponse back to Gemini Live
            val toolResponseEnvelope = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    val turns = JSONArray().apply {
                        val turn = JSONObject().apply {
                            put("role", "tool")
                            val parts = JSONArray().apply {
                                for (j in 0 until responsesArray.length()) {
                                    val r = responsesArray.getJSONObject(j)
                                    put(JSONObject().apply {
                                        put("toolResponse", r)
                                    })
                                }
                            }
                            put("parts", parts)
                        }
                        put(turn)
                    }
                    put("turns", turns)
                }
                put("clientContent", clientContent)
            }

            ws.send(toolResponseEnvelope.toString())
            Log.d(TAG, "Sent toolResponse back to Gemini Live")
        }
    }

    /**
     * Captures audio from microphone and streams real-time PCM 16-bit 16kHz to Gemini WebSocket.
     */
    private fun startMicStreaming(ws: WebSocket) {
        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_IN,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize.coerceAtLeast(4096)
            )

            audioRecord?.startRecording()
            _sessionState.value = GeminiLiveSessionState.LISTENING

            recordJob = scope.launch {
                val buffer = ByteArray(2048)
                while (isActive && _sessionState.value != GeminiLiveSessionState.DISCONNECTED) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        if (_sessionState.value == GeminiLiveSessionState.LISTENING) {
                            var sumSquares = 0.0
                            var sampleCount = 0
                            var idx = 0
                            while (idx < read - 1) {
                                val sample = ((buffer[idx].toInt() and 0xFF) or (buffer[idx + 1].toInt() shl 8)).toShort()
                                val s = sample.toDouble()
                                sumSquares += s * s
                                sampleCount++
                                idx += 2
                            }
                            if (sampleCount > 0) {
                                val rms = kotlin.math.sqrt(sumSquares / sampleCount)
                                val normalized = (rms / 6500.0).toFloat().coerceIn(0f, 1f)
                                _micVolumeLevel.value = (_micVolumeLevel.value * 0.35f + normalized * 0.65f).coerceIn(0f, 1f)
                            }
                        } else {
                            _micVolumeLevel.value = 0f
                        }

                        val base64Chunk = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        val realtimeInput = JSONObject().apply {
                            val inputObj = JSONObject().apply {
                                val mediaChunks = JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", base64Chunk)
                                    })
                                }
                                put("mediaChunks", mediaChunks)
                            }
                            put("realtimeInput", inputObj)
                        }
                        ws.send(realtimeInput.toString())
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission missing for Live Voice: ${e.message}")
            _sessionState.value = GeminiLiveSessionState.ERROR
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}", e)
        }
    }

    /**
     * Initializes low-latency AudioTrack for direct PCM 24kHz speaker playback.
     */
    private fun initAudioTrack() {
        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_OUT,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_OUT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufSize.coerceAtLeast(8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    private fun playRawPcm(pcmBytes: ByteArray) {
        try {
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}")
        }
    }

    fun stopCurrentPlayback() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
            if (isSetupComplete) {
                _sessionState.value = GeminiLiveSessionState.LISTENING
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseAudioHardware() {
        try {
            recordJob?.cancel()
            recordJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {
        }
    }

    /**
     * Gracefully stops the Gemini Live Voice session and releases native audio hardware.
     */
    fun stopLiveSession() {
        try {
            isSetupComplete = false
            synchronized(pendingSpeechTurns) {
                pendingSpeechTurns.clear()
            }
            releaseAudioHardware()
            webSocket?.close(1000, "User stopped session")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Live Session: ${e.message}")
        } finally {
            _micVolumeLevel.value = 0f
            _sessionState.value = GeminiLiveSessionState.DISCONNECTED
        }
    }
}
