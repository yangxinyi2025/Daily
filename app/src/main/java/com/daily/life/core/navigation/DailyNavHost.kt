package com.daily.life.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.daily.life.core.designsystem.DailyBottomBar
import com.daily.life.core.designsystem.DailyPlaceholderPage

@Composable
fun DailyNavHost(
    navController: NavHostController,
    rootState: DailyRootState
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            DailyBottomBar(
                current = rootState.currentDestination,
                onDestinationSelected = rootState.onDestinationSelected
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = DailyDestination.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(DailyDestination.Home.route) {
                DailyPlaceholderPage(
                    title = DailyDestination.Home.label,
                    pageLabel = "首页页面",
                    message = "首页摘要将在后续任务接入本地数据与设置。"
                )
            }
            composable(DailyDestination.Timetable.route) {
                DailyPlaceholderPage(
                    title = DailyDestination.Timetable.label,
                    pageLabel = "课表页面",
                    message = "课表导入、周次计算与预览确认流程将在后续任务实现。"
                )
            }
            composable(DailyDestination.Schedule.route) {
                DailyPlaceholderPage(
                    title = DailyDestination.Schedule.label,
                    pageLabel = "日程页面",
                    message = "日程提醒、生日和闹钟功能将在后续任务实现。"
                )
            }
            composable(DailyDestination.Health.route) {
                DailyPlaceholderPage(
                    title = DailyDestination.Health.label,
                    pageLabel = "健康页面",
                    message = "体重、活动与月报模块将在后续任务接入。"
                )
            }
            composable(DailyDestination.Bill.route) {
                DailyPlaceholderPage(
                    title = DailyDestination.Bill.label,
                    pageLabel = "账单页面",
                    message = "账单导入、分类与预算统计将在后续任务实现。"
                )
            }
            composable(DailyDestination.Settings.route) {
                DailyPlaceholderPage(
                    title = DailyDestination.Settings.label,
                    pageLabel = "设置页面",
                    message = "设置页将在后续任务接入学期、目标和同步配置。"
                )
            }
        }
    }
}

fun NavHostController.navigateToPrimaryDestination(destination: DailyDestination) {
    navigate(destination.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}
