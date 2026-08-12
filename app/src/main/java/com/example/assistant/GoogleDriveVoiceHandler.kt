package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed class DriveActionResult {
    data class Success(val message: String, val openUrl: String? = null) : DriveActionResult()
    data class Failure(val message: String) : DriveActionResult()
}

object GoogleDriveVoiceHandler {

    private const val TAG = "GoogleDriveVoiceHandler"

    fun isDriveCommand(queryLower: String): Boolean {
        return queryLower.contains("google drive") ||
                queryLower.contains("drive mein") ||
                queryLower.contains("drive search") ||
                queryLower.contains("drive file") ||
                queryLower.contains("drive open") ||
                (queryLower.contains("drive") && (queryLower.contains("open") || queryLower.contains("find") || queryLower.contains("search") || queryLower.contains("kholo")))
    }

    fun processDriveCommand(context: Context, rawQuery: String): DriveActionResult {
        val q = rawQuery.lowercase().trim()
        Log.d(TAG, "Processing Drive command: $q")

        return try {
            val searchTerm = extractSearchTerm(rawQuery)

            if (searchTerm.isNotBlank() && searchTerm != "drive") {
                val driveSearchUri = Uri.parse("https://drive.google.com/drive/search?q=" + Uri.encode(searchTerm))
                val intent = Intent(Intent.ACTION_VIEW, driveSearchUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                DriveActionResult.Success("Searching Google Drive for '$searchTerm', Boss.", driveSearchUri.toString())
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.docs")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    DriveActionResult.Success("Opening Google Drive app, Boss.")
                } else {
                    val webDriveUri = "https://drive.google.com"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webDriveUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DriveActionResult.Success("Opening Google Drive web interface, Boss.", webDriveUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Drive command error: ${e.message}", e)
            DriveActionResult.Failure("Failed to open Google Drive: ${e.localizedMessage}")
        }
    }

    private fun extractSearchTerm(query: String): String {
        return query.replace("open google drive and search", "", ignoreCase = true)
            .replace("search google drive for", "", ignoreCase = true)
            .replace("search drive for", "", ignoreCase = true)
            .replace("find in drive", "", ignoreCase = true)
            .replace("drive search", "", ignoreCase = true)
            .replace("google drive", "", ignoreCase = true)
            .replace("drive kholo", "", ignoreCase = true)
            .replace("open drive", "", ignoreCase = true)
            .replace("drive", "", ignoreCase = true)
            .trim()
    }
}
