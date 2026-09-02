package com.daily.life.feature.health

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthWeightDateTest {
    @Test
    fun chosenWeightTimeIsStoredAtTheSelectedLocalTime() {
        assertEquals(
            Instant.parse("2026-08-17T00:30:00Z"),
            weightRecordInstantFor(
                recordedAt = LocalDateTime.of(2026, 8, 17, 8, 30),
                zoneId = ZoneId.of("Asia/Shanghai")
            )
        )
    }

    @Test
    fun chosenWeightDateIsStoredAtTheStartOfThatLocalDay() {
        assertEquals(
            Instant.parse("2026-08-16T16:00:00Z"),
            weightRecordInstantFor(
                date = LocalDate.of(2026, 8, 17),
                zoneId = ZoneId.of("Asia/Shanghai")
            )
        )
    }
}
