package com.worstalarm.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
        // On Android 10+ background activity starts are restricted, but since we're running
        // from a user-facing alarm (AlarmManager.setAlarmClock) this path is allowed.
        context.startActivity(activityIntent)

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // (Kept for clarity; minSdk=26 means this is never taken.)
        }
    }
}
