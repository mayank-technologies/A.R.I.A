package com.example.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.WarningAmber

/**
 * Animated Pulse & Sound Wave Visual Indicator shown when ARIA is actively listening for voice input.
 */
@Composable
fun ListeningPulseIndicator(
    isListening: Boolean,
    modifier: Modifier = Modifier,
    disableAnimations: Boolean = false
) {
    if (!isListening) return

    val ripple1Scale: Float
    val ripple1Alpha: Float
    val ripple2Scale: Float
    val ripple2Alpha: Float
    val bar1Height: Float
    val bar2Height: Float
    val bar3Height: Float
    val bar4Height: Float

    if (!disableAnimations) {
        val infiniteTransition = rememberInfiniteTransition(label = "ListeningPulseTransition")

        // Animated ripple expanding circle 1
        val r1s by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Ripple1Scale"
        )
        ripple1Scale = r1s

        val r1a by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Ripple1Alpha"
        )
        ripple1Alpha = r1a

        // Animated ripple expanding circle 2 (offset)
        val r2s by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, delayMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Ripple2Scale"
        )
        ripple2Scale = r2s

        val r2a by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, delayMillis = 600, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Ripple2Alpha"
        )
        ripple2Alpha = r2a

        // Sound wave bar heights
        val b1 by infiniteTransition.animateFloat(
            initialValue = 6f,
            targetValue = 28f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar1"
        )
        bar1Height = b1

        val b2 by infiniteTransition.animateFloat(
            initialValue = 12f,
            targetValue = 36f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar2"
        )
        bar2Height = b2

        val b3 by infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 32f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar3"
        )
        bar3Height = b3

        val b4 by infiniteTransition.animateFloat(
            initialValue = 14f,
            targetValue = 24f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar4"
        )
        bar4Height = b4
    } else {
        // Static values in battery saver mode
        ripple1Scale = 1.0f
        ripple1Alpha = 0.3f
        ripple2Scale = 1.3f
        ripple2Alpha = 0.15f
        bar1Height = 16f
        bar2Height = 24f
        bar3Height = 18f
        bar4Height = 20f
    }

    Box(
        modifier = modifier
            .testTag("listening_pulse_indicator"),
        contentAlignment = Alignment.Center
    ) {
        // Outer Radar Ripple Rings
        Canvas(modifier = Modifier.size(160.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.width / 4f

            // Ripple 1
            drawCircle(
                color = WarningAmber.copy(alpha = ripple1Alpha),
                radius = baseRadius * ripple1Scale,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Ripple 2
            drawCircle(
                color = CyberCyan.copy(alpha = ripple2Alpha),
                radius = baseRadius * ripple2Scale,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Sound Wave Bar Row pill below mic
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar1Height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WarningAmber)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar2Height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CyberCyan)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar3Height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WarningAmber)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar4Height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CyberCyan)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LISTENING",
                color = WarningAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
