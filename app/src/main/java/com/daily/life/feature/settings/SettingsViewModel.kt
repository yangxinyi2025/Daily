package com.daily.life.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.datastore.DailyPreferences
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val semesterRepository: SemesterSettingsRepository,
    private val preferences: DailyPreferences,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val saveStatus = MutableStateFlow<String?>(null)

    private val localSettings =
        combine(
            semesterRepository.currentSemester,
            preferences.semesterStartDate,
            preferences.targetWeightJin,
            preferences.defaultBudgetCents
        ) { semester, preferredStartDate, targetWeight, monthlyBudget ->
            LocalSettingsState(
                currentSemesterName = semester.name,
                semesterStartDate = preferredStartDate ?: semester.startDate,
                targetWeightJin = targetWeight,
                monthlyBudgetCents = monthlyBudget
            )
        }

    val state: StateFlow<SettingsState> =
        combine(
            localSettings,
            saveStatus
        ) { local, latestSaveStatus ->
            SettingsState(
                currentSemesterName = local.currentSemesterName,
                semesterStartDate = local.semesterStartDate,
                targetWeightJin = local.targetWeightJin,
                monthlyBudgetCents = local.monthlyBudgetCents,
                saveStatus = latestSaveStatus
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsState()
        )

    fun updateSemesterStartDate(date: LocalDate) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            preferences.setSemesterStartDate(date)
            saveStatus.value = "学期开始日期已保存"
        }
    }

    fun updateTargetWeightJin(value: Double?) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            preferences.setTargetWeightJin(value)
            saveStatus.value = if (value == null) "已清除目标体重" else "目标体重已保存"
        }
    }

    fun updateMonthlyBudgetCents(value: Long?) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            preferences.setDefaultBudgetCents(value)
            saveStatus.value = if (value == null) "已清除月预算" else "月预算已保存"
        }
    }

    private data class LocalSettingsState(
        val currentSemesterName: String?,
        val semesterStartDate: LocalDate?,
        val targetWeightJin: Double?,
        val monthlyBudgetCents: Long?
    )
}
