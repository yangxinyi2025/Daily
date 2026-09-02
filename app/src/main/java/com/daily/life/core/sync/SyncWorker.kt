package com.daily.life.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (!enabledCheck()) return Result.success()
        val repository = provider?.invoke(applicationContext) ?: return Result.success()
        val result = repository.upload()
        return if (result.status == SyncStatus.Failed) Result.retry() else Result.success()
    }

    companion object {
        private var provider: ((Context) -> WebDavSyncRepository?)? = null
        private var enabledCheck: suspend () -> Boolean = { false }

        fun installProvider(
            factory: (Context) -> WebDavSyncRepository?,
            isEnabled: suspend () -> Boolean
        ) {
            provider = factory
            enabledCheck = isEnabled
        }

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("daily-webdav-sync", ExistingWorkPolicy.KEEP, request)
        }
    }
}
