package com.daily.life.feature.timetable

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

fun interface TimetableParser {
    fun parse(input: InputStream): TimetableParseResult
}

class PdfTimetableParser : TimetableParser {
    override fun parse(input: InputStream): TimetableParseResult = input.use { ownedInput ->
        PDDocument.load(ownedInput).use { document ->
            val text = PDFTextStripper().getText(document)
            parseExtractedText(text)
        }
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
            unsupportedRows = unsupportedRows
        )
    }

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
        val PERIOD_NUMBER = Regex("""\d+""")
    }
}
