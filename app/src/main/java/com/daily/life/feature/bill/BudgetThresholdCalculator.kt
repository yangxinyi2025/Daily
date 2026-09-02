package com.daily.life.feature.bill

import java.math.BigInteger

object BudgetThresholdCalculator {
    private val thresholds = setOf(50, 70, 90, 110)

    fun evaluate(
        spendingCents: Long,
        budgetCents: Long,
        triggered: Set<Int>
    ): BudgetThresholdResult {
        if (budgetCents <= 0L || spendingCents < 0L) {
            return BudgetThresholdResult(emptySet(), triggered)
        }
        val spending = BigInteger.valueOf(spendingCents)
        val budget = BigInteger.valueOf(budgetCents)
        val newNotifications = thresholds.filterTo(linkedSetOf()) { threshold ->
            threshold !in triggered && spending.multiply(BigInteger.valueOf(100L)) >=
                budget.multiply(BigInteger.valueOf(threshold.toLong()))
        }
        return BudgetThresholdResult(
            newNotifications = newNotifications,
            triggeredPercentages = triggered + newNotifications
        )
    }
}
