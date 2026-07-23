package com.worstalarm.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.worstalarm.clock.WorstAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms every enabled alarm when the timezone or the system clock changes (**B11**).
 *
 * `AlarmManager.setAlarmClock` pins an absolute instant; after travel across timezones or a
 * manual clock change that instant no longer matches the intended local time, so the alarm
 * would fire hours early or late. A timezone change doesn't reboot, so the boot re-arm path
 * never runs. This mirrors [BootReceiver]'s re-arm, gated by the unit-tested [TimeChangePolicy].
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!TimeChangePolicy.shouldReArm(intent?.action)) return

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
