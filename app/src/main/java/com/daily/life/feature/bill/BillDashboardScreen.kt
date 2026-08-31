package com.daily.life.feature.bill

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R
import com.daily.life.core.designsystem.QuietSkyListRow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val BillBackground = Color(0xFFF7F7FD)
private val BillInk = Color(0xFF10162A)
private val BillMuted = Color(0xFF7C8498)
private val BillPurple = Color(0xFF756CF6)
private val BillPurpleSurface = Color(0xFFF0EDFF)
private val BillGreen = Color(0xFF59B98B)
private val BillOrange = Color(0xFFFF9D78)
private val BillOutline = Color(0xFFECECF4)

@Composable
fun BillDashboardScreen(
    state: BillState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onCurrentPeriod: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onPeriodChange: (BillPeriod) -> Unit,
    onDirectionChange: (Direction?) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenNewEditor: () -> Unit,
    onOpenEditor: (BillPreviewRow) -> Unit
) {
    val scrollState = rememberScrollState()
    var periodMenuExpanded by remember { mutableStateOf(false) }
    val categoryItems = state.statistics.categoryItems()
    val presentation = billDashboardPresentation(state.statistics)
    val groupedTransactions = state.statistics.transactions
        .sortedByDescending { it.occurredAt }
        .groupBy { row ->
            Instant.ofEpochMilli(row.occurredAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BillBackground)
            .verticalScroll(scrollState)
            .padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BillHeader(onOpenImport = onOpenImport, onOpenNewEditor = onOpenNewEditor)
        BillPeriodSelector(
            state = state,
            expanded = periodMenuExpanded,
            onExpandedChange = { periodMenuExpanded = it },
            onPeriodChange = onPeriodChange,
            onPreviousPeriod = onPreviousPeriod,
            onNextPeriod = onNextPeriod,
            onCurrentPeriod = onCurrentPeriod,
            onSelectMonth = onSelectMonth
        )
        BillSummaryCard(statistics = state.statistics, countLabel = presentation.transactionCountLabel)
        BillFilterAndSearch(
            directionFilter = state.directionFilter,
            searchText = state.searchText,
            onDirectionChange = onDirectionChange,
            onSearchChange = onSearchChange
        )

        BillSectionTitle(title = "支出分类", trailing = state.periodLabel, showChevron = true)
        BillCategoryPanel(
            items = categoryItems,
            emptyLabel = presentation.categoryEmptyLabel
        )

        BillSectionTitle(title = "最近账单", trailing = presentation.transactionCountLabel)
        if (presentation.showEmptyRecentCard) {
            BillEmptyRecentCard(
                title = presentation.recentEmptyTitle,
                description = presentation.recentEmptyDescription
            )
        } else {
            BillTransactionPanel(groupedTransactions = groupedTransactions, onOpenEditor = onOpenEditor)
        }

        state.statusMessage?.let { message ->
            Text(message, color = BillGreen, fontSize = 13.sp)
        }
        state.errorMessage?.let { message ->
            Text(message, color = BillOrange, fontSize = 13.sp)
        }
        if (state.isLoading) {
            Text("正在更新账单…", color = BillMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BillHeader(onOpenImport: () -> Unit, onOpenNewEditor: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "账单",
                color = BillInk,
                fontSize = 34.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "把每一笔生活，都放在心上。",
                modifier = Modifier.padding(top = 3.dp),
                color = BillMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenImport, modifier = Modifier.padding(top = 7.dp)) {
            Icon(
                imageVector = Icons.Outlined.FileUpload,
                contentDescription = "导入账单",
                tint = BillInk,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onOpenNewEditor, modifier = Modifier.padding(top = 7.dp)) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "记一笔",
                tint = BillInk,
                modifier = Modifier.size(31.dp)
            )
        }
    }
}

@Composable
private fun BillPeriodSelector(
    state: BillState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPeriodChange: (BillPeriod) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onCurrentPeriod: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit
) {
    val context = LocalContext.current
    val isCurrentMonth = state.selectedMonth == YearMonth.now()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(state.selectedPeriod, state.selectedMonth, state.selectedWeekAnchor) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (abs(totalDrag) > 60f) {
                            if (totalDrag < 0f) onNextPeriod() else onPreviousPeriod()
                        }
                        totalDrag = 0f
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousPeriod, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = state.previousPeriodLabel,
                    tint = BillMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        DatePickerDialog(
                            context,
                            { _, year, month, _ -> onSelectMonth(YearMonth.of(year, month + 1)) },
                            state.selectedMonth.year,
                            state.selectedMonth.monthValue - 1,
                            1
                        ).show()
                    }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.periodLabel,
                    color = BillInk,
                    fontSize = 25.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onNextPeriod, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = state.nextPeriodLabel,
                    tint = BillMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box {
                IconButton(onClick = { onExpandedChange(true) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = "选择账单周期",
                        tint = BillPurple,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    BillPeriod.entries.filter { it != BillPeriod.WEEK }.forEach { period ->
                        DropdownMenuItem(
                            text = { Text("按${period.label}查看") },
                            onClick = {
                                onExpandedChange(false)
                                onPeriodChange(period)
                            }
                        )
                    }
                }
            }
        }
        if (!isCurrentMonth) {
            Text(
                text = "回到本月",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCurrentPeriod)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                color = BillPurple,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BillSummaryCard(statistics: BillStatistics, countLabel: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "本月支出",
                modifier = Modifier.padding(start = 22.dp, top = 24.dp),
                color = BillMuted,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 18.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF9F8FF)
            ) {
                Text(
                    text = "共 $countLabel",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = BillMuted,
                    fontSize = 15.sp,
                    lineHeight = 18.sp
                )
            }
            Text(
                text = formatBillCents(statistics.expenseCents),
                modifier = Modifier.padding(start = 22.dp, top = 70.dp),
                color = BillInk,
                fontSize = 36.sp,
                lineHeight = 43.sp,
                fontWeight = FontWeight.Medium
            )
            Image(
                painter = painterResource(R.drawable.bill_wallet_illustration),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 10.dp)
                    .width(108.dp)
                    .height(90.dp),
                contentScale = ContentScale.Fit
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 24.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BillMetric(label = "收入", value = formatBillCents(statistics.incomeCents), color = BillGreen)
                BillMetric(label = "结余", value = formatBillCents(statistics.balanceCents()), color = BillPurple)
                BillMetric(
                    label = "预算",
                    value = statistics.budgetCents?.let(::formatBillCents) ?: "未设置",
                    color = BillInk
                )
            }
        }
    }
}

