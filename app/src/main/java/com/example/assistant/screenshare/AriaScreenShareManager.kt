package com.example.assistant.screenshare

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper & Manager for controlling ARIA Screen Sharing sessions and state.
 */
object AriaScreenShareManager {

    private const val TAG = "AriaScreenShareManager"

    const val ACTION_START_SHARE = "com.example.action.START_SCREEN_SHARE"
    const val ACTION_STOP_SHARE = "com.example.action.STOP_SCREEN_SHARE"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"

    private val _isSharingActive = MutableStateFlow(false)
    val isSharingActive: StateFlow<Boolean> = _isSharingActive.asStateFlow()

    internal fun setSharingActive(active: Boolean) {
        _isSharingActive.value = active
    }

    /**
     * Checks if SYSTEM_ALERT_WINDOW (Draw Over Other Apps) permission is granted.
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Opens Android System Settings page for granting Draw Over Other Apps permission.
     */
    fun openOverlayPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening overlay settings: ${e.message}", e)
        }
    }

    /**
     * Creates system MediaProjection intent for screen capture permission.
     */
    fun getScreenCaptureIntent(context: Context): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    /**
     * Launches the AriaScreenShareService foreground service with screen capture results.
     */
    fun startScreenShare(context: Context, resultCode: Int, resultData: Intent) {
        Log.d(TAG, "Launching AriaScreenShareService foreground service")
        val serviceIntent = Intent(context, AriaScreenShareService::class.java).apply {
            action = ACTION_START_SHARE
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_RESULT_DATA, resultData)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    /**
     * Sends action to stop the screen sharing service and overlay.
     */
    fun stopScreenShare(context: Context) {
        Log.d(TAG, "Sending stop action to AriaScreenShareService")
        val serviceIntent = Intent(context, AriaScreenShareService::class.java).apply {
            action = ACTION_STOP_SHARE
        }
        context.startService(serviceIntent)
    }
}
