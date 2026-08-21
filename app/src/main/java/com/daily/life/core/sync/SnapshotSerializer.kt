package com.daily.life.core.sync

import com.daily.life.core.database.ActivityRecordEntity
import com.daily.life.core.database.ActivityType
import com.daily.life.core.database.AdviceSource
import com.daily.life.core.database.BudgetEntity
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.ImportLogEntity
import com.daily.life.core.database.MonthlyReportEntity
import com.daily.life.core.database.ReportGenerationStatus
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.database.ScheduleEventEntity
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.database.TransactionDirection
import com.daily.life.core.database.TransactionEntity
import com.daily.life.core.database.WeightRecordEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

class SnapshotSerializer {
    fun encode(snapshot: DailySnapshot): ByteArray {
        require(snapshot.schemaVersion == CURRENT_SCHEMA_VERSION) { "只能写入当前备份版本" }
        val root = JSONObject()
            .put("schemaVersion", snapshot.schemaVersion)
            .put("deviceId", snapshot.deviceId)
            .put("createdAt", snapshot.createdAt.toString())
            .put("updatedAt", snapshot.updatedAt.toString())
            .put("settings", encodeSettings(snapshot.settings))
            .put("semesters", JSONArray(snapshot.semesters.sortedBy { it.id }.map(::encodeSemester)))
            .put("courses", JSONArray(snapshot.courses.sortedBy { it.id }.map(::encodeCourse)))
            .put("courseWeeks", JSONArray(snapshot.courseWeeks.sortedWith(compareBy({ it.courseId }, { it.week })).map(::encodeCourseWeek)))
            .put("scheduleEvents", JSONArray(snapshot.scheduleEvents.sortedBy { it.id }.map(::encodeScheduleEvent)))
            .put("weights", JSONArray(snapshot.weights.sortedBy { it.id }.map(::encodeWeight)))
            .put("activities", JSONArray(snapshot.activities.sortedBy { it.id }.map(::encodeActivity)))
            .put("monthlyReports", JSONArray(snapshot.monthlyReports.sortedBy { it.id }.map(::encodeMonthlyReport)))
            .put("transactions", JSONArray(snapshot.transactions.sortedBy { it.id }.map(::encodeTransaction)))
            .put("budgets", JSONArray(snapshot.budgets.sortedBy { it.month }.map(::encodeBudget)))
            .put("importLogs", JSONArray(snapshot.importLogs.sortedBy { it.batchId }.map(::encodeImportLog)))
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): DailySnapshot = try {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val version = root.requiredInt("schemaVersion")
        if (version != CURRENT_SCHEMA_VERSION) throw SnapshotValidationException.UnsupportedSchemaVersion(version)
        val snapshot = DailySnapshot(
            version,
            root.requiredString("deviceId").also { requireNonBlank("deviceId", it) },
            root.requiredInstant("createdAt"),
            root.requiredInstant("updatedAt"),
            decodeSettings(root.requiredObject("settings")),
            root.requiredArray("semesters").objects(::decodeSemester),
            root.requiredArray("courses").objects(::decodeCourse),
            root.requiredArray("courseWeeks").objects(::decodeCourseWeek),
            root.requiredArray("scheduleEvents").objects(::decodeScheduleEvent),
            root.requiredArray("weights").objects(::decodeWeight),
            root.requiredArray("activities").objects(::decodeActivity),
            root.requiredArray("monthlyReports").objects(::decodeMonthlyReport),
            root.requiredArray("transactions").objects(::decodeTransaction),
            root.requiredArray("budgets").objects(::decodeBudget),
            root.requiredArray("importLogs").objects(::decodeImportLog)
        )
        validate(snapshot)
        snapshot
    } catch (error: SnapshotValidationException) { throw error }
      catch (error: Exception) { throw SnapshotValidationException.MalformedSnapshot(error.message ?: "格式错误", error) }

    private fun validate(snapshot: DailySnapshot) {
        if (snapshot.updatedAt.isBefore(snapshot.createdAt)) throw SnapshotValidationException.MalformedSnapshot("更新时间早于创建时间")
        if (snapshot.deviceId.isBlank()) throw SnapshotValidationException.MalformedSnapshot("缺少设备标识")
        if (snapshot.settings.defaultBudgetCents != null && snapshot.settings.defaultBudgetCents < 0) throw SnapshotValidationException.MalformedSnapshot("预算不能为负数")
        snapshot.courses.forEach { if (it.dayOfWeek !in 1..7 || it.startPeriod <= 0 || it.endPeriod < it.startPeriod) throw SnapshotValidationException.MalformedSnapshot("课程时间字段无效") }
        if (snapshot.courseWeeks.any { week -> snapshot.courses.none { it.id == week.courseId } }) throw SnapshotValidationException.MalformedSnapshot("课表周次引用无效")
        snapshot.transactions.forEach { if (it.amountCents < 0) throw SnapshotValidationException.MalformedSnapshot("账单金额不能为负数"); if (it.updatedAt < it.createdAt) throw SnapshotValidationException.MalformedSnapshot("账单时间字段无效") }
    }

