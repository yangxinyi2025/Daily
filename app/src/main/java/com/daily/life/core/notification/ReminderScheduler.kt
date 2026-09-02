package com.daily.life.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ReminderMode
import java.time.Instant

enum class ReminderScheduleStatus { SCHEDULED, PERMISSION_RESTRICTED }

data class ReminderScheduleDecision(
    val status: ReminderScheduleStatus,
    val triggerAt: Instant
)

data class AlarmReminderSpec(
    val id: Long,
    val title: String,
    val triggerAt: Instant,
    val eventAt: Instant,
    val notes: String? = null
)

interface ExactAlarmPlatform {
    fun canScheduleExactAlarms(): Boolean
    fun schedule(reminder: AlarmReminderSpec)
    fun cancel(reminderId: Long)
}

class PlatformReminderScheduler(
    private val platform: ExactAlarmPlatform
) : ReminderScheduler {
    override suspend fun schedule(reminder: AlarmReminderSpec): ReminderScheduleDecision {
        if (!platform.canScheduleExactAlarms()) {
            return ReminderScheduleDecision(ReminderScheduleStatus.PERMISSION_RESTRICTED, reminder.triggerAt)
        }
        return try {
            platform.schedule(reminder)
            ReminderScheduleDecision(ReminderScheduleStatus.SCHEDULED, reminder.triggerAt)
        } catch (_: SecurityException) {
            // OEM settings can report a stale exact-alarm state. Treat this as a
            // restricted registration instead of claiming the alarm was scheduled.
            ReminderScheduleDecision(ReminderScheduleStatus.PERMISSION_RESTRICTED, reminder.triggerAt)
        }
    }

    override suspend fun cancel(reminderId: Long) {
        platform.cancel(reminderId)
    }
}

class AndroidReminderScheduler(context: Context) : ReminderScheduler by PlatformReminderScheduler(
    AndroidExactAlarmPlatform(context.applicationContext)
)

private class AndroidExactAlarmPlatform(
    private val context: Context
) : ExactAlarmPlatform {
    private val alarmManager = requireNotNull(context.getSystemService(AlarmManager::class.java))

    override fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    override fun schedule(reminder: AlarmReminderSpec) {
        val operation = reminderPendingIntent(reminder, PendingIntent.FLAG_UPDATE_CURRENT)
        val showIntent = PendingIntent.getActivity(
            context,
            reminderRequestCode(reminder.id) + SHOW_INTENT_OFFSET,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_REMINDER_ID, reminder.id)
                .putExtra(AlarmActivity.EXTRA_TITLE, reminder.title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(reminder.triggerAt.toEpochMilli(), showIntent),
            requireNotNull(operation)
        )
    }

    override fun cancel(reminderId: Long) {
        val operation = reminderPendingIntent(
            AlarmReminderSpec(
                id = reminderId,
                title = "",
                triggerAt = Instant.EPOCH,
                eventAt = Instant.EPOCH
            ),
            PendingIntent.FLAG_NO_CREATE
        )
        if (operation != null) {
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun reminderPendingIntent(reminder: AlarmReminderSpec, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            reminderRequestCode(reminder.id),
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmReceiver.ACTION_FIRE)
                .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
                .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, reminder.title)
                .putExtra(ReminderAlarmReceiver.EXTRA_EVENT_AT, reminder.eventAt.toEpochMilli())
                .putExtra(ReminderAlarmReceiver.EXTRA_NOTES, reminder.notes),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    private fun reminderRequestCode(id: Long): Int = id.hashCode()

    private companion object {
        const val SHOW_INTENT_OFFSET = 1_000_000
    }
}

/** Courses use the system Calendar for fixed message reminders. */
interface ReminderScheduler {
    suspend fun schedule(reminder: AlarmReminderSpec): ReminderScheduleDecision =
        ReminderScheduleDecision(ReminderScheduleStatus.PERMISSION_RESTRICTED, reminder.triggerAt)

    suspend fun cancel(reminderId: Long) = Unit

    suspend fun scheduleCourseReminders(semesterId: Long) = Unit
}

internal suspend fun rescheduleStoredAlarmReminders(
    database: DailyDatabase,
    scheduler: ReminderScheduler,
    now: Instant
) {
    database.scheduleEventDao().findAll()
        .filter { it.reminderMode == ReminderMode.ALARM }
        .forEach { event ->
            val eventAt = Instant.ofEpochMilli(event.eventAt)
            val triggerAt = eventAt.minusSeconds(event.reminderOffsetMinutes.coerceAtLeast(0) * 60L)
            if (triggerAt.isAfter(now)) {
                scheduler.schedule(
                    AlarmReminderSpec(
                        id = event.id,
                        title = event.title,
                        triggerAt = triggerAt,
                        eventAt = eventAt,
                        notes = event.notes
                    )
                )
            }
        }
}
