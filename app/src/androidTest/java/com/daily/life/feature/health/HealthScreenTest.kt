package com.daily.life.feature.health

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daily.life.core.database.ActivityType
import com.daily.life.core.designsystem.DailyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class HealthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun healthScreenShowsTabbedContentForWeightActivityAndReport() {
        composeRule.setContent {
            var state by remember { mutableStateOf(sampleState()) }
            DailyTheme {
                HealthScreen(
                    state = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onCurrentMonth = {},
                    onSelectTab = { tab -> state = state.copy(selectedTab = tab) },
                    onRecordWeight = {},
                    onSetTargetWeight = {},
                    onReadActivity = {},
                    onRegenerateReport = {}
                )
            }
        }

        composeRule.onNodeWithText("体重").assertIsDisplayed()
        composeRule.onNodeWithText("活动").assertIsDisplayed()
        composeRule.onNodeWithText("月报").assertIsDisplayed()

        composeRule.onNodeWithText("记录体重").assertIsDisplayed()
        composeRule.onNodeWithText("最近 30 天体重").assertIsDisplayed()
        composeRule.onNodeWithText("读取手机健康数据").assertDoesNotExist()
        composeRule.onNodeWithText("重新生成月报").assertDoesNotExist()

        composeRule.onNodeWithText("活动").performClick()
        composeRule.onNodeWithText("读取手机健康数据").assertIsDisplayed()
        composeRule.onNodeWithText("活动明细").assertIsDisplayed()
        composeRule.onNodeWithText("记录体重").assertDoesNotExist()

        composeRule.onNodeWithText("月报").performClick()
        composeRule.onNodeWithText("重新生成月报").assertIsDisplayed()
        composeRule.onNodeWithText("读取手机健康数据").assertDoesNotExist()
    }

    private fun sampleState(): HealthState = HealthState(
        selectedMonth = YearMonth.of(2026, 8),
        selectedTab = HealthTab.WEIGHT,
        weights = listOf(
            WeightRecord(
                recordedAt = Instant.parse("2026-08-20T07:00:00Z"),
                weightJin = 136.0,
                source = "MANUAL"
            )
        ),
        activities = listOf(
            ActivityRecord(
                recordedAt = Instant.parse("2026-08-20T08:00:00Z"),
                activityType = ActivityType.WALK,
                steps = 2_000L,
                source = "TEST"
            )
        ),
        report = LocalReport(
            month = YearMonth.of(2026, 8),
            monthAverageJin = 136.0,
            monthOverMonthJin = -1.5,
            weeklyAveragesJin = mapOf(LocalDate.parse("2026-08-17") to 136.0),
            recentWeights = listOf(WeightPoint(LocalDate.parse("2026-08-20"), 136.0)),
            totalSteps = 2_000L,
            walkingSteps = 2_000L,
            runningSteps = 0L,
            totalDistanceMeters = 1_500.0,
            totalDurationMinutes = 20,
            targetDifferenceJin = 6.0,
            dataState = ReportDataState.READY,
            weightTrendSummary = "本月平均 136.0 斤",
            activitySummary = "本月 2000 步（步行 2000，跑步 0）"
        ),
        targetWeightJin = 130.0
    )
}
