package com.daily.life.core.system

internal enum class BackgroundRuntimeSettingsAction {
    REQUEST_EXEMPTION,
    APP_DETAILS
}

internal fun backgroundRuntimeSettingsAction(apiLevel: Int): BackgroundRuntimeSettingsAction =
    if (apiLevel >= 23) {
        BackgroundRuntimeSettingsAction.REQUEST_EXEMPTION
    } else {
        BackgroundRuntimeSettingsAction.APP_DETAILS
    }
