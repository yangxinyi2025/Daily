package com.daily.life.feature.timetable

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTimetableParserTest {
    @Test
    fun parsesSyntheticPdfIntoPreviewRowsWithoutPersistence() {
        val input = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/timetable-synthetic.pdf")
        )

        val result = PdfTimetableParser().parse(input)

        assertEquals(2, result.courses.size)
        assertTrue(result.unsupportedRows.isEmpty())
        assertTrue(result.warnings.isEmpty())

        val firstCourse = result.courses.first()
        assertEquals("Advanced Mathematics", firstCourse.courseName)
        assertEquals(1, firstCourse.dayOfWeek)
        assertEquals(1, firstCourse.startPeriod)
        assertEquals(2, firstCourse.endPeriod)
        assertEquals(setOf(1, 2, 3, 4), firstCourse.weekRule.weeks)
        assertEquals("Building A101", firstCourse.location)
        assertEquals("Zhang", firstCourse.teacher)
        assertFalse(firstCourse.needsReview)
    }

    @Test
    fun rowsWithRuleWarningsOrMissingFieldsRequireReview() {
        val input = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/timetable-synthetic.pdf")
        )

        val result = PdfTimetableParser().parse(input)
        val reviewRow = result.courses.last()

        assertTrue(reviewRow.needsReview)
        assertTrue(reviewRow.weekRule.warnings.isNotEmpty())
    }

    @Test
    fun closesTheSuppliedStreamAfterCreatingPreview() {
        val bytes = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/timetable-synthetic.pdf")
        ).use { it.readBytes() }
        val input = CloseTrackingInputStream(bytes)

        PdfTimetableParser().parse(input)

        assertTrue(input.closed)
    }

    @Test
    fun initializesPdfBoxOnlyWhenAPdfIsActuallyImported() {
        var initializationCount = 0
        val input = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/timetable-synthetic.pdf")
        )

        PdfTimetableParser { initializationCount += 1 }.parse(input)

        assertEquals(1, initializationCount)
    }

    @Test
    fun recognizesPeriodTimesWithCommonChineseSeparators() {
        val result = PdfTimetableParser().parseExtractedText(
            "第1节 08:00-08:45\n2节 08:50～09:35\n第3节 09:50 至 10:35\n第4节 25:00-11:25"
        )

        assertEquals(LocalTime.of(8, 0), result.parsedPeriodTimes.getValue(1).startTime)
        assertEquals(LocalTime.of(9, 35), result.parsedPeriodTimes.getValue(2).endTime)
        assertEquals(LocalTime.of(10, 35), result.parsedPeriodTimes.getValue(3).endTime)
        assertFalse(result.parsedPeriodTimes.containsKey(4))
    }

    private class CloseTrackingInputStream(bytes: ByteArray) : java.io.ByteArrayInputStream(bytes) {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
