package com.daily.life.feature.timetable

import com.daily.life.core.calendar.SystemCalendarSpecialDay
import com.daily.life.core.calendar.SystemCalendarSpecialDayKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableImportAdjustmentTest {
    @Test
    fun systemCalendarSourceStillRequiresTheUserToChooseTheCourseDay() {
        val date = LocalDate.of(2026, 10, 10)

        val choices = buildTimetableAdjustmentChoices(
            listOf(
                specialDay(
                    date = date,
                    kind = SystemCalendarSpecialDayKind.MakeupWorkday,
                    sourceDayOfWeek = 5,
                    label = "国庆补周五"
                )
            ),
            existing = emptyList()
        )

        assertEquals(null, choices.single().selectedSourceDayOfWeek)
        assertEquals(null, choices.single().selectedSourceWeekParity)
        assertEquals((1..7).toList(), choices.single().options)
        assertTrue(choices.single().isRequired)
        assertFalse(adjustmentChoicesAreComplete(choices))
    }

    @Test
    fun unknownMakeupSourceRequiresUserSelectionBeforeImport() {
        val date = LocalDate.of(2026, 10, 10)

        val choices = buildTimetableAdjustmentChoices(
            listOf(
                specialDay(
                    date = date,
                    kind = SystemCalendarSpecialDayKind.MakeupWorkday,
                    sourceDayOfWeek = null,
                    label = "调休上班"
                )
            ),
            existing = emptyList()
        )

        assertEquals(null, choices.single().selectedSourceDayOfWeek)
        assertEquals(null, choices.single().selectedSourceWeekParity)
        assertFalse(adjustmentChoicesAreComplete(choices))
    }

    @Test
    fun userSelectionMakesAdjustmentCompleteAndCanBePersisted() {
        val date = LocalDate.of(2026, 10, 10)
        val selected = buildTimetableAdjustmentChoices(
            listOf(specialDay(date, SystemCalendarSpecialDayKind.MakeupWorkday, null, "调休上班")),
            existing = emptyList()
        ).map { it.copy(selectedSourceDayOfWeek = 4, selectedSourceWeekParity = WeekParity.EVEN) }

        assertTrue(adjustmentChoicesAreComplete(selected))
        assertEquals(
            listOf(SemesterCalendarAdjustmentInput(date, 4, WeekParity.EVEN, sourceLabel = "调休上班")),
            selected.toAdjustmentInputs()
        )
    }

    @Test
    fun existingUserChoiceIsPreservedWhenCalendarDataRefreshes() {
        val date = LocalDate.of(2026, 10, 10)
        val existing = listOf(
            TimetableAdjustmentChoiceState(
                date,
                "系统补周五",
                selectedSourceDayOfWeek = 4,
                selectedSourceWeekParity = WeekParity.ODD
            )
        )

        val choices = buildTimetableAdjustmentChoices(
            listOf(specialDay(date, SystemCalendarSpecialDayKind.MakeupWorkday, 5, "补周五")),
            existing
        )

        assertEquals(4, choices.single().selectedSourceDayOfWeek)
        assertEquals(WeekParity.ODD, choices.single().selectedSourceWeekParity)
    }

    @Test
    fun weekdayWithoutParityCannotConfirmAnAdjustment() {
        val date = LocalDate.of(2026, 10, 10)
        val selected = listOf(
            TimetableAdjustmentChoiceState(
                actualDate = date,
                label = "调休上班",
                selectedSourceDayOfWeek = 5
            )
        )

        assertFalse(adjustmentChoicesAreComplete(selected))
        assertTrue(selected.toAdjustmentInputs().isEmpty())
    }

    private fun specialDay(
        date: LocalDate,
        kind: SystemCalendarSpecialDayKind,
        sourceDayOfWeek: Int?,
        label: String
    ) = SystemCalendarSpecialDay(date, kind, sourceDayOfWeek, null, label)
}
