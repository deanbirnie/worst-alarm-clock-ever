package com.worstalarm.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = AlarmScheduler.alarmIdFromIntent(intent) ?: return
        val action = intent?.action

        val serviceAction = when (action) {
            AlarmScheduler.ACTION_STEP_RING -> AlarmService.ACTION_STEP_RING
            AlarmScheduler.ACTION_AWAKE_CHECK -> AlarmService.ACTION_AWAKE_CHECK_SHOW
            AlarmScheduler.ACTION_AWAKE_CHECK_TIMEOUT -> AlarmService.ACTION_AWAKE_CHECK_TIMEOUT
            else -> AlarmService.ACTION_RING
        }
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = serviceAction
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_DISMISSED_COUNT, AlarmScheduler.dismissedCountFromIntent(intent))
            putExtra(AlarmService.EXTRA_DEADLINE, AlarmScheduler.deadlineFromIntent(intent))
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // The silent awake-check popup is a lightweight, non-lockdown screen; every other
        // AlarmManager-driven event (including a missed awake check, which rings the alarm
        // again) shows the full ringing lockdown screen.
        val activityClass = if (action == AlarmScheduler.ACTION_AWAKE_CHECK)
            AwakeCheckActivity::class.java else AlarmActivity::class.java

        val activityIntent = Intent(context, activityClass).apply {
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
