package com.daily.life.feature.health

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class HealthDashboardPresentation(
    val overviewTitle: String,
    val latestWeight: String,
    val targetSummary: String,
    val monthRecordSummary: String,
    val nextPeriodSummary: String,
    val cycleSummary: String,
    val history: List<PeriodHistoryPresentation>
)

internal data class PeriodHistoryPresentation(
    val record: PeriodRecord,
    val dateRange: String
)

internal fun healthDashboardPresentation(
    state: HealthState,
    zoneId: ZoneId = ZoneId.systemDefault()
): HealthDashboardPresentation {
    val latestWeight = state.weights.maxByOrNull { it.recordedAt }?.weightJin
    val monthlyRecordCount = state.weights.count {
        java.time.YearMonth.from(it.recordedAt.atZone(zoneId)) == state.selectedMonth
    }
    return HealthDashboardPresentation(
        overviewTitle = "本月健康概览",
        latestWeight = latestWeight?.let(::formatHealthPresentationWeight) ?: "--",
        targetSummary = "目标 ${state.targetWeightJin?.let(::formatHealthPresentationWeight) ?: "未设置"} 斤",
        monthRecordSummary = "本月记录 $monthlyRecordCount 次",
        nextPeriodSummary = state.nextPeriodStart
            ?.let { "下次预计 ${it.format(HEALTH_MONTH_DAY_FORMATTER)}" }
            ?: "还没有经期记录",
        cycleSummary = if (state.nextPeriodStart == null) {
            "记录后将按 ${state.menstrualCycleDays} 天周期预测"
        } else {
            "当前按 ${state.menstrualCycleDays} 天周期预测"
        },
        history = state.periodRecords
            .sortedByDescending { it.startDate }
            .map { record ->
                PeriodHistoryPresentation(
                    record = record,
                    dateRange = "${record.startDate.format(HEALTH_MONTH_DAY_FORMATTER)} — ${record.endDate.format(HEALTH_MONTH_DAY_FORMATTER)}"
                )
            }
    )
}

private fun formatHealthPresentationWeight(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private val HEALTH_MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
