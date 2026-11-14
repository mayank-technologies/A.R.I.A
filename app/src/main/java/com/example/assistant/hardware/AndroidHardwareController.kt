package com.example.assistant.hardware

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Result of executing a hardware control command.
 */
sealed class HardwareActionResult {
    data class Success(val message: String) : HardwareActionResult()
    data class Failure(val message: String) : HardwareActionResult()
}

/**
 * AndroidHardwareController:
 * Native Android hardware control module for ARC Assistant.
 *
 * Supported Voice Controls:
 * 1. Flashlight ON/OFF (CameraManager with FLASH_INFO_AVAILABLE verification)
 * 2. WiFi toggle / quick panel (direct toggle < Android 10, Settings.Panel on Android 10+)
 * 3. Bluetooth toggle / settings (BluetoothManager + BLUETOOTH_CONNECT check on Android 12+)
 * 4. Volume up / down / mute / full (AudioManager STREAM_MUSIC)
 * 5. Screen brightness adjust up / down / percentage (Settings.System.SCREEN_BRIGHTNESS)
 * 6. Battery percentage & charging telemetry (BatteryManager)
 */
object AndroidHardwareController {

    private const val TAG = "ARC_HARDWARE_CTRL"
    private var isTorchOn = false

    /**
     * Toggles device rear flashlight / torch safely by picking a camera unit that exposes flash.
     */
    fun setTorchMode(context: Context, enable: Boolean): HardwareActionResult {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return HardwareActionResult.Failure("Camera hardware available nahi hai.")

        return try {
            val flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return HardwareActionResult.Failure("Is device par flashlight unit nahi mila.")

            cameraManager.setTorchMode(flashCameraId, enable)
            isTorchOn = enable
            val stateText = if (enable) "chalu kar di hai 🔦" else "band kar di hai"
            HardwareActionResult.Success("Torch $stateText, Boss!")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting torch mode: ${e.message}")
            HardwareActionResult.Failure("Flashlight toggle nahi ho saki: ${e.localizedMessage}")
        }
    }

    /**
     * Adjusts system media volume up or down.
     */
    fun adjustVolume(context: Context, directionUp: Boolean, steps: Int = 2): HardwareActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return HardwareActionResult.Failure("Audio service available nahi hai.")

        val direction = if (directionUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(steps) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val pct = ((currentVol.toFloat() / maxVol) * 100).toInt()

        val label = if (directionUp) "badha diya hai" else "kam kar diya hai"
        return HardwareActionResult.Success("Volume $label ($pct%).")
    }

    /**
     * Sets media volume directly to mute (0%) or max (100%).
     */
    fun setVolumeExtreme(context: Context, mute: Boolean): HardwareActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return HardwareActionResult.Failure("Audio service available nahi hai.")

        val target = if (mute) 0 else audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return HardwareActionResult.Success(
            if (mute) "Media volume mute kar diya hai 🔇" else "Media volume full (100%) kar diya hai 🔊"
        )
    }

    /**
     * Toggles WiFi on Android < 10 directly, or opens Android Q+ WiFi Settings Panel.
     */
    fun openWifiControl(context: Context, enableDesired: Boolean? = null): HardwareActionResult {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && wifiManager != null) {
                @Suppress("DEPRECATION")
                val targetState = enableDesired ?: !wifiManager.isWifiEnabled
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = targetState
                val stateLabel = if (targetState) "ON" else "OFF"
                HardwareActionResult.Success("WiFi $stateLabel kar diya hai, Boss!")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                val statusHint = if (wifiManager?.isWifiEnabled == true) "abhi ON hai" else "abhi OFF hai"
                HardwareActionResult.Success("WiFi $statusHint — quick toggle panel khol diya hai, Boss!")
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                HardwareActionResult.Success("WiFi settings khol di hai.")
            }
        } catch (e: Exception) {
            HardwareActionResult.Failure("WiFi control open nahi ho saka: ${e.localizedMessage}")
        }
    }

    /**
     * Enables Bluetooth via system prompt when off & permitted, or opens Bluetooth Settings.
     */
    fun openBluetoothControl(context: Context, enableDesired: Boolean? = null): HardwareActionResult {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            val hasConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (adapter != null && hasConnectPerm && enableDesired == true && !adapter.isEnabled) {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(enableIntent)
                return HardwareActionResult.Success("Bluetooth ON karne ke liye system prompt khol diya hai, Boss!")
            }

            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val currentLabel = if (adapter?.isEnabled == true) "abhi ON hai" else "abhi OFF hai"
            HardwareActionResult.Success("Bluetooth $currentLabel — Bluetooth settings khol di hai, Boss!")
        } catch (e: Exception) {
            HardwareActionResult.Failure("Bluetooth screen open nahi ho saki: ${e.localizedMessage}")
        }
    }

    /**
     * Adjusts screen brightness directly when WRITE_SETTINGS is granted,
     * otherwise opens the system "Modify system settings" permission page for ARC.
     */
    fun adjustBrightness(
        context: Context,
        directionUp: Boolean? = null,
        targetPercent: Int? = null
    ): HardwareActionResult {
        if (!Settings.System.canWrite(context)) {
            return try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                HardwareActionResult.Success(
                    "Brightness control ke liye 'Modify system settings' permission allow kijiye — maine settings page khol diya hai!"
                )
            } catch (e: Exception) {
                openDisplayBrightnessSettings(context)
            }
        }

        return try {
            val resolver = context.contentResolver
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val currentVal = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val newVal = when {
                targetPercent != null -> ((targetPercent.coerceIn(5, 100) / 100f) * 255).toInt()
                directionUp == true -> (currentVal + 55).coerceAtMost(255)
                directionUp == false -> (currentVal - 55).coerceAtLeast(15)
                else -> currentVal
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, newVal)
            val pct = ((newVal / 255f) * 100).toInt()
            HardwareActionResult.Success("Screen brightness $pct% par set kar di hai, Boss! ☀️")
        } catch (e: Exception) {
            Log.e(TAG, "Brightness adjust error: ${e.message}")
            openDisplayBrightnessSettings(context)
        }
    }

    /**
     * Fallback to open Display Settings screen.
     */
    fun openDisplayBrightnessSettings(context: Context): HardwareActionResult {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            HardwareActionResult.Success("Display brightness screen open kar di hai.")
        } catch (e: Exception) {
            HardwareActionResult.Failure("Display settings kholne me problem aayi.")
        }
    }

    /**
     * Reads current battery percentage and charging state.
     */
    fun getBatteryTelemetry(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging ?: false

        return if (batteryPct >= 0) {
            val chargingStatus = if (isCharging) "aur phone charging par laga hai ⚡" else "aur battery drain normal hai"
            "Boss, battery level $batteryPct% hai, $chargingStatus."
        } else {
            "Battery percentage read nahi ho pa raha hai."
        }
    }
}
