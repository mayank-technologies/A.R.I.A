package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AriaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AriaReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Reminder"
        val timeString = intent.getStringExtra(EXTRA_REMINDER_TIME) ?: ""

        if (action == ACTION_SNOOZE_REMINDER) {
            val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)

            // Cancel active notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(reminderId.toInt())

            // Reschedule notification
            val newTimeString = AriaNotificationScheduler.scheduleReminderNotificationInMinutes(
                context = context,
                reminderId = reminderId,
                title = title,
                minutes = snoozeMinutes
            )

            // Update database record
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AriaDatabase.getDatabase(context).ariaDao()
                    val existing = dao.getReminderById(reminderId)
                    if (existing != null) {
                        dao.updateReminder(existing.copy(timeString = newTimeString, isCompleted = false))
                    }
                } catch (e: Exception) {
                    Log.e("AriaReminderReceiver", "Error updating snoozed reminder in DB", e)
                } finally {
                    pendingResult.finish()
                }
            }

            Toast.makeText(
                context,
                "⏰ Snoozed '$title' for $snoozeMinutes min ($newTimeString)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Standard notification trigger
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val snooze5Action = createSnoozeAction(context, reminderId, title, 5)
        val snooze10Action = createSnoozeAction(context, reminderId, title, 10)
        val snooze15Action = createSnoozeAction(context, reminderId, title, 15)

        val notification = NotificationCompat.Builder(context, AriaNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("A.R.I.A. Reminder ⏰")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\nScheduled for $timeString\nTap snooze below to delay by 5, 10, or 15 mins."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(snooze5Action)
            .addAction(snooze10Action)
            .addAction(snooze15Action)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(reminderId.toInt(), notification)
    }

    private fun createSnoozeAction(
        context: Context,
        reminderId: Long,
        title: String,
        minutes: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, AriaReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_SNOOZE_MINUTES, minutes)
        }
        val requestCode = (reminderId.toInt() * 100) + minutes
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_popup_reminder,
            "Snooze ${minutes}m",
            pendingIntent
        ).build()
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REMINDER_TIME = "extra_reminder_time"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val ACTION_SNOOZE_REMINDER = "com.example.notification.ACTION_SNOOZE_REMINDER"
    }
}
