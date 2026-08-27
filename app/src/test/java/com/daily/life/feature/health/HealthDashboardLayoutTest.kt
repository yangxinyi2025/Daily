package com.daily.life.feature.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthDashboardLayoutTest {
    @Test
    fun weightEntryIsInsertedBetweenTrendAndGoal() {
        assertEquals(
            listOf(
                HealthDashboardSection.Header,
                HealthDashboardSection.Overview,
                HealthDashboardSection.Trend,
                HealthDashboardSection.WeightEntry,
                HealthDashboardSection.Goal,
                HealthDashboardSection.Period,
                HealthDashboardSection.History
            ),
            healthDashboardSectionOrder(showWeightEditor = true, showPeriodEditor = false)
        )
    }
}
