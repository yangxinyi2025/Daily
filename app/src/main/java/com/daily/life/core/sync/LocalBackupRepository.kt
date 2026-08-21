package com.daily.life.core.sync

import androidx.room.withTransaction
import com.daily.life.core.database.ActivityRecordEntity
import com.daily.life.core.database.BudgetEntity
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ImportLogEntity
import com.daily.life.core.database.MonthlyReportEntity
import com.daily.life.core.database.ScheduleEventEntity
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.database.TransactionEntity
import com.daily.life.core.database.WeightRecordEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first

class LocalBackupRepository(
    private val store: LocalSnapshotStore,
    private val serializer: SnapshotSerializer,
    private val snapshotDirectory: File,
    private val deviceId: String,
    private val clock: Clock = Clock.systemUTC()
) : SnapshotRepository {
    override suspend fun createSnapshot(): File {
        val now = clock.instant()
        val snapshot = store.read().copy(
            schemaVersion = SnapshotSerializer.CURRENT_SCHEMA_VERSION,
            deviceId = deviceId,
            createdAt = now,
            updatedAt = now
        )
        val file = snapshotDirectory.resolve("daily-${now.toEpochMilli()}.snapshot")
        writeAtomically(file, serializer.encode(snapshot))
        return file
    }

    override suspend fun restoreSnapshot(file: File, replaceExisting: Boolean): RestoreResult {
        if (!replaceExisting) throw RestoreConfirmationRequired()
        val staged = serializer.decode(file.readBytes())
        if (staged.deviceId != deviceId) {
            throw SnapshotValidationException.DeviceMismatch(deviceId, staged.deviceId)
        }
        val safetyCopy = createSnapshot()
        store.replace(staged)
        return RestoreResult(file = file, safetyCopy = safetyCopy)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        snapshotDirectory.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("无法替换本地备份文件")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("无法完成本地备份写入")
        }
    }
}

class RoomSnapshotStore(
    private val database: DailyDatabase,
    private val preferences: DailyPreferences
) : LocalSnapshotStore {
    override suspend fun read(): DailySnapshot {
        val settings = SnapshotSettings(
            semesterStartDate = preferences.semesterStartDate.first(),
            currentSemesterId = preferences.currentSemesterId.first(),
            targetWeightJin = preferences.targetWeightJin.first(),
            defaultBudgetCents = preferences.defaultBudgetCents.first(),
            webDavEndpoint = preferences.webDavEndpoint.first(),
            autoSyncEnabled = preferences.autoSyncEnabled.first()
        )
        return DailySnapshot.empty(
            deviceId = "local",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        ).copy(
            settings = settings,
            semesters = database.semesterDao().findAll(),
            courses = database.courseDao().findAll(),
            courseWeeks = database.courseDao().findAllWeeks(),
            scheduleEvents = database.scheduleEventDao().findAll(),
            weights = database.healthDao().findAllWeights(),
            activities = database.healthDao().findAllActivities(),
            monthlyReports = database.healthDao().findAllMonthlyReports(),
            transactions = database.transactionDao().findAll(),
            budgets = database.budgetDao().findAll(),
            importLogs = database.importLogDao().findAll()
        )
    }

    override suspend fun replace(snapshot: DailySnapshot) {
        database.withTransaction {
            database.courseDao().deleteAllWeeks()
            database.courseDao().deleteAll()
            database.scheduleEventDao().deleteAll()
            database.healthDao().deleteAllMonthlyReports()
            database.healthDao().deleteAllActivities()
            database.healthDao().deleteAllWeights()
            database.transactionDao().deleteAll()
            database.budgetDao().deleteAll()
            database.importLogDao().deleteAll()
            database.semesterDao().deleteAll()

            snapshot.semesters.forEach { database.semesterDao().insert(it) }
            snapshot.courses.forEach { database.courseDao().insert(it) }
            snapshot.courseWeeks.forEach { database.courseDao().insertWeek(it) }
            snapshot.scheduleEvents.forEach { database.scheduleEventDao().insert(it) }
            snapshot.weights.forEach { database.healthDao().insertWeight(it) }
            snapshot.activities.forEach { database.healthDao().insertActivity(it) }
            snapshot.monthlyReports.forEach { database.healthDao().insertMonthlyReport(it) }
            snapshot.transactions.forEach { database.transactionDao().insert(it) }
            snapshot.budgets.forEach { database.budgetDao().upsert(it) }
            snapshot.importLogs.forEach { database.importLogDao().insert(it) }
        }
        preferences.setSemesterStartDate(snapshot.settings.semesterStartDate)
        preferences.setCurrentSemesterId(snapshot.settings.currentSemesterId)
        preferences.setTargetWeightJin(snapshot.settings.targetWeightJin)
        preferences.setDefaultBudgetCents(snapshot.settings.defaultBudgetCents)
        preferences.setWebDavEndpoint(snapshot.settings.webDavEndpoint)
        preferences.setAutoSyncEnabled(snapshot.settings.autoSyncEnabled)
    }
}
