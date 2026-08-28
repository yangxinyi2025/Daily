package com.daily.life.core.calendar

import java.time.LocalDate
import java.time.ZoneId

internal fun parseSystemCalendarSpecialDay(
    event: SystemCalendarScheduleEvent,
    zone: ZoneId
): List<SystemCalendarSpecialDay> {
    val startDate = event.startAt.atZone(zone).toLocalDate()
    val endDate = event.endAt.atZone(zone).toLocalDate()
    val dates = if (endDate.isAfter(startDate)) {
        generateSequence(startDate) { date ->
            date.plusDays(1).takeIf { it.isBefore(endDate) }
        }.toList()
    } else {
        listOf(startDate)
    }
    val classification = classifyChineseSpecialDay(event.title, event.description, startDate) ?: return emptyList()
    val label = event.title?.trim().orEmpty().ifBlank { kindLabel(classification.kind) }
    return dates.map { date ->
        SystemCalendarSpecialDay(
            date = date,
            kind = when (classification.kind) {
                CalendarDayKind.HOLIDAY_REST -> SystemCalendarSpecialDayKind.Holiday
                CalendarDayKind.MAKEUP_WORKDAY -> SystemCalendarSpecialDayKind.MakeupWorkday
                else -> return emptyList()
            },
            sourceDayOfWeek = classification.sourceDayOfWeek,
            sourceDate = classification.sourceDate,
            label = label
        )
    }
}

internal fun systemCalendarSpecialDayKindFor(
    title: String?,
    description: String?
): SystemCalendarSpecialDayKind? {
    val text = normalizedCalendarText(title, description)
    if (text.isBlank() || text.contains("课程：") || text.contains("课程:")) return null
    return when {
        text.contains("调休") ||
            text.contains("补班") ||
            text.contains("上班") ||
            text == "班" ||
            MAKEUP_SOURCE_DAY_PATTERN.containsMatchIn(text) ->
            SystemCalendarSpecialDayKind.MakeupWorkday
        text.contains("节假日") || text.contains("放假") || text.contains("休息") ||
            text.contains("休假") || text == "休" || text.endsWith("休") || text.contains(" 休") ||
            CHINESE_PUBLIC_HOLIDAY_NAMES.any(text::contains) ->
            SystemCalendarSpecialDayKind.Holiday
        else -> null
    }
}

internal fun mergeSystemCalendarSpecialDays(
    days: Iterable<SystemCalendarSpecialDay>
): List<SystemCalendarSpecialDay> = days
    .groupBy(SystemCalendarSpecialDay::date)
    .mapNotNull { (_, sameDate) ->
        sameDate.firstOrNull { it.kind == SystemCalendarSpecialDayKind.MakeupWorkday }
            ?: sameDate.firstOrNull()
    }
    .sortedBy(SystemCalendarSpecialDay::date)

private fun kindLabel(kind: CalendarDayKind): String = when (kind) {
    CalendarDayKind.HOLIDAY_REST -> "休"
    CalendarDayKind.MAKEUP_WORKDAY -> "班"
    else -> ""
}

private val CHINESE_PUBLIC_HOLIDAY_NAMES = listOf(
    "元旦",
    "春节",
    "清明节",
    "劳动节",
    "端午节",
    "中秋节",
    "国庆节"
)
