package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed class GmailActionResult {
    data class Success(val message: String, val openUrl: String? = null) : GmailActionResult()
    data class Failure(val message: String) : GmailActionResult()
}

object GmailVoiceHandler {

    private const val TAG = "GmailVoiceHandler"

    fun isGmailCommand(queryLower: String): Boolean {
        return queryLower.contains("gmail") ||
                queryLower.contains("email") ||
                queryLower.contains("mail send") ||
                queryLower.contains("send email") ||
                queryLower.contains("check email") ||
                queryLower.contains("check inbox") ||
                queryLower.contains("compose mail")
    }

    fun processGmailCommand(context: Context, rawQuery: String): GmailActionResult {
        val q = rawQuery.lowercase().trim()
        Log.d(TAG, "Processing Gmail command: $q")

        return try {
            if (q.contains("send email") || q.contains("compose") || q.contains("mail karo") || q.contains("send mail")) {
                val (recipient, subject) = extractRecipientAndSubject(rawQuery)
                val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    if (recipient.isNotBlank()) {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                    }
                    if (subject.isNotBlank()) {
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.startActivity(mailIntent)
                    val msg = if (recipient.isNotBlank()) "Opening Gmail to compose email to $recipient" else "Opening Gmail compose screen, Boss."
                    GmailActionResult.Success(msg)
                } catch (e: Exception) {
                    val mailtoUri = "https://mail.google.com/mail/?view=cm&fs=1"
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mailtoUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    GmailActionResult.Success("Opening Gmail Web Composer, Boss.", mailtoUri)
                }
            } else if (q.contains("check") || q.contains("inbox") || q.contains("read")) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    GmailActionResult.Success("Opening Gmail Inbox, Boss.")
                } else {
                    val gmailWebUri = "https://mail.google.com"
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(gmailWebUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    GmailActionResult.Success("Opening Gmail Inbox in browser, Boss.", gmailWebUri)
                }
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    GmailActionResult.Success("Opening Gmail app, Boss.")
                } else {
                    val gmailWebUri = "https://mail.google.com"
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(gmailWebUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    GmailActionResult.Success("Opening Gmail web interface, Boss.", gmailWebUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gmail command error: ${e.message}", e)
            GmailActionResult.Failure("Failed to handle Gmail command: ${e.localizedMessage}")
        }
    }

    private fun extractRecipientAndSubject(query: String): Pair<String, String> {
        val q = query.lowercase()
        var recipient = ""
        var subject = ""

        if (q.contains("to ")) {
            recipient = q.substringAfter("to ").substringBefore(" ").trim()
        }
        if (q.contains("about ") || q.contains("subject ")) {
            subject = q.substringAfter("about ").ifEmpty { q.substringAfter("subject ") }.trim()
        }
        return Pair(recipient, subject)
    }
}
