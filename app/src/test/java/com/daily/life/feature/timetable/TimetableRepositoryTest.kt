package com.daily.life.feature.timetable

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.NoOpReminderScheduler
import com.daily.life.core.ReminderScheduler
import com.daily.life.core.calendar.CalendarAlertMethod
import com.daily.life.core.calendar.CalendarProviderClient
import com.daily.life.core.calendar.CalendarReminderRequest
import com.daily.life.core.calendar.CalendarReminderSyncer
import com.daily.life.core.calendar.CalendarTarget
import com.daily.life.core.calendar.SystemCalendarScheduleEvent
import com.daily.life.core.calendar.SystemCalendarScheduleReader
import com.daily.life.core.calendar.SystemCalendarGateway
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
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
class TimetableRepositoryTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        val directory = createTempDir(prefix = "daily-timetable-prefs")
        preferences = DailyPreferences.create(
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            produceFile = { File(directory, "daily.preferences_pb") }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun confirmImportReplacesCoursesWritesNormalizedWeeksThenSchedules() = runTest {
        val semesterId = database.semesterDao().insert(
            SemesterEntity(
                id = 1L,
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 9, 1),
                isCurrent = true,
                createdAt = 1L
            )
        )
        val oldCourseId = database.courseDao().insert(
            CourseEntity(
                semesterId = semesterId,
                courseName = "旧课程",
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weekRuleText = "1周",
                parsedWeeks = setOf(1)
            )
        )
        database.courseDao().insertWeek(CourseWeekEntity(oldCourseId, 1))
        preferences.setCurrentSemesterId(semesterId)
        val scheduler = RecordingReminderScheduler(database)
        val repository = RoomTimetableRepository(
            database = database,
            preferences = preferences,
            reminderScheduler = scheduler,
            clock = fixedClock()
        )

        val confirmedSemesterId = repository.confirmImport(
            preview = TimetableParseResult(
                courses = listOf(
                    previewCourse("高等数学", 1, 1, 2, "1-4周 单周", setOf(1, 2, 3, 4), WeekParity.ODD),
                    previewCourse("大学英语", 3, 3, 4, "2,4,6周", setOf(2, 4, 6))
                ),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            ),
            semester = SemesterInput(
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2027, 1, 15)
            ),
            replaceExisting = true,
            calendarAdjustments = listOf(
                SemesterCalendarAdjustmentInput(
                    actualDate = LocalDate.of(2026, 10, 10),
                    sourceDayOfWeek = 5,
                    sourceWeekParity = WeekParity.ODD,
                    sourceLabel = "补周五"
                )
            )
        )

        val courses = database.courseDao().observeBySemester(confirmedSemesterId).first()
        assertEquals(listOf("高等数学", "大学英语"), courses.map(CourseEntity::courseName))
        assertEquals(
            listOf(1, 3),
            database.courseDao().findWithWeeksById(courses[0].id)?.weeks?.map(CourseWeekEntity::week)?.sorted()
        )
        assertEquals(
            listOf(2, 4, 6),
            database.courseDao().findWithWeeksById(courses[1].id)?.weeks?.map(CourseWeekEntity::week)?.sorted()
        )
        assertEquals(confirmedSemesterId, preferences.currentSemesterId.first())
        assertEquals(
            listOf(LocalDate.of(2026, 10, 10)),
            database.semesterCalendarAdjustmentDao().findBySemester(confirmedSemesterId).map { it.actualDate }
        )
        assertEquals(listOf(confirmedSemesterId), scheduler.scheduledSemesterIds)
        assertTrue(scheduler.coursesWereVisibleWhenScheduled)
    }

    @Test
    fun noOpReminderSchedulerIsSafeToInject() = runTest {
        NoOpReminderScheduler.scheduleCourseReminders(42L)
    }

    @Test
    fun confirmImportPersistsSemesterClassOverrides() = runTest {
        val repository = RoomTimetableRepository(
            database = database,
            preferences = preferences,
            reminderScheduler = NoOpReminderScheduler,
            clock = fixedClock()
        )
        val actualDate = LocalDate.of(2026, 9, 19)
        val semesterId = repository.confirmImport(
            preview = TimetableParseResult(
                courses = listOf(previewCourse("数据库", 5, 1, 2, "1周", setOf(1))),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            ),
            semester = SemesterInput("2026 秋季", LocalDate.of(2026, 8, 31)),
            replaceExisting = false,
            classOverrides = listOf(SemesterClassOverrideInput(actualDate, ClassOverride.HAS_CLASS))
        )

        assertEquals(
            ClassOverride.HAS_CLASS.name,
            database.semesterClassOverrideDao().findBySemester(semesterId).single().overrideKind
        )
    }

    @Test
    fun confirmImportUsesSystemCalendarInsteadOfDailyCourseSchedulerWhenCalendarSyncIsConfigured() = runTest {
        val scheduler = RecordingReminderScheduler(database)
        val calendarClient = RecordingCalendarClient()
        preferences.setCourseReminderMinutes(15)
        val repository = RoomTimetableRepository(
            database = database,
            preferences = preferences,
            reminderScheduler = scheduler,
            calendarReminderSyncer = CalendarReminderSyncer(
                database = database,
                gateway = SystemCalendarGateway(calendarClient)
            ),
            clock = fixedClock()
        )

        repository.confirmImport(
            preview = TimetableParseResult(
                courses = listOf(previewCourse("数据库", 1, 1, 2, "1周", setOf(1))),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            ),
            semester = SemesterInput(
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2027, 1, 15),
                isCurrent = true
            ),
            replaceExisting = false
        )

        assertTrue(scheduler.scheduledSemesterIds.isEmpty())
        assertEquals(listOf("课程：数据库"), calendarClient.insertedRequests.map(CalendarReminderRequest::title))
    }

    @Test
    fun courseCalendarMessagesAlsoFollowConfirmedMakeupDay() = runTest {
        val calendarClient = RecordingCalendarClient()
        preferences.setCourseReminderMinutes(15)
        val calendarReader = SystemCalendarScheduleReader(
            canReadCalendar = { true },
            queryEvents = { _, _ ->
                listOf(
                    SystemCalendarScheduleEvent(
                        startAt = LocalDate.of(2026, 9, 5).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                        endAt = LocalDate.of(2026, 9, 6).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                        title = "补周五",
                        description = null
                    )
                )
            }
        )
        val repository = RoomTimetableRepository(
            database = database,
            preferences = preferences,
            reminderScheduler = NoOpReminderScheduler,
            calendarReminderSyncer = CalendarReminderSyncer(
                database = database,
                gateway = SystemCalendarGateway(calendarClient)
            ),
            calendarScheduleReader = calendarReader,
            clock = fixedClock()
        )

        repository.confirmImport(
            preview = TimetableParseResult(
                courses = listOf(previewCourse("数据库", 5, 1, 2, "1周", setOf(1))),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            ),
            semester = SemesterInput(
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 8, 31),
                endDate = LocalDate.of(2027, 1, 15),
                isCurrent = true
            ),
            replaceExisting = false,
            calendarAdjustments = listOf(
                SemesterCalendarAdjustmentInput(
                    actualDate = LocalDate.of(2026, 9, 5),
                    sourceDayOfWeek = 5,
                    sourceWeekParity = WeekParity.ODD,
                    sourceLabel = "补周五"
                )
            )
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 4),
                LocalDate.of(2026, 9, 5)
            ),
            calendarClient.insertedRequests.map { it.startAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate() }
        )
    }

    @Test
    fun replacingAnImportedTimetableDeletesItsOldSystemCalendarCourseReminders() = runTest {
        val calendarClient = RecordingCalendarClient()
        preferences.setCourseReminderMinutes(15)
        val repository = RoomTimetableRepository(
            database = database,
            preferences = preferences,
            reminderScheduler = NoOpReminderScheduler,
            calendarReminderSyncer = CalendarReminderSyncer(
                database = database,
                gateway = SystemCalendarGateway(calendarClient)
            ),
            clock = fixedClock()
        )
        val semester = SemesterInput(
            name = "2026 秋季",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2027, 1, 15),
            isCurrent = true
        )

        repository.confirmImport(
            preview = TimetableParseResult(
                courses = listOf(previewCourse("旧课程", 1, 1, 2, "1周", setOf(1))),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            ),
            semester = semester,
            replaceExisting = false
        )
        repository.confirmImport(
            preview = TimetableParseResult(
                courses = listOf(previewCourse("新课程", 2, 3, 4, "1周", setOf(1))),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            ),
            semester = semester,
            replaceExisting = true
        )

        assertEquals(listOf(1L), calendarClient.deletedEventIds)
        assertEquals(listOf("课程：旧课程", "课程：新课程"), calendarClient.insertedRequests.map(CalendarReminderRequest::title))
    }

    @Test
    fun updatingPeriodTimesKeepsStoredRowsWhenCalendarSyncFails() = runTest {
        preferences.setCourseReminderMinutes(15)
        val repository = RoomTimetableRepository(
            database = database,
            preferences = preferences,
            reminderScheduler = NoOpReminderScheduler,
            calendarReminderSyncer = CalendarReminderSyncer(
                database = database,
                gateway = SystemCalendarGateway(PermissionDeniedCalendarClient())
            ),
            clock = fixedClock()
        )
        val semesterId = repository.confirmImport(
            preview = TimetableParseResult(
                courses = listOf(previewCourse("数据库", 1, 1, 2, "1周", setOf(1))),
                warnings = emptyList(),
                unsupportedRows = emptyList()
            ),
            semester = SemesterInput(
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2027, 1, 15),
                isCurrent = true
            ),
            replaceExisting = false
        )
        val changedTimes = defaultSemesterPeriodTimes().toMutableList().also {
            it[0] = SemesterPeriodTime(1, java.time.LocalTime.of(8, 5), java.time.LocalTime.of(8, 45))
        }

        val result = runCatching { repository.updatePeriodTimes(semesterId, changedTimes) }

        assertTrue(result.isFailure)
        assertEquals(java.time.LocalTime.of(8, 0), database.semesterPeriodDao().findBySemester(semesterId).first().startTime)
    }

    private fun previewCourse(
        name: String,
        day: Int,
        start: Int,
        end: Int,
        rawWeeks: String,
        weeks: Set<Int>,
        parity: WeekParity? = null
    ) = TimetablePreviewCourse(
        courseName = name,
        dayOfWeek = day,
        startPeriod = start,
        endPeriod = end,
        weekRule = WeekRuleResult(rawWeeks, weeks, parity),
        rawRow = name,
        needsReview = false
    )

    private fun fixedClock(): Clock = Clock.fixed(
        Instant.parse("2026-08-20T00:00:00Z"),
        ZoneOffset.UTC
    )

    private class RecordingReminderScheduler(
        private val database: DailyDatabase
    ) : ReminderScheduler {
        val scheduledSemesterIds = mutableListOf<Long>()
        var coursesWereVisibleWhenScheduled = false

        override suspend fun scheduleCourseReminders(semesterId: Long) {
            scheduledSemesterIds += semesterId
            coursesWereVisibleWhenScheduled =
                database.courseDao().observeBySemester(semesterId).first().isNotEmpty()
        }
    }

    private class RecordingCalendarClient : CalendarProviderClient {
        val insertedRequests = mutableListOf<CalendarReminderRequest>()
        val deletedEventIds = mutableListOf<Long>()

        override fun hasReadWritePermission(): Boolean = true

        override fun writableCalendars(): List<CalendarTarget> = listOf(
            CalendarTarget(id = 1L, isPrimary = true, canWrite = true, isVisible = true)
        )

        override fun insertEvent(calendarId: Long, request: CalendarReminderRequest): Long {
            insertedRequests += request
            return insertedRequests.size.toLong()
        }

        override fun updateEvent(eventId: Long, request: CalendarReminderRequest): Boolean = true

        override fun replaceAlertReminder(
            eventId: Long,
            minutes: Int,
            method: CalendarAlertMethod
        ): Boolean = true

        override fun deleteEvent(eventId: Long): Boolean {
            deletedEventIds += eventId
            return true
        }
    }

    private class PermissionDeniedCalendarClient : CalendarProviderClient {
        override fun hasReadWritePermission(): Boolean = false

        override fun writableCalendars(): List<CalendarTarget> = emptyList()

        override fun createDailyLocalCalendar(): CalendarTarget? = null

        override fun insertEvent(calendarId: Long, request: CalendarReminderRequest): Long = 0L

        override fun updateEvent(eventId: Long, request: CalendarReminderRequest): Boolean = false

        override fun replaceAlertReminder(eventId: Long, minutes: Int, method: CalendarAlertMethod): Boolean = false

        override fun clearReminders(eventId: Long): Boolean = false

        override fun deleteEvent(eventId: Long): Boolean = false
    }
}
