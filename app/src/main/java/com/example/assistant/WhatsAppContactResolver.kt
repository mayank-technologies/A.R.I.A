package com.example.assistant

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import java.util.Locale

data class ContactMatch(
    val displayName: String,
    val phoneNumber: String,
    val rawNumber: String
)

sealed class MatchResult {
    data class SingleMatch(val contact: ContactMatch) : MatchResult()
    data class MultipleMatches(val contacts: List<ContactMatch>) : MatchResult()
    object NoMatch : MatchResult()
}

sealed class WhatsAppActionResult {
    data class Success(val message: String, val openUrl: String? = null) : WhatsAppActionResult()
    data class CallAdvice(val message: String, val openUrl: String) : WhatsAppActionResult()
    data class Disambiguation(val requestedName: String, val message: String, val matches: List<ContactMatch>) : WhatsAppActionResult()
    data class ContactNotFound(val requestedName: String, val message: String, val suggestions: List<String> = emptyList()) : WhatsAppActionResult()
    data class GeneralFailure(val message: String) : WhatsAppActionResult()
}

object WhatsAppContactResolver {

    private const val TAG = "ARIA_WHATSAPP_RESOLVER"

    /**
     * Parses and executes WhatsApp voice intent commands such as:
     * - "open whatsapp and message to chandan friend"
     * - "WhatsApp message Rahul hello how are you"
     * - "open whatsapp and go to Rahul and call"
     * - "WhatsApp video call karo Priya ko"
     * - "Send message to Rahul on WhatsApp"
     */
    fun processWhatsAppCommand(context: Context, rawQuery: String): WhatsAppActionResult? {
        val queryLower = rawQuery.lowercase(Locale.ROOT).trim()

        // Check if query pertains to WhatsApp or direct messaging/calling intent
        val isWhatsAppIntent = queryLower.contains("whatsapp") ||
                queryLower.contains("what's app") ||
                queryLower.contains("whats app") ||
                queryLower.contains("wa.me") ||
                (queryLower.contains("message") && (queryLower.contains("karo") || queryLower.contains("send"))) ||
                (queryLower.contains("call") && queryLower.contains("video"))

        if (!isWhatsAppIntent) return null

        // Detect action type
        val isVideoCall = queryLower.contains("video call") || queryLower.contains("video")
        val isCall = queryLower.contains("call") || queryLower.contains("dial") || isVideoCall
        val isMessage = queryLower.contains("message") || queryLower.contains("chat") || queryLower.contains("send") || queryLower.contains("bhejo") || queryLower.contains("text")

        val actionType = when {
            isVideoCall -> "VIDEO_CALL"
            isCall -> "AUDIO_CALL"
            else -> "MESSAGE"
        }

        // 1. Improved Command Parser: Extracts target contact name and message body
        val (contactName, messageText) = extractContactAndMessage(rawQuery)

        if (contactName.isBlank()) {
            // No specific contact name detected, open default WhatsApp
            SmartAppOpener.smartLaunchApp(context, "whatsapp")
            return WhatsAppActionResult.Success("WhatsApp app khol rahi hu, Boss!")
        }

        Log.d(TAG, "==========================================================")
        Log.d(TAG, "[PARSED VOICE COMMAND] Action: $actionType | Raw Target: '$contactName' | Msg: '$messageText'")
        println("[ARIA WHATSAPP LOG] Parsed Target Contact: '$contactName' | Action: $actionType")

        // 2. Fetch all contacts from Android device ContactsProvider (with console debug logs)
        val allContacts = getAllContacts(context)

        // 3. Perform Fuzzy Matching with String Similarity (Threshold 0.60) & Disambiguation Check
        val matchResult = findBestContactMatches(allContacts, contactName)

        return when (matchResult) {
            is MatchResult.NoMatch -> {
                val suggestions = getSimilarContactNames(allContacts, contactName)
                val suggestionText = if (suggestions.isNotEmpty()) {
                    " Kya aapka matlab hai: ${suggestions.joinToString(", ")}?"
                } else ""
                val failMsg = "Sorry Boss, mujhe '$contactName' naam ka contact aapki phonebook me nahi mila.$suggestionText"
                Log.e(TAG, "[MATCH FAILED] No contact match found for: '$contactName'")
                println("[ARIA WHATSAPP LOG] Match Failed for target: '$contactName'")
                WhatsAppActionResult.ContactNotFound(contactName, failMsg, suggestions)
            }

            is MatchResult.MultipleMatches -> {
                // Disambiguation Logic: Ask user which specific contact to message/call
                val optionsText = matchResult.contacts.map { it.displayName }.joinToString(" ya ")
                val disambiguationMsg = "Kaunsa $contactName — $optionsText?"
                Log.d(TAG, "[DISAMBIGUATION REQUIRED] Multiple candidates found: ${matchResult.contacts.map { it.displayName }}")
                println("[ARIA WHATSAPP DISAMBIGUATION] $disambiguationMsg")
                WhatsAppActionResult.Disambiguation(contactName, disambiguationMsg, matchResult.contacts)
            }

            is MatchResult.SingleMatch -> {
                val contactMatch = matchResult.contact
                Log.d(TAG, "[MATCH SUCCESS] Found contact: '${contactMatch.displayName}' (${contactMatch.phoneNumber})")
                println("[ARIA WHATSAPP MATCH] Selected Contact: '${contactMatch.displayName}' -> Phone: ${contactMatch.phoneNumber}")

                val cleanPhone = formatPhoneNumberForWhatsApp(contactMatch.phoneNumber)

                when (actionType) {
                    "MESSAGE" -> {
                        val waUrl = if (messageText.isNotBlank()) {
                            "https://wa.me/$cleanPhone?text=${Uri.encode(messageText)}"
                        } else {
                            "https://wa.me/$cleanPhone"
                        }

                        val launched = launchWhatsAppUri(context, waUrl)
                        if (launched) {
                            val msg = if (messageText.isNotBlank()) {
                                "Maine ${contactMatch.displayName} ko WhatsApp par '$messageText' ka text khol diya hai! Send button par tap karein."
                            } else {
                                "Maine ${contactMatch.displayName} ka WhatsApp chat screen khol diya hai, Boss!"
                            }
                            WhatsAppActionResult.Success(msg, waUrl)
                        } else {
                            WhatsAppActionResult.GeneralFailure("WhatsApp app open nahi ho pa raha hai.")
                        }
                    }

                    "AUDIO_CALL", "VIDEO_CALL" -> {
                        val waChatUrl = "https://wa.me/$cleanPhone"
                        val launched = launchWhatsAppUri(context, waChatUrl)

                        val callTypeLabel = if (isVideoCall) "video call" else "audio call"
                        if (launched) {
                            val msg = "Maine ${contactMatch.displayName} ka WhatsApp chat khol diya hai, Boss! Top-right corner par $callTypeLabel icon par tap kijiye."
                            WhatsAppActionResult.CallAdvice(msg, waChatUrl)
                        } else {
                            WhatsAppActionResult.GeneralFailure("WhatsApp open nahi ho sakha.")
                        }
                    }

                    else -> {
                        WhatsAppActionResult.GeneralFailure("Unknown WhatsApp action.")
                    }
                }
            }
        }
    }

