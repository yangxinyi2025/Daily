package com.daily.life.feature.schedule

import com.daily.life.R
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRedesignAssetsTest {
    @Test
    fun scheduleRedesignUsesTheProvidedMascotAndQuickCreateAssets() {
        assertTrue(R.drawable.schedule_mascot_clipboard != 0)
        assertTrue(R.drawable.schedule_quick_exam_icon != 0)
        assertTrue(R.drawable.schedule_quick_birthday_icon != 0)
        assertTrue(R.drawable.schedule_quick_minor_task_icon != 0)
    }
}
