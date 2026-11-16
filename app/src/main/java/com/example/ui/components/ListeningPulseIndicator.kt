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
import com.example.ui.theme.HologramBlue

/**
 * Animated Pulse & Sound Wave Visual Indicator shown when ARC is actively listening or triggered by Wake Word.
 */
@Composable
fun ListeningPulseIndicator(
    isListening: Boolean,
    modifier: Modifier = Modifier,
    micVolume: Float = 0f,
    label: String = "LISTENING",
    accentColor: Color = CyberCyan,
    disableAnimations: Boolean = false
) {
    if (!isListening) return

    val bar1Height: Float
    val bar2Height: Float
    val bar3Height: Float
    val bar4Height: Float
    val clampedVol = micVolume.coerceIn(0f, 1f)

    if (!disableAnimations) {
        val infiniteTransition = rememberInfiniteTransition(label = "ListeningPulseTransition")

        val b1 by infiniteTransition.animateFloat(
            initialValue = 6f,
            targetValue = 20f + clampedVol * 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar1"
        )
        bar1Height = b1

        val b2 by infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = 26f + clampedVol * 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 390, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar2"
        )
        bar2Height = b2

        val b3 by infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 24f + clampedVol * 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar3"
        )
        bar3Height = b3

        val b4 by infiniteTransition.animateFloat(
            initialValue = 11f,
            targetValue = 19f + clampedVol * 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar4"
        )
        bar4Height = b4
    } else {
        bar1Height = 14f + clampedVol * 8f
        bar2Height = 22f + clampedVol * 10f
        bar3Height = 16f + clampedVol * 8f
        bar4Height = 18f + clampedVol * 8f
    }

    Box(
        modifier = modifier
            .testTag("listening_pulse_indicator"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .border(1.2.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar1Height.coerceAtMost(32f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HologramBlue)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar2Height.coerceAtMost(34f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar3Height.coerceAtMost(34f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HologramBlue)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar4Height.coerceAtMost(32f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
        }
    }
}
