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
        require(snapshot.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "只能写入当前备份版本"
        }
        val root = JSONObject()
            .put("schemaVersion", snapshot.schemaVersion)
            .put("deviceId", snapshot.deviceId)
            .put("createdAt", snapshot.createdAt.toString())
            .put("updatedAt", snapshot.updatedAt.toString())
            .put("settings", encodeSettings(snapshot.settings))
            .put("semesters", JSONArray(snapshot.semesters.map(::encodeSemester)))
            .put("courses", JSONArray(snapshot.courses.map(::encodeCourse)))
            .put("courseWeeks", JSONArray(snapshot.courseWeeks.map(::encodeCourseWeek)))
            .put("scheduleEvents", JSONArray(snapshot.scheduleEvents.map(::encodeScheduleEvent)))
            .put("weights", JSONArray(snapshot.weights.map(::encodeWeight)))
            .put("activities", JSONArray(snapshot.activities.map(::encodeActivity)))
            .put("monthlyReports", JSONArray(snapshot.monthlyReports.map(::encodeMonthlyReport)))
            .put("transactions", JSONArray(snapshot.transactions.map(::encodeTransaction)))
            .put("budgets", JSONArray(snapshot.budgets.map(::encodeBudget)))
            .put("importLogs", JSONArray(snapshot.importLogs.map(::encodeImportLog)))
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): DailySnapshot = try {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val version = root.requiredInt("schemaVersion")
        if (version != CURRENT_SCHEMA_VERSION) {
            throw SnapshotValidationException.UnsupportedSchemaVersion(version)
        }
        val snapshot = DailySnapshot(
            schemaVersion = version,
            deviceId = root.requiredString("deviceId").also { requireNonBlank("deviceId", it) },
            createdAt = root.requiredInstant("createdAt"),
            updatedAt = root.requiredInstant("updatedAt"),
            settings = decodeSettings(root.requiredObject("settings")),
            semesters = root.requiredArray("semesters").objects(::decodeSemester),
            courses = root.requiredArray("courses").objects(::decodeCourse),
            courseWeeks = root.requiredArray("courseWeeks").objects(::decodeCourseWeek),
            scheduleEvents = root.requiredArray("scheduleEvents").objects(::decodeScheduleEvent),
            weights = root.requiredArray("weights").objects(::decodeWeight),
            activities = root.requiredArray("activities").objects(::decodeActivity),
            monthlyReports = root.requiredArray("monthlyReports").objects(::decodeMonthlyReport),
            transactions = root.requiredArray("transactions").objects(::decodeTransaction),
            budgets = root.requiredArray("budgets").objects(::decodeBudget),
            importLogs = root.requiredArray("importLogs").objects(::decodeImportLog)
        )
        validate(snapshot)
        snapshot
    } catch (error: SnapshotValidationException) {
        throw error
    } catch (error: Exception) {
        throw SnapshotValidationException.MalformedSnapshot(error.message ?: "格式错误", error)
    }

    private fun validate(snapshot: DailySnapshot) {
        if (snapshot.updatedAt.isBefore(snapshot.createdAt)) {
            throw SnapshotValidationException.MalformedSnapshot("更新时间早于创建时间")
        }
        if (snapshot.deviceId.isBlank()) {
            throw SnapshotValidationException.MalformedSnapshot("缺少设备标识")
        }
        snapshot.courses.forEach { course ->
            if (course.dayOfWeek !in 1..7 || course.startPeriod <= 0 || course.endPeriod < course.startPeriod) {
                throw SnapshotValidationException.MalformedSnapshot("课程时间字段无效")
            }
        }
        snapshot.transactions.forEach { transaction ->
            if (transaction.amountCents < 0L) {
                throw SnapshotValidationException.MalformedSnapshot("账单金额不能为负数")
            }
        }
    }

    private fun encodeSettings(settings: SnapshotSettings): JSONObject = JSONObject()
        .putNullable("semesterStartDate", settings.semesterStartDate?.toString())
        .putNullable("currentSemesterId", settings.currentSemesterId)
        .putNullable("targetWeightJin", settings.targetWeightJin)
        .putNullable("defaultBudgetCents", settings.defaultBudgetCents)
        .putNullable("webDavEndpoint", settings.webDavEndpoint)
        .put("autoSyncEnabled", settings.autoSyncEnabled)

    private fun decodeSettings(json: JSONObject): SnapshotSettings = SnapshotSettings(
        semesterStartDate = json.optionalString("semesterStartDate")?.let(LocalDate::parse),
        currentSemesterId = json.optionalLong("currentSemesterId"),
        targetWeightJin = json.optionalDouble("targetWeightJin"),
        defaultBudgetCents = json.optionalLong("defaultBudgetCents"),
        webDavEndpoint = json.optionalString("webDavEndpoint"),
        autoSyncEnabled = json.requiredBoolean("autoSyncEnabled")
    )

    private fun encodeSemester(value: SemesterEntity): JSONObject = JSONObject()
        .put("id", value.id).put("name", value.name).put("startDate", value.startDate.toString())
        .putNullable("endDate", value.endDate?.toString()).put("isCurrent", value.isCurrent)
        .put("createdAt", value.createdAt)

    private fun decodeSemester(json: JSONObject): SemesterEntity = SemesterEntity(
        id = json.requiredLong("id"), name = json.requiredString("name"),
        startDate = LocalDate.parse(json.requiredString("startDate")),
        endDate = json.optionalString("endDate")?.let(LocalDate::parse),
        isCurrent = json.requiredBoolean("isCurrent"), createdAt = json.requiredLong("createdAt")
    )

    private fun encodeCourse(value: CourseEntity): JSONObject = JSONObject()
        .put("id", value.id).put("semesterId", value.semesterId).put("courseName", value.courseName)
        .put("dayOfWeek", value.dayOfWeek).put("startPeriod", value.startPeriod).put("endPeriod", value.endPeriod)
        .put("weekRuleText", value.weekRuleText).put("parsedWeeks", JSONArray(value.parsedWeeks.sorted()))
        .putNullable("campus", value.campus).putNullable("location", value.location)
        .putNullable("teacher", value.teacher).putNullable("courseCode", value.courseCode)
        .putNullable("credits", value.credits).putNullable("notes", value.notes)

    private fun decodeCourse(json: JSONObject): CourseEntity = CourseEntity(
        id = json.requiredLong("id"), semesterId = json.requiredLong("semesterId"),
        courseName = json.requiredString("courseName"), dayOfWeek = json.requiredInt("dayOfWeek"),
        startPeriod = json.requiredInt("startPeriod"), endPeriod = json.requiredInt("endPeriod"),
        weekRuleText = json.requiredString("weekRuleText"), parsedWeeks = json.requiredArray("parsedWeeks").ints().toSet(),
        campus = json.optionalString("campus"), location = json.optionalString("location"),
        teacher = json.optionalString("teacher"), courseCode = json.optionalString("courseCode"),
        credits = json.optionalDouble("credits"), notes = json.optionalString("notes")
    )

    private fun encodeCourseWeek(value: CourseWeekEntity): JSONObject = JSONObject()
        .put("courseId", value.courseId).put("week", value.week)

    private fun decodeCourseWeek(json: JSONObject): CourseWeekEntity = CourseWeekEntity(
        courseId = json.requiredLong("courseId"), week = json.requiredInt("week")
    )

    private fun encodeScheduleEvent(value: ScheduleEventEntity): JSONObject = JSONObject()
        .put("id", value.id).put("title", value.title).put("eventAt", value.eventAt)
        .put("reminderOffsetMinutes", value.reminderOffsetMinutes).put("reminderMode", value.reminderMode.name)
        .put("repeatYearly", value.repeatYearly).putNullable("notes", value.notes)
        .put("isDismissed", value.isDismissed).put("createdAt", value.createdAt).put("updatedAt", value.updatedAt)

    private fun decodeScheduleEvent(json: JSONObject): ScheduleEventEntity = ScheduleEventEntity(
        id = json.requiredLong("id"), title = json.requiredString("title"), eventAt = json.requiredLong("eventAt"),
        reminderOffsetMinutes = json.requiredInt("reminderOffsetMinutes"),
        reminderMode = enumValue(json.requiredString("reminderMode")), repeatYearly = json.requiredBoolean("repeatYearly"),
        notes = json.optionalString("notes"), isDismissed = json.requiredBoolean("isDismissed"),
        createdAt = json.requiredLong("createdAt"), updatedAt = json.requiredLong("updatedAt")
    )

    private fun encodeWeight(value: WeightRecordEntity): JSONObject = JSONObject()
        .put("id", value.id).put("recordedAt", value.recordedAt).put("weightJin", value.weightJin)
        .put("source", value.source).putNullable("notes", value.notes)

    private fun decodeWeight(json: JSONObject): WeightRecordEntity = WeightRecordEntity(
        id = json.requiredLong("id"), recordedAt = json.requiredLong("recordedAt"),
        weightJin = json.requiredDouble("weightJin"), source = json.requiredString("source"), notes = json.optionalString("notes")
    )

    private fun encodeActivity(value: ActivityRecordEntity): JSONObject = JSONObject()
        .put("id", value.id).put("recordedAt", value.recordedAt).put("activityType", value.activityType.name)
        .putNullable("steps", value.steps).putNullable("distanceMeters", value.distanceMeters)
        .putNullable("durationMinutes", value.durationMinutes).put("source", value.source)
        .putNullable("rawRecordId", value.rawRecordId)

    private fun decodeActivity(json: JSONObject): ActivityRecordEntity = ActivityRecordEntity(
        id = json.requiredLong("id"), recordedAt = json.requiredLong("recordedAt"),
        activityType = enumValue(json.requiredString("activityType")), steps = json.optionalLong("steps"),
        distanceMeters = json.optionalDouble("distanceMeters"), durationMinutes = json.optionalInt("durationMinutes"),
        source = json.requiredString("source"), rawRecordId = json.optionalString("rawRecordId")
    )

    private fun encodeMonthlyReport(value: MonthlyReportEntity): JSONObject = JSONObject()
        .put("id", value.id).put("month", value.month.toString()).put("weightTrendSummary", value.weightTrendSummary)
        .put("activitySummary", value.activitySummary).putNullable("aiAdviceText", value.aiAdviceText)
        .put("generatedAt", value.generatedAt).put("adviceSource", value.adviceSource.name)
        .put("generationStatus", value.generationStatus.name)

    private fun decodeMonthlyReport(json: JSONObject): MonthlyReportEntity = MonthlyReportEntity(
        id = json.requiredLong("id"), month = YearMonth.parse(json.requiredString("month")),
        weightTrendSummary = json.requiredString("weightTrendSummary"), activitySummary = json.requiredString("activitySummary"),
        aiAdviceText = json.optionalString("aiAdviceText"), generatedAt = json.requiredLong("generatedAt"),
        adviceSource = enumValue(json.requiredString("adviceSource")),
        generationStatus = enumValue(json.requiredString("generationStatus"))
    )

    private fun encodeTransaction(value: TransactionEntity): JSONObject = JSONObject()
        .put("id", value.id).put("occurredAt", value.occurredAt).put("amountCents", value.amountCents)
        .put("direction", value.direction.name).put("category", value.category).put("counterparty", value.counterparty)
        .put("source", value.source).putNullable("paymentMethod", value.paymentMethod)
        .putNullable("transactionType", value.transactionType).putNullable("status", value.status)
        .putNullable("merchantOrderId", value.merchantOrderId).putNullable("orderId", value.orderId)
        .putNullable("rawText", value.rawText).putNullable("notes", value.notes)
        .putNullable("importBatchId", value.importBatchId).put("createdAt", value.createdAt).put("updatedAt", value.updatedAt)

    private fun decodeTransaction(json: JSONObject): TransactionEntity = TransactionEntity(
        id = json.requiredLong("id"), occurredAt = json.requiredLong("occurredAt"), amountCents = json.requiredLong("amountCents"),
        direction = enumValue(json.requiredString("direction")), category = json.requiredString("category"),
        counterparty = json.requiredString("counterparty"), source = json.requiredString("source"),
        paymentMethod = json.optionalString("paymentMethod"), transactionType = json.optionalString("transactionType"),
        status = json.optionalString("status"), merchantOrderId = json.optionalString("merchantOrderId"),
        orderId = json.optionalString("orderId"), rawText = json.optionalString("rawText"), notes = json.optionalString("notes"),
        importBatchId = json.optionalString("importBatchId"), createdAt = json.requiredLong("createdAt"), updatedAt = json.requiredLong("updatedAt")
    )

    private fun encodeBudget(value: BudgetEntity): JSONObject = JSONObject()
        .put("month", value.month).put("budgetCents", value.budgetCents)
        .put("triggeredPercentages", JSONArray(value.triggeredPercentages.sorted())).put("updatedAt", value.updatedAt)

    private fun decodeBudget(json: JSONObject): BudgetEntity = BudgetEntity(
        month = json.requiredString("month"), budgetCents = json.requiredLong("budgetCents"),
        triggeredPercentages = json.requiredArray("triggeredPercentages").ints().toSet(), updatedAt = json.requiredLong("updatedAt")
    )

    private fun encodeImportLog(value: ImportLogEntity): JSONObject = JSONObject()
        .put("batchId", value.batchId).put("fileName", value.fileName).put("sourceType", value.sourceType)
        .put("importedAt", value.importedAt).put("totalRows", value.totalRows).put("successRows", value.successRows)
        .put("skippedRows", value.skippedRows).putNullable("errorSummary", value.errorSummary)

    private fun decodeImportLog(json: JSONObject): ImportLogEntity = ImportLogEntity(
        batchId = json.requiredString("batchId"), fileName = json.requiredString("fileName"), sourceType = json.requiredString("sourceType"),
        importedAt = json.requiredLong("importedAt"), totalRows = json.requiredInt("totalRows"),
        successRows = json.requiredInt("successRows"), skippedRows = json.requiredInt("skippedRows"), errorSummary = json.optionalString("errorSummary")
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String): T = try {
        enumValueOf(value)
    } catch (error: IllegalArgumentException) {
        throw SnapshotValidationException.MalformedSnapshot("枚举值无效：$value", error)
    }

    private fun requireNonBlank(name: String, value: String) {
        if (value.isBlank()) throw SnapshotValidationException.MalformedSnapshot("$name 不能为空")
    }

    private fun JSONObject.requiredString(key: String): String = getString(key)
    private fun JSONObject.requiredInt(key: String): Int = getInt(key)
    private fun JSONObject.requiredLong(key: String): Long = getLong(key)
    private fun JSONObject.requiredDouble(key: String): Double = getDouble(key)
    private fun JSONObject.requiredBoolean(key: String): Boolean = getBoolean(key)
    private fun JSONObject.requiredObject(key: String): JSONObject = getJSONObject(key)
    private fun JSONObject.requiredArray(key: String): JSONArray = getJSONArray(key)
    private fun JSONObject.requiredInstant(key: String): Instant = Instant.parse(requiredString(key))

    private fun JSONObject.optionalString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun JSONObject.optionalInt(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.optionalLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optionalDouble(key: String): Double? = if (!has(key) || isNull(key)) null else getDouble(key)
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    private inline fun <T> JSONArray.objects(mapper: (JSONObject) -> T): List<T> = buildList {
        for (index in 0 until length()) add(mapper(getJSONObject(index)))
    }

    private fun JSONArray.ints(): List<Int> = buildList {
        for (index in 0 until length()) add(getInt(index))
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
