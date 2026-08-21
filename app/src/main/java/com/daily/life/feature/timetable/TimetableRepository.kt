package com.daily.life.feature.timetable

import androidx.room.withTransaction
import com.daily.life.core.ReminderScheduler
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.datastore.DailyPreferences
import java.time.Clock
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface TimetableRepository {
    val currentSemester: Flow<SemesterEntity?>

    fun observeCourses(semesterId: Long, week: Int): Flow<List<CourseEntity>>

    suspend fun confirmImport(
        preview: TimetableParseResult,
        semester: SemesterInput,
        replaceExisting: Boolean
    ): Long
}

class RoomTimetableRepository(
    private val database: DailyDatabase,
    private val preferences: DailyPreferences,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock = Clock.systemDefaultZone()
) : TimetableRepository {
    private val semesterDao = database.semesterDao()
    private val courseDao = database.courseDao()

    override val currentSemester: Flow<SemesterEntity?> =
        combine(preferences.currentSemesterId, semesterDao.observeAll()) { preferredId, semesters ->
            semesters.firstOrNull { it.id == preferredId }
                ?: semesters.firstOrNull { it.isCurrent }
        }

    override fun observeCourses(semesterId: Long, week: Int): Flow<List<CourseEntity>> =
        courseDao.observeBySemesterWeek(semesterId, week)

    override suspend fun confirmImport(
        preview: TimetableParseResult,
        semester: SemesterInput,
        replaceExisting: Boolean
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
            id
        }

        if (semester.isCurrent) preferences.setCurrentSemesterId(semesterId)
        reminderScheduler.scheduleCourseReminders(semesterId)
        return semesterId
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
