package com.daily.life.feature.schedule

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.HolidayCalendarRepository
import com.daily.life.core.calendar.IcsCalendarFetcher
import com.daily.life.core.calendar.IcsCalendarSource
import com.daily.life.core.calendar.IcsFetchResult
import com.daily.life.core.calendar.SystemCalendarScheduleReader
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.HolidayCalendarEventEntity
import com.daily.life.core.database.HolidayCalendarSourceEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleHolidayOverrideTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences
    private lateinit var preferenceFile: File
    private lateinit var repository: HolidayCalendarRepository

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        preferenceFile = File(
            System.getProperty("java.io.tmpdir"),
            "daily-schedule-holiday-${System.nanoTime()}.preferences_pb"
        )
        preferences = DailyPreferences.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            produceFile = { preferenceFile }
        )
        repository = HolidayCalendarRepository(
            dao = database.holidayCalendarDao(),
            preferences = preferences,
            systemCalendarReader = SystemCalendarScheduleReader(ZoneId.of("Asia/Shanghai"), { false }) { _, _ -> emptyList() },
            icsClient = object : IcsCalendarFetcher {
                override fun fetch(
                    source: IcsCalendarSource,
                    etag: String?,
                    lastModified: String?
                ): IcsFetchResult = IcsFetchResult.NotModified
            },
            now = { Instant.parse("2026-08-28T08:00:00Z") }
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::preferenceFile.isInitialized) preferenceFile.delete()
    }

    @Test
    fun rangeOverrideExpandsToOneRowPerDateAndCanBeCleared() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.first { it.calendarRules.isNotEmpty() }
        val start = LocalDate.of(2026, 8, 3)
        val end = LocalDate.of(2026, 8, 5)

        viewModel.saveCalendarDayOverride(start, end, CalendarDayKind.HOLIDAY_REST, "暑假")
        viewModel.state.first { state ->
            state.calendarRules.filter { it.date in start..end }.all { it.sourceLabel == "手动修正" }
        }

        val stored = database.holidayCalendarDao().observeDayOverridesBetween(start, end).first()
        assertEquals(listOf(start, start.plusDays(1), end), stored.map { it.date })
        assertTrue(viewModel.state.value.calendarRules.filter { it.date in start..end }.all { it.sourceLabel == "手动修正" })

        viewModel.clearCalendarDayOverrides(listOf(start.plusDays(1), end))
        viewModel.state.first { state ->
            state.calendarRules.any { it.date == start && it.sourceLabel == "手动修正" } &&
                state.calendarRules.any { it.date == end && it.kind == CalendarDayKind.REGULAR_WORKDAY }
        }

        val remaining = database.holidayCalendarDao().observeDayOverridesBetween(start, end).first()
        assertEquals(listOf(start), remaining.map { it.date })
        val latestRules = viewModel.state.value.calendarRules.associateBy { it.date }
        assertEquals(CalendarDayKind.HOLIDAY_REST, latestRules.getValue(start).kind)
        assertEquals(CalendarDayKind.REGULAR_WORKDAY, latestRules.getValue(start.plusDays(1)).kind)
        assertEquals(CalendarDayKind.REGULAR_WORKDAY, latestRules.getValue(end).kind)
    }

    @Test
    fun restoringAutomaticRuleRemovesManualOverrideAndFallsBackToIcs() = runTest {
        val targetDate = LocalDate.of(2026, 10, 1)
        database.holidayCalendarDao().insertSourceIfMissing(
            HolidayCalendarSourceEntity(
                id = "custom-ics",
                name = "订阅源",
                url = "https://example.com/china.ics",
                builtIn = false,
                enabled = true
            )
        )
        database.holidayCalendarDao().insertEvents(
            listOf(
                HolidayCalendarEventEntity(
                    sourceId = "custom-ics",
                    eventKey = "oct-1",
                    startDate = targetDate,
                    endDateInclusive = targetDate,
                    summary = "国庆节",
                    kind = CalendarDayKind.HOLIDAY_REST,
                    fetchedAt = 1L
                )
            )
        )
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.first { it.calendarRules.isNotEmpty() }

        viewModel.selectDate(targetDate)
        viewModel.state.first { it.selectedDate == targetDate && it.calendarRules.any { rule -> rule.date == targetDate } }
        viewModel.saveCalendarDayOverride(targetDate, targetDate, CalendarDayKind.MAKEUP_WORKDAY, "补课")
        viewModel.state.first { it.selectedCalendarRule?.kind == CalendarDayKind.MAKEUP_WORKDAY }
        assertEquals(CalendarDayKind.MAKEUP_WORKDAY, viewModel.state.value.selectedCalendarRule?.kind)

        viewModel.clearCalendarDayOverrides(listOf(targetDate))
        viewModel.state.first { it.selectedCalendarRule?.kind == CalendarDayKind.HOLIDAY_REST }

        assertEquals(CalendarDayKind.HOLIDAY_REST, viewModel.state.value.selectedCalendarRule?.kind)
        assertEquals("订阅源", viewModel.state.value.selectedCalendarRule?.sourceName)
    }

    private fun createViewModel(scope: CoroutineScope): ScheduleViewModel = ScheduleViewModel(
        repository = RecordingScheduleRepository(),
        holidayCalendarRepository = repository,
        clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC),
        coroutineScope = scope
    )

    private class RecordingScheduleRepository : ScheduleRepository {
        private val events = MutableStateFlow<List<ScheduleEvent>>(emptyList())

        override suspend fun create(event: ScheduleEvent): Long = 1L

        override suspend fun update(event: ScheduleEvent) = Unit

        override suspend fun delete(id: Long) = Unit

        override fun observeBetween(
            startInclusive: Instant,
            endInclusive: Instant
        ): Flow<List<ScheduleEvent>> = events
    }
}
