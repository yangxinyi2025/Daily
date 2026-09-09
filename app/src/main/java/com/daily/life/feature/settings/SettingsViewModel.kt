package com.daily.life.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.calendar.HolidayCalendarRepository
import com.daily.life.core.datastore.DailyPreferences
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val semesterRepository: SemesterSettingsRepository,
    private val preferences: DailyPreferences,
    private val holidayCalendarRepository: HolidayCalendarRepository? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val saveStatus = MutableStateFlow<String?>(null)
    private val holidayFormError = MutableStateFlow<String?>(null)

    private val localSettings =
        combine(
            semesterRepository.currentSemester,
            preferences.semesterStartDate,
            preferences.targetWeightJin
        ) { semester, preferredStartDate, targetWeight ->
            LocalSettingsState(
                currentSemesterName = semester.name,
                semesterStartDate = preferredStartDate ?: semester.startDate,
                targetWeightJin = targetWeight
            )
        }

    private val holidaySources =
        holidayCalendarRepository?.let { repository ->
            flow {
                repository.initialize()
                emitAll(repository.observeSources())
            }
        } ?: flowOf(emptyList())

    private val holidaySettings =
        combine(
            holidaySources,
            preferences.holidaySyncStatus,
            preferences.holidayLastSyncAt,
            combine(preferences.holidaySyncError, holidayFormError) { syncError, localError ->
                localError ?: syncError
            }
        ) { holidaySources, holidaySyncStatus, holidayLastSyncAt, holidayError ->
            HolidaySettingsState(
                holidaySources = holidaySources.map { source ->
                    HolidayCalendarSourceState(
                        id = source.id,
                        name = source.name,
                        url = source.url,
                        builtIn = source.builtIn,
                        enabled = source.enabled,
                        lastSuccessfulSyncAt = source.lastSuccessfulSyncAt?.let(Instant::ofEpochMilli),
                        error = source.lastError
                    )
                },
                holidaySyncStatus = holidaySyncStatus,
                holidayLastSyncAt = holidayLastSyncAt,
                holidayError = holidayError
            )
        }

    val state: StateFlow<SettingsState> =
        combine(
            localSettings,
            saveStatus,
            holidaySettings
        ) { local, latestSaveStatus, holiday ->
            SettingsState(
                currentSemesterName = local.currentSemesterName,
                semesterStartDate = local.semesterStartDate,
                targetWeightJin = local.targetWeightJin,
                saveStatus = latestSaveStatus,
                holidaySources = holiday.holidaySources,
                holidaySyncStatus = holiday.holidaySyncStatus,
                holidayLastSyncAt = holiday.holidayLastSyncAt,
                holidayError = holiday.holidayError
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

    fun addHolidaySource(name: String, url: String) {
        val repository = holidayCalendarRepository ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val normalizedName = name.trim()
            val normalizedUrl = url.trim()
            val error = validateHolidaySourceInput(normalizedName, normalizedUrl)
            if (error != null) {
                holidayFormError.value = error
                return@launch
            }
            runCatching { repository.addCustomSource(normalizedName, normalizedUrl) }
                .onSuccess {
                    holidayFormError.value = null
                    saveStatus.value = "节假日订阅已添加"
                }
                .onFailure { throwable ->
                    holidayFormError.value = throwable.message ?: "添加订阅失败"
                }
        }
    }

    fun toggleHolidaySource(id: String, enabled: Boolean) {
        val repository = holidayCalendarRepository ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.setSourceEnabled(id, enabled)
            holidayFormError.value = null
            saveStatus.value = if (enabled) "已启用节假日来源" else "已停用节假日来源"
        }
    }

    fun deleteHolidaySource(id: String) {
        val repository = holidayCalendarRepository ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val target = state.value.holidaySources.firstOrNull { it.id == id }
            if (target?.builtIn == true) {
                holidayFormError.value = "内置来源不能删除"
                return@launch
            }
            repository.deleteSource(id)
            holidayFormError.value = null
            saveStatus.value = "节假日订阅已删除"
        }
    }

    fun syncHolidaySources() {
        val repository = holidayCalendarRepository ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            holidayFormError.value = null
            val summary = repository.syncAllEnabledSources()
            if (summary.failed == 0) {
                saveStatus.value = if (summary.usedCache > 0) "节假日订阅已同步，部分继续使用缓存" else "节假日订阅已同步"
            }
        }
    }

    private fun validateHolidaySourceInput(name: String, url: String): String? = when {
        name.isBlank() -> "请输入订阅名称"
        url.isBlank() -> "请输入 HTTPS 订阅地址"
        !url.startsWith("https://", ignoreCase = true) -> "仅支持 HTTPS 订阅地址"
        else -> null
    }

    private data class LocalSettingsState(
        val currentSemesterName: String?,
        val semesterStartDate: LocalDate?,
        val targetWeightJin: Double?
    )

    private data class HolidaySettingsState(
        val holidaySources: List<HolidayCalendarSourceState>,
        val holidaySyncStatus: String?,
        val holidayLastSyncAt: Instant?,
        val holidayError: String?
    )
}
