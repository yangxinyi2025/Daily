package com.daily.life.feature.health

import java.time.ZoneId
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class HealthDashboardPresentation(
    val overviewTitle: String,
    val latestWeight: String,
    val targetSummary: String,
    val currentWeight: String,
    val targetWeight: String,
    val lastPeriod: String,
    val predictedPeriod: String,
    val monthRecordSummary: String,
    val nextPeriodSummary: String,
    val cycleSummary: String,
    val history: List<PeriodHistoryPresentation>
)

internal data class PeriodHistoryPresentation(
    val record: PeriodRecord,
    val dateRange: String
)

internal enum class WeightTrendRange(val days: Long, val label: String) {
    Days7(7, "近7天"),
    Days30(30, "近30天"),
    Days90(90, "近90天")
}

internal enum class HealthContentState { Loading, Empty, Loaded, Error }

internal fun overviewContentState(
    isLoaded: Boolean,
    hasData: Boolean,
    errorMessage: String?
): HealthContentState = when {
    errorMessage != null -> HealthContentState.Error
    !isLoaded -> HealthContentState.Loading
    hasData -> HealthContentState.Loaded
    else -> HealthContentState.Empty
}

internal fun daysUntilPeriod(nextStart: LocalDate?, today: LocalDate): Int? =
    nextStart?.let { ChronoUnit.DAYS.between(today, it).coerceAtLeast(0).toInt() }

internal fun selectWeightTrendPoints(
    weights: List<WeightRecord>,
    range: WeightTrendRange,
    today: LocalDate,
    zoneId: ZoneId
): List<WeightPoint> {
    val firstDay = today.minusDays(range.days - 1)
    return weights.map { WeightPoint(it.recordedAt.atZone(zoneId).toLocalDate(), it.weightJin) }
        .filter { it.date in firstDay..today }
        .sortedBy(WeightPoint::date)
}

internal fun healthDashboardPresentation(
    state: HealthState,
    zoneId: ZoneId = ZoneId.systemDefault()
): HealthDashboardPresentation {
    val latestWeight = state.weights.maxByOrNull { it.recordedAt }?.weightJin
    val latestPeriodStart = state.periodRecords.maxByOrNull(PeriodRecord::startDate)?.startDate
    val monthlyRecordCount = state.weights.count {
        java.time.YearMonth.from(it.recordedAt.atZone(zoneId)) == state.selectedMonth
    }
    return HealthDashboardPresentation(
        overviewTitle = "本月健康概览",
        latestWeight = latestWeight?.let(::formatHealthPresentationWeight) ?: "尚未记录",
        targetSummary = state.targetWeightJin
            ?.let { "目标 ${formatHealthPresentationWeight(it)} 斤" }
            ?: "尚未设置",
        currentWeight = latestWeight?.let { "${formatHealthPresentationWeight(it)} 斤" } ?: "尚未记录",
        targetWeight = state.targetWeightJin
            ?.let { "${formatHealthPresentationWeight(it)} 斤" }
            ?: "尚未设置",
        lastPeriod = latestPeriodStart?.format(HEALTH_MONTH_DAY_FORMATTER) ?: "尚未记录",
        predictedPeriod = state.nextPeriodStart?.format(HEALTH_MONTH_DAY_FORMATTER) ?: "尚未预测",
        monthRecordSummary = if (monthlyRecordCount == 0) "尚未记录" else "本月记录 $monthlyRecordCount 次",
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
