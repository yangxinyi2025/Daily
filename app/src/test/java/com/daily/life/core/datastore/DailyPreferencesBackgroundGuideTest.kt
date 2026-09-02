package com.daily.life.core.datastore

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPreferencesBackgroundGuideTest {
    private val preferenceFile = File(
        System.getProperty("java.io.tmpdir"),
        "daily-background-guide-${System.nanoTime()}.preferences_pb"
    )

    @After
    fun tearDown() {
        preferenceFile.delete()
    }

    @Test
    fun backgroundRuntimeGuideIsShownUntilItIsAcknowledged() = runTest {
        val preferences = DailyPreferences.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { preferenceFile }
        )

        assertFalse(preferences.backgroundRuntimeGuideAcknowledged.first())

        preferences.setBackgroundRuntimeGuideAcknowledged()

        assertTrue(preferences.backgroundRuntimeGuideAcknowledged.first())
    }
}
