package com.daily.life.feature.timetable

import java.time.LocalDate

enum class WeekParity {
    ODD,
    EVEN
}

data class WeekRuleResult(
    val rawText: String,
    val weeks: Set<Int>,
    val parity: WeekParity? = null,
    val warnings: List<String> = emptyList()
)

data class TimetablePreviewCourse(
    val courseName: String,
    val dayOfWeek: Int?,
    val startPeriod: Int?,
    val endPeriod: Int?,
    val weekRule: WeekRuleResult,
    val location: String? = null,
    val teacher: String? = null,
    val campus: String? = null,
    val courseCode: String? = null,
    val credits: Double? = null,
    val notes: String? = null,
    val rawRow: String,
    val needsReview: Boolean
)

data class UnsupportedTimetableRow(
    val rawText: String,
    val reason: String
)

data class TimetableParseResult(
    val courses: List<TimetablePreviewCourse>,
    val warnings: List<String>,
    val unsupportedRows: List<UnsupportedTimetableRow>
)

data class SemesterInput(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isCurrent: Boolean = true
)

data class TimetableCourseUiState(
    val id: String,
    val courseName: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeksLabel: String,
    val location: String?,
    val teacher: String?,
    val detail: String
)

data class TimetableDayColumnState(
    val dayOfWeek: Int,
    val label: String,
    val courses: List<TimetableCourseUiState>
)

data class TimetablePeriodLabel(
    val period: Int,
    val label: String
)

data class TimetableImportRowState(
    val index: Int,
    val courseName: String,
    val dayOfWeek: String,
    val periodRange: String,
    val weekRuleText: String,
    val location: String,
    val teacher: String,
    val needsReview: Boolean,
    val warnings: List<String>,
    val rawRow: String
)

data class TimetableImportState(
    val isOpen: Boolean = false,
    val fileName: String? = null,
    val semesterName: String = "",
    val semesterStartDate: String = "",
    val isLoading: Boolean = false,
    val previewRows: List<TimetableImportRowState> = emptyList(),
    val warnings: List<String> = emptyList(),
    val unsupportedRows: List<UnsupportedTimetableRow> = emptyList(),
    val replaceExisting: Boolean = true,
    val canConfirm: Boolean = false,
    val errorMessage: String? = null
)

data class TimetableState(
    val currentSemesterName: String? = null,
    val currentSemesterStartDate: LocalDate? = null,
    val selectedWeek: Int = 1,
    val currentWeek: Int = 1,
    val weekLabel: String = "第 1 周",
    val days: List<TimetableDayColumnState> = defaultTimetableDays(),
    val timeLabels: List<TimetablePeriodLabel> = defaultPeriodLabels(),
    val importState: TimetableImportState = TimetableImportState(),
    val isEmpty: Boolean = true
)

fun defaultTimetableDays(): List<TimetableDayColumnState> = listOf(
    TimetableDayColumnState(1, "周一", emptyList()),
    TimetableDayColumnState(2, "周二", emptyList()),
    TimetableDayColumnState(3, "周三", emptyList()),
    TimetableDayColumnState(4, "周四", emptyList()),
    TimetableDayColumnState(5, "周五", emptyList()),
    TimetableDayColumnState(6, "周六", emptyList()),
    TimetableDayColumnState(7, "周日", emptyList())
)

fun defaultPeriodLabels(): List<TimetablePeriodLabel> = listOf(
    TimetablePeriodLabel(1, "1\n08:00"),
    TimetablePeriodLabel(2, "2\n08:55"),
    TimetablePeriodLabel(3, "3\n10:10"),
    TimetablePeriodLabel(4, "4\n11:05"),
    TimetablePeriodLabel(5, "5\n14:00"),
    TimetablePeriodLabel(6, "6\n14:55"),
    TimetablePeriodLabel(7, "7\n16:10"),
    TimetablePeriodLabel(8, "8\n17:05"),
    TimetablePeriodLabel(9, "9\n19:00"),
    TimetablePeriodLabel(10, "10\n19:55"),
    TimetablePeriodLabel(11, "11\n20:50"),
    TimetablePeriodLabel(12, "12\n21:45")
)
