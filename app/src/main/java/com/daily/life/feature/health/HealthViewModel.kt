package com.daily.life.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.datastore.DailyPreferences
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HealthTab(val label: String) {
    WEIGHT("体重"),
    PERIOD("经期")
}

data class HealthState(
    val selectedMonth: YearMonth,
    val selectedTab: HealthTab = HealthTab.WEIGHT,
    val weights: List<WeightRecord> = emptyList(),
    val periodRecords: List<PeriodRecord> = emptyList(),
    val nextPeriodStart: LocalDate? = null,
    val menstrualCycleDays: Int = DailyPreferences.DEFAULT_MENSTRUAL_CYCLE_DAYS,
    val targetWeightJin: Double? = null,
    val weightRecordsLoaded: Boolean = false,
    val targetWeightLoaded: Boolean = false,
    val periodRecordsLoaded: Boolean = false,
    val periodPredictionLoaded: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class HealthViewModel(
    private val repository: HealthRepository,
    private val periodRepository: PeriodRepository,
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
            repository.observeWeightRecords()
                .catch { error ->
                    _state.update {
                        it.copy(
                            weightRecordsLoaded = true,
                            errorMessage = error.message ?: "健康数据读取失败"
                        )
                    }
                }
                .collect { weights ->
                    _state.update { it.copy(weights = weights, weightRecordsLoaded = true) }
                }
        }
        scope.launch {
            preferences.targetWeightJin
                .catch { error ->
                    _state.update {
                        it.copy(
                            targetWeightLoaded = true,
                            errorMessage = error.message ?: "健康数据读取失败"
                        )
                    }
                }
                .collect { target ->
                    _state.update { it.copy(targetWeightJin = target, targetWeightLoaded = true) }
                }
        }
        scope.launch {
            periodRepository.observeRecords()
                .catch { error ->
                    _state.update {
                        it.copy(
                            periodRecordsLoaded = true,
                            errorMessage = error.message ?: "健康数据读取失败"
                        )
                    }
                }
                .collect { records ->
                    _state.update { it.copy(periodRecords = records, periodRecordsLoaded = true) }
                }
        }
        scope.launch {
            periodRepository.observeNextStartDate()
                .catch { error ->
                    _state.update {
                        it.copy(
                            periodPredictionLoaded = true,
                            errorMessage = error.message ?: "健康数据读取失败"
                        )
                    }
                }
                .collect { nextStartDate ->
                    _state.update { it.copy(nextPeriodStart = nextStartDate, periodPredictionLoaded = true) }
                }
        }
        scope.launch {
            preferences.menstrualCycleDays
                .catch { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "健康数据读取失败") }
                }
                .collect { cycleDays ->
                    _state.update { it.copy(menstrualCycleDays = cycleDays) }
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

    fun selectTab(tab: HealthTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun recordWeight(weightJin: Double, recordedOn: LocalDate) {
        recordWeight(weightJin, recordedOn.atStartOfDay())
    }

    fun recordWeight(weightJin: Double, recordedAt: LocalDateTime) {
        scope.launch {
            try {
                repository.recordWeight(
                    recordedAt = weightRecordInstantFor(recordedAt, clock.zone),
                    weightJin = weightJin
                )
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

    fun recordPeriod(startDate: LocalDate, endDate: LocalDate) {
        scope.launch {
            try {
                periodRepository.record(startDate, endDate)
                _state.update {
                    it.copy(
                        statusMessage = "经期已记录",
                        errorMessage = null
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(errorMessage = error.message ?: "经期记录失败")
                }
            }
        }
    }

    fun updatePeriod(record: PeriodRecord) {
        scope.launch {
            runCatching { periodRepository.update(record) }
                .onSuccess { _state.update { it.copy(statusMessage = "经期记录已更新", errorMessage = null) } }
                .onFailure { error -> _state.update { it.copy(errorMessage = error.message ?: "经期更新失败") } }
        }
    }

    fun deletePeriod(id: Long) {
        scope.launch {
            periodRepository.delete(id)
            _state.update { it.copy(statusMessage = "经期记录已删除", errorMessage = null) }
        }
    }

}
