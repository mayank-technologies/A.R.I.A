package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.ui.AssistantStatus
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iron Man / JARVIS Arc Reactor HUD Central Visualizer.
 * Features:
 * 1. Background A.I. grid crosshair texture.
 * 2. 36-degree radial tech tick mark scale.
 * 3. Dual counter-rotating concentric arc rings.
 * 4. Dynamic audio-reactive pulse wave animation based on AssistantStatus.
 * 5. High-intensity glowing energy core with lens flare halo.
 */
@Composable
fun ArcReactorVisualizer(
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorHUD")

    // Rotation Speeds
    val mainRotationDuration = when (status) {
        AssistantStatus.LISTENING -> 2000
        AssistantStatus.PROCESSING -> 1200
        AssistantStatus.SLEEP -> 20000
        else -> 6000
    }
    val clockwiseAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = mainRotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ClockwiseAngle"
    )

    val counterClockwiseAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (mainRotationDuration * 1.4f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CounterClockwiseAngle"
    )

    // Pulse Scale & Audio Reactivity
    val pulseMin = when (status) {
        AssistantStatus.LISTENING -> 0.75f
        AssistantStatus.PROCESSING -> 0.82f
        AssistantStatus.SLEEP -> 0.95f
        else -> 0.88f
    }
    val pulseMax = when (status) {
        AssistantStatus.LISTENING -> 1.38f
        AssistantStatus.PROCESSING -> 1.22f
        AssistantStatus.SLEEP -> 1.05f
        else -> 1.12f
    }
    val pulseDuration = when (status) {
        AssistantStatus.LISTENING -> 500
        AssistantStatus.PROCESSING -> 700
        AssistantStatus.SLEEP -> 3000
        else -> 1200
    }

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = pulseMin,
        targetValue = pulseMax,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Expanding Audio Waves for Listening Mode
    val listeningWaveRadius by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ListeningWaveRadius"
    )

    // Core Theme Color Palette based on Assistant State
    val coreColor = when (status) {
        AssistantStatus.IDLE -> CyberCyan
        AssistantStatus.LISTENING -> WarningAmber
        AssistantStatus.PROCESSING -> NeonPurple
        AssistantStatus.SPEAKING -> ElectricEmerald
        AssistantStatus.SLEEP -> Color(0xFF64748B)
    }

    val secondaryColor = when (status) {
        AssistantStatus.LISTENING -> CyberCyan
        AssistantStatus.PROCESSING -> CyberCyan
        else -> ElectricBlue
    }

    Box(
        modifier = modifier
            .size(220.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .clipToBounds()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.width / 3.2f

            // 1. Background A.I. Grid & Crosshair Lines (Blueprint Texture)
            val gridColor = CyberCyan.copy(alpha = 0.08f)
            val dashedStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
            
            // Horizontal & Vertical Crosshair
            drawLine(
                color = gridColor,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 1.dp.toPx()
            )

            // Outer Target Reticle Box
            drawCircle(
                color = CyberCyan.copy(alpha = 0.06f),
                radius = baseRadius * 1.55f,
                center = center,
                style = dashedStroke
            )

            // 2. Active Audio Wave Pulse Emission (Listening State)
            if (status == AssistantStatus.LISTENING) {
                val waveAlpha = (1.55f - listeningWaveRadius).coerceIn(0f, 0.85f)
                drawCircle(
                    color = WarningAmber.copy(alpha = waveAlpha),
                    radius = baseRadius * listeningWaveRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = CyberCyan.copy(alpha = waveAlpha * 0.6f),
                    radius = baseRadius * (listeningWaveRadius * 0.85f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 3. Ambient Radiant Energy Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.4f * pulseScale),
                        secondaryColor.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.6f
                ),
                radius = baseRadius * 1.45f,
                center = center
            )

            // 4. Outer Tech Radial Tick Marks (36 Ticks around Perimeter)
            val tickCount = 36
            for (i in 0 until tickCount) {
                val angleDeg = i * (360f / tickCount)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 9 == 0
                val tickLength = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                val tickColor = if (isMajor) coreColor.copy(alpha = 0.8f) else CyberCyan.copy(alpha = 0.25f)
                val tickWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()

                val innerR = baseRadius * 1.25f
                val outerR = innerR + tickLength

                val startPt = Offset(
                    x = center.x + (innerR * cos(angleRad)).toFloat(),
                    y = center.y + (innerR * sin(angleRad)).toFloat()
                )
                val endPt = Offset(
                    x = center.x + (outerR * cos(angleRad)).toFloat(),
                    y = center.y + (outerR * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = tickColor,
                    start = startPt,
                    end = endPt,
                    strokeWidth = tickWidth
                )
            }

            // 5. Primary Rotating Outer Ring (Clockwise)
            rotate(clockwiseAngle, pivot = center) {
                // Main Ring Base
                drawCircle(
                    color = coreColor.copy(alpha = 0.4f),
                    radius = baseRadius * 1.15f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 6 Radial Nodes along Outer Ring
                val nodeCount = 6
                for (i in 0 until nodeCount) {
                    val angleRad = Math.toRadians((i * (360f / nodeCount)).toDouble())
                    val nodeCenter = Offset(
                        x = center.x + (baseRadius * 1.15f * cos(angleRad)).toFloat(),
                        y = center.y + (baseRadius * 1.15f * sin(angleRad)).toFloat()
                    )
                    drawCircle(
                        color = coreColor,
                        radius = 3.5.dp.toPx(),
                        center = nodeCenter
                    )
                }

                // Triple Arc HUD Brackets
                drawArc(
                    color = coreColor,
                    startAngle = 0f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 1.15f, center.y - baseRadius * 1.15f),
                    size = Size(baseRadius * 2.3f, baseRadius * 2.3f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = coreColor,
                    startAngle = 120f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 1.15f, center.y - baseRadius * 1.15f),
                    size = Size(baseRadius * 2.3f, baseRadius * 2.3f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = coreColor,
                    startAngle = 240f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 1.15f, center.y - baseRadius * 1.15f),
                    size = Size(baseRadius * 2.3f, baseRadius * 2.3f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 6. Secondary Counter-Rotating Inner Ring
            rotate(counterClockwiseAngle, pivot = center) {
                drawArc(
                    color = CyberCyan.copy(alpha = 0.8f),
                    startAngle = 30f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 0.9f, center.y - baseRadius * 0.9f),
                    size = Size(baseRadius * 1.8f, baseRadius * 1.8f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = CyberCyan.copy(alpha = 0.8f),
                    startAngle = 210f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 0.9f, center.y - baseRadius * 0.9f),
                    size = Size(baseRadius * 1.8f, baseRadius * 1.8f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 7. Core Energy Eye Lens Flare
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        coreColor,
                        secondaryColor,
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.6f * pulseScale
                ),
                radius = baseRadius * 0.5f * pulseScale,
                center = center
            )

            // Inner Core Bright Spot
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = baseRadius * 0.18f * pulseScale,
                center = center
            )
        }
    }
}

