package com.daily.life.core.system

internal enum class BackgroundRuntimeSettingsAction {
    APP_DETAILS
}

internal fun backgroundRuntimeSettingsAction(apiLevel: Int): BackgroundRuntimeSettingsAction =
    BackgroundRuntimeSettingsAction.APP_DETAILS
