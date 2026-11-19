package com.example.assistant.whatsapp

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.FileProvider
import com.example.assistant.WhatsAppActionResult
import com.example.assistant.WhatsAppContactResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing an incoming WhatsApp notification.
 */
data class WhatsAppNotificationItem(
    val id: String,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val timeFormatted: String
)

/**
 * WhatsAppAutomationManager:
 * Provides end-to-end automation for WhatsApp:
 * 1. Send text message via wa.me deep link with pre-filled message text.
 * 2. Record device audio note and share directly to WhatsApp via Intent.ACTION_SEND.
 * 3. Read unread WhatsApp notifications aloud using NotificationListenerService.
 * 4. WhatsApp Call Limitation Handling: Clearly opens chat and informs user to tap the call button
 *    (as Meta strictly disallows external auto-dialing without user interaction).
 */
object WhatsAppAutomationManager {

    private const val TAG = "ARC_WHATSAPP_AUTO"
    private var mediaRecorder: MediaRecorder? = null
    private var currentVoiceNoteFile: File? = null
    private var isRecording = false

    fun isRecordingActive(): Boolean = isRecording

    /**
     * Checks if Notification Listener access is granted for this app.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val myComponent = "${context.packageName}/${ArcNotificationListenerService::class.java.canonicalName}"
        return enabledListeners.contains(myComponent) || enabledListeners.contains(context.packageName)
    }

    /**
     * Opens Android System Settings directly to Notification Access permission page.
     */
    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings: ${e.message}")
        }
    }

    /**
     * Reads all unread WhatsApp notifications formatted for voice readout.
     */
    fun getUnreadWhatsAppMessagesSummary(): String {
        val list = ArcNotificationListenerService.capturedWhatsAppNotifications.value
        if (list.isEmpty()) {
            return "Koi naye unread WhatsApp messages nahi hain, Boss."
        }
        val builder = StringBuilder("Aapke paas ${list.size} naye WhatsApp messages hain:\n")
        list.take(5).forEachIndexed { idx, item ->
            builder.append("${idx + 1}. ${item.sender} ne kaha: \"${item.message}\"\n")
        }
        return builder.toString().trim()
    }

    /**
     * Starts recording an audio voice note using device microphone.
     */
    fun startVoiceNoteRecording(context: Context): Boolean {
        return try {
            val cacheDir = File(context.cacheDir, "arc_voice_notes").apply { if (!exists()) mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioFile = File(cacheDir, "ARC_VN_$timeStamp.m4a")
            currentVoiceNoteFile = audioFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d(TAG, "Voice note recording started: ${audioFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice note recording: ${e.message}", e)
            isRecording = false
            false
        }
    }

    /**
     * Stops recording and creates an Intent to send the audio note to WhatsApp.
     */
    fun stopAndSendVoiceNote(context: Context, contactName: String): WhatsAppActionResult {
        if (!isRecording || mediaRecorder == null || currentVoiceNoteFile == null) {
            return WhatsAppActionResult.GeneralFailure("Voice note recording active nahi thi.")
        }

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder: ${e.message}")
        }

        val file = currentVoiceNoteFile
        if (file == null || !file.exists() || file.length() == 0L) {
            return WhatsAppActionResult.GeneralFailure("Recorded audio file empty hai.")
        }

        // Find contact match
        val contactMatch = WhatsAppContactResolver.findContactByName(context, contactName)
        val cleanPhone = contactMatch?.phoneNumber?.replace(Regex("[^0-9]"), "") ?: ""

        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                setPackage("com.whatsapp")
                if (cleanPhone.isNotBlank()) {
                    putExtra("jid", "$cleanPhone@s.whatsapp.net")
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(shareIntent)
            val recipientLabel = contactMatch?.displayName ?: contactName
            WhatsAppActionResult.Success(
                message = "Voice note record karke WhatsApp share screen khol diya hai: $recipientLabel ko send kijiye!",
                openUrl = "https://wa.me/$cleanPhone"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share voice note: ${e.message}", e)
            WhatsAppActionResult.GeneralFailure("WhatsApp par audio bhejne me error aaya: ${e.localizedMessage}")
        }
    }

    /**
     * Handles WhatsApp voice/video call intent with honest limitation explanation.
     */
    fun handleWhatsAppCallIntent(context: Context, contactName: String, isVideo: Boolean): WhatsAppActionResult {
        val contactMatch = WhatsAppContactResolver.findContactByName(context, contactName)
        val cleanPhone = contactMatch?.phoneNumber?.replace(Regex("[^0-9]"), "") ?: ""

        val callType = if (isVideo) "video" else "voice"

        // META RESTRICTION NOTE:
        // WhatsApp does NOT expose a public auto-dial intent without user tap due to end-to-end encryption & spam protection.
        // ARC opens the direct chat screen so user can tap the call icon directly.
        val chatUrl = if (cleanPhone.isNotBlank()) "https://wa.me/$cleanPhone" else "https://wa.me"
        val launched = WhatsAppContactResolver.launchWhatsAppUri(context, chatUrl)

        val name = contactMatch?.displayName ?: contactName
        return if (launched) {
            WhatsAppActionResult.Success(
                message = "Maine $name ka WhatsApp chat khol diya hai, top right me $callType call button dabaiye!",
                openUrl = chatUrl
            )
        } else {
            WhatsAppActionResult.GeneralFailure("WhatsApp launch nahi ho saka.")
        }
    }
}

/**
 * Android NotificationListenerService to capture incoming notifications across ALL apps
 * (WhatsApp, SMS, Instagram, Gmail, Telegram, etc.), respecting per-app Settings toggles.
 */
class ArcNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "ArcNotificationListener"
        private val _notifications = MutableStateFlow<List<WhatsAppNotificationItem>>(emptyList())
        val capturedWhatsAppNotifications: StateFlow<List<WhatsAppNotificationItem>> = _notifications.asStateFlow()

        fun clearNotifications() {
            _notifications.value = emptyList()
            com.example.assistant.notification.GeneralNotificationManager.clearAll()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras: Bundle? = notification.extras

        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val previewText = bigText.ifBlank { normalText }.trim()

        // 1. Capture into GeneralNotificationManager (all apps: SMS, Instagram, Gmail, WhatsApp, etc.)
        com.example.assistant.notification.GeneralNotificationManager.recordIncomingNotification(
            context = applicationContext,
            packageName = packageName,
            sender = title,
            message = previewText,
            postTime = sbn.postTime,
            notificationId = sbn.key ?: "${sbn.id}_${sbn.postTime}",
            isOngoing = isOngoing
        )

        // 2. Keep WhatsApp-specific list populated for "WhatsApp messages padho"
        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            val senderLabel = title.ifBlank { "WhatsApp" }
            if (previewText.isNotBlank() &&
                !senderLabel.contains("WhatsApp", ignoreCase = true) &&
                !senderLabel.contains("messages", ignoreCase = true)
            ) {
                val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(sbn.postTime))
                val item = WhatsAppNotificationItem(
                    id = sbn.key ?: "${sbn.id}_${sbn.postTime}",
                    sender = senderLabel,
                    message = previewText,
                    timestamp = sbn.postTime,
                    timeFormatted = timeFormatted
                )
                Log.d(TAG, "Captured WhatsApp Notification from $senderLabel: $previewText")
                _notifications.value = (listOf(item) + _notifications.value)
                    .distinctBy { "${it.sender}_${it.message}" }
                    .take(20)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification dismissed by user
    }
}
