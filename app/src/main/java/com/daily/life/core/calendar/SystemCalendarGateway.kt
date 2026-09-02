package com.daily.life.core.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.ZoneId

class SystemCalendarGateway(
    private val client: CalendarProviderClient
) {
    fun upsert(existingEventId: Long?, request: CalendarReminderRequest): CalendarGatewayResult {
        return try {
        if (!client.hasReadWritePermission()) return CalendarGatewayResult.PermissionDenied
        val calendar = client.writableCalendars()
            .filter { it.canWrite && it.isVisible }
            .sortedWith(compareByDescending<CalendarTarget> { it.isPrimary }.thenBy { it.id })
            .firstOrNull()
            ?: client.createDailyLocalCalendar()
            ?: return CalendarGatewayResult.NoWritableCalendar
        val eventId = existingEventId?.takeIf { client.updateEvent(it, request) }
            ?: client.insertEvent(calendar.id, request)
        val reminderSaved = request.reminderMinutes?.let { minutes ->
            client.replaceAlertReminder(eventId, minutes.coerceAtLeast(0), request.reminderMethod)
        } ?: client.clearReminders(eventId)
        if (eventId <= 0L || !reminderSaved
        ) {
            CalendarGatewayResult.ProviderFailure("系统日历未能保存提醒")
        } else {
            CalendarGatewayResult.Synced(calendar.id, eventId)
        }
    } catch (error: SecurityException) {
        CalendarGatewayResult.PermissionDenied
    } catch (error: IllegalArgumentException) {
        CalendarGatewayResult.ProviderFailure(error.message ?: "系统日历参数无效")
    } catch (error: RuntimeException) {
        CalendarGatewayResult.ProviderFailure(error.message ?: "系统日历拒绝创建或写入事件")
        }
    }

    fun delete(eventId: Long): CalendarGatewayResult {
        return try {
        if (!client.hasReadWritePermission()) return CalendarGatewayResult.PermissionDenied
        if (client.deleteEvent(eventId)) {
            CalendarGatewayResult.Synced(calendarId = -1L, eventId = eventId)
        } else {
            CalendarGatewayResult.ProviderFailure("系统日历未能删除事件")
        }
    } catch (error: SecurityException) {
        CalendarGatewayResult.PermissionDenied
    } catch (error: IllegalArgumentException) {
        CalendarGatewayResult.ProviderFailure(error.message ?: "系统日历参数无效")
    } catch (error: RuntimeException) {
        CalendarGatewayResult.ProviderFailure(error.message ?: "系统日历拒绝删除事件")
        }
    }
}

class AndroidCalendarProviderClient(context: Context) : CalendarProviderClient {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override fun hasReadWritePermission(): Boolean = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    ).all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun writableCalendars(): List<CalendarTarget> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        return resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
            ?.use { cursor ->
                buildList {
                    val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                    val primaryIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
                    val accessIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                    val visibleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
                    while (cursor.moveToNext()) {
                        add(
                            CalendarTarget(
                                id = cursor.getLong(idIndex),
                                isPrimary = cursor.getInt(primaryIndex) == 1,
                                canWrite = cursor.getInt(accessIndex) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                                isVisible = cursor.getInt(visibleIndex) == 1
                            )
                        )
                    }
                }
            }
            .orEmpty()
    }

    override fun createDailyLocalCalendar(): CalendarTarget? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, DAILY_CALENDAR_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, DAILY_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, DAILY_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, DAILY_CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
            put(
                CalendarContract.Calendars.ALLOWED_REMINDERS,
                listOf(
                    CalendarContract.Reminders.METHOD_DEFAULT,
                    CalendarContract.Reminders.METHOD_ALERT
                ).joinToString(",")
            )
            put(CalendarContract.Calendars.MAX_REMINDERS, 1)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, DAILY_CALENDAR_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val syncAdapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, DAILY_CALENDAR_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val id = resolver.insert(syncAdapterUri, values)?.lastPathSegment?.toLongOrNull() ?: return null
        return CalendarTarget(id = id, isPrimary = false, canWrite = true, isVisible = true)
    }

    override fun insertEvent(calendarId: Long, request: CalendarReminderRequest): Long =
        requireNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, eventValues(calendarId, request))) {
            "系统日历没有返回事件 ID"
        }.lastPathSegment?.toLongOrNull() ?: 0L

    override fun updateEvent(eventId: Long, request: CalendarReminderRequest): Boolean =
        resolver.update(
            CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(eventId.toString()).build(),
            eventValues(calendarId = null, request),
            null,
            null
        ) > 0

    override fun replaceAlertReminder(eventId: Long, minutes: Int, method: CalendarAlertMethod): Boolean {
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        return resolver.insert(CalendarContract.Reminders.CONTENT_URI, values) != null
    }

    override fun clearReminders(eventId: Long): Boolean = resolver.delete(
        CalendarContract.Reminders.CONTENT_URI,
        "${CalendarContract.Reminders.EVENT_ID} = ?",
        arrayOf(eventId.toString())
    ) >= 0

    override fun deleteEvent(eventId: Long): Boolean = resolver.delete(
        CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(eventId.toString()).build(),
        null,
        null
    ) > 0

    private fun eventValues(calendarId: Long?, request: CalendarReminderRequest) = ContentValues().apply {
        calendarId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }
        put(CalendarContract.Events.TITLE, "Daily｜${request.title}")
        put(CalendarContract.Events.DESCRIPTION, request.description)
        put(CalendarContract.Events.DTSTART, request.startAt.toEpochMilli())
        put(CalendarContract.Events.DTEND, request.endAt.toEpochMilli())
        put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        if (request.repeatYearly) put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
    }

    private companion object {
        const val DAILY_CALENDAR_NAME = "Daily"
        const val DAILY_CALENDAR_COLOR = 0xFF8BB7F0.toInt()
    }
}
