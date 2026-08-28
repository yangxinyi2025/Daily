package com.daily.life.feature.schedule

import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.CalendarDayRule
import com.daily.life.core.calendar.CalendarRuleSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleCalendarPresentationTest {
    @Test
    fun calendarBadgesUseOnlyExplicitSystemCalendarHolidayMarkers() {
        assertEquals(ScheduleCalendarBadge.Holiday, systemCalendarBadgeFor("国庆节 休", null))
        assertEquals(ScheduleCalendarBadge.Holiday, systemCalendarBadgeFor("国庆节休", null))
        assertEquals(ScheduleCalendarBadge.Holiday, systemCalendarBadgeFor("节假日", "放假安排"))
        assertEquals(ScheduleCalendarBadge.AdjustedWorkday, systemCalendarBadgeFor("国庆补班", null))
        assertEquals(ScheduleCalendarBadge.AdjustedWorkday, systemCalendarBadgeFor("调休上班", null))
        assertNull(systemCalendarBadgeFor("国庆节", null))
        assertNull(systemCalendarBadgeFor("课程：高等数学", null))
    }

    @Test
    fun systemCalendarEntriesAreMappedWithoutAnyFallbackDates() {
        val badges = systemCalendarBadgesFor(
            entries = listOf(
                SystemCalendarDayEntry(
                    startAt = Instant.parse("2026-10-01T00:00:00Z"),
                    endAt = Instant.parse("2026-10-02T00:00:00Z"),
                    title = "国庆节 休"
                ),
                SystemCalendarDayEntry(
                    startAt = Instant.parse("2026-10-11T00:00:00Z"),
                    endAt = Instant.parse("2026-10-12T00:00:00Z"),
                    title = "国庆补班"
                ),
                SystemCalendarDayEntry(
                    startAt = Instant.parse("2026-10-11T00:00:00Z"),
                    endAt = Instant.parse("2026-10-12T00:00:00Z"),
                    title = "国庆节休"
                )
            ),
            zone = ZoneOffset.UTC
        )

        assertEquals(ScheduleCalendarBadge.Holiday, badges[LocalDate.of(2026, 10, 1)])
        assertEquals(ScheduleCalendarBadge.AdjustedWorkday, badges[LocalDate.of(2026, 10, 11)])
        assertNull(badges[LocalDate.of(2026, 10, 2)])
    }

    @Test
    fun editorAppearsBeforeGoalSectionsAfterItIsOpened() {
        assertEquals(
            listOf(
                ScheduleDashboardSection.Calendar,
                ScheduleDashboardSection.DaySummary,
                ScheduleDashboardSection.QuickCreate,
                ScheduleDashboardSection.Editor
            ),
            scheduleDashboardSectionOrder(showEditor = true)
        )
    }

    @Test
    fun calendarDayRulesMapToDistinctExistingBadgesAndManualMarker() {
        val updatedAt = Instant.parse("2026-08-28T08:00:00Z").toEpochMilli()
        assertEquals(
            ScheduleCalendarBadge.RestDay,
            badgeForCalendarRule(
                CalendarDayRule(
                    date = LocalDate.of(2026, 8, 1),
                    kind = CalendarDayKind.REGULAR_REST_DAY,
                    source = CalendarRuleSource.WEEKEND_DEFAULT,
                    updatedAt = updatedAt
                )
            )
        )
        assertEquals(
            ScheduleCalendarBadge.Holiday,
            badgeForCalendarRule(
                CalendarDayRule(
                    date = LocalDate.of(2026, 10, 1),
                    kind = CalendarDayKind.HOLIDAY_REST,
                    source = CalendarRuleSource.BUILTIN_ICS,
                    updatedAt = updatedAt
                )
            )
        )
        assertEquals(
            ScheduleCalendarBadge.AdjustedWorkday,
            badgeForCalendarRule(
                CalendarDayRule(
                    date = LocalDate.of(2026, 10, 10),
                    kind = CalendarDayKind.MAKEUP_WORKDAY,
                    source = CalendarRuleSource.CUSTOM_ICS,
                    updatedAt = updatedAt
                )
            )
        )
        assertEquals(
            "改",
            manualMarkerForCalendarRule(
                CalendarDayRule(
                    date = LocalDate.of(2026, 8, 3),
                    kind = CalendarDayKind.REGULAR_WORKDAY,
                    source = CalendarRuleSource.MANUAL,
                    updatedAt = updatedAt
                )
            )
        )
    }
}
