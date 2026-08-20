package com.daily.life

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationIdentityIsDaily() {
        assertEquals("com.daily.life", BuildConfig.APPLICATION_ID)
    }
}
