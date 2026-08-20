package com.daily.life.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(119.5, healthDao.findWeightById(1L)?.weightJin ?: Double.NaN, 0.0)
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

    @Test
    fun migrationFrom1To2CreatesCourseWeeksTableWithoutDroppingCourses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "daily-migration-test.db"
        deleteDatabaseFiles(context, databaseName)

        createVersion1Database(context, databaseName).use { version1Database ->
            version1Database.execSQL(
                """
                INSERT INTO semesters (id, name, startDate, endDate, isCurrent, createdAt)
                VALUES (1, '2026 秋季', '2026-09-01', NULL, 0, 1700000000000)
                """.trimIndent()
            )
            version1Database.execSQL(
                """
                INSERT INTO courses (
                    id, semesterId, courseName, dayOfWeek, startPeriod, endPeriod,
                    weekRuleText, parsedWeeks, campus, location, teacher, courseCode, credits, notes
                ) VALUES (
                    10, 1, '数据库', 2, 3, 4, '第 3 周', '3',
                    NULL, NULL, NULL, NULL, NULL, NULL
                )
                """.trimIndent()
            )

            DailyDatabase.MIGRATION_1_2.migrate(version1Database)

            assertTrue(version1Database.hasTable("course_weeks"))
            assertTrue(version1Database.hasIndex("index_course_weeks_week_courseId"))
            assertEquals(1, version1Database.longForQuery("SELECT COUNT(*) FROM courses"))
            assertEquals(0, version1Database.longForQuery("SELECT COUNT(*) FROM course_weeks"))
        }

        deleteDatabaseFiles(context, databaseName)
    }

    private fun createVersion1Database(context: Context, databaseName: String): SupportSQLiteDatabase {
        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `semesters` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `startDate` TEXT NOT NULL,
                                `endDate` TEXT,
                                `isCurrent` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `courses` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `semesterId` INTEGER NOT NULL,
                                `courseName` TEXT NOT NULL,
                                `dayOfWeek` INTEGER NOT NULL,
                                `startPeriod` INTEGER NOT NULL,
                                `endPeriod` INTEGER NOT NULL,
                                `weekRuleText` TEXT NOT NULL,
                                `parsedWeeks` TEXT NOT NULL,
                                `campus` TEXT,
                                `location` TEXT,
                                `teacher` TEXT,
                                `courseCode` TEXT,
                                `credits` REAL,
                                `notes` TEXT,
                                FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_courses_semesterId_parsedWeeks_dayOfWeek` ON `courses` (`semesterId`, `parsedWeeks`, `dayOfWeek`)"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_courses_semesterId` ON `courses` (`semesterId`)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .writableDatabase
    }

    private fun deleteDatabaseFiles(context: Context, databaseName: String) {
        context.deleteDatabase(databaseName)
        context.getDatabasePath("$databaseName-wal").delete()
        context.getDatabasePath("$databaseName-shm").delete()
        context.getDatabasePath("$databaseName-journal").delete()
    }

    private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use { cursor ->
            cursor.moveToFirst()
        }

    private fun SupportSQLiteDatabase.hasIndex(indexName: String): Boolean =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(indexName)
        ).use { cursor ->
            cursor.moveToFirst()
        }

    private fun SupportSQLiteDatabase.longForQuery(sql: String): Long =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
