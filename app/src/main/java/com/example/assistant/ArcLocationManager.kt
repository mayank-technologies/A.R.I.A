package com.example.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Snapshot of resolved device GPS coordinates and human-readable locality/city metadata.
 */
data class ArcLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null,
    val locality: String? = null,
    val formattedAddress: String? = null,
    val accuracyMeters: Float? = null,
    val isPrecise: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Best compact label suitable for voice confirmation, ride pickup pins, and UI badges.
     */
    fun displayLabel(): String {
        val parts = listOfNotNull(
            locality?.trim()?.takeIf { it.isNotBlank() },
            city?.trim()?.takeIf { it.isNotBlank() && !it.equals(locality, ignoreCase = true) }
        )
        return when {
            parts.isNotEmpty() -> parts.joinToString(", ")
            !formattedAddress.isNullOrBlank() -> formattedAddress
            else -> String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
        }
    }
}

data class GeocodedPlace(
    val city: String?,
    val locality: String?,
    val formattedAddress: String?
)

/**
 * Unified foreground Location Manager for ARC:
 * - Checks foreground GPS permissions (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`).
 * - Primary retrieval via Google Play Services `FusedLocationProviderClient` (`getCurrentLocation` -> `lastLocation`).
 * - Resilient fallback via Android framework `LocationManagerCompat` / `LocationManager` (`GPS_PROVIDER`, `NETWORK_PROVIDER`, `PASSIVE_PROVIDER`).
 * - Async-safe reverse geocoding via `Geocoder` (API 33+ `GeocodeListener` & legacy synchronous API) to extract city + departure address.
 */
object ArcLocationManager {

    private const val TAG = "ARC_LOCATION"
    private const val CACHE_MAX_AGE_MS = 5 * 60 * 1000L
    private const val FUSED_TIMEOUT_MS = 4500L
    private const val FRAMEWORK_TIMEOUT_MS = 3500L
    private const val GEOCODE_TIMEOUT_MS = 3000L

    val REQUIRED_LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _cachedLocation = MutableStateFlow<ArcLocationInfo?>(null)
    val cachedLocation: StateFlow<ArcLocationInfo?> = _cachedLocation.asStateFlow()

    @Volatile
    private var testingOverride: ArcLocationInfo? = null

    internal fun setLocationOverrideForTesting(info: ArcLocationInfo?) {
        testingOverride = info
        if (info != null) {
            _cachedLocation.value = info
        }
    }

    fun isLocationPermissionGranted(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val granted = fine || coarse
        _hasLocationPermission.value = granted
        return granted
    }

    fun isPreciseLocationGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshPermissionState(context: Context): Boolean {
        return isLocationPermissionGranted(context)
    }

