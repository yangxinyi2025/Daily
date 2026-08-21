package com.daily.life.feature.timetable

import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.SemesterEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableViewModelTest {
    @Test
    fun startsOnCurrentWeekAndGroupsVisibleCoursesByDay() = runTest {
        val repository = FakeTimetableRepository(
            semester = SemesterEntity(
                id = 7L,
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 9, 1),
                isCurrent = true,
                createdAt = 1L
            ),
            courses = mapOf(
                2 to listOf(
                    CourseEntity(
                        id = 11L,
                        semesterId = 7L,
                        courseName = "数据库",
                        dayOfWeek = 3,
                        startPeriod = 3,
                        endPeriod = 4,
                        weekRuleText = "1-16周",
                        parsedWeeks = (1..16).toSet(),
                        location = "教二 203",
                        teacher = "李老师"
                    )
                )
            )
        )
        val viewModel = TimetableViewModel(
            repository = repository,
            parser = PdfTimetableParser(),
            clock = fixedClock(),
            coroutineScope = backgroundScope
        )

        val state = viewModel.state.first { it.days.any { day -> day.courses.isNotEmpty() } }

        assertEquals(2, state.currentWeek)
        assertEquals(2, state.selectedWeek)
        assertEquals("第 2 周", state.weekLabel)
        assertEquals("数据库", state.days.first { it.dayOfWeek == 3 }.courses.single().courseName)

        viewModel.selectNextWeek()
        runCurrent()
        assertEquals(3, viewModel.state.value.selectedWeek)
        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun editedPreviewIsConfirmedOnlyAfterExplicitConfirmation() = runTest {
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(
            repository = repository,
            parser = PdfTimetableParser(),
            clock = fixedClock(),
            coroutineScope = backgroundScope
        )
        val preview = TimetableParseResult(
            courses = listOf(
                TimetablePreviewCourse(
                    courseName = "高等数学",
                    dayOfWeek = null,
                    startPeriod = 1,
                    endPeriod = 2,
                    weekRule = WeekRuleResult("1-4周", setOf(1, 2, 3, 4)),
                    rawRow = "高等数学 | ? | 1-2 | 1-4周",
                    needsReview = true
                )
            ),
            warnings = emptyList(),
            unsupportedRows = emptyList()
        )

        viewModel.showImportPreview("synthetic.pdf", preview)
        viewModel.updateSemesterInput("2026 秋季", "2026-09-01")
        runCurrent()
        assertFalse(viewModel.state.value.importState.canConfirm)
        assertTrue(repository.confirmations.isEmpty())

        val edited = viewModel.state.value.importState.previewRows.single().copy(dayOfWeek = "周一")
        viewModel.updateImportRow(edited)
        runCurrent()
        assertTrue(viewModel.state.value.importState.canConfirm)
        assertTrue(repository.confirmations.isEmpty())

        viewModel.confirmImport()
        runCurrent()

        val confirmation = repository.confirmations.single()
        assertEquals("高等数学", confirmation.preview.courses.single().courseName)
        assertEquals(1, confirmation.preview.courses.single().dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 1), confirmation.semester.startDate)
        assertFalse(viewModel.state.value.importState.isOpen)
    }

    private fun fixedClock(): Clock = Clock.fixed(
        Instant.parse("2026-09-08T00:00:00Z"),
        ZoneOffset.UTC
    )

    private class FakeTimetableRepository(
        semester: SemesterEntity? = null,
        courses: Map<Int, List<CourseEntity>> = emptyMap()
    ) : TimetableRepository {
        override val currentSemester = MutableStateFlow(semester)
        private val coursesByWeek = courses.mapValues { MutableStateFlow(it.value) }
        val confirmations = mutableListOf<Confirmation>()

        override fun observeCourses(semesterId: Long, week: Int): Flow<List<CourseEntity>> =
            coursesByWeek[week] ?: MutableStateFlow(emptyList())

        override suspend fun confirmImport(
            preview: TimetableParseResult,
            semester: SemesterInput,
            replaceExisting: Boolean
        ): Long {
            confirmations += Confirmation(preview, semester, replaceExisting)
            return 99L
        }
    }

    private data class Confirmation(
        val preview: TimetableParseResult,
        val semester: SemesterInput,
        val replaceExisting: Boolean
    )
}
