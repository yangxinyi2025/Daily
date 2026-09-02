package com.daily.life

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyPermissionDegradedModeTest {
    @Test
    fun activityCanBeCreatedBeforeOptionalPermissions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(!it.isFinishing) }
        }
    }
}
