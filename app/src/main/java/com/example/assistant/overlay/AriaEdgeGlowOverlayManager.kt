package com.example.assistant.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for Software-based Screen Edge Glow Overlay.
 * Uses SYSTEM_ALERT_WINDOW to draw a non-intrusive glowing neon border
 * across any running app when ARIA is active.
 */
object AriaEdgeGlowOverlayManager {

    private var windowManager: WindowManager? = null
    private var glowView: AriaEdgeGlowView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isGlowActive = MutableStateFlow(false)
    val isGlowActive: StateFlow<Boolean> = _isGlowActive.asStateFlow()

    private val _glowState = MutableStateFlow(AriaEdgeGlowView.GlowState.IDLE)
    val glowState: StateFlow<AriaEdgeGlowView.GlowState> = _glowState.asStateFlow()

    /**
     * Checks if SYSTEM_ALERT_WINDOW permission is granted.
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Opens system settings for granting "Display over other apps" permission.
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * Displays the glowing edge overlay across the entire device screen.
     * Uses FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE so touches pass straight through!
     */
    fun showEdgeGlow(context: Context, state: AriaEdgeGlowView.GlowState = AriaEdgeGlowView.GlowState.LISTENING) {
        if (!isOverlayPermissionGranted(context)) {
            return
        }

        mainHandler.post {
            try {
                if (windowManager == null) {
                    windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                }

                if (glowView == null) {
                    glowView = AriaEdgeGlowView(context.applicationContext)

                    val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                    }

                    // Key WindowManager flags: FLAG_NOT_TOUCHABLE ensures users can tap underneath without interference
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    )

                    windowManager?.addView(glowView, params)
                    _isGlowActive.value = true
                }

                _glowState.value = state
                glowView?.setState(state)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Updates the glow visual state (Listening vs Speaking vs Processing).
     */
    fun updateGlowState(state: AriaEdgeGlowView.GlowState) {
        mainHandler.post {
            _glowState.value = state
            glowView?.setState(state)
        }
    }

    /**
     * Hides the glowing edge overlay and releases resources.
     */
    fun hideEdgeGlow() {
        mainHandler.post {
            try {
                if (glowView != null && windowManager != null) {
                    glowView?.stopAnimations()
                    windowManager?.removeView(glowView)
                    glowView = null
                    _isGlowActive.value = false
                    _glowState.value = AriaEdgeGlowView.GlowState.IDLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Auto pulse test for a duration in milliseconds, then automatically dismisses.
     */
    fun pulseForDuration(context: Context, durationMs: Long = 4000, state: AriaEdgeGlowView.GlowState = AriaEdgeGlowView.GlowState.LISTENING) {
        showEdgeGlow(context, state)
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            hideEdgeGlow()
        }, durationMs)
    }
}
