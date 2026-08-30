package com.daily.life.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStopActionTest {
    @Test
    fun onlyTheExplicitStopActionCanStopAnAlarm() {
        assertTrue(shouldStopAlarm(AlarmStopReceiver.ACTION_STOP))
        assertFalse(shouldStopAlarm(ReminderAlarmReceiver.ACTION_FIRE))
        assertFalse(shouldStopAlarm(null))
    }
}
