package com.daily.life.feature.health

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthWeightTrendTest {
    @Test
    fun latestWeightDeltaUsesTheTwoMostRecentRecords() {
        val delta = weightChangeFromLatest(
            listOf(
                weight(id = 1, at = "2026-08-20T09:00:00Z", jin = 64.3),
                weight(id = 2, at = "2026-08-22T09:00:00Z", jin = 63.5),
                weight(id = 3, at = "2026-08-01T09:00:00Z", jin = 65.0)
            )
        )

        assertEquals(-0.8, delta ?: 0.0, 0.001)
    }

    @Test
    fun latestWeightDeltaIsUnavailableWithoutTwoRecords() {
        assertNull(weightChangeFromLatest(emptyList()))
        assertNull(weightChangeFromLatest(listOf(weight(id = 1, at = "2026-08-22T09:00:00Z", jin = 63.5))))
    }

    private fun weight(id: Long, at: String, jin: Double) = WeightRecord(
        id = id,
        recordedAt = Instant.parse(at),
        weightJin = jin
    )
}