    /**
     * Fetches the freshest available device location with reverse-geocoded city/address.
     * Returns null safely if neither fine nor coarse location permission has been granted.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationInfo(
        context: Context,
        forceRefresh: Boolean = false
    ): ArcLocationInfo? {
        testingOverride?.let { return it }

        if (!isLocationPermissionGranted(context)) {
            Log.d(TAG, "Foreground location permission not granted yet.")
            return null
        }

        val currentCache = _cachedLocation.value
        val isCacheFresh = currentCache != null &&
            (System.currentTimeMillis() - currentCache.timestampMs) <= CACHE_MAX_AGE_MS
        if (!forceRefresh && isCacheFresh) {
            return currentCache
        }

        val appContext = context.applicationContext ?: context
        val isPrecise = isPreciseLocationGranted(appContext)

        // 1. Primary: FusedLocationProviderClient (getCurrentLocation -> lastLocation)
        var rawLocation: Location? = fetchFusedLocation(appContext, isPrecise)

        // 2. Fallback: Android framework LocationManagerCompat / lastKnownLocation
        if (rawLocation == null) {
            rawLocation = fetchFrameworkLocation(appContext, isPrecise)
        }

        if (rawLocation == null) {
            Log.w(TAG, "Unable to obtain fresh or cached device coordinates.")
            return currentCache
        }

        val geocoded = reverseGeocodeSuspend(appContext, rawLocation.latitude, rawLocation.longitude)
        val info = ArcLocationInfo(
            latitude = rawLocation.latitude,
            longitude = rawLocation.longitude,
            city = geocoded?.city,
            locality = geocoded?.locality,
            formattedAddress = geocoded?.formattedAddress,
            accuracyMeters = if (rawLocation.hasAccuracy()) rawLocation.accuracy else null,
            isPrecise = isPrecise,
            timestampMs = System.currentTimeMillis()
        )
        _cachedLocation.value = info
        Log.d(
            TAG,
            "Resolved location: lat=${info.latitude}, lon=${info.longitude}, city=${info.city}, locality=${info.locality}"
        )
        return info
    }

    /**
     * Non-suspending helper for synchronous callers (e.g., immediate deep-link assembly).
     * Returns testing override, recent cached location, or OS last-known provider fix.
     */
    @SuppressLint("MissingPermission")
    fun getBestCachedLocationSync(context: Context): ArcLocationInfo? {
        testingOverride?.let { return it }
        if (!isLocationPermissionGranted(context)) return null

        _cachedLocation.value?.let { cached ->
            if ((System.currentTimeMillis() - cached.timestampMs) <= CACHE_MAX_AGE_MS) {
                return cached
            }
        }

        val loc = getBestLastKnownFrameworkLocation(context) ?: return _cachedLocation.value
        val info = ArcLocationInfo(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            isPrecise = isPreciseLocationGranted(context),
            timestampMs = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        _cachedLocation.value = info
        return info
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFusedLocation(context: Context, isPrecise: Boolean): Location? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val priority = if (isPrecise) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            val cts = CancellationTokenSource()
            val fresh = withTimeoutOrNull(FUSED_TIMEOUT_MS) {
                awaitTask(
                    client.getCurrentLocation(priority, cts.token),
                    onCancel = { cts.cancel() }
                )
            }
            if (fresh != null) return fresh

            withTimeoutOrNull(1800L) {
                awaitTask(client.lastLocation)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "FusedLocationProviderClient unavailable/failed, using framework fallback: ${t.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFrameworkLocation(context: Context, isPrecise: Boolean): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val preferredProvider = when {
            isPrecise && safeIsProviderEnabled(lm, LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            safeIsProviderEnabled(lm, LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                safeIsProviderEnabled(lm, LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            else -> null
        }

        if (preferredProvider != null) {
            val freshCompat = withTimeoutOrNull(FRAMEWORK_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            lm.getCurrentLocation(
                                preferredProvider,
                                signal,
                                ContextCompat.getMainExecutor(context)
                            ) { location ->
                                if (cont.isActive) cont.resume(location)
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            LocationManagerCompat.getCurrentLocation(
                                lm,
                                preferredProvider,
                                null as androidx.core.os.CancellationSignal?,
                                ContextCompat.getMainExecutor(context)
                            ) { location ->
                                if (cont.isActive) cont.resume(location)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getCurrentLocation failed: ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (freshCompat != null) return freshCompat
        }

        return getBestLastKnownFrameworkLocation(context)
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownFrameworkLocation(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = buildList {
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                add(LocationManager.PASSIVE_PROVIDER)
            }
            var best: Location? = null
            for (provider in providers) {
                val candidate = try {
                    lm.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
                if (candidate != null && (best == null || candidate.time > best.time)) {
                    best = candidate
                }
            }
            best
        } catch (e: Exception) {
            Log.w(TAG, "Last known framework location read failed: ${e.message}")
            null
        }
    }

    private fun safeIsProviderEnabled(lm: LocationManager, provider: String): Boolean {
        return try {
            lm.isProviderEnabled(provider)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reverse geocodes coordinates to city + locality + readable pickup address.
     * Properly handles Android 13+ async GeocodeListener as well as synchronous < API 33 APIs.
     */
    suspend fun reverseGeocodeSuspend(
        context: Context,
        latitude: Double,
        longitude: Double
    ): GeocodedPlace? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        try {
                            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                                if (cont.isActive) {
                                    cont.resume(addresses.firstOrNull())
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Async Geocoder failed: ${e.message}")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }

            address?.let { extractPlaceFromAddress(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.message}")
            null
        }
    }

    internal fun extractPlaceFromAddress(addr: Address): GeocodedPlace {
        val city = addr.locality?.takeIf { it.isNotBlank() }
            ?: addr.subAdminArea?.takeIf { it.isNotBlank() }
            ?: addr.adminArea?.takeIf { it.isNotBlank() }

        val locality = addr.subLocality?.takeIf { it.isNotBlank() }
            ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
            ?: addr.featureName?.takeIf {
                it.isNotBlank() && !it.equals(city, ignoreCase = true)
            }

        val fullLine = try {
            addr.getAddressLine(0)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        val compactAddress = fullLine ?: listOfNotNull(locality, city, addr.adminArea)
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotBlank() }

        return GeocodedPlace(
            city = city,
            locality = locality,
            formattedAddress = compactAddress
        )
    }

    private suspend fun <T> awaitTask(
        task: Task<T>,
        onCancel: (() -> Unit)? = null
    ): T? = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { onCancel?.invoke() }
        task.addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }.addOnFailureListener {
            if (cont.isActive) cont.resume(null)
        }.addOnCanceledListener {
            if (cont.isActive) cont.resume(null)
        }
    }
}
