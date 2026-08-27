package com.daily.life.feature.schedule

import java.time.LocalDate

internal fun monthCalendarDays(selectedDate: LocalDate): List<LocalDate> {
    val firstDay = selectedDate.withDayOfMonth(1)
    val firstMonday = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
    return (0 until 42).map { firstMonday.plusDays(it.toLong()) }
}

internal fun eventDatesForCalendar(
    selectedDate: LocalDate,
    eventDates: List<LocalDate>
): Set<LocalDate> = eventDates.filter { it == selectedDate }.toSet()
