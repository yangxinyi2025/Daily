package com.daily.life.feature.timetable

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterPeriodScheduleTest {
    @Test
    fun defaultScheduleKeepsTheExistingTwelveClassTimes() {
        val labels = periodLabels(defaultSemesterPeriodTimes()).associateBy(TimetablePeriodLabel::period)

        assertEquals("1\n08:00\n08:45", labels.getValue(1).label)
        assertEquals("2\n08:50\n09:35", labels.getValue(2).label)
        assertEquals("12\n20:10\n20:55", labels.getValue(12).label)
    }

    @Test
    fun validationRejectsAnEndTimeBeforeItsStartTime() {
        val broken = defaultSemesterPeriodTimes().toMutableList().also {
            it[0] = SemesterPeriodTime(1, LocalTime.of(8, 45), LocalTime.of(8, 0))
        }

        assertTrue(validateSemesterPeriodTimes(broken).orEmpty().contains("结束"))
    }

    @Test
    fun validationRejectsOverlappingPeriods() {
        val broken = defaultSemesterPeriodTimes().toMutableList().also {
            it[1] = SemesterPeriodTime(2, LocalTime.of(8, 40), LocalTime.of(9, 35))
        }

        assertTrue(validateSemesterPeriodTimes(broken).orEmpty().contains("重叠"))
    }

    @Test
    fun validationAcceptsTheDefaultSchedule() {
        assertNull(validateSemesterPeriodTimes(defaultSemesterPeriodTimes()))
    }
}
