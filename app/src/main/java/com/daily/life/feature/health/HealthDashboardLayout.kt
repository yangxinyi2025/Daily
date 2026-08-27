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

internal fun healthDashboardSectionOrder(
    showWeightEditor: Boolean,
    showPeriodEditor: Boolean
): List<HealthDashboardSection> = buildList {
    add(HealthDashboardSection.Header)
    add(HealthDashboardSection.Overview)
    add(HealthDashboardSection.Trend)
    if (showWeightEditor) add(HealthDashboardSection.WeightEntry)
    add(HealthDashboardSection.Goal)
    add(HealthDashboardSection.Period)
    if (showPeriodEditor) add(HealthDashboardSection.PeriodEntry)
    add(HealthDashboardSection.History)
}
