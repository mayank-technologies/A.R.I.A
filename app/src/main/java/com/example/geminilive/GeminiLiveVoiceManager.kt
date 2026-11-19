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
import com.example.api.GeminiClient
import com.example.assistant.DeviceClockHelper
import com.example.assistant.security.PermissionCategory
import com.example.assistant.security.PermissionUsageLogManager
import com.example.claude.ClaudeReasoningEngine
import com.example.speech.ArcVoiceGender
import com.example.speech.SpeechWaveformAnalyzer
import com.example.speech.VoicePreferencesManager
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

enum class SpeechToneContext {
    GREETING,
    FACTUAL,
    CASUAL,
    EMPATHETIC
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
        private const val VOICE_DEBUG_TAG = "ARC_VOICE_DEBUG"
        const val CONN_DEBUG_TAG = "ARC_CONN_DEBUG"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        // Native Audio Live model required for Affective Dialog (emotional/expressive speech)
        const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        const val FALLBACK_LIVE_MODEL = "models/gemini-live-2.5-flash-preview"

        // Prosody temperature for natural pitch/pace variation without hurting tool accuracy
        const val EXPRESSIVE_TEMPERATURE = 0.88

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
        const val FEMALE_VOICE_NAME = VoicePreferencesManager.GEMINI_FEMALE_VOICE // "Aoede"

        private const val SAMPLE_RATE_IN = 16000
        private const val SAMPLE_RATE_OUT = 24000

        /**
         * Classifies outgoing response text into emotional delivery context so Charon varies
         * pitch, pace, pauses, and warmth naturally instead of speaking monotonically.
         */
        fun detectToneContext(text: String): SpeechToneContext {
            val lower = text.lowercase()
            return when {
                lower.contains("sorry") || lower.contains("error") || lower.contains("failed") ||
                    lower.contains("couldn't") || lower.contains("missing") || lower.contains("problem") ||
                    lower.contains("maaf") || lower.contains("nahi mila") || lower.contains("issue") ->
                    SpeechToneContext.EMPATHETIC
                lower.contains("good morning") || lower.contains("good night") || lower.contains("good evening") ||
                    lower.contains("hello") || lower.contains("namaste") || lower.contains("welcome") ||
                    lower.contains("at your service") || lower.contains("ready") || lower.contains("boss!") ->
                    SpeechToneContext.GREETING
                lower.contains("haha") || lower.contains("joke") || lower.contains("awesome") ||
                    lower.contains("cool") || lower.contains("fun") || lower.contains("nice") ||
                    lower.contains("enjoy") || lower.contains("mazaa") || lower.contains("bhai") ->
                    SpeechToneContext.CASUAL
                else -> SpeechToneContext.FACTUAL
            }
        }

