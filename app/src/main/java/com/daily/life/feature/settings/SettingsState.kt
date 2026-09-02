package com.daily.life.feature.settings

import java.time.Instant
import java.time.LocalDate

data class HolidayCalendarSourceState(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean,
    val enabled: Boolean,
    val lastSuccessfulSyncAt: Instant? = null,
    val error: String? = null
)

data class SettingsState(
    val currentSemesterName: String? = null,
    val semesterStartDate: LocalDate? = null,
    val targetWeightJin: Double? = null,
    val monthlyBudgetCents: Long? = null,
    val saveStatus: String? = null,
    val holidaySources: List<HolidayCalendarSourceState> = emptyList(),
    val holidaySyncStatus: String? = null,
    val holidayLastSyncAt: Instant? = null,
    val holidayError: String? = null
)
