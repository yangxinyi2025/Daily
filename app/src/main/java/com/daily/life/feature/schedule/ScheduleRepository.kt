package com.daily.life.feature.schedule

import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.calendar.CalendarGatewayResult
import com.daily.life.core.calendar.CalendarReminderSyncer
import com.daily.life.core.calendar.ReminderRouting
import com.daily.life.core.notification.AlarmReminderSpec
import com.daily.life.core.notification.ReminderScheduler
import com.daily.life.core.notification.ReminderScheduleStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

interface ScheduleRepository {
    val lastReminderStatus: ReminderScheduleStatus?
        get() = null
    suspend fun create(event: ScheduleEvent): Long
    suspend fun update(event: ScheduleEvent)
    suspend fun delete(id: Long)
    fun observeBetween(startInclusive: Instant, endInclusive: Instant): Flow<List<ScheduleEvent>>
    suspend fun findById(id: Long): ScheduleEvent? = null
    suspend fun systemCalendarEventId(eventId: Long): Long? = null
    suspend fun findAll(): List<ScheduleEvent> = emptyList()
    suspend fun createYearlyInstancesIfNeeded(now: Instant): List<ScheduleEvent> = emptyList()

    companion object {
        fun nextOccurrence(event: ScheduleEvent, referenceDate: LocalDate): LocalDate {
            val zone = ZoneId.systemDefault()
            val original = event.eventAt.atZone(zone).toLocalDate()
            fun occurrence(year: Int): LocalDate {
                val month = YearMonth.of(year, original.month)
                return month.atDay(minOf(original.dayOfMonth, month.lengthOfMonth()))
            }
            val candidate = occurrence(referenceDate.year)
            return if (candidate.isBefore(referenceDate)) occurrence(referenceDate.year + 1) else candidate
        }
    }
}

class RoomScheduleRepository(
    private val database: DailyDatabase,
    private val reminderScheduler: ReminderScheduler? = null,
    private val calendarReminderSyncer: CalendarReminderSyncer? = null,
    private val clock: java.time.Clock = java.time.Clock.systemDefaultZone()
) : ScheduleRepository {
    private val dao = database.scheduleEventDao()
    private val zone = clock.zone
    private val _lastReminderStatus = MutableStateFlow<ReminderScheduleStatus?>(null)
    override val lastReminderStatus: ReminderScheduleStatus?
        get() = _lastReminderStatus.value

    override suspend fun create(event: ScheduleEvent): Long {
        val id = dao.insert(event.toEntity().copy(id = 0L))
        scheduleReminder(event.copy(id = id))
        return id
    }

    override suspend fun update(event: ScheduleEvent) {
        dao.findById(event.id)?.toModel()?.let { previous -> cancelReminder(previous) }
        dao.update(event.toEntity())
        scheduleReminder(event)
    }

    override suspend fun delete(id: Long) {
        dao.findById(id)?.toModel()?.let { previous -> cancelReminder(previous) }
        dao.deleteById(id)
    }

    override fun observeBetween(startInclusive: Instant, endInclusive: Instant): Flow<List<ScheduleEvent>> =
        dao.observeAll().map { entities ->
            entities
                .flatMap { entity ->
                    val event = entity.toModel()
                    if (event.repeatYearly) {
                        yearlyOccurrences(event, startInclusive, endInclusive)
                    } else {
                        listOf(event)
                    }
                }
                .filter { it.eventAt in startInclusive..endInclusive }
                .sortedBy(ScheduleEvent::eventAt)
        }

    override suspend fun findById(id: Long): ScheduleEvent? = dao.findById(id)?.toModel()

    override suspend fun systemCalendarEventId(eventId: Long): Long? =
        calendarReminderSyncer?.scheduleCalendarEventId(eventId)

    override suspend fun findAll(): List<ScheduleEvent> = dao.findAll().map { it.toModel() }

    override suspend fun createYearlyInstancesIfNeeded(now: Instant): List<ScheduleEvent> =
        findAll().filter(ScheduleEvent::repeatYearly).map { event -> occurrenceFor(event, now) }

    private fun yearlyOccurrences(
        event: ScheduleEvent,
        startInclusive: Instant,
        endInclusive: Instant
    ): List<ScheduleEvent> {
        val startYear = startInclusive.atZone(zone).year - 1
        val endYear = endInclusive.atZone(zone).year + 1
        return (startYear..endYear).map { year ->
            val date = event.eventAt.atZone(zone).toLocalDate()
            val month = YearMonth.of(year, date.month)
            val adjustedDate = month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))
            val localTime = event.eventAt.atZone(zone).toLocalTime()
            event.copy(eventAt = adjustedDate.atTime(localTime).atZone(zone).toInstant())
        }
    }

    private fun occurrenceFor(event: ScheduleEvent, now: Instant): ScheduleEvent {
        val date = ScheduleRepository.nextOccurrence(event, now.atZone(zone).toLocalDate())
        val time = event.eventAt.atZone(zone).toLocalTime()
        return event.copy(eventAt = date.atTime(time).atZone(zone).toInstant())
    }

    private suspend fun scheduleReminder(event: ScheduleEvent) {
        when {
            ReminderRouting.usesDailyAlarm(event) -> {
                calendarReminderSyncer?.deleteSchedule(event.id)
                val scheduler = reminderScheduler
                _lastReminderStatus.value = if (scheduler == null) {
                    ReminderScheduleStatus.PERMISSION_RESTRICTED
                } else {
                    scheduler.schedule(
                        AlarmReminderSpec(
                            id = event.id,
                            title = event.title,
                            triggerAt = event.eventAt.minusSeconds(event.reminderOffsetMinutes.coerceAtLeast(0) * 60L),
                            eventAt = event.eventAt,
                            notes = event.notes
                        )
                    ).status
                }
            }
            ReminderRouting.usesSystemCalendar(event) && calendarReminderSyncer != null -> {
                reminderScheduler?.cancel(event.id)
                _lastReminderStatus.value = when (calendarReminderSyncer.syncSchedule(event)) {
                    is CalendarGatewayResult.Synced -> ReminderScheduleStatus.SCHEDULED
                    else -> ReminderScheduleStatus.PERMISSION_RESTRICTED
                }
            }
            else -> _lastReminderStatus.value = null
        }
    }

    private suspend fun cancelReminder(event: ScheduleEvent) {
        when {
            ReminderRouting.usesDailyAlarm(event) -> reminderScheduler?.cancel(event.id)
            ReminderRouting.usesSystemCalendar(event) -> calendarReminderSyncer?.deleteSchedule(event.id)
        }
    }
}
