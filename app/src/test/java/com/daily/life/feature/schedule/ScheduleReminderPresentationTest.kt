package com.daily.life.feature.schedule

import com.daily.life.core.database.ReminderMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleReminderPresentationTest {
    @Test
    fun labelsAnAlarmEventAsAnAlarmInsteadOfAMessage() {
        assertEquals("闹钟提醒", ReminderMode.ALARM.displayLabel())
    }
}
