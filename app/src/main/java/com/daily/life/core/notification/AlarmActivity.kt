package com.daily.life.core.notification

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.daily.life.core.designsystem.DailyPageScaffold
import com.daily.life.core.designsystem.DailyPrimaryAction
import com.daily.life.core.designsystem.DailyTheme

class AlarmActivity : ComponentActivity() {
    private var eventId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = intent.getLongExtra(ReminderReceiver.EXTRA_EVENT_ID, 0L)
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty().ifBlank { "日程闹钟" }
        val message = intent.getStringExtra(ReminderReceiver.EXTRA_MESSAGE).orEmpty()
        ContextCompat.startForegroundService(
            this,
            AlarmRingtoneService.startIntent(this, eventId, title)
        )
        setContent {
            DailyTheme {
                DailyPageScaffold(title = "日程闹钟") {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = title)
                        if (message.isNotBlank()) Text(text = message)
                        DailyPrimaryAction(text = "关闭闹钟", onClick = ::closeAlarm)
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        closeAlarm()
    }

    private fun closeAlarm() {
        startService(AlarmRingtoneService.stopIntent(this, eventId))
        stopService(Intent(this, AlarmRingtoneService::class.java))
        finish()
    }
}
