package com.daily.life.feature.bill

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.daily.life.core.designsystem.DailyTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BillImportTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun duplicateCandidateIsVisibleAndCancelDoesNotConfirmPreview() {
        var cancelled = false
        val row = BillPreviewRow(
            rowNumber = 2,
            occurredAt = Instant.parse("2026-08-01T04:00:00Z").toEpochMilli(),
            amountCents = 1_234L,
            direction = Direction.EXPENSE,
            category = Category.FOOD,
            counterparty = "重复商户",
            source = BillSource.WECHAT,
            rawText = "duplicate",
            isDuplicateCandidate = true
        )
        composeRule.setContent {
            DailyTheme {
                BillImportScreen(
                    state = BillImportState(
                        isOpen = true,
                        fileName = "wechat-synthetic.csv",
                        preview = BillParseResult(
                            rows = listOf(row),
                            duplicateCandidates = listOf(DuplicateCandidate(row, row)),
                            source = BillSource.WECHAT
                        )
                    ),
                    onChooseFile = {},
                    onToggleRow = {},
                    onCancel = { cancelled = true },
                    onConfirm = {}
                )
            }
        }

        composeRule.onNodeWithText("重复候选").assertIsDisplayed()
        composeRule.onNodeWithTag("bill_import_cancel").performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }
}
