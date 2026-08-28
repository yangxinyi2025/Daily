package com.daily.life

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.daily.life.core.AppContainer
import com.daily.life.core.DefaultAppContainer
import com.daily.life.core.calendar.HolidayCalendarSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultAppContainer(this)
    }

    @Synchronized
    fun initializePdfBox() {
        PDFBoxResourceLoader.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        HolidayCalendarSyncWorker.installProvider { context ->
            (context.applicationContext as? DailyApplication)?.container?.holidayCalendarRepository
        }
        applicationScope.launch {
            container.holidayCalendarRepository.initialize()
            container.holidayCalendarRepository.syncIfStale()
        }
        HolidayCalendarSyncWorker.enqueue(this)
    }
}
