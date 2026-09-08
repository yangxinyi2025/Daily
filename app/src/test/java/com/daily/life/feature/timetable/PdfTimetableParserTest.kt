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

    @Test
    fun layoutParserKeepsTeacherAndLocationWhenChineseMetadataLabelsWrapAcrossLines() {
        val result = parseLayout(
            "生理学(甲)",
            "(1-2节)1-5周/校区:浙大城市",
            "学院/场地:教三",
            "508/教",
            "师:俞",
            "彬/教学班:(2026-2027-1)-B04009",
            "/教学班组成:临床医学2503",
            "/学分:4.0"
        )

        val course = result.courses.single()
        assertEquals("教三508", course.location)
        assertEquals("俞彬", course.teacher)
    }

    private fun parseLayout(vararg lines: String): TimetableParseResult {
        val parser = PdfTimetableParser()
        val chunkClass = Class.forName(
            "com.daily.life.feature.timetable.PdfTimetableParser\$LayoutTextChunk"
        )
        val constructor = chunkClass.getDeclaredConstructor(
            Float::class.javaPrimitiveType,
            String::class.java
        ).apply { isAccessible = true }
        val chunks = lines.map { line -> constructor.newInstance(104f, line) }
        val method = PdfTimetableParser::class.java.getDeclaredMethod("parseLayout", List::class.java)
            .apply { isAccessible = true }

        return method.invoke(parser, chunks) as TimetableParseResult
    }

    @Test
    fun layoutCellKeepsSameNamedFragmentsWithTheirOwnMultilineFields() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "数据结构",
                "(1-2节) 1-8周 /校区:主校区 /场地:理1-",
                "403 /教师:张",
                "三 /教学班:CS101 /学分:3",
                "数据结构",
                "(3-4节) 9-16周 /校区:主校区 /场地:教三",
                "508 /教师:李四 /教学班:CS102 /学分:3"
            )
        )

        assertEquals(2, result.courses.size)
        assertEquals(listOf("数据结构", "数据结构"), result.courses.map { it.courseName })
        assertEquals(listOf(1, 3), result.courses.map { it.startPeriod })
        assertEquals(listOf(setOf(1, 2, 3, 4, 5, 6, 7, 8), setOf(9, 10, 11, 12, 13, 14, 15, 16)), result.courses.map { it.weekRule.weeks })
        assertEquals(listOf("理1-403", "教三508"), result.courses.map { it.location })
        assertEquals(listOf("张三", "李四"), result.courses.map { it.teacher })
        assertEquals(listOf("主校区", "主校区"), result.courses.map { it.campus })
        assertEquals(listOf("CS101", "CS102"), result.courses.map { it.courseCode })
        assertEquals(listOf(3.0, 3.0), result.courses.map { it.credits })
        assertTrue(result.courses.none { it.needsReview })
    }

    @Test
    fun layoutCellKeepsBareWrappedLocationAndTeacherValueLines() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "数据结构",
                "(1-2节) 1-8周 /校区:主校区 /场地:理1-",
                "403",
                "/教师:张",
                "三 /教学班:CS101 /学分:3"
            )
        )

        val course = result.courses.single()
        assertEquals("理1-403", course.location)
        assertEquals("张三", course.teacher)
        assertFalse(course.needsReview)
    }

    @Test
    fun layoutCellRequiresReviewWhenTaggedFieldsAreLowConfidence() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "课程",
                "(1-2节) 1-8周 /场地:待定 /教师:未知 /教学班:A /学分:2"
            )
        )

        assertEquals(1, result.courses.size)
        assertTrue(result.courses.single().needsReview)
    }

    @Test
    fun layoutCellKeepsLocationValueOnItsOwnLineInsideTheFragment() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "课程",
                "(1-2节) 1-8周 /校区:主校区",
                "/场地:",
                "理1-403",
                "/教师:张三 /教学班:A /学分:2"
            )
        )

        assertEquals("理1-403", result.courses.single().location)
        assertEquals("张三", result.courses.single().teacher)
    }

    @Test
    fun layoutCellKeepsTeacherValueOnItsOwnLineInsideTheFragment() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "课程",
                "(1-2节) 1-8周 /校区:主校区 /场地:理1-403",
                "/教师:",
                "张三",
                "/教学班:A /学分:2"
            )
        )

        assertEquals("理1-403", result.courses.single().location)
        assertEquals("张三", result.courses.single().teacher)
    }

    @Test
    fun layoutCellDoesNotTreatCourseAfterEmptyLocationLabelAsAFieldContinuation() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "前序课程",
                "(1-2节) 1-8周 /校区:主校区",
                "/场地:",
                "后续课程",
                "(3-4节) 9-16周 /校区:主校区 /场地:理1-403 /教师:李四 /教学班:B /学分:2"
            )
        )

        assertEquals(listOf("前序课程", "后续课程"), result.courses.map { it.courseName })
        assertFalse(result.courses.first().rawRow.contains("后续课程"))
        assertEquals(3, result.courses.last().startPeriod)
        assertEquals("理1-403", result.courses.last().location)
        assertEquals("李四", result.courses.last().teacher)
    }

    @Test
    fun layoutCellDoesNotTreatDistantCourseAfterEmptyLocationLabelAsAFieldContinuation() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "前序课程",
                "(1-2节) 1-8周 /校区:主校区",
                "/场地:",
                "后续课程",
                "星期二",
                "周次说明",
                "(3-4节) 9-16周 /校区:主校区 /场地:理1-403 /教师:李四 /教学班:B /学分:2"
            )
        )

        assertEquals(listOf("前序课程", "后续课程"), result.courses.map { it.courseName })
        assertEquals(3, result.courses.last().startPeriod)
    }

    @Test
    fun layoutCellDoesNotTreatCourseAfterEmptyTeacherLabelAsAFieldContinuation() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "前序课程",
                "(1-2节) 1-8周 /校区:主校区 /场地:理1-403",
                "/教师:",
                "后续课程",
                "(3-4节) 9-16周 /校区:主校区 /场地:理2-404 /教师:李四 /教学班:B /学分:2"
            )
        )

        assertEquals(listOf("前序课程", "后续课程"), result.courses.map { it.courseName })
        assertFalse(result.courses.first().rawRow.contains("后续课程"))
        assertEquals(3, result.courses.last().startPeriod)
        assertEquals("理2-404", result.courses.last().location)
        assertEquals("李四", result.courses.last().teacher)
    }

    @Test
    fun layoutCellUsesFullWidthCampusBoundaryBeforeParsingWeeks() {
        val result = PdfTimetableParser().parseLayoutCell(
            day = 1,
            lines = listOf(
                "线性代数",
                "(5-6节) 2-6周 ／校区：主校区",
                "／场地：理1-403",
                "／教师：王五",
                "／教学班：M01 ／学分：2"
            )
        )

        val course = result.courses.single()
        assertEquals(setOf(2, 3, 4, 5, 6), course.weekRule.weeks)
        assertEquals("主校区", course.campus)
        assertEquals("理1-403", course.location)
        assertEquals("王五", course.teacher)
        assertEquals("M01", course.courseCode)
        assertEquals(2.0, course.credits)
    }

    @Test
    fun retainsFlatTextTableFallbackParsing() {
        val result = PdfTimetableParser().parseExtractedText(
            "Algorithms | Tuesday | 3-4 | 1-8 | Building B202 | Li"
        )

        val course = result.courses.single()
        assertEquals("Algorithms", course.courseName)
        assertEquals(2, course.dayOfWeek)
        assertEquals(3, course.startPeriod)
        assertEquals(4, course.endPeriod)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 8), course.weekRule.weeks)
        assertEquals("Building B202", course.location)
        assertEquals("Li", course.teacher)
        assertFalse(course.needsReview)
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