    private fun encodeSettings(s: SnapshotSettings) = JSONObject().putNullable("semesterStartDate", s.semesterStartDate?.toString()).putNullable("currentSemesterId", s.currentSemesterId).putNullable("targetWeightJin", s.targetWeightJin).putNullable("defaultBudgetCents", s.defaultBudgetCents).putNullable("webDavEndpoint", s.webDavEndpoint).put("autoSyncEnabled", s.autoSyncEnabled)
    private fun decodeSettings(j: JSONObject) = SnapshotSettings(j.optionalString("semesterStartDate")?.let(LocalDate::parse), j.optionalLong("currentSemesterId"), j.optionalDouble("targetWeightJin"), j.optionalLong("defaultBudgetCents"), j.optionalString("webDavEndpoint"), j.requiredBoolean("autoSyncEnabled"))
    private fun encodeSemester(v: SemesterEntity) = JSONObject().put("id", v.id).put("name", v.name).put("startDate", v.startDate.toString()).putNullable("endDate", v.endDate?.toString()).put("isCurrent", v.isCurrent).put("createdAt", v.createdAt)
    private fun decodeSemester(j: JSONObject) = SemesterEntity(j.requiredLong("id"), j.requiredString("name"), LocalDate.parse(j.requiredString("startDate")), j.optionalString("endDate")?.let(LocalDate::parse), j.requiredBoolean("isCurrent"), j.requiredLong("createdAt"))
    private fun encodeCourse(v: CourseEntity) = JSONObject().put("id", v.id).put("semesterId", v.semesterId).put("courseName", v.courseName).put("dayOfWeek", v.dayOfWeek).put("startPeriod", v.startPeriod).put("endPeriod", v.endPeriod).put("weekRuleText", v.weekRuleText).put("parsedWeeks", JSONArray(v.parsedWeeks.sorted())).putNullable("campus", v.campus).putNullable("location", v.location).putNullable("teacher", v.teacher).putNullable("courseCode", v.courseCode).putNullable("credits", v.credits).putNullable("notes", v.notes)
    private fun decodeCourse(j: JSONObject) = CourseEntity(j.requiredLong("id"), j.requiredLong("semesterId"), j.requiredString("courseName"), j.requiredInt("dayOfWeek"), j.requiredInt("startPeriod"), j.requiredInt("endPeriod"), j.requiredString("weekRuleText"), j.requiredArray("parsedWeeks").ints().toSet(), j.optionalString("campus"), j.optionalString("location"), j.optionalString("teacher"), j.optionalString("courseCode"), j.optionalDouble("credits"), j.optionalString("notes"))
    private fun encodeCourseWeek(v: CourseWeekEntity) = JSONObject().put("courseId", v.courseId).put("week", v.week)
    private fun decodeCourseWeek(j: JSONObject) = CourseWeekEntity(j.requiredLong("courseId"), j.requiredInt("week"))
    private fun encodeScheduleEvent(v: ScheduleEventEntity) = JSONObject().put("id", v.id).put("title", v.title).put("eventAt", v.eventAt).put("reminderOffsetMinutes", v.reminderOffsetMinutes).put("reminderMode", v.reminderMode.name).put("repeatYearly", v.repeatYearly).putNullable("notes", v.notes).put("isDismissed", v.isDismissed).put("createdAt", v.createdAt).put("updatedAt", v.updatedAt)
    private fun decodeScheduleEvent(j: JSONObject) = ScheduleEventEntity(j.requiredLong("id"), j.requiredString("title"), j.requiredLong("eventAt"), j.requiredInt("reminderOffsetMinutes"), enumValue(j.requiredString("reminderMode")), j.requiredBoolean("repeatYearly"), j.optionalString("notes"), j.requiredBoolean("isDismissed"), j.requiredLong("createdAt"), j.requiredLong("updatedAt"))
    private fun encodeWeight(v: WeightRecordEntity) = JSONObject().put("id", v.id).put("recordedAt", v.recordedAt).put("weightJin", v.weightJin).put("source", v.source).putNullable("notes", v.notes)
    private fun decodeWeight(j: JSONObject) = WeightRecordEntity(j.requiredLong("id"), j.requiredLong("recordedAt"), j.requiredDouble("weightJin"), j.requiredString("source"), j.optionalString("notes"))
    private fun encodeActivity(v: ActivityRecordEntity) = JSONObject().put("id", v.id).put("recordedAt", v.recordedAt).put("activityType", v.activityType.name).putNullable("steps", v.steps).putNullable("distanceMeters", v.distanceMeters).putNullable("durationMinutes", v.durationMinutes).put("source", v.source).putNullable("rawRecordId", v.rawRecordId)
    private fun decodeActivity(j: JSONObject) = ActivityRecordEntity(j.requiredLong("id"), j.requiredLong("recordedAt"), enumValue(j.requiredString("activityType")), j.optionalLong("steps"), j.optionalDouble("distanceMeters"), j.optionalInt("durationMinutes"), j.requiredString("source"), j.optionalString("rawRecordId"))
    private fun encodeMonthlyReport(v: MonthlyReportEntity) = JSONObject().put("id", v.id).put("month", v.month.toString()).put("weightTrendSummary", v.weightTrendSummary).put("activitySummary", v.activitySummary).putNullable("aiAdviceText", v.aiAdviceText).put("generatedAt", v.generatedAt).put("adviceSource", v.adviceSource.name).put("generationStatus", v.generationStatus.name)
    private fun decodeMonthlyReport(j: JSONObject) = MonthlyReportEntity(j.requiredLong("id"), YearMonth.parse(j.requiredString("month")), j.requiredString("weightTrendSummary"), j.requiredString("activitySummary"), j.optionalString("aiAdviceText"), j.requiredLong("generatedAt"), enumValue(j.requiredString("adviceSource")), enumValue(j.requiredString("generationStatus")))
    private fun encodeTransaction(v: TransactionEntity) = JSONObject().put("id", v.id).put("occurredAt", v.occurredAt).put("amountCents", v.amountCents).put("direction", v.direction.name).put("category", v.category).put("counterparty", v.counterparty).put("source", v.source).putNullable("paymentMethod", v.paymentMethod).putNullable("transactionType", v.transactionType).putNullable("status", v.status).putNullable("merchantOrderId", v.merchantOrderId).putNullable("orderId", v.orderId).putNullable("rawText", v.rawText).putNullable("notes", v.notes).putNullable("importBatchId", v.importBatchId).put("createdAt", v.createdAt).put("updatedAt", v.updatedAt)
    private fun decodeTransaction(j: JSONObject) = TransactionEntity(j.requiredLong("id"), j.requiredLong("occurredAt"), j.requiredLong("amountCents"), enumValue(j.requiredString("direction")), j.requiredString("category"), j.requiredString("counterparty"), j.requiredString("source"), j.optionalString("paymentMethod"), j.optionalString("transactionType"), j.optionalString("status"), j.optionalString("merchantOrderId"), j.optionalString("orderId"), j.optionalString("rawText"), j.optionalString("notes"), j.optionalString("importBatchId"), j.requiredLong("createdAt"), j.requiredLong("updatedAt"))
    private fun encodeBudget(v: BudgetEntity) = JSONObject().put("month", v.month).put("budgetCents", v.budgetCents).put("triggeredPercentages", JSONArray(v.triggeredPercentages.sorted())).put("updatedAt", v.updatedAt)
    private fun decodeBudget(j: JSONObject) = BudgetEntity(j.requiredString("month"), j.requiredLong("budgetCents"), j.requiredArray("triggeredPercentages").ints().toSet(), j.requiredLong("updatedAt"))
    private fun encodeImportLog(v: ImportLogEntity) = JSONObject().put("batchId", v.batchId).put("fileName", v.fileName).put("sourceType", v.sourceType).put("importedAt", v.importedAt).put("totalRows", v.totalRows).put("successRows", v.successRows).put("skippedRows", v.skippedRows).putNullable("errorSummary", v.errorSummary)
    private fun decodeImportLog(j: JSONObject) = ImportLogEntity(j.requiredString("batchId"), j.requiredString("fileName"), j.requiredString("sourceType"), j.requiredLong("importedAt"), j.requiredInt("totalRows"), j.requiredInt("successRows"), j.requiredInt("skippedRows"), j.optionalString("errorSummary"))
    private inline fun <reified T : Enum<T>> enumValue(value: String): T = try { enumValueOf(value) } catch (e: IllegalArgumentException) { throw SnapshotValidationException.MalformedSnapshot("枚举值无效：$value", e) }
    private fun requireNonBlank(name: String, value: String) { if (value.isBlank()) throw SnapshotValidationException.MalformedSnapshot("$name 不能为空") }
    private fun JSONObject.requiredString(key: String) = getString(key)
    private fun JSONObject.requiredInt(key: String) = getInt(key)
    private fun JSONObject.requiredLong(key: String) = getLong(key)
    private fun JSONObject.requiredDouble(key: String) = getDouble(key)
    private fun JSONObject.requiredBoolean(key: String) = getBoolean(key)
    private fun JSONObject.requiredObject(key: String) = getJSONObject(key)
    private fun JSONObject.requiredArray(key: String) = getJSONArray(key)
    private fun JSONObject.requiredInstant(key: String) = Instant.parse(requiredString(key))
    private fun JSONObject.optionalString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun JSONObject.optionalInt(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.optionalLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optionalDouble(key: String): Double? = if (!has(key) || isNull(key)) null else getDouble(key)
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private inline fun <T> JSONArray.objects(mapper: (JSONObject) -> T): List<T> = buildList { for (index in 0 until length()) add(mapper(getJSONObject(index))) }
    private fun JSONArray.ints(): List<Int> = buildList { for (index in 0 until length()) add(getInt(index)) }
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}
