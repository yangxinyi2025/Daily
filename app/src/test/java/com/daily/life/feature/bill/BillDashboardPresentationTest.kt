package com.daily.life.feature.bill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillDashboardPresentationTest {
    @Test
    fun emptyDashboardUsesTheDesignedBillAndCategoryEmptyStates() {
        val presentation = billDashboardPresentation(BillStatistics())

        assertEquals("0 笔", presentation.transactionCountLabel)
        assertEquals("还没有支出记录", presentation.categoryEmptyLabel)
        assertEquals("当前范围暂无账单", presentation.recentEmptyTitle)
        assertEquals("可以导入 CSV / Excel，或点击右上角记一笔。", presentation.recentEmptyDescription)
        assertTrue(presentation.showEmptyRecentCard)
    }
}
