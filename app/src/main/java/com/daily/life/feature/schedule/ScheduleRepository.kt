package com.daily.life.feature.schedule

import com.daily.life.core.database.DailyDatabase
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
    private val clock: java.time.Clock = java.time.Clock.systemDefaultZone()
) : ScheduleRepository {
    private val dao = database.scheduleEventDao()
    private val zone = clock.zone
    private val _lastReminderStatus = MutableStateFlow<ReminderScheduleStatus?>(null)
    override val lastReminderStatus: ReminderScheduleStatus?
        get() = _lastReminderStatus.value

    override suspend fun create(event: ScheduleEvent): Long {
        val id = dao.insert(event.toEntity().copy(id = 0L))
        _lastReminderStatus.value = reminderScheduler?.schedule(event.copy(id = id))?.status
        return id
    }

    override suspend fun update(event: ScheduleEvent) {
        dao.update(event.toEntity())
        _lastReminderStatus.value = reminderScheduler?.schedule(event)?.status
    }

    override suspend fun delete(id: Long) {
        reminderScheduler?.cancel(id)
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
}
