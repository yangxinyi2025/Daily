package com.daily.life.feature.schedule

import com.daily.life.core.calendar.SystemCalendarScheduleEvent
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import com.daily.life.core.calendar.mergeSystemCalendarSpecialDays
import com.daily.life.core.calendar.parseSystemCalendarSpecialDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class ScheduleCalendarBadge(val label: String) {
    Holiday("休"),
    AdjustedWorkday("班")
}

internal enum class ScheduleDashboardSection {
    Calendar,
    DaySummary,
    QuickCreate,
    Editor
}

internal fun scheduleDashboardSectionOrder(showEditor: Boolean): List<ScheduleDashboardSection> = buildList {
    add(ScheduleDashboardSection.Calendar)
    add(ScheduleDashboardSection.DaySummary)
    add(ScheduleDashboardSection.QuickCreate)
    if (showEditor) add(ScheduleDashboardSection.Editor)
}

internal data class SystemCalendarDayEntry(
    val startAt: Instant,
    val endAt: Instant,
    val title: String?,
    val description: String? = null
)

internal fun systemCalendarBadgeFor(
    title: String?,
    description: String?
): ScheduleCalendarBadge? {
    val text = listOfNotNull(title, description)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (text.isBlank() || text.contains("课程：") || text.contains("课程:")) return null
    return when {
        text.contains("调休") ||
            text.contains("补班") ||
            text.contains("上班") ||
            text == "班" ||
            Regex("补\\s*(?:上班|课)?\\s*(?:周|星期)\\s*[一二三四五六日天1-7]").containsMatchIn(text) ->
            ScheduleCalendarBadge.AdjustedWorkday
        text.contains("节假日") ||
            text.contains("放假") ||
            text.contains("休息") ||
            text.contains("休假") ||
            text == "休" ||
            text.endsWith("休") ||
            text.contains(" 休") ->
            ScheduleCalendarBadge.Holiday
        else -> null
    }
}

internal fun systemCalendarBadgesFor(
    entries: List<SystemCalendarDayEntry>,
    zone: ZoneId
): Map<LocalDate, ScheduleCalendarBadge> = buildMap {
    mergeSystemCalendarSpecialDays(
        entries.flatMap { entry ->
            parseSystemCalendarSpecialDay(
                event = SystemCalendarScheduleEvent(entry.startAt, entry.endAt, entry.title, entry.description),
                zone = zone
            )
        }
    ).forEach { day ->
        put(
            day.date,
            when (day.kind) {
                SystemCalendarSpecialDayKind.Holiday -> ScheduleCalendarBadge.Holiday
                SystemCalendarSpecialDayKind.MakeupWorkday -> ScheduleCalendarBadge.AdjustedWorkday
            }
        )
    }
}
