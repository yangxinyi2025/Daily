package com.daily.life.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

class TimetablePeriodLabelsTest {
    @Test
    fun usesTheConfiguredTwelveClassTimeRanges() {
        assertEquals(
            listOf(
                "1\n08:00\n08:45", "2\n08:50\n09:35", "3\n09:50\n10:35",
                "4\n10:40\n11:25", "5\n11:30\n12:15", "6\n13:30\n14:15",
                "7\n14:20\n15:05", "8\n15:20\n16:05", "9\n16:10\n16:55",
                "10\n18:30\n19:15", "11\n19:20\n20:05", "12\n20:10\n20:55"
            ),
            defaultPeriodLabels().map(TimetablePeriodLabel::label)
        )
    }
}
