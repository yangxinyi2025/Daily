package com.daily.life.feature.timetable

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableWeekDatesTest {
    @Test
    fun firstWeekMapsEveryWeekdayToItsSemesterDate() {
        val days = timetableDaysForWeek(LocalDate.of(2026, 9, 14), week = 1)

        assertEquals("周一", days.first().label)
        assertEquals(LocalDate.of(2026, 9, 14), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 20), days.last().date)
    }
}
