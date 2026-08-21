package com.daily.life.feature.health

import com.daily.life.core.database.ActivityRecordEntity
import com.daily.life.core.database.HealthDao
import com.daily.life.core.database.MonthlyReportEntity
import com.daily.life.core.database.ReportGenerationStatus
import com.daily.life.core.database.AdviceSource
import com.daily.life.core.database.WeightRecordEntity
import com.daily.life.core.datastore.DailyPreferences
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class HealthRepository(
    private val healthDao: HealthDao,
    private val preferences: DailyPreferences,
    private val activitySources: () -> List<ActivityDataSource> = { emptyList() },
    private val activitySource: (() -> ActivityDataSource?)? = null,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend fun recordWeight(
        recordedAt: Instant = clock.instant(),
        weightJin: Double,
        source: String = "MANUAL",
        notes: String? = null
    ): Long {
        require(weightJin > 0.0) { "体重必须大于 0" }
        return healthDao.insertWeight(
            WeightRecordEntity(
                recordedAt = recordedAt.toEpochMilli(),
                weightJin = weightJin,
                source = source,
                notes = notes
            )
        )
    }

    suspend fun recordWeight(record: WeightRecord): Long = recordWeight(
        recordedAt = record.recordedAt,
        weightJin = record.weightJin,
        source = record.source,
        notes = record.notes
    )

    suspend fun setTargetWeight(weightJin: Double?) {
        require(weightJin == null || weightJin > 0.0) { "目标体重必须大于 0" }
        preferences.setTargetWeightJin(weightJin)
    }

    fun observeWeightRecords(): Flow<List<WeightRecord>> = healthDao.observeWeights().map { records ->
        records.map(::toDomain)
    }

    fun observeActivityRecords(month: YearMonth, zone: ZoneId = clock.zone): Flow<List<ActivityRecord>> {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        return healthDao.observeActivitiesBetween(start, end).map { records -> records.map(::toDomain) }
    }

    suspend fun readActivity(start: Instant, end: Instant): ActivityReadResult {
        val sources = activitySource?.invoke()?.let(::listOfNotNull) ?: activitySources()
        if (sources.isEmpty()) {
            return ActivityReadResult(0, 0, "没有可用的健康数据源")
        }
        var importedCount = 0
        val messages = mutableListOf<String>()
        val availabilities = mutableListOf<DataSourceAvailability>()
        sources.forEach { source ->
            var availability = source.availability()
            if (availability.status == DataSourceStatus.PERMISSION_REQUIRED &&
                source is PermissionAwareActivityDataSource
            ) {
                availability = source.requestPermission()
            }
            availabilities += availability
            if (availability.status != DataSourceStatus.AVAILABLE) {
                messages += "${availability.label}：${availability.detail}"
                return@forEach
            }
            source.read(start, end).forEach { record ->
                healthDao.insertActivity(record.toEntity())
                importedCount += 1
            }
            messages += "${availability.label}：已读取"
        }
        return ActivityReadResult(
            importedCount = importedCount,
            sourcesChecked = sources.size,
            message = messages.joinToString("；"),
            availabilities = availabilities
        )
    }

    suspend fun readActivity(month: YearMonth, zone: ZoneId = clock.zone): ActivityReadResult =
        readActivity(
            start = month.atDay(1).atStartOfDay(zone).toInstant(),
            end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        )

    suspend fun generateMonthlyReport(month: YearMonth, zone: ZoneId = clock.zone): LocalReport {
        val weights = observeWeightRecords().first()
        val activities = observeActivityRecords(month, zone).first()
        val report = MonthlyReportCalculator.calculate(
            month = month,
            weights = weights,
            activities = activities,
            targetWeightJin = preferences.targetWeightJin.first(),
            zone = zone
        )
        val existing = healthDao.findMonthlyReport(month)
        healthDao.insertMonthlyReport(
            MonthlyReportEntity(
                id = existing?.id ?: 0L,
                month = month,
                weightTrendSummary = report.weightTrendSummary,
                activitySummary = report.activitySummary,
                generatedAt = clock.instant().toEpochMilli(),
                adviceSource = AdviceSource.LOCAL,
                generationStatus = ReportGenerationStatus.COMPLETE
            )
        )
        return report
    }

    private fun toDomain(entity: WeightRecordEntity): WeightRecord = WeightRecord(
        id = entity.id,
        recordedAt = Instant.ofEpochMilli(entity.recordedAt),
        weightJin = entity.weightJin,
        source = entity.source,
        notes = entity.notes
    )

    private fun toDomain(entity: ActivityRecordEntity): ActivityRecord = ActivityRecord(
        id = entity.id,
        recordedAt = Instant.ofEpochMilli(entity.recordedAt),
        activityType = entity.activityType,
        steps = entity.steps,
        distanceMeters = entity.distanceMeters,
        durationMinutes = entity.durationMinutes,
        source = entity.source,
        rawRecordId = entity.rawRecordId
    )

    private fun ActivityRecord.toEntity(): ActivityRecordEntity = ActivityRecordEntity(
        id = id,
        recordedAt = recordedAt.toEpochMilli(),
        activityType = activityType,
        steps = steps,
        distanceMeters = distanceMeters,
        durationMinutes = durationMinutes,
        source = source,
        rawRecordId = rawRecordId
    )
}

private fun YearMonth.isValidDate(instant: Instant, zone: ZoneId): Boolean =
    YearMonth.from(instant.atZone(zone)) == this
