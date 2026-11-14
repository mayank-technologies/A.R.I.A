package com.example.claude

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.assistant.DeviceClockHelper
import com.example.geminilive.GeminiLiveFunctionRegistry
import com.example.geminilive.ToolExecutionOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result produced by the Claude (Anthropic) reasoning brain layer.
 */
sealed class ClaudeReasoningResult {
    data class Success(
        val spokenResponse: String,
        val toolCalls: List<ToolCallDecision> = emptyList(),
        val executedOutputs: List<ToolExecutionOutput> = emptyList()
    ) : ClaudeReasoningResult()

    data class FallbackRequired(val reason: String) : ClaudeReasoningResult()
}

data class ToolCallDecision(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

/**
 * Abstraction for Anthropic HTTP calls so production uses real OkHttp calls to
 * api.anthropic.com/v1/messages while local JVM tests can inject deterministic mock responses.
 */
fun interface ClaudeMessagesTransport {
    fun postMessages(apiKey: String, payloadJson: String): Pair<Int, String>
}

/**
 * ClaudeReasoningEngine:
 * Acts as ARC's "text-reasoning & tool-decision brain" paired with Gemini Live (which handles voice I/O).
 *
 * Role Split:
 * - Gemini Live: Real-time audio streaming (hearing user speech -> inputTranscription, speaking reply in Charon).
 * - Claude (Anthropic API): Complex command understanding, multi-step reasoning, tool selection
 *   (contact call, app launch, WhatsApp, hardware control, Q&A response generation).
 * - Automatic Fallback: If ANTHROPIC_API_KEY is missing or Anthropic API fails, returns FallbackRequired
 *   so Gemini Live's own reasoning / GeminiClient handles the request without breaking the pipeline.
 */
object ClaudeReasoningEngine {

    private const val TAG = "ARC_CLAUDE_BRAIN"
    private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val PRIMARY_CLAUDE_MODEL = "claude-sonnet-5-5"
    private const val FALLBACK_CLAUDE_MODEL = "claude-sonnet-4-5-20250929"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val defaultTransport = ClaudeMessagesTransport { apiKey, payloadJson ->
        val request = Request.Builder()
            .url(ANTHROPIC_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(payloadJson.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            Pair(response.code, bodyStr)
        }
    }

    @Volatile
    private var transportOverride: ClaudeMessagesTransport? = null

    internal fun setTransportForTesting(transport: ClaudeMessagesTransport?) {
        transportOverride = transport
    }

    /**
     * Reads ANTHROPIC_API_KEY strictly from BuildConfig (populated from AI Studio Secrets / .env).
     * Never hardcodes any API key in source code.
     */
    fun getApiKey(): String = BuildConfig.ANTHROPIC_API_KEY.trim()

    fun isPlaceholderKey(key: String): Boolean {
        val v = key.trim()
        return v.isBlank() ||
            v.equals("your_key_here", ignoreCase = true) ||
            v.equals("MY_ANTHROPIC_API_KEY", ignoreCase = true) ||
            v.startsWith("YOUR_", ignoreCase = true)
    }

    fun hasValidApiKey(): Boolean = !isPlaceholderKey(getApiKey())

    /**
     * Builds the Anthropic Messages API tool schema array matching ARC's whitelisted Android tools.
     */
    fun buildAnthropicToolsJson(): JSONArray {
        val tools = JSONArray()

        fun addTool(name: String, description: String, props: JSONObject, required: List<String> = emptyList()) {
            val schema = JSONObject().apply {
                put("type", "object")
                put("properties", props)
                if (required.isNotEmpty()) {
                    put("required", JSONArray(required))
                }
            }
            tools.put(JSONObject().apply {
                put("name", name)
                put("description", description)
                put("input_schema", schema)
            })
        }

        addTool(
            name = "callContact",
            description = "Searches device address book for a contact by name (e.g., Mummy, Mom, Rahul) and dials them.",
            props = JSONObject().apply {
                put("contactName", prop("string", "Full or partial name of the contact to call."))
            },
            required = listOf("contactName")
        )

        addTool(
            name = "makeCall",
            description = "Dials an explicit telephone number directly via Android Phone Dialer.",
            props = JSONObject().apply {
                put("phoneNumber", prop("string", "Telephone digits to dial."))
                put("recipientName", prop("string", "Optional label of the person being called."))
            },
            required = listOf("phoneNumber")
        )

        addTool(
            name = "openWhatsApp",
            description = "Opens WhatsApp to message or initiate chat/call setup for a contact.",
            props = JSONObject().apply {
                put("recipientName", prop("string", "Contact name on WhatsApp."))
                put("messageText", prop("string", "Optional pre-filled text message."))
                put("isVideoCall", prop("boolean", "True if video call requested, else false."))
            },
            required = listOf("recipientName")
        )

        addTool(
            name = "openApp",
            description = "Launches an installed Android application or web fallback (YouTube, Chrome, Spotify, WhatsApp, Flipkart, etc.).",
            props = JSONObject().apply {
                put("appName", prop("string", "Name of the app to launch."))
            },
            required = listOf("appName")
        )

        addTool(
            name = "openUrl",
            description = "Opens a web URL or web search query in the browser.",
            props = JSONObject().apply {
                put("url", prop("string", "URL or search URL to open."))
            },
            required = listOf("url")
        )

        addTool(
            name = "getCurrentDateTime",
            description = "Returns the exact current local device time, date, and timezone.",
            props = JSONObject()
        )

        addTool(
            name = "controlHardware",
            description = "Controls device hardware: flashlight, wifi, bluetooth, volume, brightness, or battery status.",
            props = JSONObject().apply {
                put(
                    "action",
                    prop(
                        "string",
                        "One of: flashlight_on, flashlight_off, wifi_toggle, bluetooth_toggle, volume_up, volume_down, volume_mute, brightness_up, brightness_down, battery_status"
                    )
                )
            },
            required = listOf("action")
        )

        addTool(
            name = "toggleSecurityGuard",
            description = "Arms or disarms ARC Anti-Theft Siren Guard.",
            props = JSONObject().apply {
                put("armed", prop("boolean", "True to arm security guard, false to disarm."))
            },
            required = listOf("armed")
        )

        addTool(
            name = "readWhatsAppNotifications",
            description = "Reads unread captured WhatsApp notifications.",
            props = JSONObject()
        )

        addTool(
            name = "readScreenVision",
            description = "Reads current visible text on screen using Accessibility + ML Kit OCR.",
            props = JSONObject()
        )

        addTool(
            name = "openGoogleMapsRoute",
            description = "Builds and opens driving directions between origin and destination in Google Maps or browser (e.g. 'Delhi se Mathura ka route dikhao').",
            props = JSONObject().apply {
                put("origin", prop("string", "Origin city/location (optional; omit if user didn't specify origin)."))
                put("destination", prop("string", "Destination city/location (e.g. 'Mathura')."))
                put("travelMode", prop("string", "Travel mode such as 'driving', 'walking', 'transit', 'bicycling'."))
            },
            required = listOf("destination")
        )

        addTool(
            name = "assistRideBooking",
            description = "Assists ride booking on Uber/Ola/Rapido. Leave vehicleType empty if user did not choose Bike, Car 4-seater, or 6-seater yet so ARC asks first. Never confirms booking automatically.",
            props = JSONObject().apply {
                put("destination", prop("string", "Dropoff destination address/city."))
                put("vehicleType", prop("string", "One of: bike, car_4_seater, car_6_seater, auto. Omit/empty if not yet chosen by user."))
                put("preferredApp", prop("string", "Optional ride app: uber, ola, or rapido."))
            },
            required = listOf("destination")
        )

        addTool(
            name = "assistShoppingSearch",
            description = "Opens Flipkart/Flipkart Minutes/Blinkit/Zepto/Amazon/Instamart with product search deep-link. NEVER places orders or processes payments; user adds to cart and pays.",
            props = JSONObject().apply {
                put("appName", prop("string", "Shopping app name e.g. Flipkart, Flipkart Minutes, Blinkit, Zepto, Amazon, Instamart."))
                put("productQuery", prop("string", "Product search text e.g. '500g sugar'."))
            },
            required = listOf("appName", "productQuery")
        )

        return tools
    }

    private fun prop(type: String, description: String): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("description", description)
        }
    }

    /**
     * Reasons over userTranscript using Anthropic Messages API, decides any tool calls,
     * executes them on Android via GeminiLiveFunctionRegistry, and returns a concise spoken reply
     * ready for Gemini Live (Charon male voice) to speak aloud.
     */
    suspend fun reasonAndExecute(
        context: Context,
        userTranscript: String,
        userName: String = "Boss",
        executeTools: Boolean = true,
        apiKeyOverride: String? = null
    ): ClaudeReasoningResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride?.trim() ?: getApiKey()
        if (isPlaceholderKey(apiKey)) {
            return@withContext ClaudeReasoningResult.FallbackRequired("ANTHROPIC_API_KEY missing or placeholder.")
        }

        val clock = DeviceClockHelper.getCurrentDeviceClockInfo(
            context = context,
            logDebug = false,
            source = "ClaudeReasoningEngine"
        )
        val displayName = userName.ifBlank { "Boss" }

        val systemPrompt = """
            You are ARC's reasoning brain (partnering with Gemini Live male voice Charon).
            User name: $displayName. Local device time: ${clock.formattedTime}, ${clock.formattedDate} (${clock.timeZoneId}).
            
            ROLE & STYLE:
            1. Analyze the user's command deeply and decide if an Android device tool should be called.
            2. Match the user's language (Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, or Urdu).
            3. Always use warm, natural masculine Hindi/Hinglish grammar (e.g., "kar raha hu", "call laga raha hu", "bata raha hu").
            4. Keep responses concise (1-3 sentences) because they will be spoken aloud by Gemini Live's Charon male voice.
            5. SAFETY & HONESTY FOR RIDES / SHOPPING: For rides (`assistRideBooking`), if vehicle isn't specified, ask "Bike, car 4-seater, ya 6-seater?". NEVER say "booking confirm ho gayi" or "order ho gaya". Always state clearly that the user must tap Book/Add-to-Cart and complete final confirmation and payment themselves.
        """.trimIndent()

        val payloadPrimary = buildRequestPayload(PRIMARY_CLAUDE_MODEL, systemPrompt, userTranscript)
        val transport = transportOverride ?: defaultTransport

        try {
            var (statusCode, rawBody) = transport.postMessages(apiKey, payloadPrimary)

            // Retry once on fallback Claude Sonnet model ID if primary ID returns 404/model error
            if (statusCode == 404) {
                Log.w(TAG, "Primary Claude model returned 404, retrying with $FALLBACK_CLAUDE_MODEL")
                val payloadFallback = buildRequestPayload(FALLBACK_CLAUDE_MODEL, systemPrompt, userTranscript)
                val retry = transport.postMessages(apiKey, payloadFallback)
                statusCode = retry.first
                rawBody = retry.second
            }

            if (statusCode !in 200..299 || rawBody.isBlank()) {
                Log.w(TAG, "Anthropic API returned HTTP $statusCode: ${rawBody.take(200)}")
                return@withContext ClaudeReasoningResult.FallbackRequired("Anthropic HTTP $statusCode")
            }

            val responseJson = JSONObject(rawBody)
            val contentArray = responseJson.optJSONArray("content") ?: JSONArray()
            val textBuilder = StringBuilder()
            val decidedTools = mutableListOf<ToolCallDecision>()
            val executedOutputs = mutableListOf<ToolExecutionOutput>()

            for (i in 0 until contentArray.length()) {
                val block = contentArray.optJSONObject(i) ?: continue
                when (block.optString("type")) {
                    "text" -> {
                        val t = block.optString("text", "").trim()
                        if (t.isNotBlank()) {
                            if (textBuilder.isNotEmpty()) textBuilder.append(" ")
                            textBuilder.append(t)
                        }
                    }
                    "tool_use" -> {
                        val toolId = block.optString("id", "claude_tool_$i")
                        val toolName = block.optString("name", "")
                        val toolInput = block.optJSONObject("input") ?: JSONObject()
                        if (toolName.isNotBlank()) {
                            val decision = ToolCallDecision(toolId, toolName, toolInput)
                            decidedTools.add(decision)
                            if (executeTools) {
                                val output = GeminiLiveFunctionRegistry.executeToolCall(
                                    context = context,
                                    callId = toolId,
                                    functionName = toolName,
                                    args = toolInput
                                )
                                executedOutputs.add(output)
                            }
                        }
                    }
                }
            }

            val spokenSummary = buildString {
                val toolMsg = executedOutputs.joinToString(" ") { it.verbalConfirmation }.trim()
                val textMsg = textBuilder.toString().trim()
                when {
                    toolMsg.isNotBlank() && textMsg.isNotBlank() -> append("$textMsg $toolMsg")
                    toolMsg.isNotBlank() -> append(toolMsg)
                    textMsg.isNotBlank() -> append(textMsg)
                    else -> append("Ho gaya, $displayName.")
                }
            }

            Log.d(TAG, "Claude reasoning complete: tools=${decidedTools.map { it.name }}, reply='$spokenSummary'")
            return@withContext ClaudeReasoningResult.Success(
                spokenResponse = spokenSummary,
                toolCalls = decidedTools,
                executedOutputs = executedOutputs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Claude API exception, falling back to Gemini Live: ${e.message}", e)
            return@withContext ClaudeReasoningResult.FallbackRequired(e.localizedMessage ?: "Claude network error")
        }
    }

    private fun buildRequestPayload(modelId: String, systemPrompt: String, userTranscript: String): String {
        return JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 512)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userTranscript)
                })
            })
            put("tools", buildAnthropicToolsJson())
        }.toString()
    }
}
