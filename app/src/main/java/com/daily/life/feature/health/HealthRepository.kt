package com.daily.life.feature.health

import com.daily.life.core.database.HealthDao
import com.daily.life.core.database.WeightRecordEntity
import com.daily.life.core.datastore.DailyPreferences
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HealthRepository(
    private val healthDao: HealthDao,
    private val preferences: DailyPreferences,
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

    private fun toDomain(entity: WeightRecordEntity): WeightRecord = WeightRecord(
        id = entity.id,
        recordedAt = Instant.ofEpochMilli(entity.recordedAt),
        weightJin = entity.weightJin,
        source = entity.source,
        notes = entity.notes
    )

}
