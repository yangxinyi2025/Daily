package com.daily.life.core.calendar

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemCalendarGatewayTest {
    @Test
    fun upsertUsesPrimaryWritableCalendarAndCreatesAlertAtRequestedOffset() {
        val client = FakeCalendarProviderClient(
            calendars = listOf(CalendarTarget(id = 7L, isPrimary = true, canWrite = true, isVisible = true))
        )

        val result = SystemCalendarGateway(client).upsert(existingEventId = null, request(reminderMinutes = 15))

        assertEquals(CalendarGatewayResult.Synced(calendarId = 7L, eventId = 42L), result)
        assertEquals(15, client.insertedReminderMinutes)
        assertEquals(CalendarAlertMethod.ALERT, client.insertedReminderMethod)
    }

    @Test
    fun upsertReturnsNoWritableCalendarWhenAllCalendarsAreReadOnly() {
        val client = FakeCalendarProviderClient(
            calendars = listOf(CalendarTarget(id = 3L, isPrimary = true, canWrite = false, isVisible = true))
        )

        assertEquals(CalendarGatewayResult.NoWritableCalendar, SystemCalendarGateway(client).upsert(null, request()))
    }

    @Test
    fun upsertCreatesDailyLocalCalendarWhenNoWritableCalendarExists() {
        val client = FakeCalendarProviderClient(
            calendars = emptyList(),
            localCalendar = CalendarTarget(id = 88L, isPrimary = false, canWrite = true, isVisible = true)
        )

        val result = SystemCalendarGateway(client).upsert(null, request())

        assertEquals(CalendarGatewayResult.Synced(calendarId = 88L, eventId = 42L), result)
        assertEquals("Daily", client.createdLocalCalendarName)
    }

    @Test
    fun upsertReturnsProviderFailureWhenTheDeviceRejectsDailyLocalCalendarCreation() {
        val client = FakeCalendarProviderClient(
            calendars = emptyList(),
            localCalendarCreationError = UnsupportedOperationException("不支持本地日历")
        )

        assertEquals(
            CalendarGatewayResult.ProviderFailure("不支持本地日历"),
            SystemCalendarGateway(client).upsert(null, request())
        )
    }

    private fun request(
        reminderMinutes: Int = 10,
        reminderMethod: CalendarAlertMethod = CalendarAlertMethod.ALERT
    ) = CalendarReminderRequest(
        title = "考试",
        description = "教室 A101",
        startAt = Instant.parse("2026-09-15T08:00:00Z"),
        endAt = Instant.parse("2026-09-15T08:30:00Z"),
        reminderMinutes = reminderMinutes,
        reminderMethod = reminderMethod,
        repeatYearly = false
    )
}

private class FakeCalendarProviderClient(
    private val calendars: List<CalendarTarget>,
    private val localCalendar: CalendarTarget? = null,
    private val localCalendarCreationError: RuntimeException? = null
) : CalendarProviderClient {
    var insertedReminderMinutes: Int? = null
    var insertedReminderMethod: CalendarAlertMethod? = null
    var createdLocalCalendarName: String? = null

    override fun hasReadWritePermission(): Boolean = true

    override fun writableCalendars(): List<CalendarTarget> = calendars

    override fun createDailyLocalCalendar(): CalendarTarget? {
        createdLocalCalendarName = "Daily"
        localCalendarCreationError?.let { throw it }
        return localCalendar
    }

    override fun insertEvent(calendarId: Long, request: CalendarReminderRequest): Long = 42L

    override fun updateEvent(eventId: Long, request: CalendarReminderRequest): Boolean = true

    override fun replaceAlertReminder(eventId: Long, minutes: Int, method: CalendarAlertMethod): Boolean {
        insertedReminderMinutes = minutes
        insertedReminderMethod = method
        return true
    }

    override fun deleteEvent(eventId: Long): Boolean = true
}
