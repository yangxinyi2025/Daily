package com.daily.life.feature.settings

import java.time.LocalDate

data class SettingsState(
    val currentSemesterName: String? = null,
    val semesterStartDate: LocalDate? = null,
    val targetWeightJin: Double? = null,
    val monthlyBudgetCents: Long? = null,
    val notificationPermissionSummary: String = "通知稍后按需开启",
    val exactAlarmPermissionSummary: String = "精确提醒稍后按需开启",
    val healthPermissionSummary: String = "健康权限稍后连接",
    val syncStatus: String = "仅使用本地数据",
    val deepSeekKeySummary: String = "未保存",
    val webDavEndpoint: String? = null,
    val webDavUsernameSummary: String = "未保存",
    val webDavPasswordSummary: String = "未保存",
    val saveStatus: String? = null
)
