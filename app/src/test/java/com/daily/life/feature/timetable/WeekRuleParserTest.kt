package com.daily.life.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRuleParserTest {
    @Test
    fun parsesRangeAndOddEvenRules() {
        assertEquals(setOf(1, 2, 3, 4), WeekRuleParser.parse("1-4周").weeks)
        assertEquals(WeekParity.ODD, WeekRuleParser.parse("单周").parity)
        assertEquals(setOf(2, 4, 6), WeekRuleParser.parse("2,4,6周").weeks)
    }

    @Test
    fun supportsChineseSeparatorsAndMixedExpressions() {
        val result = WeekRuleParser.parse("1-4周，6周, 8-10 周 双周")

        assertEquals(setOf(1, 2, 3, 4, 6, 8, 9, 10), result.weeks)
        assertEquals(WeekParity.EVEN, result.parity)
        assertEquals("1-4周，6周, 8-10 周 双周", result.rawText)
    }

    @Test
    fun preservesWarningsForUnparsedSegments() {
        val result = WeekRuleParser.parse("1-4周 实验周待定")

        assertEquals(setOf(1, 2, 3, 4), result.weeks)
        assertTrue(result.warnings.any { it.contains("实验周待定") })
    }

    @Test
    fun parityMarkersAreFullyConsumed() {
        val odd = WeekRuleParser.parse("单周")
        val even = WeekRuleParser.parse("双周")

        assertEquals(WeekParity.ODD, odd.parity)
        assertTrue(odd.warnings.isEmpty())
        assertEquals(WeekParity.EVEN, even.parity)
        assertTrue(even.warnings.isEmpty())
    }
}
