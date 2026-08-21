package com.daily.life.feature.health

import com.daily.life.core.database.ActivityType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyReportCalculatorTest {
    @Test
    fun reportContainsWeeklyAverageAndMonthOverMonthChange() {
        val report = MonthlyReportCalculator.calculate(
            month = YearMonth.of(2026, 8),
            weights = listOf(
                weight("2026-07-31", 140.0),
                weight("2026-08-07", 138.0),
                weight("2026-08-14", 136.0)
            ),
            activities = listOf(
                walk("2026-08-01", 5_000),
                run("2026-08-02", 3_000)
            ),
            targetWeightJin = 130.0
        )

        assertEquals(137.0, report.monthAverageJin, 0.01)
        assertEquals(-4.0, report.monthOverMonthJin, 0.01)
        assertEquals(8_000L, report.totalSteps)
        assertEquals(7.0, report.targetDifferenceJin, 0.01)
        assertTrue(report.weeklyAveragesJin.isNotEmpty())
    }

    @Test
    fun reportUsesExplicitEmptyStateWhenNoRecordsExist() {
        val report = MonthlyReportCalculator.calculate(
            month = YearMonth.of(2026, 8),
            weights = emptyList(),
            activities = emptyList(),
            targetWeightJin = null
        )

        assertEquals(null, report.monthAverageJin)
        assertEquals(null, report.monthOverMonthJin)
        assertEquals(0L, report.totalSteps)
        assertEquals(ReportDataState.EMPTY, report.dataState)
    }

    private fun weight(date: String, jin: Double): WeightRecord = WeightRecord(
        recordedAt = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC),
        weightJin = jin,
        source = "TEST"
    )

    private fun walk(date: String, steps: Long): ActivityRecord = activity(date, ActivityType.WALK, steps)

    private fun run(date: String, steps: Long): ActivityRecord = activity(date, ActivityType.RUN, steps)

    private fun activity(date: String, type: ActivityType, steps: Long): ActivityRecord = ActivityRecord(
        recordedAt = Instant.parse("${date}T00:00:00Z"),
        activityType = type,
        steps = steps,
        source = "TEST"
    )
}
