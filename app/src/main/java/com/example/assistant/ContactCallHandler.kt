package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * FEATURE 3: Direct Phone Calling & Contact Matching Handler
 * 
 * Voice Command Examples:
 * - "call Rahul"
 * - "call this person"
 * - "call Papa"
 * - "call 9876543210"
 * - "Rahul ko call karo"
 * 
 * Yeh class:
 * 1. Command se contact ka naam ya phone number extract karti hai.
 * 2. Device ke contacts database (`ContactsContract`) se exact / fuzzy matching karti hai.
 * 3. Matched contact ka phone number nikaal kar native Phone Dialer (`tel:[number]`) trigger karti hai.
 * 4. Agar contact na mile, ARIA bolti hai: "Sorry, mujhe [naam] naam ka contact nahi mila".
 */
object ContactCallHandler {

    private const val TAG = "ARIA_CONTACT_CALL_HANDLER"

    /**
     * Checks whether the voice query is requesting a direct phone call (excluding WhatsApp call).
     */
    fun isDirectCallCommand(rawQuery: String): Boolean {
        val q = rawQuery.lowercase().trim()

        // Exclude explicit WhatsApp commands as they are handled by WhatsAppContactResolver
        if (q.contains("whatsapp") || q.contains("what's app") || q.contains("whats app")) {
            return false
        }

        return q.startsWith("call ") ||
                q.contains("call karo") ||
                q.contains("ko call") ||
                q.contains("phone call") ||
                q.contains("dial ") ||
                q.contains("call to ")
    }

    /**
     * Processes direct phone call voice command.
     */
    fun processCallCommand(context: Context, rawQuery: String): ContactCallResult {
        // 1. Extract contact name or phone number string
        val extractedNameOrNumber = extractContactNameOrNumber(rawQuery)

        if (extractedNameOrNumber.isBlank()) {
            val failMsg = "Boss, kisko call karna hai? Naam batayein."
            return ContactCallResult.Failure(failMsg)
        }

        Log.d(TAG, "Extracted Target for Calling: '$extractedNameOrNumber'")

        // 2. Check if user provided direct phone digits (e.g., "call 9876543210")
        val isDirectDigits = extractedNameOrNumber.replace(Regex("[^0-9+]"), "").length >= 7
        val phoneNumberToCall: String
        val displayNameToSpeak: String

        if (isDirectDigits) {
            phoneNumberToCall = extractedNameOrNumber.replace(Regex("[^0-9+]"), "")
            displayNameToSpeak = phoneNumberToCall
        } else {
            // 3. Search contact in Android Device Contacts Provider
            val contactMatch = WhatsAppContactResolver.findContactByName(context, extractedNameOrNumber)

            if (contactMatch == null) {
                // Requested failure message as specified in requirement 5
                val notFoundMsg = "Sorry, mujhe $extractedNameOrNumber naam ka contact nahi mila"
                Log.d(TAG, "Contact not found for query '$extractedNameOrNumber'")
                return ContactCallResult.Failure(notFoundMsg)
            }

            phoneNumberToCall = contactMatch.phoneNumber
            displayNameToSpeak = contactMatch.displayName
        }

        // 4. Check CALL_PHONE runtime permission and initiate call
        val hasCallPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val cleanUri = Uri.parse("tel:${Uri.encode(phoneNumberToCall)}")

        if (hasCallPermission) {
            return try {
                val callIntent = Intent(Intent.ACTION_CALL, cleanUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                val successMsg = "Calling $displayNameToSpeak, Boss!"
                ContactCallResult.Success(successMsg, phoneNumberToCall)
            } catch (e: Exception) {
                Log.e(TAG, "Direct call failed, falling back to dialer", e)
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, cleanUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    ContactCallResult.Success("Opening dialer for $displayNameToSpeak, Boss!", phoneNumberToCall)
                } catch (ex: Exception) {
                    ContactCallResult.Failure("Sorry, call initiate nahi ho pa raha hai.")
                }
            }
        } else {
            // Permission denied feedback and fallback to dialer
            return try {
                val dialIntent = Intent(Intent.ACTION_DIAL, cleanUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                val permMsg = "Boss, direct auto-calling ke liye Phone Call permission zaroori hai. Maine $displayNameToSpeak ka number dialer par open kar diya hai."
                ContactCallResult.Success(permMsg, phoneNumberToCall)
            } catch (ex: Exception) {
                ContactCallResult.Failure("Phone Call permission permission missing hai aur dialer bhi launch nahi ho saka.")
            }
        }
    }

    /**
     * Extracts contact name or phone number from natural voice queries.
     * e.g., "call Rahul", "call Rahul Sharma", "Rahul ko call karo", "call 9876543210"
     */
    private fun extractContactNameOrNumber(rawQuery: String): String {
        var clean = rawQuery
            .replace("open phone and call", "", ignoreCase = true)
            .replace("make a call to", "", ignoreCase = true)
            .replace("phone call to", "", ignoreCase = true)
            .replace("phone call karo", "", ignoreCase = true)
            .replace("call karo", "", ignoreCase = true)
            .replace("call to", "", ignoreCase = true)
            .replace("call this person", "", ignoreCase = true)
            .replace("call this number", "", ignoreCase = true)
            .replace("call this contact", "", ignoreCase = true)
            .replace("call", "", ignoreCase = true)
            .replace("dial", "", ignoreCase = true)
            .replace("ko call", "", ignoreCase = true)
            .replace("ko", "", ignoreCase = true)
            .replace("please", "", ignoreCase = true)
            .trim()

        val relationWords = listOf(
            "friend", "friends", "bhaiya", "bhai", "sir", "dost", "bro", "ji",
            "uncle", "aunty", "colleague", "boss", "companion", "relative", "mate", "partner"
        )

        val tokens = clean.split("\\s+".toRegex()).filter { token ->
            token.isNotBlank() && !relationWords.contains(token.lowercase())
        }

        return tokens.joinToString(" ").trim()
    }
}

/** Result Model for Contact Calling */
sealed class ContactCallResult {
    data class Success(val message: String, val phoneNumber: String) : ContactCallResult()
    data class Failure(val message: String) : ContactCallResult()
}
