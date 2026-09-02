package com.daily.life.feature.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodPredictionTest {
    @Test
    fun nextStartUsesTheLatestRecordedStartDateAndThirtyDayCycle() {
        val nextStart = PeriodPredictionCalculator.nextStartDate(
            records = listOf(
                PeriodRecord(
                    startDate = LocalDate.of(2026, 8, 1),
                    endDate = LocalDate.of(2026, 8, 5)
                )
            ),
            cycleDays = 30
        )

        assertEquals(LocalDate.of(2026, 8, 31), nextStart)
    }

    @Test
    fun nextStartIsUnavailableWithoutAnyRecordedPeriod() {
        assertNull(PeriodPredictionCalculator.nextStartDate(emptyList(), cycleDays = 30))
    }
}