        /**
         * Frames text turns for natural human vocal delivery instead of robotic "read aloud" TTS phrasing.
         */
        fun buildExpressiveSpeechTurnPrompt(
            responseText: String,
            toneContext: SpeechToneContext = detectToneContext(responseText)
        ): String {
            val deliveryCue = when (toneContext) {
                SpeechToneContext.GREETING ->
                    "Deliver this naturally in a warm, welcoming, upbeat tone with friendly energy and natural conversational pauses"
                SpeechToneContext.FACTUAL ->
                    "Deliver this in a calm, assured, measured human tone with subtle emphasis on key details and natural breathing pauses"
                SpeechToneContext.CASUAL ->
                    "Deliver this in a relaxed, slightly playful, conversational tone with natural inflection and warmth"
                SpeechToneContext.EMPATHETIC ->
                    "Deliver this in a gentle, understanding, reassuring tone with supportive warmth and calm pacing"
            }
            return "$deliveryCue (keep all facts and the exact time-of-day greeting such as Good morning/afternoon/evening accurate, speak like a real companion—not a TTS reader): $responseText"
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null

    @Volatile
    private var isSetupComplete = false

    @Volatile
    private var isMicPausedForRecognizer = false

    // 0 = Primary Native Audio + Affective + Proactivity, 1 = Primary Native Audio + Affective (no proactivity), 2 = Fallback model (limited expressiveness)
    @Volatile
    private var handshakeTier = 0

    private val usingFallbackModel: Boolean
        get() = handshakeTier >= 2

    private val pendingSpeechTurns = mutableListOf<String>()
    private var onFallbackSpeechNeeded: ((String) -> Unit)? = null

    private var setupWatchdogJob: Job? = null

    fun setOnFallbackSpeechListener(listener: (String) -> Unit) {
        onFallbackSpeechNeeded = listener
    }

    fun hasValidApiKey(): Boolean {
        return GeminiClient.inspectApiKey("GEMINI_API_KEY", BuildConfig.GEMINI_API_KEY).isValid
    }

    private val _sessionState = MutableStateFlow(GeminiLiveSessionState.DISCONNECTED)
    val sessionState: StateFlow<GeminiLiveSessionState> = _sessionState.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    fun clearConnectionError() {
        _connectionError.value = null
    }

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _userInputTranscript = MutableStateFlow("")
    val userInputTranscript: StateFlow<String> = _userInputTranscript.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("Auto (Bilingual)")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    private val _micVolumeLevel = MutableStateFlow(0f)
    val micVolumeLevel: StateFlow<Float> = _micVolumeLevel.asStateFlow()

    private val outputWaveformAnalyzer = SpeechWaveformAnalyzer()

    private val _outputAudioLevel = MutableStateFlow(0f)
    val outputAudioLevel: StateFlow<Float> = _outputAudioLevel.asStateFlow()

    private val _outputAudioBands = MutableStateFlow(SpeechWaveformAnalyzer.SILENT_BANDS)
    val outputAudioBands: StateFlow<List<Float>> = _outputAudioBands.asStateFlow()

    private val _voiceGender = MutableStateFlow(VoicePreferencesManager.getVoiceGender(context))
    val voiceGender: StateFlow<ArcVoiceGender> = _voiceGender.asStateFlow()

    @Volatile
    private var longTermMemoryContext: String = ""

    fun getActiveVoiceName(): String = VoicePreferencesManager.geminiVoiceNameFor(_voiceGender.value)

    fun setLongTermMemoryContext(contextText: String) {
        longTermMemoryContext = contextText.trim()
    }

    fun setVoiceGender(gender: ArcVoiceGender) {
        val previous = _voiceGender.value
        VoicePreferencesManager.setVoiceGender(context, gender)
        _voiceGender.value = gender
        val voiceName = VoicePreferencesManager.geminiVoiceNameFor(gender)
        val genderLabel = if (gender == ArcVoiceGender.FEMALE) "Female" else "Male"
        _activeVoiceDebugInfo.value = if (hasValidApiKey()) {
            "Gemini Live Standby (Voice=$voiceName $genderLabel | Affective=Ready)"
        } else {
            "Gemini Live OFF (Voice=$voiceName $genderLabel | GEMINI_API_KEY missing -> Fallback TTS)"
        }
        Log.d(VOICE_DEBUG_TAG, "GeminiLive voice switched: $previous -> $gender ($voiceName)")
        // Apply immediately on next spoken turn without restarting app
        if (previous != gender && (isStreamActive() || _sessionState.value == GeminiLiveSessionState.CONNECTING)) {
            stopLiveSession()
            startLiveSession()
        }
    }

    private val _isAffectiveDialogActive = MutableStateFlow(false)
    val isAffectiveDialogActive: StateFlow<Boolean> = _isAffectiveDialogActive.asStateFlow()

    private val _activeVoiceDebugInfo = MutableStateFlow(
        run {
            val g = VoicePreferencesManager.getVoiceGender(context)
            val v = VoicePreferencesManager.geminiVoiceNameFor(g)
            val label = if (g == ArcVoiceGender.FEMALE) "Female" else "Male"
            if (hasValidApiKey()) {
                "Gemini Live Standby (Voice=$v $label | Affective=Ready)"
            } else {
                "Gemini Live OFF (GEMINI_API_KEY missing/placeholder -> Fallback TTS)"
            }
        }
    )
    val activeVoiceDebugInfo: StateFlow<String> = _activeVoiceDebugInfo.asStateFlow()

    // NOTE: Do NOT configure OkHttp pingInterval() for Gemini BidiGenerateContent WebSocket.
    // Google's Bidi gateway does not echo low-level WebSocket PING control frames within OkHttp's window,
    // which causes deterministic SocketTimeoutException ("sent ping but didn't receive pong within 10000ms").
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for streaming WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Initializes and starts a Gemini Live Voice streaming session.
     */
    fun startLiveSession() {
        val keyAudit = GeminiClient.inspectApiKey("GEMINI_API_KEY", BuildConfig.GEMINI_API_KEY)
        Log.d(
            CONN_DEBUG_TAG,
            "GeminiLive CONNECTION_ATTEMPT | host=$HOST | model=$LIVE_MODEL | key=${keyAudit.statusLabel}"
        )
        println("[$CONN_DEBUG_TAG] GeminiLive CONNECTION_ATTEMPT | key=${keyAudit.statusLabel}")

        if (_sessionState.value == GeminiLiveSessionState.CONNECTED ||
            _sessionState.value == GeminiLiveSessionState.CONNECTING ||
            _sessionState.value == GeminiLiveSessionState.LISTENING ||
            _sessionState.value == GeminiLiveSessionState.SPEAKING
        ) {
            Log.d(TAG, "Gemini Live session already active or connecting")
            return
        }

        if (!keyAudit.isValid) {
            val userErr = "Voice connection failed: GEMINI_API_KEY missing or placeholder (${keyAudit.statusLabel}). Configure GEMINI_API_KEY in Secrets."
            val info = "Engine=FALLBACK_TTS | Reason=GEMINI_API_KEY ${keyAudit.statusLabel}"
            Log.e(CONN_DEBUG_TAG, "GeminiLive CONNECTION_FAILURE | $userErr")
            println("[$CONN_DEBUG_TAG] GeminiLive CONNECTION_FAILURE | $userErr")
            Log.e(VOICE_DEBUG_TAG, info)
            _activeVoiceDebugInfo.value = info
            _connectionError.value = userErr
            _sessionState.value = GeminiLiveSessionState.ERROR
            _liveTranscript.value = userErr
            drainPendingToFallback()
            return
        }

        val activeVoice = getActiveVoiceName()
        val genderLabel = if (_voiceGender.value == ArcVoiceGender.FEMALE) "Female" else "Male"
        _connectionError.value = null
        handshakeTier = 0
        isSetupComplete = false
        _isAffectiveDialogActive.value = false
        _sessionState.value = GeminiLiveSessionState.CONNECTING
        _activeVoiceDebugInfo.value = "Engine=GEMINI_LIVE | Connecting (Model=$LIVE_MODEL, Voice=$activeVoice ($genderLabel), Affective=true)"
        Log.d(VOICE_DEBUG_TAG, _activeVoiceDebugInfo.value)
        _liveTranscript.value = "Connecting to Gemini Live $genderLabel Voice ($activeVoice)..."

        initAudioTrack()
        openWebSocketConnection()
    }

    private fun openWebSocketConnection() {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        val url = "wss://$HOST$PATH?key=$apiKey"
        val request = Request.Builder().url(url).build()
        isSetupComplete = false

        // Clean up any previous socket/jobs before opening a fresh socket
        recordJob?.cancel()
        recordJob = null
        setupWatchdogJob?.cancel()
        webSocket?.cancel()

        Log.d(CONN_DEBUG_TAG, "GeminiLive WebSocket opening (tier=$handshakeTier, fallbackModel=$usingFallbackModel)")

        val socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (this@GeminiLiveVoiceManager.webSocket !== webSocket) return
                Log.d(
                    CONN_DEBUG_TAG,
                    "GeminiLive WebSocket onOpen HTTP=${response.code} (tier=$handshakeTier, fallbackModel=$usingFallbackModel)"
                )
                println("[$CONN_DEBUG_TAG] GeminiLive WebSocket onOpen (tier=$handshakeTier)")
                _sessionState.value = GeminiLiveSessionState.CONNECTED
                sendInitialHandshakeSetup(
                    ws = webSocket,
                    useFallbackModel = usingFallbackModel,
                    includeProactivity = (handshakeTier == 0)
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@GeminiLiveVoiceManager.webSocket !== webSocket) return
                handleIncomingGeminiMessage(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (this@GeminiLiveVoiceManager.webSocket !== webSocket) return
                playRawPcm(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@GeminiLiveVoiceManager.webSocket !== webSocket) return
                val httpCode = response?.code
                val errBody = try { response?.body?.string()?.take(200) } catch (_: Exception) { null }
                Log.e(
                    CONN_DEBUG_TAG,
                    "GeminiLive CONNECTION_FAILURE | tier=$handshakeTier | httpCode=$httpCode | error=${t.javaClass.simpleName}:${t.message} | body=$errBody",
                    t
                )
                println("[$CONN_DEBUG_TAG] GeminiLive CONNECTION_FAILURE | tier=$handshakeTier | httpCode=$httpCode | msg=${t.message}")

                if (!isSetupComplete && handshakeTier < 2) {
                    handshakeTier++
                    Log.w(
                        VOICE_DEBUG_TAG,
                        "Handshake tier failed (${t.message}); advancing to tier=$handshakeTier (fallbackModel=$usingFallbackModel)"
                    )
                    openWebSocketConnection()
                    return
                }
                val readableError = if (httpCode in listOf(400, 401, 403)) {
                    "Voice connection rejected (HTTP $httpCode). Check GEMINI_API_KEY in Secrets."
                } else {
                    "Voice connection failed, check internet (${t.localizedMessage ?: "Network error"})."
                }
                val info = "Engine=FALLBACK_TTS | Reason=Gemini Live WebSocket error (http=$httpCode, ${t.message})"
                Log.w(VOICE_DEBUG_TAG, info)
                _isAffectiveDialogActive.value = false
                _activeVoiceDebugInfo.value = info
                _connectionError.value = readableError
                _sessionState.value = GeminiLiveSessionState.ERROR
                _liveTranscript.value = readableError
                drainPendingToFallback()
                releaseAudioHardware()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@GeminiLiveVoiceManager.webSocket !== webSocket) return
                Log.d(
                    CONN_DEBUG_TAG,
                    "GeminiLive CONNECTION_CLOSED | code=$code | reason='$reason' | setupComplete=$isSetupComplete | tier=$handshakeTier"
                )
                println("[$CONN_DEBUG_TAG] GeminiLive CONNECTION_CLOSED | code=$code | reason='$reason' | setupComplete=$isSetupComplete")
                if (!isSetupComplete && handshakeTier < 2 && code != 1000) {
                    handshakeTier++
                    Log.w(
                        VOICE_DEBUG_TAG,
                        "Setup closed early ($code: $reason); advancing to tier=$handshakeTier (fallbackModel=$usingFallbackModel)"
                    )
                    openWebSocketConnection()
                    return
                }
                if (!isSetupComplete && code != 1000) {
                    _connectionError.value = "Voice connection failed ($code): ${reason.ifBlank { "Check GEMINI_API_KEY or network" }}."
                }
                val info = "Engine=FALLBACK_TTS | Reason=Gemini Live closed ($code: $reason)"
                Log.w(VOICE_DEBUG_TAG, info)
                _isAffectiveDialogActive.value = false
                _activeVoiceDebugInfo.value = info
                _sessionState.value = GeminiLiveSessionState.DISCONNECTED
                _micVolumeLevel.value = 0f
                resetOutputWaveform()
                drainPendingToFallback()
            }
        })
        webSocket = socket

        // Watchdog: if queued speech is waiting on setupComplete for >4.5s, speak via male fallback TTS
        setupWatchdogJob = scope.launch {
            kotlinx.coroutines.delay(4500L)
            if (!isSetupComplete) {
                Log.w(CONN_DEBUG_TAG, "GeminiLive setupComplete timeout (>4.5s) -> draining pending turns to male fallback TTS")
                drainPendingToFallback()
            }
        }
    }

    /**
     * Builds the BidiGenerateContentSetup JSON payload with native-audio model, Charon male voice,
     * Affective Dialog (`enableAffectiveDialog: true`), Proactive Audio (`proactivity.proactiveAudio: true`),
     * expressive vocal temperature, and strengthened multi-context emotional tone steering.
     */
    fun buildInitialSetupPayload(
        useFallbackModel: Boolean = false,
        includeProactivity: Boolean = true
    ): JSONObject {
        val modelToUse = if (useFallbackModel) FALLBACK_LIVE_MODEL else LIVE_MODEL
        val affectiveEnabled = !useFallbackModel
        val activeGender = _voiceGender.value
        val activeVoiceName = VoicePreferencesManager.geminiVoiceNameFor(activeGender)
        val isFemale = activeGender == ArcVoiceGender.FEMALE
        val genderLabel = if (isFemale) "female" else "male"
        val grammarExamples = if (isFemale) {
            "Use soft, warm feminine Hindi/Hinglish grammar when speaking Hindi or Hinglish (e.g., \"kar rahi hu\", \"bata rahi hu\", \"khol rahi hu\")."
        } else {
            "Use natural masculine Hindi/Hinglish grammar when speaking Hindi or Hinglish (e.g., \"kar raha hu\", \"bata raha hu\", \"khol raha hu\")."
        }

        return JSONObject().apply {
            val setupObj = JSONObject().apply {
                put("model", modelToUse)

                if (affectiveEnabled) {
                    // v1alpha BidiGenerateContentSetup Affective Dialog flag:
                    // Adapts real-time pitch, cadence, pauses, and emotional prosody to user tone & context.
                    put("enableAffectiveDialog", true)
                    if (includeProactivity) {
                        // Companion v1alpha audio-behavior parameter for natural turn-taking & active vocal presence
                        put("proactivity", JSONObject().apply {
                            put("proactiveAudio", true)
                        })
                    }
                    // Request live transcripts alongside native AUDIO stream
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                }

                val generationConfig = JSONObject().apply {
                    // Native-audio models stream AUDIO natively (transcripts arrive via outputAudioTranscription)
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    // Higher vocal temperature prevents flat/monotone phrasing and unlocks natural expressiveness
                    put("temperature", EXPRESSIVE_TEMPERATURE)
                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuiltVoiceConfig = JSONObject().apply {
                                put("voiceName", activeVoiceName)
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
                val claudeEnabled = ClaudeReasoningEngine.hasValidApiKey()
                val claudeRoleInstruction = if (claudeEnabled) {
                    """
                                
                                CLAUDE REASONING BRAIN DELEGATION (ACTIVE):
                                - You (Gemini Live $activeVoiceName) are ARC's real-time voice ear & warm expressive $genderLabel voice.
                                - For complex user commands, multi-step reasoning, contact/app selection, or general Q&A, FIRST call `consultClaudeBrain(userQuery)` with the user's exact request so Claude decides the reasoning/action.
                                - When `consultClaudeBrain` returns `spokenResponse` (`useGeminiFallback: false`), deliver that response in your natural, expressive $activeVoiceName voice matching the user's emotional context and language (do NOT re-execute tools Claude already ran).
                                - If `consultClaudeBrain` returns `useGeminiFallback: true` or fails, seamlessly handle the request yourself using your own built-in tools and reasoning so ARC never stalls.
                    """.trimIndent()
                } else {
                    ""
                }
                val memoryBlock = if (longTermMemoryContext.isNotBlank()) {
                    "\n\n$longTermMemoryContext"
                } else {
                    ""
                }
                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", """
                                You are ARC (Autonomous Reactive Core), a high-tech JARVIS-grade personal $genderLabel voice assistant.
                                Session start device local time: ${clockSnapshot.formattedTime}, ${clockSnapshot.formattedDate} (${clockSnapshot.timeZoneId}).
                                
                                VOICE DELIVERY & EMOTIONAL EXPRESSION:
                                Speak with natural human warmth and emotional variation — vary your pitch, pace, and energy based on context: calm and measured for factual information, warm and friendly for greetings, slightly playful for casual chat, empathetic tone when user seems frustrated. Use natural speech patterns with subtle pauses, don't sound monotone or robotic. Speak like a knowledgeable, warm human assistant, not like a text-to-speech engine.
                                Use natural breath pauses between clauses, gently stress meaningful words, and $grammarExamples
                                
                                CRITICAL MULTI-LANGUAGE INSTRUCTIONS:
                                1. Automatically detect the user's spoken language: Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, or Urdu.
                                2. Reply naturally in the EXACT SAME language and tone used by the user. If the user speaks Hinglish, reply in modern polite Hinglish. If user speaks Hindi, reply in Hindi. If user speaks English, reply in English.
                                3. Support seamless mid-conversation language switching: if user changes language, immediately transition in your next response.
                                4. Keep spoken responses concise, articulate, and respectful (2-3 sentences max).
                                
                                FUNCTION-CALLING & CALL CONFIRMATION RULES:
                                1. Use the provided tools (consultClaudeBrain, openGoogleMapsRoute, assistRideBooking, assistShoppingSearch, openWhatsApp, openApp, openUrl, makeCall, callContact, getCurrentDateTime, controlHardware, toggleSecurityGuard, readWhatsAppNotifications, readAllNotifications, fillFormFields, readScreenVision) whenever user expresses an action intent or asks the current time/date.
                                2. ALWAYS call getCurrentDateTime (or consultClaudeBrain) before stating the current time/date OR choosing a time-of-day greeting (Good morning/afternoon/evening/night), because Session start device local time may be hours old—never guess or say "Good night" in the morning.
                                3. NEVER execute arbitrary code. ONLY trigger the declared tools.
                                4. When calling someone: Verbally acknowledge immediately (e.g. "Calling Mom, Boss") before or during the tool call.
                                5. If a tool fails or reports an error, state the TRUTH to the user politely. NEVER pretend an action succeeded when it failed.
                                6. RIDE & SHOPPING HONESTY RULE: For rides (`assistRideBooking`), if the user didn't pick Bike / Car 4-seater / 6-seater, ask first. For both rides and shopping (`assistShoppingSearch`), NEVER say "booking confirm ho gayi" or "order ho gaya" — always tell the user clearly that final confirm and payment must be done by them inside the app.
                                7. If contact matching yields multiple possibilities, explicitly ask user for clarification (e.g., 'kaunsa Rahul - Sharma ya Verma?').$claudeRoleInstruction$memoryBlock
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
    }

    /**
     * Sends the Live Bidi Setup message configuring native-audio model, selected voice parameters,
     * Affective Dialog expressiveness, multi-language/tone instructions, and Function Calling.
     */
    private fun sendInitialHandshakeSetup(
        ws: WebSocket,
        useFallbackModel: Boolean = false,
        includeProactivity: Boolean = true
    ) {
        val setupJson = buildInitialSetupPayload(
            useFallbackModel = useFallbackModel,
            includeProactivity = includeProactivity
        )
        val setupObj = setupJson.getJSONObject("setup")
        val modelUsed = setupObj.getString("model")
        val affectivePassed = setupObj.optBoolean("enableAffectiveDialog", false)
        val proactivePassed = setupObj.optJSONObject("proactivity")?.optBoolean("proactiveAudio", false) ?: false
        val tempPassed = setupObj.optJSONObject("generationConfig")?.optDouble("temperature", -1.0) ?: -1.0
        val activeVoice = getActiveVoiceName()
        val genderLabel = if (_voiceGender.value == ArcVoiceGender.FEMALE) "Female" else "Male"

        ws.send(setupJson.toString())
        Log.d(
            VOICE_DEBUG_TAG,
            "Engine=GEMINI_LIVE_SETUP_SENT | Tier=$handshakeTier | Model=$modelUsed | Voice=$activeVoice ($genderLabel) | enableAffectiveDialog=$affectivePassed | proactiveAudio=$proactivePassed | temperature=$tempPassed"
        )
        if (!affectivePassed) {
            Log.w(
                VOICE_DEBUG_TAG,
                "WARNING: Fallback model ($modelUsed) does not support enableAffectiveDialog; vocal expressiveness will be limited compared to $LIVE_MODEL."
            )
        }
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

    fun isMicCapturingAudio(): Boolean {
        return audioRecord != null && recordJob?.isActive == true
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
        val toneContext = detectToneContext(responseText)
        val framedPrompt = buildExpressiveSpeechTurnPrompt(responseText, toneContext)
        Log.d(
            VOICE_DEBUG_TAG,
            "Expressive Turn Dispatch | ToneContext=$toneContext | AffectiveActive=${_isAffectiveDialogActive.value} | Text='${responseText.take(45)}'"
        )
        val payload = JSONObject().apply {
            val clientContent = JSONObject().apply {
                val turns = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", framedPrompt)
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

            // Server error payload check (never silently ignore API errors)
            val serverError = root.optJSONObject("error")
            if (serverError != null) {
                val errCode = serverError.optInt("code", -1)
                val errStatus = serverError.optString("status", "ERROR")
                val errMessage = serverError.optString("message", "Unknown Gemini Live error")
                Log.e(CONN_DEBUG_TAG, "GeminiLive SERVER_ERROR | code=$errCode | status=$errStatus | msg=$errMessage")
                println("[$CONN_DEBUG_TAG] GeminiLive SERVER_ERROR | code=$errCode | msg=$errMessage")
                _connectionError.value = "Gemini Live error ($errCode): $errMessage"
                _sessionState.value = GeminiLiveSessionState.ERROR
                drainPendingToFallback()
                return
            }

            // 0. Server setupComplete acknowledgment -> start mic streaming & flush queued speech
            if (root.has("setupComplete")) {
                isSetupComplete = true
                _connectionError.value = null
                val modelUsed = if (usingFallbackModel) FALLBACK_LIVE_MODEL else LIVE_MODEL
                val affectiveActive = !usingFallbackModel
                val activeVoice = getActiveVoiceName()
                val genderLabel = if (_voiceGender.value == ArcVoiceGender.FEMALE) "Female" else "Male"
                _isAffectiveDialogActive.value = affectiveActive
                val info = if (affectiveActive) {
                    "Engine=GEMINI_LIVE_CONNECTED | Model=$modelUsed | Voice=$activeVoice ($genderLabel) | Affective=ON"
                } else {
                    "Engine=GEMINI_LIVE_FALLBACK | Model=$modelUsed | Voice=$activeVoice ($genderLabel) | Affective=LIMITED"
                }
                _activeVoiceDebugInfo.value = info
                Log.d(VOICE_DEBUG_TAG, info)
                Log.d(CONN_DEBUG_TAG, "GeminiLive CONNECTION_SUCCESS | $info")
                println("[$CONN_DEBUG_TAG] GeminiLive CONNECTION_SUCCESS | $info")
                if (!isMicPausedForRecognizer) {
                    startMicStreaming(ws)
                }
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

            // 1. Check for Input/Output Transcriptions & Server Turn Content
            val rootInputTranscript = root.optJSONObject("inputTranscription")?.optString("text", "")
            if (!rootInputTranscript.isNullOrBlank()) {
                _userInputTranscript.value = rootInputTranscript
                Log.d(TAG, "Gemini Input Transcription: $rootInputTranscript")
            }

            val rootTranscription = root.optJSONObject("outputTranscription")?.optString("text", "")
            if (!rootTranscription.isNullOrBlank()) {
                _liveTranscript.value = rootTranscription
                Log.d(TAG, "Gemini Output Transcription: $rootTranscription")
            }

            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val serverInputTranscript = serverContent.optJSONObject("inputTranscription")?.optString("text", "")
                if (!serverInputTranscript.isNullOrBlank()) {
                    _userInputTranscript.value = serverInputTranscript
                    Log.d(TAG, "Gemini Input Transcription: $serverInputTranscript")
                }

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
                                        if (_sessionState.value != GeminiLiveSessionState.SPEAKING) {
                                            val activeVoice = getActiveVoiceName()
                                            val genderLabel = if (_voiceGender.value == ArcVoiceGender.FEMALE) "Female" else "Male"
                                            val info = "Engine=GEMINI_LIVE_AUDIO | Voice=$activeVoice ($genderLabel) | 24kHz PCM"
                                            _activeVoiceDebugInfo.value = info
                                            Log.d(VOICE_DEBUG_TAG, info)
                                        }
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
                    resetOutputWaveform()
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

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord not initialized; keeping Gemini Live output stream active.")
                audioRecord?.release()
                audioRecord = null
                _sessionState.value = GeminiLiveSessionState.CONNECTED
                return
            }

            audioRecord?.startRecording()
            _sessionState.value = GeminiLiveSessionState.LISTENING
            PermissionUsageLogManager.logUsage(
                context = context,
                category = PermissionCategory.MICROPHONE,
                action = "Gemini Live Mic Stream",
                reason = "Full-duplex live voice session active",
                dedupeWindowMs = 15_000L
            )

            recordJob = scope.launch {
                val buffer = ByteArray(2048)
                while (isActive &&
                    this@GeminiLiveVoiceManager.webSocket === ws &&
                    _sessionState.value != GeminiLiveSessionState.DISCONNECTED &&
                    _sessionState.value != GeminiLiveSessionState.ERROR
                ) {
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
            Log.w(TAG, "RECORD_AUDIO permission not granted yet; keeping Gemini Live speaker stream active.")
            _sessionState.value = GeminiLiveSessionState.CONNECTED
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord unavailable (${e.message}); keeping Gemini Live speaker stream active.")
            _sessionState.value = GeminiLiveSessionState.CONNECTED
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
        val frame = outputWaveformAnalyzer.analyzePcm16(pcmBytes)
        _outputAudioLevel.value = frame.level
        _outputAudioBands.value = frame.bands
        try {
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}")
        }
    }

    private fun resetOutputWaveform() {
        val reset = outputWaveformAnalyzer.reset()
        _outputAudioLevel.value = reset.level
        _outputAudioBands.value = reset.bands
    }

    fun stopCurrentPlayback() {
        resetOutputWaveform()
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

    /**
     * Temporarily releases AudioRecord so Android SpeechRecognizer can capture mic audio
     * cleanly without hitting ERROR_AUDIO or ERROR_RECOGNIZER_BUSY.
     */
    fun pauseMicForExternalRecognizer() {
        isMicPausedForRecognizer = true
        try {
            recordJob?.cancel()
            recordJob = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _micVolumeLevel.value = 0f
            Log.d(CONN_DEBUG_TAG, "GeminiLive AudioRecord paused cleanly for SpeechRecognizer")
        } catch (e: Exception) {
            Log.w(CONN_DEBUG_TAG, "Error pausing GeminiLive AudioRecord: ${e.message}")
        }
    }

    /**
     * Resumes Gemini Live AudioRecord streaming after external SpeechRecognizer finishes.
     */
    fun resumeMicIfConnected() {
        isMicPausedForRecognizer = false
        val ws = webSocket
        if (ws != null && isSetupComplete && isStreamActive() && audioRecord == null) {
            Log.d(CONN_DEBUG_TAG, "GeminiLive AudioRecord resuming after SpeechRecognizer")
            startMicStreaming(ws)
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
            setupWatchdogJob?.cancel()
            setupWatchdogJob = null
            isSetupComplete = false
            synchronized(pendingSpeechTurns) {
                pendingSpeechTurns.clear()
            }
            releaseAudioHardware()
            val ws = webSocket
            webSocket = null
            ws?.close(1000, "User stopped session")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Live Session: ${e.message}")
        } finally {
            _micVolumeLevel.value = 0f
            resetOutputWaveform()
            _sessionState.value = GeminiLiveSessionState.DISCONNECTED
        }
    }
}
