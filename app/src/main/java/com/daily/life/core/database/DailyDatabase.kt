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
        CourseEntity::class,
        CourseWeekEntity::class,
        ScheduleEventEntity::class,
        WeightRecordEntity::class,
        ActivityRecordEntity::class,
        MonthlyReportEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        ImportLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DailyConverters::class)
abstract class DailyDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun scheduleEventDao(): ScheduleEventDao
    abstract fun healthDao(): HealthDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun importLogDao(): ImportLogDao

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

        fun build(context: Context): DailyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2).build()

        fun buildInMemory(context: Context): DailyDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java
            ).allowMainThreadQueries().build()
    }
}
