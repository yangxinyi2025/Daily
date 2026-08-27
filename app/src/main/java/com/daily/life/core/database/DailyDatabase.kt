package com.daily.life.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SemesterEntity::class,
        SemesterPeriodEntity::class,
        SemesterCalendarAdjustmentEntity::class,
        CourseEntity::class,
        CourseWeekEntity::class,
        ScheduleEventEntity::class,
        WeightRecordEntity::class,
        PeriodRecordEntity::class,
        ActivityRecordEntity::class,
        MonthlyReportEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        ImportLogEntity::class,
        CalendarSyncLinkEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(DailyConverters::class)
abstract class DailyDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun semesterPeriodDao(): SemesterPeriodDao
    abstract fun semesterCalendarAdjustmentDao(): SemesterCalendarAdjustmentDao
    abstract fun courseDao(): CourseDao
    abstract fun scheduleEventDao(): ScheduleEventDao
    abstract fun healthDao(): HealthDao
    abstract fun periodDao(): PeriodDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun importLogDao(): ImportLogDao
    abstract fun calendarSyncLinkDao(): CalendarSyncLinkDao

    companion object {
        private const val DATABASE_NAME = "daily.db"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `course_weeks` (
                        `courseId` INTEGER NOT NULL,
                        `week` INTEGER NOT NULL,
                        PRIMARY KEY(`courseId`, `week`),
                        FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_course_weeks_week_courseId` ON `course_weeks` (`week`, `courseId`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_weight_records_recordedAt` ON `weight_records` (`recordedAt`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_records_recordedAt` ON `activity_records` (`recordedAt`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_records_rawRecordId` ON `activity_records` (`rawRecordId`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `period_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `endDate` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_period_records_startDate` ON `period_records` (`startDate`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_period_records_endDate` ON `period_records` (`endDate`)"
                )
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE courses ADD COLUMN courseReminderMode TEXT NOT NULL DEFAULT 'FOLLOW_GLOBAL'")
                database.execSQL("ALTER TABLE courses ADD COLUMN courseReminderMinutes INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar_sync_links` (
                        `ownerKind` TEXT NOT NULL,
                        `ownerKey` TEXT NOT NULL,
                        `calendarId` INTEGER NOT NULL,
                        `eventId` INTEGER NOT NULL,
                        `eventStartAt` INTEGER NOT NULL,
                        `lastError` TEXT,
                        PRIMARY KEY(`ownerKind`, `ownerKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `semester_periods` (
                        `semesterId` INTEGER NOT NULL,
                        `period` INTEGER NOT NULL,
                        `startTime` TEXT NOT NULL,
                        `endTime` TEXT NOT NULL,
                        PRIMARY KEY(`semesterId`, `period`),
                        FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_semester_periods_semesterId` ON `semester_periods` (`semesterId`)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `semester_calendar_adjustments` (
                        `semesterId` INTEGER NOT NULL,
                        `actualDate` TEXT NOT NULL,
                        `sourceDayOfWeek` INTEGER NOT NULL,
                        `sourceDate` TEXT,
                        `sourceLabel` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`semesterId`, `actualDate`),
                        FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_semester_calendar_adjustments_semesterId` ON `semester_calendar_adjustments` (`semesterId`)"
                )
            }
        }

        fun build(context: Context): DailyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8
            ).build()

        fun buildInMemory(context: Context): DailyDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java
            ).allowMainThreadQueries().build()
    }
}
