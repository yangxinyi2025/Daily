package com.daily.life.core.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ReminderMode
import com.daily.life.feature.schedule.ScheduleEvent
import com.daily.life.feature.schedule.ScheduleRepository
import com.daily.life.feature.schedule.toModel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ReminderScheduleStatus {
    SCHEDULED,
    PERMISSION_RESTRICTED
}

data class ReminderScheduleDecision(
    val status: ReminderScheduleStatus,
    val triggerAt: Instant
)

object ReminderTimeCalculator {
    fun messageTrigger(event: ScheduleEvent): Instant =
        event.eventAt.minusSeconds(event.reminderOffsetMinutes.coerceAtLeast(0) * 60L)

    fun alarmTrigger(event: ScheduleEvent): Instant = messageTrigger(event)
}

object ReminderPlan {
    fun decide(
        event: ScheduleEvent,
        canPostNotifications: Boolean,
        canScheduleExactAlarms: Boolean
    ): ReminderScheduleDecision {
        val triggerAt = when (event.reminderMode) {
            ReminderMode.NOTIFICATION -> ReminderTimeCalculator.messageTrigger(event)
            ReminderMode.ALARM -> ReminderTimeCalculator.alarmTrigger(event)
        }
        val restricted = when (event.reminderMode) {
            ReminderMode.NOTIFICATION -> !canPostNotifications
            ReminderMode.ALARM -> !canScheduleExactAlarms
        }
        return ReminderScheduleDecision(
            status = if (restricted) {
                ReminderScheduleStatus.PERMISSION_RESTRICTED
            } else {
                ReminderScheduleStatus.SCHEDULED
            },
            triggerAt = triggerAt
        )
    }
}

interface ReminderScheduler {
    suspend fun scheduleCourseReminders(semesterId: Long) = Unit

    suspend fun schedule(event: ScheduleEvent): ReminderScheduleDecision =
        ReminderPlan.decide(event, canPostNotifications = true, canScheduleExactAlarms = true)

    suspend fun cancel(eventId: Long) = Unit

    suspend fun rescheduleFutureEvents(now: Instant) = Unit
}

class AndroidReminderScheduler(
    private val context: Context,
    private val database: DailyDatabase,
    private val clock: Clock = Clock.systemDefaultZone()
) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager by lazy {
        requireNotNull(appContext.getSystemService(AlarmManager::class.java))
    }

    override suspend fun schedule(event: ScheduleEvent): ReminderScheduleDecision = withContext(Dispatchers.Main) {
        val decision = ReminderPlan.decide(
            event = event,
            canPostNotifications = canPostNotifications(),
            canScheduleExactAlarms = canScheduleExactAlarms()
        )
        if (decision.status == ReminderScheduleStatus.PERMISSION_RESTRICTED) return@withContext decision
        ReminderChannels.ensure(appContext)
        val pendingIntent = reminderPendingIntent(event)
        alarmManager.cancel(pendingIntent)
        when (event.reminderMode) {
            ReminderMode.NOTIFICATION -> alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                decision.triggerAt.toEpochMilli(),
                pendingIntent
            )
            ReminderMode.ALARM -> alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                decision.triggerAt.toEpochMilli(),
                pendingIntent
            )
        }
        decision
    }

    override suspend fun cancel(eventId: Long) = withContext(Dispatchers.Main) {
        alarmManager.cancel(reminderPendingIntent(eventId))
    }

    override suspend fun rescheduleFutureEvents(now: Instant) {
        val events = database.scheduleEventDao().findAll().map { it.toModel() }
        events.forEach { original ->
            val event = if (original.repeatYearly) {
                val repository = ScheduleRepository.nextOccurrence(
                    original,
                    now.atZone(clock.zone).toLocalDate()
                )
                val localTime = original.eventAt.atZone(clock.zone).toLocalTime()
                original.copy(
                    eventAt = repository.atTime(localTime).atZone(clock.zone).toInstant()
                )
            } else {
                original
            }
            if (ReminderTimeCalculator.messageTrigger(event).isAfter(now)) schedule(event)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun reminderPendingIntent(event: ScheduleEvent): PendingIntent =
        reminderPendingIntent(event.id, event.reminderMode, event.title, event.notes)

    private fun reminderPendingIntent(eventId: Long): PendingIntent =
        reminderPendingIntent(eventId, ReminderMode.ALARM, "", null)

    private fun reminderPendingIntent(
        eventId: Long,
        mode: ReminderMode,
        title: String,
        message: String?
    ): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_MODE, mode.name)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
        }
        return PendingIntent.getBroadcast(
            appContext,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

object ReminderChannels {
    const val MESSAGE_CHANNEL_ID = "schedule_messages"
    const val ALARM_CHANNEL_ID = "schedule_alarms"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "日程提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALARM_CHANNEL_ID,
                "日程闹钟",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null) }
        )
    }
}
