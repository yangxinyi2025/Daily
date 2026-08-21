package com.daily.life.feature.health

import com.daily.life.core.database.ActivityType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object MonthlyReportCalculator {
    fun calculate(
        month: YearMonth,
        weights: List<WeightRecord>,
        activities: List<ActivityRecord>,
        targetWeightJin: Double?,
        zone: ZoneId = ZoneId.of("UTC")
    ): LocalReport {
        val currentWeights = weights
            .filter { YearMonth.from(it.recordedAt.atZone(zone)) == month }
            .sortedBy(WeightRecord::recordedAt)
        val previousWeights = weights
            .filter { YearMonth.from(it.recordedAt.atZone(zone)) == month.minusMonths(1) }
            .sortedBy(WeightRecord::recordedAt)
        val currentActivities = activities
            .filter { YearMonth.from(it.recordedAt.atZone(zone)) == month }
            .sortedBy(ActivityRecord::recordedAt)

        val monthAverage = currentWeights
            .map(WeightRecord::weightJin)
            .takeIf { it.isNotEmpty() }
            ?.average()
        val monthOverMonth = currentWeights.lastOrNull()?.weightJin?.let { latest ->
            previousWeights.lastOrNull()?.weightJin?.let { previous -> latest - previous }
        }
        val weeklyAverages = currentWeights
            .groupBy { it.recordedAt.toLocalDate(zone).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }
            .mapValues { (_, records) -> records.map(WeightRecord::weightJin).average() }
        val recentStart = month.atEndOfMonth().minusDays(29)
        val recentWeights = currentWeights.filter { record ->
            val date = record.recordedAt.toLocalDate(zone)
            date in recentStart..month.atEndOfMonth()
        }.map { record ->
            WeightPoint(record.recordedAt.toLocalDate(zone), record.weightJin)
        }
        val totalSteps = currentActivities.sumOf { it.steps ?: 0L }
        val walkingSteps = currentActivities
            .filter { it.activityType == ActivityType.WALK }
            .sumOf { it.steps ?: 0L }
        val runningSteps = currentActivities
            .filter { it.activityType == ActivityType.RUN }
            .sumOf { it.steps ?: 0L }
        val state = when {
            currentWeights.isEmpty() && currentActivities.isEmpty() -> ReportDataState.EMPTY
            currentWeights.size < 2 && currentActivities.isEmpty() -> ReportDataState.INSUFFICIENT
            else -> ReportDataState.READY
        }

        return LocalReport(
            month = month,
            monthAverageJin = monthAverage,
            monthOverMonthJin = monthOverMonth,
            weeklyAveragesJin = weeklyAverages,
            recentWeights = recentWeights,
            totalSteps = totalSteps,
            walkingSteps = walkingSteps,
            runningSteps = runningSteps,
            totalDistanceMeters = currentActivities.sumOf { it.distanceMeters ?: 0.0 },
            totalDurationMinutes = currentActivities.sumOf { it.durationMinutes ?: 0 },
            targetDifferenceJin = monthAverage?.let { average -> targetWeightJin?.let { average - it } },
            dataState = state,
            weightTrendSummary = weightTrendSummary(monthAverage, monthOverMonth, state),
            activitySummary = activitySummary(totalSteps, walkingSteps, runningSteps, currentActivities.size)
        )
    }

    private fun weightTrendSummary(
        average: Double?,
        change: Double?,
        state: ReportDataState
    ): String = when (state) {
        ReportDataState.EMPTY -> "还没有健康记录"
        ReportDataState.INSUFFICIENT -> "记录还不够，继续记录后可查看趋势"
        ReportDataState.READY -> when {
            average == null -> "本月暂无体重数据"
            change == null -> String.format(Locale.US, "本月平均 %.1f 斤", average)
            else -> String.format(Locale.US, "本月平均 %.1f 斤，较上月 %.1f 斤", average, change)
        }
    }

    private fun activitySummary(totalSteps: Long, walkingSteps: Long, runningSteps: Long, count: Int): String =
        if (count == 0) {
            "本月暂无活动数据"
        } else {
            "本月 $totalSteps 步（步行 $walkingSteps，跑步 $runningSteps）"
        }

    private fun Instant.toLocalDate(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()
}
