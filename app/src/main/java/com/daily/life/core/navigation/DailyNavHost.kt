package com.daily.life.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.daily.life.feature.health.HealthRepository
import com.daily.life.feature.health.HealthScreen
import com.daily.life.feature.health.HealthViewModel
import com.daily.life.feature.health.PeriodRepository
import com.daily.life.feature.bill.BillRepository
import com.daily.life.feature.bill.BillScreen
import com.daily.life.feature.bill.BillViewModel
import com.daily.life.feature.settings.DaoSemesterSettingsRepository
import com.daily.life.feature.settings.SettingsScreen
import com.daily.life.feature.settings.SettingsViewModel
import com.daily.life.feature.schedule.RoomScheduleRepository
import com.daily.life.feature.schedule.ScheduleScreen
import com.daily.life.feature.schedule.ScheduleViewModel
import com.daily.life.core.calendar.AndroidCalendarProviderClient
import com.daily.life.core.calendar.CalendarReminderSyncer
import com.daily.life.core.calendar.SystemCalendarGateway
import com.daily.life.core.calendar.SystemCalendarScheduleReader
import com.daily.life.core.notification.AndroidReminderScheduler
import com.daily.life.core.system.BackgroundRuntimeGuideDialog
import com.daily.life.core.system.openAppDetailsSettings
import com.daily.life.feature.timetable.PdfTimetableParser
import com.daily.life.feature.timetable.RoomTimetableRepository
import com.daily.life.feature.timetable.TimetableScreen
import com.daily.life.feature.timetable.TimetableViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

