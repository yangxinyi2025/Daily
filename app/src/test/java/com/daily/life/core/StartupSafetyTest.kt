package com.daily.life.core

import org.junit.Assert.assertNull
import org.junit.Test

class StartupSafetyTest {
    @Test
    fun optionalIntegrationsAreNotCreatedByDefault() {
        val factories = AdapterFactories()
        assertNull(factories.healthConnectAdapterFactory.create())
        assertNull(factories.webDavClientFactory.create())
        assertNull(factories.deepSeekAdviceClientFactory.create())
    }
}
