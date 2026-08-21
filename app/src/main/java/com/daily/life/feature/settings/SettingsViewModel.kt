package com.daily.life.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.core.security.SecretId
import com.daily.life.core.security.SecretStore
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
    private val secretStore: SecretStore,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val secretSummaries = MutableStateFlow(SecretSummaryState())
    private val saveStatus = MutableStateFlow<String?>(null)

    private val localSettings =
        combine(
            semesterRepository.currentSemester,
            preferences.semesterStartDate,
            preferences.targetWeightJin,
            preferences.defaultBudgetCents,
            preferences.webDavEndpoint
        ) { semester, preferredStartDate, targetWeight, monthlyBudget, webDavEndpoint ->
            LocalSettingsState(
                currentSemesterName = semester.name,
                semesterStartDate = preferredStartDate ?: semester.startDate,
                targetWeightJin = targetWeight,
                monthlyBudgetCents = monthlyBudget,
                webDavEndpoint = webDavEndpoint
            )
        }

    val state: StateFlow<SettingsState> =
        combine(
            localSettings,
            secretSummaries,
            saveStatus
        ) { local, secrets, latestSaveStatus ->
            SettingsState(
                currentSemesterName = local.currentSemesterName,
                semesterStartDate = local.semesterStartDate,
                targetWeightJin = local.targetWeightJin,
                monthlyBudgetCents = local.monthlyBudgetCents,
                syncStatus = if (local.webDavEndpoint.isNullOrBlank()) {
                    "仅使用本地数据"
                } else {
                    "WebDAV 已配置，等待后续同步任务"
                },
                deepSeekKeySummary = secrets.deepSeekKeySummary,
                webDavEndpoint = local.webDavEndpoint,
                webDavUsernameSummary = secrets.webDavUsernameSummary,
                webDavPasswordSummary = secrets.webDavPasswordSummary,
                saveStatus = latestSaveStatus
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsState()
        )

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            refreshSecretSummaries()
        }
    }

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

    fun saveDeepSeekKey(value: String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (value.isBlank()) {
                secretStore.remove(SecretId.DeepSeekApiKey)
            } else {
                secretStore.put(SecretId.DeepSeekApiKey, value)
            }
            refreshSecretSummaries()
            saveStatus.value = if (value.isBlank()) "已清除 DeepSeek Key" else "DeepSeek Key 已保存"
        }
    }

    fun saveWebDavConfig(config: WebDavConfigInput) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            preferences.setWebDavEndpoint(config.endpoint.ifBlank { null })
            if (config.username.isBlank()) {
                secretStore.remove(SecretId.WebDavUsername)
            } else {
                secretStore.put(SecretId.WebDavUsername, config.username)
            }
            if (config.password.isBlank()) {
                secretStore.remove(SecretId.WebDavPassword)
            } else {
                secretStore.put(SecretId.WebDavPassword, config.password)
            }
            refreshSecretSummaries()
            saveStatus.value = "WebDAV 配置已保存"
        }
    }

    private suspend fun refreshSecretSummaries() {
        secretSummaries.value = SecretSummaryState(
            deepSeekKeySummary = if (secretStore.read(SecretId.DeepSeekApiKey).isNullOrBlank()) "未保存" else "已保存",
            webDavUsernameSummary = if (secretStore.read(SecretId.WebDavUsername).isNullOrBlank()) "未保存" else "已保存",
            webDavPasswordSummary = if (secretStore.read(SecretId.WebDavPassword).isNullOrBlank()) "未保存" else "已保存"
        )
    }

    private data class SecretSummaryState(
        val deepSeekKeySummary: String = "未保存",
        val webDavUsernameSummary: String = "未保存",
        val webDavPasswordSummary: String = "未保存"
    )

    private data class LocalSettingsState(
        val currentSemesterName: String?,
        val semesterStartDate: LocalDate?,
        val targetWeightJin: Double?,
        val monthlyBudgetCents: Long?,
        val webDavEndpoint: String?
    )
}
