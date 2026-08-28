package com.daily.life.core.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class IcsCalendarSource(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean
)

data class IcsCalendarEvent(
    val eventKey: String,
    val startDate: LocalDate,
    val endExclusiveDate: LocalDate,
    val title: String?,
    val description: String?,
    val kind: CalendarDayKind,
    val sourceDayOfWeek: Int?,
    val sourceDate: LocalDate?
)

fun parseIcsCalendar(
    text: String,
    source: IcsCalendarSource,
    zone: ZoneId
): List<IcsCalendarEvent> {
    val events = mutableListOf<IcsCalendarEvent>()
    var currentEventLines: MutableList<String>? = null

    for (line in unfoldIcsLines(text)) {
        when (line) {
            "BEGIN:VEVENT" -> currentEventLines = mutableListOf()
            "END:VEVENT" -> {
                val parsed = currentEventLines?.let { parseIcsEvent(it, source, zone) }
                if (parsed != null) {
                    events += parsed
                }
                currentEventLines = null
            }
            else -> currentEventLines?.add(line)
        }
    }

    return events
}

internal data class ChineseSpecialDayDetails(
    val kind: CalendarDayKind,
    val sourceDayOfWeek: Int?,
    val sourceDate: LocalDate?
)

internal fun classifyChineseSpecialDay(
    title: String?,
    description: String?,
    actualDate: LocalDate
): ChineseSpecialDayDetails? {
    val text = normalizedCalendarText(title, description)
    val systemKind = systemCalendarSpecialDayKindFor(title, description) ?: return null
    val sourceDate = parseSourceDate(text, actualDate)
    return ChineseSpecialDayDetails(
        kind = when (systemKind) {
            SystemCalendarSpecialDayKind.Holiday -> CalendarDayKind.HOLIDAY_REST
            SystemCalendarSpecialDayKind.MakeupWorkday -> CalendarDayKind.MAKEUP_WORKDAY
        },
        sourceDayOfWeek = parseSourceDayOfWeek(text) ?: sourceDate?.dayOfWeek?.value,
        sourceDate = sourceDate
    )
}

internal fun normalizedCalendarText(title: String?, description: String?): String =
    listOfNotNull(title, description)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun parseSourceDate(text: String, actualDate: LocalDate): LocalDate? {
    val match = Regex("补\\s*(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日?").find(text)
        ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: actualDate.year
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

internal fun parseSourceDayOfWeek(text: String): Int? {
    val match = MAKEUP_SOURCE_DAY_PATTERN.find(text) ?: return null
    return when (match.groupValues[1]) {
        "一", "1" -> 1
        "二", "2" -> 2
        "三", "3" -> 3
        "四", "4" -> 4
        "五", "5" -> 5
        "六", "6" -> 6
        "日", "天", "7" -> 7
        else -> null
    }
}

internal val MAKEUP_SOURCE_DAY_PATTERN = Regex("补\\s*(?:上班|课)?\\s*(?:周|星期)\\s*([一二三四五六日天1-7])")

private data class IcsDateValue(
    val date: LocalDate,
    val isAllDay: Boolean
)

private fun parseIcsEvent(
    lines: List<String>,
    source: IcsCalendarSource,
    defaultZone: ZoneId
): IcsCalendarEvent? {
    val properties = parseProperties(lines)
    val startProperty = properties["DTSTART"] ?: return null
    val start = parseDateValue(startProperty, defaultZone) ?: return null
    val endProperty = properties["DTEND"]
    val endExclusiveDate = when {
        endProperty != null -> {
            val end = parseDateValue(endProperty, defaultZone) ?: return null
            if (start.isAllDay && end.isAllDay) {
                end.date
            } else {
                end.date.plusDays(1)
            }
        }
        start.isAllDay -> start.date.plusDays(1)
        else -> start.date.plusDays(1)
    }
    if (!endExclusiveDate.isAfter(start.date)) return null

    val title = properties["SUMMARY"]?.value?.let(::unescapeIcsText)
    val description = properties["DESCRIPTION"]?.value?.let(::unescapeIcsText)
    val classification = classifyChineseSpecialDay(title, description, start.date) ?: return null
    val uid = properties["UID"]?.value?.trim().orEmpty()
    val eventKey = uid.ifBlank {
        buildString {
            append(source.id)
            append(':')
            append(start.date)
            append(':')
            append(endExclusiveDate)
            append(':')
            append(title.orEmpty())
        }
    }

    return IcsCalendarEvent(
        eventKey = eventKey,
        startDate = start.date,
        endExclusiveDate = endExclusiveDate,
        title = title,
        description = description,
        kind = classification.kind,
        sourceDayOfWeek = classification.sourceDayOfWeek,
        sourceDate = classification.sourceDate
    )
}

private data class IcsProperty(
    val name: String,
    val parameters: Map<String, String>,
    val value: String
)

private fun parseProperties(lines: List<String>): Map<String, IcsProperty> =
    lines.mapNotNull(::parsePropertyLine).associateBy(IcsProperty::name)

private fun parsePropertyLine(line: String): IcsProperty? {
    val separatorIndex = line.indexOf(':')
    if (separatorIndex <= 0) return null
    val rawName = line.substring(0, separatorIndex)
    val value = line.substring(separatorIndex + 1)
    val parts = rawName.split(';')
    val name = parts.first().uppercase()
    val parameters = parts.drop(1)
        .mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index).uppercase() to part.substring(index + 1)
        }
        .toMap()
    return IcsProperty(name = name, parameters = parameters, value = value)
}

private fun parseDateValue(property: IcsProperty, defaultZone: ZoneId): IcsDateValue? {
    val rawValue = property.value.trim()
    if (rawValue.isBlank()) return null

    if (property.parameters["VALUE"]?.uppercase() == "DATE") {
        return runCatching {
            IcsDateValue(LocalDate.parse(rawValue, DATE_FORMATTER), isAllDay = true)
        }.getOrNull()
    }

    val sourceZone = property.parameters["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone
    val instant = when {
        rawValue.endsWith("Z") -> runCatching { Instant.from(UTC_DATE_TIME_FORMATTER.parse(rawValue)) }.getOrNull()
        else -> runCatching {
            LocalDateTime.parse(rawValue, LOCAL_DATE_TIME_FORMATTER).atZone(sourceZone).toInstant()
        }.getOrNull()
    } ?: return null

    return IcsDateValue(instant.atZone(defaultZone).toLocalDate(), isAllDay = false)
}

private fun unfoldIcsLines(text: String): List<String> {
    val result = mutableListOf<String>()
    text.replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .forEach { rawLine ->
            when {
                rawLine.startsWith(" ") || rawLine.startsWith("\t") -> {
                    if (result.isNotEmpty()) {
                        result[result.lastIndex] = result.last() + rawLine.drop(1)
                    }
                }
                else -> result += rawLine
            }
        }
    return result
}

private fun unescapeIcsText(value: String): String =
    value
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
private val UTC_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
private val LOCAL_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
