package com.daily.life.core.calendar

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract

object SystemCalendarEventEditor {
    fun intentFor(eventId: Long): Intent = Intent(Intent.ACTION_EDIT).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    }
}
