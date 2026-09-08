package com.daily.life.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCourseFieldParserTest {
    @Test
    fun extractsLabeledFieldsAcrossLineBreaksAndRemovesWhitespace() {
        val fields = PdfCourseFieldParser.parse("/场地:教三508/教师:俞\n彬/教学班")

        assertEquals("教三508", fields.location)
        assertEquals("俞彬", fields.teacher)
        assertTrue(fields.warnings.isEmpty())
    }

    @Test
    fun normalizesFullWidthLabelsAndCharactersWithoutLosingHyphens() {
        val fields = PdfCourseFieldParser.parse("／场地：理１－\n４０３／教师：俞\n彬／教学班")

        assertEquals("理1-403", fields.location)
        assertEquals("俞彬", fields.teacher)
        assertTrue(fields.warnings.isEmpty())
    }

    @Test
    fun recognizesOnlineAndNamedVenuesAsLocations() {
        val online = PdfCourseFieldParser.parse("/场地:线上课/教师:李四/教学班")
        val namedVenue = PdfCourseFieldParser.parse("/场地:创新创业中心/教师:王五/教学班")

        assertEquals("线上课", online.location)
        assertEquals("李四", online.teacher)
        assertTrue(online.warnings.isEmpty())
        assertEquals("创新创业中心", namedVenue.location)
        assertEquals("王五", namedVenue.teacher)
        assertTrue(namedVenue.warnings.isEmpty())
    }

    @Test
    fun swapsFieldsOnlyWhenBothValuesStronglyMatchTheOppositeType() {
        val swapped = PdfCourseFieldParser.parse("/场地:张三/教师:教三508/教学班")
        val oneSidedMismatch = PdfCourseFieldParser.parse("/场地:张三/教师:李四/教学班")

        assertEquals("教三508", swapped.location)
        assertEquals("张三", swapped.teacher)
        assertTrue(swapped.warnings.isEmpty())
        assertEquals("张三", oneSidedMismatch.location)
        assertEquals("李四", oneSidedMismatch.teacher)
        assertTrue(oneSidedMismatch.warnings.containsKey(TimetablePreviewField.Location))
        assertFalse(oneSidedMismatch.warnings.containsKey(TimetablePreviewField.Teacher))
    }

    @Test
    fun preservesUnreliableOrBlankValuesAndFlagsTheirPreviewFields() {
        val unreliable = PdfCourseFieldParser.parse("/场地:未安排/教师:李四/教学班")
        val blank = PdfCourseFieldParser.parse("/场地:/教师:/教学班")

        assertEquals("未安排", unreliable.location)
        assertEquals("李四", unreliable.teacher)
        assertTrue(unreliable.warnings.containsKey(TimetablePreviewField.Location))
        assertTrue(unreliable.warnings.getValue(TimetablePreviewField.Location).isNotBlank())
        assertEquals("", blank.location)
        assertEquals("", blank.teacher)
        assertTrue(blank.warnings.containsKey(TimetablePreviewField.Location))
        assertTrue(blank.warnings.containsKey(TimetablePreviewField.Teacher))
    }
}
