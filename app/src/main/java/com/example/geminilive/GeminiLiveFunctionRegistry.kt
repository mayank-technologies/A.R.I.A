package com.example.geminilive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.assistant.ContactCallHandler
import com.example.assistant.ContactCallResult
import com.example.assistant.DeviceClockHelper
import com.example.assistant.SmartAppOpener
import com.example.assistant.WhatsAppActionResult
import com.example.assistant.WhatsAppContactResolver
import org.json.JSONArray
import org.json.JSONObject

/**
 * Result returned by an executed tool back to the Gemini Live session.
 */
data class ToolExecutionOutput(
    val callId: String,
    val toolName: String,
    val success: Boolean,
    val responseData: JSONObject,
    val verbalConfirmation: String
)

/**
 * GeminiLiveFunctionRegistry:
 * Defines strict JSON schemas for ARC tools and executes them safely on the Android device.
 * 
 * Predefined Whitelisted Functions:
 * 1. openWhatsApp(recipientName, messageText, isVideoCall)
 * 2. openApp(appName)
 * 3. openUrl(url)
 * 4. makeCall(phoneNumber, recipientName)
 * 5. callContact(contactName)
 * 
 * Rules:
 * - NO arbitrary or generated code execution.
 * - If action fails, reports exact failure reason so ARC never lies ("ho gaya" bolke fail nahi karta).
 * - Multi-contact disambiguation support.
 */
object GeminiLiveFunctionRegistry {

    private const val TAG = "GEMINI_LIVE_TOOLS"

