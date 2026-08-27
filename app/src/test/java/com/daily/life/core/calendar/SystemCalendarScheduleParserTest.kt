package com.daily.life.core.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCalendarScheduleParserTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun parsesHolidayAndEachDateOfAnAllDayRange() {
        val days = parseSystemCalendarSpecialDay(
            event(
                start = "2026-10-01T00:00:00Z",
                end = "2026-10-08T00:00:00Z",
                title = "国庆节放假"
            ),
            zone
        )

        assertEquals((1..7).map { LocalDate.of(2026, 10, it) }, days.map { it.date })
        assertTrue(days.all { it.kind == SystemCalendarSpecialDayKind.Holiday })
    }

    @Test
    fun recognizesHolidayNamedOnlyByItsPublicHolidayTitle() {
        val day = parseSystemCalendarSpecialDay(
            event(
                start = "2026-10-01T00:00:00Z",
                end = "2026-10-02T00:00:00Z",
                title = "国庆节"
            ),
            zone
        ).single()

        assertEquals(SystemCalendarSpecialDayKind.Holiday, day.kind)
    }

    @Test
    fun parsesExplicitMakeupWeekdayFromChineseTitle() {
        val day = parseSystemCalendarSpecialDay(
            event(
                start = "2026-10-10T00:00:00Z",
                end = "2026-10-11T00:00:00Z",
                title = "国庆补周五"
            ),
            zone
        ).single()

        assertEquals(SystemCalendarSpecialDayKind.MakeupWorkday, day.kind)
        assertEquals(5, day.sourceDayOfWeek)
    }

    @Test
    fun leavesSourceDayUnknownWhenTitleOnlySaysMakeupWorkday() {
        val day = parseSystemCalendarSpecialDay(
            event(
                start = "2026-10-10T00:00:00Z",
                end = "2026-10-11T00:00:00Z",
                title = "调休上班"
            ),
            zone
        ).single()

        assertEquals(SystemCalendarSpecialDayKind.MakeupWorkday, day.kind)
        assertNull(day.sourceDayOfWeek)
        assertNull(day.sourceDate)
    }

    @Test
    fun parsesExplicitSourceDate() {
        val day = parseSystemCalendarSpecialDay(
            event(
                start = "2026-10-10T00:00:00Z",
                end = "2026-10-11T00:00:00Z",
                title = "调休补10月9日"
            ),
            zone
        ).single()

        assertEquals(LocalDate.of(2026, 10, 9), day.sourceDate)
        assertEquals(5, day.sourceDayOfWeek)
    }

    @Test
    fun makeupWorkdayWinsWhenHolidayAndMakeupOverlap() {
        val days = mergeSystemCalendarSpecialDays(
            listOf(
                SystemCalendarSpecialDay(
                    date = LocalDate.of(2026, 10, 10),
                    kind = SystemCalendarSpecialDayKind.Holiday,
                    sourceDayOfWeek = null,
                    sourceDate = null,
                    label = "休"
                ),
                SystemCalendarSpecialDay(
                    date = LocalDate.of(2026, 10, 10),
                    kind = SystemCalendarSpecialDayKind.MakeupWorkday,
                    sourceDayOfWeek = 5,
                    sourceDate = null,
                    label = "补周五"
                )
            )
        )

        assertEquals(SystemCalendarSpecialDayKind.MakeupWorkday, days.single().kind)
        assertEquals(5, days.single().sourceDayOfWeek)
    }

    @Test
    fun ignoresDailyCourseCalendarEvents() {
        val days = parseSystemCalendarSpecialDay(
            event(
                start = "2026-10-10T08:00:00Z",
                end = "2026-10-10T09:00:00Z",
                title = "课程：高等数学"
            ),
            zone
        )

        assertTrue(days.isEmpty())
    }

    private fun event(start: String, end: String, title: String) = SystemCalendarScheduleEvent(
        startAt = Instant.parse(start),
        endAt = Instant.parse(end),
        title = title,
        description = null
    )
}
