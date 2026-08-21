package com.daily.life.feature.bill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard

@Composable
fun BillImportScreen(
    state: BillImportState,
    onChooseFile: () -> Unit,
    onToggleRow: (Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("预览账单导入", style = MaterialTheme.typography.headlineSmall)
        state.fileName?.let { Text("文件：$it") }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.preview == null) {
            Button(onClick = onChooseFile) { Text(if (state.isParsing) "解析中…" else "选择 CSV / Excel") }
        } else {
            Text("识别 ${state.preview.rows.size} 条，跳过 ${state.preview.skippedRows} 条")
            if (state.preview.duplicateCandidates.isNotEmpty()) {
                Text(
                    "发现 ${state.preview.duplicateCandidates.size} 条重复候选，请逐条确认是否导入",
                    color = MaterialTheme.colorScheme.error
                )
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.preview.rows, key = { it.rowNumber }) { row ->
                    DailyCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = row.include, onCheckedChange = { onToggleRow(row.rowNumber) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${row.counterparty} · ${row.category.label}")
                                Text("${row.direction.displayLabel()} · ${row.amountCents / 100.0} 元")
                                if (row.isDuplicateCandidate) {
                                    Text("重复候选", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCancel, modifier = Modifier.testTag("bill_import_cancel")) { Text("取消") }
                Button(onClick = onConfirm, modifier = Modifier.testTag("bill_import_confirm")) { Text("确认写入") }
            }
        }
    }
}

private fun Direction.displayLabel(): String = when (this) {
    Direction.INCOME -> "收入"
    Direction.EXPENSE -> "支出"
}
