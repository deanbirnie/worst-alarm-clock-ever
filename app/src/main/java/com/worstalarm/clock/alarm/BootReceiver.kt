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
                rearmAwakeChecks(context, app)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Re-arms any awake-check cycles that were mid-flight when the device rebooted / the
     * process died. A popup that was already showing (popupDeadlineAtMs set) re-arms its
     * miss deadline; otherwise the next popup's show time is re-armed. Either re-arm can
     * land in the past — AlarmManager fires past-due exact alarms immediately, so an
     * overdue check resolves (as a miss, or by showing) the moment this finishes rather
     * than silently vanishing.
     */
    private suspend fun rearmAwakeChecks(context: Context, app: WorstAlarmApp) {
        app.repository.getAllAwakeChecks().forEach { row ->
            if (row.popupDeadlineAtMs > 0L) {
                AlarmScheduler.scheduleAwakeCheckTimeout(context, row.alarmId, row.popupDeadlineAtMs)
            } else {
                AlarmScheduler.scheduleAwakeCheck(
                    context, row.alarmId, row.nextCheckAtMs, row.dismissedCount
                )
            }
        }
    }
}
