package com.daily.life.feature.timetable

import com.daily.life.core.calendar.SystemCalendarSpecialDay
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.CalendarDayRule
import com.daily.life.core.calendar.CalendarRuleSource
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableCalendarMappingTest {
    private val semesterStart = LocalDate.of(2026, 9, 14)

    @Test
    fun normalWeekUsesActualWeekday() {
        val slots = mapWeekToScheduleSlots(
            semesterStartDate = semesterStart,
            selectedWeek = 1,
            specialDays = emptyList(),
            confirmedAdjustments = emptyMap()
        )

        assertEquals(semesterStart, slots.first().actualDate)
        assertEquals(1, slots.first().courseDayOfWeek)
        assertFalse(slots.first().isHoliday)
    }

    @Test
    fun holidaySlotHasNoCourseSource() {
        val holiday = specialDay(LocalDate.of(2026, 9, 17), SystemCalendarSpecialDayKind.Holiday)

        val slot = mapWeekToScheduleSlots(semesterStart, 1, listOf(holiday), emptyMap())
            .single { it.actualDate == holiday.date }

        assertEquals(null, slot.courseDayOfWeek)
        assertTrue(slot.isHoliday)
        assertFalse(slot.needsMakeupConfirmation)
    }

    @Test
    fun confirmedMakeupUsesSelectedSourceWeekdayOnActualWeekendDate() {
        val actualDate = LocalDate.of(2026, 9, 19)
        val makeup = specialDay(actualDate, SystemCalendarSpecialDayKind.MakeupWorkday)

        val slot = mapWeekToScheduleSlots(
            semesterStart,
            selectedWeek = 1,
            specialDays = listOf(makeup),
            confirmedAdjustments = mapOf(actualDate to MakeupCourseSource(5, WeekParity.ODD))
        ).single { it.actualDate == actualDate }

        assertEquals(5, slot.courseDayOfWeek)
        assertFalse(slot.isHoliday)
        assertFalse(slot.needsMakeupConfirmation)
    }

    @Test
    fun unconfirmedWeekendMakeupDoesNotChooseACourseForTheUser() {
        val actualDate = LocalDate.of(2026, 9, 19)
        val makeup = specialDay(actualDate, SystemCalendarSpecialDayKind.MakeupWorkday)

        val slot = mapWeekToScheduleSlots(semesterStart, 1, listOf(makeup), emptyMap())
            .single { it.actualDate == actualDate }

        assertEquals(null, slot.courseDayOfWeek)
        assertTrue(slot.needsMakeupConfirmation)
    }

    @Test
    fun sameDateHolidayOverridesMakeup() {
        val actualDate = LocalDate.of(2026, 9, 19)
        val slot = mapWeekToScheduleSlots(
            semesterStart,
            1,
            listOf(
                specialDay(actualDate, SystemCalendarSpecialDayKind.Holiday),
                specialDay(actualDate, SystemCalendarSpecialDayKind.MakeupWorkday)
            ),
            mapOf(actualDate to MakeupCourseSource(5, WeekParity.ODD))
        ).single { it.actualDate == actualDate }

        assertEquals(null, slot.courseDayOfWeek)
        assertTrue(slot.isHoliday)
    }

    @Test
    fun reminderOccurrencesReturnActualMakeupDateAndSkipHolidayDate() {
        val holidayDate = LocalDate.of(2026, 9, 18)
        val makeupDate = LocalDate.of(2026, 9, 19)
        val dates = courseOccurrenceDates(
            semesterStartDate = semesterStart,
            week = 1,
            courseDayOfWeek = 5,
            specialDays = listOf(
                specialDay(holidayDate, SystemCalendarSpecialDayKind.Holiday),
                specialDay(makeupDate, SystemCalendarSpecialDayKind.MakeupWorkday)
            ),
            confirmedAdjustments = mapOf(makeupDate to MakeupCourseSource(5, WeekParity.ODD))
        )

        assertEquals(listOf(makeupDate), dates)
    }

    @Test
    fun confirmedMakeupAddsTheActualDateForTheSourceWeekday() {
        val actual = LocalDate.of(2026, 9, 5)

        assertEquals(
            listOf(LocalDate.of(2026, 9, 4), actual),
            courseOccurrenceDates(
                LocalDate.of(2026, 8, 31),
                1,
                5,
                listOf(makeupDay(actual)),
                mapOf(actual to MakeupCourseSource(5, WeekParity.ODD))
            )
        )
    }

    @Test
    fun unifiedHolidayRuleSuppressesCourseAndMakeupUsesTheUserSelection() {
        val holidayDate = LocalDate.of(2026, 9, 17)
        val makeupDate = LocalDate.of(2026, 9, 19)
        val rules = listOf(
            rule(holidayDate, CalendarDayKind.HOLIDAY_REST),
            rule(makeupDate, CalendarDayKind.MAKEUP_WORKDAY, sourceDayOfWeek = 5)
        )

        val slots = mapWeekToScheduleSlotsWithRules(
            semesterStart,
            1,
            rules,
            confirmedAdjustments = mapOf(makeupDate to MakeupCourseSource(4, WeekParity.ODD))
        )
        assertEquals(null, slots.single { it.actualDate == holidayDate }.courseDayOfWeek)
        assertEquals(4, slots.single { it.actualDate == makeupDate }.courseDayOfWeek)
        assertEquals(
            listOf(makeupDate),
            courseOccurrenceDatesWithRules(
                semesterStart,
                1,
                4,
                rules,
                confirmedAdjustments = mapOf(makeupDate to MakeupCourseSource(4, WeekParity.ODD))
            )
        )
    }

    @Test
    fun weekendMakeupWithoutAnExplicitSourceWaitsForUserSelection() {
        val actualDate = LocalDate.of(2026, 9, 19)
        val rules = listOf(rule(actualDate, CalendarDayKind.MAKEUP_WORKDAY))
        val slot = mapWeekToScheduleSlotsWithRules(semesterStart, 1, rules, emptyMap())
            .single { it.actualDate == actualDate }
        assertEquals(null, slot.courseDayOfWeek)
        assertTrue(slot.needsMakeupConfirmation)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 18)),
            courseOccurrenceDatesWithRules(semesterStart, 1, 5, rules, emptyMap())
        )
    }

    @Test
    fun sundayMakeupWithoutAnExplicitSourceWaitsForUserSelection() {
        val actualDate = LocalDate.of(2026, 9, 20)
        val rules = listOf(rule(actualDate, CalendarDayKind.MAKEUP_WORKDAY))

        val slot = mapWeekToScheduleSlotsWithRules(semesterStart, 1, rules, emptyMap())
            .single { it.actualDate == actualDate }

        assertEquals(null, slot.courseDayOfWeek)
        assertTrue(slot.needsMakeupConfirmation)
        assertEquals(
            listOf(semesterStart),
            courseOccurrenceDatesWithRules(semesterStart, 1, 1, rules, emptyMap())
        )
    }

    @Test
    fun classOverridesWinOverUnifiedCalendarRule() {
        val holidayDate = LocalDate.of(2026, 9, 17)
        val rules = listOf(rule(holidayDate, CalendarDayKind.HOLIDAY_REST))
        val slots = mapWeekToScheduleSlotsWithRules(
            semesterStart,
            1,
            rules,
            emptyMap(),
            mapOf(holidayDate to ClassOverride.HAS_CLASS)
        )
        assertEquals(4, slots.single { it.actualDate == holidayDate }.courseDayOfWeek)
        assertEquals(
            listOf(holidayDate),
            courseOccurrenceDatesWithRules(
                semesterStart,
                1,
                4,
                rules,
                emptyMap(),
                mapOf(holidayDate to ClassOverride.HAS_CLASS)
            )
        )
    }

    @Test
    fun makeupOnlyRunsCoursesFromTheChosenParity() {
        val makeupDate = LocalDate.of(2026, 9, 26)
        val rules = listOf(rule(makeupDate, CalendarDayKind.MAKEUP_WORKDAY))
        val confirmed = mapOf(makeupDate to MakeupCourseSource(5, WeekParity.EVEN))

        assertEquals(
            listOf(LocalDate.of(2026, 9, 18)),
            courseOccurrenceDatesWithRules(semesterStart, 1, 5, rules, confirmed)
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 25), makeupDate),
            courseOccurrenceDatesWithRules(semesterStart, 2, 5, rules, confirmed)
        )
    }

    private fun specialDay(date: LocalDate, kind: SystemCalendarSpecialDayKind) = SystemCalendarSpecialDay(
        date = date,
        kind = kind,
        sourceDayOfWeek = null,
        sourceDate = null,
        label = kind.name
    )

    private fun makeupDay(actualDate: LocalDate) = SystemCalendarSpecialDay(
        date = actualDate,
        kind = SystemCalendarSpecialDayKind.MakeupWorkday,
        sourceDayOfWeek = null,
        sourceDate = null,
        label = "调休上班"
    )

    private fun rule(date: LocalDate, kind: CalendarDayKind, sourceDayOfWeek: Int? = null) = CalendarDayRule(
        date = date,
        kind = kind,
        source = CalendarRuleSource.BUILTIN_ICS,
        sourceDayOfWeek = sourceDayOfWeek,
        updatedAt = 1L
    )
}
