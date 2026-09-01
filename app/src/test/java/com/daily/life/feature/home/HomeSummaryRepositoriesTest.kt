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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
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
            semesterPeriodDao = database.semesterPeriodDao(),
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
            semesterPeriodDao = database.semesterPeriodDao(),
            preferences = preferences,
            clock = fixedClock(),
            currentPeriodProvider = { 9 }
        )

        val summary = repository.summary.first { it.todayCourseCount == 3 }

        assertEquals("今日课程已结束", summary.nextCourseLabel)
    }

    @Test
    fun timetableSummaryRefreshesAsTimeAdvancesWithoutDatabaseWrites() = runTest {
        seedSemesterCourses()
        val preferences = createTestPreferences()
        preferences.setCurrentSemesterId(1L)
        preferences.setSemesterStartDate(LocalDate.of(2026, 8, 20))
        val refreshTicks = MutableSharedFlow<Unit>()
        var currentPeriod = 3
        val repository = DaoTimetableSummaryRepository(
            semesterDao = database.semesterDao(),
            courseDao = database.courseDao(),
            semesterPeriodDao = database.semesterPeriodDao(),
            preferences = preferences,
            clock = fixedClock(),
            currentPeriodProvider = { currentPeriod },
            refreshTicks = refreshTicks
        )
        val labels = mutableListOf<String?>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.summary
                .filter { it.todayCourseCount == 3 }
                .map { it.nextCourseLabel }
                .distinctUntilChanged()
                .collect(labels::add)
        }
        advanceUntilIdle()

        awaitLabels(labels, listOf("数据库 第 3-4 节"), testScheduler)

        currentPeriod = 5
        refreshTicks.emit(Unit)
        awaitLabels(labels, listOf("数据库 第 3-4 节", "体育 第 7-8 节"), testScheduler)

        currentPeriod = 9
        refreshTicks.emit(Unit)
        awaitLabels(
            labels,
            listOf("数据库 第 3-4 节", "体育 第 7-8 节", "今日课程已结束"),
            testScheduler
        )
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

    private suspend fun awaitLabels(
        labels: List<String?>,
        expected: List<String?>,
        scheduler: TestCoroutineScheduler
    ) {
        repeat(100) {
            scheduler.advanceUntilIdle()
            if (labels == expected) return
            Thread.sleep(20L)
        }
        assertEquals(expected, labels)
    }
}
