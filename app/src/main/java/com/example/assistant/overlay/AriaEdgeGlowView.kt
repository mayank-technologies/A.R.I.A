package com.example.assistant.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Custom Floating Edge Glow View.
 * Renders a futuristic, subtle animated glowing neon border along device screen edges
 * when ARIA detects wake-word, listens, or speaks.
 *
 * Designed to be used with SYSTEM_ALERT_WINDOW and FLAG_NOT_TOUCHABLE so normal phone
 * usage is never obstructed.
 */
class AriaEdgeGlowView(context: Context) : View(context) {

    enum class GlowState {
        IDLE,
        LISTENING,
        SPEAKING,
        PROCESSING
    }

    private var currentState = GlowState.LISTENING

    // Paints
    private val borderGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val softOuterGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val rectF = RectF()
    private var cornerRadius = 32f

    // Animation values
    private var pulseAlpha = 1.0f
    private var sweepAngleOffset = 0f
    private var pulseAnimator: ValueAnimator? = null
    private var rotationAnimator: ValueAnimator? = null

    // Theme Colors
    private val cyberCyan = Color.parseColor("#00E5FF")
    private val electricBlue = Color.parseColor("#0070F3")
    private val neonPurple = Color.parseColor("#9D00FF")
    private val emeraldGreen = Color.parseColor("#10B981")

    init {
        // Transparent background so only the outer edge glow is visible
        setBackgroundColor(Color.TRANSPARENT)
        startGlowAnimations()
    }

    fun setState(state: GlowState) {
        currentState = state
        invalidate()
    }

    private fun startGlowAnimations() {
        // Breathing pulse animation
        pulseAnimator = ValueAnimator.ofFloat(0.35f, 1.0f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                pulseAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Slow rotation angle for dynamic cyberpunk sweep effect
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                sweepAngleOffset = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokeWidth = 8f * resources.displayMetrics.density
        borderGlowPaint.strokeWidth = strokeWidth
        softOuterGlowPaint.strokeWidth = strokeWidth * 2.2f

        val halfStroke = softOuterGlowPaint.strokeWidth / 2f
        rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
        cornerRadius = 28f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val primaryColor = when (currentState) {
            GlowState.LISTENING -> cyberCyan
            GlowState.SPEAKING -> emeraldGreen
            GlowState.PROCESSING -> neonPurple
            GlowState.IDLE -> cyberCyan
        }

        val secondaryColor = when (currentState) {
            GlowState.LISTENING -> electricBlue
            GlowState.SPEAKING -> cyberCyan
            GlowState.PROCESSING -> electricBlue
            GlowState.IDLE -> electricBlue
        }

        // Gradient shader along screen boundary
        val gradient = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(primaryColor, secondaryColor, primaryColor),
            floatArrayOf(0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // 1. Soft feathered ambient glow layer
        softOuterGlowPaint.shader = gradient
        softOuterGlowPaint.alpha = (pulseAlpha * 90).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, softOuterGlowPaint)

        // 2. Crisp neon inner line
        borderGlowPaint.shader = gradient
        borderGlowPaint.alpha = (pulseAlpha * 240).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderGlowPaint)
    }

    fun stopAnimations() {
        pulseAnimator?.cancel()
        rotationAnimator?.cancel()
    }
}
