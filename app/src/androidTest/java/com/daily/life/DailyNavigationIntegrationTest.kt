package com.daily.life

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyNavigationIntegrationTest {
    @Test
    fun mainActivityOpensWithDailyIdentity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertEquals("com.daily.life", activity.packageName) }
        }
    }
}
