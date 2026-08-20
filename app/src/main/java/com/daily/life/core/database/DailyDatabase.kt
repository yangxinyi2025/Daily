package com.daily.life.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
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

        fun build(context: Context): DailyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java,
                DATABASE_NAME
            ).build()

        fun buildInMemory(context: Context): DailyDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java
            ).allowMainThreadQueries().build()
    }
}
