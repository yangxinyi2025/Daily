package com.daily.life.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
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

    @Query("SELECT * FROM semesters ORDER BY id")
    suspend fun findAll(): List<SemesterEntity>

    @Query("DELETE FROM semesters")
    suspend fun deleteAll()
}

@Dao
interface SemesterPeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SemesterPeriodEntity>)

    @Query("SELECT * FROM semester_periods WHERE semesterId = :semesterId ORDER BY period")
    fun observeBySemester(semesterId: Long): Flow<List<SemesterPeriodEntity>>

    @Query("SELECT * FROM semester_periods WHERE semesterId = :semesterId ORDER BY period")
    suspend fun findBySemester(semesterId: Long): List<SemesterPeriodEntity>

    @Query("DELETE FROM semester_periods WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)
}

@Dao
interface SemesterCalendarAdjustmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SemesterCalendarAdjustmentEntity>)

    @Query("SELECT * FROM semester_calendar_adjustments WHERE semesterId = :semesterId ORDER BY actualDate")
    suspend fun findBySemester(semesterId: Long): List<SemesterCalendarAdjustmentEntity>

    @Query("SELECT * FROM semester_calendar_adjustments WHERE semesterId = :semesterId ORDER BY actualDate")
    fun observeBySemester(semesterId: Long): Flow<List<SemesterCalendarAdjustmentEntity>>

    @Query("DELETE FROM semester_calendar_adjustments WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)
}

@Dao
interface HolidayCalendarDao {
    @Query("SELECT * FROM holiday_calendar_sources ORDER BY builtIn DESC, name, id")
    fun observeSources(): Flow<List<HolidayCalendarSourceEntity>>

