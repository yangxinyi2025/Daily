package com.daily.life.core.calendar

import com.daily.life.core.database.CalendarDayOverrideEntity
import com.daily.life.core.database.HolidayCalendarEventEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarDayRuleMergerTest {
    @Test
    fun manualOverrideBeatsIcsSystemAndWeekend() {
        val date = LocalDate.of(2026, 10, 3)

        val merged = CalendarDayRuleMerger.merge(
            startDate = date,
            endDate = date,
            cachedIcsDays = listOf(
                event(
                    sourceId = "builtin",
                    date = date,
                    kind = CalendarDayKind.HOLIDAY_REST,
                    summary = "国庆节"
                )
            ),
            systemDays = listOf(
                systemDay(
                    date = date,
                    kind = SystemCalendarSpecialDayKind.Holiday,
                    label = "系统节假日"
                )
            ),
            overrides = listOf(
                override(
                    date = date,
                    kind = CalendarDayKind.MAKEUP_WORKDAY,
                    note = "临时补班"
                )
            )
        )

        assertEquals(
            listOf(
                CalendarDayRule(
                    date = date,
                    kind = CalendarDayKind.MAKEUP_WORKDAY,
                    source = CalendarRuleSource.MANUAL,
                    sourceId = null,
                    label = "临时补班",
                    sourceDayOfWeek = null,
                    sourceDate = null,
                    updatedAt = 2L
                )
            ),
            merged
        )
    }

    @Test
    fun icsBeatsSystemCalendar() {
        val date = LocalDate.of(2026, 10, 4)

        val merged = CalendarDayRuleMerger.merge(
            startDate = date,
            endDate = date,
            cachedIcsDays = listOf(
                event(
                    sourceId = "custom",
                    date = date,
                    kind = CalendarDayKind.HOLIDAY_REST,
                    summary = "ICS 节假日"
                )
            ),
            systemDays = listOf(
                systemDay(
                    date = date,
                    kind = SystemCalendarSpecialDayKind.MakeupWorkday,
                    label = "系统调休"
                )
            ),
            overrides = emptyList()
        )

        assertEquals(CalendarDayKind.HOLIDAY_REST, merged.single().kind)
        assertEquals(CalendarRuleSource.CUSTOM_ICS, merged.single().source)
        assertEquals("custom", merged.single().sourceId)
        assertEquals("ICS 节假日", merged.single().label)
    }

    @Test
    fun makeupBeatsHolidayWithinOnePriority() {
        val date = LocalDate.of(2026, 10, 5)

        val merged = CalendarDayRuleMerger.merge(
            startDate = date,
            endDate = date,
            cachedIcsDays = listOf(
                event(
                    sourceId = "builtin",
                    date = date,
                    kind = CalendarDayKind.HOLIDAY_REST,
                    summary = "节日"
                ),
                event(
                    sourceId = "builtin",
                    date = date,
                    kind = CalendarDayKind.MAKEUP_WORKDAY,
                    summary = "补班"
                )
            ),
            systemDays = emptyList(),
            overrides = emptyList()
        )

        assertEquals(CalendarDayKind.MAKEUP_WORKDAY, merged.single().kind)
        assertEquals("builtin", merged.single().sourceId)
        assertEquals("节日 / 补班", merged.single().label)
    }

    @Test
    fun missingSourcesUseSaturdayRestDefault() {
        val saturday = LocalDate.of(2026, 10, 10)

        val merged = CalendarDayRuleMerger.merge(
            startDate = saturday,
            endDate = saturday,
            cachedIcsDays = emptyList(),
            systemDays = emptyList(),
            overrides = emptyList()
        )

        assertEquals(CalendarDayKind.REGULAR_REST_DAY, merged.single().kind)
        assertEquals(CalendarRuleSource.WEEKEND_DEFAULT, merged.single().source)
        assertNull(merged.single().sourceId)
        assertNull(merged.single().label)
    }

    @Test
    fun sourceSummariesAreRetainedWithoutDuplicateRules() {
        val date = LocalDate.of(2026, 10, 6)

        val merged = CalendarDayRuleMerger.merge(
            startDate = date,
            endDate = date,
            cachedIcsDays = listOf(
                event(
                    sourceId = "builtin",
                    date = date,
                    kind = CalendarDayKind.HOLIDAY_REST,
                    summary = "国庆假期"
                ),
                event(
                    sourceId = "custom",
                    date = date,
                    kind = CalendarDayKind.HOLIDAY_REST,
                    summary = "自定义说明"
                )
            ),
            systemDays = emptyList(),
            overrides = emptyList()
        )

        assertEquals(1, merged.size)
        assertEquals(CalendarDayKind.HOLIDAY_REST, merged.single().kind)
        assertEquals("builtin,custom", merged.single().sourceId)
        assertEquals("国庆假期 / 自定义说明", merged.single().label)
    }

    @Test
    fun mergeGeneratesEveryDateInInclusiveRange() {
        val start = LocalDate.of(2026, 10, 1)
        val end = LocalDate.of(2026, 10, 3)

        val merged = CalendarDayRuleMerger.merge(
            startDate = start,
            endDate = end,
            cachedIcsDays = emptyList(),
            systemDays = emptyList(),
            overrides = emptyList()
        )

        assertEquals(listOf(start, start.plusDays(1), end), merged.map(CalendarDayRule::date))
    }

    private fun event(
        sourceId: String,
        date: LocalDate,
        kind: CalendarDayKind,
        summary: String,
        builtIn: Boolean = sourceId == "builtin"
    ) = HolidayCalendarEventEntity(
        sourceId = sourceId,
        eventKey = "$sourceId:$date:$kind:$summary",
        startDate = date,
        endDateInclusive = date,
        summary = summary,
        description = null,
        kind = kind,
        sourceDayOfWeek = null,
        sourceDate = null,
        fetchedAt = 1L
    )

    private fun systemDay(
        date: LocalDate,
        kind: SystemCalendarSpecialDayKind,
        label: String
    ) = SystemCalendarSpecialDay(
        date = date,
        kind = kind,
        sourceDayOfWeek = null,
        sourceDate = null,
        label = label
    )

    private fun override(
        date: LocalDate,
        kind: CalendarDayKind,
        note: String
    ) = CalendarDayOverrideEntity(
        date = date,
        targetKind = kind,
        note = note,
        createdAt = 1L,
        updatedAt = 2L
    )
}
