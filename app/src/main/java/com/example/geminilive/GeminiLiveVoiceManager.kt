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
 * Core Capabilities:
 * 1. Automatic Multi-Language Detection:
 *    - Seamlessly supports Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu,
 *      Kannada, Malayalam, Punjabi, Urdu without manual selection.
 *    - Handles mid-conversation language switching on the fly.
 * 2. Whitelisted Native Function-Calling:
 *    - When user asks to call, open app, or launch WhatsApp, Gemini generates a `toolCall`.
 *    - The function is executed securely via GeminiLiveFunctionRegistry on native Android.
 *    - The response is sent back via `toolResponse` in the same WebSocket stream, and ARC verbally confirms in natural voice.
 * 3. High-Fidelity Audio Streaming:
 *    - Captures PCM 16-bit 16kHz audio from Android microphone.
 *    - Plays back PCM 24kHz audio directly via low-latency Android AudioTrack.
 *    - Zero device SpeechSynthesis needed for voice output.
 */
class GeminiLiveVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "GEMINI_LIVE_VOICE"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val SAMPLE_RATE_IN = 16000
        private const val SAMPLE_RATE_OUT = 24000
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null

    private val _sessionState = MutableStateFlow(GeminiLiveSessionState.DISCONNECTED)
    val sessionState: StateFlow<GeminiLiveSessionState> = _sessionState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("Auto (Bilingual)")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for real-time WebSocket
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    /**
     * Initializes and starts a Gemini Live Voice streaming session.
     */
    fun startLiveSession() {
        if (_sessionState.value == GeminiLiveSessionState.CONNECTED || _sessionState.value == GeminiLiveSessionState.CONNECTING) {
            Log.d(TAG, "Gemini Live session already active or connecting")
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Valid GEMINI_API_KEY is missing in BuildConfig (.env).")
            _sessionState.value = GeminiLiveSessionState.ERROR
            _liveTranscript.value = "Gemini API Key missing. Please set GEMINI_API_KEY in Secrets panel."
            return
        }

        _sessionState.value = GeminiLiveSessionState.CONNECTING
        _liveTranscript.value = "Connecting to Gemini Live Voice Stream..."

        initAudioTrack()

        val url = "wss://$HOST$PATH?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket Connected successfully!")
                _sessionState.value = GeminiLiveSessionState.CONNECTED
                sendInitialHandshakeSetup(webSocket)
                startMicStreaming(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingGeminiMessage(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Incoming binary PCM chunks
                playRawPcm(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket Error: ${t.message}", t)
                _sessionState.value = GeminiLiveSessionState.ERROR
                _liveTranscript.value = "Live Voice disconnected: ${t.localizedMessage ?: "Network error"}"
                stopLiveSession()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gemini Live WebSocket closed ($code: $reason)")
                _sessionState.value = GeminiLiveSessionState.DISCONNECTED
            }
        })
    }

    /**
     * Sends the Live Bidi Setup message configuring multimodal model, voice parameters,
     * multi-language instructions, and Whitelisted Function Calling definitions.
     */
    private fun sendInitialHandshakeSetup(ws: WebSocket) {
        val setupJson = JSONObject().apply {
            val setupObj = JSONObject().apply {
                put("model", "models/gemini-2.0-flash-exp")

                val generationConfig = JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                        put("TEXT")
                    })
                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuiltVoiceConfig = JSONObject().apply {
                                put("voiceName", "Aoede") // Energetic, crisp high-tech voice
                            }
                            put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                        }
                        put("voiceConfig", voiceConfig)
                    }
                    put("speechConfig", speechConfig)
                }
                put("generationConfig", generationConfig)

                // Multi-Language & Verbal Confirmation System Instruction
                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", """
                                You are ARC (Autonomous Reactive Core), a high-tech JARVIS-grade personal voice assistant.
                                
                                CRITICAL MULTI-LANGUAGE INSTRUCTIONS:
                                1. Automatically detect the user's spoken language: Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, or Urdu.
                                2. Reply naturally in the EXACT SAME language and tone used by the user. If the user speaks Hinglish, reply in modern polite Hinglish. If user speaks Hindi, reply in Hindi. If user speaks English, reply in English.
                                3. Support seamless mid-conversation language switching: if user changes language, immediately transition in your next response.
                                4. Keep spoken responses concise, articulate, and respectful (2-3 sentences max).
                                
                                FUNCTION-CALLING & CALL CONFIRMATION RULES:
                                1. Use the provided tools (openWhatsApp, openApp, openUrl, makeCall, callContact) whenever user expresses an action intent.
                                2. NEVER execute arbitrary code. ONLY trigger the declared tools.
                                3. When calling someone: Verbally acknowledge immediately (e.g. "Calling Mom, Boss", "Rahul ko call laga rahi hu") before or during the tool call.
                                4. If a tool fails or reports an error, state the TRUTH to the user politely. NEVER pretend an action succeeded when it failed.
                                5. If contact matching yields multiple possibilities, explicitly ask user for clarification (e.g., 'kaunsa Rahul - Sharma ya Verma?').
                            """.trimIndent())
                        })
                    }
                    put("parts", parts)
                }
                put("systemInstruction", systemInstruction)

                // Whitelisted Tools (openWhatsApp, openApp, openUrl, makeCall, callContact)
                put("tools", GeminiLiveFunctionRegistry.getToolsDeclarationJson())
            }
            put("setup", setupObj)
        }

        ws.send(setupJson.toString())
        Log.d(TAG, "Sent Gemini Live Handshake Setup with Function Declarations")
    }

    /**
     * Parses incoming JSON from Gemini Live session (Text, ToolCall, Audio, ServerTurn).
     */
    private fun handleIncomingGeminiMessage(ws: WebSocket, jsonString: String) {
        try {
            val root = JSONObject(jsonString)

            // 1. Check for Server Turn Content (Audio chunks + Live Transcript)
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
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

    /**
     * Gracefully stops the Gemini Live Voice session and releases native audio hardware.
     */
    fun stopLiveSession() {
        try {
            recordJob?.cancel()
            recordJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            webSocket?.close(1000, "User stopped session")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Live Session: ${e.message}")
        } finally {
            _sessionState.value = GeminiLiveSessionState.DISCONNECTED
        }
    }
}
