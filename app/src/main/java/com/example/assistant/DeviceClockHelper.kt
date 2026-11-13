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
    val isoLocalTimestamp: String
)

/**
 * Centralized, stateless device clock helper for ARC.
 *
 * Root-cause protections:
 * 1. Never caches SimpleDateFormat or Calendar instances across ticks (SimpleDateFormat snapshots
 *    TimeZone.getDefault() at construction time, which causes fixed offset bugs if timezone updates).
 * 2. Directly reads System.currentTimeMillis() + TimeZone.getDefault() without manual UTC offsets,
 *    preventing double-conversion offset shifts (e.g. 1.5h / 5.5h errors).
 * 3. Exposes millisecond-accurate boundary calculation for periodic UI updates and logs raw
 *    DateTime values for console verification.
 */
object DeviceClockHelper {

    private const val TAG = "ARC_CLOCK"

    /**
     * Reads fresh system time & active device timezone.
     *
     * @param context Optional Android context to respect device 12h/24h format preference when needed.
     * @param respect24HourSetting If true and context is available, uses HH:mm when device is in 24h mode.
     * @param logDebug Prints raw DateTime / TimeZone debug info to Logcat & stdout for verification.
     * @param source Caller label included in debug logs.
     */
    fun getCurrentDeviceClockInfo(
        context: Context? = null,
        respect24HourSetting: Boolean = false,
        logDebug: Boolean = true,
        source: String = "ClockRead"
    ): DeviceClockSnapshot {
        val nowMillis = System.currentTimeMillis()
        val rawDate = Date(nowMillis)
        val activeTimeZone = TimeZone.getDefault()
        val locale = Locale.getDefault()

        val calendar = Calendar.getInstance(activeTimeZone, locale).apply {
            timeInMillis = nowMillis
        }

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
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            formattedTime = timeFormatter.format(rawDate),
            formattedDate = dateFormatter.format(rawDate),
            isoLocalTimestamp = try {
                isoFormatter.format(rawDate)
            } catch (_: Throwable) {
                rawDate.toString()
            }
        )

        if (logDebug) {
            logClockSnapshot(snapshot, source)
        }
        return snapshot
    }

    /**
     * Prints raw DateTime.now() equivalent (`Date(System.currentTimeMillis())`) and active timezone
     * details to both Android Logcat and stdout console for verification.
     */
    fun logClockSnapshot(snapshot: DeviceClockSnapshot, source: String = "Clock") {
        val sign = if (snapshot.offsetMinutes >= 0) "+" else "-"
        val absMin = kotlin.math.abs(snapshot.offsetMinutes)
        val offsetFormatted = String.format(Locale.US, "UTC%s%02d:%02d", sign, absMin / 60, absMin % 60)
        val message = "[$source] rawDateTimeNow=${snapshot.rawDate} | localISO=${snapshot.isoLocalTimestamp} | tz=${snapshot.timeZoneId} ($offsetFormatted) | formattedTime=${snapshot.formattedTime}"
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
            TimeZone.setDefault(TimeZone.getTimeZone(newTimeZoneId))
        } else {
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
