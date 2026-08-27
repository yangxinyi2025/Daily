package com.daily.life.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePresentationTest {
    @Test
    fun courseCardContentKeepsTheNextRealCourseWhenTodayHasClasses() {
        val content = courseCardContent(
            HomeState(
                todayCourses = listOf(
                    HomeCourseRow(
                        startPeriod = 3,
                        courseName = "数据库",
                        detail = "信息楼 201"
                    )
                )
            )
        )

        assertEquals("第 3 节 · 数据库", content.primaryText)
        assertEquals("信息楼 201", content.secondaryText)
        assertTrue(content.hasContent)
    }

    @Test
    fun scheduleCardContentUsesTheFigmaEmptyCopyWhenThereAreNoSchedules() {
        val content = scheduleCardContent(HomeState())

        assertEquals("今天还没有待办", content.primaryText)
        assertEquals(null, content.secondaryText)
        assertTrue(!content.hasContent)
    }
}
