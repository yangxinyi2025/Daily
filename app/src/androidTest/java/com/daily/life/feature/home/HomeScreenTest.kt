package com.daily.life.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.daily.life.MainActivity
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsSummaryCardsAndSettingsValuesPersistAcrossNavigation() {
        composeRule.onNodeWithText("你好").assertIsDisplayed()
        composeRule.onNodeWithText("今日课表").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("打开设置").performClick()

        composeRule.onNodeWithTag("settings_target_weight").performTextClearance()
        composeRule.onNodeWithTag("settings_target_weight").performTextInput("132.5")
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("今日课表").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("打开设置").performClick()
        composeRule.onNodeWithTag("settings_target_weight").assertTextContains("132.5")
    }
}
