package com.daily.life.feature.bill

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard
import java.time.YearMonth
import java.util.Locale

@Composable
fun BillScreen(
    state: BillState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onCurrentPeriod: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onPeriodChange: (BillPeriod) -> Unit,
    onDirectionChange: (Direction?) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenNewEditor: () -> Unit,
    onFileSelected: (String, java.io.InputStream) -> Unit,
    onTogglePreviewRow: (Int) -> Unit,
    onCancelImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onOpenEditor: (BillPreviewRow) -> Unit,
    onEditorChange: (BillEditorState) -> Unit,
    onCancelEditor: () -> Unit,
    onSaveEditor: () -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
            ?: "bill.csv"
        context.contentResolver.openInputStream(uri)?.let { input -> onFileSelected(fileName, input) }
    }

    if (state.importState.isOpen) {
        BillImportScreen(
            state = state.importState,
            onChooseFile = { picker.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
            onToggleRow = onTogglePreviewRow,
            onCancel = onCancelImport,
            onConfirm = onConfirmImport
        )
        return
    }
    if (state.editorState != null) {
        BillEditorScreen(
            state = state.editorState,
            onChange = onEditorChange,
            onCancel = onCancelEditor,
            onSave = onSaveEditor
        )
        return
    }

    BillDashboardScreen(
        state = state,
        onPreviousPeriod = onPreviousPeriod,
        onNextPeriod = onNextPeriod,
        onCurrentPeriod = onCurrentPeriod,
        onSelectMonth = onSelectMonth,
        onPeriodChange = onPeriodChange,
        onDirectionChange = onDirectionChange,
        onSearchChange = onSearchChange,
        onOpenImport = onOpenImport,
        onOpenNewEditor = onOpenNewEditor,
        onOpenEditor = onOpenEditor
    )
    return

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("账单", style = MaterialTheme.typography.headlineLarge)
                Text("${state.selectedMonth} · ${state.statistics.count} 笔")
            }
            Button(onClick = onOpenImport, modifier = Modifier.testTag("bill_open_import")) { Text("导入账单") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BillPeriod.entries.forEach { period ->
                FilterChip(
                    selected = state.selectedPeriod == period,
                    onClick = { onPeriodChange(period) },
                    label = { Text(period.label) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onPreviousPeriod) { Text("上一期") }
            Button(onClick = onCurrentPeriod) { Text("本期") }
            Button(onClick = onNextPeriod) { Text("下一期") }
        }
        OutlinedTextField(
            value = state.searchText,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索交易对方或备注") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.directionFilter == null, onClick = { onDirectionChange(null) }, label = { Text("全部") })
            FilterChip(selected = state.directionFilter == Direction.EXPENSE, onClick = { onDirectionChange(Direction.EXPENSE) }, label = { Text("支出") })
            FilterChip(selected = state.directionFilter == Direction.INCOME, onClick = { onDirectionChange(Direction.INCOME) }, label = { Text("收入") })
        }
        DailyCard {
            Text("总支出：${formatCents(state.statistics.expenseCents)}")
            Text("总收入：${formatCents(state.statistics.incomeCents)}")
            Text("预算：${state.statistics.budgetCents?.let(::formatCents) ?: "未设置"}")
            state.statistics.budgetProgressPercent?.let { Text("预算进度：$it%") }
        }
        if (state.statistics.transactions.isEmpty()) {
            Text("当前范围暂无账单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.statistics.transactions, key = { it.rowNumber }) { row ->
                    DailyCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(row.counterparty, style = MaterialTheme.typography.titleMedium)
                                Text("${row.category.label} · ${row.source.label}")
                            }
                            Text(
                                text = (if (row.direction == Direction.EXPENSE) "-" else "+") + formatCents(row.amountCents),
                                color = if (row.direction == Direction.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(onClick = { onOpenEditor(row) }) { Text("编辑") }
                    }
                }
            }
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

private fun formatCents(cents: Long): String = String.format(Locale.US, "¥%,.2f", cents / 100.0)
