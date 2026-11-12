package com.example.assistant.hardware

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Result of executing a hardware command.
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
 * 1. Flashlight on/off (CameraManager torchMode)
 * 2. WiFi toggle (Settings.Panel on Android 10+ or direct WiFi manager)
 * 3. Bluetooth toggle (Settings panel / Intent)
 * 4. Volume adjust ("volume badhao", "volume kam karo", "mute karo")
 * 5. Screen Brightness adjust ("brightness badhao", "brightness kam karo")
 * 6. Battery telemetry read ("battery percentage batao")
 */
object AndroidHardwareController {

    private const val TAG = "ARC_HARDWARE_CTRL"
    private var isTorchOn = false

    /**
     * Toggles device flashlight / torch.
     */
    fun setTorchMode(context: Context, enable: Boolean): HardwareActionResult {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return HardwareActionResult.Failure("Camera hardware available nahi hai.")

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return HardwareActionResult.Failure("Flashlight hardware nahi mila.")

            cameraManager.setTorchMode(cameraId, enable)
            isTorchOn = enable
            val stateText = if (enable) "chalu kar di hai 🔦" else "band kar di hai"
            HardwareActionResult.Success("Torch $stateText, Boss!")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting torch mode: ${e.message}")
            HardwareActionResult.Failure("Flashlight toggle nahi ho saki: ${e.localizedMessage}")
        }
    }

    /**
     * Adjusts system media/voice volume up or down.
     */
    fun adjustVolume(context: Context, directionUp: Boolean, steps: Int = 2): HardwareActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return HardwareActionResult.Failure("Audio service available nahi hai.")

        val direction = if (directionUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(steps) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val pct = ((currentVol.toFloat() / maxVol) * 100).toInt()

        val label = if (directionUp) "badha diya hai" else "kam kar diya hai"
        return HardwareActionResult.Success("Volume $label ($pct%).")
    }

    /**
     * Opens WiFi settings panel or direct toggle.
     */
    fun openWifiControl(context: Context): HardwareActionResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                HardwareActionResult.Success("WiFi settings panel khol diya hai, Boss!")
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                HardwareActionResult.Success("WiFi settings khol di hai.")
            }
        } catch (e: Exception) {
            HardwareActionResult.Failure("WiFi panel open nahi ho saka.")
        }
    }

    /**
     * Opens Bluetooth settings panel.
     */
    fun openBluetoothControl(context: Context): HardwareActionResult {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            HardwareActionResult.Success("Bluetooth settings khol di hai, Boss!")
        } catch (e: Exception) {
            HardwareActionResult.Failure("Bluetooth screen open nahi ho saki.")
        }
    }

    /**
     * Adjusts screen brightness by opening Display Settings or setting overlay.
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
