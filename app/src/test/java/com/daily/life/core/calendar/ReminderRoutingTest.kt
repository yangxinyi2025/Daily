package com.daily.life.core.calendar

import com.daily.life.core.database.ReminderMode
import com.daily.life.feature.schedule.ScheduleEvent
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRoutingTest {
    @Test
    fun alarmScheduleUsesDailyAlarmInsteadOfSystemCalendarMessage() {
        val event = ScheduleEvent(
            title = "测试闹钟",
            eventAt = Instant.parse("2026-09-15T08:00:00Z"),
            reminderOffsetMinutes = 10,
            reminderMode = ReminderMode.ALARM,
            repeatYearly = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        assertFalse(ReminderRouting.usesCalendarMessage(event))
        assertTrue(ReminderRouting.usesDailyAlarm(event))
    }
}
