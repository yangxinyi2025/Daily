package com.daily.life.core

import android.content.Context
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.core.notification.AndroidReminderScheduler
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

    override val repositories: RepositoryFactories = RepositoryFactories(
        healthRepositoryFactory = DeferredFactory {
            com.daily.life.feature.health.HealthRepository(
                healthDao = database.healthDao(),
                preferences = preferences,
                activitySources = {
                    listOfNotNull(
                        adapters.healthConnectAdapterFactory.create(),
                        adapters.sensorActivityAdapterFactory.create()
                    )
                }
            )
        }
    )
    override val adapters: AdapterFactories = AdapterFactories(
        reminderSchedulerFactory = DeferredFactory {
            AndroidReminderScheduler(
                context = context,
                database = database
            )
        },
        healthConnectAdapterFactory = DeferredFactory {
            com.daily.life.feature.health.HealthConnectAdapter(context)
        },
        sensorActivityAdapterFactory = DeferredFactory {
            com.daily.life.feature.health.SensorActivityAdapter(context)
        }
    )
}

data class RepositoryFactories(
    val timetableRepositoryFactory: ComponentFactory<TimetableRepository> = DeferredFactory(),
    val scheduleRepositoryFactory: ComponentFactory<ScheduleRepository> = DeferredFactory(),
    val healthRepositoryFactory: ComponentFactory<com.daily.life.feature.health.HealthRepository> = DeferredFactory(),
    val billRepositoryFactory: ComponentFactory<BillRepository> = DeferredFactory()
)

data class AdapterFactories(
    val reminderSchedulerFactory: ComponentFactory<ReminderScheduler> = DeferredFactory(),
    val healthConnectAdapterFactory: ComponentFactory<com.daily.life.feature.health.HealthConnectAdapter> = DeferredFactory(),
    val sensorActivityAdapterFactory: ComponentFactory<com.daily.life.feature.health.SensorActivityAdapter> = DeferredFactory(),
    val webDavClientFactory: ComponentFactory<WebDavClient> = DeferredFactory(),
    val deepSeekAdviceClientFactory: ComponentFactory<DeepSeekAdviceClient> = DeferredFactory()
)

interface TimetableRepository
interface ScheduleRepository
interface BillRepository
typealias ReminderScheduler = com.daily.life.core.notification.ReminderScheduler

object NoOpReminderScheduler : ReminderScheduler
interface WebDavClient
interface DeepSeekAdviceClient

interface ComponentFactory<T : Any> {
    fun create(): T?
}

class DeferredFactory<T : Any>(private val provider: () -> T? = { null }) : ComponentFactory<T> {
    override fun create(): T? = provider()
}
