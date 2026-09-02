package com.daily.life.feature.timetable

object WeekRuleParser {
    private val rangeRegex = Regex("""(\d+)\s*[-~～至]\s*(\d+)""")
    private val singleWeekRegex = Regex("""(?<!\d)(\d+)(?!\d)""")
    private val standaloneWeekMarker = Regex("""(?<![\p{L}\p{N}])周(?![\p{L}\p{N}])""")
    private val standaloneOrdinalMarker = Regex("""(?<![\p{L}\p{N}])第(?![\p{L}\p{N}])""")

    fun parse(raw: String): WeekRuleResult {
        val normalized = raw.trim()
        if (normalized.isBlank()) {
            return WeekRuleResult(
                rawText = raw,
                weeks = emptySet(),
                warnings = listOf("空周次规则")
            )
        }

        val parity = when {
            normalized.contains("单周") -> WeekParity.ODD
            normalized.contains("双周") -> WeekParity.EVEN
            else -> null
        }

        val weeks = linkedSetOf<Int>()
        rangeRegex.findAll(normalized).forEach { match ->
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt()
            if (start <= end) {
                (start..end).forEach(weeks::add)
            } else {
                (end..start).forEach(weeks::add)
            }
        }

        val normalizedWithoutRanges = rangeRegex.replace(normalized, " ")
        singleWeekRegex.findAll(normalizedWithoutRanges).forEach { match ->
            weeks += match.groupValues[1].toInt()
        }

        val warnings = mutableListOf<String>()
        val cleaned = normalized
            .replace(rangeRegex, " ")
            .replace(singleWeekRegex, " ")
            .replace("单周", " ")
            .replace("双周", " ")
            .replace(standaloneWeekMarker, " ")
            .replace(standaloneOrdinalMarker, " ")
            .replace(Regex("""[,，、;；/]"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
        if (cleaned.isNotBlank()) {
            warnings += "未解析片段: $cleaned"
        }
        if (weeks.isEmpty() && parity == null) {
            warnings += "未解析出有效周次"
        }

        return WeekRuleResult(
            rawText = raw,
            weeks = weeks.toSet(),
            parity = parity,
            warnings = warnings.distinct()
        )
    }
}
