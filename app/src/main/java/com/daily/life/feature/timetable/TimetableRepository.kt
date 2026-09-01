package com.daily.life.feature.timetable

import androidx.room.withTransaction
import com.daily.life.core.ReminderScheduler
import com.daily.life.core.calendar.CalendarReminderSyncer
import com.daily.life.core.calendar.CalendarGatewayResult
import com.daily.life.core.calendar.HolidayCalendarRepository
import com.daily.life.core.calendar.SystemCalendarScheduleReader
import com.daily.life.core.database.CourseReminderMode
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.database.SemesterClassOverrideEntity
import com.daily.life.core.datastore.DailyPreferences
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

sealed interface CourseMutationResult {
    data class Saved(val courseId: Long, val reminderSyncFailed: Boolean = false) : CourseMutationResult
    data class Rejected(val message: String) : CourseMutationResult
}

interface TimetableRepository {
    val currentSemester: Flow<SemesterEntity?>

    fun observeCourses(semesterId: Long, week: Int): Flow<List<CourseEntity>>

    fun observePeriodTimes(semesterId: Long): Flow<List<SemesterPeriodTime>>

    fun observeCalendarAdjustments(semesterId: Long): Flow<List<com.daily.life.core.database.SemesterCalendarAdjustmentEntity>>

    fun observeClassOverrides(semesterId: Long): Flow<List<SemesterClassOverrideEntity>> = flowOf(emptyList())

    suspend fun updatePeriodTimes(semesterId: Long, times: List<SemesterPeriodTime>)

    suspend fun findCourse(courseId: Long): com.daily.life.core.database.CourseWithWeeks? = null

    suspend fun saveCourse(draft: TimetableCourseDraft): CourseMutationResult =
        CourseMutationResult.Rejected("当前课表不支持手动保存课程")

    suspend fun deleteCourse(semesterId: Long, courseId: Long): CourseMutationResult =
        CourseMutationResult.Rejected("当前课表不支持删除课程")

    suspend fun retryCourseReminderSync(semesterId: Long): CourseMutationResult =
        CourseMutationResult.Rejected("当前课表不支持重试课程提醒")

    suspend fun confirmImport(
        preview: TimetableParseResult,
        semester: SemesterInput,
        replaceExisting: Boolean,
        periodTimes: List<SemesterPeriodTime> = defaultSemesterPeriodTimes(),
        calendarAdjustments: List<SemesterCalendarAdjustmentInput> = emptyList()
    ): Long

    suspend fun confirmImport(
        preview: TimetableParseResult,
        semester: SemesterInput,
        replaceExisting: Boolean,
        periodTimes: List<SemesterPeriodTime> = defaultSemesterPeriodTimes(),
        calendarAdjustments: List<SemesterCalendarAdjustmentInput> = emptyList(),
        classOverrides: List<SemesterClassOverrideInput> = emptyList()
    ): Long = confirmImport(preview, semester, replaceExisting, periodTimes, calendarAdjustments)
}

