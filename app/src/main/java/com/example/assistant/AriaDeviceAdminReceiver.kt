package com.example.assistant

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * DeviceAdminReceiver for ARIA Voice Assistant.
 * Handles activation and deactivation events for screen lock capability.
 */
class AriaDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "ARIA Device Admin Enabled for Screen Lock capability.")
        Toast.makeText(context, "ARIA Device Admin Activated! Screen lock voice command enabled.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "ARIA Device Admin Disabled.")
        Toast.makeText(context, "ARIA Device Admin Deactivated.", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "AriaDeviceAdmin"
    }
}
