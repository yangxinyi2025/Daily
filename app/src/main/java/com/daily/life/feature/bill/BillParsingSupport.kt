package com.daily.life.feature.bill

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal data class BillHeaderMap(
    val date: Int,
    val counterparty: Int,
    val direction: Int,
    val amount: Int,
    val rawType: Int? = null,
    val paymentMethod: Int? = null,
    val status: Int? = null,
    val orderId: Int? = null,
    val merchantOrderId: Int? = null,
    val notes: Int? = null,
    val description: Int? = null
)

internal data class RawBillRow(
    val rowNumber: Int,
    val cells: List<String>,
    val rawText: String
)

internal object BillParsingSupport {
    private val classifier = BillClassifier()
    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
    )

    fun parseRows(rows: List<RawBillRow>, source: BillSource): BillParseResult {
        val headerIndex = rows.indexOfFirst { row -> findHeaderMap(row.cells) != null }
        if (headerIndex < 0) {
            return BillParseResult(
                rows = emptyList(),
                warnings = listOf(BillParseWarning(null, "未找到包含日期、金额和收支方向的表头")),
                skippedRows = rows.count { it.cells.any(String::isNotBlank) },
                source = source
            )
        }
        val header = findHeaderMap(rows[headerIndex].cells)!!
        val warnings = mutableListOf<BillParseWarning>()
        val parsed = mutableListOf<BillPreviewRow>()
        var skipped = 0
        rows.drop(headerIndex + 1).forEach { row ->
            if (row.cells.all(String::isBlank)) return@forEach
            val directionText = cell(row, header.direction)
            val rawAmountText = cell(row, header.amount)
            val status = header.status?.let { cell(row, it) }
            if (status.containsAny("关闭", "失败", "撤销", "取消")) {
                skipped += 1
                warnings += BillParseWarning(row.rowNumber, "已跳过非成功交易：$status")
                return@forEach
            }
            val occurredAt = parseDate(cell(row, header.date))
            val amountCents = parseAmountCents(rawAmountText)
            if (occurredAt == null || amountCents == null) {
                skipped += 1
                warnings += BillParseWarning(row.rowNumber, "无法识别日期或金额，已跳过")
                return@forEach
            }
            val direction = parseDirection(directionText, rawAmountText)
            val counterparty = cell(row, header.counterparty).ifBlank {
                header.description?.let { cell(row, it) }.orEmpty()
            }.ifBlank { "未命名交易" }
            val rawText = row.rawText
            parsed += BillPreviewRow(
                rowNumber = row.rowNumber,
                occurredAt = occurredAt,
                amountCents = amountCents,
                direction = direction,
                category = classifier.classify(counterparty, rawText, direction),
                counterparty = counterparty,
                source = source,
                rawText = rawText,
                paymentMethod = header.paymentMethod?.let { cell(row, it).nullIfBlank() },
                transactionType = header.rawType?.let { cell(row, it).nullIfBlank() },
                status = status.nullIfBlank(),
                merchantOrderId = header.merchantOrderId?.let { cell(row, it).nullIfBlank() },
                orderId = header.orderId?.let { cell(row, it).nullIfBlank() },
                notes = header.notes?.let { cell(row, it).nullIfBlank() }
            )
        }

        val firstByFingerprint = linkedMapOf<String, BillPreviewRow>()
        val duplicateCandidates = mutableListOf<DuplicateCandidate>()
        val rowsWithDuplicates = parsed.map { row ->
            val previous = firstByFingerprint.putIfAbsent(row.fingerprint(), row)
            if (previous == null) {
                row
            } else {
                val duplicate = row.copy(isDuplicateCandidate = true)
                duplicateCandidates += DuplicateCandidate(duplicate, previous)
                duplicate
            }
        }
        return BillParseResult(
            rows = rowsWithDuplicates,
            warnings = warnings,
            skippedRows = skipped,
            duplicateCandidates = duplicateCandidates,
            source = source
        )
    }

    fun decode(bytes: ByteArray): String {
        val utf8 = if (bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) {
            String(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
        } else {
            String(bytes, StandardCharsets.UTF_8)
        }
        return if ('\uFFFD' in utf8) {
            String(bytes, Charset.forName("GBK")).removePrefix("\uFEFF")
        } else {
            utf8.removePrefix("\uFEFF")
        }
    }

    fun parseCsvLines(text: String): List<RawBillRow> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').mapIndexed { index, line ->
            RawBillRow(index + 1, parseCsvLine(line), line)
        }

    fun parseExcelRows(rows: List<RawBillRow>, source: BillSource): BillParseResult = parseRows(rows, source)

    fun findHeaderMap(cells: List<String>): BillHeaderMap? {
        val normalized = cells.map(::normalizeHeader)
        fun find(vararg names: String): Int? = names.firstNotNullOfOrNull { name ->
            normalized.indexOfFirst { it == normalizeHeader(name) }.takeIf { it >= 0 }
        }
        val date = find("交易时间", "交易日期", "日期", "时间") ?: return null
        val amount = find("金额(元)", "金额", "收支金额", "交易金额") ?: return null
        val direction = find("收/支", "收支", "交易方向") ?: return null
        val counterparty = find("交易对方", "对方", "商户", "付款方", "收款方") ?: return null
        return BillHeaderMap(
            date = date,
            counterparty = counterparty,
            direction = direction,
            amount = amount,
            rawType = find("交易类型", "交易分类"),
            paymentMethod = find("支付方式", "资金渠道", "付款方式"),
            status = find("当前状态", "交易状态", "状态"),
            orderId = find("交易单号", "交易订单号", "订单号"),
            merchantOrderId = find("商户单号", "商家订单号", "商户订单号"),
            notes = find("备注"),
            description = find("商品", "商品说明", "商品名称", "说明")
        )
    }

    fun parseDate(value: String): Long? {
        val normalized = value.trim()
        normalized.toLongOrNull()?.let { numeric ->
            return if (numeric < 100_000_000_000L) numeric * 1_000L else numeric
        }
        dateFormatters.firstNotNullOfOrNull { formatter ->
            try {
                LocalDateTime.parse(normalized, formatter)
            } catch (_: DateTimeParseException) {
                null
            }
        }?.let { dateTime -> return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        try {
            return LocalDate.parse(normalized).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            return null
        }
    }

    fun parseAmountCents(value: String): Long? {
        val cleaned = value.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
            .replace("元", "")
            .replace("(", "-")
            .replace(")", "")
        return try {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun parseDirection(direction: String, amount: String): Direction {
        val normalized = direction.trim()
        return when {
            normalized.containsAny("收入", "收款", "入账", "转入", "退款", "充值") -> Direction.INCOME
            normalized.containsAny("支出", "付款", "消费", "转出") -> Direction.EXPENSE
            amount.trim().startsWith("-") || amount.trim().startsWith("(") -> Direction.EXPENSE
            else -> Direction.EXPENSE
        }
    }

    fun rowFromCells(rowNumber: Int, cells: List<String>): RawBillRow =
        RawBillRow(rowNumber, cells, cells.joinToString(","))

    private fun cell(row: RawBillRow, index: Int): String = row.cells.getOrNull(index).orEmpty().trim()

    private fun normalizeHeader(value: String): String = value
        .trim()
        .removePrefix("\uFEFF")
        .replace(" ", "")
        .replace("（", "(")
        .replace("）", ")")
        .lowercase()

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val character = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index += 1
                } else {
                    quoted = !quoted
                }
                ',' -> if (quoted) current.append(character) else {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
            index += 1
        }
        result += current.toString()
        return result
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf(String::isNotBlank)

    private fun BillPreviewRow.fingerprint(): String = listOf(
        source.name,
        occurredAt,
        amountCents,
        counterparty.trim().lowercase(),
        orderId.orEmpty(),
        merchantOrderId.orEmpty()
    ).joinToString("|")

    private fun String?.containsAny(vararg values: String): Boolean = values.any { value -> this?.contains(value) == true }
}
