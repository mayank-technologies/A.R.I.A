package com.example

import android.app.Application
import android.content.Context
import android.speech.tts.Voice
import android.text.InputType
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.assistant.AriaCommandProcessor
import com.example.assistant.AriaCommandResult
import com.example.assistant.accessibility.FormFieldType
import com.example.assistant.accessibility.PersonalProfileInfo
import com.example.assistant.accessibility.PersonalProfileManager
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.assistant.memory.LongTermMemoryExtractor
import com.example.assistant.notification.GeneralNotificationManager
import com.example.data.AriaDatabase
import com.example.data.MemoryRepository
import com.example.geminilive.GeminiLiveVoiceManager
import com.example.speech.ArcVoiceGender
import com.example.speech.AriaSpeechEngine
import com.example.speech.VoicePreferencesManager
import com.example.ui.AriaViewModel
import com.example.ui.components.SpeechAudioVisualizer
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AriaTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewFeaturesSuiteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var inMemoryDb: AriaDatabase

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        GeneralNotificationManager.resetPreferencesForTesting(application)
        AriaBatterySaverManager.setManualOverride(true, application)
        inMemoryDb = Room.inMemoryDatabaseBuilder(application, AriaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun `voice gender toggle switches Gemini Live Aoede and ElevenLabs Rachel and persists across restarts`() {
        assertEquals(ArcVoiceGender.MALE, VoicePreferencesManager.getVoiceGender(application))

        val speechEngine = AriaSpeechEngine(application)
        val liveManager = GeminiLiveVoiceManager(application)

        // Default Male assertions
        assertEquals("Charon", VoicePreferencesManager.geminiVoiceNameFor(liveManager.voiceGender.value))
        assertEquals("pNInz6obpgDQGcFmaJgB", speechEngine.getActiveVoiceId())

        // Switch to Female live
        VoicePreferencesManager.setVoiceGender(application, ArcVoiceGender.FEMALE)
        speechEngine.setVoiceGender(ArcVoiceGender.FEMALE)
        liveManager.setVoiceGender(ArcVoiceGender.FEMALE)

        assertEquals(ArcVoiceGender.FEMALE, liveManager.voiceGender.value)
        assertEquals("21m00Tcm4TlvDq8ikWAM", speechEngine.getActiveVoiceId())

        val setupPayload = liveManager.buildInitialSetupPayload(useFallbackModel = false, includeProactivity = true)
        val setup = setupPayload.getJSONObject("setup")
        assertTrue(setup.getBoolean("enableAffectiveDialog"))
        val genConfig = setup.getJSONObject("generationConfig")
        assertEquals(GeminiLiveVoiceManager.EXPRESSIVE_TEMPERATURE, genConfig.getDouble("temperature"), 0.001)
        val voiceName = genConfig
            .getJSONObject("speechConfig")
            .getJSONObject("voiceConfig")
            .getJSONObject("prebuiltVoiceConfig")
            .getString("voiceName")
        assertEquals("Aoede", voiceName)

        // Verify Android TTS female voice picker prioritizes female Google voices
        val maleHi = Voice("hi-in-x-hie-local", Locale("hi", "IN"), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
        val femaleHi = Voice("hi-in-x-hia-local", Locale("hi", "IN"), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
        val pickedFemale = AriaSpeechEngine.pickBestFemaleVoice(listOf(maleHi, femaleHi))
        assertNotNull(pickedFemale)
        assertEquals("hi-in-x-hia-local", pickedFemale?.name)

        speechEngine.destroy()

        // Fresh instances simulate app restart -> Female choice remains persisted
        val restartedSpeech = AriaSpeechEngine(application)
        val restartedLive = GeminiLiveVoiceManager(application)
        assertEquals(ArcVoiceGender.FEMALE, restartedLive.voiceGender.value)
        assertEquals("21m00Tcm4TlvDq8ikWAM", restartedSpeech.getActiveVoiceId())
        restartedSpeech.destroy()
    }

    @Test
    fun `general notifications capture multi-app messages respect per-app toggles and respond to Notifications padho`() = runTest {
        GeneralNotificationManager.recordIncomingNotification(
            context = application,
            packageName = "com.google.android.apps.messaging",
            sender = "Maa",
            message = "Ghar kab aaoge?",
            appNameOverride = "Messages (SMS)"
        )
        GeneralNotificationManager.recordIncomingNotification(
            context = application,
            packageName = "com.instagram.android",
            sender = "Rohan",
            message = "Sent you a reel",
            appNameOverride = "Instagram"
        )
        GeneralNotificationManager.recordIncomingNotification(
            context = application,
            packageName = "com.google.android.gm",
            sender = "HR Team",
            message = "Interview confirmed at 11 AM",
            appNameOverride = "Gmail"
        )

        val summaryAll = GeneralNotificationManager.getUnreadNotificationsSummary(application)
        assertTrue(summaryAll.contains("Messages (SMS)"))
        assertTrue(summaryAll.contains("Instagram"))
        assertTrue(summaryAll.contains("Gmail"))

        // Toggle Instagram OFF
        GeneralNotificationManager.setAppEnabled(application, "com.instagram.android", "Instagram", false)
        assertFalse(GeneralNotificationManager.isAppEnabled(application, "com.instagram.android"))

        val summaryFiltered = GeneralNotificationManager.getUnreadNotificationsSummary(application)
        assertTrue(summaryFiltered.contains("Messages (SMS)"))
        assertTrue(summaryFiltered.contains("Gmail"))
        assertFalse(summaryFiltered.contains("Instagram"))

        // Command processor route for "Notifications padho"
        val processor = AriaCommandProcessor(application, inMemoryDb.ariaDao())
        val result = processor.processCommand("Notifications padho", "Boss")
        assertTrue(result is AriaCommandResult.TextResponse)
        val resp = (result as AriaCommandResult.TextResponse).response
        assertTrue(resp.contains("Messages (SMS)"))
        assertTrue(resp.contains("HR Team"))
        assertFalse(resp.contains("Sent you a reel"))
    }

    @Test
    fun `long term memory persists preferences in Room and blocks sensitive PIN or password auto-save`() = runTest {
        val repo = MemoryRepository(inMemoryDb.ariaDao())

        val chaiExtraction = LongTermMemoryExtractor.analyzeTranscript("Mujhe adrak wali chai pasand hai")
        assertTrue(chaiExtraction.safeFactsToAutoSave.isNotEmpty())
        assertNull(chaiExtraction.pendingSensitiveConfirmation)
        chaiExtraction.safeFactsToAutoSave.forEach {
            repo.saveOrUpdateFact(it.factKey, it.factValue, it.category, "Auto")
        }

        val officeExtraction = LongTermMemoryExtractor.analyzeTranscript("Mera office 9 baje start hota hai")
        assertTrue(officeExtraction.safeFactsToAutoSave.isNotEmpty())
        assertNull(officeExtraction.pendingSensitiveConfirmation)
        officeExtraction.safeFactsToAutoSave.forEach {
            repo.saveOrUpdateFact(it.factKey, it.factValue, it.category, "Auto")
        }

        val savedFacts = repo.getAllMemoriesOnce()
        assertTrue(savedFacts.any { it.factValue.contains("chai", ignoreCase = true) })
        assertTrue(savedFacts.any { it.factValue.contains("9 baje", ignoreCase = true) })

        val promptContext = repo.buildPromptMemoryContext()
        assertTrue(promptContext.contains("SAVED LONG-TERM USER MEMORY"))
        assertTrue(promptContext.contains("chai", ignoreCase = true))

        // Privacy Guard: sensitive ATM PIN / password must NEVER auto-save
        val sensitiveExtraction = LongTermMemoryExtractor.analyzeTranscript("Remember my ATM PIN is 4829")
        assertTrue(sensitiveExtraction.safeFactsToAutoSave.isEmpty())
        assertNotNull(sensitiveExtraction.pendingSensitiveConfirmation)
        assertTrue(sensitiveExtraction.pendingSensitiveConfirmation!!.isSensitive)

        val factsAfterSensitiveAttempt = repo.getAllMemoriesOnce()
        assertFalse(factsAfterSensitiveAttempt.any { it.factValue.contains("4829") })

        // Test individual delete & clear all
        val firstId = factsAfterSensitiveAttempt.first().id
        repo.deleteFact(firstId)
        assertEquals(factsAfterSensitiveAttempt.size - 1, repo.getAllMemoriesOnce().size)

        repo.clearAllFacts()
        assertTrue(repo.getAllMemoriesOnce().isEmpty())
    }

    @Test
    fun `SpeechAudioVisualizer displays Listening waveform and Speaking output waveform`() {
        composeTestRule.setContent {
            AriaTheme {
                SpeechAudioVisualizer(
                    isSpeaking = false,
                    isListening = true,
                    audioLevel = 0.65f,
                    audioBands = emptyList(),
                    disableAnimations = true
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speech_audio_visualizer").assertIsDisplayed()
        composeTestRule.onNodeWithText("VOICE INPUT · LISTENING").assertIsDisplayed()
    }

    @Test
    fun `PersonalProfileManager and form classifier fill standard fields and skip passwords and unknown inputs`() = runTest {
        val profile = PersonalProfileInfo(
            fullName = "Aarav Sharma",
            email = "aarav@example.com",
            phone = "+91 9876543210",
            address = "42 MG Road, Delhi"
        )
        PersonalProfileManager.saveProfile(application, profile)
        val loaded = PersonalProfileManager.getProfile(application)
        assertEquals("Aarav Sharma", loaded.fullName)
        assertEquals("aarav@example.com", loaded.email)
        assertEquals("+91 9876543210", loaded.phone)
        assertEquals("42 MG Road, Delhi", loaded.address)

        assertEquals(FormFieldType.NAME, PersonalProfileManager.classifyFormField("Full Name", null, "et_name"))
        assertEquals(FormFieldType.EMAIL, PersonalProfileManager.classifyFormField("Email Address", null, "et_email"))
        assertEquals(FormFieldType.PHONE, PersonalProfileManager.classifyFormField("Mobile Number", null, "et_phone"))
        assertEquals(FormFieldType.ADDRESS, PersonalProfileManager.classifyFormField("Street Address", null, "et_address"))

        // Sensitive & ambiguous inputs MUST be skipped
        assertEquals(
            FormFieldType.PASSWORD_OR_SENSITIVE,
            PersonalProfileManager.classifyFormField("Enter Password", null, "et_pass", isPassword = true)
        )
        assertEquals(
            FormFieldType.PASSWORD_OR_SENSITIVE,
            PersonalProfileManager.classifyFormField("6-digit OTP", null, "et_otp")
        )
        assertEquals(
            FormFieldType.PASSWORD_OR_SENSITIVE,
            PersonalProfileManager.classifyFormField("CVV", null, "et_cvv", inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        )
        assertEquals(
            FormFieldType.UNKNOWN,
            PersonalProfileManager.classifyFormField("Search products", null, "search_bar")
        )

        val processor = AriaCommandProcessor(application, inMemoryDb.ariaDao())
        val cmdRes = processor.processCommand("Form fill karo", "Aarav") as AriaCommandResult.TextResponse
        assertEquals("FORM_AUTOFILL", cmdRes.category)
        assertTrue(cmdRes.response.contains("Accessibility", ignoreCase = true))
    }

    @Test
    fun `SettingsScreen renders all 6 sections and toggles voice gender cleanly`() {
        val viewModel = AriaViewModel(application)
        composeTestRule.setContent {
            AriaTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_header_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_section_voice").assertIsDisplayed()

        // Toggle Voice to Female via Settings Switch
        assertEquals(ArcVoiceGender.MALE, viewModel.voiceGender.value)
        composeTestRule.onNodeWithTag("settings_voice_gender_switch").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ArcVoiceGender.FEMALE, viewModel.voiceGender.value)

        // Scroll through remaining sections inside LazyColumn
        val listNode = composeTestRule.onNodeWithTag("settings_screen_list")
        listNode.performScrollToNode(hasTestTag("settings_section_notifications"))
        composeTestRule.onNodeWithTag("settings_section_notifications").assertIsDisplayed()

        listNode.performScrollToNode(hasTestTag("settings_section_memory"))
        composeTestRule.onNodeWithTag("settings_section_memory").assertIsDisplayed()

        listNode.performScrollToNode(hasTestTag("settings_section_personal_info"))
        composeTestRule.onNodeWithTag("settings_section_personal_info").assertIsDisplayed()

        listNode.performScrollToNode(hasTestTag("settings_section_permissions"))
        composeTestRule.onNodeWithTag("settings_section_permissions").assertIsDisplayed()

        listNode.performScrollToNode(hasTestTag("settings_section_about_privacy"))
        composeTestRule.onNodeWithTag("settings_section_about_privacy").assertIsDisplayed()
    }
}
