package com.daily.life.feature.settings

import com.daily.life.core.database.SemesterDao
import com.daily.life.core.datastore.DailyPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class WebDavConfigInput(
    val endpoint: String,
    val username: String,
    val password: String
)

data class SemesterSettingsSummary(
    val name: String?,
    val startDate: LocalDate?
)

interface SemesterSettingsRepository {
    val currentSemester: Flow<SemesterSettingsSummary>
}

object EmptySemesterSettingsRepository : SemesterSettingsRepository {
    override val currentSemester: Flow<SemesterSettingsSummary> =
        flowOf(SemesterSettingsSummary(name = null, startDate = null))
}

class DaoSemesterSettingsRepository(
    semesterDao: SemesterDao,
    preferences: DailyPreferences
) : SemesterSettingsRepository {
    override val currentSemester: Flow<SemesterSettingsSummary> =
        combine(
            semesterDao.observeAll(),
            preferences.currentSemesterId
        ) { semesters, currentSemesterId ->
            val currentSemester = semesters.firstOrNull { it.id == currentSemesterId }
                ?: semesters.firstOrNull { it.isCurrent }

            SemesterSettingsSummary(
                name = currentSemester?.name,
                startDate = currentSemester?.startDate
            )
        }
}
