package com.daily.life.feature.schedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R
import com.daily.life.core.designsystem.DailyDatePickerField
import com.daily.life.core.designsystem.DailyTimePickerField

@Composable
fun ScheduleEditorScreen(
    state: ScheduleEditorState,
    onChange: (ScheduleEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    ScheduleReferenceCard {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EventNote, contentDescription = null, tint = SchedulePurple, modifier = Modifier.size(25.dp))
                Text(
                    if (state.id == null) "新建日程" else "编辑日程",
                    modifier = Modifier.padding(start = 9.dp),
                    color = ScheduleInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Image(
                painter = painterResource(R.drawable.schedule_note_illustration),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).width(108.dp).height(46.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.42f
            )
        }
        ScheduleOutlinedField(
            value = state.title,
            onValueChange = { onChange(state.copy(title = it)) },
            label = "标题"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DailyDatePickerField(
                value = state.date,
                label = "日期",
                onDateSelected = { onChange(state.copy(date = it.toString())) },
                modifier = Modifier.weight(1f)
            )
            DailyTimePickerField(
                value = state.time,
                label = "时间",
                onTimeSelected = { onChange(state.copy(time = it.toString())) },
                modifier = Modifier.weight(1f)
            )
        }
        ScheduleOutlinedField(
            value = state.reminderOffsetMinutes,
            onValueChange = { onChange(state.copy(reminderOffsetMinutes = it)) },
            label = "提前提醒（分钟）",
            leadingIcon = { Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = SchedulePurple, modifier = Modifier.size(20.dp)) }
        )
        Text("提醒方式", color = ScheduleInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.reminderMode == com.daily.life.core.database.ReminderMode.NOTIFICATION,
                    onClick = { onChange(state.copy(reminderMode = com.daily.life.core.database.ReminderMode.NOTIFICATION)) },
                    colors = RadioButtonDefaults.colors(selectedColor = SchedulePurple, unselectedColor = ScheduleInk.copy(alpha = 0.48f))
                )
                Text("消息提醒", color = ScheduleInk, fontSize = 15.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.reminderMode == com.daily.life.core.database.ReminderMode.ALARM,
                    onClick = { onChange(state.copy(reminderMode = com.daily.life.core.database.ReminderMode.ALARM)) },
                    colors = RadioButtonDefaults.colors(selectedColor = SchedulePurple, unselectedColor = ScheduleInk.copy(alpha = 0.48f))
                )
                Text("闹钟提醒", color = ScheduleInk, fontSize = 15.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.repeatYearly,
                onCheckedChange = { onChange(state.copy(repeatYearly = it)) },
                colors = CheckboxDefaults.colors(checkedColor = SchedulePurple, uncheckedColor = ScheduleInk.copy(alpha = 0.48f))
            )
            Text("每年重复", color = ScheduleInk, fontSize = 15.sp)
        }
        ScheduleOutlinedField(
            value = state.location,
            onValueChange = { onChange(state.copy(location = it)) },
            label = "地点（可选）",
            leadingIcon = { Icon(Icons.Outlined.EventNote, contentDescription = null, tint = SchedulePurple, modifier = Modifier.size(20.dp)) }
        )
        ScheduleOutlinedField(
            value = state.notes,
            onValueChange = { onChange(state.copy(notes = it)) },
            label = "消息/备注",
            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = SchedulePurple, modifier = Modifier.size(20.dp)) }
        )
        state.errorMessage?.let { Text(it, color = ScheduleOrange, fontSize = 13.sp) }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SchedulePurple, contentColor = Color.White)
        ) { Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, SchedulePurple.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SchedulePurple)
        ) { Text("取消", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        if (onDelete != null) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, ScheduleOrange.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ScheduleOrange)
            ) { Text("删除此日程", fontSize = 14.sp) }
        }
    }
}

@Composable
private fun ScheduleOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: (@Composable (() -> Unit))? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = ScheduleInk.copy(alpha = 0.58f), fontSize = 14.sp) },
        leadingIcon = leadingIcon,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = ScheduleInk),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SchedulePurple,
            unfocusedBorderColor = ScheduleGreen.copy(alpha = 0.72f),
            cursorColor = SchedulePurple,
            focusedContainerColor = ScheduleSurfaceCream,
            unfocusedContainerColor = ScheduleSurfaceCream
        )
    )
}
