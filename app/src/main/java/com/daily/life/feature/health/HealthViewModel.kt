package com.daily.life.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthState(
    val selectedMonth: YearMonth,
    val weights: List<WeightRecord> = emptyList(),
    val activities: List<ActivityRecord> = emptyList(),
    val report: LocalReport? = null,
    val targetWeightJin: Double? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sourceMessage: String? = null,
    val sourceAvailability: List<DataSourceAvailability> = emptyList(),
    val statusMessage: String? = null
)

class HealthViewModel(
    private val repository: HealthRepository,
    private val preferences: com.daily.life.core.datastore.DailyPreferences,
    private val clock: Clock = Clock.systemDefaultZone(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val selectedMonth = MutableStateFlow(YearMonth.now(clock))
    private val _state = MutableStateFlow(HealthState(selectedMonth = selectedMonth.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            repository.observeWeightRecords().collect { weights ->
                _state.update { it.copy(weights = weights) }
            }
        }
        scope.launch {
            preferences.targetWeightJin.collect { target ->
                _state.update { it.copy(targetWeightJin = target) }
            }
        }
        scope.launch {
            selectedMonth.collectLatest { month ->
                _state.update { it.copy(selectedMonth = month, isLoading = true, errorMessage = null) }
                try {
                    val activities = repository.observeActivityRecords(month).first()
                    val report = repository.generateMonthlyReport(month)
                    _state.update {
                        it.copy(
                            activities = activities,
                            report = report,
                            isLoading = false
                        )
                    }
                } catch (error: Exception) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "月报生成失败"
                            )
                        }
                }
            }
        }
    }

    fun selectPreviousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun selectNextMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    fun selectCurrentMonth() {
        selectedMonth.value = YearMonth.now(clock)
    }

    fun recordWeight(weightJin: Double, notes: String? = null) {
        scope.launch {
            try {
                repository.recordWeight(weightJin = weightJin, notes = notes)
                _state.update { it.copy(statusMessage = "体重已记录", errorMessage = null) }
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = error.message ?: "体重记录失败") }
            }
        }
    }

    fun setTargetWeight(weightJin: Double?) {
        scope.launch {
            try {
                repository.setTargetWeight(weightJin)
                _state.update { it.copy(statusMessage = "目标体重已保存", errorMessage = null) }
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = error.message ?: "目标体重保存失败") }
            }
        }
    }

    fun readActivity() {
        scope.launch {
            val month = selectedMonth.value
            _state.update { it.copy(isLoading = true, errorMessage = null, sourceMessage = null) }
            try {
                val result = repository.readActivity(month)
                val report = repository.generateMonthlyReport(month)
                _state.update {
                    it.copy(
                        report = report,
                        isLoading = false,
                        sourceMessage = result.message,
                        sourceAvailability = result.availabilities,
                        statusMessage = "已读取 ${result.importedCount} 条活动记录"
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "读取活动失败")
                }
            }
        }
    }

    fun regenerateReport() {
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val report = repository.generateMonthlyReport(selectedMonth.value)
                _state.update { it.copy(report = report, isLoading = false) }
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }
}
