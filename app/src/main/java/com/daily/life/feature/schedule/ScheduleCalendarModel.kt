package com.daily.life.feature.schedule

import java.time.LocalDate
import java.time.YearMonth

internal enum class CalendarFlipDirection {
    PREVIOUS,
    NEXT
}

internal data class CalendarSelection(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate
)

internal fun calendarGridWindow(month: YearMonth): ClosedRange<LocalDate> {
    val firstDay = month.atDay(1)
    val firstMonday = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
    return firstMonday..firstMonday.plusDays(41)
}

internal fun browseCalendarMonth(month: YearMonth, direction: CalendarFlipDirection): YearMonth =
    if (direction == CalendarFlipDirection.NEXT) month.plusMonths(1) else month.minusMonths(1)

internal fun selectCalendarDate(visibleMonth: YearMonth, date: LocalDate): CalendarSelection =
    CalendarSelection(visibleMonth = YearMonth.from(date), selectedDate = date)

internal fun monthCalendarDays(selectedDate: LocalDate): List<LocalDate> {
    val window = calendarGridWindow(YearMonth.from(selectedDate))
    return (0 until 42).map { window.start.plusDays(it.toLong()) }
}

internal fun eventDatesForCalendar(
    selectedDate: LocalDate,
    eventDates: List<LocalDate>
): Set<LocalDate> = eventDates.filter { it == selectedDate }.toSet()
