package com.daily.life.feature.timetable

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

object WeekCalculator {
    fun currentWeek(startDate: LocalDate, date: LocalDate): Int {
        val daysBetween = ChronoUnit.DAYS.between(startDate, date).toInt()
        return max(1, daysBetween / 7 + 1)
    }
}
