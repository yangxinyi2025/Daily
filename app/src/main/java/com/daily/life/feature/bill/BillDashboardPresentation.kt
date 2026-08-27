package com.daily.life.feature.bill

internal data class BillDashboardPresentation(
    val transactionCountLabel: String,
    val categoryEmptyLabel: String,
    val recentEmptyTitle: String,
    val recentEmptyDescription: String,
    val showEmptyRecentCard: Boolean
)

internal fun billDashboardPresentation(statistics: BillStatistics): BillDashboardPresentation =
    BillDashboardPresentation(
        transactionCountLabel = "${statistics.count} 笔",
        categoryEmptyLabel = "还没有支出记录",
        recentEmptyTitle = "当前范围暂无账单",
        recentEmptyDescription = "可以导入 CSV / Excel，或点击右上角记一笔。",
        showEmptyRecentCard = statistics.transactions.isEmpty()
    )
