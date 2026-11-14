package com.example.assistant.security

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SirenGuardState represents the anti-theft security alarm system state.
 */
enum class SirenGuardState {
    DISARMED,
    ARMED,
    TRIGGERED
}

/**
 * AntiTheftSirenManager:
 * Transparent Anti-Theft Hardware Guard for ARC Assistant.
 *
 * Capabilities:
 * 1. Voice arm ("security on karo"): listens to Accelerometer (pickup/motion) and Charger Unplug events.
 * 2. Silent-mode override siren: maximizes STREAM_ALARM (100%), restores ringer if DND policy is granted,
 *    plays loud looping alarm audio + continuous vibration + rear flash strobe.
 * 3. Visible Front-Camera Alert Snapshot: notifies the UI so AntiTheftAlertDialog displays a visible
 *    front-camera security warning & captures a transparent snapshot (never hidden or covert).
 * 4. Voice disarm ("security off karo"): immediately stops siren, strobe, vibration, and sensors.
 */
object AntiTheftSirenManager : SensorEventListener {

    private const val TAG = "ARC_SIREN_GUARD"
    private const val MOTION_THRESHOLD = 3.8f // m/s^2 deviation from resting gravity

    private val _guardState = MutableStateFlow(SirenGuardState.DISARMED)
    val guardState: StateFlow<SirenGuardState> = _guardState.asStateFlow()

    private val _triggerReason = MutableStateFlow<String?>(null)
    val triggerReason: StateFlow<String?> = _triggerReason.asStateFlow()

    private val _capturedAlertPhotoPath = MutableStateFlow<String?>(null)
    val capturedAlertPhotoPath: StateFlow<String?> = _capturedAlertPhotoPath.asStateFlow()

    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var powerReceiver: BroadcastReceiver? = null

    private var baselineAcceleration = 9.81f
    private var isCalibrated = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var flashStrobeJob: Job? = null

    /**
     * Arms the Anti-Theft Siren Guard (accelerometer motion + charger unplug detection).
     */
    fun armSecurity(context: Context): String {
        if (_guardState.value == SirenGuardState.ARMED) {
            return "Security pehle se armed hai, Boss!"
        }

        val safeCtx = context.applicationContext
        appContext = safeCtx

        sensorManager = safeCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        audioManager = safeCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (safeCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            safeCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        isCalibrated = false
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Register power disconnected receiver safely with Android 13/14+ flag
        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_POWER_DISCONNECTED &&
                    _guardState.value == SirenGuardState.ARMED
                ) {
                    triggerAlarm(safeCtx, "Charging cable unplug ho gayi!")
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        ContextCompat.registerReceiver(
            safeCtx,
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        _capturedAlertPhotoPath.value = null
        _triggerReason.value = null
        _guardState.value = SirenGuardState.ARMED
        Log.d(TAG, "Anti-Theft Siren Guard ARMED")

        return "Anti-Theft Security ARM ho gayi hai, Boss! Agar phone hila ya charger nikla to full siren bajega aur visible camera alert khulega."
    }

    /**
     * Disarms the security system immediately and silences siren, strobe flash, and sensors.
     */
    fun disarmSecurity(context: Context): String {
        if (_guardState.value == SirenGuardState.DISARMED) {
            return "Security already off hai, Boss."
        }

        val safeCtx = context.applicationContext
        stopAlarm(safeCtx)

        try {
            sensorManager?.unregisterListener(this)
            powerReceiver?.let { receiver ->
                (appContext ?: safeCtx).unregisterReceiver(receiver)
                powerReceiver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering sensors: ${e.message}")
        }

        _guardState.value = SirenGuardState.DISARMED
        _triggerReason.value = null
        _capturedAlertPhotoPath.value = null
        Log.d(TAG, "Anti-Theft Siren Guard DISARMED")

        return "Anti-Theft Security DISARM kar di gayi hai. Safe to use!"
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || _guardState.value != SirenGuardState.ARMED) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val currentMagnitude = sqrt(x * x + y * y + z * z)

            if (!isCalibrated) {
                baselineAcceleration = currentMagnitude
                isCalibrated = true
                return
            }

            val delta = abs(currentMagnitude - baselineAcceleration)
            if (delta > MOTION_THRESHOLD) {
                triggerAlarm(appContext, "Phone move/touch detect hua!")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun recordVisibleAlertPhoto(filePath: String) {
        _capturedAlertPhotoPath.value = filePath
    }

    /**
     * Triggers full-volume loud siren (overriding silent mode), vibration, and torch strobe.
     * The UI simultaneously observes SirenGuardState.TRIGGERED to show a visible front-camera alert.
     */
    fun triggerAlarm(context: Context?, reason: String) {
        if (_guardState.value == SirenGuardState.TRIGGERED) return
        val resolvedCtx = context?.applicationContext ?: appContext

        _guardState.value = SirenGuardState.TRIGGERED
        _triggerReason.value = reason
        Log.w(TAG, "ANTI-THEFT ALARM TRIGGERED: $reason")

        // 1. Silent-mode override + maximize STREAM_ALARM volume
        try {
            if (resolvedCtx != null) {
                val nm = resolvedCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.isNotificationPolicyAccessGranted) {
                    audioManager?.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            }
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio override partial: ${e.message}")
        }

        // 2. Loud looping siren using USAGE_ALARM (bypasses silent/vibrate mode)
        try {
            if (resolvedCtx != null) {
                val sirenUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                if (sirenUri != null) {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(resolvedCtx, sirenUri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = true
                        prepare()
                        start()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting siren audio: ${e.message}", e)
        }

        // 3. Continuous haptic alert
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 250), 0)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 250), 0)
            }
        } catch (_: Exception) {
        }

        // 4. Visible torch strobe alert
        if (resolvedCtx != null) {
            startTorchStrobe(resolvedCtx)
        }
    }

    private fun startTorchStrobe(context: Context) {
        flashStrobeJob?.cancel()
        flashStrobeJob = scope.launch {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return@launch
            val flashCameraId = try {
                cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (_: Exception) {
                null
            } ?: return@launch

            var isOn = false
            while (isActive && _guardState.value == SirenGuardState.TRIGGERED) {
                try {
                    isOn = !isOn
                    cameraManager.setTorchMode(flashCameraId, isOn)
                } catch (_: Exception) {
                    break
                }
                delay(350L)
            }
            try {
                cameraManager.setTorchMode(flashCameraId, false)
            } catch (_: Exception) {
            }
        }
    }

    private fun stopAlarm(context: Context) {
        try {
            flashStrobeJob?.cancel()
            flashStrobeJob = null

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val flashCameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (flashCameraId != null) {
                cameraManager.setTorchMode(flashCameraId, false)
            }
        } catch (_: Exception) {
        }

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping siren audio: ${e.message}")
        }
    }
}
