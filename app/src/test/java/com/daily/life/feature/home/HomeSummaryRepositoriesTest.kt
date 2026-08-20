package com.daily.life.feature.home

import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeSummaryRepositoriesTest {
    private lateinit var database: DailyDatabase

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun timetableSummaryUsesFirstUpcomingCourseForCurrentPeriod() = runTest {
        seedSemesterCourses()
        val preferences = createTestPreferences()
        preferences.setCurrentSemesterId(1L)
        preferences.setSemesterStartDate(LocalDate.of(2026, 8, 20))

        val repository = DaoTimetableSummaryRepository(
            semesterDao = database.semesterDao(),
            courseDao = database.courseDao(),
            preferences = preferences,
            clock = fixedClock(),
            currentPeriodProvider = { 3 }
        )

        val summary = repository.summary.first { it.todayCourseCount == 3 }

        assertEquals(3, summary.todayCourseCount)
        assertEquals("数据库 第 3-4 节", summary.nextCourseLabel)
    }

    @Test
    fun timetableSummaryReportsDayFinishedAfterLastCourse() = runTest {
        seedSemesterCourses()
        val preferences = createTestPreferences()
        preferences.setCurrentSemesterId(1L)
        preferences.setSemesterStartDate(LocalDate.of(2026, 8, 20))

        val repository = DaoTimetableSummaryRepository(
            semesterDao = database.semesterDao(),
            courseDao = database.courseDao(),
            preferences = preferences,
            clock = fixedClock(),
            currentPeriodProvider = { 9 }
        )

        val summary = repository.summary.first { it.todayCourseCount == 3 }

        assertEquals("今日课程已结束", summary.nextCourseLabel)
    }

    private suspend fun seedSemesterCourses() {
        database.semesterDao().insert(
            SemesterEntity(
                id = 1L,
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 8, 20),
                isCurrent = true,
                createdAt = 1L
            )
        )
        listOf(
            CourseEntity(
                id = 10L,
                semesterId = 1L,
                courseName = "英语",
                dayOfWeek = 4,
                startPeriod = 1,
                endPeriod = 2,
                weekRuleText = "第 1 周",
                parsedWeeks = setOf(1)
            ),
            CourseEntity(
                id = 11L,
                semesterId = 1L,
                courseName = "数据库",
                dayOfWeek = 4,
                startPeriod = 3,
                endPeriod = 4,
                weekRuleText = "第 1 周",
                parsedWeeks = setOf(1)
            ),
            CourseEntity(
                id = 12L,
                semesterId = 1L,
                courseName = "体育",
                dayOfWeek = 4,
                startPeriod = 7,
                endPeriod = 8,
                weekRuleText = "第 1 周",
                parsedWeeks = setOf(1)
            )
        ).forEach { course ->
            database.courseDao().insert(course)
            database.courseDao().insertWeek(CourseWeekEntity(courseId = course.id, week = 1))
        }
    }

    private fun createTestPreferences(): DailyPreferences {
        val directory = createTempDir(prefix = "daily-home-summary-prefs")
        return DailyPreferences.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { File(directory, "daily.preferences_pb") }
        )
    }

    private fun fixedClock(): Clock = Clock.fixed(
        Instant.parse("2026-08-20T01:00:00Z"),
        ZoneId.of("Asia/Shanghai")
    )
}
