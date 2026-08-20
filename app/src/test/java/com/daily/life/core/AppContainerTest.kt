package com.daily.life.core

import org.junit.Assert.assertNull
import org.junit.Test

class AppContainerTest {
    @Test
    fun unavailableFactoriesReturnNullInsteadOfThrowing() {
        assertNull(RepositoryFactories().timetableRepositoryFactory.create())
        assertNull(AdapterFactories().deepSeekAdviceClientFactory.create())
    }
}
