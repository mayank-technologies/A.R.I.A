package com.example.assistant.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Categories of sensitive device permissions tracked in ARC's user-facing transparency log.
 */
enum class PermissionCategory(val label: String) {
    MICROPHONE("Microphone"),
    LOCATION("Location"),
    CONTACTS("Contacts"),
    PHONE("Phone Call"),
    CAMERA("Camera")
}

/**
 * Single immutable entry in the Permission-Usage Transparency Log ("Recent Activity").
 */
data class PermissionUsageEntry(
    val id: String = UUID.randomUUID().toString(),
    val category: PermissionCategory,
    val action: String,
    val reason: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("hh:mm:ss a · dd MMM", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}

/**
 * Thread-safe, persistent Permission-Usage Transparency Log manager.
 *
 * Records real runtime accesses to sensitive permissions (Microphone, Location, Contacts,
 * Phone Call, Camera) so any user installing ARC can audit exactly when and why a permission
 * was used in the "Recent Activity" panel.
 */
object PermissionUsageLogManager {

    private const val PREFS_NAME = "arc_permission_audit_log"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 60

    private val lock = Any()
    private val lastLoggedByKey = mutableMapOf<String, Long>()

    @Volatile
    private var loadedFromPrefs = false

    private val _entries = MutableStateFlow<List<PermissionUsageEntry>>(emptyList())
    val entries: StateFlow<List<PermissionUsageEntry>> = _entries.asStateFlow()

    /**
     * Loads persisted transparency logs from SharedPreferences if not already loaded.
     */
    fun ensureLoaded(context: Context) {
        if (loadedFromPrefs) return
        synchronized(lock) {
            if (loadedFromPrefs) return
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_ENTRIES, null)
                if (!raw.isNullOrBlank()) {
                    _entries.value = deserialize(raw)
                }
            } catch (_: Exception) {
            } finally {
                loadedFromPrefs = true
            }
        }
    }

    /**
     * Appends a sensitive permission usage event to the Recent Activity log.
     *
     * @param dedupeWindowMs Optional cooldown window for identical (category + action) events
     *        to keep standby loops readable without hiding real triggers.
     */
    fun logUsage(
        context: Context,
        category: PermissionCategory,
        action: String,
        reason: String,
        dedupeWindowMs: Long = 0L
    ) {
        val safeContext = context.applicationContext ?: context
        ensureLoaded(safeContext)

        val now = System.currentTimeMillis()
        val dedupeKey = "${category.name}:$action"

        synchronized(lock) {
            if (dedupeWindowMs > 0L) {
                val lastTs = lastLoggedByKey[dedupeKey]
                if (lastTs != null && (now - lastTs) < dedupeWindowMs) {
                    return
                }
            }
            lastLoggedByKey[dedupeKey] = now

            val newEntry = PermissionUsageEntry(
                category = category,
                action = action,
                reason = reason,
                timestampMs = now
            )
            val updated = (listOf(newEntry) + _entries.value).take(MAX_ENTRIES)
            _entries.value = updated
            persistLocked(safeContext, updated)
        }
    }

    /**
     * Clears all recorded transparency log entries (e.g., user-initiated log reset).
     */
    fun clearLogs(context: Context) {
        val safeContext = context.applicationContext ?: context
        synchronized(lock) {
            lastLoggedByKey.clear()
            _entries.value = emptyList()
            loadedFromPrefs = true
            try {
                safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_ENTRIES)
                    .apply()
            } catch (_: Exception) {
            }
        }
    }

    private fun persistLocked(context: Context, list: List<PermissionUsageEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { entry ->
                arr.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("category", entry.category.name)
                        put("action", entry.action)
                        put("reason", entry.reason)
                        put("timestampMs", entry.timestampMs)
                    }
                )
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun deserialize(rawJson: String): List<PermissionUsageEntry> {
        return try {
            val arr = JSONArray(rawJson)
            val result = mutableListOf<PermissionUsageEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val catName = obj.optString("category", PermissionCategory.MICROPHONE.name)
                val category = runCatching { PermissionCategory.valueOf(catName) }
                    .getOrDefault(PermissionCategory.MICROPHONE)
                val action = obj.optString("action", "")
                val reason = obj.optString("reason", "")
                val ts = obj.optLong("timestampMs", System.currentTimeMillis())
                val id = obj.optString("id", UUID.randomUUID().toString())
                if (action.isNotBlank()) {
                    result.add(
                        PermissionUsageEntry(
                            id = id,
                            category = category,
                            action = action,
                            reason = reason,
                            timestampMs = ts
                        )
                    )
                }
            }
            result.take(MAX_ENTRIES)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
