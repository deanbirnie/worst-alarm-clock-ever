package com.worstalarm.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.worstalarm.clock.WorstAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext as WorstAlarmApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = app.repository.getAllEnabledAlarms().map { it.alarm }
                AlarmScheduler.rescheduleAll(context, alarms)
            } finally {
                pending.finish()
            }
        }
    }
}
