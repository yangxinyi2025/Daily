package com.daily.life.feature.bill

data class BillCategoryItem(
    val category: Category,
    val amountCents: Long,
    val progress: Float
)

fun BillStatistics.balanceCents(): Long = incomeCents - expenseCents

fun BillStatistics.categoryItems(): List<BillCategoryItem> {
    val sorted = categoryTotals
        .filterValues { it > 0L }
        .toList()
        .sortedByDescending { it.second }
    val largest = sorted.firstOrNull()?.second ?: return emptyList()
    return sorted.map { (category, amount) ->
        BillCategoryItem(
            category = category,
            amountCents = amount,
            progress = (amount.toFloat() / largest.toFloat()).coerceIn(0f, 1f)
        )
    }
}
