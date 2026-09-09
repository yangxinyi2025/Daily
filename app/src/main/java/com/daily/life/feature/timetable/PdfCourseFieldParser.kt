package com.daily.life.feature.timetable

import java.text.Normalizer

internal enum class TimetablePreviewField { Location, Teacher }

internal data class PdfCourseFields(
    val location: String,
    val teacher: String,
    val warnings: Map<TimetablePreviewField, String>
)

internal object PdfCourseFieldParser {
    fun parse(text: String): PdfCourseFields {
        val extractedLocation = extractValue(text, "场地", "教师")
        val extractedTeacher = extractValue(text, "教师", "教学班")
        val location = normalize(extractedLocation)
        val teacher = normalize(extractedTeacher)
        val fields = if (isTeacher(location) && isLocation(teacher)) {
            teacher to location
        } else {
            location to teacher
        }

        return PdfCourseFields(
            location = fields.first,
            teacher = fields.second,
            warnings = buildMap {
                if (fields.first.isBlank() || !isLocation(fields.first)) {
                    put(TimetablePreviewField.Location, "无法可靠识别场地，请检查原始内容")
                }
                if (fields.second.isBlank() || !isTeacher(fields.second)) {
                    put(TimetablePreviewField.Teacher, "无法可靠识别教师，请检查原始内容")
                }
            }
        )
    }

    private fun extractValue(text: String, startLabel: String, endLabel: String): String {
        val start = flexibleLabelPattern(startLabel)
        val end = flexibleLabelPattern(endLabel)
        val pattern = Regex(
            "[／/]\\s*$start\\s*[:：]\\s*(.*?)(?=[／/]\\s*$end(?:\\s*[:：]|$))",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return pattern.find(text)?.groupValues?.get(1).orEmpty()
    }

    private fun flexibleLabelPattern(label: String): String = label
        .map { character -> Regex.escape(character.toString()) }
        .joinToString("\\s*")

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).replace(WHITESPACE, "")

    private fun isLocation(value: String): Boolean = value.isNotBlank() &&
        value !in UNRELIABLE_VALUES &&
        (LOCATION_KEYWORDS.any(value::contains) || ROOM_NUMBER.containsMatchIn(value))

    private fun isTeacher(value: String): Boolean = value.isNotBlank() &&
        value !in UNRELIABLE_VALUES &&
        CHINESE_NAME.matches(value) &&
        !LOCATION_KEYWORDS.any(value::contains) &&
        !ROOM_NUMBER.containsMatchIn(value)

    private val WHITESPACE = Regex("\\s+")
    private val ROOM_NUMBER = Regex(
        """^(?:(?:教|理|文|工|商|法|艺|实|科|学|综|信|电|经|管|外|医|东|西|南|北)(?:[一二三四五六七八九十]+)?[0-9]{2,4}|(?:教|理|文|工|商|法|艺|实|科|学|综|信|电|经|管|外|医|东|西|南|北)[0-9]{1,2}-[0-9]{2,4}|[A-Za-z][0-9]{2,4}|[A-Za-z][0-9]{1,2}-[0-9]{2,4})$"""
    )
    private val CHINESE_NAME = Regex("""[一-龥]{2,5}""")
    private val LOCATION_KEYWORDS = listOf(
        "线上", "在线", "网络", "腾讯", "会议", "平台", "教室", "教学楼", "校区", "中心",
        "实验室", "机房", "实训", "图书馆", "体育馆", "操场", "楼", "馆", "室"
    )
    private val UNRELIABLE_VALUES = setOf("未安排", "待定", "待通知", "未知", "不详", "无", "--", "-")
}
