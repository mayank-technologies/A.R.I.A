package com.example.assistant

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Snapshot of the live device system clock and active timezone.
 * Built fresh on every call so no stale SimpleDateFormat or cached timezone offset is ever retained.
 */
data class DeviceClockSnapshot(
    val epochMillis: Long,
    val rawDate: Date,
    val timeZoneId: String,
    val offsetMinutes: Int,
    val hourOfDay: Int,
    val formattedTime: String,
    val formattedDate: String,
    val isoLocalTimestamp: String,
    val minute: Int = 0,
    val amPm: String = "AM"
)

data class GreetingDecision(
    val greeting: String,
    val hourOfDay: Int,
    val minute: Int,
    val amPm: String,
    val timeZoneId: String,
    val formattedTime: String,
    val source: String,
    val isManualOverride: Boolean = false
)

/**
 * Centralized, stateless device clock & greeting helper for ARC.
 *
 * Root-cause protections:
 * 1. Never caches SimpleDateFormat or Calendar instances across ticks (SimpleDateFormat snapshots
 *    TimeZone.getDefault() at construction time, which causes fixed offset bugs if timezone updates).
 * 2. Directly reads System.currentTimeMillis() + live TimeZone without manual UTC offsets,
 *    preventing double-conversion offset shifts (e.g. 1.5h / 5.5h errors).
 * 3. Always uses Calendar.HOUR_OF_DAY (0..23 24-hour clock) with explicit non-overlapping ranges
 *    for time-based greetings and prints raw DateTime / Hour24 / TimeZone debug logs.
 */
object DeviceClockHelper {

    private const val TAG = "ARC_CLOCK"
    private const val GREETING_DEBUG_TAG = "ARC_GREETING_DEBUG"

    @Volatile
    private var hasExplicitTimezoneOverride: Boolean = false

    /**
     * Resolves the freshest active TimeZone.
     * On real Android devices, if no explicit override was set via onTimezoneChanged(), checks
     * Android ICU's live default timezone so a cached JVM default never keeps the app stuck on
     * UTC or a previous timezone before BroadcastReceiver mounts.
     */
    private fun resolveActiveTimeZone(): TimeZone {
        val currentJavaTz = TimeZone.getDefault()
        if (hasExplicitTimezoneOverride) {
            return currentJavaTz
        }
        val liveIcuTzId = try {
            android.icu.util.TimeZone.getDefault()?.id
        } catch (_: Throwable) {
            null
        }
        if (!liveIcuTzId.isNullOrBlank() && liveIcuTzId != currentJavaTz.id) {
            val syncedTz = TimeZone.getTimeZone(liveIcuTzId)
            TimeZone.setDefault(syncedTz)
            return syncedTz
        }
        return currentJavaTz
    }

    /**
     * Reads fresh system time & active device timezone.
     *
     * @param context Optional Android context to respect device 12h/24h format preference when needed.
     * @param respect24HourSetting If true and context is available, uses HH:mm when device is in 24h mode.
     * @param logDebug Prints raw DateTime / TimeZone debug info to Logcat & stdout for verification.
     * @param source Caller label included in debug logs.
     * @param nowMillis Timestamp in epoch millis (defaults to live System.currentTimeMillis()).
     */
    fun getCurrentDeviceClockInfo(
        context: Context? = null,
        respect24HourSetting: Boolean = false,
        logDebug: Boolean = true,
        source: String = "ClockRead",
        nowMillis: Long = System.currentTimeMillis()
    ): DeviceClockSnapshot {
        val rawDate = Date(nowMillis)
        val activeTimeZone = resolveActiveTimeZone()
        val locale = Locale.getDefault()

        val calendar = Calendar.getInstance(activeTimeZone, locale).apply {
            timeInMillis = nowMillis
        }

        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm = if (hour24 < 12) "AM" else "PM"

        val use24Hour = respect24HourSetting && context != null && try {
            DateFormat.is24HourFormat(context)
        } catch (_: Throwable) {
            false
        }

        val timePattern = if (use24Hour) "HH:mm" else "h:mm a"
        val timeFormatter = SimpleDateFormat(timePattern, locale).apply {
            timeZone = activeTimeZone
        }
        val dateFormatter = SimpleDateFormat("EEEE, dd MMMM yyyy", locale).apply {
            timeZone = activeTimeZone
        }
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = activeTimeZone
        }

        val snapshot = DeviceClockSnapshot(
            epochMillis = nowMillis,
            rawDate = rawDate,
            timeZoneId = activeTimeZone.id,
            offsetMinutes = activeTimeZone.getOffset(nowMillis) / 60_000,
            hourOfDay = hour24,
            formattedTime = timeFormatter.format(rawDate),
            formattedDate = dateFormatter.format(rawDate),
            isoLocalTimestamp = try {
                isoFormatter.format(rawDate)
            } catch (_: Throwable) {
                rawDate.toString()
            },
            minute = minute,
            amPm = amPm
        )

