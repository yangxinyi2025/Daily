package com.daily.life.feature.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillState(
    val selectedMonth: YearMonth,
    val selectedPeriod: BillPeriod = BillPeriod.MONTH,
    val selectedWeekAnchor: LocalDate? = null,
    val directionFilter: Direction? = null,
    val categoryFilter: Category? = null,
    val searchText: String = "",
    val statistics: BillStatistics = BillStatistics(),
    val importState: BillImportState = BillImportState(),
    val editorState: BillEditorState? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
) {
    val periodLabel: String
        get() = when (selectedPeriod) {
            BillPeriod.MONTH -> "${selectedMonth.year}年${selectedMonth.monthValue}月"
            BillPeriod.YEAR -> "${selectedMonth.year}年"
            BillPeriod.WEEK -> {
                val start = (selectedWeekAnchor ?: selectedMonth.atDay(1))
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                "${start.year}年${start.monthValue}月${start.dayOfMonth}日 - " +
                    "${start.plusDays(6).year}年${start.plusDays(6).monthValue}月${start.plusDays(6).dayOfMonth}日"
            }
        }

    val previousPeriodLabel: String
        get() = when (selectedPeriod) {
            BillPeriod.MONTH -> "上个月"
            BillPeriod.YEAR -> "上一年"
            BillPeriod.WEEK -> "上周"
        }

    val currentPeriodLabel: String
        get() = when (selectedPeriod) {
            BillPeriod.MONTH -> "本月"
            BillPeriod.YEAR -> "今年"
            BillPeriod.WEEK -> "本周"
        }

    val nextPeriodLabel: String
        get() = when (selectedPeriod) {
            BillPeriod.MONTH -> "下个月"
            BillPeriod.YEAR -> "下一年"
            BillPeriod.WEEK -> "下周"
        }
}

