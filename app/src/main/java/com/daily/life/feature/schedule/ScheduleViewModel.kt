package com.daily.life.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.notification.ReminderScheduleStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val viewMode = MutableStateFlow(ScheduleViewMode.MONTH)
    private val selectedDate = MutableStateFlow(LocalDate.now(clock))
    private val _state = MutableStateFlow(
        ScheduleState(
            viewMode = viewMode.value,
            selectedDate = selectedDate.value
        )
    )
    val state = _state.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(viewMode, selectedDate, ::ScheduleQuery)
                .flatMapLatest { query -> repository.observeBetween(query.start(clock.zone), query.end(clock.zone)) }
                .collect { events -> _state.update { it.copy(events = events) } }
        }
    }

    fun setViewMode(mode: ScheduleViewMode) {
        viewMode.value = mode
        _state.update { it.copy(viewMode = mode) }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        _state.update { it.copy(selectedDate = date) }
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

    fun updateReminderMode(value: ReminderMode) = updateEditor { copy(reminderMode = value) }

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
            val message = if (repository.lastReminderStatus == ReminderScheduleStatus.PERMISSION_RESTRICTED) {
                "日程已保存，但提醒权限受限"
            } else {
                "日程已保存"
            }
            _state.update { current -> current.copy(editor = null, statusMessage = message) }
        }
    }

    fun deleteEvent(eventId: Long) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.delete(eventId)
            _state.update { it.copy(statusMessage = "日程已删除") }
        }
    }

    private fun updateEditor(update: ScheduleEditorState.() -> ScheduleEditorState) {
        _state.update { current -> current.editor?.let { current.copy(editor = it.update()) } ?: current }
    }

    private data class ScheduleQuery(
        val mode: ScheduleViewMode,
        val date: LocalDate
    ) {
        fun start(zone: ZoneId): Instant = when (mode) {
            ScheduleViewMode.MONTH -> date.withDayOfMonth(1).atStartOfDay(zone).toInstant()
            ScheduleViewMode.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong()).atStartOfDay(zone).toInstant()
            ScheduleViewMode.DAY -> date.atStartOfDay(zone).toInstant()
        }

        fun end(zone: ZoneId): Instant = when (mode) {
            ScheduleViewMode.MONTH -> date.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().minusMillis(1)
            ScheduleViewMode.WEEK -> start(zone).plusSeconds(7 * 24 * 60 * 60L).minusMillis(1)
            ScheduleViewMode.DAY -> date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
        }
    }
}
