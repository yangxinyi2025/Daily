package com.daily.life.feature.health

import com.daily.life.core.database.ActivityType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

data class WeightRecord(
    val id: Long = 0L,
    val recordedAt: Instant,
    val weightJin: Double,
    val source: String = "MANUAL",
    val notes: String? = null
)

data class ActivityRecord(
    val id: Long = 0L,
    val recordedAt: Instant,
    val activityType: ActivityType,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val durationMinutes: Int? = null,
    val source: String = "MANUAL",
    val rawRecordId: String? = null
)

enum class DataSourceStatus {
    AVAILABLE,
    PERMISSION_REQUIRED,
    UNAVAILABLE
}

data class DataSourceAvailability(
    val status: DataSourceStatus,
    val label: String,
    val detail: String
) {
    companion object {
        fun available(label: String): DataSourceAvailability = DataSourceAvailability(
            status = DataSourceStatus.AVAILABLE,
            label = label,
            detail = "可以读取活动数据"
        )

        fun unavailable(label: String, detail: String): DataSourceAvailability = DataSourceAvailability(
            status = DataSourceStatus.UNAVAILABLE,
            label = label,
            detail = detail
        )

        fun permissionRequired(label: String, detail: String): DataSourceAvailability = DataSourceAvailability(
            status = DataSourceStatus.PERMISSION_REQUIRED,
            label = label,
            detail = detail
        )
    }
}

interface ActivityDataSource {
    suspend fun read(start: Instant, end: Instant): List<ActivityRecord>
    suspend fun availability(): DataSourceAvailability
}

interface PermissionAwareActivityDataSource : ActivityDataSource {
    suspend fun requestPermission(): DataSourceAvailability
}

data class ActivityReadResult(
    val importedCount: Int,
    val sourcesChecked: Int,
    val message: String,
    val availabilities: List<DataSourceAvailability> = emptyList()
)

enum class ReportDataState {
    EMPTY,
    INSUFFICIENT,
    READY
}

data class WeightPoint(
    val date: LocalDate,
    val weightJin: Double
)

data class LocalReport(
    val month: YearMonth,
    val monthAverageJin: Double?,
    val monthOverMonthJin: Double?,
    val weeklyAveragesJin: Map<LocalDate, Double>,
    val recentWeights: List<WeightPoint>,
    val totalSteps: Long,
    val walkingSteps: Long,
    val runningSteps: Long,
    val totalDistanceMeters: Double,
    val totalDurationMinutes: Int,
    val targetDifferenceJin: Double?,
    val dataState: ReportDataState,
    val weightTrendSummary: String,
    val activitySummary: String
) {
    val weeklyAverageJin: Double?
        get() = weeklyAveragesJin.values.takeIf { it.isNotEmpty() }?.average()
}
