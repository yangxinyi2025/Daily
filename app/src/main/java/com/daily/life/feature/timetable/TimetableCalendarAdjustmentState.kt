package com.daily.life.feature.timetable

import com.daily.life.core.calendar.SystemCalendarSpecialDay
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind

internal fun buildTimetableAdjustmentChoices(
    specialDays: Iterable<SystemCalendarSpecialDay>,
    existing: Iterable<TimetableAdjustmentChoiceState>
): List<TimetableAdjustmentChoiceState> {
    val existingByDate = existing.associateBy(TimetableAdjustmentChoiceState::actualDate)
    return specialDays
        .filter { it.kind == SystemCalendarSpecialDayKind.MakeupWorkday }
        .distinctBy(SystemCalendarSpecialDay::date)
        .sortedBy(SystemCalendarSpecialDay::date)
        .map { day ->
            val old = existingByDate[day.date]
            TimetableAdjustmentChoiceState(
                actualDate = day.date,
                label = day.label,
                selectedSourceDayOfWeek = old?.selectedSourceDayOfWeek,
                selectedSourceWeekParity = old?.selectedSourceWeekParity,
                sourceDate = old?.sourceDate ?: day.sourceDate,
                isRequired = true
            )
        }
}

internal fun adjustmentChoicesAreComplete(choices: Iterable<TimetableAdjustmentChoiceState>): Boolean =
    choices.all { choice ->
        choice.selectedSourceDayOfWeek in 1..7 && choice.selectedSourceWeekParity != null
    }

internal fun List<TimetableAdjustmentChoiceState>.toAdjustmentInputs(): List<SemesterCalendarAdjustmentInput> =
    mapNotNull { choice ->
        choice.selectedSourceDayOfWeek?.takeIf { it in 1..7 }?.let { sourceDay ->
            choice.selectedSourceWeekParity?.let { sourceWeekParity ->
            SemesterCalendarAdjustmentInput(
                actualDate = choice.actualDate,
                sourceDayOfWeek = sourceDay,
                sourceWeekParity = sourceWeekParity,
                sourceDate = choice.sourceDate,
                sourceLabel = choice.label
            )
            }
        }
    }
