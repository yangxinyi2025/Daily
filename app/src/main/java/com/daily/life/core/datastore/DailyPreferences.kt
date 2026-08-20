package com.daily.life.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

    val defaultBudgetCents: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[DEFAULT_BUDGET_CENTS] }

    val webDavEndpoint: Flow<String?> =
        dataStore.data.map { preferences -> preferences[WEB_DAV_ENDPOINT] }

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

    suspend fun setDefaultBudgetCents(value: Long?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(DEFAULT_BUDGET_CENTS)
            } else {
                preferences[DEFAULT_BUDGET_CENTS] = value
            }
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

    companion object {
        private val SEMESTER_START_DATE = stringPreferencesKey("semester_start_date")
        private val CURRENT_SEMESTER_ID = longPreferencesKey("current_semester_id")
        private val TARGET_WEIGHT_JIN = doublePreferencesKey("target_weight_jin")
        private val DEFAULT_BUDGET_CENTS = longPreferencesKey("default_budget_cents")
        private val WEB_DAV_ENDPOINT = stringPreferencesKey("web_dav_endpoint")

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
