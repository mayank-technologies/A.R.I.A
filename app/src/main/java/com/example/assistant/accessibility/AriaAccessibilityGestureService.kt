package com.example.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed class GestureResult {
    data class Success(val message: String) : GestureResult()
    data class Failure(val message: String) : GestureResult()
}

/**
 * Android Native AccessibilityService for ARC Voice Gestures + Screen Vision & ML Kit OCR/Translation.
 *
 * Integrated Capabilities:
 * 1. Reels/Shorts Gestures: scroll next, scroll previous, play/pause center tap, double-tap like.
 * 2. Screen Text Reading: AccessibilityNodeInfo tree extraction + Android 11+ takeScreenshot() with
 *    ML Kit On-Device OCR fallback for images/posters/video frames.
 * 3. Named Button Auto-Tap: Tries Accessibility ACTION_CLICK first and falls back to exact
 *    coordinate tap via dispatchGesture(bounds.centerX(), bounds.centerY()).
 * 4. On-Device Screen Translation: Extracts visible screen text -> identifies language -> translates
 *    on-device using ML Kit Translation.
 */
class AriaAccessibilityGestureService : AccessibilityService() {

    companion object {
        private const val TAG = "AriaAccessibility"

        private var instance: AriaAccessibilityGestureService? = null

        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

        fun isServiceRunning(): Boolean = instance != null

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

        fun scrollNextVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARC ko enable kijiye."))
                return
            }
            service.performSwipeUp(callback)
        }

        fun scrollPreviousVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARC ko enable kijiye."))
                return
            }
            service.performSwipeDown(callback)
        }

        fun togglePlayPauseVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARC ko enable kijiye."))
                return
            }
            service.performCenterTap(callback)
        }

        fun doubleTapLikeVideo(callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARC ko enable kijiye."))
                return
            }
            service.performDoubleTap(callback)
        }

        fun readScreenContent(forceOcr: Boolean = false, callback: (String) -> Unit) {
            val service = instance
            if (service == null) {
                callback("Accessibility Service off hai. Pehle Settings me ARC ko permission dijiye.")
                return
            }
            service.extractActiveScreenTextWithOcrFallback(forceOcr, callback)
        }

        suspend fun readScreenContentSuspend(forceOcr: Boolean = false): String {
            return withTimeoutOrNull(8000L) {
                suspendCancellableCoroutine { cont ->
                    readScreenContent(forceOcr) { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                }
            } ?: "Screen read timeout ho gaya, Boss."
        }

        fun translateScreenContent(targetLanguageCode: String = TranslateLanguage.HINDI, callback: (String) -> Unit) {
            val service = instance
            if (service == null) {
                callback("Accessibility Service off hai. Pehle Settings me ARC ko permission dijiye.")
                return
            }
            service.extractAndTranslateScreen(targetLanguageCode, callback)
        }

        suspend fun translateScreenContentSuspend(targetLanguageCode: String = TranslateLanguage.HINDI): String {
            return withTimeoutOrNull(18000L) {
                suspendCancellableCoroutine { cont ->
                    translateScreenContent(targetLanguageCode) { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                }
            } ?: "Screen translation timeout ho gaya. Offline language model download check karein."
        }

        fun autoTapButtonByName(buttonLabel: String, callback: ((GestureResult) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                callback?.invoke(GestureResult.Failure("Accessibility Service off hai. Pehle Settings me ARC enable kijiye."))
                return
            }
            service.findAndClickNodeByText(buttonLabel, callback)
        }

        suspend fun autoTapButtonByNameSuspend(buttonLabel: String): GestureResult {
            return withTimeoutOrNull(4000L) {
                suspendCancellableCoroutine { cont ->
                    autoTapButtonByName(buttonLabel) { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                }
            } ?: GestureResult.Failure("'$buttonLabel' button tap timeout ho gaya.")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        Log.d(TAG, "ARC Accessibility Gesture & Screen Vision Service Connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Real-time window changes available on demand
    }

    override fun onInterrupt() {
        Log.w(TAG, "ARC Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
        Log.d(TAG, "ARC Accessibility Service Destroyed")
    }

    private fun performSwipeUp(callback: ((GestureResult) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0 (Nougat) or higher."))
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val swipePath = Path().apply {
            moveTo(width * 0.5f, height * 0.78f)
            lineTo(width * 0.5f, height * 0.22f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 260))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(GestureResult.Success("Video scroll ho gaya hai! 📱✨"))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(GestureResult.Failure("Swipe gesture cancel ho gaya."))
            }
        }, null)
    }

    private fun performSwipeDown(callback: ((GestureResult) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0+"))
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val swipePath = Path().apply {
            moveTo(width * 0.5f, height * 0.25f)
            lineTo(width * 0.5f, height * 0.75f)
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

    private fun performCenterTap(callback: ((GestureResult) -> Unit)?) {
        val metrics = resources.displayMetrics
        dispatchTapAt(
            x = metrics.widthPixels * 0.5f,
            y = metrics.heightPixels * 0.5f,
            successMessage = "Video play/pause toggle kar diya hai! ⏯️",
            failureMessage = "Tap gesture cancel ho gaya.",
            callback = callback
        )
    }

    private fun performDoubleTap(callback: ((GestureResult) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0+"))
            return
        }

        val metrics = resources.displayMetrics
        val tapPath = Path().apply {
            moveTo(metrics.widthPixels * 0.5f, metrics.heightPixels * 0.5f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 40))
            .addStroke(GestureDescription.StrokeDescription(tapPath, 110, 40))
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

    private fun dispatchTapAt(
        x: Float,
        y: Float,
        successMessage: String,
        failureMessage: String,
        callback: ((GestureResult) -> Unit)?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(GestureResult.Failure("Gesture dispatch requires Android 7.0+"))
            return
        }

        val tapPath = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 50))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(GestureResult.Success(successMessage))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(GestureResult.Failure(failureMessage))
            }
        }, null)
    }

    /**
     * Collects raw text lines from AccessibilityNodeInfo tree without user-facing prefix.
     */
    private fun collectRawAccessibilityText(): List<String> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val collectedText = mutableListOf<String>()
        traverseNodeForText(rootNode, collectedText)
        @Suppress("DEPRECATION")
        rootNode.recycle()
        return collectedText.distinct()
    }

    private fun traverseNodeForText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrBlank()) list.add(text)
        else if (!desc.isNullOrBlank()) list.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNodeForText(child, list)
            @Suppress("DEPRECATION")
            child?.recycle()
        }
    }

    /**
     * Extracts screen text from Accessibility tree, and falls back to Android 11+ takeScreenshot +
     * ML Kit On-Device OCR when tree text is empty/minimal or forceOcr is requested.
     */
    private fun extractActiveScreenTextWithOcrFallback(
        forceOcr: Boolean,
        callback: (String) -> Unit
    ) {
        val treeItems = collectRawAccessibilityText()
        if (!forceOcr && treeItems.size >= 2) {
            val fullText = treeItems.take(15).joinToString(". ")
            callback("Screen par ye likha hai: $fullText")
            return
        }

        // ML Kit OCR fallback on Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runMlKitOcrOnCurrentScreen { ocrText ->
                val merged = when {
                    ocrText.isNotBlank() && treeItems.isNotEmpty() ->
                        "${treeItems.take(6).joinToString(". ")}. $ocrText"
                    ocrText.isNotBlank() -> ocrText
                    treeItems.isNotEmpty() -> treeItems.take(10).joinToString(". ")
                    else -> ""
                }
                if (merged.isNotBlank()) {
                    callback("Screen Vision (OCR) result: ${merged.take(600)}")
                } else {
                    callback("Screen par koi readable text ya image text nahi mila.")
                }
            }
        } else {
            if (treeItems.isNotEmpty()) {
                callback("Screen par ye likha hai: ${treeItems.take(12).joinToString(". ")}")
            } else {
                callback("Screen par koi readable text nahi mila (Image OCR fallback Android 11+ par available hai).")
            }
        }
    }

    /**
     * Takes an Accessibility screenshot (API 30+) and extracts text with ML Kit TextRecognition.
     */
    private fun runMlKitOcrOnCurrentScreen(callback: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback("")
            return
        }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        if (hwBitmap == null) {
                            hardwareBuffer.close()
                            callback("")
                            return
                        }
                        val softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        hwBitmap.recycle()
                        hardwareBuffer.close()

                        if (softwareBitmap == null) {
                            callback("")
                            return
                        }

                        val image = InputImage.fromBitmap(softwareBitmap, 0)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                softwareBitmap.recycle()
                                val cleaned = visionText.text.trim().replace("\n", ". ")
                                callback(cleaned)
                            }
                            .addOnFailureListener { e ->
                                softwareBitmap.recycle()
                                Log.e(TAG, "ML Kit OCR error: ${e.message}")
                                callback("")
                            }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Accessibility takeScreenshot failed with error code: $errorCode")
                        callback("")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot exception: ${e.message}")
            callback("")
        }
    }

    /**
     * Extracts on-screen text (Accessibility + OCR fallback) and translates it on-device using ML Kit.
     */
    private fun extractAndTranslateScreen(
        targetLangCode: String,
        callback: (String) -> Unit
    ) {
        extractActiveScreenTextWithOcrFallback(forceOcr = false) { rawScreenSummary ->
            val rawText = rawScreenSummary
                .removePrefix("Screen par ye likha hai: ")
                .removePrefix("Screen Vision (OCR) result: ")
                .trim()

            if (rawText.isBlank() || rawText.startsWith("Screen par koi readable")) {
                callback("Screen par translate karne ke liye koi text nahi mila.")
                return@extractActiveScreenTextWithOcrFallback
            }

            val sampleForLangId = rawText.take(400)
            val languageIdentifier = LanguageIdentification.getClient()
            languageIdentifier.identifyLanguage(sampleForLangId)
                .addOnSuccessListener { detectedCode ->
                    val sourceCode = if (detectedCode == "und") {
                        TranslateLanguage.ENGLISH
                    } else {
                        TranslateLanguage.fromLanguageTag(detectedCode) ?: TranslateLanguage.ENGLISH
                    }

                    if (sourceCode == targetLangCode) {
                        callback("Screen text pehle se target language me hai: ${rawText.take(350)}")
                        return@addOnSuccessListener
                    }

                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(sourceCode)
                        .setTargetLanguage(targetLangCode)
                        .build()
                    val translator = Translation.getClient(options)
                    val conditions = DownloadConditions.Builder().build()

                    translator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener {
                            translator.translate(rawText.take(600))
                                .addOnSuccessListener { translated ->
                                    translator.close()
                                    callback("Screen translation ($sourceCode -> $targetLangCode): $translated")
                                }
                                .addOnFailureListener { e ->
                                    translator.close()
                                    callback("On-device translation failed: ${e.localizedMessage}")
                                }
                        }
                        .addOnFailureListener { e ->
                            translator.close()
                            callback("Offline translation model download nahi ho saka (internet check karein): ${e.localizedMessage}")
                        }
                }
                .addOnFailureListener {
                    callback("Screen language detect nahi ho saki.")
                }
        }
    }

    /**
     * Finds interactive node matching targetLabel and executes ACTION_CLICK,
     * falling back to coordinate-based dispatchGesture() tap at node bounds center.
     */
    private fun findAndClickNodeByText(targetLabel: String, callback: ((GestureResult) -> Unit)?) {
        val cleanTarget = targetLabel.trim()
        if (cleanTarget.isBlank()) {
            callback?.invoke(GestureResult.Failure("Button ka naam batayein, Boss."))
            return
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            callback?.invoke(GestureResult.Failure("Screen read nahi ho pa raha hai."))
            return
        }

        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        rootNode.findAccessibilityNodeInfosByText(cleanTarget)?.let { matchedNodes.addAll(it) }

        if (matchedNodes.isEmpty()) {
            findMatchingNodesRecursive(rootNode, cleanTarget.lowercase(), matchedNodes)
        }

        if (matchedNodes.isEmpty()) {
            @Suppress("DEPRECATION")
            rootNode.recycle()
            callback?.invoke(GestureResult.Failure("'$cleanTarget' button screen par nahi mila."))
            return
        }

        // 1. Try native Accessibility ACTION_CLICK on node or clickable parent
        for (node in matchedNodes) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                @Suppress("DEPRECATION")
                rootNode.recycle()
                callback?.invoke(GestureResult.Success("'$cleanTarget' button par click kar diya hai! 👆"))
                return
            }
            val parent = node.parent
            if (parent != null && parent.isClickable) {
                val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                @Suppress("DEPRECATION")
                parent.recycle()
                if (clicked) {
                    @Suppress("DEPRECATION")
                    rootNode.recycle()
                    callback?.invoke(GestureResult.Success("'$cleanTarget' button par click kar diya hai! 👆"))
                    return
                }
            }
        }

        // 2. Fallback: Exact coordinate tap via dispatchGesture() on matched node bounds
        val firstBounds = Rect()
        matchedNodes.first().getBoundsInScreen(firstBounds)
        @Suppress("DEPRECATION")
        rootNode.recycle()

        if (!firstBounds.isEmpty) {
            dispatchTapAt(
                x = firstBounds.centerX().toFloat(),
                y = firstBounds.centerY().toFloat(),
                successMessage = "'$cleanTarget' button par gesture tap kar diya hai! 👆",
                failureMessage = "'$cleanTarget' mila lekin gesture tap cancel ho gaya.",
                callback = callback
            )
        } else {
            callback?.invoke(GestureResult.Failure("'$cleanTarget' mila lekin tap coordinates available nahi the."))
        }
    }

    private fun findMatchingNodesRecursive(
        node: AccessibilityNodeInfo?,
        queryLower: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        val text = node.text?.toString()?.lowercase()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase()?.trim().orEmpty()
        if (text.contains(queryLower) || desc.contains(queryLower)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            findMatchingNodesRecursive(node.getChild(i), queryLower, results)
        }
    }
}
