package com.daily.life.feature.health

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthPresentationTest {
    @Test
    fun dashboardPresentationUsesTheHealthOverviewHeadingForTheMonthlySummary() {
        val presentation = healthDashboardPresentation(
            state = HealthState(selectedMonth = YearMonth.of(2026, 8))
        )

        assertEquals("本月健康概览", presentation.overviewTitle)
    }

    @Test
    fun dashboardPresentationCombinesWeightGoalPredictionAndHistory() {
        val presentation = healthDashboardPresentation(
            state = HealthState(
                selectedMonth = YearMonth.of(2026, 8),
                weights = listOf(
                    WeightRecord(1, Instant.parse("2026-08-22T08:00:00Z"), 119.0),
                    WeightRecord(2, Instant.parse("2026-08-12T08:00:00Z"), 120.0)
                ),
                targetWeightJin = 110.0,
                periodRecords = listOf(
                    PeriodRecord(7, LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 26))
                ),
                nextPeriodStart = LocalDate.of(2026, 9, 21),
                menstrualCycleDays = 30
            ),
            zoneId = ZoneId.of("Asia/Shanghai")
        )

        assertEquals("本月健康概览", presentation.overviewTitle)
        assertEquals("119.0", presentation.latestWeight)
        assertEquals("目标 110.0 斤", presentation.targetSummary)
        assertEquals("119.0 斤", presentation.currentWeight)
        assertEquals("110.0 斤", presentation.targetWeight)
        assertEquals("8月22日", presentation.lastPeriod)
        assertEquals("9月21日", presentation.predictedPeriod)
        assertEquals("本月记录 2 次", presentation.monthRecordSummary)
        assertEquals("下次预计 9月21日", presentation.nextPeriodSummary)
        assertEquals("当前按 30 天周期预测", presentation.cycleSummary)
        assertEquals("8月22日 — 8月26日", presentation.history.first().dateRange)
    }

    @Test
    fun dashboardPresentationUsesCompactEmptyPeriodCopy() {
        val presentation = healthDashboardPresentation(
            state = HealthState(
                selectedMonth = YearMonth.of(2026, 8),
                menstrualCycleDays = 30
            )
        )

        assertEquals("还没有经期记录", presentation.nextPeriodSummary)
        assertEquals("记录后将按 30 天周期预测", presentation.cycleSummary)
    }

    @Test
    fun dashboardPresentationUsesNaturalCopyForEmptyHealthData() {
        val presentation = healthDashboardPresentation(
            state = HealthState(selectedMonth = YearMonth.of(2026, 8))
        )

        assertEquals("尚未记录", presentation.latestWeight)
        assertEquals("尚未设置", presentation.targetSummary)
        assertEquals("尚未记录", presentation.currentWeight)
        assertEquals("尚未设置", presentation.targetWeight)
        assertEquals("尚未记录", presentation.lastPeriod)
        assertEquals("尚未预测", presentation.predictedPeriod)
        assertEquals("尚未记录", presentation.monthRecordSummary)
    }
}
