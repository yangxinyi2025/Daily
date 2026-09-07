package com.daily.life.feature.health

internal enum class HealthDashboardSection {
    Header,
    WeightOverview,
    PeriodOverview
}

internal fun healthDashboardSectionOrder(): List<HealthDashboardSection> =
    listOf(
        HealthDashboardSection.Header,
        HealthDashboardSection.WeightOverview,
        HealthDashboardSection.PeriodOverview
    )
