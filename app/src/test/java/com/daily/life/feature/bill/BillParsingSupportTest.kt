package com.daily.life.feature.bill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillParsingSupportTest {
    @Test
    fun exposesWhyNonSuccessfulRowsWereSkipped() {
        val result = BillParsingSupport.parseRows(
            listOf(
                BillParsingSupport.rowFromCells(1, listOf("交易时间", "交易对方", "收/支", "金额(元)", "当前状态")),
                BillParsingSupport.rowFromCells(2, listOf("2026-08-12 10:00:00", "测试商户", "支出", "12.50", "交易关闭"))
            ),
            BillSource.WECHAT
        )

        assertEquals(0, result.rows.size)
        assertEquals(1, result.skippedRows)
        assertEquals("已跳过非成功交易：交易关闭", result.skippedDetails.single().reason)
        assertTrue(result.skippedDetails.single().rawPreview.contains("测试商户"))
    }

    @Test
    fun identifiesWhichFieldMadeAnInvalidRowSkip() {
        val result = BillParsingSupport.parseRows(
            listOf(
                BillParsingSupport.rowFromCells(1, listOf("交易时间", "交易对方", "收/支", "金额(元)")),
                BillParsingSupport.rowFromCells(2, listOf("不是日期", "测试商户", "支出", "不是金额"))
            ),
            BillSource.ALIPAY
        )

        assertEquals("无法识别日期或金额，已跳过", result.skippedDetails.single().reason)
    }
}
