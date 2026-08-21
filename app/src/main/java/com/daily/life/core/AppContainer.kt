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

    override val repositories: RepositoryFactories = RepositoryFactories()
    override val adapters: AdapterFactories = AdapterFactories(
        reminderSchedulerFactory = DeferredFactory {
            AndroidReminderScheduler(
                context = context,
                database = database
            )
        }
    )
}

data class RepositoryFactories(
    val timetableRepositoryFactory: ComponentFactory<TimetableRepository> = DeferredFactory(),
    val scheduleRepositoryFactory: ComponentFactory<ScheduleRepository> = DeferredFactory(),
    val healthRepositoryFactory: ComponentFactory<HealthRepository> = DeferredFactory(),
    val billRepositoryFactory: ComponentFactory<BillRepository> = DeferredFactory()
)

data class AdapterFactories(
    val reminderSchedulerFactory: ComponentFactory<ReminderScheduler> = DeferredFactory(),
    val healthConnectAdapterFactory: ComponentFactory<HealthConnectAdapter> = DeferredFactory(),
    val sensorActivityAdapterFactory: ComponentFactory<SensorActivityAdapter> = DeferredFactory(),
    val webDavClientFactory: ComponentFactory<WebDavClient> = DeferredFactory(),
    val deepSeekAdviceClientFactory: ComponentFactory<DeepSeekAdviceClient> = DeferredFactory()
)

interface TimetableRepository
interface ScheduleRepository
interface HealthRepository
interface BillRepository
typealias ReminderScheduler = com.daily.life.core.notification.ReminderScheduler

object NoOpReminderScheduler : ReminderScheduler
interface HealthConnectAdapter
interface SensorActivityAdapter
interface WebDavClient
interface DeepSeekAdviceClient

interface ComponentFactory<T : Any> {
    fun create(): T?
}

class DeferredFactory<T : Any>(private val provider: () -> T? = { null }) : ComponentFactory<T> {
    override fun create(): T? = provider()
}
