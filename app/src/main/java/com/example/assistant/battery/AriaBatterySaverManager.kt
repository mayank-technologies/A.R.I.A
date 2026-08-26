package com.example.assistant.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Battery Saver Mode for A.R.I.A.
 * 
 * Capabilities:
 * 1. Automatically activates when battery drops below 20% (<20%) and device is not charging.
 * 2. Limits background sync frequency & wake-word listening loop duty cycle to save CPU/wake-locks.
 * 3. Disables non-essential GPU/Canvas animations (Arc Reactor HUD rotation, Edge Glow sweep, ripple waves).
 * 4. Supports manual override (Auto, Force On, Force Off).
 */
object AriaBatterySaverManager {

    private const val TAG = "AriaBatterySaver"
    private const val PREFS_NAME = "aria_battery_prefs"
    private const val KEY_MANUAL_OVERRIDE = "battery_saver_manual_override" // -1: Auto, 1: On, 0: Off
    const val BATTERY_THRESHOLD_PERCENT = 20

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    // null = Auto (activates when level < 20% and not charging), true = Force ON, false = Force OFF
    private val _manualOverride = MutableStateFlow<Boolean?>(null)
    val manualOverride: StateFlow<Boolean?> = _manualOverride.asStateFlow()

    private val _isBatterySaverActive = MutableStateFlow(false)
    val isBatterySaverActive: StateFlow<Boolean> = _isBatterySaverActive.asStateFlow()

    private var isInitialized = false
    private var appContext: Context? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateFromIntent(it) }
        }
    }

    /**
     * Initializes battery monitoring using system BroadcastReceiver.
     */
    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext

        // Restore manual override preference
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedOverride = prefs.getInt(KEY_MANUAL_OVERRIDE, -1)
        _manualOverride.value = when (savedOverride) {
            1 -> true
            0 -> false
            else -> null // Auto
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
        }

        val stickyIntent = context.registerReceiver(batteryReceiver, filter)
        stickyIntent?.let { updateFromIntent(it) }

        isInitialized = true
        Log.d(TAG, "AriaBatterySaverManager initialized. Level=${_batteryLevel.value}%, Active=${_isBatterySaverActive.value}")
    }

    private fun updateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val pct = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            _batteryLevel.value
        }

        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        _batteryLevel.value = pct
        _isCharging.value = charging

        recalculateBatterySaverState()
    }

    private fun recalculateBatterySaverState() {
        val override = _manualOverride.value
        val shouldActivate = if (override != null) {
            override
        } else {
            // Auto Mode: Activate if battery is below 20% and NOT charging
            _batteryLevel.value < BATTERY_THRESHOLD_PERCENT && !_isCharging.value
        }

        _isBatterySaverActive.value = shouldActivate
    }

    /**
     * Sets manual override:
     * - null: Auto mode (triggers automatically when battery < 20% and not charging)
     * - true: Always ON (Battery saver forced on)
     * - false: Always OFF (Battery saver forced off)
     */
    fun setManualOverride(enabled: Boolean?, context: Context? = appContext) {
        _manualOverride.value = enabled

        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.apply {
            putInt(KEY_MANUAL_OVERRIDE, when (enabled) {
                true -> 1
                false -> 0
                null -> -1
            })
            apply()
        }

        recalculateBatterySaverState()
        Log.d(TAG, "Manual override set to: $enabled. Active: ${_isBatterySaverActive.value}")
    }

    /**
     * Toggles between ON and AUTO/OFF.
     */
    fun toggleBatterySaver(context: Context? = appContext): Boolean {
        val currentState = _isBatterySaverActive.value
        val newState = !currentState
        setManualOverride(newState, context)
        return newState
    }

    /**
     * Resets to Automatic Mode (< 20% threshold).
     */
    fun resetToAutoMode(context: Context? = appContext) {
        setManualOverride(null, context)
    }

    /**
     * Returns throttled delay for background wake-listening loop to limit CPU consumption.
     * Normal delay (e.g. 500ms) is increased to 3500ms - 5000ms when battery saver is active.
     */
    fun getBackgroundWakeDelay(normalDelayMs: Long = 600L): Long {
        return if (_isBatterySaverActive.value) {
            maxOf(normalDelayMs * 5, 3500L)
        } else {
            normalDelayMs
        }
    }

    /**
     * Checks if UI and rendering animations should be disabled.
     */
    fun shouldDisableAnimations(): Boolean {
        return _isBatterySaverActive.value
    }

    /**
     * Provides human-readable voice & text summary of battery and power saver state.
     */
    fun getStatusSummary(): String {
        val level = _batteryLevel.value
        val charging = _isCharging.value
        val active = _isBatterySaverActive.value
        val mode = when (_manualOverride.value) {
            true -> "Forced ON"
            false -> "Forced OFF"
            null -> if (level < BATTERY_THRESHOLD_PERCENT) "Auto (<20% Low Battery)" else "Auto (Normal)"
        }

        val chargingText = if (charging) "charging ⚡" else "not charging 🔋"
        val saverDetails = if (active) {
            "Battery Saver is ACTIVE ⚡ Background sync is throttled and GPU animations are disabled to preserve power."
        } else {
            "Battery Saver is OFF. All animations and high-frequency sync are running normally."
        }

        return "Battery is at $level% ($chargingText). $saverDetails (Mode: $mode)"
    }
}
