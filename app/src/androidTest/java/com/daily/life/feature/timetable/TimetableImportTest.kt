package com.daily.life.feature.timetable

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.NoOpReminderScheduler
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.core.designsystem.DailyTheme
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TimetableImportTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: DailyDatabase
    private lateinit var viewModel: TimetableViewModel

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = DailyDatabase.buildInMemory(context)
        val preferences = DailyPreferences.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                File(context.cacheDir, "timetable-import-${System.nanoTime()}.preferences_pb")
            }
        )
        val semesterId = database.semesterDao().insert(
            SemesterEntity(
                id = 1L,
                name = "2026 秋季",
                startDate = LocalDate.of(2026, 9, 1),
                isCurrent = true,
                createdAt = 1L
            )
        )
        val courseId = database.courseDao().insert(
            CourseEntity(
                semesterId = semesterId,
                courseName = "旧课程",
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weekRuleText = "1周",
                parsedWeeks = setOf(1)
            )
        )
        database.courseDao().insertWeek(CourseWeekEntity(courseId, 1))
        preferences.setCurrentSemesterId(semesterId)
        viewModel = TimetableViewModel(
            repository = RoomTimetableRepository(
                database = database,
                preferences = preferences,
                reminderScheduler = NoOpReminderScheduler
            ),
            parser = PdfTimetableParser()
        )

        composeRule.setContent {
            DailyTheme {
                val state by viewModel.state.collectAsState()
                TimetableImportScreen(
                    state = state.importState,
                    onChooseFile = {},
                    onSemesterInputChange = viewModel::updateSemesterInput,
                    onRowChange = viewModel::updateImportRow,
                    onReplaceExistingChange = viewModel::updateReplaceExisting,
                    onCancel = viewModel::cancelImport,
                    onConfirm = viewModel::confirmImport
                )
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cancelKeepsExistingRowsAndConfirmReplacesThem() = runBlocking {
        showCompletePreview()
        composeRule.onNodeWithTag("timetable_import_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("旧课程"),
            database.courseDao().observeBySemester(1L).first().map(CourseEntity::courseName)
        )

        showCompletePreview()
        composeRule.onNodeWithTag("timetable_import_confirm").assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.state.value.importState.isOpen
        }

        assertEquals(
            listOf("高等数学"),
            database.courseDao().observeBySemester(1L).first().map(CourseEntity::courseName)
        )
    }

    private fun showCompletePreview() {
        composeRule.runOnIdle {
            viewModel.showImportPreview(
                "synthetic.pdf",
                TimetableParseResult(
                    courses = listOf(
                        TimetablePreviewCourse(
                            courseName = "高等数学",
                            dayOfWeek = 1,
                            startPeriod = 1,
                            endPeriod = 2,
                            weekRule = WeekRuleResult("1-4周", setOf(1, 2, 3, 4)),
                            location = "教一 A101",
                            teacher = "张老师",
                            rawRow = "高等数学 | 周一 | 1-2 | 1-4周",
                            needsReview = false
                        )
                    ),
                    warnings = emptyList(),
                    unsupportedRows = emptyList()
                )
            )
            viewModel.updateSemesterInput("2026 秋季", "2026-09-01")
        }
    }
}
