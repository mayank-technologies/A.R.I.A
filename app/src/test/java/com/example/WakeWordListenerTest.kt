package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.speech.RecognitionService
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.speech.AriaSpeechEngine
import com.example.speech.WakeWordState
import com.example.ui.AriaViewModel
import com.example.ui.AssistantStatus
import com.example.ui.screens.VoiceHudScreen
import com.example.ui.theme.AriaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class WakeWordListenerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AriaBatterySaverManager.setManualOverride(false, application)

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
    fun `parseWakeWord detects JARVIS and Hey ARC accurately without false positives on arcade`() {
        val jarvisOnly = AriaSpeechEngine.parseWakeWord("JARVIS")
        assertTrue(jarvisOnly.hasWakeWord)
        assertTrue(jarvisOnly.isOnlyWakeWord)
        assertEquals("jarvis", jarvisOnly.matchedWakeWord)

        val heyJarvisCmd = AriaSpeechEngine.parseWakeWord("Hey Jarvis, what is the weather today")
        assertTrue(heyJarvisCmd.hasWakeWord)
        assertFalse(heyJarvisCmd.isOnlyWakeWord)
        assertEquals("what is the weather today", heyJarvisCmd.command)

        val heyArcCmd = AriaSpeechEngine.parseWakeWord("Hey ARC open YouTube")
        assertTrue(heyArcCmd.hasWakeWord)
        assertFalse(heyArcCmd.isOnlyWakeWord)
        assertEquals("open YouTube", heyArcCmd.command)

        // Ensure substring inside normal words does NOT falsely wake ARC
        val falsePositive = AriaSpeechEngine.parseWakeWord("Please search for arcade games")
        assertFalse(falsePositive.hasWakeWord)
        assertEquals("Please search for arcade games", falsePositive.command)
    }

    @Test
    fun `speech engine partial and final wake transcripts trigger callback and ignore chatter`() {
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val engine = AriaSpeechEngine(application)

        var triggeredCount = 0
        var lastParsed: AriaSpeechEngine.Companion.WakeWordParseResult? = null
        engine.setOnWakeWordDetectedListener { parsed, _ ->
            triggeredCount++
            lastParsed = parsed
        }

        // Enable wake mode explicitly for testing speech transcript processing
        engine.setWakeWordModeEnabled(true)

        // Ambient chatter should NOT trigger wake word
        engine.processRecognizedSpeech("what is on TV tonight", isPartial = false)
        assertEquals(0, triggeredCount)

        // Partial wake phrase triggers instant TRIGGERED state & live partial transcript feedback
        engine.processRecognizedSpeech("Hey Jarvis", isPartial = true)
        assertEquals(WakeWordState.TRIGGERED, engine.wakeWordState.value)
        assertEquals("Hey Jarvis", engine.partialTranscript.value)

        // Final speech with trailing command delivers full parsed command once
        engine.processRecognizedSpeech("Hey Jarvis what is the time", isPartial = false)
        assertEquals(1, triggeredCount)
        assertTrue(lastParsed?.hasWakeWord == true)
        assertEquals("what is the time", lastParsed?.command)

        engine.destroy()
    }

    @Test
    fun `voice hud shows live listening indicator and wake word pill when triggered`() {
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val viewModel = AriaViewModel(application)
        viewModel.onMicPermissionUpdated()
        // Disable animations via battery saver override so Robolectric UI tree settles cleanly
        AriaBatterySaverManager.setManualOverride(true, application)

        composeTestRule.setContent {
            AriaTheme {
                VoiceHudScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("wake_word_hud_pill").assertIsDisplayed()

        // Simulate bare JARVIS wake-word detection opening active microphone listening
        val wakeOnly = AriaSpeechEngine.parseWakeWord("Hey ARC")
        composeTestRule.runOnUiThread {
            viewModel.handleWakeWordTrigger(wakeOnly, "Hey ARC")
        }
        composeTestRule.waitForIdle()

        assertTrue(viewModel.wakeFlashActive.value)
        assertEquals(AssistantStatus.LISTENING, viewModel.assistantStatus.value)
        composeTestRule.onNodeWithTag("listening_pulse_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("ARC ACTIVATED").assertIsDisplayed()
    }
}
