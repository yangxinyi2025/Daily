package com.daily.life.core.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class HolidayCalendarSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return execute(provider?.invoke(applicationContext))
    }

    companion object {
        internal suspend fun execute(repository: HolidayCalendarRepository?): Result {
            return runCatching { repository?.syncAllEnabledSources() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.success() }
            )
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
