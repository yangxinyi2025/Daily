package com.daily.life.core

import org.junit.Assert.assertNull
import org.junit.Test

class StartupSafetyTest {
    @Test
    fun optionalIntegrationsAreNotCreatedByDefault() {
        val factories = AdapterFactories()
        assertNull(factories.reminderSchedulerFactory.create())
    }
}