@Composable
private fun BillMetric(label: String, value: String, color: Color) {
    Column(modifier = Modifier.width(78.dp)) {
        Text(text = label, color = BillMuted, fontSize = 14.sp, lineHeight = 19.sp)
        Text(
            text = value,
            modifier = Modifier.padding(top = 3.dp),
            color = color,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BillFilterAndSearch(
    directionFilter: Direction?,
    searchText: String,
    onDirectionChange: (Direction?) -> Unit,
    onSearchChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BillFilterPill(label = "全部", selected = directionFilter == null) { onDirectionChange(null) }
            BillFilterPill(label = "支出", selected = directionFilter == Direction.EXPENSE) {
                onDirectionChange(Direction.EXPENSE)
            }
            BillFilterPill(label = "收入", selected = directionFilter == Direction.INCOME) {
                onDirectionChange(Direction.INCOME)
            }
        }
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            placeholder = { Text("搜索账单", color = BillMuted, fontSize = 16.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "搜索账单",
                    tint = BillMuted,
                    modifier = Modifier.size(27.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BillPurple,
                unfocusedBorderColor = BillOutline,
                focusedTextColor = BillInk,
                unfocusedTextColor = BillInk,
                cursorColor = BillPurple
            )
        )
    }
}

@Composable
private fun BillFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        color = if (selected) BillPurple else Color.Transparent,
        shape = RoundedCornerShape(25.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, BillOutline)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp),
            color = if (selected) Color.White else BillMuted,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun BillSectionTitle(title: String, trailing: String, showChevron: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = BillInk,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = trailing, color = BillPurple, fontSize = 15.sp, lineHeight = 20.sp)
            if (showChevron) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = BillPurple,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun BillCategoryPanel(items: List<BillCategoryItem>, emptyLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        if (items.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = BillPurpleSurface,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PieChart,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = BillPurple
                    )
                }
                Text(
                    text = emptyLabel,
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                    color = BillInk,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "查看支出分类",
                    tint = BillPurple,
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEach { item -> BillCategoryRow(item) }
            }
        }
    }
}

