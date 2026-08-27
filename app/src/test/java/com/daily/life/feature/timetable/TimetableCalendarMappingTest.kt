package com.daily.life.feature.timetable

import com.daily.life.core.calendar.SystemCalendarSpecialDay
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
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
            confirmedAdjustments = mapOf(actualDate to 5)
        ).single { it.actualDate == actualDate }

        assertEquals(5, slot.courseDayOfWeek)
        assertFalse(slot.isHoliday)
        assertFalse(slot.needsMakeupConfirmation)
    }

    @Test
    fun unconfirmedMakeupDoesNotCopyAnyCourse() {
        val actualDate = LocalDate.of(2026, 9, 19)
        val makeup = specialDay(actualDate, SystemCalendarSpecialDayKind.MakeupWorkday)

        val slot = mapWeekToScheduleSlots(semesterStart, 1, listOf(makeup), emptyMap())
            .single { it.actualDate == actualDate }

        assertEquals(null, slot.courseDayOfWeek)
        assertTrue(slot.needsMakeupConfirmation)
    }

    @Test
    fun sameDateMakeupOverridesHoliday() {
        val actualDate = LocalDate.of(2026, 9, 19)
        val slot = mapWeekToScheduleSlots(
            semesterStart,
            1,
            listOf(
                specialDay(actualDate, SystemCalendarSpecialDayKind.Holiday),
                specialDay(actualDate, SystemCalendarSpecialDayKind.MakeupWorkday)
            ),
            mapOf(actualDate to 5)
        ).single { it.actualDate == actualDate }

        assertEquals(5, slot.courseDayOfWeek)
        assertFalse(slot.isHoliday)
    }

    @Test
    fun reminderOccurrencesReturnActualMakeupDateAndSkipHolidayDate() {
        val holidayDate = LocalDate.of(2026, 9, 17)
        val makeupDate = LocalDate.of(2026, 9, 19)
        val dates = courseOccurrenceDates(
            semesterStartDate = semesterStart,
            week = 1,
            courseDayOfWeek = 5,
            specialDays = listOf(
                specialDay(holidayDate, SystemCalendarSpecialDayKind.Holiday),
                specialDay(makeupDate, SystemCalendarSpecialDayKind.MakeupWorkday)
            ),
            confirmedAdjustments = mapOf(makeupDate to 5)
        )

        assertEquals(listOf(makeupDate), dates)
    }

    private fun specialDay(date: LocalDate, kind: SystemCalendarSpecialDayKind) = SystemCalendarSpecialDay(
        date = date,
        kind = kind,
        sourceDayOfWeek = null,
        sourceDate = null,
        label = kind.name
    )
}
