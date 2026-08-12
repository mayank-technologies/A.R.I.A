package com.example.assistant

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

sealed class ContactsActionResult {
    data class Success(val message: String) : ContactsActionResult()
    data class Failure(val message: String) : ContactsActionResult()
}

object GoogleContactsVoiceHandler {

    private const val TAG = "GoogleContactsVoiceHandler"

    fun isContactsCommand(queryLower: String): Boolean {
        return queryLower.contains("contact") ||
                queryLower.contains("contacts") ||
                queryLower.contains("phonebook") ||
                queryLower.contains("number dikhao") ||
                queryLower.contains("number dhoondo") ||
                queryLower.contains("search contact") ||
                queryLower.contains("find contact")
    }

    fun processContactsCommand(context: Context, rawQuery: String): ContactsActionResult {
        val q = rawQuery.lowercase().trim()
        Log.d(TAG, "Processing Contacts command: $q")

        return try {
            val contactName = extractContactName(rawQuery)

            if (contactName.isNotBlank() && contactName != "contacts" && contactName != "contact") {
                val foundNumber = searchContactInDevice(context, contactName)
                if (foundNumber != null) {
                    ContactsActionResult.Success("Contact '$contactName' found: $foundNumber, Boss.")
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ContactsActionResult.Success("Contact '$contactName' not found directly. Opening Google Contacts app, Boss.")
                }
            } else {
                val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ContactsActionResult.Success("Opening Google Contacts, Boss.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contacts command error: ${e.message}", e)
            ContactsActionResult.Failure("Failed to access Contacts: ${e.localizedMessage}")
        }
    }

    private fun searchContactInDevice(context: Context, name: String): String? {
        val contentResolver: ContentResolver = context.contentResolver
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex >= 0) {
                    return it.getString(numberIndex)
                }
            }
        }
        return null
    }

    private fun extractContactName(query: String): String {
        return query.replace("find contact", "", ignoreCase = true)
            .replace("search contact", "", ignoreCase = true)
            .replace("contact of", "", ignoreCase = true)
            .replace("number of", "", ignoreCase = true)
            .replace("number dikhao", "", ignoreCase = true)
            .replace("contacts", "", ignoreCase = true)
            .replace("contact", "", ignoreCase = true)
            .trim()
    }
}
