package com.daily.life.core.notification

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmManifestTest {
    @Test
    fun declaresTheUserGrantableExactAlarmPermissionForModernAndroid() {
        val manifest = findManifest().readText()

        assertTrue(
            "Daily must request SCHEDULE_EXACT_ALARM on Android 12+ so the system settings page can grant it.",
            manifest.contains("<uses-permission android:name=\"android.permission.SCHEDULE_EXACT_ALARM\" />")
        )
        assertFalse(
            "USE_EXACT_ALARM is not the user-grantable permission used by Daily's alarm setting.",
            manifest.contains("android.permission.USE_EXACT_ALARM")
        )
    }

    private fun findManifest(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .map { File(it, "app/src/main/AndroidManifest.xml") }
        .first { it.isFile }
}
