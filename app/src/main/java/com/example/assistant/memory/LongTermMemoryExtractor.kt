package com.example.assistant.memory

/**
 * Candidate user fact extracted from conversational turns.
 */
data class MemoryCandidate(
    val factKey: String,
    val factValue: String,
    val category: String,
    val isSensitive: Boolean = false,
    val sensitiveReason: String = ""
)

/**
 * Result of analyzing a user transcript for memorable preferences/routines and sensitive data guards.
 */
data class MemoryExtractionResult(
    val safeFactsToAutoSave: List<MemoryCandidate> = emptyList(),
    val pendingSensitiveConfirmation: MemoryCandidate? = null,
    val isExplicitRememberRequest: Boolean = false
)

/**
 * LongTermMemoryExtractor:
 * Extracts durable user preferences and routines from Hindi/Hinglish/English utterances
 * while blocking auto-storage of sensitive secrets (passwords, PINs, OTPs, bank/card/UPI/ID numbers)
 * unless the user explicitly confirms saving them.
 */
object LongTermMemoryExtractor {

    private val sensitiveKeywords = listOf(
        "password", "passcode", "pin", "otp", "cvv", "atm", "debit card", "credit card",
        "card number", "bank account", "account number", "ifsc", "upi pin", "mpin",
        "aadhaar", "aadhar", "pan card", "pan number", "ssn", "secret key", "api key",
        "private key", "seed phrase", "recovery phrase", "medical report", "diagnosis"
    )

