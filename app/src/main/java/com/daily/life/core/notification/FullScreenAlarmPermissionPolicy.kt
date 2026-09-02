package com.daily.life.core.notification

internal enum class FullScreenAlarmPermissionAction {
    NONE,
    OPEN_SETTINGS
}

/**
 * Android 14+ lets the user revoke full-screen notification access even when
 * an app declares USE_FULL_SCREEN_INTENT. Alarm audio must still be delivered
 * through the foreground service, while this action decides whether the app
 * needs to lead the user to the dedicated system setting for the alarm UI.
 */
internal fun fullScreenAlarmPermissionAction(
    apiLevel: Int,
    canUseFullScreenIntent: Boolean
): FullScreenAlarmPermissionAction = when {
    apiLevel < 34 -> FullScreenAlarmPermissionAction.NONE
    canUseFullScreenIntent -> FullScreenAlarmPermissionAction.NONE
    else -> FullScreenAlarmPermissionAction.OPEN_SETTINGS
}
