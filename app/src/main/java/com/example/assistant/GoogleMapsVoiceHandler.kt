package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed class MapsActionResult {
    data class Success(val message: String, val openUrl: String? = null) : MapsActionResult()
    data class Failure(val message: String) : MapsActionResult()
}

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

    fun processMapsCommand(context: Context, rawQuery: String): MapsActionResult {
        val q = rawQuery.lowercase().trim()
        Log.d(TAG, "Processing Maps command: $q")

        return try {
            if (q.contains("navigate to") || q.contains("direction to") || q.contains("rasta dikhao") || q.contains("route to")) {
                val destination = extractDestination(rawQuery)
                val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (mapIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(mapIntent)
                    MapsActionResult.Success("Google Maps navigation started for '$destination', Boss.")
                } else {
                    val webUri = "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination)
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    MapsActionResult.Success("Opening Google Maps directions for '$destination' in browser.", webUri)
                }
            } else if (q.contains("nearby") || q.contains("pass me")) {
                val placeType = extractNearbyPlace(rawQuery)
                val searchUri = Uri.parse("geo:0,0?q=" + Uri.encode(placeType))
                val mapIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val webUri = "https://www.google.com/maps/search/" + Uri.encode(placeType)
                try {
                    context.startActivity(mapIntent)
                    MapsActionResult.Success("Searching for nearby '$placeType' on Google Maps, Boss.")
                } catch (e: Exception) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    MapsActionResult.Success("Searching nearby '$placeType' on Google Maps web.", webUri)
                }
            } else {
                val location = extractLocation(rawQuery)
                val searchUri = Uri.parse("geo:0,0?q=" + Uri.encode(location))
                val mapIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val webUri = "https://www.google.com/maps/search/" + Uri.encode(location)
                try {
                    context.startActivity(mapIntent)
                    MapsActionResult.Success("Opening location '$location' on Google Maps, Boss.")
                } catch (e: Exception) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    MapsActionResult.Success("Showing '$location' on Google Maps.", webUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Maps command error: ${e.message}", e)
            MapsActionResult.Failure("Failed to open Google Maps: ${e.localizedMessage}")
        }
    }

    private fun extractDestination(query: String): String {
        return query.replace("navigate to", "", ignoreCase = true)
            .replace("direction to", "", ignoreCase = true)
            .replace("directions to", "", ignoreCase = true)
            .replace("rasta dikhao", "", ignoreCase = true)
            .replace("route to", "", ignoreCase = true)
            .replace("show route to", "", ignoreCase = true)
            .replace("open maps for", "", ignoreCase = true)
            .trim().ifEmpty { "Current Location" }
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
