package com.daily.life.feature.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthDashboardLayoutTest {
    @Test
    fun redesignedDashboardContainsOnlyHeaderWeightAndPeriodOverviews() {
        assertEquals(
            listOf(
                HealthDashboardSection.Header,
                HealthDashboardSection.WeightOverview,
                HealthDashboardSection.PeriodOverview
            ),
            healthDashboardSectionOrder()
        )
    }
}
