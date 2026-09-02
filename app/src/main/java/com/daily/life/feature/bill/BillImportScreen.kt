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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.QuietSkyPageHeader
import com.daily.life.core.designsystem.SkyMutedText

@Composable
fun BillImportScreen(
    state: BillImportState,
    onChooseFile: () -> Unit,
    onToggleRow: (Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuietSkyPageHeader(
            title = "导入账单",
            subtitle = "从微信或支付宝账单中整理生活记录。"
        )

        if (state.preview == null) {
            DailyCard {
                Text("选择文件", style = MaterialTheme.typography.titleLarge)
                Text("支持 CSV / Excel 文件", color = SkyMutedText)
                state.fileName?.let { Text("已选择：$it", color = SkyMutedText) }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isParsing) "解析中…" else "选择文件")
                }
            }
        } else {
            DailyCard {
                Text("导入预览", style = MaterialTheme.typography.titleLarge)
                Text(
                    "识别 ${state.preview.rows.size} 条，跳过 ${state.preview.skippedRows} 条",
                    color = SkyMutedText
                )
                if (state.preview.duplicateCandidates.isNotEmpty()) {
                    Text(
                        "发现 ${state.preview.duplicateCandidates.size} 条重复候选，请逐条确认。",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.preview.rows, key = { it.rowNumber }) { row ->
                    DailyCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.include,
                                onCheckedChange = { onToggleRow(row.rowNumber) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${row.counterparty} · ${row.category.label}")
                                Text(
                                    "${row.direction.displayLabel()} · ${formatBillCents(row.amountCents)}",
                                    color = SkyMutedText
                                )
                                if (row.isDuplicateCandidate) {
                                    Text("重复候选", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                if (state.preview.skippedDetails.isNotEmpty()) {
                    item(key = "skipped_header") {
                        DailyCard {
                            Text("跳过明细（${state.preview.skippedDetails.size} 条）", style = MaterialTheme.typography.titleMedium)
                            Text("这些记录未写入账单，下面列出具体原因。", color = SkyMutedText)
                        }
                    }
                    items(
                        state.preview.skippedDetails,
                        key = { detail -> "skipped_${detail.rowNumber}_${detail.reason}" }
                    ) { detail ->
                        DailyCard {
                            Text("第 ${detail.rowNumber ?: "?"} 行：${detail.reason}", color = MaterialTheme.colorScheme.error)
                            if (detail.rawPreview.isNotBlank()) {
                                Text(detail.rawPreview, color = SkyMutedText)
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.testTag("bill_import_cancel")) {
                    Text("取消")
                }
                Button(onClick = onConfirm, modifier = Modifier.testTag("bill_import_confirm")) {
                    Text("确认写入")
                }
            }
        }
    }
}

private fun Direction.displayLabel(): String = when (this) {
    Direction.INCOME -> "收入"
    Direction.EXPENSE -> "支出"
}
