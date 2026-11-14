package com.example

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.assistant.DeviceCoordinates
import com.example.assistant.GoogleMapsVoiceHandler
import com.example.assistant.MapsActionResult
import com.example.assistant.RideApp
import com.example.assistant.RideBookingAssistHandler
import com.example.assistant.RideBookingResult
import com.example.assistant.RideVehicleOption
import com.example.assistant.ShoppingAssistHandler
import com.example.assistant.ShoppingAssistResult
import com.example.geminilive.GeminiLiveFunctionRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationRideShoppingAssistTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        RideBookingAssistHandler.clearPendingRide()
    }

    @After
    fun tearDown() {
        RideBookingAssistHandler.clearPendingRide()
    }

    @Test
    fun `google maps extracts origin and destination and builds exact driving route url`() {
        val (origin, dest) = GoogleMapsVoiceHandler.extractOriginAndDestination("Delhi se Mathura ka route dikhao")
        assertEquals("Delhi", origin)
        assertEquals("Mathura", dest)

        val routeUrl = GoogleMapsVoiceHandler.buildGoogleMapsRouteUrl(origin, dest)
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=Delhi&destination=Mathura&travelmode=driving",
            routeUrl
        )

        val (origin2, dest2) = GoogleMapsVoiceHandler.extractOriginAndDestination("from Noida Sector 62 to India Gate route")
        assertEquals("Noida Sector 62", origin2)
        assertEquals("India Gate", dest2)

        val result = GoogleMapsVoiceHandler.processMapsCommand(application, "Delhi se Mathura ka route dikhao")
        assertTrue(result is MapsActionResult.Success)
        assertEquals(routeUrl, (result as MapsActionResult.Success).openUrl)

        val startedIntent = Shadows.shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(routeUrl, startedIntent.dataString)
    }

    @Test
    fun `ride booking asks bike or 4-seater or 6-seater first then launches prefilled link with honesty warning`() {
        // Step 1: User says destination without specifying vehicle type
        val step1 = RideBookingAssistHandler.processRideCommand(application, "Mathura ke liye ride book karo")
        assertTrue(step1 is RideBookingResult.NeedsVehicleSelection)
        val askText = (step1 as RideBookingResult.NeedsVehicleSelection).message
        assertTrue(askText.contains("Bike"))
        assertTrue(askText.contains("4-seater"))
        assertTrue(askText.contains("6-seater"))

        // Step 2: User answers "car 4-seater"
        assertTrue(RideBookingAssistHandler.isPendingVehicleReply("car 4-seater"))
        val step2 = RideBookingAssistHandler.processRideCommand(application, "car 4-seater")
        assertTrue(step2 is RideBookingResult.Launched)
        val launched = step2 as RideBookingResult.Launched
        assertEquals("Car 4-seater", launched.vehicleLabel)
        assertEquals("Mathura", launched.destination)
        assertTrue(launched.deepLinkUri.contains("Mathura"))
        // Must NEVER falsely claim ride is booked/confirmed
        assertFalse(launched.message.lowercase().contains("booking confirm ho gayi"))
        assertTrue(launched.message.contains("Final confirm aur payment"))
    }

    @Test
    fun `ride deep links encode gps coordinates and vehicle categories for uber ola and rapido`() {
        val gps = DeviceCoordinates(28.6139, 77.2090)

        val uber = RideBookingAssistHandler.buildRideDeepLink(RideApp.UBER, "Mathura", RideVehicleOption.CAR_4_SEATER, gps)
        assertTrue(uber.primaryUri.startsWith("uber://?action=setPickup&pickup=my_location"))
        assertTrue(uber.primaryUri.contains("dropoff[formatted_address]=Mathura"))
        assertTrue(uber.fallbackWebUrl.contains("pickup[latitude]=28.6139"))

        val ola6 = RideBookingAssistHandler.buildRideDeepLink(RideApp.OLA, "Mathura", RideVehicleOption.CAR_6_SEATER, gps)
        assertTrue(ola6.primaryUri.startsWith("olacabs://app/launch"))
        assertTrue(ola6.primaryUri.contains("category=prime_suv"))
        assertTrue(ola6.primaryUri.contains("drop_address=Mathura"))

        val rapidoBike = RideBookingAssistHandler.buildRideDeepLink(RideApp.RAPIDO, "Mathura", RideVehicleOption.BIKE, gps)
        assertTrue(rapidoBike.primaryUri.startsWith("rapido://book"))
        assertTrue(rapidoBike.primaryUri.contains("service=bike"))
        assertTrue(rapidoBike.primaryUri.contains("pickup_lat=28.6139"))
    }

    @Test
    fun `shopping assist builds flipkart search deep link and never claims auto checkout`() {
        val (store, product) = ShoppingAssistHandler.extractStoreAndProduct("Flipkart se 500g sugar order karo")
        assertEquals("Flipkart", store.displayName)
        assertEquals("500g sugar", product)

        val (deepLink, webUrl) = ShoppingAssistHandler.buildSearchUris(store, product)
        assertEquals("flipkart://search?q=500g%20sugar", deepLink)
        assertEquals("https://www.flipkart.com/search?q=500g%20sugar", webUrl)

        val result = ShoppingAssistHandler.processShoppingCommand(application, "Flipkart se 500g sugar order karo")
        val msg = when (result) {
            is ShoppingAssistResult.OpenedWithSearch -> result.message
            is ShoppingAssistResult.OpenedAppOnly -> result.message
            is ShoppingAssistResult.Failure -> result.message
        }
        assertTrue(msg.contains("500g sugar"))
        assertFalse(msg.lowercase().contains("order ho gaya"))
        assertTrue(msg.contains("final confirm aur payment"))
    }

    @Test
    fun `gemini live registry exposes route ride and shopping tools`() {
        val declarations = GeminiLiveFunctionRegistry.getToolsDeclarationJson()
            .getJSONObject(0)
            .getJSONArray("functionDeclarations")
        val names = (0 until declarations.length()).map { declarations.getJSONObject(it).getString("name") }

        assertTrue("openGoogleMapsRoute" in names)
        assertTrue("assistRideBooking" in names)
        assertTrue("assistShoppingSearch" in names)

        // Verify assistRideBooking asks vehicle question when vehicleType omitted
        val askOutput = GeminiLiveFunctionRegistry.executeToolCall(
            context = application,
            callId = "ride_1",
            functionName = "assistRideBooking",
            args = JSONObject().put("destination", "Mathura")
        )
        assertTrue(askOutput.success)
        assertTrue(askOutput.responseData.getBoolean("needsVehicleSelection"))
        assertTrue(askOutput.verbalConfirmation.contains("Bike"))
    }
}
