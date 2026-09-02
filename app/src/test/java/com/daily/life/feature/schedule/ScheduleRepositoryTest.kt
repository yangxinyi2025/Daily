package com.daily.life.feature.schedule

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.notification.AlarmReminderSpec
import com.daily.life.core.notification.ReminderScheduleDecision
import com.daily.life.core.notification.ReminderScheduleStatus
import com.daily.life.core.notification.ReminderScheduler
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ReminderMode
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleRepositoryTest {
    private lateinit var database: DailyDatabase
    private lateinit var repository: RoomScheduleRepository

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        repository = RoomScheduleRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createUpdateDeleteAndObserveDateRange() = runTest {
        val createdId = repository.create(eventAt("2026-08-21T10:00:00+08:00"))

        val observed = repository.observeBetween(
            startInclusive = instantAt("2026-08-21T00:00:00+08:00"),
            endInclusive = instantAt("2026-08-21T23:59:59+08:00")
        ).first()
        assertEquals(listOf(createdId), observed.map(ScheduleEvent::id))

        val original = observed.single()
        repository.update(original.copy(title = "已更新"))
        assertEquals("已更新", repository.findById(createdId)?.title)

        repository.delete(createdId)
        assertTrue(repository.observeBetween(Instant.EPOCH, Instant.ofEpochMilli(Long.MAX_VALUE)).first().isEmpty())
    }

    @Test
    fun yearlyBirthdayUsesNextOccurrenceAfterReferenceDate() {
        val event = eventAt("2025-08-21T09:00:00+08:00").copy(repeatYearly = true)

        assertEquals(
            LocalDate.of(2026, 8, 21),
            ScheduleRepository.nextOccurrence(event, LocalDate.of(2026, 1, 1))
        )
    }

    @Test
    fun alarmScheduleUsesDailyExactAlarmAndNotTheSystemCalendar() = runTest {
        val scheduler = RecordingAlarmScheduler()
        repository = RoomScheduleRepository(database, reminderScheduler = scheduler)
        val event = eventAt("2026-08-21T10:00:00+08:00").copy(
            reminderMode = ReminderMode.ALARM,
            reminderOffsetMinutes = 10
        )

        repository.create(event)

        assertEquals(event.eventAt.minusSeconds(10 * 60L), scheduler.scheduled.single().triggerAt)
        assertEquals(event.eventAt, scheduler.scheduled.single().eventAt)
    }

    private fun eventAt(value: String): ScheduleEvent =
        ScheduleEvent(
            title = "生日",
            eventAt = instantAt(value),
            reminderOffsetMinutes = 20,
            reminderMode = ReminderMode.NOTIFICATION,
            repeatYearly = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

    private fun instantAt(value: String): Instant = OffsetDateTime.parse(value).toInstant()

    private class RecordingAlarmScheduler : ReminderScheduler {
        val scheduled = mutableListOf<AlarmReminderSpec>()

        override suspend fun schedule(reminder: AlarmReminderSpec): ReminderScheduleDecision {
            scheduled += reminder
            return ReminderScheduleDecision(ReminderScheduleStatus.SCHEDULED, reminder.triggerAt)
        }
    }
}
