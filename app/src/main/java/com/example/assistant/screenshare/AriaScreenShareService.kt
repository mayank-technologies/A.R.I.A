package com.example.assistant.screenshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.assistant.screenshare.AriaScreenShareManager.ACTION_START_SHARE
import com.example.assistant.screenshare.AriaScreenShareManager.ACTION_STOP_SHARE
import com.example.assistant.screenshare.AriaScreenShareManager.EXTRA_RESULT_CODE
import com.example.assistant.screenshare.AriaScreenShareManager.EXTRA_RESULT_DATA

/**
 * =========================================================================================
 * 📱 ARIA SCREEN SHARE FOREGROUND SERVICE & FLOATING OVERLAY
 * =========================================================================================
 *
 * This Service manages Android's native MediaProjection API to capture the device screen
 * and presents a WhatsApp-style floating overlay control (SYSTEM_ALERT_WINDOW) that stays
 * visible and interactive over all apps even when ARIA is in the background.
 *
 * FEATURES:
 * 1. MediaProjection Screen Capture API session management.
 * 2. Foreground Service with mediaProjection service type (Android 10/14+ compliant).
 * 3. System Floating Window (WindowManager) overlay with a "Stop Sharing" button.
 * 4. Drag & Tap touch support allowing users to position or tap the control anytime.
 * 5. One-tap instant release & clean session termination.
 * =========================================================================================
 */
class AriaScreenShareService : Service() {

    companion object {
        private const val TAG = "AriaScreenShareService"
        private const val CHANNEL_ID = "aria_screen_share_channel"
        private const val NOTIFICATION_ID = 2026
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null // Unbound service
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AriaScreenShareService created")
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START_SHARE -> {
                val resultCode = intent.getIntParameter(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)

                if (resultCode != -1 && resultData != null) {
                    startForegroundNotification()
                    startMediaProjection(resultCode, resultData)
                    showFloatingOverlay()
                    AriaScreenShareManager.setSharingActive(true)
                } else {
                    Log.e(TAG, "Invalid resultCode or resultData for MediaProjection")
                    stopSelf()
                }
            }
            ACTION_STOP_SHARE -> {
                stopScreenShare()
            }
            else -> {
                Log.w(TAG, "Unknown action: $action")
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Helper to safely read Intent parcelable extra across Android versions.
     */
    @Suppress("DEPRECATION")
    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            getParcelableExtra(key) as? T
        }
    }

    /**
     * Safe helper for Intent integer extra
     */
    private fun Intent.getIntParameter(key: String, defaultValue: Int): Int {
        return getIntExtra(key, defaultValue)
    }

    /**
     * Configures the Foreground Service notification required by Android for screen capture.
     */
    private fun startForegroundNotification() {
        val stopIntent = Intent(this, AriaScreenShareService::class.java).apply {
            action = ACTION_STOP_SHARE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val mainActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A.R.I.A. Screen Share Active")
            .setContentText("Screen recording is live. Tap overlay or notification to stop.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Initializes the MediaProjection session & VirtualDisplay.
     */
    private fun startMediaProjection(resultCode: Int, resultData: Intent) {
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system")
                    stopScreenShare()
                }
            }, null)

            val metrics = DisplayMetrics()
            windowManager?.defaultDisplay?.getMetrics(metrics)
            val density = metrics.densityDpi
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "AriaScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            Log.d(TAG, "VirtualDisplay created ($width x $height @ $density dpi)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MediaProjection: ${e.message}", e)
            stopScreenShare()
        }
    }

    /**
     * Constructs and displays a floating WhatsApp-style overlay pill over all Android apps.
     */
    private fun showFloatingOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        if (overlayView != null) {
            return // Overlay already showing
        }

        val overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 16, 24, 16)

            // Dark Cyberpunk / WhatsApp Styled Translucent Background
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0F172A")) // Deep dark navy
                cornerRadius = 60f
                setStroke(3, Color.parseColor("#FF00E5FF")) // Cyber cyan border
            }
            background = bgDrawable
        }

        // 1. Red pulsating screen share indicator dot
        val dotIndicator = View(this).apply {
            val dotDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#FFEF4444")) // Red warning color
                shape = GradientDrawable.OVAL
            }
            background = dotDrawable
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        // 2. Status Label Text
        val labelText = TextView(this).apply {
            text = "Screen Sharing Active"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 24, 0)
            }
        }

        // 3. Close / Stop Sharing Button
        val closeButton = Button(this).apply {
            text = "Stop Sharing ✕"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(24, 8, 24, 8)

            val btnDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444")) // Red stop button
                cornerRadius = 40f
            }
            background = btnDrawable

            setOnClickListener {
                Log.d(TAG, "Overlay 'Stop Sharing' button clicked!")
                stopScreenShare()
            }
        }

        overlayContainer.addView(dotIndicator)
        overlayContainer.addView(labelText)
        overlayContainer.addView(closeButton)

        // WindowManager parameters for system-wide floating overlay
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120 // Position near top of screen
        }

        // Touch & Drag handling for smooth floating position adjustment
        overlayContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayContainer, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            // Tap detected on the overlay container
                            Log.d(TAG, "Overlay container tapped")
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(overlayContainer, layoutParams)
            overlayView = overlayContainer
            Log.d(TAG, "Floating overlay successfully added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating overlay view: ${e.message}", e)
        }
    }

    /**
     * Safely releases MediaProjection, VirtualDisplay, Floating Overlay and terminates Service.
     */
    private fun stopScreenShare() {
        Log.d(TAG, "Stopping screen sharing session...")

        // 1. Remove floating overlay
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.d(TAG, "Floating overlay removed from WindowManager")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating overlay: ${e.message}")
            }
            overlayView = null
        }

        // 2. Release MediaProjection & VirtualDisplay
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection: ${e.message}")
        }

        // 3. Update active state
        AriaScreenShareManager.setSharingActive(false)

        // 4. Show confirmation Toast
        Toast.makeText(applicationContext, "Screen sharing stopped", Toast.LENGTH_SHORT).show()

        // 5. Stop foreground notification and service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "A.R.I.A. Screen Sharing Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification displayed while ARIA screen sharing is active."
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopScreenShare()
        super.onDestroy()
    }
}