    @Update
    suspend fun updateSource(entity: HolidayCalendarSourceEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceIfMissing(entity: HolidayCalendarSourceEntity): Long

    @Transaction
    suspend fun upsertSource(entity: HolidayCalendarSourceEntity) {
        if (updateSource(entity) == 0) {
            insertSourceIfMissing(entity)
        }
    }

    @Query("DELETE FROM holiday_calendar_sources WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustomSource(id: String)

    @Query(
        """
        SELECT * FROM holiday_calendar_events
        WHERE endDateInclusive >= :start
          AND startDate <= :end
        ORDER BY startDate, endDateInclusive, sourceId, eventKey
        """
    )
    suspend fun findEventsBetween(start: LocalDate, end: LocalDate): List<HolidayCalendarEventEntity>

    @Query("SELECT * FROM holiday_calendar_events WHERE sourceId = :sourceId ORDER BY startDate, endDateInclusive, eventKey")
    suspend fun findEventsForSource(sourceId: String): List<HolidayCalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(rows: List<HolidayCalendarEventEntity>)

    @Query("DELETE FROM holiday_calendar_events WHERE sourceId = :sourceId")
    suspend fun deleteEventsForSource(sourceId: String)

    @Transaction
    suspend fun replaceEventsForSource(sourceId: String, events: List<HolidayCalendarEventEntity>) {
        require(events.all { it.sourceId == sourceId }) {
            "replaceEventsForSource only accepts rows for sourceId=$sourceId"
        }
        deleteEventsForSource(sourceId)
        if (events.isNotEmpty()) {
            insertEvents(events)
        }
    }

    @Query(
        """
        SELECT * FROM calendar_day_overrides
        WHERE date BETWEEN :start AND :end
        ORDER BY date
        """
    )
    fun observeDayOverridesBetween(start: LocalDate, end: LocalDate): Flow<List<CalendarDayOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayOverrides(rows: List<CalendarDayOverrideEntity>)

    @Query("DELETE FROM calendar_day_overrides WHERE date IN (:dates)")
    suspend fun deleteDayOverrides(dates: List<LocalDate>)
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

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startPeriod")
    suspend fun findBySemester(semesterId: Long): List<CourseEntity>

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

    @Query("SELECT * FROM courses ORDER BY id")
    suspend fun findAll(): List<CourseEntity>

    @Query("SELECT * FROM course_weeks ORDER BY courseId, week")
    suspend fun findAllWeeks(): List<CourseWeekEntity>

    @Query("DELETE FROM course_weeks")
    suspend fun deleteAllWeeks()

    @Query("DELETE FROM courses")
    suspend fun deleteAll()
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

    @Query("SELECT * FROM schedule_events ORDER BY eventAt")
    suspend fun findAll(): List<ScheduleEventEntity>

    @Query("DELETE FROM schedule_events")
    suspend fun deleteAll()

    @Query("SELECT * FROM schedule_events ORDER BY eventAt")
    fun observeAll(): Flow<List<ScheduleEventEntity>>

    @Query("SELECT * FROM schedule_events WHERE eventAt BETWEEN :startInclusive AND :endInclusive ORDER BY eventAt")
    fun observeBetween(startInclusive: Long, endInclusive: Long): Flow<List<ScheduleEventEntity>>
}

@Dao
interface CalendarSyncLinkDao {
    @Query("SELECT * FROM calendar_sync_links WHERE ownerKind = :ownerKind AND ownerKey = :ownerKey LIMIT 1")
    suspend fun find(ownerKind: CalendarSyncKind, ownerKey: String): CalendarSyncLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: CalendarSyncLinkEntity)

    @Query("DELETE FROM calendar_sync_links WHERE ownerKind = :ownerKind AND ownerKey = :ownerKey")
    suspend fun delete(ownerKind: CalendarSyncKind, ownerKey: String)

    @Query("SELECT * FROM calendar_sync_links WHERE ownerKind = :ownerKind ORDER BY ownerKey")
    suspend fun findByKind(ownerKind: CalendarSyncKind): List<CalendarSyncLinkEntity>
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

    @Query(
        """
        SELECT * FROM activity_records
        WHERE source = :source
          AND rawRecordId = :rawRecordId
        LIMIT 1
        """
    )
    suspend fun findActivityBySourceAndRawRecordId(
        source: String,
        rawRecordId: String
    ): ActivityRecordEntity?

    @Query(
        """
        SELECT * FROM activity_records
        WHERE source = :source
          AND recordedAt = :recordedAt
          AND activityType = :activityType
          AND ((:steps IS NULL AND steps IS NULL) OR steps = :steps)
          AND ((:distanceMeters IS NULL AND distanceMeters IS NULL) OR distanceMeters = :distanceMeters)
          AND ((:durationMinutes IS NULL AND durationMinutes IS NULL) OR durationMinutes = :durationMinutes)
        LIMIT 1
        """
    )
    suspend fun findActivityByFingerprint(
        source: String,
        recordedAt: Long,
        activityType: ActivityType,
        steps: Long?,
        distanceMeters: Double?,
        durationMinutes: Int?
    ): ActivityRecordEntity?

    @Query("SELECT * FROM monthly_reports WHERE id = :id")
    suspend fun findMonthlyReportById(id: Long): MonthlyReportEntity?

    @Query("SELECT * FROM weight_records ORDER BY recordedAt DESC")
    fun observeWeights(): Flow<List<WeightRecordEntity>>

    @Query("SELECT * FROM activity_records WHERE recordedAt BETWEEN :startInclusive AND :endInclusive ORDER BY recordedAt DESC")
    fun observeActivitiesBetween(startInclusive: Long, endInclusive: Long): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM activity_records ORDER BY recordedAt DESC")
    fun observeActivities(): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM monthly_reports WHERE month = :month LIMIT 1")
    suspend fun findMonthlyReport(month: YearMonth): MonthlyReportEntity?

    @Query("SELECT * FROM monthly_reports ORDER BY month DESC")
    fun observeMonthlyReports(): Flow<List<MonthlyReportEntity>>

    @Query("SELECT * FROM weight_records ORDER BY id")
    suspend fun findAllWeights(): List<WeightRecordEntity>

    @Query("SELECT * FROM activity_records ORDER BY id")
    suspend fun findAllActivities(): List<ActivityRecordEntity>

    @Query("SELECT * FROM monthly_reports ORDER BY id")
    suspend fun findAllMonthlyReports(): List<MonthlyReportEntity>

    @Query("DELETE FROM monthly_reports")
    suspend fun deleteAllMonthlyReports()

    @Query("DELETE FROM activity_records")
    suspend fun deleteAllActivities()

    @Query("DELETE FROM weight_records")
    suspend fun deleteAllWeights()
}

@Dao
interface PeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PeriodRecordEntity): Long

    @Update
    suspend fun update(entity: PeriodRecordEntity)

    @Query("DELETE FROM period_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM period_records WHERE id = :id")
    suspend fun findById(id: Long): PeriodRecordEntity?

    @Query("SELECT * FROM period_records ORDER BY startDate DESC, id DESC")
    fun observeAll(): Flow<List<PeriodRecordEntity>>

    @Query("SELECT * FROM period_records ORDER BY startDate DESC, id DESC")
    suspend fun findAll(): List<PeriodRecordEntity>
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

    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun findAll(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
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

    @Query("SELECT * FROM budgets ORDER BY month")
    suspend fun findAll(): List<BudgetEntity>

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
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

    @Query("SELECT * FROM import_logs ORDER BY batchId")
    suspend fun findAll(): List<ImportLogEntity>

    @Query("DELETE FROM import_logs")
    suspend fun deleteAll()
}
