package com.daily.life.feature.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.SemesterEntity
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimetableViewModel(
    private val repository: TimetableRepository,
    private val parser: TimetableParser,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.IO,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val selectedWeek = MutableStateFlow(1)
    private val importState = MutableStateFlow(TimetableImportState())
    private var previewCourses: List<TimetablePreviewCourse> = emptyList()

    val state: StateFlow<TimetableState> = combine(
        repository.currentSemester,
        selectedWeek,
        importState
    ) { semester, week, importing ->
        Query(semester, week, importing)
    }.flatMapLatest { query ->
        val semester = query.semester
        if (semester == null) {
            flowOf(createState(null, query.week, emptyList(), query.importState))
        } else {
            repository.observeCourses(semester.id, query.week).map { courses ->
                createState(semester, query.week, courses, query.importState)
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = TimetableState()
    )

    init {
        scope.launch {
            repository.currentSemester.collect { semester ->
                semester ?: return@collect
                selectedWeek.value = WeekCalculator.currentWeek(
                    semester.startDate,
                    LocalDate.now(clock)
                )
            }
        }
    }

    fun selectPreviousWeek() {
        selectedWeek.value = (selectedWeek.value - 1).coerceAtLeast(1)
    }

    fun selectNextWeek() {
        selectedWeek.value += 1
    }

    fun selectCurrentWeek() {
        selectedWeek.value = state.value.currentWeek
    }

    fun openImport() {
        val timetable = state.value
        updateImportState(
            TimetableImportState(
                isOpen = true,
                semesterName = timetable.currentSemesterName.orEmpty(),
                semesterStartDate = timetable.currentSemesterStartDate?.toString().orEmpty()
            )
        )
    }

    fun selectPdf(fileName: String, input: InputStream) {
        updateImportState(
            importState.value.copy(
                isOpen = true,
                fileName = fileName,
                isLoading = true,
                errorMessage = null
            )
        )
        scope.launch(parserDispatcher) {
            runCatching { parser.parse(input) }
                .onSuccess { result -> showImportPreview(fileName, result) }
                .onFailure { error ->
                    updateImportState(
                        importState.value.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "PDF 解析失败"
                        )
                    )
                }
        }
    }

    fun showImportPreview(fileName: String, preview: TimetableParseResult) {
        previewCourses = preview.courses
        val previous = importState.value
        updateImportState(
            previous.copy(
                isOpen = true,
                fileName = fileName,
                isLoading = false,
                previewRows = previewCourses.mapIndexed(::toRowState),
                warnings = preview.warnings,
                unsupportedRows = preview.unsupportedRows,
                errorMessage = null
            )
        )
    }

    fun updateSemesterInput(name: String, startDate: String) {
        updateImportState(
            importState.value.copy(
                semesterName = name,
                semesterStartDate = startDate
            )
        )
    }

    fun updateReplaceExisting(replaceExisting: Boolean) {
        updateImportState(importState.value.copy(replaceExisting = replaceExisting))
    }

    fun updateImportRow(row: TimetableImportRowState) {
        val existing = previewCourses.getOrNull(row.index) ?: return
        val periods = parsePeriodRange(row.periodRange)
        val weekRule = WeekRuleParser.parse(row.weekRuleText)
        val updated = existing.copy(
            courseName = row.courseName.trim(),
            dayOfWeek = parseDayOfWeek(row.dayOfWeek),
            startPeriod = periods?.first,
            endPeriod = periods?.last,
            weekRule = weekRule,
            location = row.location.trim().ifBlank { null },
            teacher = row.teacher.trim().ifBlank { null },
            needsReview = false
        ).let { course ->
            course.copy(needsReview = validationWarnings(course).isNotEmpty())
        }
        previewCourses = previewCourses.toMutableList().also { it[row.index] = updated }
        updateImportState(
            importState.value.copy(previewRows = previewCourses.mapIndexed(::toRowState))
        )
    }

    fun cancelImport() {
        previewCourses = emptyList()
        importState.value = TimetableImportState()
    }

    fun confirmImport() {
        val importing = importState.value
        val startDate = parseDate(importing.semesterStartDate) ?: return
        if (!importing.canConfirm) return
        updateImportState(importing.copy(isLoading = true, errorMessage = null))
        scope.launch {
            runCatching {
                repository.confirmImport(
                    preview = TimetableParseResult(
                        courses = previewCourses,
                        warnings = importing.warnings,
                        unsupportedRows = importing.unsupportedRows
                    ),
                    semester = SemesterInput(
                        name = importing.semesterName.trim(),
                        startDate = startDate
                    ),
                    replaceExisting = importing.replaceExisting
                )
            }.onSuccess {
                previewCourses = emptyList()
                importState.value = TimetableImportState()
            }.onFailure { error ->
                updateImportState(
                    importState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "导入失败"
                    )
                )
            }
        }
    }

    private fun createState(
        semester: SemesterEntity?,
        week: Int,
        courses: List<CourseEntity>,
        importing: TimetableImportState
    ): TimetableState {
        val currentWeek = semester?.let {
            WeekCalculator.currentWeek(it.startDate, LocalDate.now(clock))
        } ?: 1
        val coursesByDay = courses.groupBy(CourseEntity::dayOfWeek)
        return TimetableState(
            currentSemesterName = semester?.name,
            currentSemesterStartDate = semester?.startDate,
            selectedWeek = week,
            currentWeek = currentWeek,
            weekLabel = "第 $week 周",
            days = defaultTimetableDays().map { day ->
                day.copy(
                    courses = coursesByDay[day.dayOfWeek].orEmpty().map(::toCourseUiState)
                )
            },
            importState = importing,
            isEmpty = courses.isEmpty()
        )
    }

    private fun toCourseUiState(course: CourseEntity): TimetableCourseUiState =
        TimetableCourseUiState(
            id = course.id.toString(),
            courseName = course.courseName,
            dayOfWeek = course.dayOfWeek,
            startPeriod = course.startPeriod,
            endPeriod = course.endPeriod,
            weeksLabel = course.weekRuleText,
            location = course.location,
            teacher = course.teacher,
            detail = listOfNotNull(
                course.location,
                course.teacher,
                "第 ${course.startPeriod}-${course.endPeriod} 节",
                course.weekRuleText
            ).joinToString(" · ")
        )

    private fun toRowState(index: Int, course: TimetablePreviewCourse): TimetableImportRowState {
        val warnings = validationWarnings(course)
        return TimetableImportRowState(
            index = index,
            courseName = course.courseName,
            dayOfWeek = course.dayOfWeek?.let(::dayLabel).orEmpty(),
            periodRange = if (course.startPeriod == null) "" else {
                "${course.startPeriod}-${course.endPeriod ?: course.startPeriod}"
            },
            weekRuleText = course.weekRule.rawText,
            location = course.location.orEmpty(),
            teacher = course.teacher.orEmpty(),
            needsReview = warnings.isNotEmpty(),
            warnings = warnings,
            rawRow = course.rawRow
        )
    }

    private fun validationWarnings(course: TimetablePreviewCourse): List<String> = buildList {
        if (course.courseName.isBlank()) add("缺少课程名")
        if (course.dayOfWeek == null) add("缺少或无法识别星期")
        if (course.startPeriod == null || course.endPeriod == null) add("缺少或无法识别节次")
        if (course.weekRule.weeks.isEmpty() && course.weekRule.parity == null) add("缺少或无法识别周次")
        addAll(course.weekRule.warnings)
    }.distinct()

    private fun updateImportState(value: TimetableImportState) {
        importState.value = value.copy(
            canConfirm = !value.isLoading &&
                value.semesterName.isNotBlank() &&
                parseDate(value.semesterStartDate) != null &&
                previewCourses.isNotEmpty() &&
                previewCourses.all { course ->
                    course.courseName.isNotBlank() &&
                        course.dayOfWeek != null &&
                        course.startPeriod != null &&
                        course.endPeriod != null &&
                        (course.weekRule.weeks.isNotEmpty() || course.weekRule.parity != null)
                }
        )
    }

    private fun parseDayOfWeek(raw: String): Int? = when (raw.trim().lowercase()) {
        "1", "一", "周一", "星期一", "monday", "mon" -> 1
        "2", "二", "周二", "星期二", "tuesday", "tue" -> 2
        "3", "三", "周三", "星期三", "wednesday", "wed" -> 3
        "4", "四", "周四", "星期四", "thursday", "thu" -> 4
        "5", "五", "周五", "星期五", "friday", "fri" -> 5
        "6", "六", "周六", "星期六", "saturday", "sat" -> 6
        "7", "日", "天", "周日", "星期日", "sunday", "sun" -> 7
        else -> null
    }

    private fun parsePeriodRange(raw: String): IntRange? {
        val numbers = Regex("""\d+""").findAll(raw).map { it.value.toInt() }.toList()
        if (numbers.isEmpty()) return null
        val start = numbers.first()
        val end = numbers.getOrElse(1) { start }
        return minOf(start, end)..maxOf(start, end)
    }

    private fun parseDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    private fun dayLabel(day: Int): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day - 1]

    private data class Query(
        val semester: SemesterEntity?,
        val week: Int,
        val importState: TimetableImportState
    )
}
