package com.daily.life.feature.bill

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelBillParserTest {
    @Test
    fun parsesSyntheticWorkbookAndKeepsCents() {
        val bytes = ByteArrayOutputStream().use { output ->
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("账单")
                val header = sheet.createRow(0)
                val data = sheet.createRow(1)
                listOf("交易时间", "交易对方", "收/支", "金额(元)").forEachIndexed { index, value ->
                    header.createCell(index).setCellValue(value)
                }
                listOf("2026-08-10 12:00:00", "Excel 商户", "支出", "42.50")
                    .forEachIndexed { index, value -> data.createCell(index).setCellValue(value) }
                workbook.write(output)
            }
            output.toByteArray()
        }

        val result = ExcelBillParser().parse(ByteArrayInputStream(bytes), BillSource.ALIPAY)

        assertEquals(1, result.rows.size)
        assertEquals(4_250L, result.rows.single().amountCents)
        assertTrue(result.warnings.isEmpty())
    }
}
