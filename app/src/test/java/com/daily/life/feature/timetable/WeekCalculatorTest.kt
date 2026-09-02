package com.daily.life.feature.timetable

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekCalculatorTest {
    @Test
    fun weekOneStartsOnSemesterStartDate() {
        assertEquals(
            1,
            WeekCalculator.currentWeek(
                startDate = LocalDate.of(2026, 9, 1),
                date = LocalDate.of(2026, 9, 1)
            )
        )
        assertEquals(
            2,
            WeekCalculator.currentWeek(
                startDate = LocalDate.of(2026, 9, 1),
                date = LocalDate.of(2026, 9, 8)
            )
        )
    }

    @Test
    fun datesBeforeSemesterStartClampToWeekOne() {
        assertEquals(
            1,
            WeekCalculator.currentWeek(
                startDate = LocalDate.of(2026, 9, 1),
                date = LocalDate.of(2026, 8, 28)
            )
        )
    }

    @Test
    fun leapDaysAndMonthBoundariesUseWholeWeekBuckets() {
        assertEquals(
            1,
            WeekCalculator.currentWeek(
                startDate = LocalDate.of(2028, 2, 28),
                date = LocalDate.of(2028, 2, 29)
            )
        )
        assertEquals(
            2,
            WeekCalculator.currentWeek(
                startDate = LocalDate.of(2028, 2, 28),
                date = LocalDate.of(2028, 3, 6)
            )
        )
    }
}
