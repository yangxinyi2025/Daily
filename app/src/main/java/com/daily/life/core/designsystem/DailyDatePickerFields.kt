package com.daily.life.core.designsystem

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
fun DailyDatePickerField(
    value: String,
    label: String,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = runCatching { LocalDate.parse(value).format(dateFormatter) }.getOrDefault("请选择日期"),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    DatePickerDialog(context, { _, year, month, day ->
                        onDateSelected(LocalDate.of(year, month + 1, day))
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }
        )
    }
}

@Composable
fun DailyTimePickerField(
    value: String,
    label: String,
    onTimeSelected: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val time = runCatching { LocalTime.parse(value) }.getOrElse { LocalTime.now() }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = runCatching { LocalTime.parse(value).format(timeFormatter) }.getOrDefault("请选择时间"),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    TimePickerDialog(context, { _, hour, minute ->
                        onTimeSelected(LocalTime.of(hour, minute))
                    }, time.hour, time.minute, true).show()
                }
        )
    }
}

@Composable
fun DailyDateTimePickerField(
    value: String,
    label: String,
    onDateTimeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateTime = runCatching { LocalDateTime.parse(value, dateTimeFormatter) }
        .getOrElse { LocalDateTime.now().withSecond(0).withNano(0) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = dateTime.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    DatePickerDialog(context, { _, year, month, day ->
                        val selectedDate = LocalDate.of(year, month + 1, day)
                        TimePickerDialog(context, { _, hour, minute ->
                            onDateTimeSelected(
                                LocalDateTime.of(selectedDate, LocalTime.of(hour, minute))
                                    .format(dateTimeFormatter)
                            )
                        }, dateTime.hour, dateTime.minute, true).show()
                    }, dateTime.year, dateTime.monthValue - 1, dateTime.dayOfMonth).show()
                }
        )
    }
}
