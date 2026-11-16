package com.example

import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.speech.SpeechWaveformAnalyzer
import com.example.ui.AssistantStatus
import com.example.ui.components.ParticleAvatar3D
import com.example.ui.components.SpeechAudioVisualizer
import com.example.ui.theme.AriaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeechAudioVisualizerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `analyzer extracts non-zero bands and level from PCM16 speech sine wave`() {
        val analyzer = SpeechWaveformAnalyzer()
        val sampleCount = 480 // 20ms at 24kHz
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val s = (sin(2.0 * PI * 320.0 * i / 24000.0) * 14000.0).toInt().toShort()
            pcm[i * 2] = (s.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }

        val frame = analyzer.analyzePcm16(pcm)
        assertEquals(SpeechWaveformAnalyzer.DEFAULT_BAND_COUNT, frame.bands.size)
        assertTrue("Expected positive speech RMS level", frame.level > 0.1f)
        assertTrue("Expected active spectrum bars", frame.bands.any { it > 0.1f })

        val reset = analyzer.reset()
        assertEquals(0f, reset.level, 0.0001f)
        assertTrue(reset.bands.all { it == 0f })
    }

    @Test
    fun `analyzer produces harmonic cadence bands for TTS range fallback`() {
        val analyzer = SpeechWaveformAnalyzer()
        val frame = analyzer.synthesizeSpeechCadenceFrame(phaseRad = 1.1f, syllableBoost = 0.85f)
        assertEquals(SpeechWaveformAnalyzer.DEFAULT_BAND_COUNT, frame.bands.size)
        assertTrue(frame.level in 0.15f..1.0f)
        assertTrue(frame.bands.max() > frame.bands.min())
    }

    @Test
    fun `SpeechAudioVisualizer renders Canvas HUD and ParticleAvatar3D with speechVolume`() {
        val sampleBands = List(24) { idx -> 0.25f + ((idx % 5) * 0.14f) }

        composeTestRule.setContent {
            AriaTheme {
                ParticleAvatar3D(
                    status = AssistantStatus.SPEAKING,
                    speechVolume = 0.72f,
                    disableAnimations = true
                )
                SpeechAudioVisualizer(
                    isSpeaking = true,
                    audioLevel = 0.72f,
                    audioBands = sampleBands,
                    disableAnimations = true
                )
            }
        }

        composeTestRule.onNodeWithTag("speech_audio_visualizer")
            .assertIsDisplayed()
            .assertContentDescriptionContains("72 percent amplitude")
    }
}
