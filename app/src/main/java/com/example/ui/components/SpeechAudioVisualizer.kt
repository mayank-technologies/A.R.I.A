package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speech.SpeechWaveformAnalyzer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.HologramBlue
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-Time Canvas API Speech Output Visualizer for the ARC HUD.
 *
 * Visual Layers (drawn inside Jetpack Compose Canvas):
 * 1. Micro HUD grid horizon & corner reticle tick brackets.
 * 2. 24 mirrored symmetrical vertical spectrum bars with soft outer bloom & gradient core.
 * 3. Floating peak-hold energy micro-dots above active bars.
 * 4. Dual harmonic cubic Bezier oscilloscope waveforms synced to real speech output bands & RMS.
 */
@Composable
fun SpeechAudioVisualizer(
    isSpeaking: Boolean,
    audioLevel: Float,
    audioBands: List<Float>,
    modifier: Modifier = Modifier,
    accentColor: Color = ElectricCyan,
    secondaryColor: Color = HologramBlue,
    highlightColor: Color = ElectricEmerald,
    disableAnimations: Boolean = false,
    isListening: Boolean = false
) {
    val isActive = isSpeaking || isListening
    val clampedLevel = audioLevel.coerceIn(0f, 1f)
    val targetLevel = if (isActive) clampedLevel.coerceAtLeast(if (isSpeaking) 0.14f else 0.16f) else 0f

    val animatedLevel by animateFloatAsState(
        targetValue = targetLevel,
        animationSpec = tween(durationMillis = 75, easing = FastOutSlowInEasing),
        label = "speech_vis_level"
    )
    val smoothedLevel = if (disableAnimations) targetLevel else animatedLevel

    val phase: Float = if (isActive && !disableAnimations) {
        val infiniteTransition = rememberInfiniteTransition(label = "speech_vis_phase")
        val animPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (isSpeaking) 1200 else 950, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "oscilloscope_phase"
        )
        animPhase
    } else {
        0f
    }

    val normalizedBands = remember(audioBands, isActive, isSpeaking, isListening, clampedLevel) {
        val count = SpeechWaveformAnalyzer.DEFAULT_BAND_COUNT
        List(count) { idx ->
            val raw = audioBands.getOrElse(idx) { 0f }.coerceIn(0f, 1f)
            when {
                isSpeaking -> {
                    val floor = 0.08f + 0.08f * sin(PI * idx / (count - 1).coerceAtLeast(1)).toFloat()
                    maxOf(raw, floor * (0.5f + clampedLevel))
                }
                isListening -> {
                    val centerArch = sin(PI * idx / (count - 1).coerceAtLeast(1)).toFloat()
                    val reactive = (0.14f + centerArch * 0.42f + clampedLevel * (0.48f + 0.34f * centerArch))
                        .coerceIn(0.10f, 1f)
                    maxOf(raw, reactive)
                }
                else -> raw * 0.25f
            }
        }
    }

    val primaryWavePath = remember { Path() }
    val mirrorWavePath = remember { Path() }

    val statusDotColor = when {
        isSpeaking -> highlightColor
        isListening -> accentColor
        else -> TextMuted
    }
    val statusLabel = when {
        isSpeaking -> "AUDIO OUTPUT · SYNCED"
        isListening -> "VOICE INPUT · LISTENING"
        else -> "AUDIO BUS · STANDBY"
    }
    val activeBorderColor = when {
        isSpeaking -> highlightColor.copy(alpha = 0.45f)
        isListening -> accentColor.copy(alpha = 0.50f)
        else -> secondaryColor.copy(alpha = 0.18f)
    }
    val energyPct = kotlin.math.round(smoothedLevel * 100f).toInt().coerceIn(0, 100)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark.copy(alpha = 0.78f))
            .border(1.dp, activeBorderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics {
                contentDescription = when {
                    isSpeaking -> "ARC speech output audio visualizer active at $energyPct percent amplitude"
                    isListening -> "ARC voice input waveform active at $energyPct percent amplitude"
                    else -> "ARC speech output audio visualizer standby"
                }
            }
            .testTag("speech_audio_visualizer"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Telemetry Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Text(
                    text = statusLabel,
                    color = if (isActive) accentColor else TextMuted,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
            }
            Text(
                text = if (isActive) "${energyPct}% AMP" else "00% AMP",
                color = if (isActive) statusDotColor else TextMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            )
        }

        // Canvas Spectrum + Harmonic Oscilloscope
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val centerY = h / 2f
            val maxHalfAmp = (h * 0.43f)

            // 1. Subtle Dashed HUD Horizon Hairline
            drawLine(
                color = secondaryColor.copy(alpha = if (isActive) 0.30f else 0.16f),
                start = Offset(0f, centerY),
                end = Offset(w, centerY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )

            // Corner HUD Reticle Brackets
            val tickLen = 6.dp.toPx()
            val bracketColor = accentColor.copy(alpha = if (isActive) 0.55f else 0.20f)
            val bracketStroke = 1.2.dp.toPx()
            drawLine(bracketColor, Offset(0f, 0f), Offset(tickLen, 0f), bracketStroke)
            drawLine(bracketColor, Offset(0f, 0f), Offset(0f, tickLen), bracketStroke)
            drawLine(bracketColor, Offset(w - tickLen, 0f), Offset(w, 0f), bracketStroke)
            drawLine(bracketColor, Offset(w, 0f), Offset(w, tickLen), bracketStroke)
            drawLine(bracketColor, Offset(0f, h), Offset(tickLen, h), bracketStroke)
            drawLine(bracketColor, Offset(0f, h - tickLen), Offset(0f, h), bracketStroke)
            drawLine(bracketColor, Offset(w - tickLen, h), Offset(w, h), bracketStroke)
            drawLine(bracketColor, Offset(w, h - tickLen), Offset(w, h), bracketStroke)

            // 2. Symmetrical 24-Band Spectrum Bars + Glow Bloom + Peak Dots
            val barCount = normalizedBands.size
            val horizontalPad = 8.dp.toPx()
            val usableWidth = (w - horizontalPad * 2f).coerceAtLeast(1f)
            val slotWidth = usableWidth / barCount
            val barStroke = (slotWidth * 0.42f).coerceIn(2.dp.toPx(), 4.5.dp.toPx())
            val glowStroke = barStroke * 2.1f

            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    highlightColor,
                    accentColor,
                    secondaryColor,
                    accentColor,
                    highlightColor
                )
            )

            for (i in 0 until barCount) {
                val bandEnergy = normalizedBands[i]
                val harmonicRipple = if (isActive && !disableAnimations) {
                    0.85f + 0.15f * sin(phase * 2f + i * 0.52f)
                } else {
                    1f
                }
                val halfHeight = (maxHalfAmp * bandEnergy * harmonicRipple)
                    .coerceAtLeast(if (isActive) 2.2.dp.toPx() else 1.2.dp.toPx())

                val x = horizontalPad + slotWidth * (i + 0.5f)
                val topY = centerY - halfHeight
                val bottomY = centerY + halfHeight

                // Outer neon halo
                if (isActive && bandEnergy > 0.12f) {
                    drawLine(
                        color = accentColor.copy(alpha = (0.14f + bandEnergy * 0.22f).coerceAtMost(0.38f)),
                        start = Offset(x, topY),
                        end = Offset(x, bottomY),
                        strokeWidth = glowStroke,
                        cap = StrokeCap.Round
                    )
                }

                // Crisp inner core spectrum capsule
                drawLine(
                    brush = barBrush,
                    start = Offset(x, topY),
                    end = Offset(x, bottomY),
                    strokeWidth = barStroke,
                    cap = StrokeCap.Round
                )

                // Peak-hold glowing dot above dominant bars
                if (isActive && bandEnergy > 0.36f) {
                    drawCircle(
                        color = highlightColor.copy(alpha = 0.88f),
                        radius = 1.4.dp.toPx(),
                        center = Offset(x, (topY - 3.dp.toPx()).coerceAtLeast(2.dp.toPx()))
                    )
                }
            }

            // 3. Dual Harmonic Cubic Bezier Oscilloscope Ribbon synced to bands
            if (isActive) {
                primaryWavePath.reset()
                mirrorWavePath.reset()

                val wavePoints = 36
                var prevX = 0f
                var prevYPrimary = centerY
                var prevYMirror = centerY

                for (step in 0..wavePoints) {
                    val t = step.toFloat() / wavePoints.toFloat()
                    val x = t * w
                    val envelope = sin(PI * t).toFloat()
                    val bandIdx = ((t * (barCount - 1)).toInt()).coerceIn(0, barCount - 1)
                    val bandBoost = 0.45f + normalizedBands[bandIdx] * 0.95f
                    val waveArg = t * 4f * PI.toFloat() + phase
                    val dy = sin(waveArg) * maxHalfAmp * 0.58f * envelope * bandBoost * (0.45f + smoothedLevel * 0.85f)

                    val yPrimary = centerY - dy
                    val yMirror = centerY + dy * 0.72f

                    if (step == 0) {
                        primaryWavePath.moveTo(x, yPrimary)
                        mirrorWavePath.moveTo(x, yMirror)
                    } else {
                        val midX = (prevX + x) * 0.5f
                        primaryWavePath.cubicTo(midX, prevYPrimary, midX, yPrimary, x, yPrimary)
                        mirrorWavePath.cubicTo(midX, prevYMirror, midX, yMirror, x, yMirror)
                    }
                    prevX = x
                    prevYPrimary = yPrimary
                    prevYMirror = yMirror
                }

                val waveBrush = Brush.horizontalGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = 0.35f),
                        accentColor,
                        highlightColor,
                        accentColor,
                        secondaryColor.copy(alpha = 0.35f)
                    )
                )

                drawPath(
                    path = mirrorWavePath,
                    color = secondaryColor.copy(alpha = 0.45f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = primaryWavePath,
                    brush = waveBrush,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
