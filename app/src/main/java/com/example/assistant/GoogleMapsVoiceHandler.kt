package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed class MapsActionResult {
    data class Success(val message: String, val openUrl: String? = null) : MapsActionResult()
    data class Failure(val message: String) : MapsActionResult()
}

/**
 * GoogleMapsVoiceHandler:
 * Supports voice commands for point-to-point routes ("Delhi se Mathura ka route dikhao",
 * "route from Noida to Agra"), single-destination navigation ("navigate to Mathura"),
 * and nearby / location lookups on Google Maps / external browser.
 */
object GoogleMapsVoiceHandler {

    private const val TAG = "GoogleMapsVoiceHandler"

    fun isMapsCommand(queryLower: String): Boolean {
        return queryLower.contains("map") ||
            queryLower.contains("maps") ||
            queryLower.contains("rasta") ||
            queryLower.contains("direction") ||
            queryLower.contains("navigation") ||
            queryLower.contains("route") ||
            queryLower.contains("kahan hai") ||
            queryLower.contains("location of") ||
            queryLower.contains("navigate to") ||
            queryLower.contains("nearby") ||
            queryLower.contains("pass me")
    }

    /**
     * Builds a standard Google Maps Directions Universal Link:
     * Example: https://www.google.com/maps/dir/?api=1&origin=Delhi&destination=Mathura&travelmode=driving
     * Note: If origin is null/blank, Google Maps defaults to the user's current device GPS location.
     */
    fun buildGoogleMapsRouteUrl(
        origin: String?,
        destination: String,
        travelMode: String = "driving"
    ): String {
        val cleanDest = destination.trim().ifBlank { "New Delhi" }
        val cleanMode = travelMode.trim().lowercase().ifBlank { "driving" }
        val originParam = origin?.trim()?.takeIf { it.isNotBlank() }?.let {
            "&origin=${Uri.encode(it)}"
        }.orEmpty()
        return "https://www.google.com/maps/dir/?api=1${originParam}&destination=${Uri.encode(cleanDest)}&travelmode=${Uri.encode(cleanMode)}"
    }

    internal fun resolveOriginWithGps(context: Context, extractedOrigin: String?): String? {
        val raw = extractedOrigin?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lower = raw.lowercase()
        val isCurrentLocationPhrase = lower in setOf(
            "my location", "current location", "here", "yahan", "mere paas", "meri location", "abhi"
        )
        if (!isCurrentLocationPhrase) return raw

        val gps = ArcLocationManager.getBestCachedLocationSync(context)
        return if (gps != null) "${gps.latitude},${gps.longitude}" else null
    }

    /**
     * Extracts (origin, destination) from English and Hindi/Hinglish route commands:
     * - "Delhi se Mathura ka route dikhao" -> Pair("Delhi", "Mathura")
     * - "from Delhi to Mathura directions" -> Pair("Delhi", "Mathura")
     * - "Delhi to Mathura route dikhao" -> Pair("Delhi", "Mathura")
     * - "Mathura ka route dikhao" -> Pair(null, "Mathura")
     */
    fun extractOriginAndDestination(rawQuery: String): Pair<String?, String> {
        var cleaned = rawQuery.trim()
        val trailingNoise = listOf(
            "ka route dikhao", "ka rasta dikhao", "ka route batao", "ka rasta batao",
            "route dikhao", "rasta dikhao", "route batao", "rasta batao",
            "on google maps", "in google maps", "on maps", "in maps", "google maps me",
            "google maps par", "maps me", "maps par", "dikhao", "batao", "show me", "open",
            "directions", "direction", "route", "navigation"
        )
        val leadingNoise = listOf(
            "show route from", "show directions from", "get directions from",
            "route from", "directions from", "direction from", "navigate from",
            "google maps me", "google maps par", "maps me", "maps par", "from"
        )

        leadingNoise.forEach { prefix ->
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
            }
        }
        trailingNoise.forEach { suffix ->
            cleaned = cleaned.replace(Regex("(?i)\\b${Regex.escape(suffix)}\\b"), " ").trim()
        }
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        // Pattern 1: "<origin> se <destination> (ka/ke/tak)?"
        val hindiSeRegex = Regex("(?i)^(.+?)\\s+se\\s+(.+?)(?:\\s+(?:ka|ke|ki|tak))?$")
        hindiSeRegex.find(cleaned)?.let { match ->
            val origin = match.groupValues[1].trim()
            val dest = cleanTrailingPlaceTokens(match.groupValues[2])
            if (origin.isNotBlank() && dest.isNotBlank()) {
                return Pair(origin, dest)
            }
        }

        // Pattern 2: "<origin> to <destination>"
        val englishToRegex = Regex("(?i)^(.+?)\\s+to\\s+(.+)$")
        englishToRegex.find(cleaned)?.let { match ->
            val origin = match.groupValues[1]
                .replace(Regex("(?i)^(navigate|direction|directions|route|rasta|go)\\s*"), "")
                .trim()
            val dest = cleanTrailingPlaceTokens(match.groupValues[2])
            if (origin.isNotBlank() && dest.isNotBlank()) {
                return Pair(origin, dest)
            }
        }

