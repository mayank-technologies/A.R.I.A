package com.example.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AssistantStatus
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.HologramBlue
import com.example.ui.theme.ReactorGold
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 3 Supported 3D Particle Avatar Formats:
 * 1. CORE: Glowing sphere / ARC orb with dense bright nucleus & orbital shell.
 * 2. FEMALE: Refined human bust with flowing hair silhouette, slender neck & bright facial core.
 * 3. MALE: Athletic human bust with angular jawline, short crown silhouette, broad shoulders & bright facial core.
 */
enum class AvatarForm(val label: String) {
    CORE("CORE"),
    FEMALE("FEMALE"),
    MALE("MALE");

    companion object {
        fun fromString(name: String?): AvatarForm {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CORE
        }
    }
}

/**
 * Single 3D Point for our particle cloud normalized in [-1.0f .. 1.0f] coordinate space.
 * depth (z) provides brightness & perspective scale simulation.
 */
data class ParticlePoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val brightnessMultiplier: Float = 1.0f,
    val isFaceFeature: Boolean = false
)

/**
 * Ambient floating sparkle / "+" marker orbiting softly around the avatar (SARA-inspired polish).
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
 * Procedural Point Cloud Generator.
 * Uses 820 density-scaled particles for vivid 3D silhouettes across CORE, FEMALE, and MALE forms
 * while staying smooth at 60fps in native Compose Canvas.
 */
object AvatarShapeGenerator {
    const val PARTICLE_COUNT = 820

