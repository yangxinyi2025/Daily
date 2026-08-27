package com.daily.life.core.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemCalendarScheduleReader internal constructor(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val canReadCalendar: () -> Boolean,
    private val queryEvents: suspend (startMillis: Long, endMillis: Long) -> List<SystemCalendarScheduleEvent>
) {
    fun hasReadPermission(): Boolean = canReadCalendar()

    suspend fun readBetween(startDate: LocalDate, endDate: LocalDate): List<SystemCalendarSpecialDay> =
        withContext(Dispatchers.IO) {
            if (endDate.isBefore(startDate) || !canReadCalendar()) return@withContext emptyList()
            val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val events = runCatching { queryEvents(startMillis, endMillis) }.getOrDefault(emptyList())
            mergeSystemCalendarSpecialDays(events.flatMap { event ->
                parseSystemCalendarSpecialDay(event, zone)
            })
        }

    companion object {
        fun from(context: Context): SystemCalendarScheduleReader {
            val appContext = context.applicationContext
            return SystemCalendarScheduleReader(
                canReadCalendar = {
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.READ_CALENDAR
                    ) == PackageManager.PERMISSION_GRANTED
                },
                queryEvents = { startMillis, endMillis ->
                    queryInstances(appContext.contentResolver, startMillis, endMillis)
                }
            )
        }

        private fun queryInstances(
            contentResolver: ContentResolver,
            startMillis: Long,
            endMillis: Long
        ): List<SystemCalendarScheduleEvent> {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { builder ->
                ContentUris.appendId(builder, startMillis)
                ContentUris.appendId(builder, endMillis)
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION
            )
            return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            SystemCalendarScheduleEvent(
                                startAt = java.time.Instant.ofEpochMilli(cursor.getLong(beginIndex)),
                                endAt = java.time.Instant.ofEpochMilli(cursor.getLong(endIndex)),
                                title = cursor.getString(titleIndex),
                                description = cursor.getString(descriptionIndex)
                            )
                        )
                    }
                }
            }.orEmpty()
        }
    }
}