class BillViewModel(
    private val repository: BillRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val selectedMonth = MutableStateFlow(YearMonth.now(clock))
    private val selectedWeekAnchor = MutableStateFlow(todayWeekStart())
    private val _state = MutableStateFlow(
        BillState(selectedMonth = selectedMonth.value, selectedWeekAnchor = selectedWeekAnchor.value)
    )
    val state = _state.asStateFlow()

    init {
        scope.launch {
            repository.observeTransactions().collectLatest {
                refreshStatistics()
            }
        }
    }

    fun selectPreviousPeriod() {
        when (_state.value.selectedPeriod) {
            BillPeriod.WEEK -> {
                selectedWeekAnchor.update { it.minusWeeks(1) }
                selectedMonth.value = YearMonth.from(selectedWeekAnchor.value)
            }
            BillPeriod.YEAR -> selectedMonth.update { it.minusYears(1) }
            BillPeriod.MONTH -> selectedMonth.update { it.minusMonths(1) }
        }
        refresh()
    }

    fun selectNextPeriod() {
        when (_state.value.selectedPeriod) {
            BillPeriod.WEEK -> {
                selectedWeekAnchor.update { it.plusWeeks(1) }
                selectedMonth.value = YearMonth.from(selectedWeekAnchor.value)
            }
            BillPeriod.YEAR -> selectedMonth.update { it.plusYears(1) }
            BillPeriod.MONTH -> selectedMonth.update { it.plusMonths(1) }
        }
        refresh()
    }

    fun selectCurrentPeriod() {
        selectedMonth.value = YearMonth.now(clock)
        selectedWeekAnchor.value = todayWeekStart()
        refresh()
    }

    fun setPeriod(period: BillPeriod) {
        if (period == BillPeriod.WEEK) {
            selectedWeekAnchor.value = todayWeekStart()
        }
        _state.update {
            it.copy(
                selectedPeriod = period,
                selectedWeekAnchor = if (period == BillPeriod.WEEK) selectedWeekAnchor.value else null
            )
        }
        refresh()
    }

    private fun todayWeekStart(): LocalDate = LocalDate.now(clock)
        .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    fun setDirectionFilter(direction: Direction?) {
        _state.update { it.copy(directionFilter = direction) }
        refresh()
    }

    fun setCategoryFilter(category: Category?) {
        _state.update { it.copy(categoryFilter = category) }
        refresh()
    }

    fun setSearchText(text: String) {
        _state.update { it.copy(searchText = text) }
        refresh()
    }

    fun openImport() {
        _state.update { it.copy(importState = BillImportState(isOpen = true), errorMessage = null) }
    }

    fun selectFile(fileName: String, input: java.io.InputStream) {
        scope.launch {
            _state.update {
                it.copy(
                    importState = it.importState.copy(fileName = fileName, isParsing = true, errorMessage = null)
                )
            }
            try {
                val source = sourceFor(fileName)
                val parser: BillParser = if (fileName.lowercase().endsWith(".xls") || fileName.lowercase().endsWith(".xlsx")) {
                    ExcelBillParser()
                } else {
                    CsvBillParser()
                }
                val preview = repository.preview(parser.parse(input, source))
                _state.update {
                    it.copy(importState = it.importState.copy(preview = preview, isParsing = false))
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        importState = it.importState.copy(isParsing = false, errorMessage = error.message ?: "解析失败"),
                        errorMessage = error.message ?: "账单解析失败"
                    )
                }
            }
        }
    }

    fun togglePreviewRow(rowNumber: Int) {
        _state.update { state ->
            val preview = state.importState.preview ?: return@update state
            state.copy(
                importState = state.importState.copy(
                    preview = preview.copy(rows = preview.rows.map { row ->
                        if (row.rowNumber == rowNumber) row.copy(include = !row.include) else row
                    })
                )
            )
        }
    }

    fun cancelImport() {
        _state.update { it.copy(importState = BillImportState()) }
    }

    fun confirmImport() {
        val preview = _state.value.importState.preview ?: return
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val log = repository.confirmImport(preview, _state.value.importState.fileName ?: "账单导入")
                _state.update {
                    it.copy(
                        isLoading = false,
                        importState = BillImportState(),
                        statusMessage = "已导入 ${log.successRows} 条账单"
                    )
                }
                refreshStatistics()
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = error.message ?: "账单导入失败") }
            }
        }
    }

    fun openEditor(transaction: BillPreviewRow) {
        _state.update {
            it.copy(
                editorState = BillEditorState(
                    transaction = transaction,
                    amountText = "%.2f".format(transaction.amountCents / 100.0),
                    dateText = DATE_FORMATTER.withZone(clock.zone).format(java.time.Instant.ofEpochMilli(transaction.occurredAt)),
                    counterpartyText = transaction.counterparty,
                    category = transaction.category,
                    direction = transaction.direction,
                    notesText = transaction.notes.orEmpty()
                )
            )
        }
    }

    fun updateEditor(editor: BillEditorState) {
        _state.update { it.copy(editorState = editor) }
    }

    fun dismissEditor() {
        _state.update { it.copy(editorState = null) }
    }

    fun saveEditor() {
        val editor = _state.value.editorState ?: return
        val original = editor.transaction ?: return
        val amountCents = editor.amountText.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: return
        val date = runCatching { java.time.LocalDateTime.parse(editor.dateText, DATE_FORMATTER) }.getOrNull() ?: return
        scope.launch {
            try {
                repository.updateTransaction(
                    original.copy(
                        occurredAt = date.atZone(clock.zone).toInstant().toEpochMilli(),
                        amountCents = amountCents,
                        counterparty = editor.counterpartyText,
                        category = editor.category,
                        direction = editor.direction,
                        notes = editor.notesText.ifBlank { null }
                    )
                )
                _state.update { it.copy(editorState = null, statusMessage = "账单已更新") }
                refreshStatistics()
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = error.message ?: "保存账单失败") }
            }
        }
    }

    fun setBudget(cents: Long?) {
        scope.launch {
            try {
                repository.setBudget(selectedMonth.value, cents)
                repository.evaluateBudgetThreshold(selectedMonth.value)
                _state.update { it.copy(statusMessage = "预算已保存") }
                refreshStatistics()
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = error.message ?: "预算保存失败") }
            }
        }
    }

    private fun refresh() {
        _state.update { it.copy(selectedMonth = selectedMonth.value) }
        scope.launch { refreshStatistics() }
    }

    private suspend fun refreshStatistics() {
        val current = _state.value
        _state.update { it.copy(isLoading = true) }
        runCatching {
            repository.statistics(
                BillFilter(
                    period = current.selectedPeriod,
                    month = selectedMonth.value,
                    weekAnchor = if (current.selectedPeriod == BillPeriod.WEEK) {
                        selectedWeekAnchor.value
                    } else {
                        null
                    },
                    direction = current.directionFilter,
                    category = current.categoryFilter,
                    searchText = current.searchText
                )
            )
        }.onSuccess { statistics ->
            _state.update {
                it.copy(
                    selectedMonth = selectedMonth.value,
                    selectedWeekAnchor = if (it.selectedPeriod == BillPeriod.WEEK) selectedWeekAnchor.value else null,
                    statistics = statistics,
                    isLoading = false
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(isLoading = false, errorMessage = error.message ?: "账单统计失败") }
        }
    }

    private fun sourceFor(fileName: String): BillSource =
        if (fileName.contains("支付宝") || fileName.contains("alipay", ignoreCase = true)) {
            BillSource.ALIPAY
        } else {
            BillSource.WECHAT
        }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