    fun generatePoints(form: AvatarForm): List<ParticlePoint> {
        val rand = Random(42)
        val list = ArrayList<ParticlePoint>(PARTICLE_COUNT)

        when (form) {
            AvatarForm.CORE -> {
                for (i in 0 until PARTICLE_COUNT) {
                    val roll = rand.nextFloat()
                    when {
                        // Dense luminous core nucleus (35%)
                        roll < 0.35f -> {
                            val theta = rand.nextFloat() * 2f * PI.toFloat()
                            val phi = (rand.nextFloat() - 0.5f) * PI.toFloat()
                            val radius = rand.nextFloat().pow(1.4f) * 0.38f
                            val x = radius * cos(phi) * cos(theta)
                            val y = radius * sin(phi)
                            val z = radius * cos(phi) * sin(theta)
                            list.add(ParticlePoint(x, y, z, brightnessMultiplier = 1.55f, isFaceFeature = radius < 0.22f))
                        }
                        // Mid spherical dust cloud (40%)
                        roll < 0.75f -> {
                            val theta = rand.nextFloat() * 2f * PI.toFloat()
                            val phi = (rand.nextFloat() - 0.5f) * PI.toFloat()
                            val radius = 0.42f + rand.nextFloat() * 0.34f
                            val x = radius * cos(phi) * cos(theta)
                            val y = radius * sin(phi)
                            val z = radius * cos(phi) * sin(theta)
                            list.add(ParticlePoint(x, y, z, brightnessMultiplier = 1.05f))
                        }
                        // Outer glowing orbital shell & subtle tilted ring (25%)
                        else -> {
                            val theta = rand.nextFloat() * 2f * PI.toFloat()
                            val isRing = rand.nextFloat() < 0.45f
                            val radius = if (isRing) 0.78f + rand.nextFloat() * 0.14f else 0.72f + rand.nextFloat() * 0.16f
                            val phi = if (isRing) (rand.nextFloat() - 0.5f) * 0.14f else (rand.nextFloat() - 0.5f) * PI.toFloat()
                            val x = radius * cos(phi) * cos(theta)
                            val rawY = radius * sin(phi)
                            val z = radius * cos(phi) * sin(theta)
                            val y = if (isRing) rawY * 0.85f + z * 0.28f else rawY
                            list.add(ParticlePoint(x, y, z, brightnessMultiplier = 0.88f))
                        }
                    }
                }
            }

            AvatarForm.FEMALE -> {
                for (i in 0 until PARTICLE_COUNT) {
                    val roll = rand.nextFloat()
                    when {
                        // 1. Bright eyes & facial center highlights (16%)
                        roll < 0.16f -> {
                            val featureRoll = rand.nextFloat()
                            val (fx, fy, fz) = when {
                                // Left & right luminous eyes
                                featureRoll < 0.42f -> {
                                    val side = if (rand.nextBoolean()) 1f else -1f
                                    Triple(
                                        side * (0.09f + (rand.nextFloat() - 0.5f) * 0.04f),
                                        -0.41f + (rand.nextFloat() - 0.5f) * 0.03f,
                                        0.25f + rand.nextFloat() * 0.08f
                                    )
                                }
                                // Soft glowing cheekbones / nose / lips silhouette
                                else -> {
                                    Triple(
                                        (rand.nextFloat() - 0.5f) * 0.22f,
                                        -0.37f + (rand.nextFloat() - 0.5f) * 0.22f,
                                        0.22f + rand.nextFloat() * 0.12f
                                    )
                                }
                            }
                            list.add(ParticlePoint(fx, fy, fz, brightnessMultiplier = 1.75f, isFaceFeature = true))
                        }

                        // 2. Refined oval head & jawline contour (26%)
                        roll < 0.42f -> {
                            val theta = rand.nextFloat() * 2f * PI.toFloat()
                            val phi = (rand.nextFloat() - 0.5f) * PI.toFloat()
                            val jawTaper = if (phi > 0f) 1f - (phi / (PI.toFloat() / 2f)) * 0.26f else 1f
                            val rx = 0.27f * jawTaper * cos(phi) * cos(theta)
                            val ry = -0.38f + 0.29f * sin(phi)
                            val rz = 0.27f * jawTaper * cos(phi) * sin(theta)
                            list.add(ParticlePoint(rx, ry, rz, brightnessMultiplier = 1.1f))
                        }

                        // 3. Flowing Hair Crown & Shoulder Cascade (30%)
                        roll < 0.72f -> {
                            val isCrown = rand.nextFloat() < 0.30f
                            if (isCrown) {
                                val theta = rand.nextFloat() * PI.toFloat() // Upper arc
                                val rx = 0.33f * cos(theta) + (rand.nextFloat() - 0.5f) * 0.07f
                                val ry = -0.44f - 0.30f * sin(theta) + (rand.nextFloat() - 0.5f) * 0.05f
                                val rz = (rand.nextFloat() - 0.5f) * 0.35f - 0.05f
                                list.add(ParticlePoint(rx, ry, rz, brightnessMultiplier = 0.92f))
                            } else {
                                val side = if (rand.nextBoolean()) 1f else -1f
                                val t = rand.nextFloat() // 0 at temple -> 1 past shoulders
                                val wave = sin(t * 5f) * 0.04f
                                val hx = side * (0.28f + t * 0.14f + wave + (rand.nextFloat() - 0.5f) * 0.10f)
                                val hy = -0.56f + t * 0.88f
                                val hz = -0.18f + rand.nextFloat() * 0.36f
                                list.add(ParticlePoint(hx, hy, hz, brightnessMultiplier = 0.85f))
                            }
                        }

                        // 4. Slender Neck, Collarbone & Contoured Bust (28%)
                        else -> {
                            val isNeck = rand.nextFloat() < 0.26f
                            if (isNeck) {
                                val theta = rand.nextFloat() * 2f * PI.toFloat()
                                val nx = 0.10f * cos(theta)
                                val ny = -0.14f + rand.nextFloat() * 0.22f
                                val nz = 0.10f * sin(theta)
                                list.add(ParticlePoint(nx, ny, nz, brightnessMultiplier = 1.0f))
                            } else {
                                val tx = (rand.nextFloat() - 0.5f) * 0.96f
                                val shoulderDrop = (abs(tx) / 0.50f).coerceIn(0f, 1f).pow(1.4f) * 0.18f
                                val ty = 0.08f + shoulderDrop + rand.nextFloat() * 0.54f
                                val torsoTaper = 1f - ((ty - 0.08f) / 0.72f).coerceIn(0f, 0.35f)
                                val tz = (rand.nextFloat() - 0.5f) * 0.32f
                                list.add(ParticlePoint(tx * torsoTaper, ty, tz, brightnessMultiplier = 0.95f))
                            }
                        }
                    }
                }
            }

            AvatarForm.MALE -> {
                for (i in 0 until PARTICLE_COUNT) {
                    val roll = rand.nextFloat()
                    when {
                        // 1. Bright eyes, brow & jawline core (16%)
                        roll < 0.16f -> {
                            val featureRoll = rand.nextFloat()
                            val (fx, fy, fz) = when {
                                featureRoll < 0.42f -> {
                                    val side = if (rand.nextBoolean()) 1f else -1f
                                    Triple(
                                        side * (0.10f + (rand.nextFloat() - 0.5f) * 0.04f),
                                        -0.41f + (rand.nextFloat() - 0.5f) * 0.03f,
                                        0.27f + rand.nextFloat() * 0.08f
                                    )
                                }
                                else -> {
                                    Triple(
                                        (rand.nextFloat() - 0.5f) * 0.26f,
                                        -0.36f + (rand.nextFloat() - 0.5f) * 0.24f,
                                        0.24f + rand.nextFloat() * 0.12f
                                    )
                                }
                            }
                            list.add(ParticlePoint(fx, fy, fz, brightnessMultiplier = 1.75f, isFaceFeature = true))
                        }

                        // 2. Sculpted angular head & defined square jaw (30%)
                        roll < 0.46f -> {
                            val theta = rand.nextFloat() * 2f * PI.toFloat()
                            val phi = (rand.nextFloat() - 0.5f) * PI.toFloat()
                            val jawSquare = if (phi > 0.2f) 0.92f else 1f
                            val rx = 0.30f * jawSquare * cos(phi) * cos(theta)
                            val ry = -0.37f + 0.29f * sin(phi)
                            val rz = 0.30f * jawSquare * cos(phi) * sin(theta)
                            list.add(ParticlePoint(rx, ry, rz, brightnessMultiplier = 1.12f))
                        }

                        // 3. Tight Short Crown Hair Silhouette (16%)
                        roll < 0.62f -> {
                            val theta = rand.nextFloat() * PI.toFloat()
                            val cx = 0.31f * cos(theta) + (rand.nextFloat() - 0.5f) * 0.08f
                            val cy = -0.49f - 0.21f * sin(theta) + (rand.nextFloat() - 0.5f) * 0.05f
                            val cz = (rand.nextFloat() - 0.5f) * 0.38f
                            list.add(ParticlePoint(cx, cy, cz, brightnessMultiplier = 0.92f))
                        }

                        // 4. Strong Neck & Broad Athletic Shoulders / Upper Chest (38%)
                        else -> {
                            val isNeck = rand.nextFloat() < 0.22f
                            if (isNeck) {
                                val theta = rand.nextFloat() * 2f * PI.toFloat()
                                val nx = 0.145f * cos(theta)
                                val ny = -0.12f + rand.nextFloat() * 0.20f
                                val nz = 0.145f * sin(theta)
                                list.add(ParticlePoint(nx, ny, nz, brightnessMultiplier = 1.05f))
                            } else {
                                val tx = (rand.nextFloat() - 0.5f) * 1.38f
                                val shoulderDrop = (abs(tx) / 0.70f).coerceIn(0f, 1f).pow(1.6f) * 0.14f
                                val ty = 0.06f + shoulderDrop + rand.nextFloat() * 0.58f
                                val chestTaper = 1f - ((ty - 0.06f) / 0.75f).coerceIn(0f, 0.28f)
                                val tz = (rand.nextFloat() - 0.5f) * 0.38f
                                list.add(ParticlePoint(tx * chestTaper, ty, tz, brightnessMultiplier = 1.0f))
                            }
                        }
                    }
                }
            }
        }
        return list
    }
}

