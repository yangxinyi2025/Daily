package com.daily.life.feature.schedule

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalendarModelTest {
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
