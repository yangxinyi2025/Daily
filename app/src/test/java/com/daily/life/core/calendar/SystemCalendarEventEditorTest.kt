package com.daily.life.core.calendar

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemCalendarEventEditorTest {
    @Test
    fun opensTheExistingSystemCalendarEventForUserToChooseAlarmReminder() {
        val intent = SystemCalendarEventEditor.intentFor(eventId = 42L)

        assertEquals(Intent.ACTION_EDIT, intent.action)
        assertEquals(
            "content://com.android.calendar/events/42",
            intent.data.toString()
        )
    }
}
