package com.example

import com.example.assistant.DeviceClockHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DeviceClockHelperTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun saveOriginalTimezone() {
        originalTimeZone = TimeZone.getDefault()
        DeviceClockHelper.onTimezoneChanged(null)
    }

    @After
    fun restoreOriginalTimezone() {
        DeviceClockHelper.onTimezoneChanged(null)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `clock helper reflects live timezone changes without stale formatter cache`() {
        DeviceClockHelper.onTimezoneChanged("Asia/Kolkata")
        val istSnapshot = DeviceClockHelper.getCurrentDeviceClockInfo(logDebug = true, source = "TestIST")
        assertEquals(330, istSnapshot.offsetMinutes) // UTC+05:30

        DeviceClockHelper.onTimezoneChanged("Asia/Dubai")
        val dubaiSnapshot = DeviceClockHelper.getCurrentDeviceClockInfo(logDebug = true, source = "TestDubai")
        assertEquals(240, dubaiSnapshot.offsetMinutes) // UTC+04:00 (90 min behind IST)

        assertTrue(istSnapshot.formattedTime != dubaiSnapshot.formattedTime)
    }

    @Test
    fun `resolveTimeBasedGreeting covers all 24 hours without off-by-range or wrong order`() {
        for (hour in 4..11) {
            assertEquals("Hour $hour should be Good morning", "Good morning", DeviceClockHelper.resolveTimeBasedGreeting(hour))
        }
        for (hour in 12..16) {
            assertEquals("Hour $hour should be Good afternoon", "Good afternoon", DeviceClockHelper.resolveTimeBasedGreeting(hour))
        }
        for (hour in 17..20) {
            assertEquals("Hour $hour should be Good evening", "Good evening", DeviceClockHelper.resolveTimeBasedGreeting(hour))
        }
        for (hour in listOf(21, 22, 23, 0, 1, 2, 3)) {
            assertEquals("Hour $hour should be Good night", "Good night", DeviceClockHelper.resolveTimeBasedGreeting(hour))
        }
    }

    @Test
    fun `morning 8_30 AM IST evaluates Good morning and not Good night`() {
        // 08:30 AM IST (Asia/Kolkata, UTC+05:30) == 03:00 AM UTC
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 16, 3, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fixedEpochMillis = utcCal.timeInMillis

        // In UTC, 03:00 AM would fall into 0..3 -> Good night (demonstrating why wrong timezone caused Good Night in morning)
        DeviceClockHelper.onTimezoneChanged("UTC")
        val utcDecision = DeviceClockHelper.evaluateTimeBasedGreeting(
            source = "TestMorningUTC",
            logDebug = true,
            nowMillis = fixedEpochMillis
        )
        assertEquals(3, utcDecision.hourOfDay)
        assertEquals("Good night", utcDecision.greeting)

        // In Asia/Kolkata, the exact same instant is 08:30 AM -> must resolve Good morning
        DeviceClockHelper.onTimezoneChanged("Asia/Kolkata")
        val istDecision = DeviceClockHelper.evaluateTimeBasedGreeting(
            source = "TestMorningIST",
            logDebug = true,
            nowMillis = fixedEpochMillis
        )
        assertEquals(8, istDecision.hourOfDay)
        assertEquals(30, istDecision.minute)
        assertEquals("AM", istDecision.amPm)
        assertEquals("Good morning", istDecision.greeting)
    }

    @Test
    fun `standby power-off farewell avoids Good night in morning unless explicit Good Night routine`() {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 16, 4, 0, 0) // 09:30 AM IST
            set(Calendar.MILLISECOND, 0)
        }
        DeviceClockHelper.onTimezoneChanged("Asia/Kolkata")

        val morningPowerOff = DeviceClockHelper.resolveStandbyFarewell(
            displayName = "Rahul",
            isExplicitGoodNightCommand = false,
            nowMillis = utcCal.timeInMillis
        )
        assertFalse(morningPowerOff.contains("Good night", ignoreCase = true))

        val explicitGoodNight = DeviceClockHelper.resolveStandbyFarewell(
            displayName = "Rahul",
            isExplicitGoodNightCommand = true,
            nowMillis = utcCal.timeInMillis
        )
        assertTrue(explicitGoodNight.startsWith("Good night, Rahul!"))
    }

    @Test
    fun `millisUntilNextMinuteTick aligns to minute boundary`() {
        // 15 seconds into a minute -> 45,050 ms remaining
        val remaining = DeviceClockHelper.millisUntilNextMinuteTick(nowMillis = 60_000L * 10 + 15_000L)
        assertEquals(45_050L, remaining)
    }
}
