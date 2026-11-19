package com.example.assistant.accessibility

import android.content.Context
import android.text.InputType

/**
 * User's saved personal profile fields used for voice-triggered Forms Auto-Fill ("Form fill karo").
 */
data class PersonalProfileInfo(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = ""
) {
    fun hasAnyField(): Boolean {
        return fullName.isNotBlank() || email.isNotBlank() || phone.isNotBlank() || address.isNotBlank()
    }
}

/**
 * Supported form field classifications for Accessibility auto-fill.
 * Any sensitive or ambiguous field is explicitly marked so ARC skips it safely.
 */
enum class FormFieldType {
    NAME,
    EMAIL,
    PHONE,
    ADDRESS,
    PASSWORD_OR_SENSITIVE,
    UNKNOWN
}

data class FormFillResult(
    val success: Boolean,
    val filledFields: List<String> = emptyList(),
    val skippedCount: Int = 0,
    val message: String
)

/**
 * Persists Personal Info (Name, Email, Phone, Address) in SharedPreferences
 * and provides safe, conservative form field classification for Forms Auto-Fill.
 */
object PersonalProfileManager {

    private const val PREFS_NAME = "aria_prefs"
    private const val KEY_FULL_NAME = "profile_full_name"
    private const val KEY_EMAIL = "profile_email"
    private const val KEY_PHONE = "profile_phone"
    private const val KEY_ADDRESS = "profile_address"

    fun getProfile(context: Context): PersonalProfileInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fallbackName = prefs.getString("user_name", "").orEmpty()
        return PersonalProfileInfo(
            fullName = prefs.getString(KEY_FULL_NAME, "")?.takeIf { it.isNotBlank() } ?: fallbackName,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            phone = prefs.getString(KEY_PHONE, "").orEmpty(),
            address = prefs.getString(KEY_ADDRESS, "").orEmpty()
        )
    }

    fun saveProfile(context: Context, profile: PersonalProfileInfo) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FULL_NAME, profile.fullName.trim())
            .putString(KEY_EMAIL, profile.email.trim())
            .putString(KEY_PHONE, profile.phone.trim())
            .putString(KEY_ADDRESS, profile.address.trim())
            .apply()
    }

    /**
     * Classifies an editable AccessibilityNode using hint, contentDescription, viewIdResourceName,
     * surrounding label, inputType, and password flag.
     * Ambiguous or sensitive fields (password, OTP, PIN, CVV, card, search, comment) return
     * PASSWORD_OR_SENSITIVE / UNKNOWN so ARC never force-fills them.
     */
    fun classifyFormField(
        hintText: String?,
        contentDesc: String?,
        viewIdName: String?,
        nearbyLabel: String? = null,
        inputType: Int = InputType.TYPE_NULL,
        isPassword: Boolean = false
    ): FormFieldType {
        if (isPassword) return FormFieldType.PASSWORD_OR_SENSITIVE

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        ) {
            return FormFieldType.PASSWORD_OR_SENSITIVE
        }
        if (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return FormFieldType.PASSWORD_OR_SENSITIVE
        }

        val combined = listOfNotNull(hintText, contentDesc, viewIdName, nearbyLabel)
            .joinToString(" ")
            .lowercase()
            .trim()

        // Block sensitive & non-personal generic fields strictly
        val blockedTokens = listOf(
            "password", "passcode", "otp", "pin", "cvv", "card", "credit", "debit",
            "account", "ifsc", "aadhaar", "pan", "ssn", "search", "query", "comment",
            "message", "amount", "price", "promo", "coupon", "username", "user_name", "login id"
        )
        if (blockedTokens.any { combined.contains(it) }) {
            return if (combined.contains("password") || combined.contains("otp") ||
                combined.contains("pin") || combined.contains("cvv") || combined.contains("card")
            ) {
                FormFieldType.PASSWORD_OR_SENSITIVE
            } else {
                FormFieldType.UNKNOWN
            }
        }

        val emailMatch = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            combined.contains("email") || combined.contains("e-mail") || combined.contains("mail id")

        val phoneMatch = inputClass == InputType.TYPE_CLASS_PHONE ||
            combined.contains("phone") || combined.contains("mobile") ||
            combined.contains("contact no") || combined.contains("contact number") ||
            Regex("\\btel\\b").containsMatchIn(combined)

        val addressMatch = variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS ||
            combined.contains("address") || combined.contains("street") ||
            combined.contains("locality") || combined.contains("pincode") ||
            combined.contains("postal") || combined.contains("zip code")

        val nameMatch = combined.contains("full name") || combined.contains("your name") ||
            combined.contains("first name") || combined.contains("applicant name") ||
            combined.contains("customer name") || combined.contains("naam") ||
            Regex("(^|[_\\s-])name($|[_\\s-])").containsMatchIn(combined)

        val matchCount = listOf(emailMatch, phoneMatch, addressMatch, nameMatch).count { it }
        // If multiple categories collide on one ambiguous label, skip cleanly instead of guessing
        if (matchCount != 1) return FormFieldType.UNKNOWN

        return when {
            emailMatch -> FormFieldType.EMAIL
            phoneMatch -> FormFieldType.PHONE
            addressMatch -> FormFieldType.ADDRESS
            nameMatch -> FormFieldType.NAME
            else -> FormFieldType.UNKNOWN
        }
    }

    fun valueForField(profile: PersonalProfileInfo, fieldType: FormFieldType): String {
        return when (fieldType) {
            FormFieldType.NAME -> profile.fullName.trim()
            FormFieldType.EMAIL -> profile.email.trim()
            FormFieldType.PHONE -> profile.phone.trim()
            FormFieldType.ADDRESS -> profile.address.trim()
            FormFieldType.PASSWORD_OR_SENSITIVE, FormFieldType.UNKNOWN -> ""
        }
    }
}
