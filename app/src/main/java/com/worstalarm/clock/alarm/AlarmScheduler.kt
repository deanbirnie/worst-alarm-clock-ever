package com.worstalarm.clock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.worstalarm.clock.MainActivity
import com.worstalarm.clock.data.entity.AlarmEntity
import java.util.Calendar

object AlarmScheduler {

    private const val EXTRA_ALARM_ID = "alarm_id"

    fun alarmIdFromIntent(intent: Intent?): Long? =
        intent?.takeIf { it.hasExtra(EXTRA_ALARM_ID) }?.getLongExtra(EXTRA_ALARM_ID, -1L)
            ?.takeIf { it > 0 }

    fun schedule(context: Context, alarm: AlarmEntity) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = computeNextTriggerMs(alarm) ?: return

        val pi = pendingIntentFor(context, alarm.id, create = true) ?: return

        // setAlarmClock: the strongest wake alarm; also tells the system this is a user-facing
        // alarm clock, which bypasses Doze without needing SCHEDULE_EXACT_ALARM on 12+.
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, MainActivity::class.java),
            pendingIntentFlags()
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
    }

    fun cancel(context: Context, alarmId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        pendingIntentFor(context, alarmId, create = false)?.let { am.cancel(it) }
    }

    fun rescheduleAll(context: Context, alarms: List<AlarmEntity>) {
        alarms.forEach { if (it.enabled) schedule(context, it) else cancel(context, it.id) }
    }

    private fun pendingIntentFor(context: Context, alarmId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.worstalarm.clock.ALARM_FIRE"
            putExtra(EXTRA_ALARM_ID, alarmId)
            // Setting data makes each alarm's PendingIntent unique even if extras alone wouldn't.
            data = android.net.Uri.parse("worstalarm://alarm/$alarmId")
        }
        val flags = pendingIntentFlags() or (if (!create) PendingIntent.FLAG_NO_CREATE else 0)
        return PendingIntent.getBroadcast(context, alarmId.toInt(), intent, flags)
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

    /**
     * Returns the next firing time in epoch ms. Returns null if the alarm is disabled.
     *
     * If [AlarmEntity.daysMask] is zero, schedules the next occurrence of the time (today if
     * the time hasn't passed yet, else tomorrow).
     */
    fun computeNextTriggerMs(alarm: AlarmEntity, now: Calendar = Calendar.getInstance()): Long? {
        if (!alarm.enabled) return null

        val candidate = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.daysMask == 0) {
            if (!candidate.after(now)) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate.timeInMillis
        }

        // Check today + up to next 7 days
        repeat(8) {
            if (candidate.after(now) && dayMatches(alarm.daysMask, candidate)) {
                return candidate.timeInMillis
            }
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    private fun dayMatches(mask: Int, cal: Calendar): Boolean {
        // bit 0 = Monday, bit 6 = Sunday (ISO)
        val dowBit = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> return false
        }
        return (mask shr dowBit) and 1 == 1
    }
}
