package com.daily.life.feature.timetable

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyEmptyState
import java.io.InputStream

@Composable
fun TimetableScreen(
    state: TimetableState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onOpenImport: () -> Unit,
    onPdfSelected: (String, InputStream) -> Unit,
    onSemesterInputChange: (String, String) -> Unit,
    onImportRowChange: (TimetableImportRowState) -> Unit,
    onReplaceExistingChange: (Boolean) -> Unit,
    onCancelImport: () -> Unit,
    onConfirmImport: () -> Unit
) {
    val context = LocalContext.current
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
            onReplaceExistingChange = onReplaceExistingChange,
            onCancel = onCancelImport,
            onConfirm = onConfirmImport
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "课表", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = state.currentSemesterName ?: "尚未导入学期课表",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onOpenImport, modifier = Modifier.testTag("timetable_open_import")) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("导入 PDF")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousWeek) {
                Icon(Icons.Default.ArrowBack, contentDescription = "上一周")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.weekLabel, style = MaterialTheme.typography.titleLarge)
                if (state.selectedWeek != state.currentWeek) {
                    Text(
                        "回到本周",
                        modifier = Modifier.clickable(onClick = onCurrentWeek),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onNextWeek) {
                Icon(Icons.Default.ArrowForward, contentDescription = "下一周")
            }
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
                modifier = Modifier.weight(1f)
            )
            if (state.isEmpty) {
                Text(
                    text = "本周没有课程",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeeklyTimetableGrid(
    days: List<TimetableDayColumnState>,
    periods: List<TimetablePeriodLabel>,
    modifier: Modifier = Modifier
) {
    var selectedCourse by remember { mutableStateOf<TimetableCourseUiState?>(null) }
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            Row {
                Spacer(Modifier.width(TIME_COLUMN_WIDTH))
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(DAY_COLUMN_WIDTH)
                            .height(HEADER_HEIGHT),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(day.label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row {
                Column(modifier = Modifier.width(TIME_COLUMN_WIDTH)) {
                    periods.forEach { period ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PERIOD_HEIGHT)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(period.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(DAY_COLUMN_WIDTH)
                            .height(PERIOD_HEIGHT * periods.size)
                    ) {
                        Column {
                            periods.forEach {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(PERIOD_HEIGHT)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                        day.courses.forEach { course ->
                            CourseGridCard(
                                course = course,
                                onClick = { selectedCourse = course }
                            )
                        }
                    }
                }
            }
        }
        selectedCourse?.let { course ->
            DailyCard(modifier = Modifier.padding(top = 8.dp)) {
                Text(course.courseName, style = MaterialTheme.typography.titleMedium)
                Text(course.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CourseGridCard(course: TimetableCourseUiState, onClick: () -> Unit) {
    val span = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
    Card(
        modifier = Modifier
            .padding(3.dp)
            .offset(y = PERIOD_HEIGHT * (course.startPeriod - 1))
            .fillMaxWidth()
            .height(PERIOD_HEIGHT * span - 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            course.location?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

private val TIME_COLUMN_WIDTH = 58.dp
private val DAY_COLUMN_WIDTH = 116.dp
private val HEADER_HEIGHT = 44.dp
private val PERIOD_HEIGHT = 68.dp
