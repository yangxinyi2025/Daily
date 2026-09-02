package com.daily.life.feature.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R

private val HomeInk = Color(0xFF244C12)
private val HomeMuted = Color(0xFF74906C)
private val HomePurple = Color(0xFFC7AFEE)
private val HomeTodoPill = Color(0xFFC7E99F)
private val HomeBackground = Color(0xFFFDFDF7)
private val HomeSurface = Color(0xFFF8F8F0)
private val HomeOrange = Color(0xFFFFCA79)
private val HomeTimelineLine = Color(0xFFE5EBD9)

@Composable
fun HomeScreen(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onDestinationSelected: (com.daily.life.core.navigation.DailyDestination) -> Unit
) {
    var section by rememberSaveable { mutableStateOf(HomeSection.COURSE) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 20.dp, end = 12.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeHeader(onOpenSettings = onOpenSettings)
        HomeSectionTabs(section = section, onSelect = { section = it })
        when (section) {
            HomeSection.COURSE -> HomeCoursePanel(state)
            HomeSection.CALENDAR -> HomeSchedulePanel(state)
            HomeSection.HEALTH -> HomeHealthPanel(state)
        }
    }
}

@Composable
private fun HomeSectionTabs(section: HomeSection, onSelect: (HomeSection) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        HomeSection.values().forEach { item ->
            val label = when (item) { HomeSection.COURSE -> "课程"; HomeSection.CALENDAR -> "日历"; HomeSection.HEALTH -> "健康" }
            val mascot = when (item) { HomeSection.COURSE -> R.drawable.home_course_sheep_reading; HomeSection.CALENDAR -> R.drawable.home_calendar_sheep; HomeSection.HEALTH -> R.drawable.home_health_sheep }
            val color = when (item) { HomeSection.COURSE -> HomePurple; HomeSection.CALENDAR -> HomeTodoPill; HomeSection.HEALTH -> HomeOrange }
            Surface(
                modifier = Modifier.weight(1f).height(185.dp).clickable { onSelect(item) },
                shape = RoundedCornerShape(22.dp),
                color = if (section == item) color else color.copy(alpha = .64f)
            ) {
                Column(
                    modifier = Modifier.padding(top = 13.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(shape = RoundedCornerShape(9.dp), color = Color.White.copy(alpha = .72f)) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            color = HomeInk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Image(
                        painter = painterResource(mascot),
                        contentDescription = "${label}入口",
                        modifier = Modifier.size(100.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCoursePanel(state: HomeState) {
    val content = courseCardContent(state)
    val dateParts = state.dateLabel.split(" ", limit = 2)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = HomeSurface
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.width(13.dp).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = HomePurple
                ) {}
                Text(
                    text = "今日课程",
                    modifier = Modifier.padding(start = 18.dp),
                    color = HomeInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(dateParts.firstOrNull().orEmpty(), color = HomeMuted, fontSize = 13.sp)
                Text(
                    dateParts.getOrNull(1).orEmpty(),
                    modifier = Modifier.padding(start = 12.dp),
                    color = HomeMuted,
                    fontSize = 13.sp
                )
            }
            if (!content.hasContent) {
                Text(
                    text = "今天还没有课程",
                    modifier = Modifier.padding(vertical = 34.dp),
                    color = HomeMuted,
                    fontSize = 16.sp
                )
            } else {
                content.items.forEachIndexed { index, item ->
                    HomeCourseTimelineRow(index = index + 1, item = item)
                    if (index < content.items.lastIndex) {
                        HorizontalDivider(color = HomeTimelineLine, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCourseTimelineRow(index: Int, item: HomeCardItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(20.dp),
            color = HomeTodoPill
        ) {
            Text(
                text = index.toString(),
                modifier = Modifier.padding(top = 8.dp),
                color = HomeInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = item.primaryText,
            modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp),
            color = HomeInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Column(horizontalAlignment = Alignment.End) {
            item.secondaryText?.let { location ->
                HomeCourseDetail(R.drawable.course_location_icon, location, "课程地点")
            }
            item.trailingText?.let { time ->
                HomeCourseDetail(R.drawable.course_time_icon, time, "上课时间")
            }
        }
    }
}

@Composable
private fun HomeCourseDetail(icon: Int, text: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 4.dp),
            color = HomeMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeSchedulePanel(state: HomeState) = HomeListPanel("今日待办", state.dateLabel, state.todaySchedules.map { Triple(it.title, it.timeLabel, "") }, "今天还没有待办")

@Composable
private fun HomeListPanel(title: String, date: String, rows: List<Triple<String, String, String>>, empty: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = HomeSurface) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = HomeInk, fontSize = 21.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(date, color = HomeMuted, fontSize = 13.sp) }
            if (rows.isEmpty()) Text(empty, color = HomeMuted, fontSize = 16.sp, modifier = Modifier.padding(vertical = 20.dp))
            rows.forEachIndexed { index, row -> Row(verticalAlignment = Alignment.Top) { Surface(modifier = Modifier.size(28.dp), shape = RoundedCornerShape(14.dp), color = HomeTodoPill) { Text("${index + 1}", color = HomeInk, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp)) }; Column(modifier = Modifier.padding(start = 12.dp)) { Text(row.first, color = HomeInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(listOf(row.second, row.third).filter { it.isNotBlank() }.joinToString(" · "), color = HomeMuted, fontSize = 14.sp) } } }
        }
    }
}

@Composable
private fun HomeHealthPanel(state: HomeState) {
    val presentation = healthHomePresentation(state)
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = HomeSurface) {
        Column(modifier = Modifier.padding(20.dp)) { Text("今日健康", color = HomeInk, fontSize = 21.sp, fontWeight = FontWeight.Bold); Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { HomeHealthCard(Modifier.weight(1f), "今日体重", presentation.weightKg, if (state.latestWeightJin == null) "记录后可查看" else "kg", HomePurple); HomeHealthCard(Modifier.weight(1f), "经期", presentation.periodValue, presentation.periodCaption, HomeOrange) } }
    }
}

@Composable
private fun HomeHealthCard(modifier: Modifier, title: String, value: String, caption: String, color: Color) { Surface(modifier = modifier.heightIn(min = 142.dp), shape = RoundedCornerShape(22.dp), color = color.copy(alpha = .28f)) { Column(modifier = Modifier.padding(16.dp)) { Text(title, color = HomeInk, fontSize = 15.sp); Text(value, color = HomeInk, fontSize = if (value.startsWith("尚未")) 17.sp else 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp)); Text(caption, color = HomeMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } } }

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "首页",
                color = HomeInk,
                fontSize = 34.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "今天的重点，一眼看清。",
                modifier = Modifier.padding(top = 2.dp),
                color = HomeMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.home_settings_flower),
            contentDescription = "打开设置",
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onOpenSettings)
        )
    }
}

