package com.daily.life.core

import android.content.Context
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.core.security.AndroidSecretStore
import com.daily.life.core.security.SecretStore
import com.daily.life.feature.bill.BillRepository

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
                preferences = preferences
            )
        },
        billRepositoryFactory = DeferredFactory {
            BillRepository(
                database = database,
                preferences = preferences
            )
        }
    )
    override val adapters: AdapterFactories = AdapterFactories()
}

data class RepositoryFactories(
    val timetableRepositoryFactory: ComponentFactory<TimetableRepository> = DeferredFactory(),
    val scheduleRepositoryFactory: ComponentFactory<ScheduleRepository> = DeferredFactory(),
    val healthRepositoryFactory: ComponentFactory<com.daily.life.feature.health.HealthRepository> = DeferredFactory(),
    val billRepositoryFactory: ComponentFactory<BillRepository> = DeferredFactory()
)

data class AdapterFactories(
    val reminderSchedulerFactory: ComponentFactory<ReminderScheduler> = DeferredFactory()
)

interface TimetableRepository
interface ScheduleRepository
typealias ReminderScheduler = com.daily.life.core.notification.ReminderScheduler

object NoOpReminderScheduler : ReminderScheduler

interface ComponentFactory<T : Any> {
    fun create(): T?
}

class DeferredFactory<T : Any>(private val provider: () -> T? = { null }) : ComponentFactory<T> {
    override fun create(): T? = provider()
}