        if (logDebug) {
            logClockSnapshot(snapshot, source)
        }
        return snapshot
    }

    /**
     * Explicit, non-overlapping 24-hour time-range mapping for automatic greetings:
     * - 04:00 .. 11:59 -> "Good morning"
     * - 12:00 .. 16:59 -> "Good afternoon"
     * - 17:00 .. 20:59 -> "Good evening"
     * - 21:00 .. 23:59 & 00:00 .. 03:59 -> "Good night"
     */
    fun resolveTimeBasedGreeting(hourOfDay: Int): String {
        val normalizedHour = ((hourOfDay % 24) + 24) % 24
        return when (normalizedHour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            in 21..23, in 0..3 -> "Good night"
            else -> "Good morning"
        }
    }

    /**
     * Evaluates the live device greeting from fresh system clock + active timezone and prints raw
     * current hour, minute, AM/PM, timezone, and chosen greeting to console/Logcat for debugging.
     */
    fun evaluateTimeBasedGreeting(
        context: Context? = null,
        source: String = "AutoGreeting",
        overrideGreeting: String? = null,
        logDebug: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): GreetingDecision {
        val snapshot = getCurrentDeviceClockInfo(
            context = context,
            respect24HourSetting = false,
            logDebug = logDebug,
            source = source,
            nowMillis = nowMillis
        )
        val autoGreeting = resolveTimeBasedGreeting(snapshot.hourOfDay)
        val cleanOverride = overrideGreeting?.trim()?.takeIf { it.isNotBlank() }
        val finalGreeting = cleanOverride ?: autoGreeting

        if (logDebug) {
            val sign = if (snapshot.offsetMinutes >= 0) "+" else "-"
            val absMin = kotlin.math.abs(snapshot.offsetMinutes)
            val offsetFormatted = String.format(Locale.US, "UTC%s%02d:%02d", sign, absMin / 60, absMin % 60)
            val debugLine = "[$source] rawHour24=${snapshot.hourOfDay} | rawMinute=${snapshot.minute} | amPm=${snapshot.amPm} | " +
                "tz=${snapshot.timeZoneId} ($offsetFormatted) | formattedTime=${snapshot.formattedTime} | " +
                "rawDate=${snapshot.rawDate} | autoGreeting='$autoGreeting' | finalGreeting='$finalGreeting' | " +
                "manualOverride=${cleanOverride != null}"
            println("ARC_GREETING_DEBUG: $debugLine")
            try {
                Log.d(GREETING_DEBUG_TAG, debugLine)
            } catch (_: Throwable) {
                // Safe no-op in pure JVM unit tests without Robolectric Log shadows
            }
        }

        return GreetingDecision(
            greeting = finalGreeting,
            hourOfDay = snapshot.hourOfDay,
            minute = snapshot.minute,
            amPm = snapshot.amPm,
            timeZoneId = snapshot.timeZoneId,
            formattedTime = snapshot.formattedTime,
            source = source,
            isManualOverride = cleanOverride != null
        )
    }

    /**
     * Produces standby/power-off farewell without falsely saying "Good night" during morning/daytime
     * unless the user explicitly invoked the "Good Night ARC" voice routine.
     */
    fun resolveStandbyFarewell(
        context: Context? = null,
        displayName: String,
        isExplicitGoodNightCommand: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val cleanName = displayName.ifBlank { "Boss" }
        if (isExplicitGoodNightCommand) {
            return "Good night, $cleanName! ARC standby mode me ja raha hai taaki phone battery save ho sake. Sound sleep lijiye!"
        }
        val decision = evaluateTimeBasedGreeting(
            context = context,
            source = "StandbyPowerOff",
            logDebug = true,
            nowMillis = nowMillis
        )
        return if (decision.hourOfDay in 21..23 || decision.hourOfDay in 0..3) {
            "Good night, $cleanName! ARC standby mode me ja raha hai taaki phone battery save ho sake. Tap power button to start!"
        } else {
            "ARC standby mode me ja raha hai, $cleanName, taaki phone battery save ho sake. Tap power button to start!"
        }
    }

    /**
     * Prints raw DateTime.now() equivalent (`Date(System.currentTimeMillis())`) and active timezone
     * details to both Android Logcat and stdout console for verification.
     */
    fun logClockSnapshot(snapshot: DeviceClockSnapshot, source: String = "Clock") {
        val sign = if (snapshot.offsetMinutes >= 0) "+" else "-"
        val absMin = kotlin.math.abs(snapshot.offsetMinutes)
        val offsetFormatted = String.format(Locale.US, "UTC%s%02d:%02d", sign, absMin / 60, absMin % 60)
        val message = "[$source] rawDateTimeNow=${snapshot.rawDate} | hour24=${snapshot.hourOfDay} | localISO=${snapshot.isoLocalTimestamp} | tz=${snapshot.timeZoneId} ($offsetFormatted) | formattedTime=${snapshot.formattedTime}"
        println("ARC_CLOCK_DEBUG: $message")
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // Safe no-op in pure JVM unit tests without Robolectric Log shadows
        }
    }

    /**
     * Clears any JVM-cached default timezone when Android broadcasts ACTION_TIMEZONE_CHANGED.
     */
    fun onTimezoneChanged(newTimeZoneId: String?) {
        if (!newTimeZoneId.isNullOrBlank()) {
            hasExplicitTimezoneOverride = true
            TimeZone.setDefault(TimeZone.getTimeZone(newTimeZoneId))
        } else {
            hasExplicitTimezoneOverride = false
            TimeZone.setDefault(null)
        }
    }

    /**
     * Computes exact milliseconds remaining until the start of the next minute (:00.050s)
     * so periodic UI clocks update right when the system minute rolls over without drift.
     */
    fun millisUntilNextMinuteTick(nowMillis: Long = System.currentTimeMillis()): Long {
        val msIntoCurrentMinute = nowMillis % 60_000L
        return (60_000L - msIntoCurrentMinute + 50L).coerceAtLeast(250L)
    }
}
