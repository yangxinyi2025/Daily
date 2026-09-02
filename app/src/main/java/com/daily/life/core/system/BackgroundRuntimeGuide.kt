package com.daily.life.core.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

internal fun appDetailsSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

internal fun Context.openAppDetailsSettings() {
    startActivity(appDetailsSettingsIntent(packageName))
}

@Composable
internal fun BackgroundRuntimeGuideDialog(
    onOpenSettings: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("让闹钟按时提醒") },
        text = {
            Text(
                "请在系统应用信息页依次点击：\n" +
                    "1. 电池\n" +
                    "2. 后台耗电管理\n" +
                    "3. 允许后台耗电\n\n" +
                    "不同手机的名称可能略有不同。"
            )
        },
        confirmButton = {
            Button(onClick = onOpenSettings) { Text("去系统设置") }
        },
        dismissButton = {
            OutlinedButton(onClick = onLater) { Text("稍后处理") }
        }
    )
}
