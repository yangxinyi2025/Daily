package com.daily.life.core.calendar

import androidx.work.ListenableWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayCalendarSyncWorkerTest {
    @Test
    fun missingProviderIsSuccessful() = runBlocking {
        assertTrue(HolidayCalendarSyncWorker.execute(null) is ListenableWorker.Result.Success)
    }
}
