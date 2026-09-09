package com.daily.life.core.database

import androidx.room.TypeConverter
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.CalendarRuleSource
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class DailyConverters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun fromYearMonth(value: YearMonth?): String? = value?.toString()

    @TypeConverter
    fun toYearMonth(value: String?): YearMonth? = value?.let(YearMonth::parse)

    @TypeConverter
    fun fromIntSet(value: Set<Int>?): String? = value?.sorted()?.joinToString(",")

    @TypeConverter
    fun toIntSet(value: String?): Set<Int> =
        value
            ?.split(',')
            ?.mapNotNull { token -> token.trim().takeIf(String::isNotEmpty)?.toIntOrNull() }
            ?.toSet()
            .orEmpty()

    @TypeConverter
    fun fromReminderMode(value: ReminderMode?): String? = value?.name

    @TypeConverter
    fun toReminderMode(value: String?): ReminderMode? = value?.let(ReminderMode::valueOf)

    @TypeConverter
    fun fromCourseReminderMode(value: CourseReminderMode?): String? = value?.name

    @TypeConverter
    fun toCourseReminderMode(value: String?): CourseReminderMode? = value?.let(CourseReminderMode::valueOf)

    @TypeConverter
    fun fromActivityType(value: ActivityType?): String? = value?.name

    @TypeConverter
    fun toActivityType(value: String?): ActivityType? = value?.let(ActivityType::valueOf)

    @TypeConverter
    fun fromAdviceSource(value: AdviceSource?): String? = value?.name

    @TypeConverter
    fun toAdviceSource(value: String?): AdviceSource? = value?.let(AdviceSource::valueOf)

    @TypeConverter
    fun fromReportGenerationStatus(value: ReportGenerationStatus?): String? = value?.name

    @TypeConverter
    fun toReportGenerationStatus(value: String?): ReportGenerationStatus? =
        value?.let(ReportGenerationStatus::valueOf)

    @TypeConverter
    fun fromCalendarDayKind(value: CalendarDayKind?): String? = value?.name

    @TypeConverter
    fun toCalendarDayKind(value: String?): CalendarDayKind? = value?.let(CalendarDayKind::valueOf)

    @TypeConverter
    fun fromCalendarRuleSource(value: CalendarRuleSource?): String? = value?.name

    @TypeConverter
    fun toCalendarRuleSource(value: String?): CalendarRuleSource? = value?.let(CalendarRuleSource::valueOf)
}