@Composable
private fun BillEmptyRecentCard(title: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFCFB), Color(0xFFFFF4EF), Color(0xFFFFFBF9))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 22.dp, end = 146.dp)
            ) {
                Text(
                    text = title,
                    color = BillInk,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 10.dp),
                    color = BillMuted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            Image(
                painter = painterResource(R.drawable.bill_clipboard_illustration),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp, bottom = 0.dp)
                    .width(154.dp)
                    .height(150.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun BillTransactionPanel(
    groupedTransactions: Map<LocalDate, List<BillPreviewRow>>,
    onOpenEditor: (BillPreviewRow) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            groupedTransactions.entries.forEachIndexed { index, (date, rows) ->
                BillDateHeader(date = date, rows = rows)
                rows.forEach { row -> BillTransactionRow(row = row, onClick = { onOpenEditor(row) }) }
                if (index != groupedTransactions.size - 1) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BillCategoryRow(item: BillCategoryItem) {
    val color = categoryColor(item.category)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.category.label, modifier = Modifier.width(42.dp), color = BillInk, fontSize = 14.sp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(7.dp)
                    .background(BillOutline, RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.progress)
                        .height(7.dp)
                        .background(color, RoundedCornerShape(50))
                )
            }
            Text(
                text = formatBillCents(item.amountCents),
                modifier = Modifier.width(70.dp),
                color = BillMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun BillDateHeader(date: LocalDate, rows: List<BillPreviewRow>) {
    val expense = rows.filter { it.direction == Direction.EXPENSE }.sumOf { it.amountCents }
    val income = rows.filter { it.direction == Direction.INCOME }.sumOf { it.amountCents }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${date.monthValue}月${date.dayOfMonth}日 · ${date.dayOfWeek.chineseLabel()}",
            color = BillInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = if (income > 0L) "收入 ${formatBillCents(income)}" else "支出 ${formatBillCents(expense)}",
            color = BillMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun BillTransactionRow(row: BillPreviewRow, onClick: () -> Unit) {
    QuietSkyListRow(
        icon = Icons.Outlined.ReceiptLong,
        iconTint = categoryColor(row.category),
        title = row.counterparty,
        subtitle = "${row.category.label} · ${timeLabel(row.occurredAt)}",
        trailing = {
            Text(
                text = signedBillCents(row),
                color = if (row.direction == Direction.INCOME) BillGreen else BillInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        onClick = onClick,
        iconContentDescription = row.category.label
    )
}

private fun categoryColor(category: Category): Color = when (category) {
    Category.FOOD -> BillOrange
    Category.SHOPPING -> BillPurple
    Category.TRANSPORT -> Color(0xFF76B8C7)
    Category.INCOME -> BillGreen
    else -> BillPurple
}

private fun timeLabel(timestamp: Long): String = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

private fun java.time.DayOfWeek.chineseLabel(): String = when (this) {
    java.time.DayOfWeek.MONDAY -> "周一"
    java.time.DayOfWeek.TUESDAY -> "周二"
    java.time.DayOfWeek.WEDNESDAY -> "周三"
    java.time.DayOfWeek.THURSDAY -> "周四"
    java.time.DayOfWeek.FRIDAY -> "周五"
    java.time.DayOfWeek.SATURDAY -> "周六"
    java.time.DayOfWeek.SUNDAY -> "周日"
}

private fun signedBillCents(row: BillPreviewRow): String =
    (if (row.direction == Direction.INCOME) "+ " else "− ") + formatBillCents(row.amountCents)

internal fun formatBillCents(cents: Long): String = String.format(Locale.US, "¥%,.2f", cents / 100.0)
