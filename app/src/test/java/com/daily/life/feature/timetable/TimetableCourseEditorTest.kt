package com.daily.life.feature.timetable

import com.daily.life.core.database.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableCourseEditorTest {
    @Test
    fun validationParsesOddWeeksAndRejectsBlankName() {
        val valid = TimetableCourseDraft(null, 1, "高等数学", 1, 1, 2, "1-16周 单周", "教一 101", "", "")
        val result = validateTimetableCourseDraft(valid)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), (result as CourseDraftValidation.Valid).parsedWeeks)
        assertTrue(validateTimetableCourseDraft(valid.copy(courseName = "")) is CourseDraftValidation.Invalid)
    }

    @Test
    fun conflictNeedsSameDayWeeksAndPeriods() {
        val existing = CourseEntity(9, 1, "英语", 2, 3, 4, "1-8周", setOf(1, 2, 3, 4, 5, 6, 7, 8))
        val draft = TimetableCourseDraft(null, 1, "线代", 2, 4, 5, "2-10周", "", "", "")
        assertTrue(coursesConflict(draft, setOf(2, 3, 4, 5, 6, 7, 8, 9, 10), existing, existing.parsedWeeks))
        assertFalse(coursesConflict(draft.copy(startPeriod = 5), setOf(9, 10), existing, existing.parsedWeeks))
    }

    @Test
    fun validationRejectsInvalidRequiredEditorFields() {
        val base = TimetableCourseDraft(null, 1, "课程", 1, 1, 1, "1-16周", "", "", "")
        assertTrue(validateTimetableCourseDraft(base.copy(weekRuleText = "")) is CourseDraftValidation.Invalid)
        assertTrue(validateTimetableCourseDraft(base.copy(dayOfWeek = 0)) is CourseDraftValidation.Invalid)
        assertTrue(validateTimetableCourseDraft(base.copy(startPeriod = 0)) is CourseDraftValidation.Invalid)
        assertTrue(validateTimetableCourseDraft(base.copy(endPeriod = 13)) is CourseDraftValidation.Invalid)
        assertTrue(validateTimetableCourseDraft(base.copy(startPeriod = 2, endPeriod = 1)) is CourseDraftValidation.Invalid)
        assertTrue(validateTimetableCourseDraft(base.copy(weekRuleText = "周次未知")) is CourseDraftValidation.Invalid)
    }

    @Test
    fun conflictIgnoresDifferentIdsSemestersDaysWeeksAndNonOverlappingPeriods() {
        val existing = CourseEntity(9, 1, "英语", 2, 3, 4, "1-8周", setOf(1, 2, 3, 4, 5, 6, 7, 8))
        val candidate = TimetableCourseDraft(9, 1, "英语", 2, 3, 4, "1-8周", "", "", "")
        assertFalse(coursesConflict(candidate, existing.parsedWeeks, existing, existing.parsedWeeks))
        assertFalse(coursesConflict(candidate.copy(id = null, semesterId = 2), existing.parsedWeeks, existing, existing.parsedWeeks))
        assertFalse(coursesConflict(candidate.copy(id = null, dayOfWeek = 3), existing.parsedWeeks, existing, existing.parsedWeeks))
        assertFalse(coursesConflict(candidate.copy(id = null, startPeriod = 5), existing.parsedWeeks, existing, existing.parsedWeeks))
        assertFalse(coursesConflict(candidate.copy(id = null, weekRuleText = "9-10周"), setOf(9, 10), existing, existing.parsedWeeks))
    }

    @Test
    fun editorStateIsSeparateFromImportState() {
        val state = TimetableState()
        assertFalse(state.courseEditor.isOpen)
        assertFalse(state.importState.isOpen)
        assertEquals(null, state.courseEditor.draft)
    }
}