/**
 * Interactive Particle 3D Avatar System with smooth morphing transitions,
 * MAX-inspired active glow pulse, and SARA-inspired floating "+" & sparkle accents.
 */
@Composable
fun ParticleAvatar3D(
    currentForm: AvatarForm,
    status: AssistantStatus,
    modifier: Modifier = Modifier,
    disableAnimations: Boolean = false
) {
    val coreShape = remember { AvatarShapeGenerator.generatePoints(AvatarForm.CORE) }
    val femaleShape = remember { AvatarShapeGenerator.generatePoints(AvatarForm.FEMALE) }
    val maleShape = remember { AvatarShapeGenerator.generatePoints(AvatarForm.MALE) }

    val targetShape = when (currentForm) {
        AvatarForm.CORE -> coreShape
        AvatarForm.FEMALE -> femaleShape
        AvatarForm.MALE -> maleShape
    }

    // Initialize directly with targetShape so reopening the app never flashes CORE first.
    val currentPoints = remember { targetShape.toMutableList() }
    val startPoints = remember { targetShape.toMutableList() }
    val morphProgress = remember { Animatable(1f) }
    var lastRenderedForm by remember { mutableStateOf(currentForm) }

    // Ambient floating "+" crosshairs & sparkles around the perimeter
    val sparkles = remember {
        val rand = Random(108)
        List(18) { idx ->
            AmbientSparkle(
                angleRad = (idx.toFloat() / 18f) * 2f * PI.toFloat() + (rand.nextFloat() - 0.5f) * 0.25f,
                distanceFactor = 0.78f + rand.nextFloat() * 0.34f,
                phaseOffset = rand.nextFloat() * 2f * PI.toFloat(),
                speedMult = 0.7f + rand.nextFloat() * 0.6f,
                isCrosshair = idx % 3 == 0,
                sizeDp = 2.2f + rand.nextFloat() * 2.3f
            )
        }
    }

    LaunchedEffect(currentForm) {
        if (lastRenderedForm != currentForm) {
            for (i in 0 until AvatarShapeGenerator.PARTICLE_COUNT) {
                startPoints[i] = currentPoints[i]
            }
            lastRenderedForm = currentForm
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particle_motion")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = -0.22f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_rotate_y"
    )

    val floatPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_sparkle_phase"
    )

    val audioPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.SPEAKING -> 420
                    AssistantStatus.LISTENING -> 650
                    AssistantStatus.PROCESSING -> 520
                    else -> 2100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio_pulse"
    )

    val baseThemeColor = when (status) {
        AssistantStatus.SPEAKING -> ElectricCyan
        AssistantStatus.LISTENING -> WarningAmber
        AssistantStatus.PROCESSING -> HologramBlue
        AssistantStatus.SLEEP -> Color(0xFF64748B)
        else -> CyberCyan
    }

    val glowHaloColor = when (status) {
        AssistantStatus.SPEAKING -> ReactorGold
        AssistantStatus.LISTENING -> WarningAmber
        AssistantStatus.PROCESSING -> ElectricBlue
        AssistantStatus.SLEEP -> Color(0xFF1E293B)
        else -> ElectricCyan
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val floatOffsetPx = if (!disableAnimations) sin(floatPhase) * 3.5f * density else 0f
            val centerY = canvasHeight / 2f + floatOffsetPx
            val scaleRadius = minOf(canvasWidth, canvasHeight) * 0.42f

            val pulseFactor = if (!disableAnimations) audioPulse else 1f
            val statusIntensity = when (status) {
                AssistantStatus.SPEAKING -> 1.45f * pulseFactor
                AssistantStatus.LISTENING -> 1.35f * pulseFactor
                AssistantStatus.PROCESSING -> 1.25f * pulseFactor
                AssistantStatus.SLEEP -> 0.52f
                else -> 1.05f * (0.94f + (pulseFactor - 0.88f) * 0.35f)
            }

            // 1. Soft transparent multi-layer ambient glow (no card/box border)
            val outerGlowRadius = scaleRadius * 1.18f * (if (status == AssistantStatus.SPEAKING || status == AssistantStatus.LISTENING) 0.96f + (pulseFactor - 0.88f) * 0.25f else 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        baseThemeColor.copy(alpha = (0.24f * statusIntensity).coerceIn(0.06f, 0.38f)),
                        glowHaloColor.copy(alpha = (0.10f * statusIntensity).coerceIn(0.03f, 0.20f)),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = outerGlowRadius
                ),
                radius = outerGlowRadius,
                center = Offset(centerX, centerY)
            )

            // Subtle pulsing orbital ring when speaking or listening
            if (status == AssistantStatus.SPEAKING || status == AssistantStatus.LISTENING) {
                drawCircle(
                    color = baseThemeColor.copy(alpha = (0.32f * (pulseFactor - 0.6f)).coerceIn(0.08f, 0.42f)),
                    radius = scaleRadius * (0.86f + (pulseFactor - 0.88f) * 0.22f),
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * density)
                )
            }

            // 2. SARA-inspired floating "+" crosshairs and sparkle dots around the avatar
            sparkles.forEach { sp ->
                val phase = (if (!disableAnimations) floatPhase * sp.speedMult else 0f) + sp.phaseOffset
                val driftY = sin(phase) * 5f * density
                val driftX = cos(phase * 0.7f) * 3f * density
                val sx = centerX + cos(sp.angleRad) * (scaleRadius * sp.distanceFactor) + driftX
                val sy = (canvasHeight / 2f) + sin(sp.angleRad) * (scaleRadius * sp.distanceFactor) + driftY
                val spAlpha = ((0.35f + 0.45f * ((sin(phase * 1.4f) + 1f) / 2f)) * (if (status == AssistantStatus.SLEEP) 0.4f else 1f)).coerceIn(0.1f, 0.85f)
                val spColor = if (sp.isCrosshair) ElectricCyan.copy(alpha = spAlpha) else HologramBlue.copy(alpha = spAlpha * 0.85f)

                if (sp.isCrosshair) {
                    val arm = sp.sizeDp * density
                    val strokeW = 1.1f * density
                    drawLine(
                        color = spColor,
                        start = Offset(sx - arm, sy),
                        end = Offset(sx + arm, sy),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = spColor,
                        start = Offset(sx, sy - arm),
                        end = Offset(sx, sy + arm),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawCircle(
                        color = spColor,
                        radius = (sp.sizeDp * 0.45f) * density,
                        center = Offset(sx, sy)
                    )
                }
            }

            // 3. Render 820 density-scaled 3D morphing particles
            val morphT = morphProgress.value
            val rot = if (!disableAnimations) rotationAngle else 0f
            val cosRot = cos(rot)
            val sinRot = sin(rot)

            for (i in 0 until AvatarShapeGenerator.PARTICLE_COUNT) {
                val p0 = startPoints[i]
                val p1 = targetShape[i]

                val mx = p0.x + (p1.x - p0.x) * morphT
                val my = p0.y + (p1.y - p0.y) * morphT
                val mz = p0.z + (p1.z - p0.z) * morphT
                val brightnessMult = p0.brightnessMultiplier + (p1.brightnessMultiplier - p0.brightnessMultiplier) * morphT
                val isFace = p1.isFaceFeature

                currentPoints[i] = ParticlePoint(mx, my, mz, brightnessMult, isFace)

                val rotatedX = mx * cosRot - mz * sinRot
                val rotatedZ = mx * sinRot + mz * cosRot

                val cameraDistance = 2.4f
                val perspective = cameraDistance / (cameraDistance - rotatedZ * 0.55f)

                val screenX = centerX + (rotatedX * scaleRadius * perspective)
                val screenY = centerY + (my * scaleRadius * perspective)

                val depthFactor = ((rotatedZ + 1.0f) / 2.0f).coerceIn(0.22f, 1.0f)
                // Density-scaled radius so particles are crisp on high-DPI phones & emulators
                val particleRadius = (0.95f + depthFactor * 1.25f + if (isFace) 0.55f else 0f) * density * perspective
                val alpha = ((0.40f + depthFactor * 0.60f) * brightnessMult * (statusIntensity * 0.85f)).coerceIn(0.18f, 1.0f)

                val particleColor = when {
                    isFace -> Color.White.copy(alpha = alpha)
                    depthFactor > 0.70f -> baseThemeColor.copy(alpha = alpha)
                    else -> HologramBlue.copy(alpha = alpha * 0.88f)
                }

                drawCircle(
                    color = particleColor,
                    radius = particleRadius,
                    center = Offset(screenX, screenY)
                )
            }
        }
    }
}

/**
 * Segmented 3-Option Avatar Selector Tab Bar ("CORE", "FEMALE", "MALE").
 * Sleek pill group with glowing cyan highlight on selected option and accessible touch targets.
 */
@Composable
fun AvatarFormSelector(
    selectedForm: AvatarForm,
    onSelectForm: (AvatarForm) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark.copy(alpha = 0.72f))
            .border(1.dp, GlassBorderCyan, RoundedCornerShape(24.dp))
            .padding(3.dp)
            .testTag("avatar_form_selector_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarForm.entries.forEach { form ->
                val isSelected = form == selectedForm
                val containerColor = if (isSelected) ElectricCyan.copy(alpha = 0.22f) else Color.Transparent
                val borderColor = if (isSelected) ElectricCyan.copy(alpha = 0.9f) else Color.Transparent
                val textColor = if (isSelected) ElectricCyan else TextSecondary

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(containerColor)
                        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                        .clickable { onSelectForm(form) }
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .testTag("avatar_tab_${form.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = form.label,
                        color = textColor,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
