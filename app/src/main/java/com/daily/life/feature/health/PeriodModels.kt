package com.daily.life.feature.health

import com.daily.life.core.datastore.DailyPreferences
import java.time.LocalDate

data class PeriodRecord(
    val id: Long = 0L,
    val startDate: LocalDate,
    val endDate: LocalDate
)

object PeriodPredictionCalculator {
    fun nextStartDate(records: List<PeriodRecord>, cycleDays: Int): LocalDate? =
        records
            .maxByOrNull { it.startDate }
            ?.startDate
            ?.plusDays(
                cycleDays.coerceIn(
                    DailyPreferences.MIN_MENSTRUAL_CYCLE_DAYS,
                    DailyPreferences.MAX_MENSTRUAL_CYCLE_DAYS
                ).toLong()
            )
}
