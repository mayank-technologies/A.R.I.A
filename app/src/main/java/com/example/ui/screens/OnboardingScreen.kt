package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ArcBrandLogo
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CobaltTech
import com.example.ui.theme.DarkTitanium
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.HologramBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Futuristic ARC Tech-HUD Onboarding Screen:
 * - Background ([OnboardingHudBackdrop]): DeepSpace-to-CarbonBlack gradient with subtle radial
 *   energy blooms, faint 44.dp HUD grid lines, and gentle drifting cyan/blue micro-particles.
 * - Top Brand Emblem ([OnboardingCoreHeader]): Sharp geometric circuit-"A" logo ([ArcBrandLogo])
 *   framed in a multi-layer frosted glassmorphism badge with soft outer cyan/blue glow and
 *   twinkling shimmer particles matching the Voice HUD avatar palette (replaces orange target).
 * - Typography Hierarchy: Wide letter-spaced telemetry subtitle -> Bold heading with ElectricCyan
 *   text-shadow glow -> Muted description with breathable spacing above and below.
 * - Input Field: Unified translucent glassmorphism card with 22.dp smooth corners, glowing thin
 *   cyan border (brightening on focus), and an accented glowing Person icon badge.
 * - CTA Button ([OnboardingGlowButton]): ElectricCyan -> HologramBlue -> CobaltTech gradient pill
 *   with matching 22.dp corners and amplified outer/border glow on hover and press.
 */
@Composable
fun OnboardingScreen(
    onComplete: (String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isInputFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        val trimmed = nameText.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "Please enter your name, Boss!"
        } else {
            keyboardController?.hide()
            onComplete(trimmed)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepSpace, CarbonBlack, DeepSpace)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 600.dp

        // 1. Subtle Tech-HUD Grid + Dual Radial Blooms + Drifting Micro-Particles Backdrop
        OnboardingHudBackdrop(modifier = Modifier.fillMaxSize())

        // 2. Centered, Breathable HUD Content Column respecting Safe Insets & Keyboard
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 26.dp,
                    vertical = if (isCompactHeight) 18.dp else 32.dp
                )
        ) {
            // Top Icon Widget: Frosted Glassmorphism Frame + Outer Glow + Shimmer Particles + Geometric 'A'
            OnboardingCoreHeader(
                outerSize = if (isCompactHeight) 128.dp else 156.dp,
                badgeSize = if (isCompactHeight) 104.dp else 124.dp,
                logoSize = if (isCompactHeight) 84.dp else 100.dp
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 36.dp))

            // Title Hierarchy — Level 1: Subtle Letter-Spaced Telemetry Subtitle
            Text(
                text = "ARC SYSTEM INITIALIZATION",
                color = HologramBlue.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.4.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Title Hierarchy — Level 2: Bold Primary Heading with Cyan Accent Text-Shadow Glow
            Text(
                text = "What should I call you?",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = if (isCompactHeight) 25.sp else 29.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = ElectricCyan.copy(alpha = 0.48f),
                        offset = Offset(0f, 0f),
                        blurRadius = 22f
                    )
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Title Hierarchy — Level 3: Muted Readable Description with Extra Vertical Breathing Room
            Text(
                text = "Enter your name to personalize your AI voice assistant experience.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 26.dp else 40.dp))

            // 3. Glassmorphism Name Input Card (Frosted Translucent Card + Thin Glowing Cyan Border)
            val inputBorderColor by animateColorAsState(
                targetValue = when {
                    errorMessage != null -> WarningAmber
                    isInputFocused -> ElectricCyan
                    else -> GlassBorderCyan
                },
                animationSpec = tween(durationMillis = 220),
                label = "onboarding_input_border"
            )
            val inputGlowAlpha by animateFloatAsState(
                targetValue = if (isInputFocused) 0.32f else 0.12f,
                animationSpec = tween(durationMillis = 220),
                label = "onboarding_input_glow"
            )

            val inputShape = RoundedCornerShape(22.dp)

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Diffused behind-card frosted cyan/blue glow layer (Gaussian blur on Android 12+)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(14.dp) else Modifier)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = inputGlowAlpha * 0.65f),
                                        HologramBlue.copy(alpha = inputGlowAlpha * 0.50f),
                                        ElectricCyan.copy(alpha = inputGlowAlpha * 0.65f)
                                    )
                                ),
                                shape = inputShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(inputShape)
                            .background(
                                // Frosted multi-stop translucent glass using theme surface tokens
                                Brush.verticalGradient(
                                    colors = listOf(
                                        DarkTitanium.copy(alpha = 0.80f),
                                        GlassBackground
                                    )
                                )
                            )
                            .drawBehind {
                                // Subtle top-edge frosted glass specular sheen
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.07f),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = size.height * 0.45f
                                    )
                                )
                            }
                            .border(
                                width = 1.2.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        inputBorderColor,
                                        HologramBlue.copy(alpha = if (isInputFocused) 0.92f else 0.45f),
                                        inputBorderColor
                                    )
                                ),
                                shape = inputShape
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                    TextField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            if (errorMessage != null) errorMessage = null
                        },
                        placeholder = {
                            Text(
                                text = "Enter your name...",
                                color = TextMuted,
                                fontSize = 15.sp
                            )
                        },
                        leadingIcon = {
                            // Accent-glowing Person badge inside the glassmorphism field
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    ElectricCyan.copy(alpha = if (isInputFocused) 0.42f else 0.24f),
                                                    Color.Transparent
                                                )
                                            ),
                                            radius = size.minDimension * 0.76f
                                        )
                                    }
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.13f))
                                    .border(
                                        width = 1.dp,
                                        color = ElectricCyan.copy(alpha = if (isInputFocused) 0.75f else 0.45f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Name Icon",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = ElectricCyan
                        ),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .testTag("onboarding_name_input")
                    )
                    }
                }

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(150))
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = WarningAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 22.dp else 32.dp))

            // 4. Gradient Glow Primary CTA Button ("GET STARTED ->")
            OnboardingGlowButton(
                text = "GET STARTED",
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_continue_button")
            )
        }
    }
}

