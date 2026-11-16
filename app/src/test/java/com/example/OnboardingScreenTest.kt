package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.AriaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `onboarding validates empty name and submits entered name`() {
        var submittedName: String? = null

        composeTestRule.setContent {
            AriaTheme {
                OnboardingScreen(onComplete = { submittedName = it })
            }
        }

        composeTestRule.onNodeWithTag("onboarding_particle_core").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_arc_logo").assertDoesNotExist()
        composeTestRule.onNodeWithText("ARC SYSTEM INITIALIZATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("What should I call you?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue_button").assertIsDisplayed()

        // Tapping GET STARTED with empty name shows inline validation without submitting
        composeTestRule.onNodeWithTag("onboarding_continue_button").performClick()
        composeTestRule.waitForIdle()
        assertNull(submittedName)
        composeTestRule.onNodeWithText("Please enter your name, Boss!").assertIsDisplayed()

        // Entering name and tapping GET STARTED submits trimmed name
        composeTestRule.onNodeWithTag("onboarding_name_input").performTextInput("  Mayank  ")
        composeTestRule.onNodeWithTag("onboarding_continue_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Mayank", submittedName)
    }
}
