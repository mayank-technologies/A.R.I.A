package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.example.assistant.ArcLocationInfo
import com.example.assistant.ArcLocationManager
import com.example.assistant.AriaCommandProcessor
import com.example.assistant.DeviceCoordinates
import com.example.assistant.GoogleMapsVoiceHandler
import com.example.assistant.RideApp
import com.example.assistant.RideBookingAssistHandler
import com.example.assistant.RideBookingResult
import com.example.assistant.RideVehicleOption
import com.example.data.AriaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocationWeatherRideTest {

    private lateinit var application: Application
    private lateinit var db: AriaDatabase

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        db = AriaDatabase.getDatabase(application)
        ArcLocationManager.setLocationOverrideForTesting(null)
        RideBookingAssistHandler.clearPendingRide()
    }

    @After
    fun tearDown() {
        ArcLocationManager.setLocationOverrideForTesting(null)
        RideBookingAssistHandler.clearPendingRide()
    }

    @Test
    fun `arc location manager enforces runtime permission check and reads framework location when granted`() {
        // Without permission -> denied & returns null
        Shadows.shadowOf(application).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        assertFalse(ArcLocationManager.isLocationPermissionGranted(application))
        assertNull(ArcLocationManager.getBestCachedLocationSync(application))

        // Grant fine & coarse permissions + populate framework LocationManager last known location
        Shadows.shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val lm = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val mockLoc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 28.6415
            longitude = 77.3714
            accuracy = 12f
            time = System.currentTimeMillis()
        }
        Shadows.shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, mockLoc)

        assertTrue(ArcLocationManager.isLocationPermissionGranted(application))
        assertTrue(ArcLocationManager.isPreciseLocationGranted(application))

        val resolved = ArcLocationManager.getBestCachedLocationSync(application)
        assertNotNull(resolved)
        assertEquals(28.6415, resolved!!.latitude, 0.0001)
        assertEquals(77.3714, resolved.longitude, 0.0001)
    }

    @Test
    fun `weather command extracts explicit city or returns null for local gps lookup`() = runBlocking {
        val processor = AriaCommandProcessor(application, db.ariaDao())

        assertNull(processor.extractCity("aaj ka mausam kaisa hai"))
        assertNull(processor.extractCity("Weather update"))
        assertEquals("Mumbai", processor.extractCity("Weather in Mumbai"))

        // Provide active GPS + reverse-geocoded local city
        ArcLocationManager.setLocationOverrideForTesting(
            ArcLocationInfo(
                latitude = 28.6415,
                longitude = 77.3714,
                city = "Ghaziabad",
                locality = "Indirapuram",
                formattedAddress = "Indirapuram, Ghaziabad, UP",
                isPrecise = true
            )
        )

        val snap = processor.fetchWeatherSnapshot(city = null)
        assertTrue(snap.isCurrentLocation)
        assertEquals("Ghaziabad", snap.city)
        assertTrue(snap.summaryLine.contains("Indirapuram, Ghaziabad"))
    }

    @Test
    fun `ride deep links and processor attach reverse geocoded departure point`() = runBlocking {
        val gps = DeviceCoordinates(
            latitude = 28.6415,
            longitude = 77.3714,
            formattedAddress = "Indirapuram, Ghaziabad",
            localityLabel = "Indirapuram, Ghaziabad"
        )

        val uber = RideBookingAssistHandler.buildRideDeepLink(RideApp.UBER, "Mathura", RideVehicleOption.CAR_4_SEATER, gps)
        assertTrue(uber.primaryUri.startsWith("uber://?action=setPickup&pickup=my_location"))
        assertTrue(uber.primaryUri.contains("pickup[latitude]=28.6415"))
        assertTrue(uber.primaryUri.contains("pickup[formatted_address]=Indirapuram%2C%20Ghaziabad"))
        assertTrue(uber.fallbackWebUrl.contains("pickup[nickname]=Indirapuram%2C%20Ghaziabad"))

        val ola = RideBookingAssistHandler.buildRideDeepLink(RideApp.OLA, "Mathura", RideVehicleOption.BIKE, gps)
        assertTrue(ola.primaryUri.contains("lat=28.6415"))
        assertTrue(ola.primaryUri.contains("pickup_name=Indirapuram%2C%20Ghaziabad"))

        val rapido = RideBookingAssistHandler.buildRideDeepLink(RideApp.RAPIDO, "Mathura", RideVehicleOption.BIKE, gps)
        assertTrue(rapido.primaryUri.contains("pickup_lat=28.6415"))
        assertTrue(rapido.primaryUri.contains("pickup_address=Indirapuram%2C%20Ghaziabad"))

        // Verify suspending ride flow resolves ArcLocationManager pickup
        ArcLocationManager.setLocationOverrideForTesting(
            ArcLocationInfo(
                latitude = 28.6415,
                longitude = 77.3714,
                city = "Ghaziabad",
                locality = "Indirapuram",
                formattedAddress = "Indirapuram, Ghaziabad",
                isPrecise = true
            )
        )
        val result = RideBookingAssistHandler.processRideCommandWithLocation(
            context = application,
            rawQuery = "Uber car 4-seater book karo to Mathura"
        )
        assertTrue(result is RideBookingResult.Launched)
        val launched = result as RideBookingResult.Launched
        assertEquals("Indirapuram, Ghaziabad", launched.pickupLabel)
        assertTrue(launched.message.contains("Indirapuram, Ghaziabad se Mathura"))
    }

    @Test
    fun `google maps resolves current location phrase to gps coordinates`() {
        ArcLocationManager.setLocationOverrideForTesting(
            ArcLocationInfo(
                latitude = 28.6415,
                longitude = 77.3714,
                city = "Ghaziabad",
                locality = "Indirapuram"
            )
        )
        val resolvedOrigin = GoogleMapsVoiceHandler.resolveOriginWithGps(application, "current location")
        assertEquals("28.6415,77.3714", resolvedOrigin)
    }
}
