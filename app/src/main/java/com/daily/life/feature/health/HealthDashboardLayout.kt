package com.daily.life.feature.health

internal enum class HealthDashboardSection {
    Header,
    Overview,
    QuickRecord,
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
        HealthDashboardSection.QuickRecord,
        HealthDashboardSection.Trend,
        HealthDashboardSection.Goal,
        HealthDashboardSection.Period,
        HealthDashboardSection.History
    )
