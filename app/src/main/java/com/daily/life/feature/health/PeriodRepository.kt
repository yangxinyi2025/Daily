package com.daily.life.feature.health

import com.daily.life.core.database.PeriodDao
import com.daily.life.core.database.PeriodRecordEntity
import com.daily.life.core.datastore.DailyPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PeriodRepository(
    private val periodDao: PeriodDao,
    private val preferences: DailyPreferences
) {
    fun observeRecords(): Flow<List<PeriodRecord>> =
        periodDao.observeAll().map { records -> records.map(::toDomain) }

    fun observeNextStartDate(): Flow<LocalDate?> =
        combine(observeRecords(), preferences.menstrualCycleDays) { records, cycleDays ->
            PeriodPredictionCalculator.nextStartDate(records, cycleDays)
        }

    suspend fun record(startDate: LocalDate, endDate: LocalDate): Long {
        require(!endDate.isBefore(startDate)) { "结束日不能早于开始日" }
        return periodDao.insert(PeriodRecordEntity(startDate = startDate, endDate = endDate))
    }

    suspend fun update(record: PeriodRecord) {
        require(!record.endDate.isBefore(record.startDate)) { "结束日不能早于开始日" }
        periodDao.update(record.toEntity())
    }

    suspend fun delete(id: Long) = periodDao.deleteById(id)

    suspend fun setCycleDays(cycleDays: Int?) = preferences.setMenstrualCycleDays(cycleDays)

    private fun toDomain(entity: PeriodRecordEntity) = PeriodRecord(
        id = entity.id,
        startDate = entity.startDate,
        endDate = entity.endDate
    )

    private fun PeriodRecord.toEntity() = PeriodRecordEntity(
        id = id,
        startDate = startDate,
        endDate = endDate
    )
}
