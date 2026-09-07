package com.daily.life.feature.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthOverviewPresentationTest {
    @Test
    fun thirtyDayTrendIncludesTodayAndExcludesTheDayBeforeTheWindow() {
        val points = selectWeightTrendPoints(
            weights = listOf(weight("2026-08-01T00:00:00Z"), weight("2026-08-02T00:00:00Z")),
            range = WeightTrendRange.Days30,
            today = LocalDate.of(2026, 8, 31),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(listOf(LocalDate.of(2026, 8, 2)), points.map(WeightPoint::date))
    }

    @Test
    fun countdownClampsOverduePredictionToZeroDays() {
        assertEquals(0, daysUntilPeriod(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22)))
    }

    @Test
    fun overviewStateDoesNotTreatInitialLoadingAsEmpty() {
        assertEquals(HealthContentState.Loading, overviewContentState(false, false, null))
        assertEquals(HealthContentState.Empty, overviewContentState(true, false, null))
        assertEquals(HealthContentState.Error, overviewContentState(true, false, "读取失败"))
    }

    private fun weight(recordedAt: String) = WeightRecord(
        recordedAt = Instant.parse(recordedAt),
        weightJin = 120.0
    )
}