    /**
     * Builds the Gemini Tools declaration list formatted for the Gemini Live Multimodal API / generateContent.
     */
    fun getToolsDeclarationJson(): JSONArray {
        val toolsArray = JSONArray()

        // 1. openWhatsApp
        val openWhatsApp = JSONObject().apply {
            put("name", "openWhatsApp")
            put("description", "Opens WhatsApp to send a message or start a chat/call with a contact name or phone number.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("recipientName", "STRING", "Name of the person to contact on WhatsApp.")
                    propsPut("messageText", "STRING", "Optional text message body to send.")
                    propsPut("isVideoCall", "BOOLEAN", "True if requesting a video call, false otherwise.")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("recipientName") })
            }
            put("parameters", params)
        }

        // 2. openApp
        val openApp = JSONObject().apply {
            put("name", "openApp")
            put("description", "Launches an installed mobile application or deep-links into it (e.g., YouTube, Chrome, Flipkart, Spotify, Calculator, Camera).")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("appName", "STRING", "Name of the app to launch (e.g. 'youtube', 'chrome', 'whatsapp', 'flipkart', 'spotify').")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("appName") })
            }
            put("parameters", params)
        }

        // 3. openUrl
        val openUrl = JSONObject().apply {
            put("name", "openUrl")
            put("description", "Opens a web URL or executes a web search in the browser.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("url", "STRING", "Web address to open or search query URL.")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("url") })
            }
            put("parameters", params)
        }

        // 4. makeCall
        val makeCall = JSONObject().apply {
            put("name", "makeCall")
            put("description", "Dials a specific known verified phone number directly using Android Phone Dialer with verbal confirmation.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("phoneNumber", "STRING", "The digits of the telephone number to dial.")
                    propsPut("recipientName", "STRING", "Optional label or name of the person being called.")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("phoneNumber") })
            }
            put("parameters", params)
        }

        // 5. callContact
        val callContact = JSONObject().apply {
            put("name", "callContact")
            put("description", "Searches device address book for contact by name, verbals confirmation, and initiates phone call. Never guesses unclear numbers.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("contactName", "STRING", "Full or partial name of the contact to look up and call (e.g. 'Mom', 'Rahul', 'Papa').")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("contactName") })
            }
            put("parameters", params)
        }

        // 6. getCurrentDateTime
        val getCurrentDateTime = JSONObject().apply {
            put("name", "getCurrentDateTime")
            put("description", "Returns the device's exact current local date, time, and timezone. ALWAYS call this when the user asks what time or date it is.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
            put("parameters", params)
        }

        val functionDeclarations = JSONArray().apply {
            put(openWhatsApp)
            put(openApp)
            put(openUrl)
            put(makeCall)
            put(callContact)
            put(getCurrentDateTime)
        }

        val toolWrapper = JSONObject().apply {
            put("functionDeclarations", functionDeclarations)
        }

        toolsArray.put(toolWrapper)
        return toolsArray
    }

    private fun JSONObject.propsPut(propName: String, type: String, description: String) {
        val obj = JSONObject().apply {
            put("type", type)
            put("description", description)
        }
        this.put(propName, obj)
    }

    /**
     * Executes the incoming tool call on the Android device and returns structured outcome.
     */
    fun executeToolCall(context: Context, callId: String, functionName: String, args: JSONObject): ToolExecutionOutput {
        Log.d(TAG, "Executing Tool: $functionName with args: $args (callId: $callId)")

        return when (functionName) {
            "openWhatsApp" -> executeOpenWhatsApp(context, callId, args)
            "openApp" -> executeOpenApp(context, callId, args)
            "openUrl" -> executeOpenUrl(context, callId, args)
            "makeCall" -> executeMakeCall(context, callId, args)
            "callContact" -> executeCallContact(context, callId, args)
            "getCurrentDateTime" -> executeGetCurrentDateTime(context, callId)
            else -> {
                Log.w(TAG, "Unknown tool call: $functionName")
                ToolExecutionOutput(
                    callId = callId,
                    toolName = functionName,
                    success = false,
                    responseData = JSONObject().apply { put("error", "Unknown function: $functionName") },
                    verbalConfirmation = "Sorry, ye feature available nahi hai."
                )
            }
        }
    }

    private fun executeOpenWhatsApp(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val recipient = args.optString("recipientName", "")
        val message = args.optString("messageText", "")
        val isVideo = args.optBoolean("isVideoCall", false)

        val query = if (isVideo) "WhatsApp video call $recipient" else if (message.isNotBlank()) "WhatsApp message $recipient $message" else "open whatsapp $recipient"
        val waRes = WhatsAppContactResolver.processWhatsAppCommand(context, query)

        return if (waRes != null) {
            when (waRes) {
                is WhatsAppActionResult.Success -> {
                    ToolExecutionOutput(
                        callId = callId,
                        toolName = "openWhatsApp",
                        success = true,
                        responseData = JSONObject().apply { put("status", "opened"); put("recipient", recipient) },
                        verbalConfirmation = waRes.message
                    )
                }
                is WhatsAppActionResult.Disambiguation -> {
                    // Ask user clearly which contact they meant
                    val names = waRes.matches.joinToString(", ") { "${it.displayName} (${it.phoneNumber})" }
                    ToolExecutionOutput(
                        callId = callId,
                        toolName = "openWhatsApp",
                        success = false,
                        responseData = JSONObject().apply {
                            put("status", "ambiguous")
                            put("matches", names)
                        },
                        verbalConfirmation = "${waRes.requestedName} ke multiple contacts mile: kaunsa select karein? $names"
                    )
                }
                is WhatsAppActionResult.ContactNotFound -> {
                    ToolExecutionOutput(
                        callId = callId,
                        toolName = "openWhatsApp",
                        success = false,
                        responseData = JSONObject().apply { put("status", "not_found") },
                        verbalConfirmation = "Contact nahi mila: $recipient."
                    )
                }
                else -> {
                    ToolExecutionOutput(
                        callId = callId,
                        toolName = "openWhatsApp",
                        success = false,
                        responseData = JSONObject().apply { put("status", "failed") },
                        verbalConfirmation = "WhatsApp open nahi ho saka."
                    )
                }
            }
        } else {
            ToolExecutionOutput(
                callId = callId,
                toolName = "openWhatsApp",
                success = false,
                responseData = JSONObject().apply { put("status", "error") },
                verbalConfirmation = "WhatsApp command execute nahi ho saki."
            )
        }
    }

    private fun executeOpenApp(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val appName = args.optString("appName", "")
        val result = SmartAppOpener.processVoiceCommand(context, "open $appName")

        return when (result) {
            is com.example.assistant.AppLaunchResult.NativeAppOpened -> {
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "openApp",
                    success = true,
                    responseData = JSONObject().apply { put("status", "launched"); put("app", appName) },
                    verbalConfirmation = "${result.appName} open kar diya hai, Boss!"
                )
            }
            is com.example.assistant.AppLaunchResult.ExternalBrowserOpened -> {
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "openApp",
                    success = true,
                    responseData = JSONObject().apply { put("status", "browser_fallback"); put("url", result.url) },
                    verbalConfirmation = "${result.title} browser me open kar diya hai."
                )
            }
            is com.example.assistant.AppLaunchResult.SearchExecuted -> {
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "openApp",
                    success = true,
                    responseData = JSONObject().apply { put("status", "searched"); put("query", result.query) },
                    verbalConfirmation = result.message
                )
            }
        }
    }

    private fun executeOpenUrl(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val url = args.optString("url", "")
        return try {
            val formatted = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionOutput(
                callId = callId,
                toolName = "openUrl",
                success = true,
                responseData = JSONObject().apply { put("status", "opened"); put("url", formatted) },
                verbalConfirmation = "Website open kar di hai."
            )
        } catch (e: Exception) {
            ToolExecutionOutput(
                callId = callId,
                toolName = "openUrl",
                success = false,
                responseData = JSONObject().apply { put("error", e.localizedMessage ?: "Failed") },
                verbalConfirmation = "URL open nahi ho paya: galat link hai."
            )
        }
    }

    private fun executeMakeCall(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val phoneNumber = args.optString("phoneNumber", "").replace(Regex("[^0-9+]"), "")
        val recipientName = args.optString("recipientName", phoneNumber)

        if (phoneNumber.length < 3) {
            return ToolExecutionOutput(
                callId = callId,
                toolName = "makeCall",
                success = false,
                responseData = JSONObject().apply { put("error", "invalid_number") },
                verbalConfirmation = "Phone number invalid hai, call nahi lagaya gaya."
            )
        }

        val res = ContactCallHandler.processCallCommand(context, "call $phoneNumber")
        return when (res) {
            is ContactCallResult.Success -> {
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "makeCall",
                    success = true,
                    responseData = JSONObject().apply { put("dialed", phoneNumber); put("recipient", recipientName) },
                    verbalConfirmation = "Calling $recipientName..."
                )
            }
            is ContactCallResult.Failure -> {
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "makeCall",
                    success = false,
                    responseData = JSONObject().apply { put("error", res.message) },
                    verbalConfirmation = res.message
                )
            }
        }
    }

    private fun executeCallContact(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val contactName = args.optString("contactName", "").trim()
        if (contactName.isBlank()) {
            return ToolExecutionOutput(
                callId = callId,
                toolName = "callContact",
                success = false,
                responseData = JSONObject().apply { put("error", "empty_name") },
                verbalConfirmation = "Kisko call karna hai? Naam batayein."
            )
        }

        // Disambiguate if multiple matches exist
        val matches = WhatsAppContactResolver.findAllContactsByName(context, contactName)
        if (matches.size > 1) {
            val listText = matches.take(3).joinToString(" ya ") { "${it.displayName} (${it.phoneNumber.takeLast(4)})" }
            return ToolExecutionOutput(
                callId = callId,
                toolName = "callContact",
                success = false,
                responseData = JSONObject().apply {
                    put("status", "multiple_matches")
                    put("count", matches.size)
                },
                verbalConfirmation = "$contactName ke multiple numbers mile: $listText — kaunsa lagana hai?"
            )
        }

        val res = ContactCallHandler.processCallCommand(context, "call $contactName")
        return when (res) {
            is ContactCallResult.Success -> {
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "callContact",
                    success = true,
                    responseData = JSONObject().apply { put("called", contactName); put("number", res.phoneNumber) },
                    verbalConfirmation = "Calling $contactName..."
                )
            }
            is ContactCallResult.Failure -> {
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "callContact",
                    success = false,
                    responseData = JSONObject().apply { put("error", res.message) },
                    verbalConfirmation = res.message
                )
            }
        }
    }

    private fun executeGetCurrentDateTime(context: Context, callId: String): ToolExecutionOutput {
        val clockInfo = DeviceClockHelper.getCurrentDeviceClockInfo(
            context = context,
            respect24HourSetting = false,
            logDebug = true,
            source = "GeminiLiveTimeTool"
        )
        return ToolExecutionOutput(
            callId = callId,
            toolName = "getCurrentDateTime",
            success = true,
            responseData = JSONObject().apply {
                put("formattedTime", clockInfo.formattedTime)
                put("formattedDate", clockInfo.formattedDate)
                put("timeZoneId", clockInfo.timeZoneId)
                put("isoLocal", clockInfo.isoLocalTimestamp)
            },
            verbalConfirmation = "Boss, abhi ${clockInfo.formattedTime} hua hai (${clockInfo.formattedDate})."
        )
    }
}
