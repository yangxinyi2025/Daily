package com.daily.life.feature.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.daily.life.core.calendar.requiresCalendarPermission
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyEmptyState
import java.io.InputStream
import java.time.format.DateTimeFormatter

internal val TimetablePageBackground = Color.White
internal val TimetableSurface = Color(0xFFF8F8F0)
internal val TimetableInk = Color(0xFF244C12)
internal val TimetableMuted = Color(0xFF74906C)
internal val TimetableGreen = Color(0xFFC7E99F)
internal val TimetablePurple = Color(0xFFC7AFEE)
internal val TimetableOrange = Color(0xFFFFCA79)

@Composable
fun TimetableScreen(
    state: TimetableState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenPeriodEditor: () -> Unit,
    onPdfSelected: (String, InputStream) -> Unit,
    onSemesterInputChange: (String, String) -> Unit,
    onImportRowChange: (TimetableImportRowState) -> Unit,
    onImportPeriodTimeChange: (TimetablePeriodTimeRowState) -> Unit,
    onReplaceExistingChange: (Boolean) -> Unit,
    onCancelImport: () -> Unit,
    onConfirmImport: () -> Unit
    ,onPeriodEditorRowChange: (TimetablePeriodTimeRowState) -> Unit
    ,onRestorePeriodDefaults: () -> Unit
    ,onSavePeriodTimes: () -> Unit
    ,onClosePeriodEditor: () -> Unit
    ,onMakeupSourceChange: (java.time.LocalDate, Int?) -> Unit
    ,onMakeupParityChange: (java.time.LocalDate, WeekParity?) -> Unit
    ,onRefreshSystemCalendarDays: () -> Unit
    ,onClassOverrideChange: (java.time.LocalDate, ClassOverride) -> Unit = { _, _ -> }
    ,onNewCourse: (Int, Int) -> Unit = { _, _ -> }
    ,onEditCourse: (Long) -> Unit = { }
    ,onCourseDraftChange: (TimetableCourseDraft) -> Unit = { }
    ,onSaveCourse: () -> Unit = { }
    ,onDismissCourseEditor: () -> Unit = { }
    ,onRequestCourseDelete: () -> Unit = { }
    ,onCancelCourseDelete: () -> Unit = { }
    ,onConfirmCourseDelete: () -> Unit = { }
    ,onRetryCourseReminderSync: () -> Unit = { }
    ,onDismissCourseSyncNotice: () -> Unit = { }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readCalendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onRefreshSystemCalendarDays()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALENDAR
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                readCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            } else {
                onRefreshSystemCalendarDays()
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onRefreshSystemCalendarDays()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (!requiresCalendarPermission(
                readGranted = grants[Manifest.permission.READ_CALENDAR] == true,
                writeGranted = grants[Manifest.permission.WRITE_CALENDAR] == true
            )
        ) {
            onConfirmImport()
        }
    }
    val confirmImportWithCalendarPermission = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
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
            onConfirmImport()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: uri.lastPathSegment
            ?: "timetable.pdf"
        context.contentResolver.openInputStream(uri)?.let { input -> onPdfSelected(name, input) }
    }

    if (state.importState.isOpen) {
        TimetableImportScreen(
            state = state.importState,
            onChooseFile = { picker.launch(arrayOf("application/pdf")) },
            onSemesterInputChange = onSemesterInputChange,
            onRowChange = onImportRowChange,
            onPeriodTimeChange = onImportPeriodTimeChange,
            onReplaceExistingChange = onReplaceExistingChange,
            onCancel = onCancelImport,
            onConfirm = confirmImportWithCalendarPermission,
            onMakeupSourceChange = onMakeupSourceChange,
            onMakeupParityChange = onMakeupParityChange,
            onClassOverrideChange = onClassOverrideChange
        )
        return
    }
    if (state.periodEditor.isOpen) {
        TimetablePeriodEditorScreen(state.periodEditor, onPeriodEditorRowChange, onRestorePeriodDefaults, onSavePeriodTimes, onClosePeriodEditor)
        return
    }

    state.courseEditor.takeIf { it.isOpen }?.let { editor ->
        TimetableCourseEditorDialog(
            editor = editor,
            onDraftChange = onCourseDraftChange,
            onSave = onSaveCourse,
            onDismiss = onDismissCourseEditor,
            onRequestDelete = onRequestCourseDelete
        )
    }
    state.courseEditor.deleteConfirmationCourseName?.let { courseName ->
        AlertDialog(
            onDismissRequest = onCancelCourseDelete,
            title = { Text("删除课程") },
            text = { Text("确定删除“$courseName”？这不会影响其他课程。") },
            confirmButton = {
                TextButton(onClick = onConfirmCourseDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelCourseDelete) { Text("取消") }
            }
        )
    }
    state.courseEditor.syncNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = onDismissCourseSyncNotice,
            title = { Text("提醒同步") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(
                    enabled = !state.courseEditor.isSaving,
                    onClick = onRetryCourseReminderSync
                ) { Text(if (state.courseEditor.isSaving) "正在同步" else "重试同步") }
            },
            dismissButton = {
                TextButton(onClick = onDismissCourseSyncNotice) { Text("知道了") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimetablePageBackground),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "课表",
                style = MaterialTheme.typography.titleLarge,
                color = TimetableInk,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = state.weekLabel,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable(onClick = onCurrentWeek),
                style = MaterialTheme.typography.titleLarge,
                color = TimetablePurple
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPreviousWeek, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "上一周")
            }
            IconButton(onClick = onNextWeek, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowForward, contentDescription = "下一周")
            }
            IconButton(
                onClick = onOpenPeriodEditor,
                enabled = state.currentSemesterName != null,
                modifier = Modifier.size(36.dp)
            ) { Text("时") }
            IconButton(
                onClick = onOpenImport,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("timetable_open_import")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "导入课表")
            }
        }
        Text(
            text = state.currentSemesterName ?: "导入课表后，在这里查看完整周课表",
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TimetableMuted
        )
        state.calendarAdjustmentWarning?.let { warning ->
            Text(
                text = warning,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (state.currentSemesterName == null) {
            DailyEmptyState(
                title = "还没有课表",
                message = "导入 PDF，检查预览后再确认写入。",
                actionLabel = "导入第一份课表",
                onActionClick = onOpenImport
            )
        } else {
            WeeklyTimetableGrid(
                days = state.days,
                periods = state.timeLabels,
                isEmpty = state.isEmpty,
                onNewCourse = onNewCourse,
                onEditCourse = onEditCourse,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimetableCourseEditorDialog(
    editor: TimetableCourseEditorState,
    onDraftChange: (TimetableCourseDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val draft = editor.draft ?: return
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onDismiss() },
        title = { Text(if (draft.id == null) "添加课程" else "编辑课程") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.courseName,
                    onValueChange = { onDraftChange(draft.copy(courseName = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("课程名称") },
                    singleLine = true
                )
                CourseNumberField("星期（1=周一，7=周日）", draft.dayOfWeek) {
                    onDraftChange(draft.copy(dayOfWeek = it))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CourseNumberField("开始节次", draft.startPeriod) {
                            onDraftChange(draft.copy(startPeriod = it))
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CourseNumberField("结束节次", draft.endPeriod) {
                            onDraftChange(draft.copy(endPeriod = it))
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.weekRuleText,
                    onValueChange = { onDraftChange(draft.copy(weekRuleText = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("周次规则，例如 1-16周 单周") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.location,
                    onValueChange = { onDraftChange(draft.copy(location = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("上课地点（可选）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.teacher,
                    onValueChange = { onDraftChange(draft.copy(teacher = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("教师（可选）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { onDraftChange(draft.copy(notes = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    minLines = 2,
                    maxLines = 3
                )
                editor.fieldError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = !editor.isSaving, onClick = onSave) {
                Text(if (editor.isSaving) "正在保存" else "保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (draft.id != null) {
                    TextButton(enabled = !editor.isSaving, onClick = onRequestDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(enabled = !editor.isSaving, onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun CourseNumberField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onValueChange(it.toIntOrNull() ?: 0) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
private fun WeeklyTimetableGrid(
    days: List<TimetableDayColumnState>,
    periods: List<TimetablePeriodLabel>,
    isEmpty: Boolean,
    onNewCourse: (Int, Int) -> Unit,
    onEditCourse: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TimetableSurface)
            .testTag("timetable_grid")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .border(0.5.dp, TimetableGreen.copy(alpha = 0.78f))
        ) {
            Box(
                modifier = Modifier.width(TIME_COLUMN_WIDTH),
                contentAlignment = Alignment.Center
            ) {
                Text("节次", style = MaterialTheme.typography.labelSmall, color = SkyMutedLabel)
            }
            days.forEach { day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TimetableInk
                        )
                        day.date?.let { date ->
                            Text(
                                text = date.format(TIMETABLE_DATE_FORMATTER),
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyMutedLabel
                            )
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.width(TIME_COLUMN_WIDTH)) {
                    periods.forEach { period ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PERIOD_HEIGHT),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                                color = SkyMutedLabel
                            )
                        }
                    }
                }
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(PERIOD_HEIGHT * periods.size)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            periods.forEach { period ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(PERIOD_HEIGHT)
                                        .clickable { onNewCourse(day.dayOfWeek, period.period) }
                                )
                            }
                        }
                        day.courses.forEach { course ->
                            CourseGridCard(
                                course = course,
                                onClick = { course.id.toLongOrNull()?.let(onEditCourse) }
                            )
                        }
                    }
                }
            }
            if (isEmpty) {
                Text(
                    text = "本周没有课程",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CourseGridCard(course: TimetableCourseUiState, onClick: () -> Unit) {
    val span = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
    val palette = TIMETABLE_COURSE_COLORS[timetableCourseColorSlot(course.dayOfWeek)]
    val titleStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    val metadataStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold
    )
    val metadata = course.location.orEmpty()
    Card(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .offset(y = PERIOD_HEIGHT * (course.startPeriod - 1))
            .fillMaxWidth()
        .height(PERIOD_HEIGHT * span - 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(palette.accent.copy(alpha = 0.48f))
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = course.courseName,
                modifier = Modifier.padding(horizontal = 5.dp),
                style = titleStyle,
                color = palette.accent,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata,
                    modifier = Modifier.padding(horizontal = 5.dp),
                    style = metadataStyle,
                    color = palette.accent,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun timetableCourseColorSlot(dayOfWeek: Int): Int = ((dayOfWeek - 1) % 4 + 4) % 4

private data class TimetableCoursePalette(val background: Color, val accent: Color)

private val TIMETABLE_COURSE_COLORS = listOf(
    TimetableCoursePalette(Color(0xFFF0EAFE), Color(0xFF8970BD)),
    TimetableCoursePalette(Color(0xFFEAF6DE), Color(0xFF56833C)),
    TimetableCoursePalette(Color(0xFFFFF0DE), Color(0xFFC47B3D)),
    TimetableCoursePalette(Color(0xFFE7F4F0), Color(0xFF4B8B81))
)

private val SkyMutedLabel = TimetableMuted.copy(alpha = 0.78f)
private val TIME_COLUMN_WIDTH = 56.dp
private val HEADER_HEIGHT = 52.dp
private val PERIOD_HEIGHT = 76.dp
private val TIMETABLE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
