package com.example.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

enum class RideApp(val displayName: String, val packageName: String) {
    UBER("Uber", "com.ubercab"),
    OLA("Ola", "com.olacabs.customer"),
    RAPIDO("Rapido", "com.rapido.passenger")
}

/**
 * Vehicle categories requested by the user ("Bike, car 4-seater, ya 6-seater?").
 * Maps cleanly to each ride provider's deep-link category/service parameter.
 */
enum class RideVehicleOption(
    val displayLabel: String,
    val olaCategory: String,
    val rapidoService: String,
    val uberLabel: String
) {
    BIKE("Bike", "bike", "bike", "Uber Moto (Bike)"),
    AUTO("Auto", "auto", "auto", "Uber Auto"),
    CAR_4_SEATER("Car 4-seater", "mini", "cab", "UberGo / 4-seater Car"),
    CAR_6_SEATER("Car 6-seater", "prime_suv", "cab", "UberXL / 6-seater Car")
}

data class DeviceCoordinates(
    val latitude: Double,
    val longitude: Double,
    val formattedAddress: String? = null,
    val localityLabel: String? = null
)

data class RideDeepLinkPair(
    val app: RideApp,
    val primaryUri: String,
    val fallbackWebUrl: String
)

data class PendingRideRequest(
    val destination: String,
    val preferredApp: RideApp?,
    val createdAtMs: Long = System.currentTimeMillis()
)

sealed class RideBookingResult {
    data class NeedsVehicleSelection(
        val destination: String,
        val message: String
    ) : RideBookingResult()

    data class Launched(
        val appName: String,
        val vehicleLabel: String,
        val destination: String,
        val deepLinkUri: String,
        val fallbackWebUrl: String,
        val message: String,
        val pickupLabel: String? = null
    ) : RideBookingResult()

    data class Cancelled(val message: String) : RideBookingResult()
    data class Failure(val message: String) : RideBookingResult()
}

/**
 * RideBookingAssistHandler:
 * Implements ARC's two-step Ride Booking Assist (Uber / Ola / Rapido):
 * 1. Extracts destination (e.g. "Mathura ke liye ride book karo").
 * 2. If vehicle type is not yet specified, asks user: "Bike, car 4-seater, ya 6-seater?"
 * 3. Reads current device GPS coordinates (when permitted) + destination to build the
 *    target app's deep-link URI with pickup/drop/vehicle pre-filled.
 * 4. Opens the ride app / universal link — NEVER pretends booking is confirmed, and
 *    explicitly tells the user to tap the final Book / Payment button inside the app.
 */
object RideBookingAssistHandler {

    private const val TAG = "RideBookingAssist"
    private const val PENDING_TIMEOUT_MS = 3 * 60 * 1000L

    @Volatile
    private var pendingRide: PendingRideRequest? = null

    fun clearPendingRide() {
        pendingRide = null
    }

    fun getPendingRide(): PendingRideRequest? {
        val current = pendingRide ?: return null
        if (System.currentTimeMillis() - current.createdAtMs > PENDING_TIMEOUT_MS) {
            pendingRide = null
            return null
        }
        return current
    }

    fun isRideBookingCommand(queryLower: String): Boolean {
        val hasRideWord = queryLower.contains("ride") ||
            queryLower.contains("cab") ||
            queryLower.contains("taxi") ||
            queryLower.contains("uber") ||
            queryLower.contains("ola") ||
            queryLower.contains("rapido")
        val hasBookingAction = queryLower.contains("book") ||
            queryLower.contains("chahiye") ||
            queryLower.contains("mangwao") ||
            queryLower.contains("karo") ||
            queryLower.contains("bulao") ||
            queryLower.contains("ke liye") ||
            queryLower.contains("to ")
        return hasRideWord && hasBookingAction
    }

    fun isPendingVehicleReply(queryLower: String): Boolean {
        if (getPendingRide() == null) return false
        return parseVehicleOption(queryLower) != null ||
            queryLower.contains("cancel") ||
            queryLower.contains("rehne do") ||
            queryLower.contains("mat karo")
    }

    fun parsePreferredApp(queryLower: String): RideApp? {
        return when {
            queryLower.contains("uber") -> RideApp.UBER
            queryLower.contains("ola") -> RideApp.OLA
            queryLower.contains("rapido") -> RideApp.RAPIDO
            else -> null
        }
    }

