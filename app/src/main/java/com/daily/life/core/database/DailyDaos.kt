package com.daily.life.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

@Dao
interface SemesterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SemesterEntity): Long

    @Update
    suspend fun update(entity: SemesterEntity)

    @Query("DELETE FROM semesters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun findById(id: Long): SemesterEntity?

    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun observeAll(): Flow<List<SemesterEntity>>
}

@Dao
interface CourseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CourseEntity>)

    @Update
    suspend fun update(entity: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startPeriod")
    fun observeBySemester(semesterId: Long): Flow<List<CourseEntity>>
}

@Dao
interface ScheduleEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduleEventEntity): Long

    @Update
    suspend fun update(entity: ScheduleEventEntity)

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM schedule_events WHERE id = :id")
    suspend fun findById(id: Long): ScheduleEventEntity?

    @Query("SELECT * FROM schedule_events WHERE eventAt BETWEEN :startInclusive AND :endInclusive ORDER BY eventAt")
    fun observeBetween(startInclusive: Long, endInclusive: Long): Flow<List<ScheduleEventEntity>>
}

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(entity: WeightRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(entity: ActivityRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthlyReport(entity: MonthlyReportEntity): Long

    @Query("SELECT * FROM weight_records ORDER BY recordedAt DESC")
    fun observeWeights(): Flow<List<WeightRecordEntity>>

    @Query("SELECT * FROM activity_records WHERE recordedAt BETWEEN :startInclusive AND :endInclusive ORDER BY recordedAt DESC")
    fun observeActivitiesBetween(startInclusive: Long, endInclusive: Long): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM monthly_reports WHERE month = :month LIMIT 1")
    suspend fun findMonthlyReport(month: YearMonth): MonthlyReportEntity?
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE month = :month LIMIT 1")
    suspend fun findByMonth(month: String): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY month DESC")
    fun observeAll(): Flow<List<BudgetEntity>>
}

@Dao
interface ImportLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImportLogEntity)

    @Query("SELECT * FROM import_logs WHERE batchId = :batchId LIMIT 1")
    suspend fun findByBatchId(batchId: String): ImportLogEntity?

    @Query("SELECT * FROM import_logs ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportLogEntity>>
}
