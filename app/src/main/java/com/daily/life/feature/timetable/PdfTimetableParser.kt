package com.daily.life.feature.timetable

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import java.time.LocalTime
import kotlin.math.roundToInt

fun interface TimetableParser {
    fun parse(input: InputStream): TimetableParseResult
}

class PdfTimetableParser(
    private val initializePdfBox: () -> Unit = {}
) : TimetableParser {
    override fun parse(input: InputStream): TimetableParseResult = input.use { ownedInput ->
        initializePdfBox()
        PDDocument.load(ownedInput).use { document ->
            val stripper = LayoutTextStripper().apply { setSortByPosition(true) }
            val text = stripper.getText(document)
            val parsed = if (stripper.hasWeekdayHeader) {
                parseLayout(stripper.chunks)
            } else {
                parseExtractedText(text)
            }
            parsed.copy(parsedPeriodTimes = extractPeriodTimes(text))
        }
    }

    private fun parseLayout(chunks: List<LayoutTextChunk>): TimetableParseResult {
        val dayChunks = linkedMapOf<Int, MutableList<String>>()

        chunks.forEach { chunk ->
            val day = dayForX(chunk.x) ?: return@forEach
            if (chunk.text.isNotBlank()) {
                dayChunks.getOrPut(day) { mutableListOf() } += chunk.text.trim()
            }
        }

        val courses = mutableListOf<TimetablePreviewCourse>()
        val unsupportedRows = mutableListOf<UnsupportedTimetableRow>()
        dayChunks.forEach { (day, lines) ->
            parseCell(day, lines, courses, unsupportedRows)
        }

        return TimetableParseResult(
            courses = courses,
            warnings = if (courses.isEmpty()) listOf("未识别到课程内容，请确认 PDF 是按周课表导出的格式") else emptyList(),
            unsupportedRows = unsupportedRows
        )
    }

    private fun parseCell(
        day: Int,
        lines: List<String>,
        courses: MutableList<TimetablePreviewCourse>,
        unsupportedRows: MutableList<UnsupportedTimetableRow>
    ) {
        val initialCourseCount = courses.size
        var index = 0
        while (index < lines.size) {
            val courseName = lines[index].trim()
            val metadataIndex = (index + 1 until minOf(lines.size, index + 4))
                .firstOrNull { COURSE_METADATA.containsMatchIn(lines[it]) }
            if (metadataIndex == null || !isCourseNameCandidate(courseName)) {
                index += 1
                continue
            }

            val endIndex = findCourseEnd(lines, metadataIndex)
            val rawRow = lines.subList(index, endIndex + 1).joinToString(" ")
            val metadata = lines.subList(metadataIndex, endIndex + 1).joinToString(" ")
            val periods = COURSE_METADATA.find(metadata)?.let {
                it.groupValues[1].toInt()..it.groupValues[2].toInt()
            }
            val weekText = COURSE_METADATA.find(metadata)?.let { match ->
                metadata.substring(match.range.last + 1)
                    .substringBefore("/校区")
                    .replace("(单)", "单周")
                    .replace("（单）", "单周")
                    .replace("(双)", "双周")
                    .replace("（双）", "双周")
                    .trim()
            }.orEmpty()
            val weekRule = WeekRuleParser.parse(weekText)
            val missingFields = buildList {
                if (courseName.isBlank()) add("课程名")
                if (periods == null) add("节次")
                if (weekRule.weeks.isEmpty() && weekRule.parity == null) add("周次")
            }

            courses += TimetablePreviewCourse(
                courseName = courseName,
                dayOfWeek = day,
                startPeriod = periods?.first,
                endPeriod = periods?.last,
                weekRule = weekRule,
                location = valueBetween(metadata, "/场地:", "/教师:"),
                teacher = valueBetween(metadata, "/教师:", "/教学班:"),
                campus = valueBetween(metadata, "/校区:", "/场地:"),
                courseCode = valueBetween(metadata, "/教学班:", "/教学班组成"),
                credits = CREDITS.find(metadata)?.groupValues?.get(1)?.toDoubleOrNull(),
                notes = null,
                rawRow = rawRow,
                needsReview = missingFields.isNotEmpty() || weekRule.warnings.isNotEmpty()
            )
            index = endIndex + 1
        }

        if (lines.any { COURSE_METADATA.containsMatchIn(it) } && courses.size == initialCourseCount) {
            unsupportedRows += UnsupportedTimetableRow(
                rawText = lines.joinToString(" "),
                reason = "无法识别课程详情；已保留原始内容供检查"
            )
        }
    }

    private fun findCourseEnd(lines: List<String>, metadataIndex: Int): Int {
        val creditIndex = (metadataIndex until lines.size).firstOrNull { "学分" in lines[it] }
            ?: (lines.size - 1)
        val creditLine = lines[creditIndex]
        return if (CREDITS.containsMatchIn(creditLine)) {
            creditIndex
        } else {
            minOf(creditIndex + 1, lines.lastIndex)
        }
    }

    private fun isCourseNameCandidate(value: String): Boolean = value.isNotBlank() &&
        !value.startsWith("(") &&
        !value.startsWith("（") &&
        !value.contains("课表") &&
        !value.contains("学号") &&
        !value.contains("打印时间") &&
        !value.startsWith("星期") &&
        !value.startsWith("周") &&
        !value.contains(":") &&
        !value.contains("/校区") &&
        !value.contains("/场地") &&
        !value.contains("/教师") &&
        value !in setOf("上午", "下午", "晚上")

    private fun valueBetween(text: String, start: String, end: String): String? =
        text.substringAfter(start, "")
            .substringBefore(end)
            .trim()
            .takeIf(String::isNotBlank)

    private fun dayForX(x: Float): Int? {
        if (x < DAY_COLUMN_START - DAY_COLUMN_TOLERANCE || x > DAY_COLUMN_START + DAY_COLUMN_WIDTH * 7) {
            return null
        }
        return ((x - DAY_COLUMN_START) / DAY_COLUMN_WIDTH).roundToInt() + 1
    }

    internal fun parseExtractedText(text: String): TimetableParseResult {
        val courses = mutableListOf<TimetablePreviewCourse>()
        val unsupportedRows = mutableListOf<UnsupportedTimetableRow>()

        text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { row ->
                when (val parsed = parseRow(row)) {
                    is ParsedRow.Course -> courses += parsed.value
                    is ParsedRow.Unsupported -> unsupportedRows += parsed.value
                }
            }

        return TimetableParseResult(
            courses = courses,
            warnings = emptyList(),
            unsupportedRows = unsupportedRows,
            parsedPeriodTimes = extractPeriodTimes(text)
        )
    }

    private fun extractPeriodTimes(text: String): Map<Int, SemesterPeriodTime> =
        PERIOD_TIME.findAll(text).mapNotNull { match ->
            val period = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
            val start = runCatching { LocalTime.parse(match.groupValues[2].padStart(5, '0')) }.getOrNull()
                ?: return@mapNotNull null
            val end = runCatching { LocalTime.parse(match.groupValues[3].padStart(5, '0')) }.getOrNull()
                ?: return@mapNotNull null
            if (!end.isAfter(start)) return@mapNotNull null
            period to SemesterPeriodTime(period, start, end)
        }.toMap()

    private fun parseRow(rawRow: String): ParsedRow {
        val columns = splitColumns(rawRow)
        if (columns.size < REQUIRED_COLUMN_COUNT) {
            return ParsedRow.Unsupported(
                UnsupportedTimetableRow(
                    rawText = rawRow,
                    reason = "无法识别课程列；至少需要课程、星期、节次和周次"
                )
            )
        }

        val courseName = columns[0]
        val dayOfWeek = parseDayOfWeek(columns[1])
        val periods = parsePeriods(columns[2])
        val weekRule = WeekRuleParser.parse(columns[3])
        val missingFields = buildList {
            if (courseName.isBlank()) add("课程名")
            if (dayOfWeek == null) add("星期")
            if (periods == null) add("节次")
            if (weekRule.weeks.isEmpty() && weekRule.parity == null) add("周次")
        }

        return ParsedRow.Course(
            TimetablePreviewCourse(
                courseName = courseName,
                dayOfWeek = dayOfWeek,
                startPeriod = periods?.first,
                endPeriod = periods?.last,
                weekRule = weekRule,
                location = columns.getOrNull(4).nullIfBlank(),
                teacher = columns.getOrNull(5).nullIfBlank(),
                campus = columns.getOrNull(6).nullIfBlank(),
                courseCode = columns.getOrNull(7).nullIfBlank(),
                credits = columns.getOrNull(8)?.toDoubleOrNull(),
                notes = columns.drop(9).joinToString(" | ").nullIfBlank(),
                rawRow = rawRow,
                needsReview = missingFields.isNotEmpty() || weekRule.warnings.isNotEmpty()
            )
        )
    }

    private fun splitColumns(row: String): List<String> = when {
        '|' in row -> row.split('|')
        '\t' in row -> row.split('\t')
        else -> row.split(Regex("""\s{2,}"""))
    }.map(String::trim)

    private fun parseDayOfWeek(raw: String): Int? {
        val normalized = raw.trim().lowercase()
        return when (normalized) {
            "1", "一", "周一", "星期一", "monday", "mon" -> 1
            "2", "二", "周二", "星期二", "tuesday", "tue", "tues" -> 2
            "3", "三", "周三", "星期三", "wednesday", "wed" -> 3
            "4", "四", "周四", "星期四", "thursday", "thu", "thur", "thurs" -> 4
            "5", "五", "周五", "星期五", "friday", "fri" -> 5
            "6", "六", "周六", "星期六", "saturday", "sat" -> 6
            "7", "日", "天", "周日", "周天", "星期日", "星期天", "sunday", "sun" -> 7
            else -> null
        }
    }

    private fun parsePeriods(raw: String): IntRange? {
        val values = PERIOD_NUMBER.findAll(raw).map { it.value.toInt() }.toList()
        if (values.isEmpty()) return null
        val start = values.first()
        val end = values.getOrElse(1) { start }
        return minOf(start, end)..maxOf(start, end)
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf(String::isNotBlank)

    private sealed interface ParsedRow {
        data class Course(val value: TimetablePreviewCourse) : ParsedRow
        data class Unsupported(val value: UnsupportedTimetableRow) : ParsedRow
    }

    private companion object {
        const val REQUIRED_COLUMN_COUNT = 4
        const val DAY_COLUMN_START = 104f
        const val DAY_COLUMN_WIDTH = 103.84f
        const val DAY_COLUMN_TOLERANCE = 12f
        val PERIOD_NUMBER = Regex("""\d+""")
        val COURSE_METADATA = Regex("""\((\d+)\s*-\s*(\d+)节\)""")
        val CREDITS = Regex("""/学分\s*:?\s*([0-9]+(?:\.[0-9]+)?)""")
        val PERIOD_TIME = Regex("""(?:第\s*)?(1[0-2]|[1-9])\s*节?\s*(\d{1,2}:\d{2})\s*(?:-|–|—|~|～|至)\s*(\d{1,2}:\d{2})""")
    }

    private data class LayoutTextChunk(
        val x: Float,
        val text: String
    )

    private class LayoutTextStripper : PDFTextStripper() {
        val chunks = mutableListOf<LayoutTextChunk>()
        var hasWeekdayHeader: Boolean = false
            private set

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.contains("星期一")) hasWeekdayHeader = true
            val first = textPositions.firstOrNull()
            if (first != null) {
                chunks += LayoutTextChunk(first.xDirAdj, text)
            }
            super.writeString(text, textPositions)
        }
    }
}
