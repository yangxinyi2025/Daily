package com.daily.life.core.calendar

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsCalendarParserTest {
    private val source = IcsCalendarSource(
        id = "builtin-china-public-holidays",
        name = "China Holidays",
        url = "https://example.com/china.ics",
        builtIn = true
    )
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesAllDayExclusiveEndAndChineseKinds() {
        val events = parseIcsCalendar(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:holiday
            DTSTART;VALUE=DATE:20261001
            DTEND;VALUE=DATE:20261008
            SUMMARY:国庆节放假
            DESCRIPTION:连休七天
            END:VEVENT
            BEGIN:VEVENT
            UID:makeup-known
            DTSTART;VALUE=DATE:20261010
            DTEND;VALUE=DATE:20261011
            SUMMARY:国庆补周五
            DESCRIPTION:调休上班
            END:VEVENT
            BEGIN:VEVENT
            UID:makeup-unknown
            DTSTART;VALUE=DATE:20261011
            DTEND;VALUE=DATE:20261012
            SUMMARY:调休上班
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            source,
            zone
        )

        assertEquals(3, events.size)

        val holiday = events.first { it.eventKey == "holiday" }
        assertEquals(LocalDate.of(2026, 10, 1), holiday.startDate)
        assertEquals(LocalDate.of(2026, 10, 8), holiday.endExclusiveDate)
        assertEquals(CalendarDayKind.HOLIDAY_REST, holiday.kind)

        val makeupKnown = events.first { it.eventKey == "makeup-known" }
        assertEquals(CalendarDayKind.MAKEUP_WORKDAY, makeupKnown.kind)
        assertEquals(5, makeupKnown.sourceDayOfWeek)
        assertNull(makeupKnown.sourceDate)

        val makeupUnknown = events.first { it.eventKey == "makeup-unknown" }
        assertEquals(CalendarDayKind.MAKEUP_WORKDAY, makeupUnknown.kind)
        assertNull(makeupUnknown.sourceDayOfWeek)
        assertNull(makeupUnknown.sourceDate)
    }

    @Test
    fun parsesTimedEventsInShanghaiAndUnescapesText() {
        val events = parseIcsCalendar(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:timed
            DTSTART:20261001T160000Z
            DTEND:20261001T180000Z
            SUMMARY:调休上班\,通知
            DESCRIPTION:第一行\n第二行
             \,第三行
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            source,
            zone
        )

        val event = events.single()
        assertEquals(LocalDate.of(2026, 10, 2), event.startDate)
        assertEquals(LocalDate.of(2026, 10, 3), event.endExclusiveDate)
        assertEquals("调休上班,通知", event.title)
        assertEquals("第一行\n第二行,第三行", event.description)
        assertEquals(CalendarDayKind.MAKEUP_WORKDAY, event.kind)
    }

    @Test
    fun skipsMalformedEventWithoutDroppingValidOnes() {
        val events = parseIcsCalendar(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:broken
            DTEND;VALUE=DATE:20261002
            SUMMARY:国庆节放假
            END:VEVENT
            BEGIN:VEVENT
            UID:valid
            DTSTART;VALUE=DATE:20261003
            DTEND;VALUE=DATE:20261004
            SUMMARY:国庆节放假
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            source,
            zone
        )

        assertEquals(1, events.size)
        assertEquals("valid", events.single().eventKey)
        assertEquals(CalendarDayKind.HOLIDAY_REST, events.single().kind)
    }

    @Test
    fun ignoresEventsWithoutRecognizedHolidayMeaning() {
        val events = parseIcsCalendar(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:plain
            DTSTART;VALUE=DATE:20261003
            DTEND;VALUE=DATE:20261004
            SUMMARY:朋友聚会
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            source,
            zone
        )

        assertTrue(events.isEmpty())
    }
}
