package com.daily.life.feature.timetable

import com.daily.life.core.calendar.SystemCalendarSpecialDay
import java.time.LocalDate

enum class ClassOverride {
    FOLLOW_CALENDAR,
    HAS_CLASS,
    NO_CLASS
}

data class SemesterClassOverrideInput(
    val actualDate: LocalDate,
    val override: ClassOverride
)

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
    val unsupportedRows: List<UnsupportedTimetableRow>,
    val parsedPeriodTimes: Map<Int, SemesterPeriodTime> = emptyMap()
)

data class SemesterInput(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isCurrent: Boolean = true
)

data class SemesterCalendarAdjustmentInput(
    val actualDate: LocalDate,
    val sourceDayOfWeek: Int,
    val sourceWeekParity: WeekParity,
    val sourceDate: LocalDate? = null,
    val sourceLabel: String? = null
)

data class MakeupCourseSource(
    val dayOfWeek: Int,
    val weekParity: WeekParity
)

data class TimetableAdjustmentChoiceState(
    val actualDate: LocalDate,
    val label: String,
    val selectedSourceDayOfWeek: Int?,
    val selectedSourceWeekParity: WeekParity? = null,
    val sourceDate: LocalDate? = null,
    val options: List<Int> = (1..7).toList(),
    val isRequired: Boolean = true
)

data class TimetableClassOverrideState(
    val actualDate: LocalDate,
    val override: ClassOverride = ClassOverride.FOLLOW_CALENDAR
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
    val date: LocalDate? = null,
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

data class TimetablePeriodTimeRowState(
    val period: Int,
    val start: String,
    val end: String
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
    val periodTimes: List<TimetablePeriodTimeRowState> = defaultSemesterPeriodTimes().map {
        TimetablePeriodTimeRowState(it.period, it.startTime.toString(), it.endTime.toString())
    },
    val periodTimesDetectedFromPdf: Boolean = false,
    val calendarSpecialDays: List<SystemCalendarSpecialDay> = emptyList(),
    val calendarAdjustmentChoices: List<TimetableAdjustmentChoiceState> = emptyList(),
    val classOverrideChoices: List<TimetableClassOverrideState> = emptyList(),
    val calendarReadWarning: String? = null,
    val holidayCalendarWarning: String? = null,
    val holidayCalendarReady: Boolean = true,
    val replaceExisting: Boolean = true,
    val canConfirm: Boolean = false,
    val errorMessage: String? = null
)

data class TimetablePeriodEditorState(
    val isOpen: Boolean = false,
    val rows: List<TimetablePeriodTimeRowState> = emptyList(),
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
    val periodTimes: List<SemesterPeriodTime> = defaultSemesterPeriodTimes(),
    val importState: TimetableImportState = TimetableImportState(),
    val periodEditor: TimetablePeriodEditorState = TimetablePeriodEditorState(),
    val calendarSpecialDays: List<SystemCalendarSpecialDay> = emptyList(),
    val classOverrides: Map<LocalDate, ClassOverride> = emptyMap(),
    val calendarAdjustmentWarning: String? = null,
    val isEmpty: Boolean = true
)

fun defaultTimetableDays(weekStartDate: LocalDate? = null): List<TimetableDayColumnState> = listOf(
    "周一", "周二", "周三", "周四", "周五", "周六", "周日"
).mapIndexed { index, label ->
    TimetableDayColumnState(
        dayOfWeek = index + 1,
        label = label,
        date = weekStartDate?.plusDays(index.toLong()),
        courses = emptyList()
    )
}

fun timetableDaysForWeek(semesterStartDate: LocalDate, week: Int): List<TimetableDayColumnState> =
    defaultTimetableDays(
        weekStartDate = semesterStartDate.plusWeeks((week - 1).coerceAtLeast(0).toLong())
    )

fun defaultPeriodLabels(): List<TimetablePeriodLabel> = listOf(
    TimetablePeriodLabel(1, "1\n08:00\n08:45"),
    TimetablePeriodLabel(2, "2\n08:50\n09:35"),
    TimetablePeriodLabel(3, "3\n09:50\n10:35"),
    TimetablePeriodLabel(4, "4\n10:40\n11:25"),
    TimetablePeriodLabel(5, "5\n11:30\n12:15"),
    TimetablePeriodLabel(6, "6\n13:30\n14:15"),
    TimetablePeriodLabel(7, "7\n14:20\n15:05"),
    TimetablePeriodLabel(8, "8\n15:20\n16:05"),
    TimetablePeriodLabel(9, "9\n16:10\n16:55"),
    TimetablePeriodLabel(10, "10\n18:30\n19:15"),
    TimetablePeriodLabel(11, "11\n19:20\n20:05"),
    TimetablePeriodLabel(12, "12\n20:10\n20:55")
)
