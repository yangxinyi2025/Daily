package com.daily.life

import android.app.Application
import com.daily.life.core.AppContainer
import com.daily.life.core.DefaultAppContainer

class DailyApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultAppContainer(this)
    }
}
