package com.daily.life.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daily.life.MainActivity
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class DailyNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationShowsFivePrimaryDestinationsAndNavigatesWithoutRecreatingActivity() {
        val firstActivity = currentActivity()

        listOf("首页", "课表", "日程", "健康", "账单").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }

        composeRule.onNodeWithText("健康").performClick()
        composeRule.onNodeWithText("健康页面").assertIsDisplayed()

        assertSame(firstActivity, currentActivity())
    }

    private fun currentActivity(): ComponentActivity {
        lateinit var activity: ComponentActivity
        composeRule.activityRule.scenario.onActivity { activity = it }
        return activity
    }
}
