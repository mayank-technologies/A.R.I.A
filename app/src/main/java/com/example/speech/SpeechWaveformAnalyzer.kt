package com.example.speech

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of analyzing a live audio output buffer (16-bit PCM, MediaPlayer waveform, or TTS cadence).
 */
data class SpeechWaveformFrame(
    val level: Float,
    val bands: List<Float>
)

/**
 * SpeechWaveformAnalyzer:
 * Real-time signal analyzer converting live 16-bit mono PCM chunks (Gemini Live 24kHz audio),
 * MediaPlayer Visualizer bytes (ElevenLabs MP3), and Android TTS word-range cadence into
 * smoothed [0f..1f] amplitude + 24 symmetrical HUD spectrum bands.
 */
class SpeechWaveformAnalyzer(
    val bandCount: Int = DEFAULT_BAND_COUNT
) {
    companion object {
        const val DEFAULT_BAND_COUNT = 24
        val SILENT_BANDS: List<Float> = List(DEFAULT_BAND_COUNT) { 0f }
    }

    private val smoothedBands = FloatArray(bandCount)
    private var smoothedLevel = 0f

    /**
     * Analyzes little-endian 16-bit signed mono PCM bytes directly from Gemini Live's AudioTrack stream.
     */
    @Synchronized
    fun analyzePcm16(pcmBytes: ByteArray): SpeechWaveformFrame {
        if (pcmBytes.size < 2) {
            return decayTowardsSilence()
        }

        val totalSamples = pcmBytes.size / 2
        var sumSquares = 0.0

        // Divide buffer into `bandCount` sub-windows and also weight low/mid/high variation
        // using zero-crossing + windowed RMS so voice formants animate naturally across bars.
        val samplesPerBand = (totalSamples / bandCount).coerceAtLeast(1)
        val rawBands = FloatArray(bandCount)

        for (b in 0 until bandCount) {
            val startSample = (b * samplesPerBand).coerceAtMost(totalSamples - 1)
            val endSample = if (b == bandCount - 1) totalSamples else ((b + 1) * samplesPerBand).coerceAtMost(totalSamples)
            var bandSumSq = 0.0
            var bandDiffSum = 0.0
            var prevSample = 0.0
            var count = 0

            for (sIdx in startSample until endSample) {
                val byteIdx = sIdx * 2
                if (byteIdx + 1 >= pcmBytes.size) break
                val rawShort = ((pcmBytes[byteIdx].toInt() and 0xFF) or (pcmBytes[byteIdx + 1].toInt() shl 8)).toShort()
                val sampleNorm = rawShort / 32768.0
                bandSumSq += sampleNorm * sampleNorm
                sumSquares += sampleNorm * sampleNorm
                if (count > 0) {
                    bandDiffSum += abs(sampleNorm - prevSample)
                }
                prevSample = sampleNorm
                count++
            }

            if (count > 0) {
                val bandRms = sqrt(bandSumSq / count).toFloat()
                val brightness = (bandDiffSum / count).toFloat()
                // Center-weighted voice formant emphasis + local derivative energy
                val centerBias = 0.78f + 0.35f * sin(PI * (b + 0.5) / bandCount).toFloat()
                val perceptual = ((bandRms * 3.4f + brightness * 1.4f) * centerBias)
                rawBands[b] = perceptual.coerceIn(0f, 1f)
            }
        }

        val overallRms = sqrt(sumSquares / totalSamples.coerceAtLeast(1)).toFloat()
        // Companded perceptual speech level so conversational male voice stays expressive without clipping
        val targetLevel = (ln(1f + overallRms * 12f) / ln(13f)).coerceIn(0f, 1f)

        return smoothAndSnapshot(targetLevel, rawBands)
    }

    /**
     * Analyzes unsigned 8-bit PCM waveform bytes from Android `Visualizer.getWaveForm()`.
     */
    @Synchronized
    fun analyzeVisualizerWaveform(waveform: ByteArray): SpeechWaveformFrame {
        if (waveform.isEmpty()) return decayTowardsSilence()

        val rawBands = FloatArray(bandCount)
        val step = (waveform.size / bandCount).coerceAtLeast(1)
        var sumSq = 0.0

        for (b in 0 until bandCount) {
            val start = (b * step).coerceAtMost(waveform.size - 1)
            val end = if (b == bandCount - 1) waveform.size else ((b + 1) * step).coerceAtMost(waveform.size)
            var bandSq = 0.0
            var count = 0
            for (i in start until end) {
                val centered = ((waveform[i].toInt() and 0xFF) - 128) / 128.0
                bandSq += centered * centered
                sumSq += centered * centered
                count++
            }
            if (count > 0) {
                val bandRms = sqrt(bandSq / count).toFloat()
                rawBands[b] = (bandRms * 2.8f).coerceIn(0f, 1f)
            }
        }

        val overallRms = sqrt(sumSq / waveform.size.coerceAtLeast(1)).toFloat()
        val targetLevel = (overallRms * 2.6f).coerceIn(0f, 1f)
        return smoothAndSnapshot(targetLevel, rawBands)
    }

    /**
     * Generates a speech-synchronized harmonic cadence frame driven by utterance syllable pulses
     * (used for Native Android TTS word boundaries or MediaPlayer when Visualizer session is restricted).
     */
    @Synchronized
    fun synthesizeSpeechCadenceFrame(
        phaseRad: Float,
        syllableBoost: Float
    ): SpeechWaveformFrame {
        val rawBands = FloatArray(bandCount)
        val clampedBoost = syllableBoost.coerceIn(0.15f, 1f)
        for (b in 0 until bandCount) {
            val normPos = b.toFloat() / (bandCount - 1).coerceAtLeast(1)
            val bell = sin(PI * normPos).toFloat().coerceAtLeast(0.18f)
            val harmonicA = 0.5f + 0.5f * sin(phaseRad * 1.7f + b * 0.55f)
            val harmonicB = 0.5f + 0.5f * cos(phaseRad * 2.3f - b * 0.42f)
            rawBands[b] = (bell * (0.28f + 0.48f * harmonicA + 0.24f * harmonicB) * clampedBoost)
                .coerceIn(0.06f, 1f)
        }
        val targetLevel = (0.22f + 0.65f * clampedBoost * (0.65f + 0.35f * sin(phaseRad))).coerceIn(0f, 1f)
        return smoothAndSnapshot(targetLevel, rawBands)
    }

    /**
     * Smoothly decays remaining bars towards zero between speech pauses.
     */
    @Synchronized
    fun decayTowardsSilence(decayFactor: Float = 0.72f): SpeechWaveformFrame {
        smoothedLevel *= decayFactor
        if (smoothedLevel < 0.01f) smoothedLevel = 0f
        for (i in 0 until bandCount) {
            smoothedBands[i] *= decayFactor
            if (smoothedBands[i] < 0.01f) smoothedBands[i] = 0f
        }
        return SpeechWaveformFrame(
            level = smoothedLevel,
            bands = smoothedBands.toList()
        )
    }

    @Synchronized
    fun reset(): SpeechWaveformFrame {
        smoothedLevel = 0f
        smoothedBands.fill(0f)
        return SpeechWaveformFrame(0f, SILENT_BANDS)
    }

    private fun smoothAndSnapshot(targetLevel: Float, rawBands: FloatArray): SpeechWaveformFrame {
        // Fast attack (0.62), smooth release (0.32) for responsive HUD animation
        val levelAlpha = if (targetLevel > smoothedLevel) 0.62f else 0.32f
        smoothedLevel = (smoothedLevel + (targetLevel - smoothedLevel) * levelAlpha).coerceIn(0f, 1f)

        for (i in 0 until bandCount) {
            val target = rawBands[i].coerceIn(0f, 1f)
            val alpha = if (target > smoothedBands[i]) 0.65f else 0.30f
            smoothedBands[i] = (smoothedBands[i] + (target - smoothedBands[i]) * alpha).coerceIn(0f, 1f)
        }

        return SpeechWaveformFrame(
            level = smoothedLevel,
            bands = smoothedBands.toList()
        )
    }
}
