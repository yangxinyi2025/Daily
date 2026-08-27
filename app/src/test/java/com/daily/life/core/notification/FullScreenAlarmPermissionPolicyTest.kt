package com.daily.life.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenAlarmPermissionPolicyTest {
    @Test
    fun requiresTheDedicatedSettingsPageOnAndroid14WhenFullScreenAlarmAccessIsDenied() {
        assertEquals(
            FullScreenAlarmPermissionAction.OPEN_SETTINGS,
            fullScreenAlarmPermissionAction(apiLevel = 34, canUseFullScreenIntent = false)
        )
    }

    @Test
    fun doesNotOpenTheDedicatedSettingsPageWhenFullScreenAlarmAccessIsGranted() {
        assertEquals(
            FullScreenAlarmPermissionAction.NONE,
            fullScreenAlarmPermissionAction(apiLevel = 34, canUseFullScreenIntent = true)
        )
    }

    @Test
    fun doesNotRequireFullScreenIntentSettingsBeforeAndroid14() {
        assertEquals(
            FullScreenAlarmPermissionAction.NONE,
            fullScreenAlarmPermissionAction(apiLevel = 33, canUseFullScreenIntent = false)
        )
    }
}
