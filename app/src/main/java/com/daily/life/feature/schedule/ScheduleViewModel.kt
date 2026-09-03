package com.daily.life.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.CalendarDayRule
import com.daily.life.core.calendar.CalendarRuleSource
import com.daily.life.core.calendar.HolidayCalendarRepository
import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.notification.ReminderScheduleStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val holidayCalendarRepository: HolidayCalendarRepository? = null,
    private val preferences: DailyPreferences? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val calendarRefreshMutex = Mutex()
    private val viewMode = MutableStateFlow(ScheduleViewMode.MONTH)
    private val selectedDate = MutableStateFlow(LocalDate.now(clock))
    private val visibleMonth = MutableStateFlow(YearMonth.from(selectedDate.value))
    private val _state = MutableStateFlow(
        ScheduleState(
            viewMode = viewMode.value,
            selectedDate = selectedDate.value,
            visibleMonth = visibleMonth.value
        )
    )
    val state = _state.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(viewMode, selectedDate, visibleMonth, ::ScheduleQuery)
                .flatMapLatest { query -> query.observeEvents(repository, clock.zone) }
                .collect { events ->
                    _state.update {
                        it.copy(
                            events = events.all,
                            selectedDateEvents = events.selectedDate
                        )
                    }
                }
        }
        holidayCalendarRepository?.let { holidayRepository ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                holidayRepository.initialize()
                refreshCalendarRulesInternal(visibleMonth.value)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            (preferences?.holidayLastSyncAt ?: flowOf(null)).collect { syncedAt ->
                _state.update { current -> current.copy(holidayLastSyncAt = syncedAt) }
            }
        }
    }

    fun setViewMode(mode: ScheduleViewMode) {
        viewMode.value = mode
        _state.update { it.copy(viewMode = mode) }
    }

    fun selectDate(date: LocalDate) {
        val selection = selectCalendarDate(visibleMonth.value, date)
        selectedDate.value = selection.selectedDate
        visibleMonth.value = selection.visibleMonth
        _state.update { current ->
            current.copy(
                selectedDate = selection.selectedDate,
                visibleMonth = selection.visibleMonth,
                selectedCalendarRule = current.calendarRules.firstOrNull { it.date == date }
            )
        }
        if (holidayCalendarRepository != null) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                refreshCalendarRulesInternal(selection.visibleMonth)
            }
        }
    }

    fun browseMonth(month: YearMonth) {
        visibleMonth.value = month
        _state.update { it.copy(visibleMonth = month) }
        if (holidayCalendarRepository != null) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                refreshCalendarRulesInternal(month)
            }
        }
    }

    fun startCreate(action: ScheduleQuickAction? = null) {
        val editor = ScheduleEditorState.create(clock.instant(), clock.zone).let {
            when (action) {
                ScheduleQuickAction.EXAM -> it.copy(title = "考试")
                ScheduleQuickAction.BIRTHDAY -> it.copy(title = "生日", repeatYearly = true)
                ScheduleQuickAction.SMALL_THING -> it.copy(title = "小事")
                null -> it
            }
        }
        _state.update { it.copy(editor = editor, statusMessage = null) }
    }

    fun startEdit(event: ScheduleEvent) {
        _state.update { it.copy(editor = ScheduleEditorState.from(event, clock.zone), statusMessage = null) }
    }

    fun dismissEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun replaceEditor(editor: ScheduleEditorState) {
        _state.update { it.copy(editor = editor.copy(errorMessage = null)) }
    }

    fun updateTitle(value: String) = updateEditor { copy(title = value) }

    fun updateDate(value: String) = updateEditor { copy(date = value) }

    fun updateTime(value: String) = updateEditor { copy(time = value) }

    fun updateReminderOffset(value: String) = updateEditor { copy(reminderOffsetMinutes = value) }

    fun updateRepeatYearly(value: Boolean) = updateEditor { copy(repeatYearly = value) }

    fun updateNotes(value: String) = updateEditor { copy(notes = value) }

    fun saveEditor() {
        val editor = state.value.editor ?: return
        val now = clock.instant()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val existing = editor.id?.let { repository.findById(it) }
            val event = runCatching {
                editor.toEvent(now, clock.zone).copy(createdAt = existing?.createdAt ?: now)
            }.getOrElse { error ->
                _state.update { current ->
                    current.copy(editor = editor.copy(errorMessage = error.message ?: "日程内容无效"))
                }
                return@launch
            }
            if (editor.id == null) {
                repository.create(event)
            } else {
                repository.update(event)
            }
            val message = when (repository.lastReminderStatus) {
                ReminderScheduleStatus.PERMISSION_RESTRICTED ->
                    if (event.reminderMode == ReminderMode.ALARM) {
                        "日程已保存，但闹钟权限未开启或闹钟未注册"
                    } else {
                        "日程已保存，但系统日历提醒未同步"
                    }
                else -> "日程已保存"
            }
            _state.update { current ->
                current.copy(
                    editor = null,
                    statusMessage = message,
                    calendarEventIdToEdit = null
                )
            }
        }
    }

    fun deleteEvent(eventId: Long) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.delete(eventId)
            _state.update { it.copy(statusMessage = "日程已删除") }
        }
    }

    fun consumeCalendarEventEditorRequest() {
        _state.update { it.copy(calendarEventIdToEdit = null) }
    }

    fun refreshCalendarRules() {
        val month = visibleMonth.value
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            refreshCalendarRulesInternal(month)
        }
    }

    fun saveCalendarDayOverride(
        startDate: LocalDate,
        endDate: LocalDate,
        kind: CalendarDayKind,
        note: String?
    ) {
        val holidayRepository = holidayCalendarRepository ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                holidayRepository.saveDateOverride(startDate, endDate, kind, note?.trim().orEmpty().ifBlank { null })
                refreshCalendarRulesInternal(visibleMonth.value)
            }.onSuccess {
                _state.update { it.copy(statusMessage = "日期状态已更新") }
            }.onFailure { error ->
                _state.update { it.copy(statusMessage = error.message ?: "日期状态无效") }
            }
        }
    }

    fun clearCalendarDayOverrides(dates: List<LocalDate>) {
        val holidayRepository = holidayCalendarRepository ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            holidayRepository.clearDateOverrides(dates.distinct())
            refreshCalendarRulesInternal(visibleMonth.value)
            _state.update { it.copy(statusMessage = "已恢复自动判断") }
        }
    }

    private fun updateEditor(update: ScheduleEditorState.() -> ScheduleEditorState) {
        _state.update { current -> current.editor?.let { current.copy(editor = it.update()) } ?: current }
    }

    private suspend fun refreshCalendarRulesInternal(month: YearMonth) {
        val holidayRepository = holidayCalendarRepository ?: return
        calendarRefreshMutex.withLock {
            val gridWindow = calendarGridWindow(month)
            holidayRepository.initialize()
            val sourceNameMap = holidayRepository.observeSourcesSnapshot().associate { it.id to it.name }
            val gridRules = holidayRepository.resolveBetween(gridWindow.start, gridWindow.endInclusive)
            val selectedRule = selectedDate.value.takeIf { it !in gridWindow }?.let { date ->
                holidayRepository.resolveBetween(date, date).singleOrNull()
            }
            val rules = (gridRules + listOfNotNull(selectedRule)).map { rule ->
                rule.toUiModel(sourceNameMap)
            }
            _state.update { current ->
                current.copy(
                    calendarRules = rules,
                    selectedCalendarRule = rules.firstOrNull { it.date == current.selectedDate }
                )
            }
        }
    }

    private fun CalendarDayRule.toUiModel(sourceNameMap: Map<String, String>): ScheduleCalendarRuleUi {
        val resolvedSourceName = resolveSourceName(sourceNameMap)
        return ScheduleCalendarRuleUi(
            date = date,
            kind = kind,
            source = source,
            sourceName = resolvedSourceName,
            sourceLabel = when (source) {
                CalendarRuleSource.MANUAL -> "手动修正"
                else -> resolvedSourceName
            },
            label = label,
            badge = badgeForCalendarRule(this),
            manualMarker = manualMarkerForCalendarRule(this),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

    private fun CalendarDayRule.resolveSourceName(sourceNameMap: Map<String, String>): String {
        val ids = sourceId?.split(",")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        return when (source) {
            CalendarRuleSource.WEEKEND_DEFAULT -> "周末默认"
            CalendarRuleSource.SYSTEM_CALENDAR -> "系统日历"
            CalendarRuleSource.MANUAL -> "手动修正"
            CalendarRuleSource.BUILTIN_ICS,
            CalendarRuleSource.CUSTOM_ICS -> ids.mapNotNull { sourceNameMap[it] }.distinct().joinToString(" / ").ifBlank {
                if (source == CalendarRuleSource.BUILTIN_ICS) "内置节假日订阅" else "自定义节假日订阅"
            }
        }
    }

    private data class ScheduleQuery(
        val mode: ScheduleViewMode,
        val date: LocalDate,
        val visibleMonth: YearMonth
    ) {
        fun observeEvents(repository: ScheduleRepository, zone: ZoneId) = when (mode) {
            ScheduleViewMode.MONTH -> {
                val gridWindow = calendarGridWindow(visibleMonth)
                val gridEvents = repository.observeBetween(
                    gridWindow.start.atStartOfDay(zone).toInstant(),
                    gridWindow.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
                )
                val selectedDateEvents = repository.observeBetween(
                    date.atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
                )
                combine(gridEvents, selectedDateEvents) { grid, selected ->
                    ScheduleEvents(
                        all = (grid + selected).associateBy(ScheduleEvent::id).values.sortedBy(ScheduleEvent::eventAt),
                        selectedDate = selected
                    )
                }
            }
            ScheduleViewMode.WEEK,
            ScheduleViewMode.DAY -> repository.observeBetween(start(zone), end(zone)).map { events ->
                ScheduleEvents(
                    all = events,
                    selectedDate = events.filter { event -> event.eventAt.atZone(zone).toLocalDate() == date }
                )
            }
        }

        fun start(zone: ZoneId): Instant = when (mode) {
            ScheduleViewMode.MONTH -> calendarGridWindow(visibleMonth).start.atStartOfDay(zone).toInstant()
            ScheduleViewMode.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong()).atStartOfDay(zone).toInstant()
            ScheduleViewMode.DAY -> date.atStartOfDay(zone).toInstant()
        }

        fun end(zone: ZoneId): Instant = when (mode) {
            ScheduleViewMode.MONTH -> calendarGridWindow(visibleMonth).endInclusive.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
            ScheduleViewMode.WEEK -> start(zone).plusSeconds(7 * 24 * 60 * 60L).minusMillis(1)
            ScheduleViewMode.DAY -> date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
        }
    }

    private data class ScheduleEvents(
        val all: List<ScheduleEvent>,
        val selectedDate: List<ScheduleEvent>
    )
}
