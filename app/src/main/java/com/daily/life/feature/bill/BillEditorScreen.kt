package com.daily.life.feature.bill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyDateTimePickerField
import com.daily.life.core.designsystem.QuietSkyPageHeader
import com.daily.life.core.designsystem.SkyMutedText
import com.daily.life.core.designsystem.SkyPrimary

@Composable
fun BillEditorScreen(
    state: BillEditorState,
    onChange: (BillEditorState) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val isNew = state.transaction == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuietSkyPageHeader(
            title = if (isNew) "记一笔" else "编辑账单",
            subtitle = if (isNew) "把刚刚发生的生活记下来。" else "可以随时修正这笔记录。"
        )

        DailyCard {
            DailyDateTimePickerField(
                value = state.dateText,
                onDateTimeSelected = { onChange(state.copy(dateText = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = "日期时间"
            )
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { onChange(state.copy(amountText = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("金额（元）") }
            )
            OutlinedTextField(
                value = state.counterpartyText,
                onValueChange = { onChange(state.copy(counterpartyText = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("交易对方") }
            )
            OutlinedTextField(
                value = state.notesText,
                onValueChange = { onChange(state.copy(notesText = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("备注（可选）") }
            )

            Text("类型", style = MaterialTheme.typography.labelLarge, color = SkyMutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.direction == Direction.EXPENSE,
                    onClick = { onChange(state.copy(direction = Direction.EXPENSE)) },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = state.direction == Direction.INCOME,
                    onClick = { onChange(state.copy(direction = Direction.INCOME)) },
                    label = { Text("收入") }
                )
            }

            Text("分类", style = MaterialTheme.typography.labelLarge, color = SkyMutedText)
            Column {
                TextButton(onClick = { categoryMenuExpanded = true }) {
                    Text(state.category.label, color = SkyPrimary)
                }
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.label) },
                            onClick = {
                                categoryMenuExpanded = false
                                onChange(state.copy(category = category))
                            }
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave) { Text(if (isNew) "记下" else "保存") }
        }
    }
}
