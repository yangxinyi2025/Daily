package com.daily.life.core.notification

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmReminderSchedulerTest {
    @Test
    fun schedulesAnExactAlarmThroughThePlatformWhenPermissionIsAvailable() = kotlinx.coroutines.test.runTest {
        val platform = RecordingExactAlarmPlatform(canSchedule = true)
        val scheduler = PlatformReminderScheduler(platform)
        val reminder = AlarmReminderSpec(
            id = 7L,
            title = "考试",
            triggerAt = Instant.parse("2026-09-15T07:50:00Z"),
            eventAt = Instant.parse("2026-09-15T08:00:00Z")
        )

        val result = scheduler.schedule(reminder)

        assertEquals(ReminderScheduleStatus.SCHEDULED, result.status)
        assertEquals(reminder, platform.scheduled.single())
    }

    @Test
    fun refusesToPretendAnExactAlarmWasScheduledWithoutPermission() = kotlinx.coroutines.test.runTest {
        val platform = RecordingExactAlarmPlatform(canSchedule = false)
        val scheduler = PlatformReminderScheduler(platform)

        val result = scheduler.schedule(
            AlarmReminderSpec(
                id = 8L,
                title = "小事",
                triggerAt = Instant.parse("2026-09-15T07:50:00Z"),
                eventAt = Instant.parse("2026-09-15T08:00:00Z")
            )
        )

        assertEquals(ReminderScheduleStatus.PERMISSION_RESTRICTED, result.status)
        assertTrue(platform.scheduled.isEmpty())
    }

    @Test
    fun cancelsTheSameAlarmId() = kotlinx.coroutines.test.runTest {
        val platform = RecordingExactAlarmPlatform(canSchedule = true)
        val scheduler = PlatformReminderScheduler(platform)

        scheduler.cancel(42L)

        assertEquals(listOf(42L), platform.cancelled)
    }
}

private class RecordingExactAlarmPlatform(
    private val canSchedule: Boolean
) : ExactAlarmPlatform {
    val scheduled = mutableListOf<AlarmReminderSpec>()
    val cancelled = mutableListOf<Long>()

    override fun canScheduleExactAlarms(): Boolean = canSchedule

    override fun schedule(reminder: AlarmReminderSpec) {
        scheduled += reminder
    }

    override fun cancel(reminderId: Long) {
        cancelled += reminderId
    }
}
