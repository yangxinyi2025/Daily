package com.daily.life.core.database

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyDatabaseTest {
    private lateinit var database: DailyDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        transactionDao = database.transactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transactionAmountIsStoredAsCents() = runTest {
        val record = TransactionEntity(
            id = 1L,
            occurredAt = 1_700_000_000_000L,
            amountCents = 12_345L,
            direction = TransactionDirection.EXPENSE,
            category = "餐饮",
            counterparty = "测试商户",
            source = "TEST"
        )

        transactionDao.insert(record)

        assertEquals(12_345L, transactionDao.findById(1L)?.amountCents)
    }

    @Test
    fun importLogAndBudgetTablesAreAvailable() = runTest {
        database.budgetDao().upsert(
            BudgetEntity(
                month = "2026-08",
                budgetCents = 300_000L,
                triggeredPercentages = setOf(50, 70),
                updatedAt = 1_700_000_100_000L
            )
        )
        database.importLogDao().insert(
            ImportLogEntity(
                batchId = "batch-1",
                fileName = "synthetic.csv",
                sourceType = "WECHAT",
                importedAt = 1_700_000_200_000L,
                totalRows = 10,
                successRows = 8,
                skippedRows = 2,
                errorSummary = "2 duplicate rows"
            )
        )

        assertNotNull(database.budgetDao().findByMonth("2026-08"))
        assertEquals(1, database.importLogDao().observeAll().first().size)
    }

    @Test
    fun courseLookupUsesSemesterWeekAndDayRelation() = runTest {
        database.semesterDao().insert(
            SemesterEntity(
                id = 1L,
                name = "2026 秋季",
                startDate = java.time.LocalDate.of(2026, 9, 1),
                createdAt = 1_700_000_000_000L
            )
        )
        database.courseDao().insert(
            CourseEntity(
                id = 10L,
                semesterId = 1L,
                courseName = "数据库",
                dayOfWeek = 2,
                startPeriod = 3,
                endPeriod = 4,
                weekRuleText = "第 3 周",
                parsedWeeks = setOf(3)
            )
        )
        database.courseDao().insert(
            CourseEntity(
                id = 11L,
                semesterId = 1L,
                courseName = "不应匹配",
                dayOfWeek = 2,
                startPeriod = 5,
                endPeriod = 6,
                weekRuleText = "第 4 周",
                parsedWeeks = setOf(4)
            )
        )
        database.courseDao().insertWeek(CourseWeekEntity(courseId = 10L, week = 3))

        val courses = database.courseDao()
            .observeBySemesterWeekAndDay(semesterId = 1L, week = 3, dayOfWeek = 2)
            .first()

        assertEquals(listOf(10L), courses.map(CourseEntity::id))
        assertEquals(listOf(3), database.courseDao().findWithWeeksById(10L)?.weeks?.map(CourseWeekEntity::week))
    }

    @Test
    fun healthBudgetAndImportDaosSupportUpdateAndDelete() = runTest {
        val healthDao = database.healthDao()
        healthDao.insertWeight(
            WeightRecordEntity(id = 1L, recordedAt = 1L, weightJin = 120.0, source = "TEST")
        )
        healthDao.updateWeight(
            WeightRecordEntity(id = 1L, recordedAt = 2L, weightJin = 119.5, source = "TEST")
        )
        assertEquals(119.5, healthDao.findWeightById(1L)?.weightJin, 0.0)
        healthDao.deleteWeightById(1L)
        assertNull(healthDao.findWeightById(1L))

        healthDao.insertActivity(
            ActivityRecordEntity(id = 2L, recordedAt = 1L, activityType = ActivityType.WALK, source = "TEST")
        )
        healthDao.updateActivity(
            ActivityRecordEntity(id = 2L, recordedAt = 2L, activityType = ActivityType.RUN, source = "TEST")
        )
        assertEquals(ActivityType.RUN, healthDao.findActivityById(2L)?.activityType)
        healthDao.deleteActivityById(2L)
        assertNull(healthDao.findActivityById(2L))

        healthDao.insertMonthlyReport(
            MonthlyReportEntity(
                id = 3L,
                month = java.time.YearMonth.of(2026, 8),
                weightTrendSummary = "stable",
                activitySummary = "active",
                generatedAt = 1L,
                adviceSource = AdviceSource.LOCAL,
                generationStatus = ReportGenerationStatus.COMPLETE
            )
        )
        healthDao.updateMonthlyReport(
            MonthlyReportEntity(
                id = 3L,
                month = java.time.YearMonth.of(2026, 8),
                weightTrendSummary = "down",
                activitySummary = "active",
                generatedAt = 2L,
                adviceSource = AdviceSource.LOCAL,
                generationStatus = ReportGenerationStatus.COMPLETE
            )
        )
        assertEquals("down", healthDao.findMonthlyReportById(3L)?.weightTrendSummary)
        healthDao.deleteMonthlyReportById(3L)
        assertNull(healthDao.findMonthlyReportById(3L))

        val budgetDao = database.budgetDao()
        budgetDao.insert(BudgetEntity(month = "2026-08", budgetCents = 300L, triggeredPercentages = emptySet(), updatedAt = 1L))
        budgetDao.update(BudgetEntity(month = "2026-08", budgetCents = 500L, triggeredPercentages = setOf(50), updatedAt = 2L))
        assertEquals(500L, budgetDao.findByMonth("2026-08")?.budgetCents)
        budgetDao.deleteByMonth("2026-08")
        assertNull(budgetDao.findByMonth("2026-08"))

        val importLogDao = database.importLogDao()
        importLogDao.insert(ImportLogEntity("batch-2", "test.csv", "TEST", 1L, 1, 1, 0))
        importLogDao.update(ImportLogEntity("batch-2", "test.csv", "TEST", 2L, 2, 2, 0))
        assertEquals(2, importLogDao.findByBatchId("batch-2")?.totalRows)
        importLogDao.deleteByBatchId("batch-2")
        assertNull(importLogDao.findByBatchId("batch-2"))
    }
}
