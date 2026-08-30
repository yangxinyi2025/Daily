package com.daily.life.feature.timetable

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.NoOpReminderScheduler
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.HolidayCalendarRepository
import com.daily.life.core.calendar.IcsCalendarEvent
import com.daily.life.core.calendar.IcsCalendarFetcher
import com.daily.life.core.calendar.IcsCalendarSource
import com.daily.life.core.calendar.IcsFetchResult
import com.daily.life.core.calendar.SystemCalendarScheduleReader
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimetableImportHolidayCalendarTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        val directory = createTempDir(prefix = "daily-timetable-import-holiday-prefs")
        preferences = DailyPreferences.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { File(directory, "daily.preferences_pb") }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importLoadsFreshHolidayCalendarBeforeShowingThePreview() = runTest {
        val holidayCalendar = HolidayCalendarRepository(
            dao = database.holidayCalendarDao(),
            preferences = preferences,
            systemCalendarReader = SystemCalendarScheduleReader(ZoneId.systemDefault(), { false }) { _, _ -> emptyList() },
            icsClient = object : IcsCalendarFetcher {
                override fun fetch(source: IcsCalendarSource, etag: String?, lastModified: String?) = IcsFetchResult.Success(
                    events = listOf(
                        IcsCalendarEvent(
                            eventKey = "national-day-2026",
                            startDate = LocalDate.of(2026, 10, 1),
                            endExclusiveDate = LocalDate.of(2026, 10, 2),
                            title = "国庆节假期",
                            description = null,
                            kind = CalendarDayKind.HOLIDAY_REST,
                            sourceDayOfWeek = null,
                            sourceDate = null
                        )
                    ),
                    etag = null,
                    lastModified = null
                )
            }
        )
        val viewModel = TimetableViewModel(
            repository = RoomTimetableRepository(database, preferences, NoOpReminderScheduler),
            parser = TimetableParser { error("unused") },
            holidayCalendarRepository = holidayCalendar,
            coroutineScope = backgroundScope
        )

        viewModel.openImport()
        viewModel.updateSemesterInput("2026 秋季", "2026-09-28")
        viewModel.showImportPreview(
            "synthetic.pdf",
            TimetableParseResult(
                courses = listOf(
                    TimetablePreviewCourse(
                        courseName = "数据库",
                        dayOfWeek = 4,
                        startPeriod = 1,
                        endPeriod = 2,
                        weekRule = WeekRuleResult("1周", setOf(1)),
                        rawRow = "数据库",
                        needsReview = false
                    )
                ),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            )
        )

        val state = withContext(Dispatchers.IO) {
            withTimeoutOrNull(1_000) {
                viewModel.state.first { current ->
                    current.importState.calendarSpecialDays.any { it.date == LocalDate.of(2026, 10, 1) }
                }
            }
        }

        assertNotNull(state)
        assertEquals(SystemCalendarSpecialDayKind.Holiday, requireNotNull(state).importState.calendarSpecialDays.single().kind)
    }

    @Test
    fun importCannotConfirmWhenFreshHolidayCalendarHasNoUsableData() = runTest {
        val fetchCount = AtomicInteger(0)
        val holidayCalendar = HolidayCalendarRepository(
            dao = database.holidayCalendarDao(),
            preferences = preferences,
            systemCalendarReader = SystemCalendarScheduleReader(ZoneId.systemDefault(), { false }) { _, _ -> emptyList() },
            icsClient = object : IcsCalendarFetcher {
                override fun fetch(source: IcsCalendarSource, etag: String?, lastModified: String?): IcsFetchResult {
                    fetchCount.incrementAndGet()
                    return IcsFetchResult.Failure("网络不可用", retryable = true)
                }
            }
        )
        val viewModel = TimetableViewModel(
            repository = RoomTimetableRepository(database, preferences, NoOpReminderScheduler),
            parser = TimetableParser { error("unused") },
            holidayCalendarRepository = holidayCalendar,
            coroutineScope = backgroundScope
        )

        viewModel.openImport()
        viewModel.updateSemesterInput("2026 秋季", "2026-09-28")
        viewModel.showImportPreview(
            "synthetic.pdf",
            TimetableParseResult(
                courses = listOf(
                    TimetablePreviewCourse(
                        courseName = "数据库",
                        dayOfWeek = 4,
                        startPeriod = 1,
                        endPeriod = 2,
                        weekRule = WeekRuleResult("1周", setOf(1)),
                        rawRow = "数据库",
                        needsReview = false
                    )
                ),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            )
        )

        withContext(Dispatchers.IO) {
            withTimeoutOrNull(1_000) {
                while (fetchCount.get() == 0) delay(10)
            }
        }

        assertEquals(1, fetchCount.get())
        assertFalse(viewModel.state.value.importState.canConfirm)
    }
}
