package com.daily.life.feature.bill

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillParserTest {
    @Test
    fun parsesExpenseAmountAsPositiveCentsWithExpenseDirection() {
        val result = parseFixture("fixtures/wechat-synthetic.csv", BillSource.WECHAT)
        val row = result.rows.first()

        assertEquals(12_345L, row.amountCents)
        assertEquals(Direction.EXPENSE, row.direction)
        assertEquals("星巴克", row.counterparty)
        assertEquals("wx-order-1", row.orderId)
        assertEquals(Category.FOOD, row.category)
        assertTrue(row.rawText!!.contains("123.45"))
    }

    @Test
    fun recognizesAlipayHeadersAndIncomeDirection() {
        val result = parseFixture("fixtures/alipay-synthetic.csv", BillSource.ALIPAY)
        val row = result.rows[1]

        assertEquals(1_000L, row.amountCents)
        assertEquals(Direction.INCOME, row.direction)
        assertEquals("电商平台", row.counterparty)
        assertEquals(Category.INCOME, row.category)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun duplicateCandidatesRemainVisibleInPreview() {
        val result = parseFixture("fixtures/wechat-synthetic.csv", BillSource.WECHAT)

        assertEquals(3, result.rows.size)
        assertEquals(1, result.duplicateCandidates.size)
        assertTrue(result.duplicateCandidates.single().candidateRow.isDuplicateCandidate)
        assertFalse(result.rows.none { it.isDuplicateCandidate })
    }

    @Test
    fun parserClosesInputStreamAfterParsing() {
        val tracking = TrackingInputStream(
            "交易时间,交易对方,收/支,金额(元)\n2026-08-01 12:00:00,测试商户,支出,1.00\n"
                .toByteArray(StandardCharsets.UTF_8)
        )

        CsvBillParser().parse(tracking, BillSource.WECHAT)

        assertTrue(tracking.closed)
    }

    private fun parseFixture(path: String, source: BillSource): BillParseResult =
        javaClass.classLoader!!.getResourceAsStream(path)!!.use { input ->
            CsvBillParser().parse(input, source)
        }

    private class TrackingInputStream(bytes: ByteArray) : FilterInputStream(ByteArrayInputStream(bytes)) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
