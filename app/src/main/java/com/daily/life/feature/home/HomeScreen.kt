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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.daily.life.core.navigation.DailyDestination

private val HomeInk = Color(0xFF0E172B)
private val HomeMuted = Color(0xFF78859E)
private val HomePurple = Color(0xFF6E66F7)
private val HomeCoursePill = Color(0xFFF0EBFF)
private val HomeTodoPill = Color(0xFFFFEDE3)

@Composable
fun HomeScreen(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onDestinationSelected: (DailyDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 30.dp, end = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HomeHeader(onOpenSettings = onOpenSettings)
        HomeOverviewCard(state = state)
        HomeContentCard(
            title = "今日课表",
            content = courseCardContent(state),
            iconResource = R.drawable.home_course_icon,
            illustrationResource = R.drawable.home_course_illustration,
            iconBackground = HomeCoursePill,
            onClick = { onDestinationSelected(DailyDestination.Timetable) }
        )
        HomeContentCard(
            title = "今日待办",
            content = scheduleCardContent(state),
            iconResource = R.drawable.home_todo_icon,
            illustrationResource = R.drawable.home_todo_illustration,
            iconBackground = HomeTodoPill,
            onClick = { onDestinationSelected(DailyDestination.Schedule) }
        )
    }
}

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
            painter = painterResource(R.drawable.home_settings),
            contentDescription = "打开设置",
            modifier = Modifier
                .padding(top = 6.dp)
                .size(34.dp)
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
            .height(156.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, end = 146.dp, bottom = 24.dp)
            ) {
                Text(
                    text = content.primaryText,
                    color = HomeMuted,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                content.secondaryText?.let { detail ->
                    Text(
                        text = detail,
                        modifier = Modifier.padding(top = 2.dp),
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

private fun HomeState.budgetUsageLabel(): String {
    val budget = monthlyBudgetCents?.takeIf { it > 0L } ?: return "未设置"
    val percentage = java.math.BigInteger.valueOf(monthlySpendingCents)
        .multiply(java.math.BigInteger.valueOf(100L))
        .divide(java.math.BigInteger.valueOf(budget))
    return "$percentage%"
}
