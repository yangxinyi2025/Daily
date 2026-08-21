package com.daily.life.feature.bill

import java.io.InputStream

class CsvBillParser : BillParser {
    override fun parse(input: InputStream, source: BillSource): BillParseResult = input.use { stream ->
        val text = BillParsingSupport.decode(stream.readBytes())
        BillParsingSupport.parseRows(BillParsingSupport.parseCsvLines(text), source)
    }
}
