package com.example

import android.app.Application
import android.content.Context
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import com.example.claude.ClaudeMessagesTransport
import com.example.claude.ClaudeReasoningEngine
import com.example.claude.ClaudeReasoningResult
import com.example.geminilive.GeminiLiveFunctionRegistry
import com.example.geminilive.GeminiLiveVoiceManager
import com.example.geminilive.SpeechToneContext
import com.example.speech.AriaSpeechEngine
import com.example.ui.AriaViewModel
import com.example.ui.AssistantStatus
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MaleVoiceAndClaudeTest {


    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        ClaudeReasoningEngine.setTransportForTesting(null)
    }

    @Test
    fun `app launch and power lifecycle consistently configure male voices`() {
        // Seed legacy female voice ID to confirm AriaSpeechEngine migrates it away automatically
        val prefs = application.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("elevenlabs_voice_id", "21m00Tcm4TlvDq8ikWAM").commit()

        assertEquals("Charon", GeminiLiveVoiceManager.SELECTED_VOICE_NAME)

        val speechEngine = AriaSpeechEngine(application)
        val activeVoiceId = speechEngine.getActiveVoiceId()
        assertNotEquals("Legacy Rachel female ID must never be used", "21m00Tcm4TlvDq8ikWAM", activeVoiceId)
        assertNotEquals("Legacy Sarah female ID must never be used", "EXAVITQu4vr4xnSDxMaL", activeVoiceId)
        assertEquals("pNInz6obpgDQGcFmaJgB", activeVoiceId)
        speechEngine.destroy()

        val viewModel = AriaViewModel(application)
        // App launch defaults Gemini Live ON (male Charon voice)
        assertTrue(viewModel.isGeminiLiveMode.value)
        assertTrue(viewModel.isArcPoweredOn.value)

        // Power OFF triggers male farewell TTS and preserves Gemini Live default/preference (never forced false)
        viewModel.powerOffArc()
        assertFalse(viewModel.isArcPoweredOn.value)
        assertEquals(AssistantStatus.SLEEP, viewModel.assistantStatus.value)
        assertTrue(prefs.getBoolean("arc_gemini_live_mode", true))
        assertTrue(viewModel.isGeminiLiveMode.value)

        // Power ON keeps Gemini Live male voice active
        viewModel.powerOnArc()
        assertTrue(viewModel.isArcPoweredOn.value)
        assertTrue(viewModel.isGeminiLiveMode.value)
    }

    @Test
    fun `claude reasoning generates callContact tool call for Mummy ko call karo`() = runTest {
        var capturedApiKey = ""
        var capturedPayload: JSONObject? = null

        ClaudeReasoningEngine.setTransportForTesting(
            ClaudeMessagesTransport { apiKey, payloadJson ->
                capturedApiKey = apiKey
                capturedPayload = JSONObject(payloadJson)

                val mockAnthropicResponse = JSONObject().apply {
                    put("id", "msg_test_01")
                    put("type", "message")
                    put("role", "assistant")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "Ji Boss, Mummy ko call laga raha hu.")
                        })
                        put(JSONObject().apply {
                            put("type", "tool_use")
                            put("id", "toolu_01_call_mummy")
                            put("name", "callContact")
                            put("input", JSONObject().apply {
                                put("contactName", "Mummy")
                            })
                        })
                    })
                }
                Pair(200, mockAnthropicResponse.toString())
            }
        )

        val result = ClaudeReasoningEngine.reasonAndExecute(
            context = application,
            userTranscript = "Mummy ko call karo",
            userName = "Boss",
            executeTools = false,
            apiKeyOverride = "sk-ant-test-mock-key"
        )

        assertEquals("sk-ant-test-mock-key", capturedApiKey)
        val payload = capturedPayload!!
        assertEquals("Mummy ko call karo", payload.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertTrue(payload.getJSONArray("tools").length() >= 5)

        assertTrue(result is ClaudeReasoningResult.Success)
        val success = result as ClaudeReasoningResult.Success
        assertEquals(1, success.toolCalls.size)
        assertEquals("callContact", success.toolCalls[0].name)
        assertEquals("Mummy", success.toolCalls[0].arguments.getString("contactName"))
        assertTrue(success.spokenResponse.contains("Mummy ko call laga raha hu"))
    }

    @Test
    fun `claude reasoning triggers fallback when Anthropic API returns 500 or 404`() = runTest {
        var callCount = 0
        ClaudeReasoningEngine.setTransportForTesting(
            ClaudeMessagesTransport { _, _ ->
                callCount++
                Pair(500, """{"type":"error","error":{"message":"Internal server error"}}""")
            }
        )

        val result = ClaudeReasoningEngine.reasonAndExecute(
            context = application,
            userTranscript = "Aaj ka mausam kaisa hai",
            apiKeyOverride = "sk-ant-test-mock-key"
        )

        assertEquals(1, callCount)
        assertTrue(result is ClaudeReasoningResult.FallbackRequired)
        assertTrue((result as ClaudeReasoningResult.FallbackRequired).reason.contains("500"))

        // Verify 404 triggers secondary fallback model retry before falling back
        var retryModels = mutableListOf<String>()
        ClaudeReasoningEngine.setTransportForTesting(
            ClaudeMessagesTransport { _, payloadJson ->
                val model = JSONObject(payloadJson).getString("model")
                retryModels.add(model)
                if (retryModels.size == 1) {
                    Pair(404, """{"error":"model_not_found"}""")
                } else {
                    Pair(200, JSONObject().apply {
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Aaj mausam suhana hai, Boss.")
                            })
                        })
                    }.toString())
                }
            }
        )

        val retryResult = ClaudeReasoningEngine.reasonAndExecute(
            context = application,
            userTranscript = "Aaj ka mausam kaisa hai",
            apiKeyOverride = "sk-ant-test-mock-key"
        )

        assertEquals(2, retryModels.size)
        assertTrue(retryResult is ClaudeReasoningResult.Success)
        assertEquals("Aaj mausam suhana hai, Boss.", (retryResult as ClaudeReasoningResult.Success).spokenResponse)
    }

    @Test
    fun `gemini live registry includes consultClaudeBrain and falls back cleanly on missing query`() {
        val declarations = GeminiLiveFunctionRegistry.getToolsDeclarationJson()
            .getJSONObject(0)
            .getJSONArray("functionDeclarations")

        val names = (0 until declarations.length()).map {
            declarations.getJSONObject(it).getString("name")
        }
        assertTrue("consultClaudeBrain must be registered", "consultClaudeBrain" in names)
        assertTrue("callContact must be registered", "callContact" in names)

        val output = GeminiLiveFunctionRegistry.executeToolCall(
            context = application,
            callId = "call_empty",
            functionName = "consultClaudeBrain",
            args = JSONObject().put("userQuery", "")
        )
        assertFalse(output.success)
        assertTrue(output.responseData.getBoolean("useGeminiFallback"))
    }

    @Test
    fun `strict male TTS selector rejects Google female voice codes and prioritizes verified male voices`() {
        assertTrue(AriaSpeechEngine.isKnownFemaleVoiceName("hi-in-x-hia-local"))
        assertTrue(AriaSpeechEngine.isKnownFemaleVoiceName("hi-in-x-hid-local"))
        assertTrue(AriaSpeechEngine.isKnownFemaleVoiceName("en-in-x-enc-local"))
        assertTrue(AriaSpeechEngine.isKnownFemaleVoiceName("en-us-x-sfg-local"))

        assertFalse(AriaSpeechEngine.isKnownMaleVoiceName("hi-in-x-hia-local"))
        assertFalse(AriaSpeechEngine.isKnownMaleVoiceName("hi-in-x-hid-local"))
        assertFalse(AriaSpeechEngine.isKnownMaleVoiceName("en-in-x-enc-local"))

        assertTrue(AriaSpeechEngine.isKnownMaleVoiceName("hi-in-x-hie-local"))
        assertTrue(AriaSpeechEngine.isKnownMaleVoiceName("en-in-x-end-local"))
        assertTrue(AriaSpeechEngine.isKnownMaleVoiceName("en-us-x-iom-local"))

        val femaleHi = Voice("hi-in-x-hia-local", Locale("hi", "IN"), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
        val femaleEnIn = Voice("en-in-x-enc-local", Locale("en", "IN"), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
        val maleUs = Voice("en-us-x-iom-local", Locale.US, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
        val maleHi = Voice("hi-in-x-hie-local", Locale("hi", "IN"), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())

        // Even when female voices appear first, pickBestMaleVoice must select hi-in-x-hie-local
        val picked = AriaSpeechEngine.pickBestMaleVoice(listOf(femaleHi, femaleEnIn, maleUs, maleHi))
        assertNotNull(picked)
        assertEquals("hi-in-x-hie-local", picked?.name)

        // When only female voices exist, pickBestMaleVoice returns null so deep baritone pitch fallback applies
        val nonePicked = AriaSpeechEngine.pickBestMaleVoice(listOf(femaleHi, femaleEnIn))
        assertEquals(null, nonePicked)
    }

    @Test
    fun `gemini live setup enables affective dialog, proactivity, temperature and varies tone across contexts`() {
        val manager = GeminiLiveVoiceManager(application)
        val payload = manager.buildInitialSetupPayload(useFallbackModel = false, includeProactivity = true)
        val setup = payload.getJSONObject("setup")

        assertEquals(GeminiLiveVoiceManager.LIVE_MODEL, setup.getString("model"))
        assertTrue(setup.getBoolean("enableAffectiveDialog"))
        assertTrue(setup.getJSONObject("proactivity").getBoolean("proactiveAudio"))

        val genConfig = setup.getJSONObject("generationConfig")
        assertEquals(GeminiLiveVoiceManager.EXPRESSIVE_TEMPERATURE, genConfig.getDouble("temperature"), 0.001)
        val voiceName = genConfig
            .getJSONObject("speechConfig")
            .getJSONObject("voiceConfig")
            .getJSONObject("prebuiltVoiceConfig")
            .getString("voiceName")
        assertEquals("Charon", voiceName)

        val sysPrompt = setup
            .getJSONObject("systemInstruction")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        assertTrue(sysPrompt.contains("Speak with natural human warmth and emotional variation"))
        assertTrue(sysPrompt.contains("not like a text-to-speech engine"))

        // Context sentences classify into distinct delivery tones instead of robotic "Read aloud"
        assertEquals(SpeechToneContext.GREETING, GeminiLiveVoiceManager.detectToneContext("Good morning Boss! Kaise ho aaj?"))
        assertEquals(SpeechToneContext.FACTUAL, GeminiLiveVoiceManager.detectToneContext("Delhi ka current temperature 28°C aur sky clear hai."))
        assertEquals(SpeechToneContext.CASUAL, GeminiLiveVoiceManager.detectToneContext("Haha, that was awesome! Koi aur fun fact sunao."))
        assertEquals(SpeechToneContext.EMPATHETIC, GeminiLiveVoiceManager.detectToneContext("Sorry Boss, calendar sync failed — let me retry."))

        val greetingPrompt = GeminiLiveVoiceManager.buildExpressiveSpeechTurnPrompt("Good morning Boss!")
        val factualPrompt = GeminiLiveVoiceManager.buildExpressiveSpeechTurnPrompt("Delhi temperature is 28°C.")
        assertTrue(greetingPrompt.contains("warm, welcoming, upbeat"))
        assertTrue(factualPrompt.contains("calm, assured, measured"))
        assertFalse(greetingPrompt.contains("Read this status/response aloud"))
    }
}
