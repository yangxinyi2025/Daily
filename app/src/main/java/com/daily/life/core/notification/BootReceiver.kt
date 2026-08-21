package com.daily.life.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daily.life.DailyApplication
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as DailyApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AndroidReminderScheduler(
                    context = app,
                    database = app.container.database
                ).rescheduleFutureEvents(Instant.now())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
