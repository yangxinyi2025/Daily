package com.daily.life.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.LocalDate

class DailyPreferences private constructor(
    private val dataStore: DataStore<Preferences>
) {
    val semesterStartDate: Flow<LocalDate?> =
        dataStore.data.map { preferences ->
            preferences[SEMESTER_START_DATE]?.let(LocalDate::parse)
        }

    val currentSemesterId: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[CURRENT_SEMESTER_ID] }

    val targetWeightJin: Flow<Double?> =
        dataStore.data.map { preferences -> preferences[TARGET_WEIGHT_JIN] }

    val menstrualCycleDays: Flow<Int> =
        dataStore.data.map { preferences ->
            (preferences[MENSTRUAL_CYCLE_DAYS] ?: DEFAULT_MENSTRUAL_CYCLE_DAYS)
                .coerceIn(MIN_MENSTRUAL_CYCLE_DAYS, MAX_MENSTRUAL_CYCLE_DAYS)
        }

    val courseReminderMinutes: Flow<Int?> =
        dataStore.data.map { preferences -> preferences[COURSE_REMINDER_MINUTES] }

    val webDavEndpoint: Flow<String?> =
        dataStore.data.map { preferences -> preferences[WEB_DAV_ENDPOINT] }

    val autoSyncEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[AUTO_SYNC_ENABLED] ?: false }

    val lastSyncAt: Flow<Instant?> =
        dataStore.data.map { preferences -> preferences[LAST_SYNC_AT]?.let(Instant::ofEpochMilli) }

    val syncStatus: Flow<String> =
        dataStore.data.map { preferences -> preferences[SYNC_STATUS] ?: "仅使用本地数据" }

    val holidaySyncStatus: Flow<String?> =
        dataStore.data.map { preferences -> preferences[HOLIDAY_SYNC_STATUS] }

    val holidayLastSyncAt: Flow<Instant?> =
        dataStore.data.map { preferences -> preferences[HOLIDAY_LAST_SYNC_AT]?.let(Instant::ofEpochMilli) }

    val holidaySyncError: Flow<String?> =
        dataStore.data.map { preferences -> preferences[HOLIDAY_SYNC_ERROR] }

    val backgroundRuntimeGuideAcknowledged: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[BACKGROUND_RUNTIME_GUIDE_ACKNOWLEDGED] ?: false }

    suspend fun setSemesterStartDate(value: LocalDate?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(SEMESTER_START_DATE)
            } else {
                preferences[SEMESTER_START_DATE] = value.toString()
            }
        }
    }

    suspend fun setCurrentSemesterId(value: Long?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(CURRENT_SEMESTER_ID)
            } else {
                preferences[CURRENT_SEMESTER_ID] = value
            }
        }
    }

    suspend fun setTargetWeightJin(value: Double?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(TARGET_WEIGHT_JIN)
            } else {
                preferences[TARGET_WEIGHT_JIN] = value
            }
        }
    }

    suspend fun setMenstrualCycleDays(value: Int?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(MENSTRUAL_CYCLE_DAYS)
            } else {
                preferences[MENSTRUAL_CYCLE_DAYS] = value.coerceIn(
                    MIN_MENSTRUAL_CYCLE_DAYS,
                    MAX_MENSTRUAL_CYCLE_DAYS
                )
            }
        }
    }

    suspend fun setCourseReminderMinutes(value: Int?) {
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(COURSE_REMINDER_MINUTES)
            else preferences[COURSE_REMINDER_MINUTES] = value.coerceIn(1, 180)
        }
    }

    suspend fun setWebDavEndpoint(value: String?) {
        dataStore.edit { preferences ->
            if (value.isNullOrBlank()) {
                preferences.remove(WEB_DAV_ENDPOINT)
            } else {
                preferences[WEB_DAV_ENDPOINT] = value
            }
        }
    }

    suspend fun setAutoSyncEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_SYNC_ENABLED] = value }
    }

    suspend fun setLastSyncAt(value: Instant?) {
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(LAST_SYNC_AT)
            else preferences[LAST_SYNC_AT] = value.toEpochMilli()
        }
    }

    suspend fun setSyncStatus(value: String) {
        dataStore.edit { preferences -> preferences[SYNC_STATUS] = value }
    }

    suspend fun setHolidaySyncStatus(value: String?) {
        dataStore.edit { preferences ->
            if (value.isNullOrBlank()) preferences.remove(HOLIDAY_SYNC_STATUS)
            else preferences[HOLIDAY_SYNC_STATUS] = value
        }
    }

    suspend fun setHolidayLastSyncAt(value: Instant?) {
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(HOLIDAY_LAST_SYNC_AT)
            else preferences[HOLIDAY_LAST_SYNC_AT] = value.toEpochMilli()
        }
    }

    suspend fun setHolidaySyncError(value: String?) {
        dataStore.edit { preferences ->
            if (value.isNullOrBlank()) preferences.remove(HOLIDAY_SYNC_ERROR)
            else preferences[HOLIDAY_SYNC_ERROR] = value
        }
    }

    suspend fun setBackgroundRuntimeGuideAcknowledged() {
        dataStore.edit { preferences ->
            preferences[BACKGROUND_RUNTIME_GUIDE_ACKNOWLEDGED] = true
        }
    }

    companion object {
        private val SEMESTER_START_DATE = stringPreferencesKey("semester_start_date")
        private val CURRENT_SEMESTER_ID = longPreferencesKey("current_semester_id")
        private val TARGET_WEIGHT_JIN = doublePreferencesKey("target_weight_jin")
        private val MENSTRUAL_CYCLE_DAYS = intPreferencesKey("menstrual_cycle_days")
        private val COURSE_REMINDER_MINUTES = intPreferencesKey("course_reminder_minutes")
        private val WEB_DAV_ENDPOINT = stringPreferencesKey("web_dav_endpoint")
        private val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        private val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        private val SYNC_STATUS = stringPreferencesKey("sync_status")
        private val HOLIDAY_SYNC_STATUS = stringPreferencesKey("holiday_sync_status")
        private val HOLIDAY_LAST_SYNC_AT = longPreferencesKey("holiday_last_sync_at")
        private val HOLIDAY_SYNC_ERROR = stringPreferencesKey("holiday_sync_error")
        private val BACKGROUND_RUNTIME_GUIDE_ACKNOWLEDGED =
            booleanPreferencesKey("background_runtime_guide_acknowledged")

        const val DEFAULT_MENSTRUAL_CYCLE_DAYS = 30
        const val MIN_MENSTRUAL_CYCLE_DAYS = 15
        const val MAX_MENSTRUAL_CYCLE_DAYS = 90

        fun create(context: Context): DailyPreferences =
            create(produceFile = { context.preferencesDataStoreFile("daily.preferences_pb") })

        fun create(
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile: () -> File
        ): DailyPreferences =
            DailyPreferences(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = produceFile
                )
            )
    }
}
