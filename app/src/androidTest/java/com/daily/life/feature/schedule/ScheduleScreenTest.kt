package com.daily.life.feature.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ScheduleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scheduleScreenShowsFiltersTimelineAndQuickActions() {
        composeRule.setContent {
            ScheduleScreen(
                state = ScheduleState(selectedDate = java.time.LocalDate.of(2026, 8, 21)),
                onViewModeChange = {},
                onDateSelected = {},
                onCreate = {},
                onQuickCreate = {},
                onEdit = {},
                onDelete = {},
                onSaveEditor = {},
                onDismissEditor = {},
                onEditorChange = {}
            )
        }

        composeRule.onNodeWithText("月").assertIsDisplayed()
        composeRule.onNodeWithText("周").assertIsDisplayed()
        composeRule.onNodeWithText("日").assertIsDisplayed()
        composeRule.onNodeWithText("考试").assertIsDisplayed()
        composeRule.onNodeWithText("生日").assertIsDisplayed()
        composeRule.onNodeWithText("小事").assertIsDisplayed()
        composeRule.onNodeWithText("时间线").assertIsDisplayed()
    }
}
