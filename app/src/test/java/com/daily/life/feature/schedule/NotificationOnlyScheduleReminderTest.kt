package com.daily.life.feature.schedule

import com.daily.life.core.database.ReminderMode
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationOnlyScheduleReminderTest {
    @Test
    fun alarmEventsKeepTheirAlarmReminderModeWhenEditedAndSaved() {
        val event = ScheduleEvent(
            id = 8L,
            title = "答辩",
            eventAt = Instant.parse("2026-09-15T08:00:00Z"),
            reminderOffsetMinutes = 10,
            reminderMode = ReminderMode.ALARM,
            repeatYearly = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        val editor = ScheduleEditorState.from(event, ZoneId.of("Asia/Shanghai"))
        val saved = editor.toEvent(Instant.EPOCH, ZoneId.of("Asia/Shanghai"))

        assertEquals(ReminderMode.ALARM, editor.reminderMode)
        assertEquals(ReminderMode.ALARM, saved.reminderMode)
    }
}
