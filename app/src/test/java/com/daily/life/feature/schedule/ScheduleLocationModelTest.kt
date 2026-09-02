package com.daily.life.feature.schedule

import com.daily.life.core.database.ReminderMode
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleLocationModelTest {
    @Test
    fun editorLocationPersistsThroughScheduleEventAndEntity() {
        val now = Instant.parse("2026-09-01T00:00:00Z")
        val event = ScheduleEditorState(
            title = "讲座",
            date = "2026-09-01",
            time = "14:00",
            reminderMode = ReminderMode.NOTIFICATION,
            location = "教学楼 A201"
        ).toEvent(now, ZoneId.of("Asia/Shanghai"))

        assertEquals("教学楼 A201", event.location)
        assertEquals("教学楼 A201", event.toEntity().location)
        assertEquals("教学楼 A201", event.toEntity().toModel().location)
    }

    @Test
    fun blankEditorLocationPersistsAsNull() {
        val event = ScheduleEditorState(
            title = "课后作业",
            date = "2026-09-01",
            time = "18:00",
            location = "   "
        ).toEvent(Instant.parse("2026-09-01T00:00:00Z"), ZoneId.of("Asia/Shanghai"))

        assertNull(event.location)
    }
}
