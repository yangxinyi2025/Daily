package com.daily.life.core.notification

import com.daily.life.core.database.ReminderMode
import com.daily.life.feature.schedule.ScheduleEvent
import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun messageReminderUsesConfiguredOffset() {
        val event = eventAt("2026-08-21T10:00:00+08:00", reminderOffsetMinutes = 20)

        assertEquals(
            OffsetDateTime.parse("2026-08-21T09:40:00+08:00").toInstant(),
            ReminderTimeCalculator.messageTrigger(event)
        )
    }

    @Test
    fun alarmFallsBackToPermissionRestrictedWhenExactAlarmIsUnavailable() {
        val event = eventAt("2026-08-21T10:00:00+08:00", reminderOffsetMinutes = 0)

        val decision = ReminderPlan.decide(
            event = event.copy(reminderMode = ReminderMode.ALARM),
            canPostNotifications = true,
            canScheduleExactAlarms = false
        )

        assertEquals(ReminderScheduleStatus.PERMISSION_RESTRICTED, decision.status)
        assertTrue(decision.triggerAt.isAfter(Instant.EPOCH))
    }

    private fun eventAt(value: String, reminderOffsetMinutes: Int): ScheduleEvent =
        ScheduleEvent(
            title = "测试事件",
            eventAt = OffsetDateTime.parse(value).toInstant(),
            reminderOffsetMinutes = reminderOffsetMinutes,
            reminderMode = ReminderMode.NOTIFICATION,
            repeatYearly = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
}
