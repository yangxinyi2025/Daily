package com.daily.life.core.database

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.YearMonth

class DailyConverters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

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
    fun fromTransactionDirection(value: TransactionDirection?): String? = value?.name

    @TypeConverter
    fun toTransactionDirection(value: String?): TransactionDirection? =
        value?.let(TransactionDirection::valueOf)
}
