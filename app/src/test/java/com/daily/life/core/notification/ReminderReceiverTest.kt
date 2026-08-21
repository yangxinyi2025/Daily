package com.daily.life.core.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderReceiverTest {
    @Test
    fun alarmDispatchTargetsRingtoneServiceAndFullScreenAlarmActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val dispatch = ReminderReceiver.alarmDispatch(
            context = context,
            eventId = 42L,
            title = "考试",
            message = "带准考证"
        )

        assertEquals(
            AlarmRingtoneService::class.java.name,
            dispatch.serviceIntent.component?.className
        )
        assertEquals(
            AlarmActivity::class.java.name,
            dispatch.activityIntent.component?.className
        )
        assertTrue(dispatch.usesFullScreenNotification)
    }
}
