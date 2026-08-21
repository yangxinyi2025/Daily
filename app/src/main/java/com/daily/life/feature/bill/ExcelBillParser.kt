package com.daily.life.feature.bill

import java.io.InputStream
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory

class ExcelBillParser : BillParser {
    override fun parse(input: InputStream, source: BillSource): BillParseResult = input.use { stream ->
        WorkbookFactory.create(stream).use { workbook ->
            val formatter = DataFormatter()
            val rows = workbook.sheetIterator().asSequence().flatMap { sheet ->
                sheet.rowIterator().asSequence().map { row ->
                    val cells = (0 until row.lastCellNum.toInt().coerceAtLeast(0)).map { index ->
                        formatter.formatCellValue(row.getCell(index))
                    }
                    BillParsingSupport.rowFromCells(row.rowNum + 1, cells)
                }
            }.toList()
            BillParsingSupport.parseExcelRows(rows, source)
        }
    }
}
