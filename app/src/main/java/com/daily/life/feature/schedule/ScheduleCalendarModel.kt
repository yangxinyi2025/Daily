package com.daily.life.feature.schedule

import java.time.LocalDate
import java.time.YearMonth

internal enum class CalendarFlipDirection {
    PREVIOUS,
    NEXT
}

internal data class CalendarFlipUiState(
    val shownMonth: YearMonth,
    val pendingMonth: YearMonth? = null,
    val direction: CalendarFlipDirection? = null
) {
    val isFlipping: Boolean get() = pendingMonth != null
}

internal fun requestCalendarFlip(
    state: CalendarFlipUiState,
    direction: CalendarFlipDirection
): CalendarFlipUiState = if (state.isFlipping) {
    state
} else {
    state.copy(
        pendingMonth = browseCalendarMonth(state.shownMonth, direction),
        direction = direction
    )
}

internal fun canSelectCalendarDate(state: CalendarFlipUiState): Boolean = !state.isFlipping

internal fun completeCalendarFlip(state: CalendarFlipUiState): CalendarFlipUiState =
    state.pendingMonth?.let { month ->
        CalendarFlipUiState(shownMonth = month)
    } ?: state

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
