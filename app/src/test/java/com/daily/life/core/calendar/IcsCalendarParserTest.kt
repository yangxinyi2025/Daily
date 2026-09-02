package com.daily.life.core.calendar

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun holidayWordingAndPublicHolidayNameWinOverAmbiguousMakeupText() {
        assertEquals(
            SystemCalendarSpecialDayKind.Holiday,
            systemCalendarSpecialDayKindFor("国庆放假（调休安排）", null)
        )
        assertEquals(
            SystemCalendarSpecialDayKind.Holiday,
            systemCalendarSpecialDayKindFor("国庆节调休安排", null)
        )
    }

    @Test
    fun bareMakeupTextDoesNotCreateAWorkday() {
        assertNull(systemCalendarSpecialDayKindFor("国庆调休安排", null))
        assertEquals(
            SystemCalendarSpecialDayKind.MakeupWorkday,
            systemCalendarSpecialDayKindFor("国庆补班", null)
        )
        assertEquals(
            SystemCalendarSpecialDayKind.MakeupWorkday,
            systemCalendarSpecialDayKindFor("调休上班", null)
        )
    }

    @Test
    fun explicitMakeupTitleWinsOverHolidayArrangementInDescription() {
        val event = parseIcsCalendar(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:makeup-day
            DTSTART:20260509T090000
            DTEND:20260509T180000
            SUMMARY:劳动节 补班 第1天/共1天
            DESCRIPTION:劳动节：5月1日至5日放假调休，共5天。5月9日（周六）上班。
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            source,
            zone
        ).single()

        assertEquals(CalendarDayKind.MAKEUP_WORKDAY, event.kind)
    }

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
    fun keepsLiteralBackslashBeforeNAsPlainText() {
        val events = parseIcsCalendar(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:literal-backslash
            DTSTART;VALUE=DATE:20261010
            DTEND;VALUE=DATE:20261011
            SUMMARY:调休上班\\n通知
            DESCRIPTION:保留\\N和\\n字面量
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            source,
            zone
        )

        val event = events.single()
        assertEquals("调休上班\\n通知", event.title)
        assertEquals("保留\\N和\\n字面量", event.description)
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
    fun throwsWhenFeedHasOnlyMalformedHolidayEvents() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseIcsCalendar(
                """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:broken
                DTSTART;VALUE=DATE:2026-10-01
                DTEND;VALUE=DATE:20261008
                SUMMARY:国庆节放假
                END:VEVENT
                END:VCALENDAR
                """.trimIndent(),
                source,
                zone
            )
        }

        assertTrue(error.message.orEmpty().contains("parse"))
    }

    @Test
    fun throwsWhenFeedStructureIsMalformed() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseIcsCalendar(
                """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:broken
                DTSTART;VALUE=DATE:20261001
                DTEND;VALUE=DATE:20261008
                SUMMARY:国庆节放假
                """.trimIndent(),
                source,
                zone
            )
        }

        assertTrue(error.message.orEmpty().contains("Malformed"))
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

    @Test
    fun acceptsValidEmptyCalendar() {
        val events = parseIcsCalendar(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Daily Life//Holiday Feed//EN
            END:VCALENDAR
            """.trimIndent(),
            source,
            zone
        )

        assertTrue(events.isEmpty())
    }
}
