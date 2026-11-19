package com.example.geminilive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.assistant.ContactCallHandler
import com.example.assistant.ContactCallResult
import com.example.assistant.DeviceClockHelper
import com.example.assistant.GoogleMapsVoiceHandler
import com.example.assistant.MapsActionResult
import com.example.assistant.RideApp
import com.example.assistant.RideBookingAssistHandler
import com.example.assistant.RideBookingResult
import com.example.assistant.RideVehicleOption
import com.example.assistant.ShoppingAssistHandler
import com.example.assistant.ShoppingAssistResult
import com.example.assistant.SmartAppOpener
import com.example.assistant.WhatsAppActionResult
import com.example.assistant.WhatsAppContactResolver
import com.example.assistant.accessibility.AriaAccessibilityGestureService
import com.example.assistant.accessibility.PersonalProfileManager
import com.example.assistant.hardware.AndroidHardwareController
import com.example.assistant.hardware.HardwareActionResult
import com.example.assistant.notification.GeneralNotificationManager
import com.example.assistant.security.AntiTheftSirenManager
import com.example.assistant.whatsapp.WhatsAppAutomationManager
import com.example.claude.ClaudeReasoningEngine
import com.example.claude.ClaudeReasoningResult
import com.example.data.AriaDatabase
import com.example.data.MemoryRepository
import kotlinx.coroutines.runBlocking
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

        // 7. controlHardware
        val controlHardware = JSONObject().apply {
            put("name", "controlHardware")
            put("description", "Controls device hardware: flashlight, wifi, bluetooth, volume, brightness, or reads battery percentage.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("action", "STRING", "One of: flashlight_on, flashlight_off, wifi_toggle, bluetooth_toggle, volume_up, volume_down, volume_mute, brightness_up, brightness_down, battery_status")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("action") })
            }
            put("parameters", params)
        }

        // 8. toggleSecurityGuard
        val toggleSecurityGuard = JSONObject().apply {
            put("name", "toggleSecurityGuard")
            put("description", "Arms or disarms ARC Anti-Theft Siren Guard (motion + charger unplug alarm + visible camera alert).")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("armed", "BOOLEAN", "True to arm security guard, false to disarm.")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("armed") })
            }
            put("parameters", params)
        }

        // 9. readWhatsAppNotifications
        val readWhatsAppNotifications = JSONObject().apply {
            put("name", "readWhatsAppNotifications")
            put("description", "Reads unread captured WhatsApp notifications aloud.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
            put("parameters", params)
        }

        // 10. readScreenVision
        val readScreenVision = JSONObject().apply {
            put("name", "readScreenVision")
            put("description", "Reads current on-screen text using Accessibility tree and ML Kit OCR fallback.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
            put("parameters", params)
        }

        // 11. consultClaudeBrain (Anthropic Claude reasoning & tool-decision brain)
        val consultClaudeBrain = JSONObject().apply {
            put("name", "consultClaudeBrain")
            put("description", "Delegates complex command understanding, multi-step reasoning, contact/app selection, or general Q&A to ARC's Claude reasoning brain. Always call this first for actionable commands or Q&A when available.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("userQuery", "STRING", "Exact transcribed user request or question in the user's language.")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("userQuery") })
            }
            put("parameters", params)
        }

        // 12. openGoogleMapsRoute
        val openGoogleMapsRoute = JSONObject().apply {
            put("name", "openGoogleMapsRoute")
            put("description", "Opens driving route directions between origin and destination in Google Maps or browser (e.g., 'Delhi se Mathura ka route dikhao').")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("origin", "STRING", "Starting city/address (optional; omit or empty for current GPS location).")
                    propsPut("destination", "STRING", "Destination city/address (e.g., 'Mathura').")
                    propsPut("travelMode", "STRING", "Travel mode: 'driving', 'walking', 'bicycling', or 'transit'. Defaults to 'driving'.")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("destination") })
            }
            put("parameters", params)
        }

        // 13. assistRideBooking
        val assistRideBooking = JSONObject().apply {
            put("name", "assistRideBooking")
            put("description", "Assists booking a ride on Uber, Ola, or Rapido. If vehicleType is missing, asks user 'Bike, car 4-seater, ya 6-seater?'. Opens app pre-filled; user MUST manually tap Book and pay.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("destination", "STRING", "Dropoff location or city (e.g., 'Mathura', 'Airport').")
                    propsPut("vehicleType", "STRING", "One of: 'bike', 'car_4_seater', 'car_6_seater', 'auto'. Leave empty if user has not specified yet so ARC asks first.")
                    propsPut("preferredApp", "STRING", "Optional app preference: 'uber', 'ola', or 'rapido'.")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("destination") })
            }
            put("parameters", params)
        }

        // 14. assistShoppingSearch
        val assistShoppingSearch = JSONObject().apply {
            put("name", "assistShoppingSearch")
            put("description", "Opens shopping app (Flipkart, Flipkart Minutes, Blinkit, Zepto, Amazon, Instamart) and pre-searches a product. NEVER places orders or pays; user adds to cart and confirms payment.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    propsPut("appName", "STRING", "Store name (e.g. 'Flipkart', 'Flipkart Minutes', 'Blinkit', 'Zepto', 'Amazon', 'Instamart').")
                    propsPut("productQuery", "STRING", "Product search text (e.g. '500g sugar').")
                }
                put("properties", props)
                put("required", JSONArray().apply { put("appName"); put("productQuery") })
            }
            put("parameters", params)
        }

        // 15. readAllNotifications
        val readAllNotifications = JSONObject().apply {
            put("name", "readAllNotifications")
            put("description", "Reads unread/recent notifications across all enabled apps (WhatsApp, SMS, Instagram, Gmail, etc.) aloud with app name and preview text.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
            put("parameters", params)
        }

        // 16. fillFormFields
        val fillFormFields = JSONObject().apply {
            put("name", "fillFormFields")
            put("description", "Auto-fills visible on-screen form inputs (Name, Email, Phone, Address) using saved Personal Info while skipping password/sensitive/unknown fields.")
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
            put(controlHardware)
            put(toggleSecurityGuard)
            put(readWhatsAppNotifications)
            put(readScreenVision)
            put(consultClaudeBrain)
            put(openGoogleMapsRoute)
            put(assistRideBooking)
            put(assistShoppingSearch)
            put(readAllNotifications)
            put(fillFormFields)
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
            "controlHardware" -> executeControlHardware(context, callId, args)
            "toggleSecurityGuard" -> executeToggleSecurityGuard(context, callId, args)
            "readWhatsAppNotifications" -> executeReadWhatsAppNotifications(context, callId)
            "readScreenVision" -> executeReadScreenVision(callId)
            "consultClaudeBrain" -> executeConsultClaudeBrain(context, callId, args)
            "openGoogleMapsRoute" -> executeOpenGoogleMapsRoute(context, callId, args)
            "assistRideBooking" -> executeAssistRideBooking(context, callId, args)
            "assistShoppingSearch" -> executeAssistShoppingSearch(context, callId, args)
            "readAllNotifications" -> executeReadAllNotifications(context, callId)
            "fillFormFields" -> executeFillFormFields(context, callId)
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

    private fun executeControlHardware(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val action = args.optString("action", "")
        val message: String
        var success = true

        when (action) {
            "flashlight_on" -> {
                val res = AndroidHardwareController.setTorchMode(context, true)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "flashlight_off" -> {
                val res = AndroidHardwareController.setTorchMode(context, false)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "wifi_toggle" -> {
                val res = AndroidHardwareController.openWifiControl(context)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "bluetooth_toggle" -> {
                val res = AndroidHardwareController.openBluetoothControl(context)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "volume_up" -> {
                val res = AndroidHardwareController.adjustVolume(context, true)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "volume_down" -> {
                val res = AndroidHardwareController.adjustVolume(context, false)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "volume_mute" -> {
                val res = AndroidHardwareController.setVolumeExtreme(context, mute = true)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "brightness_up" -> {
                val res = AndroidHardwareController.adjustBrightness(context, directionUp = true)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "brightness_down" -> {
                val res = AndroidHardwareController.adjustBrightness(context, directionUp = false)
                success = res is HardwareActionResult.Success
                message = when (res) { is HardwareActionResult.Success -> res.message; is HardwareActionResult.Failure -> res.message }
            }
            "battery_status" -> {
                message = AndroidHardwareController.getBatteryTelemetry(context)
            }
            else -> {
                success = false
                message = "Unknown hardware action: $action"
            }
        }

        return ToolExecutionOutput(
            callId = callId,
            toolName = "controlHardware",
            success = success,
            responseData = JSONObject().apply { put("action", action); put("message", message) },
            verbalConfirmation = message
        )
    }

    private fun executeToggleSecurityGuard(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val armed = args.optBoolean("armed", true)
        val msg = if (armed) {
            AntiTheftSirenManager.armSecurity(context)
        } else {
            AntiTheftSirenManager.disarmSecurity(context)
        }
        return ToolExecutionOutput(
            callId = callId,
            toolName = "toggleSecurityGuard",
            success = true,
            responseData = JSONObject().apply { put("armed", armed); put("message", msg) },
            verbalConfirmation = msg
        )
    }

    private fun executeReadWhatsAppNotifications(context: Context, callId: String): ToolExecutionOutput {
        if (!WhatsAppAutomationManager.isNotificationAccessGranted(context)) {
            WhatsAppAutomationManager.openNotificationAccessSettings(context)
            val msg = "Notification Access permission enabled nahi hai. Maine settings screen khol di hai."
            return ToolExecutionOutput(
                callId = callId,
                toolName = "readWhatsAppNotifications",
                success = false,
                responseData = JSONObject().apply { put("status", "permission_required") },
                verbalConfirmation = msg
            )
        }
        val summary = WhatsAppAutomationManager.getUnreadWhatsAppMessagesSummary()
        return ToolExecutionOutput(
            callId = callId,
            toolName = "readWhatsAppNotifications",
            success = true,
            responseData = JSONObject().apply { put("summary", summary) },
            verbalConfirmation = summary
        )
    }

    private fun executeReadScreenVision(callId: String): ToolExecutionOutput {
        val text = runBlocking { AriaAccessibilityGestureService.readScreenContentSuspend() }
        return ToolExecutionOutput(
            callId = callId,
            toolName = "readScreenVision",
            success = true,
            responseData = JSONObject().apply { put("screenText", text) },
            verbalConfirmation = text
        )
    }

    private fun executeConsultClaudeBrain(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val userQuery = args.optString("userQuery", "").trim()
        if (userQuery.isBlank()) {
            return ToolExecutionOutput(
                callId = callId,
                toolName = "consultClaudeBrain",
                success = false,
                responseData = JSONObject().apply {
                    put("useGeminiFallback", true)
                    put("reason", "empty_query")
                },
                verbalConfirmation = ""
            )
        }

        val result = runBlocking {
            val memoryContext = try {
                MemoryRepository(AriaDatabase.getDatabase(context).ariaDao()).buildPromptMemoryContext()
            } catch (_: Exception) {
                ""
            }
            ClaudeReasoningEngine.reasonAndExecute(
                context = context,
                userTranscript = userQuery,
                executeTools = true,
                memoryContext = memoryContext
            )
        }

        return when (result) {
            is ClaudeReasoningResult.Success -> {
                val executedNames = JSONArray().apply {
                    result.executedOutputs.forEach { put(it.toolName) }
                }
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "consultClaudeBrain",
                    success = true,
                    responseData = JSONObject().apply {
                        put("useGeminiFallback", false)
                        put("spokenResponse", result.spokenResponse)
                        put("executedTools", executedNames)
                    },
                    verbalConfirmation = result.spokenResponse
                )
            }
            is ClaudeReasoningResult.FallbackRequired -> {
                Log.w(TAG, "Claude brain unavailable (${result.reason}); returning Gemini Live fallback signal")
                ToolExecutionOutput(
                    callId = callId,
                    toolName = "consultClaudeBrain",
                    success = false,
                    responseData = JSONObject().apply {
                        put("useGeminiFallback", true)
                        put("reason", result.reason)
                    },
                    verbalConfirmation = ""
                )
            }
        }
    }

    private fun executeOpenGoogleMapsRoute(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val origin = args.optString("origin", "").trim().takeIf { it.isNotBlank() }
        val destination = args.optString("destination", "").trim()
        val travelMode = args.optString("travelMode", "driving").ifBlank { "driving" }

        if (destination.isBlank()) {
            return ToolExecutionOutput(
                callId = callId,
                toolName = "openGoogleMapsRoute",
                success = false,
                responseData = JSONObject().apply { put("error", "missing_destination") },
                verbalConfirmation = "Kahan ka route dikhana hai? Destination batayein, Boss."
            )
        }

        val routeUrl = GoogleMapsVoiceHandler.buildGoogleMapsRouteUrl(origin, destination, travelMode)
        val inNativeMaps = GoogleMapsVoiceHandler.launchGoogleMapsOrBrowser(context, routeUrl)
        val msg = if (origin != null) {
            "$origin se $destination ka route ${if (inNativeMaps) "Google Maps" else "browser"} me khol diya hai, Boss."
        } else {
            "$destination ka route ${if (inNativeMaps) "Google Maps" else "browser"} me khol diya hai, Boss."
        }

        return ToolExecutionOutput(
            callId = callId,
            toolName = "openGoogleMapsRoute",
            success = true,
            responseData = JSONObject().apply {
                put("routeUrl", routeUrl)
                put("openedInMaps", inNativeMaps)
            },
            verbalConfirmation = msg
        )
    }

    private fun executeAssistRideBooking(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val destination = args.optString("destination", "").trim()
        val vehicleRaw = args.optString("vehicleType", "").trim().lowercase()
        val appRaw = args.optString("preferredApp", "").trim().lowercase()

        val vehicleOption = when (vehicleRaw) {
            "bike" -> RideVehicleOption.BIKE
            "auto" -> RideVehicleOption.AUTO
            "car_4_seater", "4-seater", "4_seater", "car", "mini", "sedan" -> RideVehicleOption.CAR_4_SEATER
            "car_6_seater", "6-seater", "6_seater", "xl", "suv" -> RideVehicleOption.CAR_6_SEATER
            else -> RideBookingAssistHandler.parseVehicleOption(vehicleRaw)
        }
        val preferredApp = when (appRaw) {
            "uber" -> RideApp.UBER
            "ola" -> RideApp.OLA
            "rapido" -> RideApp.RAPIDO
            else -> null
        }

        val result = runBlocking {
            RideBookingAssistHandler.processRideCommandWithLocation(
                context = context,
                rawQuery = destination,
                explicitVehicle = vehicleOption,
                explicitDestination = destination.takeIf { it.isNotBlank() },
                explicitApp = preferredApp
            )
        }

        return when (result) {
            is RideBookingResult.NeedsVehicleSelection -> ToolExecutionOutput(
                callId = callId,
                toolName = "assistRideBooking",
                success = true,
                responseData = JSONObject().apply {
                    put("needsVehicleSelection", true)
                    put("destination", result.destination)
                    put("question", result.message)
                },
                verbalConfirmation = result.message
            )
            is RideBookingResult.Launched -> ToolExecutionOutput(
                callId = callId,
                toolName = "assistRideBooking",
                success = true,
                responseData = JSONObject().apply {
                    put("needsVehicleSelection", false)
                    put("appName", result.appName)
                    put("vehicleLabel", result.vehicleLabel)
                    put("deepLinkUri", result.deepLinkUri)
                    put("userMustConfirmBookingAndPay", true)
                },
                verbalConfirmation = result.message
            )
            is RideBookingResult.Cancelled -> ToolExecutionOutput(
                callId = callId,
                toolName = "assistRideBooking",
                success = true,
                responseData = JSONObject().apply { put("cancelled", true) },
                verbalConfirmation = result.message
            )
            is RideBookingResult.Failure -> ToolExecutionOutput(
                callId = callId,
                toolName = "assistRideBooking",
                success = false,
                responseData = JSONObject().apply { put("error", result.message) },
                verbalConfirmation = result.message
            )
        }
    }

    private fun executeAssistShoppingSearch(context: Context, callId: String, args: JSONObject): ToolExecutionOutput {
        val appName = args.optString("appName", "Flipkart").trim()
        val productQuery = args.optString("productQuery", "").trim()

        val result = ShoppingAssistHandler.processShoppingCommand(
            context = context,
            rawQuery = "$appName $productQuery",
            explicitStoreName = appName,
            explicitProductQuery = productQuery.takeIf { it.isNotBlank() }
        )

        return when (result) {
            is ShoppingAssistResult.OpenedWithSearch -> ToolExecutionOutput(
                callId = callId,
                toolName = "assistShoppingSearch",
                success = true,
                responseData = JSONObject().apply {
                    put("appName", result.appName)
                    put("productQuery", result.productQuery)
                    put("openedUri", result.openedUri)
                    put("userMustAddToCartAndPay", true)
                },
                verbalConfirmation = result.message
            )
            is ShoppingAssistResult.OpenedAppOnly -> ToolExecutionOutput(
                callId = callId,
                toolName = "assistShoppingSearch",
                success = true,
                responseData = JSONObject().apply {
                    put("appName", result.appName)
                    put("productQuery", result.productQuery)
                    put("openedAppOnly", true)
                    put("userMustAddToCartAndPay", true)
                },
                verbalConfirmation = result.message
            )
            is ShoppingAssistResult.Failure -> ToolExecutionOutput(
                callId = callId,
                toolName = "assistShoppingSearch",
                success = false,
                responseData = JSONObject().apply { put("error", result.message) },
                verbalConfirmation = result.message
            )
        }
    }

    private fun executeReadAllNotifications(context: Context, callId: String): ToolExecutionOutput {
        if (!WhatsAppAutomationManager.isNotificationAccessGranted(context)) {
            WhatsAppAutomationManager.openNotificationAccessSettings(context)
            val msg = "Notification Access permission enabled nahi hai. Maine settings screen khol di hai."
            return ToolExecutionOutput(
                callId = callId,
                toolName = "readAllNotifications",
                success = false,
                responseData = JSONObject().apply { put("status", "permission_required") },
                verbalConfirmation = msg
            )
        }
        val summary = GeneralNotificationManager.getUnreadNotificationsSummary(context)
        return ToolExecutionOutput(
            callId = callId,
            toolName = "readAllNotifications",
            success = true,
            responseData = JSONObject().apply { put("summary", summary) },
            verbalConfirmation = summary
        )
    }

    private fun executeFillFormFields(context: Context, callId: String): ToolExecutionOutput {
        val profile = PersonalProfileManager.getProfile(context)
        val result = runBlocking {
            AriaAccessibilityGestureService.fillFormFieldsSuspend(profile)
        }
        return ToolExecutionOutput(
            callId = callId,
            toolName = "fillFormFields",
            success = result.success,
            responseData = JSONObject().apply {
                put("filledCount", result.filledFields.size)
                put("skippedCount", result.skippedCount)
                put("message", result.message)
            },
            verbalConfirmation = result.message
        )
    }
}
