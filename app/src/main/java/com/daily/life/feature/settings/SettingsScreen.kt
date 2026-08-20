package com.daily.life.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(
    state: SettingsState,
    onSemesterStartDateChange: (LocalDate) -> Unit,
    onTargetWeightChange: (Double?) -> Unit,
    onMonthlyBudgetChange: (Long?) -> Unit,
    onSaveDeepSeekKey: (String) -> Unit,
    onSaveWebDavConfig: (WebDavConfigInput) -> Unit
) {
    var semesterStartDateText by rememberSaveable { mutableStateOf(state.semesterStartDate?.toString().orEmpty()) }
    var targetWeightText by rememberSaveable { mutableStateOf(state.targetWeightJin?.toString().orEmpty()) }
    var monthlyBudgetText by rememberSaveable { mutableStateOf(state.monthlyBudgetCents?.let(::formatBudgetInput).orEmpty()) }
    var deepSeekKeyText by rememberSaveable { mutableStateOf("") }
    var webDavEndpointText by rememberSaveable { mutableStateOf(state.webDavEndpoint.orEmpty()) }
    var webDavUsernameText by rememberSaveable { mutableStateOf("") }
    var webDavPasswordText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.semesterStartDate) {
        semesterStartDateText = state.semesterStartDate?.toString().orEmpty()
    }
    LaunchedEffect(state.targetWeightJin) {
        targetWeightText = state.targetWeightJin?.toString().orEmpty()
    }
    LaunchedEffect(state.monthlyBudgetCents) {
        monthlyBudgetText = state.monthlyBudgetCents?.let(::formatBudgetInput).orEmpty()
    }
    LaunchedEffect(state.webDavEndpoint) {
        webDavEndpointText = state.webDavEndpoint.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "设置", style = MaterialTheme.typography.headlineLarge)
        state.saveStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        DailyCard {
            Text(text = "学期", style = MaterialTheme.typography.titleLarge)
            Text(
                text = state.currentSemesterName ?: "尚未选择当前学期",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                value = semesterStartDateText,
                onValueChange = { value ->
                    semesterStartDateText = value
                    parseLocalDateOrNull(value)?.let(onSemesterStartDateChange)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("学期开始日期") },
                supportingText = { Text("格式：YYYY-MM-DD") }
            )
        }

        DailyCard {
            Text(text = "目标", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = targetWeightText,
                onValueChange = { value ->
                    targetWeightText = value
                    onTargetWeightChange(value.toDoubleOrNull())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_target_weight"),
                label = { Text("目标体重（斤）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Text(
                text = state.targetWeightJin?.let { "当前目标：$it 斤" } ?: "还没有设置目标体重",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DailyCard {
            Text(text = "预算", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = monthlyBudgetText,
                onValueChange = { value ->
                    monthlyBudgetText = value
                    onMonthlyBudgetChange(parseBudgetInput(value))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("月预算（元）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Text(
                text = state.monthlyBudgetCents?.let { "当前预算：${formatCurrency(it)}" } ?: "还没有设置默认预算",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DailyCard {
            Text(text = "DeepSeek", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "保存状态：${state.deepSeekKeySummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = deepSeekKeyText,
                onValueChange = { deepSeekKeyText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DeepSeek Key") },
                visualTransformation = PasswordVisualTransformation()
            )
            Button(onClick = {
                onSaveDeepSeekKey(deepSeekKeyText)
                deepSeekKeyText = ""
            }) {
                Text("保存 DeepSeek Key")
            }
        }

        DailyCard {
            Text(text = "WebDAV", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "同步状态：${state.syncStatus}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "用户名：${state.webDavUsernameSummary}  密码：${state.webDavPasswordSummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = webDavEndpointText,
                onValueChange = { webDavEndpointText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 地址") }
            )
            OutlinedTextField(
                value = webDavUsernameText,
                onValueChange = { webDavUsernameText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 用户名") }
            )
            OutlinedTextField(
                value = webDavPasswordText,
                onValueChange = { webDavPasswordText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 密码") },
                visualTransformation = PasswordVisualTransformation()
            )
            Button(onClick = {
                onSaveWebDavConfig(
                    WebDavConfigInput(
                        endpoint = webDavEndpointText,
                        username = webDavUsernameText,
                        password = webDavPasswordText
                    )
                )
                webDavUsernameText = ""
                webDavPasswordText = ""
            }) {
                Text("保存 WebDAV 配置")
            }
        }

        DailyCard {
            Text(text = "权限与可用性", style = MaterialTheme.typography.titleLarge)
            Text(text = "通知：${state.notificationPermissionSummary}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "精确提醒：${state.exactAlarmPermissionSummary}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "健康权限：${state.healthPermissionSummary}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun parseLocalDateOrNull(value: String): LocalDate? =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

private fun parseBudgetInput(value: String): Long? =
    value.toDoubleOrNull()?.let { amount -> (amount * 100).roundToLong() }

private fun formatBudgetInput(value: Long): String = (value / 100.0).toString()

private fun formatCurrency(cents: Long): String =
    String.format(Locale.US, "¥%,.2f", cents / 100.0)
