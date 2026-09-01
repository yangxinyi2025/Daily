package com.daily.life.core.calendar

import com.daily.life.core.database.CalendarSyncKind
import com.daily.life.core.database.CalendarSyncLinkEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.database.CourseEntity
import com.daily.life.feature.schedule.ScheduleEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

object ReminderRouting {
    fun isBirthday(event: ScheduleEvent): Boolean = event.repeatYearly

    fun usesSystemCalendar(event: ScheduleEvent): Boolean =
        event.reminderMode == com.daily.life.core.database.ReminderMode.NOTIFICATION

    fun usesCalendarMessage(event: ScheduleEvent): Boolean = usesSystemCalendar(event)

    fun usesDailyAlarm(event: ScheduleEvent): Boolean =
        event.reminderMode == com.daily.life.core.database.ReminderMode.ALARM
}

class CalendarReminderSyncer(
    private val database: DailyDatabase,
    private val gateway: SystemCalendarGateway
) {
    private val links = database.calendarSyncLinkDao()

    suspend fun scheduleCalendarEventId(scheduleEventId: Long): Long? =
        links.find(CalendarSyncKind.SCHEDULE_EVENT, scheduleEventId.toString())?.eventId

    suspend fun syncSchedule(event: ScheduleEvent, includeCalendarReminder: Boolean = true): CalendarGatewayResult {
        val key = event.id.toString()
        val existing = links.find(CalendarSyncKind.SCHEDULE_EVENT, key)
        val result = gateway.upsert(
            existingEventId = existing?.eventId,
            request = CalendarReminderRequest(
                title = event.title,
                description = event.notes,
                startAt = event.eventAt,
                endAt = event.eventAt.plus(Duration.ofMinutes(30)),
                reminderMinutes = event.reminderOffsetMinutes,
                reminderMethod = CalendarAlertMethod.ALERT,
                repeatYearly = ReminderRouting.isBirthday(event)
            )
        )
        if (result is CalendarGatewayResult.Synced) {
            links.upsert(
                CalendarSyncLinkEntity(
                    ownerKind = CalendarSyncKind.SCHEDULE_EVENT,
                    ownerKey = key,
                    calendarId = result.calendarId,
                    eventId = result.eventId,
                    eventStartAt = event.eventAt.toEpochMilli()
                )
            )
        }
        return result
    }

    suspend fun deleteSchedule(eventId: Long): CalendarGatewayResult? {
        val key = eventId.toString()
        val existing = links.find(CalendarSyncKind.SCHEDULE_EVENT, key) ?: return null
        val result = gateway.delete(existing.eventId)
        if (result is CalendarGatewayResult.Synced) {
            links.delete(CalendarSyncKind.SCHEDULE_EVENT, key)
        }
        return result
    }

    suspend fun syncCourseOccurrence(
        course: CourseEntity,
        week: Int,
        actualDate: LocalDate,
        startAt: Instant,
        endAt: Instant,
        reminderMinutes: Int
    ): CalendarGatewayResult {
        val key = "${course.id}:$week:$actualDate"
        val existing = links.find(CalendarSyncKind.COURSE_OCCURRENCE, key)
        val result = gateway.upsert(
            existingEventId = existing?.eventId,
            request = CalendarReminderRequest(
                title = "课程：${course.courseName}",
                description = listOfNotNull(course.teacher, course.location).joinToString(" · ").ifBlank { null },
                startAt = startAt,
                endAt = endAt,
                reminderMinutes = reminderMinutes,
                repeatYearly = false
            )
        )
        if (result is CalendarGatewayResult.Synced) {
            links.upsert(
                CalendarSyncLinkEntity(
                    ownerKind = CalendarSyncKind.COURSE_OCCURRENCE,
                    ownerKey = key,
                    calendarId = result.calendarId,
                    eventId = result.eventId,
                    eventStartAt = startAt.toEpochMilli()
                )
            )
        }
        return result
    }

    suspend fun deleteCourseOccurrences(courseIds: Collection<Long>): List<CalendarGatewayResult> {
        if (courseIds.isEmpty()) return emptyList()
        val courseIdSet = courseIds.toSet()
        return deleteCourseOccurrenceLinks { link ->
            link.ownerKey.substringBefore(':').toLongOrNull() in courseIdSet
        }
    }

    suspend fun deleteAllCourseOccurrences(): List<CalendarGatewayResult> =
        deleteCourseOccurrenceLinks { true }

    private suspend fun deleteCourseOccurrenceLinks(
        shouldDelete: (CalendarSyncLinkEntity) -> Boolean
    ): List<CalendarGatewayResult> =
        links.findByKind(CalendarSyncKind.COURSE_OCCURRENCE)
            .filter(shouldDelete)
            .map { link ->
                val result = gateway.delete(link.eventId)
                if (result is CalendarGatewayResult.Synced) {
                    links.delete(CalendarSyncKind.COURSE_OCCURRENCE, link.ownerKey)
                }
                result
            }
}
