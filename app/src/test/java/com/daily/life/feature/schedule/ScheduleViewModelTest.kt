package com.daily.life.feature.schedule

import com.daily.life.core.notification.ReminderScheduleStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleViewModelTest {
    @Test
    fun browsingMonthLoadsGridAndSelectedDaySeparatelyThenMergesEventsById() = runTest {
        val repository = RecordingScheduleRepository(
            initialEvents = listOf(
                scheduleEvent(1, "Grid event", "2026-10-01T09:00:00Z"),
                scheduleEvent(2, "Selected event", "2026-09-01T09:00:00Z"),
                scheduleEvent(3, "Grid duplicate", "2026-10-02T09:00:00Z"),
                scheduleEvent(3, "Selected duplicate", "2026-09-01T10:00:00Z"),
                scheduleEvent(4, "Outside grid", "2026-11-09T09:00:00Z")
            )
        )
        val viewModel = ScheduleViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        runCurrent()
        repository.clearObservedRanges()
        viewModel.browseMonth(YearMonth.of(2026, 10))
        runCurrent()

        assertEquals(
            setOf(
                QueryRange(Instant.parse("2026-09-28T00:00:00Z"), Instant.parse("2026-11-08T23:59:59.999Z")),
                QueryRange(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T23:59:59.999Z"))
            ),
            repository.observedRanges.toSet()
        )
        assertEquals(listOf(2L, 3L, 1L), viewModel.state.value.events.map(ScheduleEvent::id))
        assertEquals("Selected duplicate", viewModel.state.value.events.first { it.id == 3L }.title)
        assertEquals(listOf(2L, 3L), viewModel.state.value.selectedDateEvents.map(ScheduleEvent::id))
    }

    @Test
    fun weekAndDayModesKeepTheirExistingQueryWindows() = runTest {
        val repository = RecordingScheduleRepository(
            initialEvents = listOf(
                scheduleEvent(1, "Week start", "2026-08-31T09:00:00Z"),
                scheduleEvent(2, "Selected day", "2026-09-01T09:00:00Z"),
                scheduleEvent(3, "Next week", "2026-09-07T09:00:00Z")
            )
        )
        val viewModel = ScheduleViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        runCurrent()
        repository.clearObservedRanges()
        viewModel.setViewMode(ScheduleViewMode.WEEK)
        runCurrent()

        assertEquals(
            setOf(QueryRange(Instant.parse("2026-08-31T00:00:00Z"), Instant.parse("2026-09-06T23:59:59.999Z"))),
            repository.observedRanges.toSet()
        )
        assertEquals(listOf(1L, 2L), viewModel.state.value.events.map(ScheduleEvent::id))

        repository.clearObservedRanges()
        viewModel.setViewMode(ScheduleViewMode.DAY)
        runCurrent()

        assertEquals(
            setOf(QueryRange(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T23:59:59.999Z"))),
            repository.observedRanges.toSet()
        )
        assertEquals(listOf(2L), viewModel.state.value.events.map(ScheduleEvent::id))
    }

    @Test
    fun browsingMonthKeepsTheBusinessDateSelected() = runTest {
        val selected = LocalDate.of(2026, 9, 1)
        val viewModel = ScheduleViewModel(
            repository = RecordingScheduleRepository(),
            clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        viewModel.selectDate(selected)
        viewModel.browseMonth(YearMonth.of(2026, 10))

        assertEquals(selected, viewModel.state.value.selectedDate)
        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.visibleMonth)
    }

    @Test
    fun editingDoesNotWriteUntilExplicitSave() = runTest {
        val repository = RecordingScheduleRepository()
        val viewModel = ScheduleViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        viewModel.startCreate()
        viewModel.updateTitle("考试")
        viewModel.updateNotes("带准考证")

        assertTrue(repository.created.isEmpty())
        assertEquals("考试", viewModel.state.value.editor?.title)

        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals(listOf("考试"), repository.created.map(ScheduleEvent::title))
        assertEquals("带准考证", repository.created.single().notes)
    }

    @Test
    fun failedSystemCalendarSyncDoesNotOfferTheOldDailyAlarmPermission() = runTest {
        val repository = RecordingScheduleRepository().apply {
            reminderStatus = ReminderScheduleStatus.PERMISSION_RESTRICTED
        }
        val viewModel = ScheduleViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        viewModel.startCreate()
        viewModel.updateTitle("考试")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals("日程已保存，但系统日历提醒未同步", viewModel.state.value.statusMessage)
    }

    @Test
    fun savingAnOldAlarmDraftUsesMessageReminder() = runTest {
        val repository = RecordingScheduleRepository().apply {
            reminderStatus = ReminderScheduleStatus.SCHEDULED
        }
        val viewModel = ScheduleViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        viewModel.startCreate()
        viewModel.updateTitle("考试")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals("日程已保存", viewModel.state.value.statusMessage)
        assertEquals(com.daily.life.core.database.ReminderMode.NOTIFICATION, repository.created.single().reminderMode)
        assertEquals(null, viewModel.state.value.calendarEventIdToEdit)
    }

    private data class QueryRange(
        val startInclusive: Instant,
        val endInclusive: Instant
    )

    private class RecordingScheduleRepository(
        initialEvents: List<ScheduleEvent> = emptyList()
    ) : ScheduleRepository {
        val created = mutableListOf<ScheduleEvent>()
        val observedRanges = mutableListOf<QueryRange>()
        var reminderStatus: ReminderScheduleStatus? = null
        var calendarEventId: Long? = 42L
        private val events = MutableStateFlow(initialEvents)

        override val lastReminderStatus: ReminderScheduleStatus?
            get() = reminderStatus

        override suspend fun create(event: ScheduleEvent): Long {
            created += event
            val id = created.size.toLong()
            events.value += event.copy(id = id)
            return id
        }

        override suspend fun update(event: ScheduleEvent) = Unit

        override suspend fun delete(id: Long) = Unit

        override fun observeBetween(startInclusive: Instant, endInclusive: Instant): Flow<List<ScheduleEvent>> {
            observedRanges += QueryRange(startInclusive, endInclusive)
            return events.map { scheduled ->
                scheduled.filter { event -> event.eventAt in startInclusive..endInclusive }.sortedBy(ScheduleEvent::eventAt)
            }
        }

        fun clearObservedRanges() {
            observedRanges.clear()
        }

        override suspend fun findById(id: Long): ScheduleEvent? = events.value.firstOrNull { it.id == id }

        override suspend fun systemCalendarEventId(eventId: Long): Long? = calendarEventId
    }

    private fun scheduleEvent(id: Long, title: String, eventAt: String): ScheduleEvent = ScheduleEvent(
        id = id,
        title = title,
        eventAt = Instant.parse(eventAt),
        reminderOffsetMinutes = 0,
        reminderMode = com.daily.life.core.database.ReminderMode.NOTIFICATION,
        repeatYearly = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
