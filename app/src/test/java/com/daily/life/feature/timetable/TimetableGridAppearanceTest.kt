package com.daily.life.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableGridAppearanceTest {
    @Test
    fun courseColorSlotsAreStableAcrossTheSevenWeekdays() {
        assertEquals(0, timetableCourseColorSlot(1))
        assertEquals(1, timetableCourseColorSlot(2))
        assertEquals(2, timetableCourseColorSlot(3))
        assertEquals(3, timetableCourseColorSlot(4))
        assertEquals(4, timetableCourseColorSlot(5))
        assertEquals(0, timetableCourseColorSlot(6))
        assertEquals(1, timetableCourseColorSlot(7))
    }
}
