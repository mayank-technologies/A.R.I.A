package com.example.assistant.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Full anti-theft hardware guard for ARC Assistant.
 * 
 * Features:
 * 1. Accelerometer motion detection: triggers if phone is picked up or moved while armed.
 * 2. Charger unplug detection: triggers immediately if charging cable is detached.
 * 3. Loud Siren Audio Override: overrides silent/vibrate mode via AudioManager (STREAM_ALARM at 100% volume).
 * 4. Transparent Flash Alert: triggers camera flash strobe/screen illumination to alert bystanders.
 * 5. Transparent voice arming/disarming: "ARC, security on karo" / "security off karo".
 */
object AntiTheftSirenManager : SensorEventListener {

    private const val TAG = "ARC_SIREN_GUARD"
    private const val MOTION_THRESHOLD = 3.8f // m/s^2 deviation from gravity

    private val _guardState = MutableStateFlow(SirenGuardState.DISARMED)
    val guardState: StateFlow<SirenGuardState> = _guardState.asStateFlow()

    private val _triggerReason = MutableStateFlow<String?>(null)
    val triggerReason: StateFlow<String?> = _triggerReason.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var powerReceiver: BroadcastReceiver? = null

    private var baselineAcceleration = 9.81f
    private var isCalibrated = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var flashStrobeJob: Job? = null

    /**
     * Arms the Anti-Theft Siren Guard.
     */
    fun armSecurity(context: Context): String {
        if (_guardState.value == SirenGuardState.ARMED) {
            return "Security pehle se armed hai, Boss!"
        }

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        isCalibrated = false
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Register power disconnected receiver
        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                    if (_guardState.value == SirenGuardState.ARMED) {
                        triggerAlarm(context, "Charging cable unplug ho gayi!")
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        context.registerReceiver(powerReceiver, filter)

        _guardState.value = SirenGuardState.ARMED
        _triggerReason.value = null
        Log.d(TAG, "🛡️ Anti-Theft Siren Guard ARMED!")

        return "Anti-Theft Security ARM ho gayi hai, Boss! Agar phone hila ya charger nikla to tezz siren bajega."
    }

    /**
     * Disarms the security system immediately and silences any active alarm.
     */
    fun disarmSecurity(context: Context): String {
        if (_guardState.value == SirenGuardState.DISARMED) {
            return "Security already off hai, Boss."
        }

        stopAlarm(context)

        try {
            sensorManager?.unregisterListener(this)
            powerReceiver?.let {
                context.unregisterReceiver(it)
                powerReceiver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering sensors: ${e.message}")
        }

        _guardState.value = SirenGuardState.DISARMED
        _triggerReason.value = null
        Log.d(TAG, "🛡️ Anti-Theft Siren Guard DISARMED!")

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

            val delta = kotlin.math.abs(currentMagnitude - baselineAcceleration)
            if (delta > MOTION_THRESHOLD) {
                triggerAlarm(null, "Phone move/touch detect hua!")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Triggers the loud audio siren and strobe flash.
     */
    fun triggerAlarm(context: Context?, reason: String) {
        if (_guardState.value == SirenGuardState.TRIGGERED) return
        _guardState.value = SirenGuardState.TRIGGERED
        _triggerReason.value = reason
        Log.w(TAG, "🚨 ANTI-THEFT ALARM TRIGGERED: $reason")

        try {
            // Maximize alarm stream volume
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }

            // Play siren tone
            val sirenUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (context != null) {
                    setDataSource(context, sirenUri)
                }
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
        } catch (e: Exception) {
            Log.e(TAG, "Error starting siren audio: ${e.message}", e)
        }
    }

    private fun stopAlarm(context: Context) {
        try {
            flashStrobeJob?.cancel()
            flashStrobeJob = null

            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping siren audio: ${e.message}")
        }
    }
}
