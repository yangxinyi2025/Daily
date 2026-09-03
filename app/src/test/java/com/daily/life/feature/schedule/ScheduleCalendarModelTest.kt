package com.daily.life.feature.schedule

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalendarModelTest {
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
