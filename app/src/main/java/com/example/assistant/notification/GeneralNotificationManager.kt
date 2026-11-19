package com.example.assistant.notification

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents any incoming notification captured across installed apps (SMS, Instagram, Gmail, WhatsApp, etc.).
 */
data class GeneralNotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val timeFormatted: String
)

/**
 * UI / Settings representation for toggling per-app notification read-aloud.
 * All apps are enabled by default unless explicitly toggled OFF by the user.
 */
data class AppNotificationPreference(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean,
    val capturedCount: Int = 0
)

/**
 * GeneralNotificationManager:
 * Captures and manages notifications from ALL apps with a SharedPreferences-backed
 * per-app enable/disable toggle (every app enabled by default).
 */
object GeneralNotificationManager {

    private const val TAG = "ARC_GENERAL_NOTIFS"
    private const val PREFS_NAME = "arc_notification_prefs"
    private const val KEY_DISABLED_PACKAGES = "disabled_notification_packages"
    private const val KEY_KNOWN_APPS = "known_notification_apps"

    // Default starter apps so Settings -> Notifications always lists clear toggleable entries
    private val defaultTrackedApps = linkedMapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.google.android.apps.messaging" to "Messages (SMS)",
        "com.android.mms" to "SMS Messages",
        "com.instagram.android" to "Instagram",
        "com.google.android.gm" to "Gmail",
        "org.telegram.messenger" to "Telegram",
        "com.snapchat.android" to "Snapchat"
    )

    private val systemNoisePackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.providers.downloads"
    )

    private val _notifications = MutableStateFlow<List<GeneralNotificationItem>>(emptyList())
    val capturedNotifications: StateFlow<List<GeneralNotificationItem>> = _notifications.asStateFlow()

    private val _disabledPackages = MutableStateFlow<Set<String>>(emptySet())
    val disabledPackages: StateFlow<Set<String>> = _disabledPackages.asStateFlow()

    private val _knownApps = MutableStateFlow<Map<String, String>>(defaultTrackedApps)
    val knownApps: StateFlow<Map<String, String>> = _knownApps.asStateFlow()

    @Volatile
    private var prefsLoaded = false

    fun ensureLoaded(context: Context) {
        if (prefsLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val disabled = prefs.getStringSet(KEY_DISABLED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        _disabledPackages.value = disabled

        val mergedApps = linkedMapOf<String, String>().apply {
            putAll(defaultTrackedApps)
        }
        val savedEntries = prefs.getStringSet(KEY_KNOWN_APPS, emptySet()) ?: emptySet()
        savedEntries.forEach { encoded ->
            val parts = encoded.split("|", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                mergedApps[parts[0]] = parts[1]
            }
        }
        _knownApps.value = mergedApps
        prefsLoaded = true
    }

    fun isAppEnabled(context: Context, packageName: String): Boolean {
        ensureLoaded(context)
        return packageName !in _disabledPackages.value
    }

    fun setAppEnabled(context: Context, packageName: String, appName: String, enabled: Boolean) {
        ensureLoaded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updatedDisabled = _disabledPackages.value.toMutableSet()
        if (enabled) {
            updatedDisabled.remove(packageName)
        } else {
            updatedDisabled.add(packageName)
        }
        _disabledPackages.value = updatedDisabled
        registerKnownApp(context, packageName, appName)
        prefs.edit().putStringSet(KEY_DISABLED_PACKAGES, updatedDisabled).apply()
        Log.d(TAG, "Notification read-aloud for $appName ($packageName) -> enabled=$enabled")
    }

    private fun registerKnownApp(context: Context, packageName: String, appName: String) {
        val current = _knownApps.value
        if (current[packageName] == appName) return
        val updated = current.toMutableMap().apply { put(packageName, appName) }
        _knownApps.value = updated
        val encoded = updated.entries.map { "${it.key}|${it.value}" }.toSet()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_KNOWN_APPS, encoded)
            .apply()
    }

    fun resolveAppName(context: Context, packageName: String): String {
        defaultTrackedApps[packageName]?.let { return it }
        _knownApps.value[packageName]?.let { return it }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun getAppPreferenceList(context: Context): List<AppNotificationPreference> {
        ensureLoaded(context)
        val disabled = _disabledPackages.value
        val counts = _notifications.value.groupingBy { it.packageName }.eachCount()
        return _knownApps.value.entries.map { (pkg, label) ->
            AppNotificationPreference(
                packageName = pkg,
                appName = label,
                isEnabled = pkg !in disabled,
                capturedCount = counts[pkg] ?: 0
            )
        }.sortedWith(compareByDescending<AppNotificationPreference> { it.capturedCount }.thenBy { it.appName })
    }

    /**
     * Records an incoming notification across any third-party/messaging app unless ignored or disabled.
     * Callable both from [com.example.assistant.whatsapp.ArcNotificationListenerService] and unit tests.
     */
    fun recordIncomingNotification(
        context: Context,
        packageName: String,
        sender: String,
        message: String,
        appNameOverride: String? = null,
        postTime: Long = System.currentTimeMillis(),
        notificationId: String = "${packageName}_$postTime",
        isOngoing: Boolean = false
    ): GeneralNotificationItem? {
        ensureLoaded(context)
        if (packageName.isBlank() || packageName == context.packageName || packageName in systemNoisePackages) {
            return null
        }
        if (isOngoing) return null

        val cleanText = message.trim()
        val cleanSender = sender.trim()
        if (cleanText.isBlank()) return null

        val appLabel = appNameOverride?.takeIf { it.isNotBlank() } ?: resolveAppName(context, packageName)
        registerKnownApp(context, packageName, appLabel)

        // Filter placeholder summary headers like "2 new messages" when meaningless
        if (cleanSender.equals(appLabel, ignoreCase = true) && cleanText.matches(Regex("^\\d+\\s+new\\s+messages?$", RegexOption.IGNORE_CASE))) {
            return null
        }

        val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(postTime))
        val item = GeneralNotificationItem(
            id = notificationId,
            packageName = packageName,
            appName = appLabel,
            sender = cleanSender.ifBlank { appLabel },
            message = cleanText,
            timestamp = postTime,
            timeFormatted = timeFormatted
        )

        _notifications.value = (listOf(item) + _notifications.value)
            .distinctBy { "${it.packageName}_${it.sender}_${it.message}" }
            .take(40)

        Log.d(TAG, "Captured notification from $appLabel ($packageName) | sender=${item.sender}")
        return item
    }

    fun getEnabledUnreadNotifications(context: Context): List<GeneralNotificationItem> {
        ensureLoaded(context)
        val disabled = _disabledPackages.value
        return _notifications.value.filter { it.packageName !in disabled }
    }

    fun getUnreadNotificationsSummary(context: Context): String {
        val enabledList = getEnabledUnreadNotifications(context)
        if (enabledList.isEmpty()) {
            return "Koi naye notifications nahi hain, Boss."
        }
        val builder = StringBuilder("Aapke paas ${enabledList.size} recent notifications hain:\n")
        enabledList.take(6).forEachIndexed { idx, item ->
            val senderLabel = if (item.sender.equals(item.appName, ignoreCase = true)) {
                item.appName
            } else {
                "${item.appName} par ${item.sender}"
            }
            builder.append("${idx + 1}. $senderLabel: \"${item.message}\"\n")
        }
        return builder.toString().trim()
    }

    fun clearAll() {
        _notifications.value = emptyList()
    }

    fun resetPreferencesForTesting(context: Context) {
        prefsLoaded = false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        _disabledPackages.value = emptySet()
        _knownApps.value = defaultTrackedApps
        _notifications.value = emptyList()
    }
}