private data class HeaderSparkle(
    val angleRad: Float,
    val radiusFraction: Float,
    val phaseOffset: Float,
    val sizeDp: Float,
    val isCrosshair: Boolean,
    val isCyan: Boolean
)

/**
 * Top Futuristic ARC Logo Header Widget:
 * - Retains the sharp geometric circuit-"A" [ArcBrandLogo] inside its cyan/blue glass frame.
 * - Softens the outer radial glow into a multi-stop atmospheric halo and adds avatar-style
 *   drifting micro-particles + subtle "+" crosshair shimmers around the glass frame.
 */
@Composable
private fun OnboardingCoreHeader(
    outerSize: Dp = 156.dp,
    badgeSize: Dp = 124.dp,
    logoSize: Dp = 100.dp
) {
    val cardShape = RoundedCornerShape(28.dp)
    val sparkles = remember {
        val rng = Random(4242)
        List(16) { idx ->
            HeaderSparkle(
                angleRad = (idx.toFloat() / 16f) * 2f * PI.toFloat() + (rng.nextFloat() - 0.5f) * 0.22f,
                radiusFraction = 0.415f + rng.nextFloat() * 0.085f,
                phaseOffset = rng.nextFloat() * 2f * PI.toFloat(),
                sizeDp = 1.2f + rng.nextFloat() * 1.4f,
                isCrosshair = idx % 4 == 0,
                isCyan = idx % 2 == 0
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_header_shimmer")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "header_glow_pulse"
    )
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "header_shimmer_phase"
    )

    Box(
        modifier = Modifier.size(outerSize),
        contentAlignment = Alignment.Center
    ) {
        // Soft diffused outer cyan/blue halo + avatar-style particle & crosshair shimmer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to ElectricCyan.copy(alpha = 0.17f * glowPulse),
                        0.48f to HologramBlue.copy(alpha = 0.10f * glowPulse),
                        0.78f to ElectricBlue.copy(alpha = 0.04f),
                        1.0f to Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.52f * glowPulse
                ),
                radius = size.minDimension * 0.52f * glowPulse,
                center = center
            )

            sparkles.forEach { sp ->
                val drift = shimmerPhase + sp.phaseOffset
                val alpha = 0.16f + 0.52f * (0.5f + 0.5f * sin(drift))
                val r = size.minDimension * sp.radiusFraction + sin(drift * 0.65f) * 2.2.dp.toPx()
                val px = center.x + cos(sp.angleRad) * r
                val py = center.y + sin(sp.angleRad) * r
                val dotColor = if (sp.isCyan) ElectricCyan else HologramBlue
                val radiusPx = sp.sizeDp.dp.toPx()

                if (sp.isCrosshair) {
                    val arm = radiusPx * 2.1f
                    drawLine(
                        color = Color.White.copy(alpha = alpha * 0.85f),
                        start = Offset(px - arm, py),
                        end = Offset(px + arm, py),
                        strokeWidth = 0.9.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = dotColor.copy(alpha = alpha),
                        start = Offset(px, py - arm),
                        end = Offset(px, py + arm),
                        strokeWidth = 0.9.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    drawCircle(
                        color = dotColor.copy(alpha = alpha),
                        radius = radiusPx,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Frosted glassmorphism rounded-square badge framing the sharp geometric circuit-"A"
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(cardShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            DarkTitanium.copy(alpha = 0.76f),
                            GlassBackground
                        )
                    )
                )
                .border(
                    width = 1.25.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.78f),
                            HologramBlue.copy(alpha = 0.28f),
                            ElectricCyan.copy(alpha = 0.65f)
                        )
                    ),
                    shape = cardShape
                )
                .testTag("onboarding_arc_logo"),
            contentAlignment = Alignment.Center
        ) {
            ArcBrandLogo(
                size = logoSize,
                animatePulse = true,
                showAmbientGlow = true
            )
        }
    }
}

