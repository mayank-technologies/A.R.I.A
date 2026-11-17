package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.speech.RecognitionService
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.assistant.background.AriaBackgroundWakeService
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.assistant.security.AntiTheftSirenManager
import com.example.assistant.security.PermissionCategory
import com.example.assistant.security.PermissionUsageLogManager
import com.example.assistant.security.SirenGuardState
import com.example.ui.AriaViewModel
import com.example.ui.screens.AboutPrivacyDialog
import com.example.ui.screens.MandatoryPermissionsScreen
import com.example.ui.screens.RecentActivityDialog
import com.example.ui.screens.VoiceHudScreen
import com.example.ui.theme.AriaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrustAndTransparencyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        PermissionUsageLogManager.clearLogs(application)
        AriaBatterySaverManager.setManualOverride(true, application)
        AntiTheftSirenManager.disarmSecurity(application)

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

    @After
    fun tearDown() {
        AntiTheftSirenManager.disarmSecurity(application)
        PermissionUsageLogManager.clearLogs(application)
    }

    @Test
    fun `background listening notification is persistent and non-dismissible with required title`() {
        val notification = AriaBackgroundWakeService.buildPersistentListeningNotification(application)
        val title = notification.extras.getString(Notification.EXTRA_TITLE)

        assertEquals(AriaBackgroundWakeService.MANDATORY_NOTIFICATION_TITLE, title)
        assertEquals("ARC is listening in background", title)
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertTrue((notification.flags and Notification.FLAG_NO_CLEAR) != 0)
    }

    @Test
    fun `background wake service refuses to run silently without microphone permission`() {
        val controller = Robolectric.buildService(AriaBackgroundWakeService::class.java).create()
        controller.startCommand(0, 0)
        assertFalse(AriaBackgroundWakeService.isRunning.value)
        controller.destroy()
    }

    @Test
    fun `permission usage log manager records sensitive actions with reasons and clears cleanly`() {
        PermissionUsageLogManager.logUsage(
            context = application,
            category = PermissionCategory.LOCATION,
            action = "GPS Weather Lookup",
            reason = "Fetching local weather for HUD badge"
        )
        PermissionUsageLogManager.logUsage(
            context = application,
            category = PermissionCategory.CONTACTS,
            action = "Contact Lookup",
            reason = "Matching contact for call command"
        )
        PermissionUsageLogManager.logUsage(
            context = application,
            category = PermissionCategory.MICROPHONE,
            action = "Voice Command Listening",
            reason = "User tapped microphone button"
        )

        val entries = PermissionUsageLogManager.entries.value
        assertEquals(3, entries.size)
        assertEquals(PermissionCategory.MICROPHONE, entries[0].category)
        assertEquals(PermissionCategory.CONTACTS, entries[1].category)
        assertEquals(PermissionCategory.LOCATION, entries[2].category)

        PermissionUsageLogManager.clearLogs(application)
        assertTrue(PermissionUsageLogManager.entries.value.isEmpty())
    }

    @Test
    fun `voice hud shows auto-triggered ON-OFF status bar and toggles siren guard visibly`() {
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val viewModel = AriaViewModel(application)

        composeTestRule.setContent {
            AriaTheme {
                VoiceHudScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("auto_systems_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_bg_listening_status")
            .assertIsDisplayed()
            .assertTextContains("BG LISTEN: OFF")
        composeTestRule.onNodeWithTag("hud_antitheft_status")
            .assertIsDisplayed()
            .assertTextContains("SIREN GUARD: OFF")

        // Tap Anti-Theft status pill on Home HUD -> clearly updates ON/ARMED status
        composeTestRule.onNodeWithTag("hud_antitheft_status").performClick()
        composeTestRule.waitForIdle()

        assertEquals(SirenGuardState.ARMED, AntiTheftSirenManager.guardState.value)
        composeTestRule.onNodeWithTag("hud_antitheft_status")
            .assertIsDisplayed()
            .assertTextContains("SIREN GUARD: ARMED")
    }

    @Test
    fun `about privacy dialog discloses cloud voice APIs, no silent recording, siren behavior, and permissions`() {
        composeTestRule.setContent {
            AriaTheme {
                AboutPrivacyDialog(
                    isBackgroundWakeRunning = false,
                    isInAppWakeWordEnabled = true,
                    sirenGuardState = SirenGuardState.DISARMED,
                    onToggleBackgroundWake = {},
                    onToggleAntiTheftGuard = {},
                    onToggleInAppWakeWord = {},
                    onOpenRecentActivity = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("about_privacy_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Gemini Live", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("ElevenLabs", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Claude", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("chhup ke record", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("loud-siren alarm", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_open_recent_activity_button").assertIsDisplayed()
    }

    @Test
    fun `recent activity dialog renders logged permission entries`() {
        PermissionUsageLogManager.logUsage(
            context = application,
            category = PermissionCategory.LOCATION,
            action = "GPS Ride Pickup",
            reason = "Resolving user requested pickup address"
        )

        composeTestRule.setContent {
            AriaTheme {
                RecentActivityDialog(onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recent_activity_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("GPS Ride Pickup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolving user requested pickup address").assertIsDisplayed()
    }

    @Test
    fun `mandatory permissions screen displays clear one-line reasoning for permissions`() {
        composeTestRule.setContent {
            AriaTheme {
                MandatoryPermissionsScreen(
                    userName = "Boss",
                    onPermissionsCompleted = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("ARC PERMISSIONS SETUP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Microphone / Audio").assertIsDisplayed()
        composeTestRule.onNodeWithText("no hidden recording", substring = true).assertExists()
    }
}
