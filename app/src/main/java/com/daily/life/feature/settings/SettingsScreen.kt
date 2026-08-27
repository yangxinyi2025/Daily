package com.daily.life.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyDatePickerField
import com.daily.life.core.system.BackgroundRuntimeSettingsAction
import com.daily.life.core.system.backgroundRuntimeSettingsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(
    state: SettingsState,
    onSemesterStartDateChange: (LocalDate) -> Unit,
    onTargetWeightChange: (Double?) -> Unit,
    onMonthlyBudgetChange: (Long?) -> Unit
) {
    var semesterStartDateText by rememberSaveable { mutableStateOf(state.semesterStartDate?.toString().orEmpty()) }
    var targetWeightText by rememberSaveable { mutableStateOf(state.targetWeightJin?.toString().orEmpty()) }
    var monthlyBudgetText by rememberSaveable { mutableStateOf(state.monthlyBudgetCents?.let(::formatBudgetInput).orEmpty()) }

    LaunchedEffect(state.semesterStartDate) {
        semesterStartDateText = state.semesterStartDate?.toString().orEmpty()
    }
    LaunchedEffect(state.targetWeightJin) {
        targetWeightText = state.targetWeightJin?.toString().orEmpty()
    }
    LaunchedEffect(state.monthlyBudgetCents) {
        monthlyBudgetText = state.monthlyBudgetCents?.let(::formatBudgetInput).orEmpty()
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
            DailyDatePickerField(
                value = semesterStartDateText,
                label = "学期开始日期",
                onDateSelected = { date ->
                    semesterStartDateText = date.toString()
                    onSemesterStartDateChange(date)
                }
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

        BackgroundRuntimeSettingsCard()

    }
}

@Composable
private fun BackgroundRuntimeSettingsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBackgroundRuntimeAllowed by rememberSaveable {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBackgroundRuntimeAllowed = context.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DailyCard {
        Text(text = "后台运行", style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (isBackgroundRuntimeAllowed) {
                "已允许忽略系统电池优化，闹钟可在息屏时正常运行。"
            } else {
                "为保证闹钟在息屏时响起，请在系统设置中为 Daily 允许后台高耗电或取消电池优化。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { context.openBackgroundRuntimeSettings() },
            modifier = Modifier.testTag("settings_background_runtime")
        ) {
            Text(if (isBackgroundRuntimeAllowed) "查看系统设置" else "去设置")
        }
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    return getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(packageName) == true
}

private fun Context.openBackgroundRuntimeSettings() {
    val packageUri = Uri.parse("package:$packageName")
    val primaryIntent = when (backgroundRuntimeSettingsAction(Build.VERSION.SDK_INT)) {
        BackgroundRuntimeSettingsAction.REQUEST_EXEMPTION -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri
        )
        BackgroundRuntimeSettingsAction.APP_DETAILS -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            packageUri
        )
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    runCatching { startActivity(primaryIntent) }
        .recoverCatching { startActivity(fallbackIntent) }
}

private fun parseBudgetInput(value: String): Long? =
    value.toDoubleOrNull()?.let { amount -> (amount * 100).roundToLong() }

private fun formatBudgetInput(value: Long): String = (value / 100.0).toString()

private fun formatCurrency(cents: Long): String =
    String.format(Locale.US, "¥%,.2f", cents / 100.0)
