package com.daily.life.core.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.daily.life.core.database.ReminderMode

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L)
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { value -> runCatching { ReminderMode.valueOf(value) }.getOrNull() }
            ?: ReminderMode.NOTIFICATION
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "日程提醒" }
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        if (mode == ReminderMode.ALARM) {
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ContextCompat.startActivity(context, alarmIntent, null)
        } else {
            postMessageNotification(context, eventId, title, message)
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"

        fun postMessageNotification(context: Context, eventId: Long, title: String, message: String) {
            ReminderChannels.ensure(context)
            val notification = NotificationCompat.Builder(context, ReminderChannels.MESSAGE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(message.ifBlank { "日程到了" })
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                context.getSystemService(NotificationManager::class.java).notify(eventId.toInt(), notification)
            }
        }
    }
}
