package com.daily.life.feature.timetable

import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.CalendarDayRule
import com.daily.life.core.calendar.SystemCalendarSpecialDay
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import com.daily.life.core.calendar.mergeSystemCalendarSpecialDays
import java.time.LocalDate

internal data class TimetableScheduleSlot(
    val actualDate: LocalDate,
    val week: Int,
    val courseDayOfWeek: Int?,
    val courseWeek: Int?,
    val isHoliday: Boolean,
    val needsMakeupConfirmation: Boolean
)

internal fun mapWeekToScheduleSlotsWithRules(
    semesterStartDate: LocalDate,
    selectedWeek: Int,
    calendarRules: Iterable<CalendarDayRule>,
    confirmedAdjustments: Map<LocalDate, MakeupCourseSource>,
    classOverrides: Map<LocalDate, ClassOverride> = emptyMap()
): List<TimetableScheduleSlot> {
    val weekStart = semesterStartDate.plusWeeks((selectedWeek - 1).coerceAtLeast(0).toLong())
    val rulesByDate = calendarRules.associateBy { it.date }
    return (0L..6L).map { offset ->
        val actualDate = weekStart.plusDays(offset)
        val defaultSlot = TimetableScheduleSlot(actualDate, selectedWeek, actualDate.dayOfWeek.value, selectedWeek, false, false)
        when (classOverrides[actualDate]) {
            ClassOverride.NO_CLASS -> defaultSlot.copy(courseDayOfWeek = null, courseWeek = null)
            ClassOverride.HAS_CLASS -> defaultSlot
            ClassOverride.FOLLOW_CALENDAR, null -> when (rulesByDate[actualDate]?.kind) {
                CalendarDayKind.HOLIDAY_REST -> defaultSlot.copy(courseDayOfWeek = null, courseWeek = null, isHoliday = true)
                CalendarDayKind.MAKEUP_WORKDAY -> confirmedAdjustments[actualDate]?.let { source ->
                    defaultSlot.copy(
                        courseDayOfWeek = source.dayOfWeek,
                        courseWeek = sourceWeekForParity(selectedWeek, source.weekParity)
                    )
                } ?: defaultSlot.copy(courseDayOfWeek = null, courseWeek = null, needsMakeupConfirmation = true)
                else -> defaultSlot
            }
        }
    }
}

internal fun courseOccurrenceDatesWithRules(
    semesterStartDate: LocalDate,
    week: Int,
    courseDayOfWeek: Int,
    calendarRules: Iterable<CalendarDayRule>,
    confirmedAdjustments: Map<LocalDate, MakeupCourseSource>,
    classOverrides: Map<LocalDate, ClassOverride> = emptyMap()
): List<LocalDate> {
    require(courseDayOfWeek in 1..7)
    val weekStart = semesterStartDate.plusWeeks((week - 1).coerceAtLeast(0).toLong())
    val rulesByDate = calendarRules.associateBy { it.date }
    return (0L..6L).map { weekStart.plusDays(it) }
        .filter { date ->
            when (classOverrides[date]) {
                ClassOverride.NO_CLASS -> false
                ClassOverride.HAS_CLASS -> date.dayOfWeek.value == courseDayOfWeek
                ClassOverride.FOLLOW_CALENDAR, null -> when (rulesByDate[date]?.kind) {
                    CalendarDayKind.HOLIDAY_REST -> false
                    CalendarDayKind.MAKEUP_WORKDAY -> confirmedAdjustments[date].matchesCourse(courseDayOfWeek, week)
                    else -> date.dayOfWeek.value == courseDayOfWeek
                }
            }
        }
}

internal fun mapWeekToScheduleSlots(
    semesterStartDate: LocalDate,
    selectedWeek: Int,
    specialDays: Iterable<SystemCalendarSpecialDay>,
    confirmedAdjustments: Map<LocalDate, MakeupCourseSource>
): List<TimetableScheduleSlot> {
    val weekStart = semesterStartDate.plusWeeks((selectedWeek - 1).coerceAtLeast(0).toLong())
    val specialDaysByDate = mergeSystemCalendarSpecialDays(specialDays).associateBy(SystemCalendarSpecialDay::date)
    return (0L..6L).map { offset ->
        val actualDate = weekStart.plusDays(offset)
        val defaultSlot = TimetableScheduleSlot(actualDate, selectedWeek, offset.toInt() + 1, selectedWeek, false, false)
        when (specialDaysByDate[actualDate]?.kind) {
            SystemCalendarSpecialDayKind.Holiday -> defaultSlot.copy(courseDayOfWeek = null, courseWeek = null, isHoliday = true)
            SystemCalendarSpecialDayKind.MakeupWorkday -> confirmedAdjustments[actualDate]?.let { source ->
                defaultSlot.copy(
                    courseDayOfWeek = source.dayOfWeek,
                    courseWeek = sourceWeekForParity(selectedWeek, source.weekParity)
                )
            } ?: defaultSlot.copy(courseDayOfWeek = null, courseWeek = null, needsMakeupConfirmation = true)
            null -> defaultSlot
        }
    }
}

internal fun courseOccurrenceDates(
    semesterStartDate: LocalDate,
    week: Int,
    courseDayOfWeek: Int,
    specialDays: Iterable<SystemCalendarSpecialDay>,
    confirmedAdjustments: Map<LocalDate, MakeupCourseSource>
): List<LocalDate> {
    require(courseDayOfWeek in 1..7)
    val weekStart = semesterStartDate.plusWeeks((week - 1).coerceAtLeast(0).toLong())
    val weekEnd = weekStart.plusDays(7)
    val specialDaysByDate = mergeSystemCalendarSpecialDays(specialDays).associateBy(SystemCalendarSpecialDay::date)
    val dates = mutableSetOf<LocalDate>()
    val nominalDate = weekStart.plusDays((courseDayOfWeek - 1).toLong())
    when (specialDaysByDate[nominalDate]?.kind) {
        SystemCalendarSpecialDayKind.Holiday -> Unit
        SystemCalendarSpecialDayKind.MakeupWorkday -> if (confirmedAdjustments[nominalDate].matchesCourse(courseDayOfWeek, week)) dates += nominalDate
        null -> dates += nominalDate
    }
    specialDaysByDate.values
        .filter { it.date >= weekStart && it.date < weekEnd }
        .filter { it.kind == SystemCalendarSpecialDayKind.MakeupWorkday }
        .filter { confirmedAdjustments[it.date].matchesCourse(courseDayOfWeek, week) }
        .forEach { dates += it.date }
    return dates.sorted()
}

internal fun sourceWeekForParity(week: Int, parity: WeekParity): Int =
    if (weekParityOf(week) == parity) week else (week - 1).takeIf { it >= 1 } ?: week + 1

internal fun weekParityOf(week: Int): WeekParity =
    if (week % 2 == 0) WeekParity.EVEN else WeekParity.ODD

private fun MakeupCourseSource?.matchesCourse(courseDayOfWeek: Int, courseWeek: Int): Boolean =
    this?.dayOfWeek == courseDayOfWeek && this.weekParity == weekParityOf(courseWeek)
