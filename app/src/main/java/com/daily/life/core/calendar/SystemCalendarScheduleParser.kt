package com.daily.life.core.calendar

import java.time.LocalDate
import java.time.ZoneId

internal fun parseSystemCalendarSpecialDay(
    event: SystemCalendarScheduleEvent,
    zone: ZoneId
): List<SystemCalendarSpecialDay> {
    val text = normalizedCalendarText(event.title, event.description)
    val kind = systemCalendarSpecialDayKindFor(event.title, event.description) ?: return emptyList()
    val startDate = event.startAt.atZone(zone).toLocalDate()
    val endDate = event.endAt.atZone(zone).toLocalDate()
    val dates = if (endDate.isAfter(startDate)) {
        generateSequence(startDate) { date ->
            date.plusDays(1).takeIf { it.isBefore(endDate) }
        }.toList()
    } else {
        listOf(startDate)
    }
    val sourceDate = parseSourceDate(text, startDate)
    val sourceDayOfWeek = parseSourceDayOfWeek(text) ?: sourceDate?.dayOfWeek?.value
    val label = event.title?.trim().orEmpty().ifBlank { kindLabel(kind) }
    return dates.map { date ->
        SystemCalendarSpecialDay(
            date = date,
            kind = kind,
            sourceDayOfWeek = sourceDayOfWeek,
            sourceDate = sourceDate,
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
        text.contains("调休") || text.contains("补班") || text.contains("上班") || text == "班" ->
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

private fun parseSourceDayOfWeek(text: String): Int? {
    val match = Regex("补\\s*(?:上班|课)?\\s*(?:周|星期)\\s*([一二三四五六日天1-7])")
        .find(text)
        ?: return null
    return when (match.groupValues[1]) {
        "一", "1" -> 1
        "二", "2" -> 2
        "三", "3" -> 3
        "四", "4" -> 4
        "五", "5" -> 5
        "六", "6" -> 6
        "日", "天", "7" -> 7
        else -> null
    }
}

private fun normalizedCalendarText(title: String?, description: String?): String =
    listOfNotNull(title, description)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun parseSourceDate(text: String, actualDate: LocalDate): LocalDate? {
    val match = Regex("补\\s*(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日?").find(text)
        ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: actualDate.year
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private fun kindLabel(kind: SystemCalendarSpecialDayKind): String = when (kind) {
    SystemCalendarSpecialDayKind.Holiday -> "休"
    SystemCalendarSpecialDayKind.MakeupWorkday -> "班"
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
