package com.daily.life.core.system

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRuntimeSettingsPolicyTest {
    @Test
    fun alwaysOpensTheAppDetailsPageInsteadOfRequestingAnExemption() {
        assertEquals(
            BackgroundRuntimeSettingsAction.APP_DETAILS,
            backgroundRuntimeSettingsAction(apiLevel = 23)
        )
    }

    @Test
    fun usesAppDetailsAsFallbackBeforeAndroidMarshmallow() {
        assertEquals(
            BackgroundRuntimeSettingsAction.APP_DETAILS,
            backgroundRuntimeSettingsAction(apiLevel = 22)
        )
    }
}
