package com.daily.life.feature.schedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daily.life.core.calendar.requiresCalendarPermission
import com.daily.life.core.calendar.SystemCalendarEventEditor
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyPageScaffold
import com.daily.life.core.notification.FullScreenAlarmPermissionAction
import com.daily.life.core.notification.fullScreenAlarmPermissionAction

@Composable
fun ScheduleScreen(
    state: ScheduleState,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onCreate: () -> Unit,
    onQuickCreate: (ScheduleQuickAction) -> Unit,
    onEdit: (ScheduleEvent) -> Unit,
    onDelete: (Long) -> Unit,
    onSaveEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onEditorChange: (ScheduleEditorState) -> Unit,
    onSaveCalendarDayOverride: (java.time.LocalDate, java.time.LocalDate, com.daily.life.core.calendar.CalendarDayKind, String?) -> Unit,
    onClearCalendarDayOverrides: (List<java.time.LocalDate>) -> Unit,
    onRefreshCalendarRules: () -> Unit,
    onCalendarEventEditorOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeVersion by remember { mutableIntStateOf(0) }
    var hasOpenedFullScreenAlarmSettings by remember(state.editor != null) { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeVersion += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var canReadSystemCalendar by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        canReadSystemCalendar = grants[Manifest.permission.READ_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!requiresCalendarPermission(
                readGranted = grants[Manifest.permission.READ_CALENDAR] == true,
                writeGranted = grants[Manifest.permission.WRITE_CALENDAR] == true
            )
        ) {
            onSaveEditor()
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canReadSystemCalendar) {
            calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
        }
    }
    LaunchedEffect(state.editor?.reminderMode, resumeVersion) {
        if (state.editor?.reminderMode != ReminderMode.ALARM) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return@LaunchedEffect
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    )
                }
                return@LaunchedEffect
            }
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (!hasOpenedFullScreenAlarmSettings &&
            fullScreenAlarmPermissionAction(
                apiLevel = Build.VERSION.SDK_INT,
                canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= 34) {
                    notificationManager?.canUseFullScreenIntent() == true
                } else {
                    true
                }
            ) == FullScreenAlarmPermissionAction.OPEN_SETTINGS
        ) {
            hasOpenedFullScreenAlarmSettings = true
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
        }
    }
    val saveEditorWithCalendarPermission = {
        // Daily alarms are registered through AlarmManager and must not be blocked
        // by the calendar write permission used for notification reminders.
        val requiresCalendar = state.editor?.reminderMode == ReminderMode.NOTIFICATION
        val needsPermission = requiresCalendar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            requiresCalendarPermission(
                readGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED,
                writeGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
            )
        if (needsPermission) {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        } else {
            onSaveEditor()
        }
    }
    LaunchedEffect(state.calendarEventIdToEdit) {
        state.calendarEventIdToEdit?.let { eventId ->
            runCatching {
                context.startActivity(SystemCalendarEventEditor.intentFor(eventId))
            }
            onCalendarEventEditorOpened()
        }
    }
    LaunchedEffect(resumeVersion) {
        onRefreshCalendarRules()
    }
    if (state.viewMode == ScheduleViewMode.MONTH) {
        ScheduleMonthScreen(
            state = state,
            onViewModeChange = onViewModeChange,
            onDateSelected = onDateSelected,
            onCreate = onCreate,
            onQuickCreate = onQuickCreate,
            onEdit = onEdit,
            onDelete = onDelete,
            onSaveEditor = saveEditorWithCalendarPermission,
            onDismissEditor = onDismissEditor,
            onEditorChange = onEditorChange,
            onSaveCalendarDayOverride = onSaveCalendarDayOverride,
            onClearCalendarDayOverrides = onClearCalendarDayOverrides,
            onRefreshCalendarRules = onRefreshCalendarRules
        )
        return
    }

    DailyPageScaffold(title = "日程") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleViewMode.values().forEach { mode ->
                        if (mode == state.viewMode) {
                            Button(onClick = { onViewModeChange(mode) }) { Text(mode.label()) }
                        } else {
                            OutlinedButton(onClick = { onViewModeChange(mode) }) { Text(mode.label()) }
                        }
                    }
                }
            }
            item {
                DailyCard {
                    Text(text = "日期：${state.selectedDate}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onDateSelected(state.selectedDate.minusDays(1)) }) {
                            Text("前一天")
                        }
                        OutlinedButton(onClick = { onDateSelected(java.time.LocalDate.now()) }) {
                            Text("今天")
                        }
                        OutlinedButton(onClick = { onDateSelected(state.selectedDate.plusDays(1)) }) {
                            Text("后一天")
                        }
                    }
                }
            }
            item {
                DailyCard {
                    Text(text = "快捷创建")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleQuickAction.values().forEach { action ->
                            OutlinedButton(onClick = { onQuickCreate(action) }) {
                                Text(action.title)
                            }
                        }
                    }
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                        Text("新建日程")
                    }
                }
            }
            item {
                Text(text = "时间线")
            }
            if (state.events.isEmpty()) {
                item {
                    DailyCard { Text("这段时间还没有日程") }
                }
            } else {
                items(state.events, key = ScheduleEvent::id) { event ->
                    DailyCard(
                        modifier = Modifier.clickable { onEdit(event) }
                    ) {
                        Text(text = event.title)
                        Text(text = event.eventAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString())
                        Text(
                            text = if (event.reminderMode == ReminderMode.ALARM) "闹钟提醒" else "消息提醒"
                        )
                        OutlinedButton(onClick = { onDelete(event.id) }) { Text("删除") }
                    }
                }
            }
            state.statusMessage?.let { message -> item { Text(message) } }
            state.editor?.let { editor ->
                item {
                    ScheduleEditorScreen(
                        state = editor,
                        onChange = onEditorChange,
                        onSave = saveEditorWithCalendarPermission,
                        onDismiss = onDismissEditor,
                        onDelete = editor.id?.let { id -> { onDelete(id); onDismissEditor() } }
                    )
                }
            }
        }
    }
}

private fun ScheduleViewMode.label(): String = when (this) {
    ScheduleViewMode.MONTH -> "月"
    ScheduleViewMode.WEEK -> "周"
    ScheduleViewMode.DAY -> "日"
}
