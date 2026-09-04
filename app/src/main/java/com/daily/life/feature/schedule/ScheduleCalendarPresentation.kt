package com.daily.life.feature.schedule

import com.daily.life.core.calendar.SystemCalendarScheduleEvent
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.CalendarDayRule
import com.daily.life.core.calendar.CalendarRuleSource
import com.daily.life.core.calendar.mergeSystemCalendarSpecialDays
import com.daily.life.core.calendar.parseSystemCalendarSpecialDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ScheduleCalendarBadge(val label: String) {
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

internal data class SelectedDayScheduleContent(
    val events: List<ScheduleEvent>
) {
    val isEmpty: Boolean
        get() = events.isEmpty()
}

internal fun selectedDayScheduleContent(selectedEvents: List<ScheduleEvent>): SelectedDayScheduleContent =
    SelectedDayScheduleContent(events = selectedEvents)

internal fun quickCreatePreset(action: ScheduleQuickAction): ScheduleEditorState = when (action) {
    ScheduleQuickAction.EXAM -> ScheduleEditorState(title = "考试")
    ScheduleQuickAction.BIRTHDAY -> ScheduleEditorState(title = "生日", repeatYearly = true)
    ScheduleQuickAction.SMALL_THING -> ScheduleEditorState(title = "小事")
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

internal fun badgeForCalendarRule(rule: CalendarDayRule): ScheduleCalendarBadge? = when (rule.kind) {
    CalendarDayKind.REGULAR_WORKDAY -> null
    CalendarDayKind.REGULAR_REST_DAY -> null
    CalendarDayKind.HOLIDAY_REST -> ScheduleCalendarBadge.Holiday
    CalendarDayKind.MAKEUP_WORKDAY -> ScheduleCalendarBadge.AdjustedWorkday
}

internal fun manualMarkerForCalendarRule(rule: CalendarDayRule): String? =
    if (rule.source == CalendarRuleSource.MANUAL) "改" else null

internal fun calendarDayKindLabel(kind: CalendarDayKind): String = when (kind) {
    CalendarDayKind.REGULAR_WORKDAY -> "工作日"
    CalendarDayKind.REGULAR_REST_DAY -> "周末休息"
    CalendarDayKind.HOLIDAY_REST -> "节假日休息"
    CalendarDayKind.MAKEUP_WORKDAY -> "调休上班"
}
