package com.daily.life.feature.schedule

import com.daily.life.core.calendar.SystemCalendarScheduleEvent
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import com.daily.life.core.calendar.parseSystemCalendarSpecialDay
import com.daily.life.core.calendar.systemCalendarSpecialDayKindFor
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
    return when (systemCalendarSpecialDayKindFor(title, description)) {
        SystemCalendarSpecialDayKind.Holiday -> ScheduleCalendarBadge.Holiday
        SystemCalendarSpecialDayKind.MakeupWorkday -> ScheduleCalendarBadge.AdjustedWorkday
        null -> null
    }
}

internal fun systemCalendarBadgesFor(
    entries: List<SystemCalendarDayEntry>,
    zone: ZoneId
): Map<LocalDate, ScheduleCalendarBadge> = buildMap {
    entries.forEach { entry ->
        parseSystemCalendarSpecialDay(
            event = SystemCalendarScheduleEvent(entry.startAt, entry.endAt, entry.title, entry.description),
            zone = zone
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
}
