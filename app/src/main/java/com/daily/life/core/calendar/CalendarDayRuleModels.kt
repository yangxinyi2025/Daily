package com.daily.life.core.calendar

import java.time.LocalDate

enum class CalendarDayKind {
    REGULAR_WORKDAY,
    REGULAR_REST_DAY,
    HOLIDAY_REST,
    MAKEUP_WORKDAY
}

enum class CalendarRuleSource {
    WEEKEND_DEFAULT,
    SYSTEM_CALENDAR,
    BUILTIN_ICS,
    CUSTOM_ICS,
    MANUAL
}

data class CalendarDayRule(
    val date: LocalDate,
    val kind: CalendarDayKind,
    val source: CalendarRuleSource,
    val sourceId: String? = null,
    val label: String? = null,
    val sourceDayOfWeek: Int? = null,
    val sourceDate: LocalDate? = null,
    val updatedAt: Long
)

data class HolidayCalendarSourceInput(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean,
    val enabled: Boolean
)