/**
 * Gradient Glow Primary Action Button:
 * - Replaces the flat solid cyan button with an ElectricCyan -> HologramBlue -> CobaltTech gradient.
 * - Matches the 22.dp rounded geometry of the glassmorphism input card above.
 * - Amplifies outer aura thickness, opacity, and rim highlight dynamically on hover and press.
 */
@Composable
private fun OnboardingGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val glowStrength by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.66f
            isHovered -> 0.50f
            else -> 0.26f
        },
        animationSpec = tween(durationMillis = 160),
        label = "button_glow_strength"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
        label = "button_press_scale"
    )

    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .scale(pressScale)
            .drawBehind {
                // Soft outer accent halo amplifying smoothly on hover or press
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = glowStrength),
                            HologramBlue.copy(alpha = glowStrength * 0.9f),
                            CobaltTech.copy(alpha = glowStrength * 0.8f)
                        )
                    ),
                    cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx()),
                    style = Stroke(width = if (isPressed || isHovered) 11.dp.toPx() else 6.dp.toPx())
                )
            }
            .height(56.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ElectricCyan,
                        HologramBlue,
                        CobaltTech
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (isPressed || isHovered) 0.58f else 0.30f)
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = DeepSpace.copy(alpha = 0.25f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = DeepSpace,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Continue",
                tint = DeepSpace,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class HudStar(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val phase: Float,
    val isCyan: Boolean
)

/**
 * Subtle Tech-HUD Backdrop:
 * Combines top/bottom atmospheric radial energy blooms, faint 44.dp blueprint grid lines,
 * and low-opacity drifting micro-particles consistent with the Voice HUD canvas without
 * distracting from foreground controls.
 */
@Composable
private fun OnboardingHudBackdrop(modifier: Modifier = Modifier) {
    val stars = remember {
        val rng = Random(2026)
        List(28) { idx ->
            HudStar(
                xFraction = rng.nextFloat(),
                yFraction = rng.nextFloat(),
                radiusDp = 1.0f + rng.nextFloat() * 1.4f,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                isCyan = idx % 2 == 0
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hud_backdrop_drift")
    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Top-center subtle radial glow behind the logo header
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = 0.11f),
                    ElectricBlue.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.26f),
                radius = maxOf(w, h) * 0.55f
            )
        )

        // Subtle secondary ambient floor glow near CTA button
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    CobaltTech.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.86f),
                radius = maxOf(w, h) * 0.45f
            )
        )

        // Faint blueprint HUD coordinate grid
        val stepPx = 44.dp.toPx()
        val gridLineColor = ElectricCyan.copy(alpha = 0.032f)
        var x = stepPx
        while (x < w) {
            drawLine(
                color = gridLineColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 0.8.dp.toPx()
            )
            x += stepPx
        }
        var y = stepPx
        while (y < h) {
            drawLine(
                color = gridLineColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 0.8.dp.toPx()
            )
            y += stepPx
        }

        // Low-opacity twinkling micro-particles
        stars.forEach { star ->
            val shimmer = 0.5f + 0.5f * sin(driftPhase + star.phase)
            val px = star.xFraction * w + cos(driftPhase * 0.5f + star.phase) * 3.dp.toPx()
            val py = star.yFraction * h + sin(driftPhase * 0.5f + star.phase) * 3.dp.toPx()
            val color = if (star.isCyan) ElectricCyan else HologramBlue
            drawCircle(
                color = color.copy(alpha = 0.08f + shimmer * 0.16f),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}