@Composable
private fun HomeOverviewCard(state: HomeState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(224.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.home_summary_scene),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentScale = ContentScale.FillBounds
            )
            Text(
                text = state.dateLabel,
                modifier = Modifier.offset(x = 24.dp, y = 24.dp),
                color = HomeMuted,
                fontSize = 15.sp,
                lineHeight = 18.sp
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 11.dp),
                color = Color.White.copy(alpha = 0.76f),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(
                    text = "今天",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color(0xFF6F67F2),
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "${state.greeting}，今天还有 ${state.upcomingEventCount} 件事",
                modifier = Modifier.offset(x = 24.dp, y = 64.dp),
                color = HomeInk,
                fontSize = 25.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(78.dp),
                color = Color.White.copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OverviewMetric(label = "课程", value = state.todayCourseCount.toString())
                    OverviewMetric(label = "日程", value = state.upcomingEventCount.toString())
                    OverviewMetric(label = "预算", value = state.budgetUsageLabel())
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String) {
    Column(
        modifier = Modifier.width(74.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = HomePurple,
            fontSize = if (label == "预算" && value.length > 3) 21.sp else 23.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            color = HomeMuted,
            fontSize = 12.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun HomeContentCard(
    title: String,
    content: HomeCardContent,
    iconResource: Int,
    illustrationResource: Int,
    iconBackground: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 156.dp)
        ) {
            Surface(
                modifier = Modifier
                    .offset(x = 20.dp, y = 28.dp)
                    .size(42.dp),
                shape = RoundedCornerShape(21.dp),
                color = iconBackground
            ) {
                Image(
                    painter = painterResource(iconResource),
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Text(
                text = title,
                modifier = Modifier.offset(x = 74.dp, y = 39.dp),
                color = HomeInk,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Image(
                painter = painterResource(illustrationResource),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 11.dp)
                    .width(132.dp)
                    .height(122.dp),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 78.dp, end = 146.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content.items.forEach { item ->
                    Text(
                        text = item.primaryText,
                        color = HomeMuted,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.secondaryText?.let { detail ->
                        Text(
                            text = detail,
                            color = HomeMuted,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun HomeState.budgetUsageLabel(): String {
    val budget = monthlyBudgetCents?.takeIf { it > 0L } ?: return "未设置"
    val percentage = java.math.BigInteger.valueOf(monthlySpendingCents)
        .multiply(java.math.BigInteger.valueOf(100L))
        .divide(java.math.BigInteger.valueOf(budget))
    return "$percentage%"
}
