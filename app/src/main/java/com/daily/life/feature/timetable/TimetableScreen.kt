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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.daily.life.core.designsystem.SkyAccent
import com.daily.life.core.designsystem.SkyCoolBorder
import com.daily.life.core.designsystem.SkyInk
import com.daily.life.core.designsystem.SkyPrimary
import com.daily.life.core.designsystem.SkySecondary
import com.daily.life.core.designsystem.SkySuccess
import com.daily.life.core.designsystem.SkySurface
import com.daily.life.core.designsystem.SkyWarm
import java.io.InputStream
import java.time.format.DateTimeFormatter

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
    ,onRefreshSystemCalendarDays: () -> Unit
    ,onClassOverrideChange: (java.time.LocalDate, ClassOverride) -> Unit = { _, _ -> }
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
            onClassOverrideChange = onClassOverrideChange
        )
        return
    }
    if (state.periodEditor.isOpen) {
        TimetablePeriodEditorScreen(state.periodEditor, onPeriodEditorRowChange, onRestorePeriodDefaults, onSavePeriodTimes, onClosePeriodEditor)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "课表", style = MaterialTheme.typography.titleLarge)
            Text(
                text = state.weekLabel,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable(onClick = onCurrentWeek),
                style = MaterialTheme.typography.titleLarge,
                color = SkyPrimary
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeeklyTimetableGrid(
    days: List<TimetableDayColumnState>,
    periods: List<TimetablePeriodLabel>,
    isEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedCourse by remember { mutableStateOf<TimetableCourseUiState?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SkySurface)
            .testTag("timetable_grid")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .border(0.5.dp, SkyCoolBorder.copy(alpha = 0.78f))
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
                            color = SkyInk
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
                        day.courses.forEach { course ->
                            CourseGridCard(
                                course = course,
                                onClick = { selectedCourse = course }
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
        selectedCourse?.let { course ->
            DailyCard(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(course.courseName, style = MaterialTheme.typography.titleMedium)
                Text(course.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CourseGridCard(course: TimetableCourseUiState, onClick: () -> Unit) {
    val span = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
    val accent = TIMETABLE_COURSE_COLORS[timetableCourseColorSlot(course.dayOfWeek)]
    val courseTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium
    )
    val metadata = listOfNotNull(course.location, course.teacher).joinToString("\n")
    Card(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .offset(y = PERIOD_HEIGHT * (course.startPeriod - 1))
            .fillMaxWidth()
            .height(PERIOD_HEIGHT * span - 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.16f))
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
                    .background(accent.copy(alpha = 0.48f))
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = course.courseName,
                modifier = Modifier.padding(horizontal = 5.dp),
                style = courseTextStyle,
                color = accent,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata,
                    modifier = Modifier.padding(horizontal = 5.dp),
                    style = courseTextStyle,
                    color = accent,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun timetableCourseColorSlot(dayOfWeek: Int): Int = ((dayOfWeek - 1) % 5 + 5) % 5

private val TIMETABLE_COURSE_COLORS = listOf(
    SkyPrimary,
    SkyAccent,
    SkySuccess,
    SkyWarm,
    SkySecondary
)

private val SkyMutedLabel = SkyInk.copy(alpha = 0.54f)
private val TIME_COLUMN_WIDTH = 56.dp
private val HEADER_HEIGHT = 52.dp
private val PERIOD_HEIGHT = 76.dp
private val TIMETABLE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
