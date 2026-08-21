package com.daily.life.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM semesters WHERE name = :name ORDER BY startDate DESC LIMIT 1")
    suspend fun findByName(name: String): SemesterEntity?

    @Query("UPDATE semesters SET isCurrent = 0 WHERE isCurrent = 1")
    suspend fun clearCurrent()

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

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun findById(id: Long): CourseEntity?

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startPeriod")
    fun observeBySemester(semesterId: Long): Flow<List<CourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeek(entity: CourseWeekEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeks(entities: List<CourseWeekEntity>)

    @Query("DELETE FROM course_weeks WHERE courseId = :courseId")
    suspend fun deleteWeeksByCourseId(courseId: Long)

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun findWithWeeksById(id: Long): CourseWithWeeks?

    @Query(
        """
        SELECT c.* FROM courses c
        INNER JOIN course_weeks cw ON cw.courseId = c.id
        WHERE c.semesterId = :semesterId
          AND cw.week = :week
          AND c.dayOfWeek = :dayOfWeek
        ORDER BY c.startPeriod
        """
    )
    fun observeBySemesterWeekAndDay(
        semesterId: Long,
        week: Int,
        dayOfWeek: Int
    ): Flow<List<CourseEntity>>

    @Query(
        """
        SELECT c.* FROM courses c
        INNER JOIN course_weeks cw ON cw.courseId = c.id
        WHERE c.semesterId = :semesterId
          AND cw.week = :week
        ORDER BY c.dayOfWeek, c.startPeriod
        """
    )
    fun observeBySemesterWeek(semesterId: Long, week: Int): Flow<List<CourseEntity>>
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
    suspend fun insertMonthlyReport(entity: MonthlyReportEntity): Long

    @Update
    suspend fun updateWeight(entity: WeightRecordEntity)

    @Update
    suspend fun updateActivity(entity: ActivityRecordEntity)

    @Update
    suspend fun updateMonthlyReport(entity: MonthlyReportEntity)

    @Query("DELETE FROM weight_records WHERE id = :id")
    suspend fun deleteWeightById(id: Long)

    @Query("DELETE FROM activity_records WHERE id = :id")
    suspend fun deleteActivityById(id: Long)

    @Query("DELETE FROM monthly_reports WHERE id = :id")
    suspend fun deleteMonthlyReportById(id: Long)

    @Query("SELECT * FROM weight_records WHERE id = :id")
    suspend fun findWeightById(id: Long): WeightRecordEntity?

    @Query("SELECT * FROM activity_records WHERE id = :id")
    suspend fun findActivityById(id: Long): ActivityRecordEntity?

    @Query("SELECT * FROM monthly_reports WHERE id = :id")
    suspend fun findMonthlyReportById(id: Long): MonthlyReportEntity?

    @Query("SELECT * FROM weight_records ORDER BY recordedAt DESC")
    fun observeWeights(): Flow<List<WeightRecordEntity>>

    @Query("SELECT * FROM activity_records WHERE recordedAt BETWEEN :startInclusive AND :endInclusive ORDER BY recordedAt DESC")
    fun observeActivitiesBetween(startInclusive: Long, endInclusive: Long): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM monthly_reports WHERE month = :month LIMIT 1")
    suspend fun findMonthlyReport(month: YearMonth): MonthlyReportEntity?

    @Query("SELECT * FROM monthly_reports ORDER BY month DESC")
    fun observeMonthlyReports(): Flow<List<MonthlyReportEntity>>
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
    suspend fun insert(entity: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity)

    @Update
    suspend fun update(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE month = :month")
    suspend fun deleteByMonth(month: String)

    @Query("SELECT * FROM budgets WHERE month = :month LIMIT 1")
    suspend fun findByMonth(month: String): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY month DESC")
    fun observeAll(): Flow<List<BudgetEntity>>
}

@Dao
interface ImportLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImportLogEntity)

    @Update
    suspend fun update(entity: ImportLogEntity)

    @Query("DELETE FROM import_logs WHERE batchId = :batchId")
    suspend fun deleteByBatchId(batchId: String)

    @Query("SELECT * FROM import_logs WHERE batchId = :batchId LIMIT 1")
    suspend fun findByBatchId(batchId: String): ImportLogEntity?

    @Query("SELECT * FROM import_logs ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportLogEntity>>
}
