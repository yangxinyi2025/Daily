package com.daily.life

import android.app.Application

class DailyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.install(this)
    }
}

internal object AppContainer {
    fun install(application: Application) {
        @Suppress("UNUSED_VARIABLE")
        val unused = application
    }
}
