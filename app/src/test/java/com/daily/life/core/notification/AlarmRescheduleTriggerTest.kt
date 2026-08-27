package com.daily.life.core.notification

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRescheduleTriggerTest {
    @Test
    fun reschedulesPendingDailyAlarmsAfterTheAppIsUpdated() {
        assertTrue(shouldRescheduleAlarmReminders(Intent.ACTION_MY_PACKAGE_REPLACED))
    }
}
