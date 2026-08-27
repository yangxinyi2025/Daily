package com.daily.life.feature.schedule

import com.daily.life.core.notification.ReminderScheduleStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleViewModelTest {
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

    private class RecordingScheduleRepository : ScheduleRepository {
        val created = mutableListOf<ScheduleEvent>()
        var reminderStatus: ReminderScheduleStatus? = null
        var calendarEventId: Long? = 42L
        private val events = MutableStateFlow<List<ScheduleEvent>>(emptyList())

        override val lastReminderStatus: ReminderScheduleStatus?
            get() = reminderStatus

        override suspend fun create(event: ScheduleEvent): Long {
            created += event
            val id = created.size.toLong()
            events.value = created.mapIndexed { index, value -> value.copy(id = index + 1L) }
            return id
        }

        override suspend fun update(event: ScheduleEvent) = Unit

        override suspend fun delete(id: Long) = Unit

        override fun observeBetween(startInclusive: Instant, endInclusive: Instant): Flow<List<ScheduleEvent>> =
            events

        override suspend fun findById(id: Long): ScheduleEvent? = events.value.firstOrNull { it.id == id }

        override suspend fun systemCalendarEventId(eventId: Long): Long? = calendarEventId
    }
}
