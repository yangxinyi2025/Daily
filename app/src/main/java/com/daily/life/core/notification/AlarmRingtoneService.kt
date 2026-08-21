package com.daily.life.core.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.app.Notification
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

class AlarmRingtoneService : Service() {
    private var ringtone: Ringtone? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ReminderChannels.ensure(this)
        val title = intent?.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty().ifBlank { "日程闹钟" }
        val notification = NotificationCompat.Builder(this, ReminderChannels.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("请打开 Daily 关闭闹钟")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(
                this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )?.also { it.play() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ringtone?.stop()
        ringtone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "com.daily.life.action.START_ALARM"
        private const val ACTION_STOP = "com.daily.life.action.STOP_ALARM"
        private const val NOTIFICATION_ID = 70_001

        fun startIntent(context: Context, eventId: Long, title: String): Intent =
            Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_START
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
                putExtra(ReminderReceiver.EXTRA_TITLE, title)
            }

        fun stopIntent(context: Context, eventId: Long): Intent =
            Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_STOP
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            }
    }
}
