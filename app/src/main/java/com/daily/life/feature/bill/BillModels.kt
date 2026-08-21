package com.daily.life.feature.bill

import com.daily.life.core.database.TransactionDirection
import java.time.LocalDate
import java.time.YearMonth

enum class BillSource(val label: String) {
    WECHAT("微信"),
    ALIPAY("支付宝")
}

enum class Direction {
    INCOME,
    EXPENSE
}

enum class Category(val label: String) {
    FOOD("餐饮"),
    TRANSPORT("交通"),
    SHOPPING("购物"),
    ENTERTAINMENT("娱乐"),
    BILLS("账单"),
    HEALTH("健康"),
    EDUCATION("教育"),
    TRANSFER("转账"),
    INCOME("收入"),
    OTHER("其他")
}

fun Direction.toEntity(): TransactionDirection = when (this) {
    Direction.INCOME -> TransactionDirection.INCOME
    Direction.EXPENSE -> TransactionDirection.EXPENSE
}

fun TransactionDirection.toBillDirection(): Direction = when (this) {
    TransactionDirection.INCOME -> Direction.INCOME
    TransactionDirection.EXPENSE -> Direction.EXPENSE
}

data class BillParseWarning(
    val rowNumber: Int?,
    val message: String
)

data class BillPreviewRow(
    val rowNumber: Int,
    val occurredAt: Long,
    val amountCents: Long,
    val direction: Direction,
    val category: Category,
    val counterparty: String,
    val source: BillSource,
    val rawText: String,
    val paymentMethod: String? = null,
    val transactionType: String? = null,
    val status: String? = null,
    val merchantOrderId: String? = null,
    val orderId: String? = null,
    val notes: String? = null,
    val isDuplicateCandidate: Boolean = false,
    val include: Boolean = true,
    val id: Long = 0L
)

data class DuplicateCandidate(
    val candidateRow: BillPreviewRow,
    val matchedRow: BillPreviewRow
)

data class BillParseResult(
    val rows: List<BillPreviewRow>,
    val warnings: List<BillParseWarning> = emptyList(),
    val skippedRows: Int = 0,
    val duplicateCandidates: List<DuplicateCandidate> = emptyList(),
    val source: BillSource? = rows.firstOrNull()?.source
)

interface BillParser {
    fun parse(input: java.io.InputStream, source: BillSource): BillParseResult
}

enum class BillPeriod(val label: String) {
    MONTH("月"),
    WEEK("周"),
    YEAR("年")
}

data class BillFilter(
    val period: BillPeriod = BillPeriod.MONTH,
    val month: YearMonth = YearMonth.now(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val direction: Direction? = null,
    val category: Category? = null,
    val searchText: String = ""
)

data class BillStatistics(
    val transactions: List<BillPreviewRow> = emptyList(),
    val count: Int = 0,
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    val categoryTotals: Map<Category, Long> = emptyMap(),
    val budgetCents: Long? = null,
    val budgetProgressPercent: Int? = null,
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null
)

data class BudgetThresholdResult(
    val newNotifications: Set<Int>,
    val triggeredPercentages: Set<Int>
)

data class BillImportState(
    val isOpen: Boolean = false,
    val fileName: String? = null,
    val preview: BillParseResult? = null,
    val isParsing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

data class BillEditorState(
    val transaction: BillPreviewRow? = null,
    val amountText: String = "",
    val dateText: String = "",
    val counterpartyText: String = "",
    val category: Category = Category.OTHER,
    val direction: Direction = Direction.EXPENSE,
    val notesText: String = ""
)

internal fun Category.toDisplayLabel(): String = label
