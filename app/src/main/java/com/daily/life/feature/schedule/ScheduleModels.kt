package com.daily.life.feature.schedule

import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.CalendarRuleSource
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.database.ScheduleEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ScheduleEvent(
    val id: Long = 0L,
    val title: String,
    val eventAt: Instant,
    val reminderOffsetMinutes: Int,
    val reminderMode: ReminderMode,
    val repeatYearly: Boolean,
    val notes: String? = null,
    val location: String? = null,
    val isDismissed: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class ScheduleViewMode {
    MONTH,
    WEEK,
    DAY
}

enum class ScheduleQuickAction(val title: String) {
    EXAM("考试"),
    BIRTHDAY("生日"),
    SMALL_THING("小事")
}

data class ScheduleEditorState(
    val id: Long? = null,
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val reminderOffsetMinutes: String = "0",
    val reminderMode: ReminderMode = ReminderMode.NOTIFICATION,
    val repeatYearly: Boolean = false,
    val notes: String = "",
    val location: String = "",
    val errorMessage: String? = null
) {
    companion object {
        fun create(now: Instant, zone: ZoneId): ScheduleEditorState {
            val local = now.atZone(zone)
            return ScheduleEditorState(
                date = local.toLocalDate().toString(),
                time = local.toLocalTime().withSecond(0).withNano(0).toString()
            )
        }

        fun from(event: ScheduleEvent, zone: ZoneId): ScheduleEditorState {
            val local = event.eventAt.atZone(zone)
            return ScheduleEditorState(
                id = event.id,
                title = event.title,
                date = local.toLocalDate().toString(),
                time = local.toLocalTime().withSecond(0).withNano(0).toString(),
                reminderOffsetMinutes = event.reminderOffsetMinutes.toString(),
                reminderMode = event.reminderMode,
                repeatYearly = event.repeatYearly,
                notes = event.notes.orEmpty(),
                location = event.location.orEmpty()
            )
        }
    }

    fun toEvent(now: Instant, zone: ZoneId): ScheduleEvent {
        require(title.isNotBlank()) { "请输入标题" }
        val localDate = LocalDate.parse(date)
        val localTime = LocalTime.parse(time)
        val offset = reminderOffsetMinutes.toIntOrNull()?.coerceAtLeast(0)
            ?: error("提醒提前量必须是非负整数")
        val eventAt = localDate.atTime(localTime).atZone(zone).toInstant()
        return ScheduleEvent(
            id = id ?: 0L,
            title = title.trim(),
            eventAt = eventAt,
            reminderOffsetMinutes = offset,
            reminderMode = reminderMode,
            repeatYearly = repeatYearly,
            notes = notes.trim().ifBlank { null },
            location = location.trim().ifBlank { null },
            createdAt = now,
            updatedAt = now
        )
    }
}

data class ScheduleState(
    val viewMode: ScheduleViewMode = ScheduleViewMode.MONTH,
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<ScheduleEvent> = emptyList(),
    val calendarRules: List<ScheduleCalendarRuleUi> = emptyList(),
    val selectedCalendarRule: ScheduleCalendarRuleUi? = null,
    val holidayLastSyncAt: Instant? = null,
    val editor: ScheduleEditorState? = null,
    val statusMessage: String? = null,
    val calendarEventIdToEdit: Long? = null
)

data class ScheduleCalendarRuleUi(
    val date: LocalDate,
    val kind: CalendarDayKind,
    val source: CalendarRuleSource,
    val sourceName: String,
    val sourceLabel: String,
    val label: String? = null,
    val badge: ScheduleCalendarBadge? = null,
    val manualMarker: String? = null,
    val updatedAt: Instant
)

internal fun ReminderMode.displayLabel(): String = when (this) {
    ReminderMode.ALARM -> "闹钟提醒"
    ReminderMode.NOTIFICATION -> "消息提醒"
}

fun ScheduleEvent.toEntity(): ScheduleEventEntity = ScheduleEventEntity(
    id = id,
    title = title,
    eventAt = eventAt.toEpochMilli(),
    reminderOffsetMinutes = reminderOffsetMinutes,
    reminderMode = reminderMode,
    repeatYearly = repeatYearly,
    notes = notes,
    location = location,
    isDismissed = isDismissed,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

fun ScheduleEventEntity.toModel(): ScheduleEvent = ScheduleEvent(
    id = id,
    title = title,
    eventAt = Instant.ofEpochMilli(eventAt),
    reminderOffsetMinutes = reminderOffsetMinutes,
    reminderMode = reminderMode,
    repeatYearly = repeatYearly,
    notes = notes,
    location = location,
    isDismissed = isDismissed,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)
