package com.daily.life.feature.health

internal enum class HealthDashboardSection {
    Header,
    Overview,
    Trend,
    WeightEntry,
    Goal,
    Period,
    PeriodEntry,
    History
}

internal fun healthDashboardSectionOrder(): List<HealthDashboardSection> =
    listOf(
        HealthDashboardSection.Header,
        HealthDashboardSection.Overview,
        HealthDashboardSection.Trend,
        HealthDashboardSection.Goal,
        HealthDashboardSection.Period,
        HealthDashboardSection.History
    )
