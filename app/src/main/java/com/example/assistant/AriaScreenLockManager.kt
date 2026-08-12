package com.example.assistant

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Screen Lock Manager providing both Device Policy Manager (lockNow) and
 * Accessibility Service (GLOBAL_ACTION_LOCK_SCREEN) screen lock capabilities.
 */
object AriaScreenLockManager {

    private const val TAG = "AriaScreenLock"

    /**
     * Checks whether Device Admin is currently enabled for ARIA.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, AriaDeviceAdminReceiver::class.java)
        return dpm?.isAdminActive(adminComponent) == true
    }

    /**
     * Creates an Intent to prompt the user to activate Device Admin for ARIA.
     */
    fun getAdminActivationIntent(context: Context): Intent {
        val adminComponent = ComponentName(context, AriaDeviceAdminReceiver::class.java)
        Log.d(TAG, "Creating Device Admin activation Intent for component: ${adminComponent.flattenToString()}")
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "ARIA needs Device Administrator permission solely to turn off and lock the screen when you say 'Lock Phone'. ARIA does NOT access, modify, or wipe your data."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Attempts to lock the screen using DevicePolicyManager.
     * @return true if locked successfully, false if Device Admin is not enabled.
     */
    fun lockScreenDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, AriaDeviceAdminReceiver::class.java)

        return if (dpm != null && dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow()
                Log.d(TAG, "Screen locked successfully via DevicePolicyManager lockNow()")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lock screen: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Device Admin is not active.")
            false
        }
    }

    /**
     * Helper for locking screen via Accessibility Service (Android 9.0 Pie / API 28+).
     */
    fun lockScreenAccessibility(service: AccessibilityService): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            Log.d(TAG, "Lock screen via Accessibility performGlobalAction: $success")
            success
        } else {
            Log.w(TAG, "GLOBAL_ACTION_LOCK_SCREEN requires Android 9.0 (API 28) or higher.")
            false
        }
    }

    /**
     * Checks if a voice query matches a lock screen command.
     */
    fun isLockCommand(queryLower: String): Boolean {
        return queryLower.contains("lock phone") ||
                queryLower.contains("lock screen") ||
                queryLower.contains("phone lock") ||
                queryLower.contains("screen lock") ||
                queryLower.contains("phone ko lock karo") ||
                queryLower.contains("phone lock karo") ||
                queryLower.contains("screen lock karo") ||
                queryLower.contains("screen band karo")
    }
}
