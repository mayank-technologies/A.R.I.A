package com.example.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class GestureResult {
    data class Success(val message: String) : GestureResult()
    data class Failure(val message: String) : GestureResult()
}

/**
 * Android Native AccessibilityService for ARIA Voice Gesture Automation.
 *
 * Capabilities:
 * 1. "scroll this video/reel/shorts", "next reel", "scroll down" -> Simulates upward swipe gesture
 * 2. "pause this video/reel/shorts", "play video", "pause this" -> Simulates center-screen tap gesture
 * 3. "previous reel", "scroll up" -> Simulates downward swipe gesture
 * 4. "double tap", "like this video" -> Simulates center-screen double tap
 *
 * NOTE FOR FLUTTER & MULTI-PLATFORM DEVELOPERS:
 * - This feature is EXCLUSIVE TO ANDROID via Android Accessibility APIs (dispatchGesture).
 * - iOS does NOT allow third-party apps to simulate system-wide touch/swipe gestures on external apps (Apple sandbox security policy).
 */
class AriaAccessibilityGestureService : AccessibilityService() {

    companion object {
        private const val TAG = "AriaAccessibility"

        private var instance: AriaAccessibilityGestureService? = null

        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

        /**
         * Check if ARIA's accessibility service is currently enabled by the user in Android Settings
         */
        fun isServiceRunning(): Boolean = instance != null

        /**
         * System setting check to see if accessibility is enabled for ARIA package
         */
        fun checkAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${AriaAccessibilityGestureService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            return accessibilityEnabled && enabledServices.contains(expectedServiceName)
        }

        /**
         * Open Android System Settings to Accessibility page so user can toggle ARIA ON
         */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            }
        }

        /**
         * Voice Action: Scroll to Next Video / Reel / Short (Swipe Up)
         */
        fun scrollNextVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARIA ko enable kijiye."))
                return
            }
            service.performSwipeUp(callback)
        }

        /**
         * Voice Action: Scroll to Previous Video / Reel (Swipe Down)
         */
        fun scrollPreviousVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARIA ko enable kijiye."))
                return
            }
            service.performSwipeDown(callback)
        }

        /**
         * Voice Action: Pause / Play Video (Single Center Tap)
         */
        fun togglePlayPauseVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARIA ko enable kijiye."))
                return
            }
            service.performCenterTap(callback)
        }

        /**
         * Voice Action: Like Video / Reel (Double Tap at Center)
         */
        fun doubleTapLikeVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARIA ko enable kijiye."))
                return
            }
            service.performDoubleTap(callback)
        }

        /**
         * Voice Action: Read Screen Content Aloud (Extract text from Accessibility Node Tree)
         */
        fun readScreenContent(callback: (String) -> Unit) {
            val service = instance
            if (service == null) {
                callback("Accessibility Service off hai. Pehle Settings me ARC ko permission dijiye.")
                return
            }
            service.extractActiveScreenText(callback)
        }

        /**
         * Voice Action: Auto-tap button by label/name
         */
        fun autoTapButtonByName(buttonLabel: String, callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARC enable kijiye."))
                return
            }
            service.findAndClickNodeByText(buttonLabel, callback)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        Log.d(TAG, "🚀 ARIA Accessibility Gesture Service Connected & Ready!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window/Content event monitoring if needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "ARIA Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
        Log.d(TAG, "ARIA Accessibility Service Destroyed")
    }

    /**
     * Dispatch swipe gesture from bottom to top (Shorts/Reels swipe next)
     */
    private fun performSwipeUp(callback: ((GestureResult) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0 (Nougat) or higher."))
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX = width * 0.5f
        val startY = height * 0.78f // Start near bottom
        val endX = width * 0.5f
        val endY = height * 0.22f   // End near top

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 260)) // 260ms smooth fast swipe
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "✅ Swipe Up (Next Video) Gesture Completed!")
                callback?.invoke(GestureResult.Success("Video scroll ho gaya hai! 📱✨"))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "❌ Swipe Up Gesture Cancelled")
                callback?.invoke(GestureResult.Failure("Swipe gesture cancel ho gaya."))
            }
        }, null)
    }

    /**
     * Dispatch swipe gesture from top to bottom (Previous Reel/Short)
     */
    private fun performSwipeDown(callback: ((GestureResult) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0+"))
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX = width * 0.5f
        val startY = height * 0.25f
        val endX = width * 0.5f
        val endY = height * 0.75f

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 260))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(GestureResult.Success("Pichla video scroll kar diya hai! 📱"))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(GestureResult.Failure("Swipe gesture cancel ho gaya."))
            }
        }, null)
    }

    /**
     * Dispatch single tap at screen center (Toggle Pause / Play)
     */
    private fun performCenterTap(callback: ((GestureResult) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0+"))
            return
        }

        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val centerY = metrics.heightPixels * 0.5f

        val tapPath = Path().apply {
            moveTo(centerX, centerY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 50)) // 50ms tap duration
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "✅ Center Tap (Play/Pause) Gesture Completed!")
                callback?.invoke(GestureResult.Success("Video play/pause toggle kar diya hai! ⏯️"))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "❌ Center Tap Cancelled")
                callback?.invoke(GestureResult.Failure("Tap gesture cancel ho gaya."))
            }
        }, null)
    }

    /**
     * Dispatch double tap at screen center (Like Reel / Video)
     */
    private fun performDoubleTap(callback: ((GestureResult) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0+"))
            return
        }

        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val centerY = metrics.heightPixels * 0.5f

        val tapPath = Path().apply {
            moveTo(centerX, centerY)
        }

        val stroke1 = GestureDescription.StrokeDescription(tapPath, 0, 40)
        val stroke2 = GestureDescription.StrokeDescription(tapPath, 110, 40)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(GestureResult.Success("Video double tap (like) ho gaya! ❤️"))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(GestureResult.Failure("Double tap cancel ho gaya."))
            }
        }, null)
    }

    /**
     * Traverses the AccessibilityNodeInfo tree and extracts all visible text content on screen.
     */
    private fun extractActiveScreenText(callback: (String) -> Unit) {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            callback("Screen content read nahi ho saka (empty window).")
            return
        }

        val collectedText = mutableListOf<String>()
        traverseNodeForText(rootNode, collectedText)
        rootNode.recycle()

        if (collectedText.isEmpty()) {
            callback("Screen par koi readable text nahi mila.")
        } else {
            val fullText = collectedText.distinct().take(12).joinToString(". ")
            callback("Screen par ye likha hai: $fullText")
        }
    }

    private fun traverseNodeForText(node: android.view.accessibility.AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrBlank()) list.add(text)
        else if (!desc.isNullOrBlank()) list.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNodeForText(child, list)
            child?.recycle()
        }
    }

    /**
     * Finds an interactive node containing the target button text and executes ACTION_CLICK.
     */
    private fun findAndClickNodeByText(targetLabel: String, callback: ((GestureResult) -> Unit)?) {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            callback?.invoke(GestureResult.Failure("Screen read nahi ho pa raha hai."))
            return
        }

        val matchedNodes = rootNode.findAccessibilityNodeInfosByText(targetLabel)
        if (matchedNodes.isNullOrEmpty()) {
            rootNode.recycle()
            callback?.invoke(GestureResult.Failure("'$targetLabel' button screen par nahi mila."))
            return
        }

        for (node in matchedNodes) {
            if (node.isClickable) {
                val clicked = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    rootNode.recycle()
                    callback?.invoke(GestureResult.Success("'$targetLabel' button par click kar diya hai! 👆"))
                    return
                }
            }
            // Check parent if child is text view inside button
            val parent = node.parent
            if (parent != null && parent.isClickable) {
                val clicked = parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    parent.recycle()
                    rootNode.recycle()
                    callback?.invoke(GestureResult.Success("'$targetLabel' button par click kar diya hai! 👆"))
                    return
                }
                parent.recycle()
            }
        }

        rootNode.recycle()
        callback?.invoke(GestureResult.Failure("'$targetLabel' mila lekin click action perform nahi ho saka."))
    }
}
