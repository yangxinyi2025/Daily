package com.daily.life.feature.bill

import androidx.room.withTransaction
import com.daily.life.core.database.BudgetEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ImportLogEntity
import com.daily.life.core.database.TransactionEntity
import com.daily.life.core.datastore.DailyPreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BillRepository(
    private val database: DailyDatabase,
    private val preferences: DailyPreferences,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val queryDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun observeTransactions(): Flow<List<BillPreviewRow>> =
        database.transactionDao().observeAll().map { rows -> rows.map(::toPreviewRow) }

    suspend fun preview(parsed: BillParseResult): BillParseResult {
        val existingFingerprints = database.transactionDao().observeAll().first()
            .map(::fingerprint)
            .toSet()
        val duplicateCandidates = parsed.duplicateCandidates.toMutableList()
        val rows = parsed.rows.map { row ->
            if (fingerprint(row) in existingFingerprints && !row.isDuplicateCandidate) {
                val duplicate = row.copy(isDuplicateCandidate = true)
                duplicateCandidates += DuplicateCandidate(duplicate, duplicate)
                duplicate
            } else {
                row
            }
        }
        return parsed.copy(rows = rows, duplicateCandidates = duplicateCandidates)
    }

    suspend fun confirmImport(
        preview: BillParseResult,
        fileName: String = "账单导入"
    ): ImportLogEntity {
        val includedRows = preview.rows.filter(BillPreviewRow::include)
        val batchId = UUID.randomUUID().toString()
        val importedAt = clock.millis()
        val entities = includedRows.map { row ->
            TransactionEntity(
                occurredAt = row.occurredAt,
                amountCents = row.amountCents,
                direction = row.direction.toEntity(),
                category = row.category.label,
                counterparty = row.counterparty,
                source = row.source.name,
                paymentMethod = row.paymentMethod,
                transactionType = row.transactionType,
                status = row.status,
                merchantOrderId = row.merchantOrderId,
                orderId = row.orderId,
                rawText = row.rawText,
                notes = row.notes,
                id = row.id,
                importBatchId = batchId,
                createdAt = importedAt,
                updatedAt = importedAt
            )
        }
        val log = ImportLogEntity(
            batchId = batchId,
            fileName = fileName,
            sourceType = preview.source?.name ?: "UNKNOWN",
            importedAt = importedAt,
            totalRows = preview.rows.size + preview.skippedRows,
            successRows = includedRows.size,
            skippedRows = preview.skippedRows + (preview.rows.size - includedRows.size),
            errorSummary = preview.warnings.joinToString("；") { warning -> warning.message }
                .takeIf(String::isNotBlank)
        )
        database.withTransaction {
            database.transactionDao().insertAll(entities)
            database.importLogDao().insert(log)
        }
        return log
    }

    suspend fun updateTransaction(row: BillPreviewRow) {
        database.transactionDao().update(
            TransactionEntity(
                id = row.id.takeIf { it > 0L } ?: row.rowNumber.toLong(),
                occurredAt = row.occurredAt,
                amountCents = row.amountCents,
                direction = row.direction.toEntity(),
                category = row.category.label,
                counterparty = row.counterparty,
                source = row.source.name,
                paymentMethod = row.paymentMethod,
                transactionType = row.transactionType,
                status = row.status,
                merchantOrderId = row.merchantOrderId,
                orderId = row.orderId,
                rawText = row.rawText,
                notes = row.notes,
                updatedAt = clock.millis()
            )
        )
    }

    suspend fun deleteTransaction(id: Long) {
        database.transactionDao().deleteById(id)
    }

    suspend fun setBudget(month: YearMonth, budgetCents: Long?): BudgetEntity? {
        require(budgetCents == null || budgetCents >= 0L) { "预算不能为负数" }
        if (budgetCents == null) {
            database.budgetDao().deleteByMonth(month.toString())
            return null
        }
        val existing = database.budgetDao().findByMonth(month.toString())
        val budget = BudgetEntity(
            month = month.toString(),
            budgetCents = budgetCents,
            triggeredPercentages = existing?.triggeredPercentages.orEmpty(),
            updatedAt = clock.millis()
        )
        database.budgetDao().upsert(budget)
        return budget
    }

    suspend fun evaluateBudgetThreshold(month: YearMonth): BudgetThresholdResult {
        val statistics = statistics(BillFilter(month = month, period = BillPeriod.MONTH))
        val budget = database.budgetDao().findByMonth(month.toString())
            ?: preferences.defaultBudgetCents.first()?.let { default ->
                BudgetEntity(month.toString(), default, emptySet(), clock.millis())
            }
            ?: return BudgetThresholdResult(emptySet(), emptySet())
        val result = BudgetThresholdCalculator.evaluate(
            spendingCents = statistics.expenseCents,
            budgetCents = budget.budgetCents,
            triggered = budget.triggeredPercentages
        )
        database.budgetDao().upsert(
            budget.copy(
                triggeredPercentages = result.triggeredPercentages,
                updatedAt = clock.millis()
            )
        )
        return result
    }

    suspend fun statistics(filter: BillFilter): BillStatistics = withContext(queryDispatcher) {
        val zone = clock.zone
        val (startDate, endDate) = filter.dateRange()
        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = database.transactionDao().findAll()
            .filter { it.occurredAt >= start && it.occurredAt < end }
            .map(::toPreviewRow)
            .filter { row -> filter.direction == null || row.direction == filter.direction }
            .filter { row -> filter.category == null || row.category == filter.category }
            .filter { row ->
                filter.searchText.isBlank() || listOfNotNull(
                    row.counterparty,
                    row.notes,
                    row.rawText
                ).any { value -> value.contains(filter.searchText, ignoreCase = true) }
            }
        val categoryTotals = rows.filter { it.direction == Direction.EXPENSE }
            .groupingBy(BillPreviewRow::category)
            .fold(0L) { total, row -> total + row.amountCents }
        val expense = rows.filter { it.direction == Direction.EXPENSE }.sumOf(BillPreviewRow::amountCents)
        val income = rows.filter { it.direction == Direction.INCOME }.sumOf(BillPreviewRow::amountCents)
        val budget = database.budgetDao().findByMonth(filter.month.toString())?.budgetCents
            ?: preferences.defaultBudgetCents.first()
        BillStatistics(
            transactions = rows,
            count = rows.size,
            expenseCents = expense,
            incomeCents = income,
            categoryTotals = categoryTotals,
            budgetCents = budget,
            budgetProgressPercent = budget?.takeIf { it > 0L }?.let { (expense * 100L / it).toInt() },
            rangeStart = startDate,
            rangeEnd = endDate
        )
    }

    private fun BillFilter.dateRange(): Pair<LocalDate, LocalDate> {
        if (startDate != null && endDate != null) return startDate to endDate
        return when (period) {
            BillPeriod.MONTH -> month.atDay(1) to month.atEndOfMonth()
            BillPeriod.YEAR -> month.atDay(1).withDayOfYear(1) to month.atDay(1).with(TemporalAdjusters.lastDayOfYear())
            BillPeriod.WEEK -> {
                val date = weekAnchor ?: month.atDay(1)
                val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
                start to start.plusDays(6)
            }
        }
    }

    private fun toPreviewRow(entity: TransactionEntity): BillPreviewRow {
        val source = runCatching { BillSource.valueOf(entity.source) }.getOrDefault(BillSource.WECHAT)
        val category = Category.entries.firstOrNull { it.label == entity.category } ?: Category.OTHER
        return BillPreviewRow(
            rowNumber = entity.id.toInt(),
            occurredAt = entity.occurredAt,
            amountCents = entity.amountCents,
            direction = entity.direction.toBillDirection(),
            category = category,
            counterparty = entity.counterparty,
            source = source,
            rawText = entity.rawText.orEmpty(),
            paymentMethod = entity.paymentMethod,
            transactionType = entity.transactionType,
            status = entity.status,
            merchantOrderId = entity.merchantOrderId,
            orderId = entity.orderId,
            notes = entity.notes,
            id = entity.id
        )
    }

    private fun fingerprint(entity: TransactionEntity): String = listOf(
        entity.source,
        entity.occurredAt,
        entity.amountCents,
        entity.counterparty.trim().lowercase(),
        entity.orderId.orEmpty(),
        entity.merchantOrderId.orEmpty()
    ).joinToString("|")

    private fun fingerprint(row: BillPreviewRow): String = listOf(
        row.source.name,
        row.occurredAt,
        row.amountCents,
        row.counterparty.trim().lowercase(),
        row.orderId.orEmpty(),
        row.merchantOrderId.orEmpty()
    ).joinToString("|")
}
