package com.daily.life.core

import android.content.Context
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.core.security.AndroidSecretStore
import com.daily.life.core.security.SecretStore

interface AppContainer {
    val database: DailyDatabase
    val preferences: DailyPreferences
    val secretStore: SecretStore
    val repositories: RepositoryFactories
    val adapters: AdapterFactories
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: DailyDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DailyDatabase.build(context)
    }

    override val preferences: DailyPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DailyPreferences.create(context)
    }

    override val secretStore: SecretStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidSecretStore(context)
    }

    override val repositories: RepositoryFactories = RepositoryFactories()
    override val adapters: AdapterFactories = AdapterFactories()
}

data class RepositoryFactories(
    val timetableRepositoryFactory: DeferredFactory = DeferredFactory("TimetableRepository"),
    val scheduleRepositoryFactory: DeferredFactory = DeferredFactory("ScheduleRepository"),
    val healthRepositoryFactory: DeferredFactory = DeferredFactory("HealthRepository"),
    val billRepositoryFactory: DeferredFactory = DeferredFactory("BillRepository")
)

data class AdapterFactories(
    val reminderSchedulerFactory: DeferredFactory = DeferredFactory("ReminderScheduler"),
    val healthConnectAdapterFactory: DeferredFactory = DeferredFactory("HealthConnectAdapter"),
    val sensorActivityAdapterFactory: DeferredFactory = DeferredFactory("SensorActivityAdapter"),
    val webDavClientFactory: DeferredFactory = DeferredFactory("WebDavClient"),
    val deepSeekAdviceClientFactory: DeferredFactory = DeferredFactory("DeepSeekAdviceClient")
)

data class DeferredFactory(val componentName: String) {
    fun create(): Nothing = error("$componentName is implemented in a later rebuild task.")
}
