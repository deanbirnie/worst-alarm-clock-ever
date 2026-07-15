package com.worstalarm.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = AlarmScheduler.alarmIdFromIntent(intent) ?: return

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_RING
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
        }
        // Direct launch works when allowed (user-facing alarm via setAlarmClock, or the
        // overlay permission is granted). If the OS blocks it, the full-screen intent on
        // the service's notification is the fallback path that surfaces the alarm UI.
        try {
            context.startActivity(activityIntent)
        } catch (_: Throwable) {
            // Full-screen intent fallback handles it.
        }
    }
}