@Composable
fun DailyNavHost(
    navController: NavHostController,
    rootState: DailyRootState
) {
    val context = LocalContext.current
    val application = context.applicationContext as DailyApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val backgroundRuntimeGuideAcknowledged by application.container.preferences
        .backgroundRuntimeGuideAcknowledged
        .map { value -> value as Boolean? }
        .collectAsState(initial = null)
    var backgroundRuntimeGuideDismissed by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    application.container.holidayCalendarRepository.syncIfStale()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    val calendarReader = SystemCalendarScheduleReader.from(application)
                    TimetableViewModel(
                        repository = RoomTimetableRepository(
                            database = container.database,
                            preferences = container.preferences,
                            reminderScheduler = container.adapters.reminderSchedulerFactory.create()
                                ?: NoOpReminderScheduler,
                            calendarReminderSyncer = CalendarReminderSyncer(
                                database = container.database,
                                gateway = SystemCalendarGateway(AndroidCalendarProviderClient(application))
                            ),
                            calendarScheduleReader = calendarReader,
                            holidayCalendarRepository = container.holidayCalendarRepository
                        ),
                        parser = PdfTimetableParser(application::initializePdfBox),
                        calendarReader = calendarReader,
                        holidayCalendarRepository = container.holidayCalendarRepository
                    )
                }
                val timetableState by timetableViewModel.state.collectAsState()
                TimetableScreen(
                    state = timetableState,
                    onPreviousWeek = timetableViewModel::selectPreviousWeek,
                    onNextWeek = timetableViewModel::selectNextWeek,
                    onCurrentWeek = timetableViewModel::selectCurrentWeek,
                    onOpenImport = timetableViewModel::openImport,
                    onOpenPeriodEditor = timetableViewModel::openPeriodEditor,
                    onPdfSelected = timetableViewModel::selectPdf,
                    onSemesterInputChange = timetableViewModel::updateSemesterInput,
                    onImportRowChange = timetableViewModel::updateImportRow,
                    onImportPeriodTimeChange = timetableViewModel::updateImportPeriodTime,
                    onReplaceExistingChange = timetableViewModel::updateReplaceExisting,
                    onCancelImport = timetableViewModel::cancelImport,
                    onConfirmImport = timetableViewModel::confirmImport,
                    onPeriodEditorRowChange = timetableViewModel::updatePeriodEditorRow,
                    onRestorePeriodDefaults = timetableViewModel::restoreDefaultPeriodTimes,
                    onSavePeriodTimes = timetableViewModel::savePeriodTimes,
                    onClosePeriodEditor = timetableViewModel::closePeriodEditor,
                    onMakeupSourceChange = timetableViewModel::updateMakeupSource,
                    onMakeupParityChange = timetableViewModel::updateMakeupParity,
                    onRefreshSystemCalendarDays = timetableViewModel::refreshSystemCalendarDays,
                    onClassOverrideChange = timetableViewModel::updateClassOverride
                )
            }
            composable(DailyDestination.Schedule.route) {
                val container = application.container
                val scheduleViewModel: ScheduleViewModel = viewModel {
                    ScheduleViewModel(
                        repository = RoomScheduleRepository(
                            database = container.database,
                            reminderScheduler = AndroidReminderScheduler(application),
                            calendarReminderSyncer = CalendarReminderSyncer(
                                database = container.database,
                                gateway = SystemCalendarGateway(AndroidCalendarProviderClient(application))
                            )
                        ),
                        holidayCalendarRepository = container.holidayCalendarRepository,
                        preferences = container.preferences
                    )
                }
                val scheduleState by scheduleViewModel.state.collectAsState()
                ScheduleScreen(
                    state = scheduleState,
                    onViewModeChange = scheduleViewModel::setViewMode,
                    onDateSelected = scheduleViewModel::selectDate,
                    onCreate = { scheduleViewModel.startCreate() },
                    onQuickCreate = { action -> scheduleViewModel.startCreate(action) },
                    onEdit = scheduleViewModel::startEdit,
                    onDelete = scheduleViewModel::deleteEvent,
                    onSaveEditor = scheduleViewModel::saveEditor,
                    onDismissEditor = scheduleViewModel::dismissEditor,
                    onEditorChange = scheduleViewModel::replaceEditor,
                    onSaveCalendarDayOverride = scheduleViewModel::saveCalendarDayOverride,
                    onClearCalendarDayOverrides = scheduleViewModel::clearCalendarDayOverrides,
                    onRefreshCalendarRules = scheduleViewModel::refreshCalendarRules,
                    onCalendarEventEditorOpened = scheduleViewModel::consumeCalendarEventEditorRequest
                )
            }
            composable(DailyDestination.Health.route) {
                val container = application.container
                val healthRepository = container.repositories.healthRepositoryFactory.create()
                    ?: HealthRepository(
                        healthDao = container.database.healthDao(),
                        preferences = container.preferences
                    )
                val healthViewModel: HealthViewModel = viewModel {
                    HealthViewModel(
                        repository = healthRepository,
                        periodRepository = PeriodRepository(
                            periodDao = container.database.periodDao(),
                            preferences = container.preferences
                        ),
                        preferences = container.preferences
                    )
                }
                val healthState by healthViewModel.state.collectAsState()
                HealthScreen(
                    state = healthState,
                    onPreviousMonth = healthViewModel::selectPreviousMonth,
                    onNextMonth = healthViewModel::selectNextMonth,
                    onCurrentMonth = healthViewModel::selectCurrentMonth,
                    onSelectTab = healthViewModel::selectTab,
                    onRecordWeight = healthViewModel::recordWeight,
                    onSetTargetWeight = healthViewModel::setTargetWeight,
                    onRecordPeriod = healthViewModel::recordPeriod,
                    onUpdatePeriod = healthViewModel::updatePeriod,
                    onDeletePeriod = healthViewModel::deletePeriod
                )
            }
            composable(DailyDestination.Bill.route) {
                val container = application.container
                val billRepository = container.repositories.billRepositoryFactory.create()
                    ?: BillRepository(
                        database = container.database,
                        preferences = container.preferences
                    )
                val billViewModel: BillViewModel = viewModel {
                    BillViewModel(repository = billRepository)
                }
                val billState by billViewModel.state.collectAsState()
                BillScreen(
                    state = billState,
                    onPreviousPeriod = billViewModel::selectPreviousPeriod,
                    onNextPeriod = billViewModel::selectNextPeriod,
                    onCurrentPeriod = billViewModel::selectCurrentPeriod,
                    onPeriodChange = billViewModel::setPeriod,
                    onDirectionChange = billViewModel::setDirectionFilter,
                    onSearchChange = billViewModel::setSearchText,
                    onOpenImport = billViewModel::openImport,
                    onOpenNewEditor = billViewModel::openNewEditor,
                    onFileSelected = billViewModel::selectFile,
                    onTogglePreviewRow = billViewModel::togglePreviewRow,
                    onCancelImport = billViewModel::cancelImport,
                    onConfirmImport = billViewModel::confirmImport,
                    onOpenEditor = billViewModel::openEditor,
                    onEditorChange = billViewModel::updateEditor,
                    onCancelEditor = billViewModel::dismissEditor,
                    onSaveEditor = billViewModel::saveEditor
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
                        holidayCalendarRepository = container.holidayCalendarRepository
                    )
                }
                val settingsState by settingsViewModel.state.collectAsState()
                SettingsScreen(
                    state = settingsState,
                    onSemesterStartDateChange = settingsViewModel::updateSemesterStartDate,
                    onTargetWeightChange = settingsViewModel::updateTargetWeightJin,
                    onMonthlyBudgetChange = settingsViewModel::updateMonthlyBudgetCents,
                    onAddHolidaySource = settingsViewModel::addHolidaySource,
                    onToggleHolidaySource = settingsViewModel::toggleHolidaySource,
                    onDeleteHolidaySource = settingsViewModel::deleteHolidaySource,
                    onSyncHolidaySources = settingsViewModel::syncHolidaySources
                )
            }
        }
    }
    if (backgroundRuntimeGuideAcknowledged == false && !backgroundRuntimeGuideDismissed) {
        BackgroundRuntimeGuideDialog(
            onOpenSettings = {
                backgroundRuntimeGuideDismissed = true
                coroutineScope.launch {
                    application.container.preferences.setBackgroundRuntimeGuideAcknowledged()
                    context.openAppDetailsSettings()
                }
            },
            onLater = {
                backgroundRuntimeGuideDismissed = true
                coroutineScope.launch {
                    application.container.preferences.setBackgroundRuntimeGuideAcknowledged()
                }
            }
        )
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
