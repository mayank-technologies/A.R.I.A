package com.example.ui.screens

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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
 * Futuristic JARVIS / ARC HUD Onboarding Screen:
 * - Background: DeepSpace-to-CarbonBlack canvas with subtle radial energy bloom,
 *   blueprint HUD grid, and softly drifting cyan/blue micro-particles.
 * - Top Brand Emblem: Sharp geometric circuit-"A" vector logo framed inside a
 *   clean chamfered dark glassmorphism badge (replaces old radar-ring / orb).
 * - Typography Hierarchy: Telemetry subtitle -> Bold primary prompt with cyan
 *   text-shadow glow -> Muted readable description with generous breathing space.
 * - Input Field: Unified translucent glassmorphism card with thin glowing cyan
 *   border and glowing accent Person badge (no harsh double inner borders).
 * - CTA Button: Horizontal ElectricCyan -> HologramBlue -> CobaltTech gradient
 *   button with interactive hover/press glow amplification.
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

        // 1. Subtle Futuristic Tech-HUD Grid + Radial Bloom + Drifting Particles Background
        OnboardingHudBackdrop(modifier = Modifier.fillMaxSize())

        // 2. Responsive Centered HUD Content Column with Safe Area & Keyboard Insets
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
                    vertical = if (isCompactHeight) 16.dp else 28.dp
                )
        ) {
            // Top Brand Emblem: Sharp Geometric Circuit-"A" Logo
            OnboardingCoreHeader(
                badgeSize = if (isCompactHeight) 104.dp else 124.dp,
                logoSize = if (isCompactHeight) 84.dp else 102.dp
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 34.dp))

            // Title Hierarchy — Level 1: Subtle Letter-Spaced Telemetry Label
            Text(
                text = "ARC SYSTEM INITIALIZATION",
                color = HologramBlue.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Title Hierarchy — Level 2: Bold Primary Heading with Subtle Cyan Accent Glow
            Text(
                text = "What should I call you?",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = if (isCompactHeight) 25.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = ElectricCyan.copy(alpha = 0.45f),
                        offset = Offset(0f, 0f),
                        blurRadius = 20f
                    )
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Title Hierarchy — Level 3: Muted Description with Breathing Room
            Text(
                text = "Enter your name to personalize your AI voice assistant experience.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 24.dp else 38.dp))

            // 3. Glassmorphism Name Input Card (Translucent Frost + Thin Glowing Cyan Border)
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
                targetValue = if (isInputFocused) 0.28f else 0.10f,
                animationSpec = tween(durationMillis = 220),
                label = "onboarding_input_glow"
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Subtle outer ambient neon border bloom
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = inputGlowAlpha),
                                        HologramBlue.copy(alpha = inputGlowAlpha * 0.85f),
                                        ElectricCyan.copy(alpha = inputGlowAlpha)
                                    )
                                ),
                                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                                style = Stroke(width = 6.dp.toPx())
                            )
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x990D182E),
                                    GlassBackground
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    inputBorderColor,
                                    HologramBlue.copy(alpha = if (isInputFocused) 0.9f else 0.45f),
                                    inputBorderColor
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
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
                            // Glowing Accent Badge around the Person Icon
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    ElectricCyan.copy(alpha = if (isInputFocused) 0.36f else 0.22f),
                                                    Color.Transparent
                                                )
                                            ),
                                            radius = size.minDimension * 0.72f
                                        )
                                    }
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.12f))
                                    .border(
                                        width = 1.dp,
                                        color = ElectricCyan.copy(alpha = 0.45f),
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
                        modifier = Modifier.padding(start = 10.dp, top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 30.dp))

            // 4. Gradient Glow CTA Button ("GET STARTED ->") matching HUD rounded geometry
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

/**
 * Top Futuristic ARC Geometric Logo Header:
 * Uses the sharp angular circuit-"A" brand emblem ([ArcBrandLogo]) framed inside
 * a sleek dark obsidian glassmorphic rounded-square badge with subtle corner glow—
 * intentionally avoiding circular radar rings or particle-orb repetition.
 */
@Composable
private fun OnboardingCoreHeader(
    badgeSize: Dp = 124.dp,
    logoSize: Dp = 102.dp
) {
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .size(badgeSize)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0x8808142B),
                        GlassBackground
                    )
                )
            )
            .border(
                width = 1.25.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.72f),
                        HologramBlue.copy(alpha = 0.25f),
                        ElectricCyan.copy(alpha = 0.62f)
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

/**
 * Gradient Glow Primary Action Button:
 * Features an ElectricCyan -> HologramBlue -> CobaltTech gradient, rounded 20.dp corners
 * matching the glassmorphism card above, and dynamic glow amplification on hover/press.
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
            isPressed -> 0.62f
            isHovered -> 0.48f
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

    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .scale(pressScale)
            .drawBehind {
                // Soft outer gradient halo whose intensity intensifies on hover/press
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = glowStrength),
                            HologramBlue.copy(alpha = glowStrength * 0.9f),
                            CobaltTech.copy(alpha = glowStrength * 0.8f)
                        )
                    ),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = if (isPressed || isHovered) 10.dp.toPx() else 6.dp.toPx())
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
                    color = Color.White.copy(alpha = if (isPressed || isHovered) 0.55f else 0.30f)
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
 * Combines a soft centered radial glow, faint coordinate grid lines,
 * and gentle ambient micro-particles so the background matches the avatar HUD aesthetic
 * without competing with foreground readability.
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

        // Top-center subtle radial glow behind the HUD core
        val glowCenter = Offset(w * 0.5f, h * 0.28f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = 0.11f),
                    ElectricBlue.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = glowCenter,
                radius = maxOf(w, h) * 0.55f
            )
        )

        // Subtle blueprint HUD grid texture
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