    private val cardOrAadhaarPattern = Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?:[\\s-]?\\d{4})?\\b")
    private val panPattern = Regex("\\b[A-Z]{5}\\d{4}[A-Z]\\b")
    private val pinOtpPattern = Regex("(?i)\\b(pin|otp|cvv|passcode|password)\\s*(is|hai|:|=)?\\s*[\\w@#\$%^&*!-]{3,}")

    fun checkSensitivity(text: String): Pair<Boolean, String> {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        val matchedKeyword = sensitiveKeywords.firstOrNull { lower.contains(it) }
        if (matchedKeyword != null) {
            return true to "Contains potentially sensitive term ('$matchedKeyword')"
        }
        if (pinOtpPattern.containsMatchIn(trimmed)) {
            return true to "Contains PIN/OTP/password credential pattern"
        }
        if (cardOrAadhaarPattern.containsMatchIn(trimmed)) {
            return true to "Contains 12-16 digit card/ID number pattern"
        }
        if (panPattern.containsMatchIn(trimmed)) {
            return true to "Contains PAN identifier pattern"
        }
        return false to ""
    }

    fun analyzeTranscript(userText: String): MemoryExtractionResult {
        val trimmed = userText.trim()
        if (trimmed.length < 4) return MemoryExtractionResult()
        val lower = trimmed.lowercase()

        val isExplicitRemember = lower.startsWith("remember ") ||
            lower.startsWith("yaad rakho") ||
            lower.startsWith("yaad rakhna") ||
            lower.contains("save in memory") ||
            lower.contains("memory me save") ||
            lower.contains("note kar lo")

        val (isSensitive, sensitiveReason) = checkSensitivity(trimmed)

        val candidates = mutableListOf<MemoryCandidate>()

        // 1. Explicit "Remember that ..." / "Yaad rakho ..."
        if (isExplicitRemember) {
            val cleaned = trimmed
                .replace(Regex("(?i)^(remember that|remember|yaad rakho ki|yaad rakho|yaad rakhna ki|yaad rakhna|save in memory|memory me save karo|note kar lo)\\s*"), "")
                .trim()
            if (cleaned.length >= 3) {
                candidates.add(
                    MemoryCandidate(
                        factKey = deriveKeyFromStatement(cleaned),
                        factValue = cleaned,
                        category = "Personal Note",
                        isSensitive = isSensitive,
                        sensitiveReason = sensitiveReason
                    )
                )
            }
        }

        // 2. Hinglish & English preference patterns ("mujhe chai pasand hai", "I like coffee")
        val hinglishLikeRegex = Regex("(?i)\\b(?:mujhe|mjhe|mujhko|mereko)\\s+(.+?)\\s+(pasand hai|bahut pasand hai|accha lagta hai|acchi lagti hai)\\b")
        hinglishLikeRegex.find(trimmed)?.let { match ->
            val likedItem = match.groupValues[1].trim()
            if (likedItem.length in 2..60) {
                candidates.add(
                    MemoryCandidate(
                        factKey = "Likes / Preference",
                        factValue = likedItem,
                        category = "Preference",
                        isSensitive = isSensitive,
                        sensitiveReason = sensitiveReason
                    )
                )
            }
        }

        val englishLikeRegex = Regex("(?i)\\b(?:i really like|i like|i love|i prefer)\\s+([^.,!?]{2,50})")
        englishLikeRegex.find(trimmed)?.let { match ->
            val likedItem = match.groupValues[1].trim()
            if (!likedItem.lowercase().startsWith("to ") || likedItem.length > 5) {
                candidates.add(
                    MemoryCandidate(
                        factKey = "Favorite / Like",
                        factValue = likedItem,
                        category = "Preference",
                        isSensitive = isSensitive,
                        sensitiveReason = sensitiveReason
                    )
                )
            }
        }

        val favoriteRegex = Regex("(?i)\\b(?:my favorite|mera favorite|meri favorite)\\s+([a-zA-Z ]{2,25})\\s+(?:is|hai)\\s+([^.,!?]{2,45})")
        favoriteRegex.find(trimmed)?.let { match ->
            val topic = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val value = match.groupValues[2].trim()
            candidates.add(
                MemoryCandidate(
                    factKey = "Favorite $topic",
                    factValue = value,
                    category = "Preference",
                    isSensitive = isSensitive,
                    sensitiveReason = sensitiveReason
                )
            )
        }

        // 3. Routine & Schedule patterns ("mera office 9 baje start hota hai", "my office starts at 9 AM")
        val officeScheduleHinglish = Regex("(?i)\\b(?:mera|meri)\\s+(office|school|college|gym|class|shift)\\s+(.{2,35}?)\\s+(start hota hai|shuru hota hai|khulta hai|hota hai)\\b")
        officeScheduleHinglish.find(trimmed)?.let { match ->
            val place = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val timing = match.groupValues[2].trim()
            candidates.add(
                MemoryCandidate(
                    factKey = "$place Schedule",
                    factValue = "$place starts $timing",
                    category = "Routine",
                    isSensitive = isSensitive,
                    sensitiveReason = sensitiveReason
                )
            )
        }

        val scheduleEnglish = Regex("(?i)\\b(?:my)\\s+(office|work|school|college|gym|shift)\\s+(?:starts at|begins at|is at)\\s+([^.,!?]{2,30})")
        scheduleEnglish.find(trimmed)?.let { match ->
            val place = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val timing = match.groupValues[2].trim()
            candidates.add(
                MemoryCandidate(
                    factKey = "$place Schedule",
                    factValue = "$place starts at $timing",
                    category = "Routine",
                    isSensitive = isSensitive,
                    sensitiveReason = sensitiveReason
                )
            )
        }

        // 4. City / Residence patterns ("mai Delhi me rehta hu", "I live in Mumbai")
        val liveRegex = Regex("(?i)\\b(?:i live in|mai|main|hum)\\s+([a-zA-Z ]{2,30})\\s*(?:me rehta hu|me rehti hu|mein rehta hu|mein rehti hu)?$")
        if (lower.contains("live in ") || lower.contains("me rehta") || lower.contains("me rehti")) {
            val cityMatch = Regex("(?i)(?:live in\\s+|mai\\s+|main\\s+)([a-zA-Z ]{2,25})(?:\\s+me\\s+rehta|\\s+me\\s+rehti|$)")
                .find(trimmed)
            cityMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }?.let { city ->
                candidates.add(
                    MemoryCandidate(
                        factKey = "Home City",
                        factValue = city,
                        category = "Profile",
                        isSensitive = isSensitive,
                        sensitiveReason = sensitiveReason
                    )
                )
            }
        }

        val distinctCandidates = candidates.distinctBy { "${it.factKey.lowercase()}|${it.factValue.lowercase()}" }

        // Privacy Guard: Never auto-save any sensitive fact without explicit user confirmation
        if (isSensitive) {
            val pending = distinctCandidates.firstOrNull() ?: if (isExplicitRemember) {
                MemoryCandidate(
                    factKey = "Sensitive Note",
                    factValue = trimmed,
                    category = "Sensitive",
                    isSensitive = true,
                    sensitiveReason = sensitiveReason
                )
            } else {
                null
            }
            return MemoryExtractionResult(
                safeFactsToAutoSave = emptyList(),
                pendingSensitiveConfirmation = pending,
                isExplicitRememberRequest = isExplicitRemember
            )
        }

        return MemoryExtractionResult(
            safeFactsToAutoSave = distinctCandidates,
            pendingSensitiveConfirmation = null,
            isExplicitRememberRequest = isExplicitRemember
        )
    }

    private fun deriveKeyFromStatement(statement: String): String {
        val lower = statement.lowercase()
        return when {
            lower.contains("chai") || lower.contains("coffee") || lower.contains("food") || lower.contains("eat") -> "Food / Drink"
            lower.contains("office") || lower.contains("work") || lower.contains("gym") || lower.contains("wake") -> "Daily Routine"
            lower.contains("birthday") || lower.contains("anniversary") -> "Important Date"
            lower.contains("allergy") || lower.contains("color") -> "Personal Preference"
            else -> "Remembered Fact"
        }
    }
}
