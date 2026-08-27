package com.daily.life

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.daily.life.core.AppContainer
import com.daily.life.core.DefaultAppContainer

class DailyApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultAppContainer(this)
    }

    @Synchronized
    fun initializePdfBox() {
        PDFBoxResourceLoader.init(this)
    }
}
