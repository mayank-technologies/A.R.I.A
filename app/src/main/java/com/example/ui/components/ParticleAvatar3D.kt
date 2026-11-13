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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.AssistantStatus
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HologramBlue
import com.example.ui.theme.ReactorGold
import com.example.ui.theme.WarningAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Single 3D Point for the dense CORE particle sphere normalized in unit sphere space (r <= 1.0f).
 * - [nx], [ny], [nz]: unit surface direction vector so idle breathing keeps true spherical symmetry.
 * - [radiusNorm]: normalized distance from sphere center [0.0f .. 1.0f] (dense near center, softer rim).
 * - [shimmerPhase]: deterministic per-particle phase offset for subtle idle luminance shimmer.
 */
data class SphereParticle(
    val nx: Float,
    val ny: Float,
    val nz: Float,
    val radiusNorm: Float,
    val shimmerPhase: Float
)

/**
 * Ambient floating "+" crosshair or micro-star accent orbiting outside the sphere boundary.
 */
private data class AmbientSparkle(
    val angleRad: Float,
    val distanceFactor: Float,
    val phaseOffset: Float,
    val speedMult: Float,
    val isCrosshair: Boolean,
    val sizeDp: Float
)

/**
 * Dense Volumetric CORE Sphere Generator (2,600 Particles):
 * - Uses isotropic spherical sampling (uniform sin/cos distribution, zero polar bunching).
 * - Radial distribution concentrates particles heavily in the center (r^2.1) with a crisp
 *   spherical boundary at r = 0.92..1.0 so it reads as a dense glowing orb rather than scattered dots.
 */
object CoreAvatarGenerator {
    const val PARTICLE_COUNT = 2600

    fun generateCorePoints(): List<SphereParticle> {
        val rand = Random(42)
        val list = ArrayList<SphereParticle>(PARTICLE_COUNT)

        for (i in 0 until PARTICLE_COUNT) {
            // Isotropic unit sphere direction
            val u = rand.nextFloat() * 2f - 1f // cos(phi) in [-1, 1]
            val sinPhi = sqrt((1f - u * u).coerceAtLeast(0f))
            val theta = rand.nextFloat() * 2f * PI.toFloat()

            val nx = sinPhi * cos(theta)
            val ny = u
            val nz = sinPhi * sin(theta)

            // Center-dense radial gradient distribution:
            // - 78% fill the glowing volumetric body (heavily dense near center via pow(2.15f))
            // - 22% form a clean, soft spherical outer shell around 0.78..0.98
            val radiusNorm = if (rand.nextFloat() < 0.78f) {
                rand.nextFloat().pow(2.15f) * 0.94f
            } else {
                0.76f + rand.nextFloat() * 0.22f
            }

            list.add(
                SphereParticle(
                    nx = nx,
                    ny = ny,
                    nz = nz,
                    radiusNorm = radiusNorm,
                    shimmerPhase = rand.nextFloat() * 2f * PI.toFloat()
                )
            )
        }
        return list
    }
}

/**
 * High-Density CORE Particle Sphere Avatar:
 * 1. Renders 2,600 fine, consistent particles batched via PointMode.Points for silky 60fps texture.
 * 2. Bright white-to-cyan inner core light emission (multi-stop radial gradient at sphere center).
 * 3. Soft outer bloom / light diffusion halo around the sphere edge without hard box clipping.
 * 4. Subtle ambient 3D rotation and shimmer that preserves the tight spherical silhouette.
 */
