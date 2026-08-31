package com.daily.life.feature.timetable

import com.daily.life.core.database.CourseEntity

data class TimetableCourseDraft(
    val id: Long?,
    val semesterId: Long,
    val courseName: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekRuleText: String,
    val location: String,
    val teacher: String,
    val notes: String
)

sealed interface CourseDraftValidation {
    data class Valid(val parsedWeeks: Set<Int>) : CourseDraftValidation
    data class Invalid(val message: String) : CourseDraftValidation
}

fun validateTimetableCourseDraft(draft: TimetableCourseDraft): CourseDraftValidation {
    if (draft.courseName.isBlank()) return CourseDraftValidation.Invalid("课程名称不能为空")
    if (draft.weekRuleText.isBlank()) return CourseDraftValidation.Invalid("周次规则不能为空")
    if (draft.dayOfWeek !in 1..7) return CourseDraftValidation.Invalid("星期必须在 1 到 7 之间")
    if (draft.startPeriod !in 1..12 || draft.endPeriod !in 1..12) {
        return CourseDraftValidation.Invalid("节次必须在 1 到 12 之间")
    }
    if (draft.startPeriod > draft.endPeriod) return CourseDraftValidation.Invalid("开始节次不能晚于结束节次")

    val parsed = WeekRuleParser.parse(draft.weekRuleText)
    val weeks = when (parsed.parity) {
        WeekParity.ODD -> parsed.weeks.filterTo(linkedSetOf()) { it % 2 == 1 }
        WeekParity.EVEN -> parsed.weeks.filterTo(linkedSetOf()) { it % 2 == 0 }
        null -> parsed.weeks
    }.toSet()
    return if (parsed.warnings.isEmpty() && weeks.isNotEmpty()) {
        CourseDraftValidation.Valid(weeks)
    } else {
        CourseDraftValidation.Invalid(parsed.warnings.firstOrNull() ?: "未解析出有效周次")
    }
}

fun coursesConflict(
    candidate: TimetableCourseDraft,
    candidateWeeks: Set<Int>,
    existing: CourseEntity,
    existingWeeks: Set<Int>
): Boolean = candidate.id != existing.id &&
    candidate.semesterId == existing.semesterId &&
    candidate.dayOfWeek == existing.dayOfWeek &&
    candidateWeeks.intersect(existingWeeks).isNotEmpty() &&
    candidate.startPeriod <= existing.endPeriod &&
    existing.startPeriod <= candidate.endPeriod

