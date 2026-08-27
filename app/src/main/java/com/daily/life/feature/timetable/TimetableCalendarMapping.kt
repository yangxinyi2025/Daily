package com.daily.life.feature.timetable

import com.daily.life.core.calendar.SystemCalendarSpecialDay
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import com.daily.life.core.calendar.mergeSystemCalendarSpecialDays
import java.time.LocalDate

internal data class TimetableScheduleSlot(
    val actualDate: LocalDate,
    val week: Int,
    val courseDayOfWeek: Int?,
    val isHoliday: Boolean,
    val needsMakeupConfirmation: Boolean
)

internal fun mapWeekToScheduleSlots(
    semesterStartDate: LocalDate,
    selectedWeek: Int,
    specialDays: Iterable<SystemCalendarSpecialDay>,
    confirmedAdjustments: Map<LocalDate, Int>
): List<TimetableScheduleSlot> {
    val weekStart = semesterStartDate.plusWeeks((selectedWeek - 1).coerceAtLeast(0).toLong())
    val specialDaysByDate = mergeSystemCalendarSpecialDays(specialDays).associateBy(SystemCalendarSpecialDay::date)
    return (0L..6L).map { offset ->
        val actualDate = weekStart.plusDays(offset)
        val specialDay = specialDaysByDate[actualDate]
        when (specialDay?.kind) {
            SystemCalendarSpecialDayKind.Holiday -> TimetableScheduleSlot(
                actualDate = actualDate,
                week = selectedWeek,
                courseDayOfWeek = null,
                isHoliday = true,
                needsMakeupConfirmation = false
            )
            SystemCalendarSpecialDayKind.MakeupWorkday -> {
                val sourceDay = confirmedAdjustments[actualDate]?.takeIf { it in 1..7 }
                TimetableScheduleSlot(
                    actualDate = actualDate,
                    week = selectedWeek,
                    courseDayOfWeek = sourceDay,
                    isHoliday = false,
                    needsMakeupConfirmation = sourceDay == null
                )
            }
            null -> TimetableScheduleSlot(
                actualDate = actualDate,
                week = selectedWeek,
                courseDayOfWeek = actualDate.dayOfWeek.value,
                isHoliday = false,
                needsMakeupConfirmation = false
            )
        }
    }
}

internal fun courseOccurrenceDates(
    semesterStartDate: LocalDate,
    week: Int,
    courseDayOfWeek: Int,
    specialDays: Iterable<SystemCalendarSpecialDay>,
    confirmedAdjustments: Map<LocalDate, Int>
): List<LocalDate> {
    require(courseDayOfWeek in 1..7)
    val weekStart = semesterStartDate.plusWeeks((week - 1).coerceAtLeast(0).toLong())
    val weekEnd = weekStart.plusDays(7)
    val specialDaysByDate = mergeSystemCalendarSpecialDays(specialDays).associateBy(SystemCalendarSpecialDay::date)
    val dates = mutableSetOf<LocalDate>()
    val nominalDate = weekStart.plusDays((courseDayOfWeek - 1).toLong())
    val nominalSpecialDay = specialDaysByDate[nominalDate]
    when (nominalSpecialDay?.kind) {
        SystemCalendarSpecialDayKind.Holiday -> Unit
        SystemCalendarSpecialDayKind.MakeupWorkday -> {
            if (confirmedAdjustments[nominalDate] == courseDayOfWeek) dates += nominalDate
        }
        null -> dates += nominalDate
    }
    specialDaysByDate.values
        .filter { it.date >= weekStart && it.date < weekEnd }
        .filter { it.kind == SystemCalendarSpecialDayKind.MakeupWorkday }
        .filter { confirmedAdjustments[it.date] == courseDayOfWeek }
        .forEach { dates += it.date }
    return dates.sorted()
}