@Composable
fun ParticleAvatar3D(
    status: AssistantStatus,
    modifier: Modifier = Modifier,
    disableAnimations: Boolean = false
) {
    val corePoints = remember { CoreAvatarGenerator.generateCorePoints() }

    // Pre-allocated bucket lists (zero per-frame allocations during draw)
    val coreWhitePoints = remember { ArrayList<Offset>(900) }
    val innerBrightPoints = remember { ArrayList<Offset>(900) }
    val midCyanPoints = remember { ArrayList<Offset>(900) }
    val outerRimPoints = remember { ArrayList<Offset>(700) }

    val sparkles = remember {
        val rand = Random(108)
        List(14) { idx ->
            AmbientSparkle(
                angleRad = (idx.toFloat() / 14f) * 2f * PI.toFloat() + (rand.nextFloat() - 0.5f) * 0.25f,
                distanceFactor = 1.06f + rand.nextFloat() * 0.26f,
                phaseOffset = rand.nextFloat() * 2f * PI.toFloat(),
                speedMult = 0.65f + rand.nextFloat() * 0.5f,
                isCrosshair = idx % 3 == 0,
                sizeDp = 2.0f + rand.nextFloat() * 1.8f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "core_sphere_motion")

    // Slow continuous 3D Y-axis rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sphere_rotate_y"
    )

    // Smooth ambient phase for shimmer and subtle vertical float
    val shimmerCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sphere_shimmer_phase"
    )

    // Audio / state-reactive core pulse
    val audioPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.SPEAKING -> 420
                    AssistantStatus.LISTENING -> 620
                    AssistantStatus.PROCESSING -> 500
                    else -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sphere_audio_pulse"
    )

    val baseThemeColor = when (status) {
        AssistantStatus.SPEAKING -> ElectricCyan
        AssistantStatus.LISTENING -> WarningAmber
        AssistantStatus.PROCESSING -> HologramBlue
        AssistantStatus.SLEEP -> Color(0xFF64748B)
        else -> CyberCyan
    }

    val outerDiffusionColor = when (status) {
        AssistantStatus.SPEAKING -> ReactorGold
        AssistantStatus.LISTENING -> WarningAmber
        AssistantStatus.PROCESSING -> ElectricBlue
        AssistantStatus.SLEEP -> Color(0xFF1E293B)
        else -> ElectricBlue
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val minDim = minOf(canvasWidth, canvasHeight)

            val centerX = canvasWidth / 2f
            val floatOffsetPx = if (!disableAnimations) sin(shimmerCycle) * 2.5f * density else 0f
            val centerY = canvasHeight / 2f + floatOffsetPx

            // Sphere radius leaves generous margin for outer soft-light diffusion without clipping
            val sphereRadius = minDim * 0.34f
            val pulse = if (!disableAnimations) audioPulse else 1f
            val isSleep = status == AssistantStatus.SLEEP

            val statusAlphaBoost = when (status) {
                AssistantStatus.SPEAKING -> 1.15f
                AssistantStatus.LISTENING -> 1.10f
                AssistantStatus.PROCESSING -> 1.05f
                AssistantStatus.SLEEP -> 0.42f
                else -> 1.0f
            }

            // -----------------------------------------------------------------
            // 1. OUTER SOFT-LIGHT DIFFUSION / BLUR BLOOM AROUND THE SPHERE
            // -----------------------------------------------------------------
            val outerBloomRadius = (minDim * 0.495f) * (if (status == AssistantStatus.SPEAKING || status == AssistantStatus.LISTENING) 0.96f + (pulse - 0.92f) * 0.16f else 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to baseThemeColor.copy(alpha = (0.34f * statusAlphaBoost).coerceIn(0.08f, 0.48f)),
                    0.48f to baseThemeColor.copy(alpha = (0.20f * statusAlphaBoost).coerceIn(0.04f, 0.30f)),
                    0.74f to outerDiffusionColor.copy(alpha = (0.10f * statusAlphaBoost).coerceIn(0.02f, 0.16f)),
                    1.00f to Color.Transparent,
                    center = Offset(centerX, centerY),
                    radius = outerBloomRadius
                ),
                radius = outerBloomRadius,
                center = Offset(centerX, centerY)
            )

            // Soft inner white-to-accent core illumination emitting light from the sphere center
            val coreGlowRadius = sphereRadius * (0.68f * (0.95f + (pulse - 0.92f) * 0.25f))
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to if (isSleep) Color(0xFF94A3B8).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.85f),
                    0.28f to if (isSleep) Color(0xFF64748B).copy(alpha = 0.18f) else Color(0xFFB8F8FF).copy(alpha = 0.56f),
                    0.62f to baseThemeColor.copy(alpha = if (isSleep) 0.08f else 0.26f),
                    1.00f to Color.Transparent,
                    center = Offset(centerX, centerY),
                    radius = coreGlowRadius
                ),
                radius = coreGlowRadius,
                center = Offset(centerX, centerY)
            )

            // Subtle energy ripple ring only when actively speaking or listening
            if (status == AssistantStatus.SPEAKING || status == AssistantStatus.LISTENING) {
                drawCircle(
                    color = baseThemeColor.copy(alpha = (0.28f * (pulse - 0.72f)).coerceIn(0.06f, 0.36f)),
                    radius = sphereRadius * (1.03f + (pulse - 0.92f) * 0.45f),
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.1f * density)
                )
            }

            // -----------------------------------------------------------------
            // 2. FLOATING "+" CROSSHAIRS & SPARKLE DOTS OUTSIDE THE SPHERE
            // -----------------------------------------------------------------
            sparkles.forEach { sp ->
                val phase = (if (!disableAnimations) shimmerCycle * sp.speedMult else 0f) + sp.phaseOffset
                val driftY = sin(phase) * 4f * density
                val driftX = cos(phase * 0.7f) * 2.5f * density
                val sx = centerX + cos(sp.angleRad) * (sphereRadius * sp.distanceFactor) + driftX
                val sy = (canvasHeight / 2f) + sin(sp.angleRad) * (sphereRadius * sp.distanceFactor) + driftY
                val spAlpha = ((0.30f + 0.45f * ((sin(phase * 1.4f) + 1f) / 2f)) * (if (isSleep) 0.30f else 1f)).coerceIn(0.08f, 0.78f)
                val spColor = if (sp.isCrosshair) baseThemeColor.copy(alpha = spAlpha) else HologramBlue.copy(alpha = spAlpha * 0.85f)

                if (sp.isCrosshair) {
                    val arm = sp.sizeDp * density
                    drawLine(
                        color = spColor,
                        start = Offset(sx - arm, sy),
                        end = Offset(sx + arm, sy),
                        strokeWidth = 1.0f * density,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = spColor,
                        start = Offset(sx, sy - arm),
                        end = Offset(sx, sy + arm),
                        strokeWidth = 1.0f * density,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawCircle(
                        color = spColor,
                        radius = (sp.sizeDp * 0.42f) * density,
                        center = Offset(sx, sy)
                    )
                }
            }

            // -----------------------------------------------------------------
            // 3. 2,600 FINE CONSISTENT PARTICLES FORMING SMOOTH DENSE SPHERE
            // -----------------------------------------------------------------
            coreWhitePoints.clear()
            innerBrightPoints.clear()
            midCyanPoints.clear()
            outerRimPoints.clear()

            val rot = if (!disableAnimations) rotationAngle else 0f
            val cosRot = cos(rot)
            val sinRot = sin(rot)
            val cameraDistance = 2.8f

            for (i in corePoints.indices) {
                val p = corePoints[i]

                // Gentle radial shimmer keeps exact spherical shape (never scatters dots outward)
                val radialWave = if (!disableAnimations) {
                    1f + sin(shimmerCycle * 1.6f + p.shimmerPhase) * 0.018f + (pulse - 1f) * 0.08f
                } else {
                    1f
                }
                val r = p.radiusNorm * radialWave

                val px = p.nx * r
                val py = p.ny * r
                val pz = p.nz * r

                // 3D Y-axis rotation
                val rotatedX = px * cosRot - pz * sinRot
                val rotatedZ = px * sinRot + pz * cosRot

                val perspective = cameraDistance / (cameraDistance - rotatedZ * 0.45f)
                val screenX = centerX + (rotatedX * sphereRadius * perspective)
                val screenY = centerY + (py * sphereRadius * perspective)
                val offset = Offset(screenX, screenY)

                // Bucket particles by radial distance & depth for fine white-center-to-cyan gradient
                val shimmerBright = if (!disableAnimations) 0.05f * sin(shimmerCycle * 2f + p.shimmerPhase) else 0f
                val radialGlow = (p.radiusNorm + shimmerBright).coerceIn(0f, 1f)

                when {
                    radialGlow < 0.24f -> coreWhitePoints.add(offset)
                    radialGlow < 0.50f -> innerBrightPoints.add(offset)
                    radialGlow < 0.78f && rotatedZ > -0.35f -> midCyanPoints.add(offset)
                    else -> outerRimPoints.add(offset)
                }
            }

            // Fine, uniform particle diameters (smooth volumetric dust texture, no chunky dots)
            drawPoints(
                points = outerRimPoints,
                pointMode = PointMode.Points,
                color = HologramBlue.copy(alpha = if (isSleep) 0.28f else 0.48f),
                strokeWidth = 1.15f * density,
                cap = StrokeCap.Round
            )
            drawPoints(
                points = midCyanPoints,
                pointMode = PointMode.Points,
                color = baseThemeColor.copy(alpha = if (isSleep) 0.38f else 0.72f),
                strokeWidth = 1.35f * density,
                cap = StrokeCap.Round
            )
            drawPoints(
                points = innerBrightPoints,
                pointMode = PointMode.Points,
                color = if (isSleep) Color(0xFF94A3B8).copy(alpha = 0.45f) else Color(0xFFA5F3FC).copy(alpha = 0.88f),
                strokeWidth = 1.45f * density,
                cap = StrokeCap.Round
            )
            drawPoints(
                points = coreWhitePoints,
                pointMode = PointMode.Points,
                color = if (isSleep) Color(0xFFCBD5E1).copy(alpha = 0.52f) else Color.White.copy(alpha = 0.96f),
                strokeWidth = 1.55f * density,
                cap = StrokeCap.Round
            )
        }
    }
}
