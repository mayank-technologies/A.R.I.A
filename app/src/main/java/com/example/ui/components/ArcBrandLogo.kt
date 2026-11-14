package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan

/**
 * Reusable razor-sharp geometric circuit-"A" brand logo for ARC.
 * Renders the vector drawable [R.drawable.ic_arc_logo] on a transparent canvas
 * with an optional soft ambient cyan/blue aura and corner power-node pulse—
 * strictly free of radar rings, concentric target circles, or sphere orbs.
 */
@Composable
fun ArcBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    animatePulse: Boolean = true,
    showAmbientGlow: Boolean = true,
    contentDescription: String? = "ARC geometric circuit A logo"
) {
    val pulseAlpha = if (animatePulse && showAmbientGlow) {
        val infiniteTransition = rememberInfiniteTransition(label = "arc_logo_pulse")
        val value by infiniteTransition.animateFloat(
            initialValue = 0.68f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arc_logo_glow_alpha"
        )
        value
    } else {
        0.85f
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (showAmbientGlow) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                // Soft ambient atmospheric cyan/blue glow behind the 'A' (no hard rings)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.22f * pulseAlpha),
                            ElectricBlue.copy(alpha = 0.10f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = this.size.minDimension * 0.52f
                    ),
                    radius = this.size.minDimension * 0.52f,
                    center = center
                )

                // Accentuate the upper-right corner power-indicator node (at 90/108, 24/108)
                val nodeCenter = Offset(
                    x = this.size.width * (90f / 108f),
                    y = this.size.height * (24f / 108f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.48f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = nodeCenter,
                        radius = this.size.minDimension * 0.14f
                    ),
                    radius = this.size.minDimension * 0.14f,
                    center = nodeCenter
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.ic_arc_logo),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
