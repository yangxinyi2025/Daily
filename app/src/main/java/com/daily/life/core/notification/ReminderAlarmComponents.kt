package com.daily.life.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Daily 提醒" }
        val serviceIntent = Intent(context, AlarmRingtoneService::class.java)
            .setAction(AlarmRingtoneService.ACTION_START)
            .putExtras(intent)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (error: Exception) {
            // Do not silently lose an alarm if an OEM blocks the foreground
            // service. The high-priority fallback notification uses the alarm
            // audio stream and remains actionable from the lock screen.
            Log.e(TAG, "Unable to start Daily alarm foreground service", error)
            AlarmRingtoneService.postFallbackAlarmNotification(
                context = context,
                title = title
            )
        }
    }

    companion object {
        const val ACTION_FIRE = "com.daily.life.action.FIRE_ALARM_REMINDER"
        const val EXTRA_REMINDER_ID = "com.daily.life.extra.REMINDER_ID"
        const val EXTRA_TITLE = "com.daily.life.extra.TITLE"
        const val EXTRA_EVENT_AT = "com.daily.life.extra.EVENT_AT"
        const val EXTRA_NOTES = "com.daily.life.extra.NOTES"
        private const val TAG = "DailyAlarmReceiver"
    }
}

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldStopAlarm(intent.action)) return
        context.stopService(Intent(context, AlarmRingtoneService::class.java))
        AlarmRingtoneService.dismissAlarmNotifications(context)
    }

    companion object {
        const val ACTION_STOP = "com.daily.life.action.STOP_ALARM"
    }
}

internal fun shouldStopAlarm(action: String?): Boolean = action == AlarmStopReceiver.ACTION_STOP

class AlarmRingtoneService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val title = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_TITLE).orEmpty().ifBlank { "Daily 提醒" }
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification(title))
            startAlarmSound(title)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start Daily alarm", error)
            postFallbackAlarmNotification(this, title)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarmSound(title: String) {
        stopAlarmSoundOnly()
        if (!requestAlarmAudioFocus(title)) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Daily:AlarmAudio")?.apply {
            setReferenceCounted(false)
            acquire(MAX_RING_DURATION_MILLIS)
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: run {
                postFallbackAlarmNotification(this, title)
                return
            }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttributes())
                isLooping = true
                setDataSource(this@AlarmRingtoneService, uri)
                prepare()
                setVolume(1f, 1f)
                start()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start Daily alarm audio", error)
            postFallbackAlarmNotification(this, title)
        }
    }

    private fun stopAlarm() {
        stopAlarmSoundOnly()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopAlarmSoundOnly() {
        player?.runCatching { if (isPlaying) stop() }
        player?.release()
        player = null
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
        audioFocusRequest?.let { request -> audioManager?.abandonAudioFocusRequest(request) }
        audioFocusRequest = null
        audioManager = null
    }

    private fun requestAlarmAudioFocus(title: String): Boolean {
        val manager = getSystemService(AudioManager::class.java) ?: run {
            postFallbackAlarmNotification(this, title)
            return false
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(alarmAudioAttributes())
            .build()
        val result = manager.requestAudioFocus(request)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            postFallbackAlarmNotification(this, title)
            return false
        }
        audioManager = manager
        audioFocusRequest = request
        return true
    }

    private fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun buildNotification(title: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Daily 闹钟")
        .setContentText(title)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setAutoCancel(false)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "停止闹钟",
            stopAlarmPendingIntent(this)
        )
        .setFullScreenIntent(fullScreenPendingIntent(title), true)
        .build()

    private fun fullScreenPendingIntent(title: String) = android.app.PendingIntent.getActivity(
        this,
        20_000,
        Intent(this, AlarmActivity::class.java)
            .putExtra(AlarmActivity.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "闹钟提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily 精准闹钟提醒"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
            }
        )
    }

    companion object {
        const val ACTION_START = "com.daily.life.action.START_ALARM_SOUND"
        const val ACTION_STOP = "com.daily.life.action.STOP_ALARM_SOUND"
        private const val CHANNEL_ID = "daily_alarm_v2"
        private const val NOTIFICATION_ID = 7_001
        private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1_000L
        private const val TAG = "DailyAlarmService"

        fun postFallbackAlarmNotification(context: Context, title: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val alarmAudio = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                manager.createNotificationChannel(
                    NotificationChannel(
                        FALLBACK_CHANNEL_ID,
                        "闹钟提醒（备用）",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Daily 前台铃声服务无法启动时的备用闹钟提醒"
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        setSound(
                            alarmAudio,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        enableVibration(true)
                    }
                )
            }
            manager.notify(
                FALLBACK_NOTIFICATION_ID,
                NotificationCompat.Builder(context, FALLBACK_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Daily 闹钟")
                    .setContentText(title)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "停止闹钟",
                        stopAlarmPendingIntent(context)
                    )
                    .setFullScreenIntent(
                        android.app.PendingIntent.getActivity(
                            context,
                            20_001,
                            Intent(context, AlarmActivity::class.java)
                                .putExtra(AlarmActivity.EXTRA_TITLE, title)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        ),
                        true
                    )
                    .build()
            )
        }

        fun dismissAlarmNotifications(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.apply {
                cancel(NOTIFICATION_ID)
                cancel(FALLBACK_NOTIFICATION_ID)
            }
        }

        private fun stopAlarmPendingIntent(context: Context) = android.app.PendingIntent.getBroadcast(
            context,
            20_002,
            Intent(context, AlarmStopReceiver::class.java).setAction(AlarmStopReceiver.ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        private const val FALLBACK_CHANNEL_ID = "daily_alarm_fallback_v3"
        private const val FALLBACK_NOTIFICATION_ID = 7_002
    }
}

class AlarmActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Daily 提醒" }
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * density).toInt(), (32 * density).toInt(), (32 * density).toInt(), (32 * density).toInt())
            setBackgroundColor(0xFFF5F7FF.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "Daily 闹钟"
            textSize = 28f
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = title
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, (18 * density).toInt(), 0, (24 * density).toInt())
        })
        layout.addView(Button(this).apply {
            text = "停止闹钟"
            setOnClickListener { dismissAlarm() }
        })
        setContentView(layout)
    }

    private fun dismissAlarm() {
        sendBroadcast(
            Intent(this, AlarmStopReceiver::class.java).setAction(AlarmStopReceiver.ACTION_STOP)
        )
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_REMINDER_ID = "com.daily.life.extra.REMINDER_ID"
        const val EXTRA_TITLE = "com.daily.life.extra.TITLE"
    }
}

class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldRescheduleAlarmReminders(intent.action)) return
        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val database = com.daily.life.core.database.DailyDatabase.build(appContext)
                try {
                    rescheduleStoredAlarmReminders(
                        database = database,
                        scheduler = AndroidReminderScheduler(appContext),
                        now = java.time.Instant.now()
                    )
                } finally {
                    database.close()
                }
            } catch (_: Exception) {
                // Boot and permission-change broadcasts must always finish. A
                // later app launch can retry if credential-protected storage is
                // not available yet during direct boot.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun shouldRescheduleAlarmReminders(action: String?): Boolean = action in setOf(
    Intent.ACTION_BOOT_COMPLETED,
    "android.intent.action.LOCKED_BOOT_COMPLETED",
    Intent.ACTION_MY_PACKAGE_REPLACED,
    android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
)
