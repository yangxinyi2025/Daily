package com.daily.life.feature.bill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard

@Composable
fun BillEditorScreen(
    state: BillEditorState,
    onChange: (BillEditorState) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    DailyCard(modifier = Modifier.padding(20.dp)) {
        Text("编辑账单", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.dateText,
            onValueChange = { onChange(state.copy(dateText = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("日期时间") }
        )
        OutlinedTextField(
            value = state.amountText,
            onValueChange = { onChange(state.copy(amountText = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("金额（元）") }
        )
        OutlinedTextField(
            value = state.counterpartyText,
            onValueChange = { onChange(state.copy(counterpartyText = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("交易对方") }
        )
        Text("分类：${state.category.label}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCancel) { Text("取消") }
            Button(onClick = onSave) { Text("保存") }
        }
    }
}
