package com.daily.life.feature.health

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthOverviewCardsTest {
    @Test
    fun overviewDisplayUsesLoadingCopyUntilEachDataGroupHasLoaded() {
        val display = healthOverviewDisplay(
            state = HealthState(selectedMonth = YearMonth.of(2026, 9)),
            presentation = presentation()
        )

        assertEquals("正在读取体重记录…", display.currentWeight)
        assertEquals("正在读取体重记录…", display.targetWeight)
        assertEquals("正在读取经期记录…", display.lastPeriod)
        assertEquals("正在读取经期记录…", display.nextPeriod)
    }

    private fun presentation() = HealthDashboardPresentation(
        overviewTitle = "本月健康概览",
        latestWeight = "尚未记录",
        targetSummary = "尚未设置",
        currentWeight = "121.0 斤",
        targetWeight = "110.0 斤",
        lastPeriod = "8月22日",
        predictedPeriod = "9月21日",
        monthRecordSummary = "尚未记录",
        nextPeriodSummary = "还没有经期记录",
        cycleSummary = "记录后将按 30 天周期预测",
        history = emptyList()
    )
}
