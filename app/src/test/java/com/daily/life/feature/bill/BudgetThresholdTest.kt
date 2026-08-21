package com.daily.life.feature.bill

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetThresholdTest {
    @Test
    fun budgetNotifiesEachThresholdOnlyOnce() {
        val first = BudgetThresholdCalculator.evaluate(5_000L, 10_000L, emptySet())
        val second = BudgetThresholdCalculator.evaluate(7_500L, 10_000L, first.triggeredPercentages)
        val third = BudgetThresholdCalculator.evaluate(11_000L, 10_000L, second.triggeredPercentages)

        assertEquals(setOf(50), first.newNotifications)
        assertEquals(setOf(70), second.newNotifications)
        assertEquals(setOf(90, 110), third.newNotifications)
        assertEquals(setOf(50, 70, 90, 110), third.triggeredPercentages)
    }

    @Test
    fun zeroBudgetDoesNotTriggerThresholds() {
        val result = BudgetThresholdCalculator.evaluate(99_999L, 0L, emptySet())

        assertEquals(emptySet<Int>(), result.newNotifications)
        assertEquals(emptySet<Int>(), result.triggeredPercentages)
    }
}
