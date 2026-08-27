package com.daily.life.core.calendar

import java.time.Instant
import java.time.LocalDate

internal data class SystemCalendarScheduleEvent(
    val startAt: Instant,
    val endAt: Instant,
    val title: String?,
    val description: String?
)

enum class SystemCalendarSpecialDayKind {
    Holiday,
    MakeupWorkday
}

data class SystemCalendarSpecialDay(
    val date: LocalDate,
    val kind: SystemCalendarSpecialDayKind,
    val sourceDayOfWeek: Int?,
    val sourceDate: LocalDate?,
    val label: String
)
