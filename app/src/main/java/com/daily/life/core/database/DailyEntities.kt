package com.daily.life.core.database

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.daily.life.core.calendar.CalendarDayKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isCurrent: Boolean = false,
    val createdAt: Long
)

@Entity(
    tableName = "semester_periods",
    primaryKeys = ["semesterId", "period"],
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["semesterId"])]
)
data class SemesterPeriodEntity(
    val semesterId: Long,
    val period: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
)

@Entity(
    tableName = "semester_calendar_adjustments",
    primaryKeys = ["semesterId", "actualDate"],
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["semesterId"])]
)
data class SemesterCalendarAdjustmentEntity(
    val semesterId: Long,
    val actualDate: LocalDate,
    val sourceDayOfWeek: Int,
    val sourceDate: LocalDate? = null,
    val sourceLabel: String? = null,
    val updatedAt: Long
)

@Entity(
    tableName = "holiday_calendar_sources",
    indices = [Index(value = ["url"], unique = true)]
)
data class HolidayCalendarSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean,
    val enabled: Boolean,
    val lastSuccessfulSyncAt: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastError: String? = null
)

@Entity(
    tableName = "holiday_calendar_events",
    primaryKeys = ["sourceId", "eventKey"],
    foreignKeys = [
        ForeignKey(
            entity = HolidayCalendarSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["startDate", "endDateInclusive"])
    ]
)
data class HolidayCalendarEventEntity(
    val sourceId: String,
    val eventKey: String,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val summary: String? = null,
    val description: String? = null,
    val kind: CalendarDayKind,
    val sourceDayOfWeek: Int? = null,
    val sourceDate: LocalDate? = null,
    val fetchedAt: Long
)

@Entity(
    tableName = "calendar_day_overrides",
    indices = [Index(value = ["updatedAt"])]
)
data class CalendarDayOverrideEntity(
    @PrimaryKey val date: LocalDate,
    val targetKind: CalendarDayKind,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["semesterId", "parsedWeeks", "dayOfWeek"]),
        Index(value = ["semesterId"])
    ]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val semesterId: Long,
    val courseName: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekRuleText: String,
    val parsedWeeks: Set<Int>,
    val campus: String? = null,
    val location: String? = null,
    val teacher: String? = null,
    val courseCode: String? = null,
    val credits: Double? = null,
    val notes: String? = null,
    val courseReminderMode: CourseReminderMode = CourseReminderMode.FOLLOW_GLOBAL,
    val courseReminderMinutes: Int? = null
)

@Entity(
    tableName = "course_weeks",
    primaryKeys = ["courseId", "week"],
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["week", "courseId"])]
)
data class CourseWeekEntity(
    val courseId: Long,
    val week: Int
)

data class CourseWithWeeks(
    @Embedded val course: CourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val weeks: List<CourseWeekEntity>
)

enum class ReminderMode {
    NOTIFICATION,
    ALARM
}

enum class CalendarSyncKind {
    SCHEDULE_EVENT,
    COURSE_OCCURRENCE
}

@Entity(tableName = "calendar_sync_links", primaryKeys = ["ownerKind", "ownerKey"])
data class CalendarSyncLinkEntity(
    val ownerKind: CalendarSyncKind,
    val ownerKey: String,
    val calendarId: Long,
    val eventId: Long,
    val eventStartAt: Long,
    val lastError: String? = null
)

enum class CourseReminderMode { FOLLOW_GLOBAL, DISABLED, CUSTOM }

@Entity(
    tableName = "schedule_events",
    indices = [Index(value = ["eventAt"])]
)
data class ScheduleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val eventAt: Long,
    val reminderOffsetMinutes: Int,
    val reminderMode: ReminderMode,
    val repeatYearly: Boolean,
    val notes: String? = null,
    val isDismissed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "weight_records",
    indices = [Index(value = ["recordedAt"])]
)
data class WeightRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recordedAt: Long,
    val weightJin: Double,
    val source: String,
    val notes: String? = null
)

@Entity(
    tableName = "period_records",
    indices = [Index(value = ["startDate"]), Index(value = ["endDate"])]
)
data class PeriodRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startDate: LocalDate,
    val endDate: LocalDate
)

enum class ActivityType {
    WALK,
    RUN
}

@Entity(
    tableName = "activity_records",
    indices = [Index(value = ["recordedAt"]), Index(value = ["rawRecordId"])]
)
data class ActivityRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recordedAt: Long,
    val activityType: ActivityType,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val durationMinutes: Int? = null,
    val source: String,
    val rawRecordId: String? = null
)

enum class AdviceSource {
    LOCAL,
    DEEPSEEK
}

enum class ReportGenerationStatus {
    PENDING,
    COMPLETE,
    FAILED
}

@Entity(
    tableName = "monthly_reports",
    indices = [Index(value = ["month"], unique = true)]
)
data class MonthlyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val month: YearMonth,
    val weightTrendSummary: String,
    val activitySummary: String,
    val aiAdviceText: String? = null,
    val generatedAt: Long,
    val adviceSource: AdviceSource,
    val generationStatus: ReportGenerationStatus
)

enum class TransactionDirection {
    INCOME,
    EXPENSE
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["category"]),
        Index(value = ["direction"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurredAt: Long,
    val amountCents: Long,
    val direction: TransactionDirection,
    val category: String,
    val counterparty: String,
    val source: String,
    val paymentMethod: String? = null,
    val transactionType: String? = null,
    val status: String? = null,
    val merchantOrderId: String? = null,
    val orderId: String? = null,
    val rawText: String? = null,
    val notes: String? = null,
    val importBatchId: String? = null,
    val createdAt: Long = occurredAt,
    val updatedAt: Long = occurredAt
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val month: String,
    val budgetCents: Long,
    val triggeredPercentages: Set<Int>,
    val updatedAt: Long
)

@Entity(tableName = "import_logs")
data class ImportLogEntity(
    @PrimaryKey val batchId: String,
    val fileName: String,
    val sourceType: String,
    val importedAt: Long,
    val totalRows: Int,
    val successRows: Int,
    val skippedRows: Int,
    val errorSummary: String? = null
)
