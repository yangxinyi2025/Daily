package com.daily.life.feature.health

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daily.life.MainActivity
import org.junit.Rule
import org.junit.Test

class HealthScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun healthPageKeepsManualActionsAvailableWithoutExternalProviders() {
        composeRule.onNodeWithText("健康").performClick()

        composeRule.onNodeWithText("健康页面").assertIsDisplayed()
        composeRule.onNodeWithText("记录体重").assertIsDisplayed()
        composeRule.onNodeWithText("读取手机健康数据").assertIsDisplayed()
        composeRule.onNodeWithText("本地月报").assertIsDisplayed()
    }
}
