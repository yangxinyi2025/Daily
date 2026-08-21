package com.daily.life.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.daily.life.core.designsystem.DailyBottomBar
import com.daily.life.core.designsystem.DailyPlaceholderPage
import com.daily.life.core.NoOpReminderScheduler
import com.daily.life.DailyApplication
import com.daily.life.feature.home.DaoBillSummaryRepository
import com.daily.life.feature.home.DaoHealthSummaryRepository
import com.daily.life.feature.home.DaoScheduleSummaryRepository
import com.daily.life.feature.home.DaoTimetableSummaryRepository
import com.daily.life.feature.home.HomeScreen
import com.daily.life.feature.home.HomeViewModel
import com.daily.life.feature.settings.DaoSemesterSettingsRepository
import com.daily.life.feature.settings.SettingsScreen
import com.daily.life.feature.settings.SettingsViewModel
import com.daily.life.feature.timetable.PdfTimetableParser
import com.daily.life.feature.timetable.RoomTimetableRepository
import com.daily.life.feature.timetable.TimetableScreen
import com.daily.life.feature.timetable.TimetableViewModel

@Composable
fun DailyNavHost(
    navController: NavHostController,
    rootState: DailyRootState
) {
    val application = LocalContext.current.applicationContext as DailyApplication

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
                val container = application.container
                val database = container.database
                val homeViewModel: HomeViewModel = viewModel {
                    HomeViewModel(
                        timetableRepository = DaoTimetableSummaryRepository(
                            semesterDao = database.semesterDao(),
                            courseDao = database.courseDao(),
                            preferences = container.preferences
                        ),
                        scheduleRepository = DaoScheduleSummaryRepository(database.scheduleEventDao()),
                        healthRepository = DaoHealthSummaryRepository(database.healthDao()),
                        billRepository = DaoBillSummaryRepository(
                            transactionDao = database.transactionDao(),
                            budgetDao = database.budgetDao(),
                            preferences = container.preferences
                        )
                    )
                }
                val homeState by homeViewModel.state.collectAsState()
                HomeScreen(
                    state = homeState,
                    onOpenSettings = {
                        navController.navigate(DailyDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    onDestinationSelected = rootState.onDestinationSelected
                )
            }
            composable(DailyDestination.Timetable.route) {
                val container = application.container
                val timetableViewModel: TimetableViewModel = viewModel {
                    TimetableViewModel(
                        repository = RoomTimetableRepository(
                            database = container.database,
                            preferences = container.preferences,
                            reminderScheduler = container.adapters.reminderSchedulerFactory.create()
                                ?: NoOpReminderScheduler
                        ),
                        parser = PdfTimetableParser()
                    )
                }
                val timetableState by timetableViewModel.state.collectAsState()
                TimetableScreen(
                    state = timetableState,
                    onPreviousWeek = timetableViewModel::selectPreviousWeek,
                    onNextWeek = timetableViewModel::selectNextWeek,
                    onCurrentWeek = timetableViewModel::selectCurrentWeek,
                    onOpenImport = timetableViewModel::openImport,
                    onPdfSelected = timetableViewModel::selectPdf,
                    onSemesterInputChange = timetableViewModel::updateSemesterInput,
                    onImportRowChange = timetableViewModel::updateImportRow,
                    onReplaceExistingChange = timetableViewModel::updateReplaceExisting,
                    onCancelImport = timetableViewModel::cancelImport,
                    onConfirmImport = timetableViewModel::confirmImport
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
                val container = application.container
                val settingsViewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(
                        semesterRepository = DaoSemesterSettingsRepository(
                            semesterDao = container.database.semesterDao(),
                            preferences = container.preferences
                        ),
                        preferences = container.preferences,
                        secretStore = container.secretStore
                    )
                }
                val settingsState by settingsViewModel.state.collectAsState()
                SettingsScreen(
                    state = settingsState,
                    onSemesterStartDateChange = settingsViewModel::updateSemesterStartDate,
                    onTargetWeightChange = settingsViewModel::updateTargetWeightJin,
                    onMonthlyBudgetChange = settingsViewModel::updateMonthlyBudgetCents,
                    onSaveDeepSeekKey = settingsViewModel::saveDeepSeekKey,
                    onSaveWebDavConfig = settingsViewModel::saveWebDavConfig
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
