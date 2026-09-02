package com.daily.life.feature.home

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePresentationTest {
    @Test
    fun healthPresentationUsesWeightAndPeriodOnly() {
        val presentation = healthHomePresentation(
            HomeState(
                latestWeightJin = 104.8,
                latestPeriod = HomePeriodSummary(
                    LocalDate.of(2026, 8, 4),
                    LocalDate.of(2026, 8, 9)
                ),
                nextPeriodStart = LocalDate.of(2026, 9, 1)
            )
        )

        assertEquals("52.4", presentation.weightKg)
        assertEquals("6 天", presentation.periodValue)
        assertEquals("距离预计经期", presentation.periodCaption)
    }

    @Test
    fun emptyHealthPresentationUsesNaturalCopy() {
        val presentation = healthHomePresentation(HomeState())

        assertEquals("尚未记录", presentation.weightKg)
        assertEquals("尚未记录经期", presentation.periodValue)
    }

    @Test
    fun courseCardContentKeepsEveryCourseWhenTodayHasClasses() {
        val content = courseCardContent(
            HomeState(
                todayCourses = listOf(
                    HomeCourseRow(
                        startPeriod = 3,
                        courseName = "数据库",
                        timeLabel = "08:00-09:35",
                        detail = "信息楼 201"
                    ),
                    HomeCourseRow(
                        startPeriod = 7,
                        courseName = "体育",
                        timeLabel = "16:00-17:35",
                        detail = "操场"
                    )
                )
            )
        )

        assertEquals(
            listOf("数据库", "体育"),
            content.items.map { it.primaryText }
        )
        assertEquals(listOf("信息楼 201", "操场"), content.items.map { it.secondaryText })
        assertEquals(
            listOf("08:00-09:35", "16:00-17:35"),
            content.items.map { it.trailingText }
        )
        assertTrue(content.hasContent)
    }

    @Test
    fun scheduleCardContentKeepsEveryScheduleToday() {
        val content = scheduleCardContent(
            HomeState(
                todaySchedules = listOf(
                    HomeScheduleRow(id = 1L, title = "提交作业", timeLabel = "09:00"),
                    HomeScheduleRow(id = 2L, title = "实验课", timeLabel = "15:30")
                )
            )
        )

        assertEquals(listOf("提交作业", "实验课"), content.items.map { it.primaryText })
        assertEquals(listOf("09:00", "15:30"), content.items.map { it.secondaryText })
        assertTrue(content.hasContent)
    }

    @Test
    fun scheduleCardContentUsesTheFigmaEmptyCopyWhenThereAreNoSchedules() {
        val content = scheduleCardContent(HomeState())

        assertEquals(listOf("今天还没有待办"), content.items.map { it.primaryText })
        assertEquals(listOf(null), content.items.map { it.secondaryText })
        assertTrue(!content.hasContent)
    }
}