class RoomTimetableRepository(
    private val database: DailyDatabase,
    private val preferences: DailyPreferences,
    private val reminderScheduler: ReminderScheduler,
    private val calendarReminderSyncer: CalendarReminderSyncer? = null,
    private val calendarScheduleReader: SystemCalendarScheduleReader? = null,
    private val holidayCalendarRepository: HolidayCalendarRepository? = null,
    private val clock: Clock = Clock.systemDefaultZone()
) : TimetableRepository {
    private val semesterDao = database.semesterDao()
    private val courseDao = database.courseDao()
    private val semesterPeriodDao = database.semesterPeriodDao()
    private val calendarAdjustmentDao = database.semesterCalendarAdjustmentDao()
    private val classOverrideDao = database.semesterClassOverrideDao()

    override val currentSemester: Flow<SemesterEntity?> =
        combine(preferences.currentSemesterId, semesterDao.observeAll()) { preferredId, semesters ->
            semesters.firstOrNull { it.id == preferredId }
                ?: semesters.firstOrNull { it.isCurrent }
        }

    override fun observeCourses(semesterId: Long, week: Int): Flow<List<CourseEntity>> =
        courseDao.observeBySemesterWeek(semesterId, week)

    override fun observePeriodTimes(semesterId: Long): Flow<List<SemesterPeriodTime>> = flow {
        database.withTransaction {
            if (semesterPeriodDao.findBySemester(semesterId).isEmpty()) {
                semesterPeriodDao.insertAll(defaultSemesterPeriodTimes().map {
                    com.daily.life.core.database.SemesterPeriodEntity(
                        semesterId = semesterId,
                        period = it.period,
                        startTime = it.startTime,
                        endTime = it.endTime
                    )
                })
            }
        }
        emitAll(semesterPeriodDao.observeBySemester(semesterId).map { rows ->
            rows.map { SemesterPeriodTime(it.period, it.startTime, it.endTime) }
        })
    }

    override fun observeCalendarAdjustments(semesterId: Long): Flow<List<com.daily.life.core.database.SemesterCalendarAdjustmentEntity>> =
        calendarAdjustmentDao.observeBySemester(semesterId)

    override fun observeClassOverrides(semesterId: Long): Flow<List<SemesterClassOverrideEntity>> =
        classOverrideDao.observeBySemester(semesterId)

    override suspend fun findCourse(courseId: Long): com.daily.life.core.database.CourseWithWeeks? =
        courseDao.findWithWeeksById(courseId)

    override suspend fun saveCourse(draft: TimetableCourseDraft): CourseMutationResult {
        val validation = validateTimetableCourseDraft(draft)
        val weeks = (validation as? CourseDraftValidation.Valid)?.parsedWeeks
            ?: return CourseMutationResult.Rejected((validation as CourseDraftValidation.Invalid).message)
        val stored = if (draft.id == null) null else courseDao.findById(draft.id)
        if (draft.id != null && stored == null) {
            return CourseMutationResult.Rejected("课程不存在，无法保存修改")
        }
        if (stored != null && stored.semesterId != draft.semesterId) {
            return CourseMutationResult.Rejected("课程不属于当前学期")
        }
        val existingCourses = courseDao.findBySemester(draft.semesterId)
        val conflict = existingCourses
            .sortedWith(compareBy(CourseEntity::dayOfWeek, CourseEntity::startPeriod, CourseEntity::id))
            .firstOrNull { existing ->
                val existingWeeks = courseDao.findWithWeeksById(existing.id)?.weeks
                    ?.map(CourseWeekEntity::week)?.toSet().orEmpty()
                coursesConflict(draft, weeks, existing, existingWeeks)
            }
        if (conflict != null) {
            val period = maxOf(draft.startPeriod, conflict.startPeriod)
            return CourseMutationResult.Rejected("与“${conflict.courseName}”在${courseDayLabel(draft.dayOfWeek)}第 $period 节冲突")
        }
        val courseId = database.withTransaction {
            val entity = (stored ?: CourseEntity(semesterId = draft.semesterId, courseName = "", dayOfWeek = 1, startPeriod = 1, endPeriod = 1, weekRuleText = "", parsedWeeks = emptySet())).copy(
                semesterId = draft.semesterId,
                courseName = draft.courseName.trim(),
                dayOfWeek = draft.dayOfWeek,
                startPeriod = draft.startPeriod,
                endPeriod = draft.endPeriod,
                weekRuleText = draft.weekRuleText.trim(),
                parsedWeeks = weeks,
                location = draft.location.trim().ifBlank { null },
                teacher = draft.teacher.trim().ifBlank { null },
                notes = draft.notes.trim().ifBlank { null }
            )
            val id = if (stored == null) courseDao.insert(entity) else {
                courseDao.update(entity)
                entity.id
            }
            courseDao.deleteWeeksByCourseId(id)
            courseDao.insertWeeks(weeks.sorted().map { CourseWeekEntity(id, it) })
            id
        }
        val reminderSyncFailed = runCatching {
            refreshCourseReminders(draft.semesterId, draft.id?.let(::listOf).orEmpty()).not()
        }.getOrDefault(true)
        return CourseMutationResult.Saved(courseId, reminderSyncFailed)
    }

    override suspend fun deleteCourse(semesterId: Long, courseId: Long): CourseMutationResult {
        val course = courseDao.findById(courseId)
            ?: return CourseMutationResult.Rejected("课程不存在")
        if (course.semesterId != semesterId) {
            return CourseMutationResult.Rejected("课程不属于当前学期")
        }
        database.withTransaction {
            courseDao.deleteById(courseId)
        }
        val reminderSyncFailed = runCatching {
            refreshCourseReminders(semesterId, listOf(courseId)).not()
        }.getOrDefault(true)
        return CourseMutationResult.Saved(courseId, reminderSyncFailed)
    }

    override suspend fun retryCourseReminderSync(semesterId: Long): CourseMutationResult {
        val synced = runCatching {
            val syncer = calendarReminderSyncer
            if (syncer == null) {
                reminderScheduler.scheduleCourseReminders(semesterId)
                true
            } else {
                val cleanup = syncer.deleteAllCourseOccurrences()
                val resync = syncFutureCourseCalendarMessages(semesterId)
                (cleanup + resync).all { it is CalendarGatewayResult.Synced }
            }
        }.getOrDefault(false)
        return CourseMutationResult.Saved(0L, reminderSyncFailed = !synced)
    }

    private suspend fun refreshCourseReminders(semesterId: Long, staleCourseIds: List<Long>): Boolean {
        val syncer = calendarReminderSyncer
        if (syncer == null) {
            reminderScheduler.scheduleCourseReminders(semesterId)
            return true
        }
        val cleanup = syncer.deleteCourseOccurrences(staleCourseIds)
        val resync = syncFutureCourseCalendarMessages(semesterId)
        return (cleanup + resync).all { it is CalendarGatewayResult.Synced }
    }

    private fun courseDayLabel(dayOfWeek: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(dayOfWeek - 1) { "星期$dayOfWeek" }

    override suspend fun updatePeriodTimes(semesterId: Long, times: List<SemesterPeriodTime>) {
        require(validateSemesterPeriodTimes(times) == null) { "节次时间不正确" }
        calendarReminderSyncer?.let { syncer ->
            val courseIds = courseDao.findBySemester(semesterId).map(CourseEntity::id)
            val cleanup = syncer.deleteCourseOccurrences(courseIds)
            require(cleanup.all { it is CalendarGatewayResult.Synced }) {
                "无法更新课程消息提醒，请检查日历权限后重试"
            }
            val resync = syncFutureCourseCalendarMessages(semesterId, times)
            require(resync.all { it is CalendarGatewayResult.Synced }) {
                "无法更新课程消息提醒，请检查日历权限后重试"
            }
        }
        database.withTransaction {
            semesterPeriodDao.deleteBySemester(semesterId)
            semesterPeriodDao.insertAll(times.map {
                com.daily.life.core.database.SemesterPeriodEntity(semesterId, it.period, it.startTime, it.endTime)
            })
        }
    }

    override suspend fun confirmImport(
        preview: TimetableParseResult,
        semester: SemesterInput,
        replaceExisting: Boolean,
        periodTimes: List<SemesterPeriodTime>,
        calendarAdjustments: List<SemesterCalendarAdjustmentInput>
    ): Long = confirmImport(
        preview,
        semester,
        replaceExisting,
        periodTimes,
        calendarAdjustments,
        emptyList()
    )

    override suspend fun confirmImport(
        preview: TimetableParseResult,
        semester: SemesterInput,
        replaceExisting: Boolean,
        periodTimes: List<SemesterPeriodTime>,
        calendarAdjustments: List<SemesterCalendarAdjustmentInput>,
        classOverrides: List<SemesterClassOverrideInput>
    ): Long {
        require(semester.name.isNotBlank()) { "学期名称不能为空" }
        require(semester.endDate == null || !semester.endDate.isBefore(semester.startDate)) {
            "学期结束日期不能早于开始日期"
        }
        val preparedCourses = preview.courses.map { course ->
            val day = requireNotNull(course.dayOfWeek) { "${course.courseName}: 缺少星期" }
            val startPeriod = requireNotNull(course.startPeriod) { "${course.courseName}: 缺少开始节次" }
            val endPeriod = requireNotNull(course.endPeriod) { "${course.courseName}: 缺少结束节次" }
            require(course.courseName.isNotBlank()) { "课程名称不能为空" }
            require(day in 1..7) { "${course.courseName}: 星期超出范围" }
            require(startPeriod in 1..12 && endPeriod in startPeriod..12) {
                "${course.courseName}: 节次超出范围"
            }
            val weeks = resolveWeeks(course.weekRule, semester)
            require(weeks.isNotEmpty()) { "${course.courseName}: 未解析出可导入周次" }
            PreparedCourse(course, day, startPeriod, endPeriod, weeks)
        }
        require(preparedCourses.isNotEmpty()) { "没有可导入的课程" }
        require(validateSemesterPeriodTimes(periodTimes) == null) { "节次时间不正确" }
        require(calendarAdjustments.all { it.sourceDayOfWeek in 1..7 }) { "调休补课来源星期不正确" }
        val effectiveEndDate = semester.endDate
            ?: semester.startDate.plusWeeks(DEFAULT_SEMESTER_WEEKS.toLong()).minusDays(1)
        require(classOverrides.all { override ->
            !override.actualDate.isBefore(semester.startDate) &&
                !override.actualDate.isAfter(effectiveEndDate)
        }) { "课程覆盖日期必须在学期范围内" }

        if (replaceExisting) {
            val existingSemester = semesterDao.findByName(semester.name)
            val existingCourseIds = existingSemester
                ?.let { courseDao.findBySemester(it.id).map(CourseEntity::id) }
                .orEmpty()
            val cleanupResults = calendarReminderSyncer
                ?.deleteCourseOccurrences(existingCourseIds)
                .orEmpty()
            require(cleanupResults.all { it is CalendarGatewayResult.Synced }) {
                "无法清理旧课表的系统日历提醒，请检查日历权限后重试"
            }
        }

        val semesterId = database.withTransaction {
            val existing = semesterDao.findByName(semester.name)
            if (semester.isCurrent) semesterDao.clearCurrent()
            val id = if (existing == null) {
                semesterDao.insert(
                    SemesterEntity(
                        name = semester.name,
                        startDate = semester.startDate,
                        endDate = semester.endDate,
                        isCurrent = semester.isCurrent,
                        createdAt = clock.millis()
                    )
                )
            } else {
                semesterDao.update(
                    existing.copy(
                        startDate = semester.startDate,
                        endDate = semester.endDate,
                        isCurrent = semester.isCurrent
                    )
                )
                existing.id
            }

            if (replaceExisting) courseDao.deleteBySemester(id)
            if (replaceExisting) calendarAdjustmentDao.deleteBySemester(id)
            if (replaceExisting) classOverrideDao.deleteBySemester(id)
            semesterPeriodDao.deleteBySemester(id)
            semesterPeriodDao.insertAll(periodTimes.map {
                com.daily.life.core.database.SemesterPeriodEntity(id, it.period, it.startTime, it.endTime)
            })
            preparedCourses.forEach { prepared ->
                val course = prepared.preview
                val courseId = courseDao.insert(
                    CourseEntity(
                        semesterId = id,
                        courseName = course.courseName,
                        dayOfWeek = prepared.dayOfWeek,
                        startPeriod = prepared.startPeriod,
                        endPeriod = prepared.endPeriod,
                        weekRuleText = course.weekRule.rawText,
                        parsedWeeks = prepared.weeks,
                        campus = course.campus,
                        location = course.location,
                        teacher = course.teacher,
                        courseCode = course.courseCode,
                        credits = course.credits,
                        notes = course.notes
                    )
                )
                courseDao.insertWeeks(prepared.weeks.map { week -> CourseWeekEntity(courseId, week) })
            }
            calendarAdjustmentDao.upsertAll(calendarAdjustments.map { adjustment ->
                com.daily.life.core.database.SemesterCalendarAdjustmentEntity(
                    semesterId = id,
                    actualDate = adjustment.actualDate,
                    sourceDayOfWeek = adjustment.sourceDayOfWeek,
                    sourceWeekParity = adjustment.sourceWeekParity.name,
                    sourceDate = adjustment.sourceDate,
                    sourceLabel = adjustment.sourceLabel,
                    updatedAt = clock.millis()
                )
            })
            classOverrideDao.upsertAll(classOverrides.map { override ->
                SemesterClassOverrideEntity(
                    semesterId = id,
                    actualDate = override.actualDate,
                    overrideKind = override.override.name,
                    updatedAt = clock.millis()
                )
            })
            id
        }

        if (semester.isCurrent) preferences.setCurrentSemesterId(semesterId)
        if (calendarReminderSyncer == null) {
            reminderScheduler.scheduleCourseReminders(semesterId)
        } else {
            syncFutureCourseCalendarMessages(semesterId, periodTimes)
        }
        return semesterId
    }

    private suspend fun syncFutureCourseCalendarMessages(
        semesterId: Long,
        periodTimes: List<SemesterPeriodTime>? = null
    ): List<CalendarGatewayResult> {
        val syncer = calendarReminderSyncer ?: return emptyList()
        val semester = semesterDao.findById(semesterId) ?: return emptyList()
        val resolvedPeriodTimes = periodTimes ?: observePeriodTimes(semesterId).first()
        val globalMinutes = preferences.courseReminderMinutes.first()
        val now = clock.instant()
        val adjustmentMap = calendarAdjustmentDao.findBySemester(semesterId)
            .mapNotNull { row ->
                row.sourceWeekParity?.let { parityName ->
                    runCatching {
                        row.actualDate to MakeupCourseSource(
                            dayOfWeek = row.sourceDayOfWeek,
                            weekParity = WeekParity.valueOf(parityName)
                        )
                    }.getOrNull()
                }
            }.toMap()
        val classOverrideMap = classOverrideDao.findBySemester(semesterId)
            .mapNotNull { row ->
                runCatching { row.actualDate to ClassOverride.valueOf(row.overrideKind) }.getOrNull()
            }
            .toMap()
        val unifiedRules = holidayCalendarRepository?.let { repository ->
            repository.initialize()
            repository.syncIfStale()
            repository.resolveBetween(
                semester.startDate,
                semester.endDate ?: semester.startDate.plusWeeks(DEFAULT_SEMESTER_WEEKS.toLong()).minusDays(1)
            )
        }.orEmpty()
        val specialDays = calendarScheduleReader
            ?.takeIf { it.hasReadPermission() }
            ?.readBetween(
                startDate = semester.startDate,
                endDate = semester.endDate ?: semester.startDate.plusWeeks(DEFAULT_SEMESTER_WEEKS.toLong()).minusDays(1)
            )
            .orEmpty()
        val results = mutableListOf<CalendarGatewayResult>()
        courseDao.findBySemester(semesterId).forEach { course ->
            val minutes = when (course.courseReminderMode) {
                CourseReminderMode.FOLLOW_GLOBAL -> globalMinutes
                CourseReminderMode.DISABLED -> null
                CourseReminderMode.CUSTOM -> course.courseReminderMinutes?.coerceIn(1, 180)
            } ?: return@forEach
            val weeks = courseDao.findWithWeeksById(course.id)?.weeks.orEmpty()
            weeks.forEach { courseWeek ->
                val start = resolvedPeriodTimes.first { it.period == course.startPeriod }.startTime
                val end = resolvedPeriodTimes.first { it.period == course.endPeriod }.endTime
                val occurrenceDates = if (unifiedRules.isNotEmpty()) {
                    courseOccurrenceDatesWithRules(
                        semesterStartDate = semester.startDate,
                        week = courseWeek.week,
                        courseDayOfWeek = course.dayOfWeek,
                        calendarRules = unifiedRules,
                        confirmedAdjustments = adjustmentMap,
                        classOverrides = classOverrideMap
                    )
                } else {
                    courseOccurrenceDates(
                        semesterStartDate = semester.startDate,
                        week = courseWeek.week,
                        courseDayOfWeek = course.dayOfWeek,
                        specialDays = specialDays,
                        confirmedAdjustments = adjustmentMap
                    )
                }
                occurrenceDates.forEach { date ->
                    val startAt = date.atTime(start).atZone(clock.zone).toInstant()
                    if (startAt.isAfter(now)) {
                        results += syncer.syncCourseOccurrence(
                            course = course,
                            week = courseWeek.week,
                            actualDate = date,
                            startAt = startAt,
                            endAt = date.atTime(end).atZone(clock.zone).toInstant(),
                            reminderMinutes = minutes
                        )
                    }
                }
            }
        }
        return results
    }


    private fun resolveWeeks(rule: WeekRuleResult, semester: SemesterInput): Set<Int> {
        val semesterWeeks = semester.endDate?.let { endDate ->
            (ChronoUnit.DAYS.between(semester.startDate, endDate) / 7 + 1).toInt()
        } ?: DEFAULT_SEMESTER_WEEKS
        val candidates = rule.weeks.ifEmpty { (1..semesterWeeks).toSet() }
        return candidates
            .asSequence()
            .filter { it in 1..semesterWeeks }
            .filter { week ->
                when (rule.parity) {
                    WeekParity.ODD -> week % 2 == 1
                    WeekParity.EVEN -> week % 2 == 0
                    null -> true
                }
            }
            .toSortedSet()
    }

    private data class PreparedCourse(
        val preview: TimetablePreviewCourse,
        val dayOfWeek: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weeks: Set<Int>
    )

    private companion object {
        const val DEFAULT_SEMESTER_WEEKS = 20
    }
}