    fun parseVehicleOption(queryLower: String): RideVehicleOption? {
        return when {
            queryLower.contains("6-seater") ||
                queryLower.contains("6 seater") ||
                queryLower.contains("six seater") ||
                queryLower.contains("suv") ||
                queryLower.contains("xl") ||
                queryLower.contains("innova") ||
                queryLower.contains("ertiga") -> RideVehicleOption.CAR_6_SEATER

            queryLower.contains("4-seater") ||
                queryLower.contains("4 seater") ||
                queryLower.contains("four seater") ||
                queryLower.contains("sedan") ||
                queryLower.contains("mini") ||
                queryLower.contains("car") ||
                queryLower.contains("cab") -> RideVehicleOption.CAR_4_SEATER

            queryLower.contains("bike") ||
                queryLower.contains("moto") ||
                queryLower.contains("scooty") ||
                queryLower.contains("two wheeler") -> RideVehicleOption.BIKE

            queryLower.contains("auto") ||
                queryLower.contains("rickshaw") -> RideVehicleOption.AUTO

            else -> null
        }
    }

    fun extractDestination(rawQuery: String): String {
        var cleaned = rawQuery.trim()
        // Pattern: "<dest> ke liye ride book karo"
        val keLiyeRegex = Regex("(?i)^(.+?)\\s+ke\\s+liye\\b.*$")
        keLiyeRegex.find(cleaned)?.let { match ->
            val candidate = match.groupValues[1]
                .replace(Regex("(?i)\\b(uber|ola|rapido|bike|car|cab|taxi|ride|auto|book|mujhe|mere)\\b"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (candidate.isNotBlank()) return candidate
        }

        // Pattern: "book uber/ride to <dest>" or "for <dest>"
        val toForRegex = Regex("(?i)\\b(?:to|for)\\s+(.+?)$")
        toForRegex.find(cleaned)?.let { match ->
            val candidate = match.groupValues[1]
                .replace(Regex("(?i)\\b(book|karo|please|pls|chahiye|ride|cab|uber|ola|rapido)\\b"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (candidate.isNotBlank()) return candidate
        }

        // Strip common filler words
        cleaned = cleaned
            .replace(Regex("(?i)\\b(uber|ola|rapido|ride|cab|taxi|bike|car|auto|4-seater|6-seater|book|karo|chahiye|mangwao|bulao|ke liye)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned
    }

    /**
     * Reads cached/last-known device GPS location + locality synchronously via ArcLocationManager.
     * Returns null safely if permission is not granted (Uber `my_location` still works!).
     */
    @SuppressLint("MissingPermission")
    fun getCurrentGpsCoordinates(context: Context): DeviceCoordinates? {
        val locInfo = ArcLocationManager.getBestCachedLocationSync(context) ?: return null
        return DeviceCoordinates(
            latitude = locInfo.latitude,
            longitude = locInfo.longitude,
            formattedAddress = locInfo.formattedAddress,
            localityLabel = locInfo.displayLabel()
        )
    }

    /**
     * Actively refreshes GPS departure point + reverse-geocoded pickup address before launching ride apps.
     */
    suspend fun resolveCurrentPickupCoordinates(context: Context): DeviceCoordinates? {
        val locInfo = ArcLocationManager.getCurrentLocationInfo(context) ?: return getCurrentGpsCoordinates(context)
        return DeviceCoordinates(
            latitude = locInfo.latitude,
            longitude = locInfo.longitude,
            formattedAddress = locInfo.formattedAddress,
            localityLabel = locInfo.displayLabel()
        )
    }

    /**
     * Builds deep-link + universal web fallback URI for Uber, Ola, or Rapido.
     *
     * Notes on third-party ride deep-link schemes:
     * - Uber: `uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]=...`
     *   plus precise `pickup[latitude]`, `pickup[longitude]`, `pickup[nickname]`, `pickup[formatted_address]`
     *   when GPS + Geocoder are available.
     * - Ola: `olacabs://app/launch?lat=...&lng=...&pickup_name=...&drop_address=...&category=<bike|auto|mini|prime_suv>&utm_source=arc_assistant`
     * - Rapido: `rapido://book?pickup_lat=...&pickup_lng=...&pickup_address=...&drop_address=...&service=<bike|auto|cab>`
     */
    fun buildRideDeepLink(
        app: RideApp,
        destination: String,
        vehicle: RideVehicleOption,
        coords: DeviceCoordinates?
    ): RideDeepLinkPair {
        val encodedDest = Uri.encode(destination)

        return when (app) {
            RideApp.UBER -> {
                val pickupLabel = coords?.localityLabel?.takeIf { it.isNotBlank() } ?: "Current Location"
                val encodedPickupNick = Uri.encode(pickupLabel)
                val encodedPickupAddr = coords?.formattedAddress?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) }

                val pickupCoordsSuffix = if (coords != null) {
                    val addrPart = if (encodedPickupAddr != null) "&pickup[formatted_address]=$encodedPickupAddr" else ""
                    "&pickup[latitude]=${coords.latitude}&pickup[longitude]=${coords.longitude}&pickup[nickname]=$encodedPickupNick$addrPart"
                } else {
                    ""
                }

                val webPickupQuery = if (coords != null) {
                    val addrPart = if (encodedPickupAddr != null) "&pickup[formatted_address]=$encodedPickupAddr" else ""
                    "pickup[latitude]=${coords.latitude}&pickup[longitude]=${coords.longitude}&pickup[nickname]=$encodedPickupNick$addrPart"
                } else {
                    "pickup=my_location"
                }

                val primaryUri = "uber://?action=setPickup&pickup=my_location$pickupCoordsSuffix&dropoff[formatted_address]=$encodedDest&dropoff[nickname]=$encodedDest"
                val webFallback = "https://m.uber.com/ul/?action=setPickup&$webPickupQuery&dropoff[formatted_address]=$encodedDest&dropoff[nickname]=$encodedDest"
                RideDeepLinkPair(app, primaryUri, webFallback)
            }

            RideApp.OLA -> {
                val gpsQuery = if (coords != null) {
                    val pickupName = coords.localityLabel ?: coords.formattedAddress
                    val nameParam = pickupName?.takeIf { it.isNotBlank() }?.let { "&pickup_name=${Uri.encode(it)}" }.orEmpty()
                    "&lat=${coords.latitude}&lng=${coords.longitude}$nameParam"
                } else ""
                val primaryUri = "olacabs://app/launch?utm_source=arc_assistant$gpsQuery&drop_address=$encodedDest&category=${vehicle.olaCategory}"
                val webFallback = "https://book.olacabs.com/?utm_source=arc_assistant$gpsQuery&drop_address=$encodedDest&category=${vehicle.olaCategory}"
                RideDeepLinkPair(app, primaryUri, webFallback)
            }

            RideApp.RAPIDO -> {
                val gpsQuery = if (coords != null) {
                    val pickupAddr = coords.formattedAddress ?: coords.localityLabel
                    val addrParam = pickupAddr?.takeIf { it.isNotBlank() }?.let { "&pickup_address=${Uri.encode(it)}" }.orEmpty()
                    "&pickup_lat=${coords.latitude}&pickup_lng=${coords.longitude}$addrParam"
                } else ""
                val primaryUri = "rapido://book?service=${vehicle.rapidoService}$gpsQuery&drop_address=$encodedDest"
                val webFallback = "https://www.rapido.bike/"
                RideDeepLinkPair(app, primaryUri, webFallback)
            }
        }
    }

    /**
     * Picks the best installed RideApp based on user preference or vehicle option.
     */
    fun selectTargetRideApp(
        context: Context,
        preferredApp: RideApp?,
        vehicle: RideVehicleOption
    ): RideApp {
        if (preferredApp != null) return preferredApp
        val priorityOrder = when (vehicle) {
            RideVehicleOption.BIKE, RideVehicleOption.AUTO -> listOf(RideApp.RAPIDO, RideApp.UBER, RideApp.OLA)
            RideVehicleOption.CAR_4_SEATER, RideVehicleOption.CAR_6_SEATER -> listOf(RideApp.UBER, RideApp.OLA, RideApp.RAPIDO)
        }
        return priorityOrder.firstOrNull { SmartAppOpener.isAppInstalled(context, it.packageName) }
            ?: RideApp.UBER
    }

    /**
     * Suspending entry point that first refreshes GPS + reverse-geocoded pickup point before launching.
     */
    suspend fun processRideCommandWithLocation(
        context: Context,
        rawQuery: String,
        explicitVehicle: RideVehicleOption? = null,
        explicitDestination: String? = null,
        explicitApp: RideApp? = null
    ): RideBookingResult {
        val resolvedCoords = resolveCurrentPickupCoordinates(context)
        return processRideCommand(
            context = context,
            rawQuery = rawQuery,
            explicitVehicle = explicitVehicle,
            explicitDestination = explicitDestination,
            explicitApp = explicitApp,
            resolvedCoords = resolvedCoords
        )
    }

    /**
     * Main entry point for voice/text ride booking commands (handles both Step 1 ask & Step 2 launch).
     */
    fun processRideCommand(
        context: Context,
        rawQuery: String,
        explicitVehicle: RideVehicleOption? = null,
        explicitDestination: String? = null,
        explicitApp: RideApp? = null,
        resolvedCoords: DeviceCoordinates? = null
    ): RideBookingResult {
        val queryLower = rawQuery.lowercase().trim()

        // Step 2: Check if user is answering our pending "Bike, car 4-seater, ya 6-seater?" question
        val activePending = getPendingRide()
        if (activePending != null && explicitDestination == null) {
            if (queryLower.contains("cancel") || queryLower.contains("rehne do") || queryLower.contains("mat karo")) {
                pendingRide = null
                return RideBookingResult.Cancelled("Theek hai Boss, ride booking assist cancel kar diya hai.")
            }

            val vehicleReply = explicitVehicle ?: parseVehicleOption(queryLower)
            val appReply = explicitApp ?: parsePreferredApp(queryLower) ?: activePending.preferredApp
            if (vehicleReply != null) {
                pendingRide = null
                return launchRideAppWithDetails(
                    context = context,
                    destination = activePending.destination,
                    vehicle = vehicleReply,
                    preferredApp = appReply,
                    resolvedCoords = resolvedCoords
                )
            }
        }

        // Step 1: Parse new ride request
        val destination = explicitDestination?.trim()?.takeIf { it.isNotBlank() }
            ?: extractDestination(rawQuery)

        if (destination.isBlank()) {
            return RideBookingResult.Failure("Boss, kahan ke liye ride chahiye? Destination batayein (jaise 'Mathura ke liye ride book karo').")
        }

        val preferredApp = explicitApp ?: parsePreferredApp(queryLower)
        val vehicle = explicitVehicle ?: parseVehicleOption(queryLower)

        if (vehicle == null) {
            // Ask vehicle confirmation question before launching ride app
            pendingRide = PendingRideRequest(destination = destination, preferredApp = preferredApp)
            val askMsg = "$destination ke liye ride open karne se pehle batayein — Bike, Car 4-seater, ya 6-seater?"
            return RideBookingResult.NeedsVehicleSelection(destination, askMsg)
        }

        pendingRide = null
        return launchRideAppWithDetails(context, destination, vehicle, preferredApp, resolvedCoords)
    }

    fun launchRideAppWithDetails(
        context: Context,
        destination: String,
        vehicle: RideVehicleOption,
        preferredApp: RideApp? = null,
        resolvedCoords: DeviceCoordinates? = null
    ): RideBookingResult {
        val targetApp = selectTargetRideApp(context, preferredApp, vehicle)
        val coords = resolvedCoords ?: getCurrentGpsCoordinates(context)
        val linkPair = buildRideDeepLink(targetApp, destination, vehicle, coords)
        val isInstalled = SmartAppOpener.isAppInstalled(context, targetApp.packageName)

        var openedNative = false
        if (isInstalled) {
            try {
                val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(linkPair.primaryUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(targetApp.packageName)
                }
                context.startActivity(deepLinkIntent)
                openedNative = true
            } catch (e: Exception) {
                Log.w(TAG, "Deep link failed for ${targetApp.displayName}, trying app launch fallback: ${e.message}")
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetApp.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        openedNative = true
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (!openedNative) {
            SmartAppOpener.launchExternalBrowser(context, linkPair.fallbackWebUrl)
        }

        val pickupSummary = coords?.let {
            it.localityLabel?.takeIf { lbl -> lbl.isNotBlank() }
                ?: it.formattedAddress?.takeIf { addr -> addr.isNotBlank() }
                ?: String.format(Locale.US, "GPS (%.4f, %.4f)", it.latitude, it.longitude)
        }
        val routeText = if (pickupSummary != null) {
            "$pickupSummary se $destination"
        } else {
            "$destination"
        }

        // CRITICAL HONESTY & SAFETY NOTICE: Never say ride is booked/confirmed!
        val honestMessage = if (openedNative) {
            "${targetApp.displayName} khol diya hai ($routeText ke liye ${vehicle.displayLabel} pre-filled details ke saath). Final confirm aur payment ke liye aap khud app ke andar 'Book Ride' button dabaiye — third-party apps external auto-booking allow nahi karte."
        } else {
            "${targetApp.displayName} app installed nahi mila, isliye browser me link khol diya hai ($routeText, ${vehicle.displayLabel}). Final confirm aur payment aap khud complete kijiyega."
        }

        return RideBookingResult.Launched(
            appName = targetApp.displayName,
            vehicleLabel = vehicle.displayLabel,
            destination = destination,
            deepLinkUri = linkPair.primaryUri,
            fallbackWebUrl = linkPair.fallbackWebUrl,
            message = honestMessage,
            pickupLabel = pickupSummary
        )
    }
}
