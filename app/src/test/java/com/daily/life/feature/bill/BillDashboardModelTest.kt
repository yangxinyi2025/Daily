package com.daily.life.feature.bill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillDashboardModelTest {
    @Test
    fun balanceIsIncomeMinusExpense() {
        val statistics = BillStatistics(expenseCents = 248_650L, incomeCents = 580_000L)

        assertEquals(331_350L, statistics.balanceCents())
    }

    @Test
    fun categoryItemsAreSortedAndScaledAgainstLargestCategory() {
        val statistics = BillStatistics(
            categoryTotals = mapOf(
                Category.SHOPPING to 100L,
                Category.FOOD to 300L,
                Category.OTHER to 200L,
                Category.TRANSPORT to 0L
            )
        )

        val items = statistics.categoryItems()

        assertEquals(listOf(Category.FOOD, Category.OTHER, Category.SHOPPING), items.map { it.category })
        assertEquals(1f, items[0].progress)
        assertTrue(items[1].progress in 0.66f..0.67f)
        assertTrue(items[2].progress in 0.33f..0.34f)
    }
}
