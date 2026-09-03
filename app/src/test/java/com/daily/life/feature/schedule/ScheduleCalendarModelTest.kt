package com.daily.life.feature.schedule

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalendarModelTest {
    @Test
    fun nextFlipLocksAdditionalRequestsUntilTheCurrentPageCompletes() {
        val initial = CalendarFlipUiState(shownMonth = YearMonth.of(2026, 12))

        val requested = requestCalendarFlip(initial, CalendarFlipDirection.NEXT)
        val ignored = requestCalendarFlip(requested, CalendarFlipDirection.PREVIOUS)

        assertEquals(YearMonth.of(2026, 12), requested.shownMonth)
        assertEquals(YearMonth.of(2027, 1), requested.pendingMonth)
        assertEquals(CalendarFlipDirection.NEXT, requested.direction)
        assertTrue(requested.isFlipping)
        assertEquals(requested, ignored)
    }

    @Test
    fun previousFlipUsesTheOppositeDirectionAcrossYears() {
        val requested = requestCalendarFlip(
            CalendarFlipUiState(shownMonth = YearMonth.of(2027, 1)),
            CalendarFlipDirection.PREVIOUS
        )

        assertEquals(YearMonth.of(2027, 1), requested.shownMonth)
        assertEquals(YearMonth.of(2026, 12), requested.pendingMonth)
        assertEquals(CalendarFlipDirection.PREVIOUS, requested.direction)
    }

    @Test
    fun selectingAnOutsideGridDateUpdatesBothTheDateAndItsVisibleMonth() {
        val selection = selectCalendarDate(
            visibleMonth = YearMonth.of(2026, 9),
            date = LocalDate.of(2026, 10, 1)
        )

        assertEquals(LocalDate.of(2026, 10, 1), selection.selectedDate)
        assertEquals(YearMonth.of(2026, 10), selection.visibleMonth)
    }

    @Test
    fun monthGridStartsOnMondayAndAlwaysContainsSixWeeks() {
        val days = monthCalendarDays(LocalDate.of(2026, 8, 22))

        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2026, 7, 27), days.first())
        assertEquals(LocalDate.of(2026, 9, 6), days.last())
        assertTrue(days.contains(LocalDate.of(2026, 8, 22)))
    }

    @Test
    fun eventDateMatchingUsesTheSelectedLocalDate() {
        val selected = LocalDate.of(2026, 8, 22)
        val matching = eventDatesForCalendar(
            selected,
            listOf(
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 23)
            )
        )

        assertTrue(matching.contains(selected))
        assertFalse(matching.contains(LocalDate.of(2026, 8, 23)))
    }
}
