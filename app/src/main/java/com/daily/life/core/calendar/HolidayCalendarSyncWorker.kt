package com.daily.life.core.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import androidx.work.workDataOf

class HolidayCalendarSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return execute { provider?.invoke(applicationContext)?.syncAllEnabledSources() }
    }

    companion object {
        internal suspend fun execute(sync: suspend () -> SyncSummary?): Result {
            return try {
                val summary = sync()
                if (summary?.retryableFailure == true) Result.retry() else Result.success()
            } catch (error: Exception) {
                Result.failure(workDataOf("error" to (error.message ?: error.javaClass.simpleName)))
            }
        }

        private var provider: ((Context) -> HolidayCalendarRepository?)? = null
        const val UNIQUE_WORK_NAME = "daily-holiday-calendar-sync"

        fun installProvider(factory: (Context) -> HolidayCalendarRepository?) {
            provider = factory
        }

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<HolidayCalendarSyncWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
