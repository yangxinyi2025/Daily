package com.daily.life.core.calendar

import java.time.Instant

data class CalendarTarget(
    val id: Long,
    val isPrimary: Boolean,
    val canWrite: Boolean,
    val isVisible: Boolean
)

data class CalendarReminderRequest(
    val title: String,
    val description: String?,
    val startAt: Instant,
    val endAt: Instant,
    val reminderMinutes: Int?,
    val reminderMethod: CalendarAlertMethod = CalendarAlertMethod.ALERT,
    val repeatYearly: Boolean
)

enum class CalendarAlertMethod { ALERT }

sealed interface CalendarGatewayResult {
    data class Synced(val calendarId: Long, val eventId: Long) : CalendarGatewayResult
    data object PermissionDenied : CalendarGatewayResult
    data object NoWritableCalendar : CalendarGatewayResult
    data class ProviderFailure(val message: String) : CalendarGatewayResult
}

interface CalendarProviderClient {
    fun hasReadWritePermission(): Boolean
    fun writableCalendars(): List<CalendarTarget>
    fun createDailyLocalCalendar(): CalendarTarget? = null
    fun insertEvent(calendarId: Long, request: CalendarReminderRequest): Long
    fun updateEvent(eventId: Long, request: CalendarReminderRequest): Boolean
    fun replaceAlertReminder(eventId: Long, minutes: Int, method: CalendarAlertMethod): Boolean
    fun clearReminders(eventId: Long): Boolean = true
    fun deleteEvent(eventId: Long): Boolean
}
