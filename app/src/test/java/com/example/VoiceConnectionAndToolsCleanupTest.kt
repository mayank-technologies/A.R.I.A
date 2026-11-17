package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.api.GeminiClient
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.geminilive.GeminiLiveSessionState
import com.example.geminilive.GeminiLiveVoiceManager
import com.example.speech.AriaSpeechEngine
import com.example.speech.SpeechErrorInfo
import com.example.speech.WakeWordState
import com.example.ui.AriaViewModel
import com.example.ui.AssistantStatus
import com.example.ui.screens.QuickToolsScreen
import com.example.ui.theme.AriaTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceConnectionAndToolsCleanupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AriaBatterySaverManager.setManualOverride(false, application)
    }

    private fun registerMockRecognitionService() {
        val recognitionIntent = Intent(RecognitionService.SERVICE_INTERFACE)
        val resolveInfo = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = "com.google.android.googlequicksearchbox"
                name = "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
            }
        }
        Shadows.shadowOf(application.packageManager)
            .addResolveInfoForIntent(recognitionIntent, resolveInfo)
    }

    @Test
    fun `startListening surfaces non-silent error when RECORD_AUDIO permission is missing`() {
        val engine = AriaSpeechEngine(application)
        var capturedError: SpeechErrorInfo? = null
        var capturedResult: String? = null
        engine.setOnSpeechErrorListener { capturedError = it }

        engine.startListening { capturedResult = it }

        assertEquals(WakeWordState.PERMISSION_REQUIRED, engine.wakeWordState.value)
        assertNotNull("Expected non-silent permission error", capturedError)
        assertTrue(capturedError!!.userMessage.contains("Microphone permission needed", ignoreCase = true))
        assertEquals("", capturedResult)
        engine.destroy()
    }

    @Test
    fun `ViewModel startListening sets visible uiErrorMessage when mic permission denied`() {
        val viewModel = AriaViewModel(application)
        viewModel.startListening()

        assertEquals(AssistantStatus.IDLE, viewModel.assistantStatus.value)
        val err = viewModel.uiErrorMessage.value
        assertNotNull("Expected non-silent HUD error banner when mic permission missing", err)
        assertTrue(err!!.contains("Microphone permission needed", ignoreCase = true))
    }

    @Test
    fun `describeSpeechError maps ERROR_AUDIO and ERROR_NETWORK to clear user messages`() {
        val audioErr = AriaSpeechEngine.describeSpeechError(SpeechRecognizer.ERROR_AUDIO)
        assertEquals("ERROR_AUDIO", audioErr.codeName)
        assertTrue(audioErr.userMessage.contains("audio", ignoreCase = true))

        val netErr = AriaSpeechEngine.describeSpeechError(SpeechRecognizer.ERROR_NETWORK)
        assertEquals("ERROR_NETWORK", netErr.codeName)
        assertTrue(netErr.userMessage.contains("internet", ignoreCase = true))
    }

    @Test
    fun `startListening succeeds past permission gate when permission and service are available`() {
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        registerMockRecognitionService()

        val viewModel = AriaViewModel(application)
        viewModel.startListening()

        assertEquals(AssistantStatus.LISTENING, viewModel.assistantStatus.value)
    }

    @Test
    fun `API key audit flags placeholder keys and Gemini Live surfaces non-silent connectionError`() = runTest {
        val placeholderAudit = GeminiClient.inspectApiKey("GEMINI_API_KEY", "your_key_here")
        assertFalse(placeholderAudit.isValid)
        assertTrue(placeholderAudit.isPlaceholder)

        val validAudit = GeminiClient.inspectApiKey("GEMINI_API_KEY", "AIzaSyTest1234567890")
        assertTrue(validAudit.isValid)
        assertFalse(validAudit.isPlaceholder)

        val textFailure = GeminiClient.queryAriaAiDetailed("Hello ARC", apiKeyOverride = "your_key_here")
        assertFalse(textFailure.isSuccess)
        assertTrue(textFailure.replyText.contains("GEMINI_API_KEY", ignoreCase = true))

        if (!GeminiLiveVoiceManager(application).hasValidApiKey()) {
            val liveManager = GeminiLiveVoiceManager(application)
            liveManager.startLiveSession()
            assertEquals(GeminiLiveSessionState.ERROR, liveManager.sessionState.value)
            assertNotNull(liveManager.connectionError.value)
            assertTrue(liveManager.connectionError.value!!.contains("GEMINI_API_KEY", ignoreCase = true))
        }
    }

    @Test
    fun `QuickToolsScreen removes Python Hub, Wikipedia, and Browser Shortcuts while keeping Weather and Calculator`() {
        val viewModel = AriaViewModel(application)

        composeTestRule.setContent {
            AriaTheme {
                QuickToolsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Scroll LazyColumn through remaining core Tools cards and confirm they are displayed
        val listNode = composeTestRule.onNodeWithTag("quick_tools_list")
        listNode.performScrollToNode(hasText("Live Weather Forecast", substring = true))
        composeTestRule.onNodeWithText("Live Weather Forecast", substring = true).assertIsDisplayed()

        listNode.performScrollToNode(hasText("ARC Math Calculator", substring = true))
        composeTestRule.onNodeWithText("ARC Math Calculator", substring = true).assertIsDisplayed()

        listNode.performScrollToNode(hasText("ARC Quick Notepad", substring = true))
        composeTestRule.onNodeWithText("ARC Quick Notepad", substring = true).assertIsDisplayed()

        // Confirm the 3 removed cards are absent anywhere in the LazyColumn
        assertTrue(
            composeTestRule.onAllNodesWithText("Python & Flutter Developer Hub", substring = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("Wikipedia Instant Knowledge", substring = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("Browser & App Shortcuts", substring = true)
                .fetchSemanticsNodes().isEmpty()
        )
    }
}