    /**
     * Fetches all contacts from device ContactsProvider and logs them to console/logcat.
     */
    fun getAllContacts(context: Context): List<ContactMatch> {
        val contactsList = mutableListOf<ContactMatch>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.let { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (c.moveToNext()) {
                    val name = if (nameIdx != -1) c.getString(nameIdx) else null
                    val number = if (numberIdx != -1) c.getString(numberIdx) else null

                    if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                        contactsList.add(ContactMatch(name.trim(), number.trim(), number.trim()))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[CONTACTS PERMISSION/FETCH ERROR] Error querying contacts provider: ${e.message}", e)
            println("[ARIA CONTACT ERROR] Unable to query ContactsProvider: ${e.message}")
        } finally {
            cursor?.close()
        }

        // Console log print statement for developer verification
        Log.d(TAG, "==========================================================")
        Log.d(TAG, "[DEVICE CONTACTS FETCHED] Total count: ${contactsList.size}")
        println("[ARIA CONTACT LOG] Total Contacts Retrieved from Phone: ${contactsList.size}")
        contactsList.forEachIndexed { idx, contact ->
            Log.d(TAG, "[CONTACT #$idx] Name: '${contact.displayName}' | Phone: '${contact.phoneNumber}'")
            println("[ARIA CONTACT LOG] #$idx -> '${contact.displayName}' (${contact.phoneNumber})")
        }
        Log.d(TAG, "==========================================================")

        return contactsList
    }

    /**
     * Single contact match helper (calls resolve function) for backward compatibility with CallHandler.
     */
    fun findContactByName(context: Context, targetName: String): ContactMatch? {
        val allContacts = getAllContacts(context)
        return when (val res = findBestContactMatches(allContacts, targetName)) {
            is MatchResult.SingleMatch -> res.contact
            is MatchResult.MultipleMatches -> res.contacts.firstOrNull()
            is MatchResult.NoMatch -> null
        }
    }

    /**
     * Performs multi-field, token-based, and fuzzy Levenshtein similarity matching with ~0.6 threshold.
     */
    fun findBestContactMatches(contacts: List<ContactMatch>, targetName: String, threshold: Double = 0.60): MatchResult {
        val cleanTarget = targetName.lowercase(Locale.ROOT).trim()
        if (cleanTarget.isBlank() || contacts.isEmpty()) return MatchResult.NoMatch

        // 1. Check exact full display name match
        val exactFullMatches = contacts.filter { it.displayName.lowercase(Locale.ROOT) == cleanTarget }
        if (exactFullMatches.size == 1) return MatchResult.SingleMatch(exactFullMatches.first())
        if (exactFullMatches.size > 1) return MatchResult.MultipleMatches(exactFullMatches.distinctBy { it.displayName })

        // 2. Score candidates using token matching, prefix/substring matching & Levenshtein similarity
        val candidateScoreMap = mutableMapOf<ContactMatch, Double>()

        for (contact in contacts) {
            val fullNameLower = contact.displayName.lowercase(Locale.ROOT).trim()
            val tokens = fullNameLower.split("\\s+".toRegex()).filter { it.isNotBlank() }

            // Exact token match (e.g. target "chandan" matches first token "chandan" in "Chandan Kumar")
            if (tokens.any { it == cleanTarget }) {
                candidateScoreMap[contact] = 1.0
                continue
            }

            // Prefix token match (e.g. "chandan" matches "chandankumar")
            if (tokens.any { it.startsWith(cleanTarget) || cleanTarget.startsWith(it) }) {
                candidateScoreMap[contact] = 0.90
                continue
            }

            // Full string contains match
            if (fullNameLower.contains(cleanTarget) || cleanTarget.contains(fullNameLower)) {
                candidateScoreMap[contact] = 0.85
                continue
            }

            // Fuzzy Levenshtein similarity score calculation (Threshold >= 0.60)
            var bestSim = calculateLevenshteinSimilarity(cleanTarget, fullNameLower)
            for (token in tokens) {
                val tokenSim = calculateLevenshteinSimilarity(cleanTarget, token)
                if (tokenSim > bestSim) {
                    bestSim = tokenSim
                }
            }

            if (bestSim >= threshold) {
                candidateScoreMap[contact] = bestSim
            }
        }

        if (candidateScoreMap.isEmpty()) return MatchResult.NoMatch

        // Sort candidates by highest score descending
        val sortedCandidates = candidateScoreMap.entries.sortedByDescending { it.value }
        val topScore = sortedCandidates.first().value

        // Group top candidates within 0.10 score band
        val topMatches = sortedCandidates
            .filter { it.value >= topScore - 0.10 }
            .map { it.key }
            .distinctBy { it.displayName.lowercase(Locale.ROOT) }

        return when {
            topMatches.size == 1 -> MatchResult.SingleMatch(topMatches.first())
            topMatches.size > 1 -> MatchResult.MultipleMatches(topMatches)
            else -> MatchResult.NoMatch
        }
    }

    /**
     * Calculates Normalized Levenshtein Similarity between two strings (0.0 to 1.0).
     */
    fun calculateLevenshteinSimilarity(s1: String, s2: String): Double {
        val str1 = s1.lowercase(Locale.ROOT).trim()
        val str2 = s2.lowercase(Locale.ROOT).trim()
        if (str1 == str2) return 1.0
        if (str1.isEmpty() || str2.isEmpty()) return 0.0

        val maxLen = maxOf(str1.length, str2.length)
        val distance = computeLevenshteinDistance(str1, str2)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    private fun computeLevenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length

        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1) { 0 }

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costReplace, minOf(costInsert, costDelete))
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    /**
     * Improved Voice Command Parser: Extracts target contact name & message body.
     * Cleans action keywords ("open whatsapp and message to", "send message to", "to", "ko")
     * and filters out relation/descriptor words ("friend", "bhai", "sir", "dost", "bro", "ji", etc.).
     */
    private fun extractContactAndMessage(query: String): Pair<String, String> {
        var clean = query.lowercase(Locale.ROOT).trim()

        var messageText = ""
        // Extract message body if user provided text after keywords like "saying", "that", "message", "text", "bolna", "bolo"
        val msgKeywords = listOf(" saying ", " that ", " message ", " text ", " bolna ", " bolo ")
        for (kw in msgKeywords) {
            val idx = clean.indexOf(kw)
            if (idx != -1) {
                messageText = clean.substring(idx + kw.length).trim()
                clean = clean.substring(0, idx).trim()
                break
            }
        }

        // 1. Action phrases to strip
        val actionPhrases = listOf(
            "open whatsapp and send message to",
            "open whatsapp and message to",
            "open whatsapp and message",
            "open whatsapp and go to",
            "open whatsapp and call",
            "open whatsapp",
            "whatsapp par message karo",
            "whatsapp message to",
            "whatsapp message",
            "whatsapp call to",
            "whatsapp call",
            "whatsapp video call",
            "whatsapp chat for",
            "whatsapp chat",
            "whatsapp par",
            "whatsapp",
            "what's app",
            "whats app",
            "send message to",
            "send message",
            "message to",
            "message karo",
            "video call karo",
            "call karo",
            "call to",
            "go to",
            "ko message karo",
            "ko message",
            "ko call karo",
            "ko call",
            "and video call",
            "and call",
            "and go to",
            "please",
            "ko",
            "to"
        )

        for (phrase in actionPhrases) {
            clean = clean.replace(phrase, " ")
        }

        // 2. Relation / Descriptor suffix/prefix words to filter out ("friend", "bhai", "sir", "dost", "bro", "ji", "uncle", "aunty")
        val relationWords = listOf(
            "friend", "friends", "bhaiya", "bhai", "sir", "dost", "bro", "ji",
            "uncle", "aunty", "colleague", "boss", "companion", "relative", "mate", "partner"
        )

        val nameTokens = clean.split("\\s+".toRegex()).filter { token ->
            token.isNotBlank() && !relationWords.contains(token)
        }

        val contactName = nameTokens.joinToString(" ").trim()

        return Pair(contactName, messageText)
    }

    private fun getSimilarContactNames(contacts: List<ContactMatch>, targetName: String): List<String> {
        val cleanTarget = targetName.lowercase(Locale.ROOT).trim()
        val matches = mutableListOf<String>()

        for (contact in contacts) {
            val sim = calculateLevenshteinSimilarity(cleanTarget, contact.displayName)
            if (sim >= 0.40) {
                matches.add(contact.displayName)
            }
        }

        return matches.distinct().take(3)
    }

    /**
     * Formats raw phone numbers for WhatsApp deep link format (e.g., +91 98765-43210 -> 919876543210).
     */
    fun formatPhoneNumberForWhatsApp(rawPhone: String): String {
        var digits = rawPhone.replace(Regex("[^0-9]"), "")

        if (digits.length == 10) {
            digits = "91$digits"
        } else if (digits.length == 11 && digits.startsWith("0")) {
            digits = "91${digits.substring(1)}"
        }

        return digits
    }

    private fun launchWhatsAppUri(context: Context, uriString: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch WhatsApp URI: $uriString", ex)
                false
            }
        }
    }
}

