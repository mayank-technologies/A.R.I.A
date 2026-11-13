package com.example

import com.example.assistant.DeviceClockHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class DeviceClockHelperTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun saveOriginalTimezone() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun restoreOriginalTimezone() {
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
    fun `millisUntilNextMinuteTick aligns to minute boundary`() {
        // 15 seconds into a minute -> 45,050 ms remaining
        val remaining = DeviceClockHelper.millisUntilNextMinuteTick(nowMillis = 60_000L * 10 + 15_000L)
        assertEquals(45_050L, remaining)
    }
}
