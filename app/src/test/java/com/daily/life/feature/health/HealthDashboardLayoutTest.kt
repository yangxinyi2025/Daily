package com.daily.life.feature.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthDashboardLayoutTest {
    @Test
    fun inlineEditorsAreNeverInsertedIntoTheDashboard() {
        assertEquals(
            listOf(
                HealthDashboardSection.Header,
                HealthDashboardSection.Overview,
                HealthDashboardSection.Trend,
                HealthDashboardSection.Goal,
                HealthDashboardSection.Period,
                HealthDashboardSection.History
            ),
            healthDashboardSectionOrder()
        )
    }
}
