package com.daily.life.feature.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.calendar.CalendarDayRule
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.HolidayCalendarRepository
import com.daily.life.core.calendar.SystemCalendarScheduleReader
import com.daily.life.core.calendar.SystemCalendarSpecialDay
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.database.SemesterClassOverrideEntity
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimetableViewModel(
    private val repository: TimetableRepository,
    private val parser: TimetableParser,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val calendarReader: SystemCalendarScheduleReader? = null,
    private val holidayCalendarRepository: HolidayCalendarRepository? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val selectedWeek = MutableStateFlow(1)
    private val importState = MutableStateFlow(TimetableImportState())
    private val periodEditor = MutableStateFlow(TimetablePeriodEditorState())
    private val calendarSpecialDays = MutableStateFlow<List<SystemCalendarSpecialDay>>(emptyList())
    private val calendarReadWarning = MutableStateFlow<String?>(null)
    private val holidayCalendarRules = MutableStateFlow<List<CalendarDayRule>>(emptyList())
    private val classOverrides = MutableStateFlow<Map<LocalDate, ClassOverride>>(emptyMap())
    private var previewCourses: List<TimetablePreviewCourse> = emptyList()

    private val query: Flow<Query> = combine(
        combine(
            repository.currentSemester,
            selectedWeek,
            importState,
            periodEditor
        ) { semester, week, importing, editor -> Query(semester, week, importing, editor) },
        calendarSpecialDays,
        calendarReadWarning
    ) { query, specialDays, readWarning ->
        query.copy(calendarSpecialDays = specialDays, calendarReadWarning = readWarning)
    }

    val state: StateFlow<TimetableState> = query.flatMapLatest { query ->
        val semester = query.semester
        if (semester == null) {
            flowOf(
                createState(
                    semester = null,
                    week = query.week,
                    courses = emptyList(),
                    periodTimes = defaultSemesterPeriodTimes(),
                    importing = query.importState,
                    editor = query.periodEditor,
                    specialDays = query.calendarSpecialDays,
                    confirmedAdjustments = emptyMap(),
                    calendarReadWarning = query.calendarReadWarning
                )
            )
        } else {
            combine(
                repository.observeCourses(semester.id, query.week),
                repository.observePeriodTimes(semester.id),
                repository.observeCalendarAdjustments(semester.id),
                repository.observeClassOverrides(semester.id),
                holidayCalendarRules
            ) { courses, periodTimes, adjustments, overrides, rules ->
                createState(
                    semester = semester,
                    week = query.week,
                    courses = courses,
                    periodTimes = periodTimes,
                    importing = query.importState,
                    editor = query.periodEditor,
                    specialDays = query.calendarSpecialDays,
                    confirmedAdjustments = adjustments.associate { it.actualDate to it.sourceDayOfWeek },
                    holidayRules = rules,
                    classOverrides = overrides.mapNotNull { row ->
                        runCatching { row.actualDate to ClassOverride.valueOf(row.overrideKind) }.getOrNull()
                    }.toMap(),
                    calendarReadWarning = query.calendarReadWarning
                )
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
                    currentDate()
                )
                refreshCalendarDays(semester.startDate, semester.endDate)
                refreshHolidayCalendarRules(semester.startDate, semester.endDate)
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
        refreshImportCalendarDays()
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
                periodTimes = defaultSemesterPeriodTimes().map { time ->
                    preview.parsedPeriodTimes[time.period]?.let { parsed ->
                        TimetablePeriodTimeRowState(parsed.period, parsed.startTime.toString(), parsed.endTime.toString())
                    } ?: TimetablePeriodTimeRowState(time.period, time.startTime.toString(), time.endTime.toString())
                },
                periodTimesDetectedFromPdf = preview.parsedPeriodTimes.isNotEmpty(),
                errorMessage = null
            )
        )
        refreshImportCalendarDays()
    }

    fun updateSemesterInput(name: String, startDate: String) {
        updateImportState(
            importState.value.copy(
                semesterName = name,
                semesterStartDate = startDate
            )
        )
        refreshImportCalendarDays()
    }

    fun refreshSystemCalendarDays() {
        scope.launch {
            val semester = repository.currentSemester.first() ?: return@launch
            refreshCalendarDays(
                startDate = semester.startDate,
                endDate = semester.endDate,
                updateImportState = importState.value.isOpen
            )
        }
    }

    fun updateMakeupSource(actualDate: LocalDate, sourceDayOfWeek: Int?) {
        val choices = importState.value.calendarAdjustmentChoices.map { choice ->
            if (choice.actualDate == actualDate) choice.copy(selectedSourceDayOfWeek = sourceDayOfWeek) else choice
        }
        updateImportState(importState.value.copy(calendarAdjustmentChoices = choices))
    }

    fun updateClassOverride(actualDate: LocalDate, override: ClassOverride) {
        val current = importState.value.classOverrideChoices.filterNot { it.actualDate == actualDate }
        updateImportState(
            importState.value.copy(
                classOverrideChoices = if (override == ClassOverride.FOLLOW_CALENDAR) current
                else current + TimetableClassOverrideState(actualDate, override)
            )
        )
    }

    fun updateReplaceExisting(replaceExisting: Boolean) {
        updateImportState(importState.value.copy(replaceExisting = replaceExisting))
    }

    fun updateImportPeriodTime(row: TimetablePeriodTimeRowState) {
        val rows = importState.value.periodTimes.toMutableList()
        val index = rows.indexOfFirst { it.period == row.period }
        if (index < 0) return
        rows[index] = row
        updateImportState(importState.value.copy(periodTimes = rows))
    }

    fun openPeriodEditor() {
        periodEditor.value = TimetablePeriodEditorState(
            isOpen = true,
            rows = state.value.periodTimes.map { TimetablePeriodTimeRowState(it.period, it.startTime.toString(), it.endTime.toString()) }
        )
    }

    fun closePeriodEditor() {
        periodEditor.value = TimetablePeriodEditorState()
    }

    fun updatePeriodEditorRow(row: TimetablePeriodTimeRowState) {
        periodEditor.value = periodEditor.value.copy(rows = periodEditor.value.rows.map { if (it.period == row.period) row else it })
    }

    fun restoreDefaultPeriodTimes() {
        periodEditor.value = periodEditor.value.copy(rows = defaultSemesterPeriodTimes().map {
            TimetablePeriodTimeRowState(it.period, it.startTime.toString(), it.endTime.toString())
        })
    }

    fun savePeriodTimes() {
        val rows = periodEditor.value.rows
        val times = importPeriodTimes(rows)
        if (times == null || validateSemesterPeriodTimes(times) != null) {
            periodEditor.value = periodEditor.value.copy(errorMessage = "请检查节次时间")
            return
        }
        scope.launch {
            val semester = repository.currentSemester.first() ?: return@launch
            runCatching { repository.updatePeriodTimes(semester.id, times) }
                .onSuccess { closePeriodEditor() }
                .onFailure { error -> periodEditor.value = periodEditor.value.copy(errorMessage = error.message ?: "保存失败") }
        }
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
                    replaceExisting = importing.replaceExisting,
                    periodTimes = importing.periodTimes.map {
                        SemesterPeriodTime(it.period, LocalTime.parse(it.start), LocalTime.parse(it.end))
                    },
                    calendarAdjustments = importing.calendarAdjustmentChoices.toAdjustmentInputs(),
                    classOverrides = importing.classOverrideChoices.map {
                        SemesterClassOverrideInput(it.actualDate, it.override)
                    }
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
        periodTimes: List<SemesterPeriodTime>,
        importing: TimetableImportState,
        editor: TimetablePeriodEditorState,
        specialDays: List<SystemCalendarSpecialDay> = emptyList(),
        confirmedAdjustments: Map<LocalDate, Int> = emptyMap(),
        holidayRules: List<CalendarDayRule> = emptyList(),
        classOverrides: Map<LocalDate, ClassOverride> = emptyMap(),
        calendarReadWarning: String? = null
    ): TimetableState {
        val currentWeek = semester?.let {
            WeekCalculator.currentWeek(it.startDate, currentDate())
        } ?: 1
        val coursesByDay = courses.groupBy(CourseEntity::dayOfWeek)
        val visibleDays = semester?.let { timetableDaysForWeek(it.startDate, week) }
            ?.mapIndexed { index, day ->
                val slot = if (holidayRules.isNotEmpty()) {
                    mapWeekToScheduleSlotsWithRules(semester.startDate, week, holidayRules, confirmedAdjustments, classOverrides)[index]
                } else {
                    mapWeekToScheduleSlots(semester.startDate, week, specialDays, confirmedAdjustments)[index]
                }
                day.copy(
                    courses = slot.courseDayOfWeek?.let(coursesByDay::get).orEmpty().map(::toCourseUiState)
                )
            }
            .orEmpty()
        val needsMakeupConfirmation = semester?.let {
            if (holidayRules.isNotEmpty()) {
                mapWeekToScheduleSlotsWithRules(it.startDate, week, holidayRules, confirmedAdjustments, classOverrides)
            } else {
                mapWeekToScheduleSlots(it.startDate, week, specialDays, confirmedAdjustments)
            }
                .any(TimetableScheduleSlot::needsMakeupConfirmation)
        } == true
        return TimetableState(
            currentSemesterName = semester?.name,
            currentSemesterStartDate = semester?.startDate,
            selectedWeek = week,
            currentWeek = currentWeek,
            weekLabel = "第 $week 周",
            timeLabels = periodLabels(periodTimes),
            periodTimes = periodTimes,
            days = visibleDays.ifEmpty { defaultTimetableDays() },
            importState = importing,
            periodEditor = editor,
            calendarSpecialDays = specialDays,
            classOverrides = classOverrides,
            calendarAdjustmentWarning = when {
                needsMakeupConfirmation -> "检测到调休日期，请重新导入课表确认补课来源。"
                else -> calendarReadWarning
            },
            isEmpty = visibleDays.none { it.courses.isNotEmpty() }
        )
    }

    private fun refreshImportCalendarDays() {
        val startDate = parseDate(importState.value.semesterStartDate) ?: return
        refreshHolidayCalendarRules(
            startDate,
            startDate.plusWeeks(DEFAULT_IMPORT_SEMESTER_WEEKS.toLong()).minusDays(1)
        )
        scope.launch {
            refreshCalendarDays(
                startDate = startDate,
                endDate = startDate.plusWeeks(DEFAULT_IMPORT_SEMESTER_WEEKS.toLong()).minusDays(1),
                updateImportState = true
            )
        }
    }

    private fun refreshCalendarDays(startDate: LocalDate, endDate: LocalDate?, updateImportState: Boolean = false) {
        val reader = calendarReader ?: return
        scope.launch {
            val rangeEnd = endDate ?: startDate.plusWeeks(DEFAULT_IMPORT_SEMESTER_WEEKS.toLong()).minusDays(1)
            val days = reader.readBetween(startDate, rangeEnd)
            calendarSpecialDays.value = days
            calendarReadWarning.value = if (reader.hasReadPermission()) null else {
                "未获得系统日历读取权限，无法校准节假日和调休。"
            }
            if (updateImportState) {
                val current = importState.value
                val choices = buildTimetableAdjustmentChoices(days, current.calendarAdjustmentChoices)
                val overrideDates = days.map { it.date }.distinct()
                val classChoices = overrideDates.map { date ->
                    current.classOverrideChoices.firstOrNull { it.actualDate == date }
                        ?: TimetableClassOverrideState(date)
                }
                updateImportState(
                    current.copy(
                        calendarSpecialDays = days,
                        calendarAdjustmentChoices = choices,
                        classOverrideChoices = classChoices,
                        calendarReadWarning = calendarReadWarning.value
                    )
                )
            }
        }
    }

    private fun refreshHolidayCalendarRules(startDate: LocalDate, endDate: LocalDate?) {
        val repository = holidayCalendarRepository ?: return
        scope.launch {
            repository.initialize()
            val rules = repository.resolveBetween(
                startDate,
                endDate ?: startDate.plusWeeks(DEFAULT_IMPORT_SEMESTER_WEEKS.toLong()).minusDays(1)
            )
            holidayCalendarRules.value = rules
            if (importState.value.isOpen) {
                val current = importState.value
                val specialDays = rules.filter { it.kind == CalendarDayKind.HOLIDAY_REST || it.kind == CalendarDayKind.MAKEUP_WORKDAY }
                    .map { rule ->
                        SystemCalendarSpecialDay(
                            date = rule.date,
                            kind = if (rule.kind == CalendarDayKind.HOLIDAY_REST) {
                                com.daily.life.core.calendar.SystemCalendarSpecialDayKind.Holiday
                            } else {
                                com.daily.life.core.calendar.SystemCalendarSpecialDayKind.MakeupWorkday
                            },
                            sourceDayOfWeek = rule.sourceDayOfWeek,
                            sourceDate = rule.sourceDate,
                            label = rule.label.orEmpty()
                        )
                    }
                val mergedDays = (current.calendarSpecialDays + specialDays).distinctBy { it.date to it.kind }
                val choices = buildTimetableAdjustmentChoices(mergedDays, current.calendarAdjustmentChoices)
                val overrideDates = mergedDays.map { it.date }.distinct()
                val classChoices = overrideDates.map { date ->
                    current.classOverrideChoices.firstOrNull { it.actualDate == date }
                        ?: TimetableClassOverrideState(date)
                }
                updateImportState(current.copy(calendarSpecialDays = mergedDays, calendarAdjustmentChoices = choices, classOverrideChoices = classChoices))
            }
        }
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
                } &&
                importPeriodTimes(value.periodTimes) != null &&
                validateSemesterPeriodTimes(importPeriodTimes(value.periodTimes).orEmpty()) == null
        )
    }

    private fun currentDate(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    private fun importPeriodTimes(rows: List<TimetablePeriodTimeRowState>): List<SemesterPeriodTime>? =
        runCatching {
            rows.map { SemesterPeriodTime(it.period, LocalTime.parse(it.start), LocalTime.parse(it.end)) }
        }.getOrNull()

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
        val importState: TimetableImportState,
        val periodEditor: TimetablePeriodEditorState,
        val calendarSpecialDays: List<SystemCalendarSpecialDay> = emptyList(),
        val calendarReadWarning: String? = null
    )

    private companion object {
        const val DEFAULT_IMPORT_SEMESTER_WEEKS = 20
    }
}
