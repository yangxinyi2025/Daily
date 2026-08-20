package com.daily.life.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class DailyPreferences private constructor(
    private val dataStore: DataStore<Preferences>
) {
    val currentSemesterId: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[CURRENT_SEMESTER_ID] }

    val targetWeightJin: Flow<Int?> =
        dataStore.data.map { preferences -> preferences[TARGET_WEIGHT_JIN] }

    val defaultBudgetCents: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[DEFAULT_BUDGET_CENTS] }

    suspend fun setCurrentSemesterId(value: Long?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(CURRENT_SEMESTER_ID)
            } else {
                preferences[CURRENT_SEMESTER_ID] = value
            }
        }
    }

    suspend fun setTargetWeightJin(value: Int?) {
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

    companion object {
        private val CURRENT_SEMESTER_ID = longPreferencesKey("current_semester_id")
        private val TARGET_WEIGHT_JIN = intPreferencesKey("target_weight_jin")
        private val DEFAULT_BUDGET_CENTS = longPreferencesKey("default_budget_cents")

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
