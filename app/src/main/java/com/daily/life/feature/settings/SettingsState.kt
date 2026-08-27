package com.daily.life.feature.settings

import java.time.LocalDate

data class SettingsState(
    val currentSemesterName: String? = null,
    val semesterStartDate: LocalDate? = null,
    val targetWeightJin: Double? = null,
    val monthlyBudgetCents: Long? = null,
    val saveStatus: String? = null
)