        // Fallback: single destination only
        val singleDest = extractDestination(rawQuery)
        return Pair(null, singleDest)
    }

    private fun cleanTrailingPlaceTokens(raw: String): String {
        return raw
            .replace(Regex("(?i)\\b(ka|ke|ki|route|rasta|dikhao|batao|directions|direction|maps|google maps)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun processMapsCommand(context: Context, rawQuery: String): MapsActionResult {
        val q = rawQuery.lowercase().trim()
        Log.d(TAG, "Processing Maps command: $q")

        return try {
            val isRouteIntent = q.contains("route") ||
                q.contains("rasta") ||
                q.contains("direction") ||
                q.contains("navigate") ||
                Regex("(?i)\\b\\w+\\s+(se|to)\\s+\\w+").containsMatchIn(q)

            if (isRouteIntent) {
                val (rawOrigin, destination) = extractOriginAndDestination(rawQuery)
                val origin = resolveOriginWithGps(context, rawOrigin)
                val routeUrl = buildGoogleMapsRouteUrl(origin, destination, travelMode = "driving")
                val launchedInMaps = launchGoogleMapsOrBrowser(context, routeUrl)

                val msg = when {
                    origin != null && launchedInMaps ->
                        "$origin se $destination ka driving route Google Maps me khol diya hai, Boss! Navigation shuru karne ke liye Start button dabaiye."
                    origin != null ->
                        "$origin se $destination ka driving route browser me khol diya hai, Boss!"
                    launchedInMaps ->
                        "$destination ke liye route Google Maps me khol diya hai, Boss! Start dabakar navigation shuru kar sakte hain."
                    else ->
                        "$destination ka route Google Maps web par khol diya hai, Boss!"
                }
                MapsActionResult.Success(msg, routeUrl)
            } else if (q.contains("nearby") || q.contains("pass me")) {
                val placeType = extractNearbyPlace(rawQuery)
                val searchUri = Uri.parse("geo:0,0?q=" + Uri.encode(placeType))
                val webUri = "https://www.google.com/maps/search/" + Uri.encode(placeType)
                if (launchMapsIntent(context, searchUri)) {
                    MapsActionResult.Success("Aas-paas '$placeType' Google Maps par search kar raha hu, Boss.", webUri)
                } else {
                    SmartAppOpener.launchExternalBrowser(context, webUri)
                    MapsActionResult.Success("Nearby '$placeType' browser me Google Maps par khol raha hu.", webUri)
                }
            } else {
                val location = extractLocation(rawQuery)
                val searchUri = Uri.parse("geo:0,0?q=" + Uri.encode(location))
                val webUri = "https://www.google.com/maps/search/" + Uri.encode(location)
                if (launchMapsIntent(context, searchUri)) {
                    MapsActionResult.Success("'$location' ko Google Maps par khol raha hu, Boss.", webUri)
                } else {
                    SmartAppOpener.launchExternalBrowser(context, webUri)
                    MapsActionResult.Success("'$location' Google Maps web par dikha raha hu.", webUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Maps command error: ${e.message}", e)
            MapsActionResult.Failure("Google Maps open karne me error aaya: ${e.localizedMessage}")
        }
    }

    /**
     * Opens the HTTPS Google Maps route URL in native Google Maps app if available,
     * falling back to external browser (url_launcher equivalent on Android).
     * Returns true if opened in native Google Maps app, false if opened in browser.
     */
    fun launchGoogleMapsOrBrowser(context: Context, routeUrl: String): Boolean {
        val uri = Uri.parse(routeUrl)
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (mapsIntent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(mapsIntent)
                true
            } catch (_: Exception) {
                SmartAppOpener.launchExternalBrowser(context, routeUrl)
                false
            }
        } else {
            SmartAppOpener.launchExternalBrowser(context, routeUrl)
            false
        }
    }

    private fun launchMapsIntent(context: Context, uri: Uri): Boolean {
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractDestination(query: String): String {
        return query.replace("navigate to", "", ignoreCase = true)
            .replace("direction to", "", ignoreCase = true)
            .replace("directions to", "", ignoreCase = true)
            .replace("rasta dikhao", "", ignoreCase = true)
            .replace("route dikhao", "", ignoreCase = true)
            .replace("route to", "", ignoreCase = true)
            .replace("show route to", "", ignoreCase = true)
            .replace("open maps for", "", ignoreCase = true)
            .replace(Regex("(?i)\\b(ka|ke|ki)\\s*$"), "")
            .trim()
            .ifEmpty { "Current Location" }
    }

    private fun extractNearbyPlace(query: String): String {
        return query.replace("find nearby", "", ignoreCase = true)
            .replace("search nearby", "", ignoreCase = true)
            .replace("pass me", "", ignoreCase = true)
            .replace("nearby", "", ignoreCase = true)
            .replace("show", "", ignoreCase = true)
            .replace("maps", "", ignoreCase = true)
            .trim().ifEmpty { "restaurants" }
    }

    private fun extractLocation(query: String): String {
        return query.replace("where is", "", ignoreCase = true)
            .replace("location of", "", ignoreCase = true)
            .replace("show on map", "", ignoreCase = true)
            .replace("google maps", "", ignoreCase = true)
            .replace("map", "", ignoreCase = true)
            .trim().ifEmpty { "Delhi, India" }
    }
}
