package com.daily.life.feature.timetable

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.NoOpReminderScheduler
import com.daily.life.core.ReminderScheduler
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
            replaceExisting = true
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
        assertEquals(listOf(confirmedSemesterId), scheduler.scheduledSemesterIds)
        assertTrue(scheduler.coursesWereVisibleWhenScheduled)
    }

    @Test
    fun noOpReminderSchedulerIsSafeToInject() = runTest {
        NoOpReminderScheduler.scheduleCourseReminders(42L)
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
}
