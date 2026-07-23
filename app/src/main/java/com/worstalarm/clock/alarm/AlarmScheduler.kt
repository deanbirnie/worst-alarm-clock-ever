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
    private const val EXTRA_DISMISSED_COUNT = "dismissed_count"
    private const val EXTRA_DEADLINE = "deadline_at_ms"

    /** Fired when an alarm's scheduled time arrives (step 0 of the routine). */
    const val ACTION_FIRE = "com.worstalarm.clock.ALARM_FIRE"

    /** Fired when the quiet pause between routine steps ends and the next step must ring. */
    const val ACTION_STEP_RING = "com.worstalarm.clock.STEP_RING"

    /** Fired when a post-routine "are you awake" popup is due to appear. */
    const val ACTION_AWAKE_CHECK = "com.worstalarm.clock.AWAKE_CHECK"

    /** Fired when a shown awake-check popup's dismiss deadline expires. */
    const val ACTION_AWAKE_CHECK_TIMEOUT = "com.worstalarm.clock.AWAKE_CHECK_TIMEOUT"

    fun alarmIdFromIntent(intent: Intent?): Long? =
        intent?.takeIf { it.hasExtra(EXTRA_ALARM_ID) }?.getLongExtra(EXTRA_ALARM_ID, -1L)
            ?.takeIf { it > 0 }

    fun dismissedCountFromIntent(intent: Intent?): Int =
        intent?.getIntExtra(EXTRA_DISMISSED_COUNT, 0) ?: 0

    fun deadlineFromIntent(intent: Intent?): Long =
        intent?.getLongExtra(EXTRA_DEADLINE, -1L) ?: -1L

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

    /**
     * Re-arms a plain alarm ring (routine step 0) at [triggerAtMs], reusing the alarm's normal
     * ACTION_FIRE PendingIntent. Used to *defer* an alarm that fired while a different one was
     * already ringing (see [AlarmAdmissionPolicy]) so it isn't silently lost — it re-fires once
     * the active alarm is done. Safe to reuse the schedule PendingIntent: the alarm's own
     * one-shot occurrence has already been consumed by the fire that triggered this, and a
     * recurring alarm isn't re-armed for its next occurrence until it completes.
     */
    fun scheduleRingAt(context: Context, alarmId: Long, triggerAtMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntentFor(context, alarmId, create = true) ?: return
        val showIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            Intent(context, MainActivity::class.java),
            pendingIntentFlags()
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent), pi)
    }

    /**
     * Arms the ring for the NEXT routine step at [triggerAtMs]. Uses setAlarmClock —
     * not Handler.postDelayed — because a Handler's uptime clock pauses in deep sleep:
     * with the screen off, the ring would silently wait until the user woke the phone.
     * setAlarmClock wakes the device from any sleep/Doze state, exactly on time.
     */
    fun scheduleStepRing(context: Context, alarmId: Long, triggerAtMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = stepPendingIntent(context, alarmId, create = true) ?: return
        val showIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            Intent(context, MainActivity::class.java),
            pendingIntentFlags()
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent), pi)
    }

    fun cancelStepRing(context: Context, alarmId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        stepPendingIntent(context, alarmId, create = false)?.let { am.cancel(it) }
    }

    /**
     * Arms the next "are you awake" popup. [dismissedCount] (0 or 1) travels with the
     * PendingIntent's extras purely so the receiver can show "check 1 of 2" / "check 2 of
     * 2" the instant the popup appears, without waiting on an async DB read first.
     */
    fun scheduleAwakeCheck(context: Context, alarmId: Long, triggerAtMs: Long, dismissedCount: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = awakeCheckPendingIntent(context, alarmId, dismissedCount, create = true) ?: return
        val showIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            Intent(context, MainActivity::class.java),
            pendingIntentFlags()
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent), pi)
    }

    fun cancelAwakeCheck(context: Context, alarmId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        awakeCheckPendingIntent(context, alarmId, dismissedCount = 0, create = false)?.let { am.cancel(it) }
    }

    /** Arms the miss deadline for a popup that's currently shown. */
    fun scheduleAwakeCheckTimeout(context: Context, alarmId: Long, deadlineAtMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = awakeCheckTimeoutPendingIntent(context, alarmId, deadlineAtMs, create = true) ?: return
        val showIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            Intent(context, MainActivity::class.java),
            pendingIntentFlags()
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(deadlineAtMs, showIntent), pi)
    }

    fun cancelAwakeCheckTimeout(context: Context, alarmId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // The deadline value isn't part of PendingIntent equality (only action/data/component
        // are), so any placeholder works here for the FLAG_NO_CREATE lookup.
        awakeCheckTimeoutPendingIntent(context, alarmId, 0L, create = false)?.let { am.cancel(it) }
    }

    fun rescheduleAll(context: Context, alarms: List<AlarmEntity>) {
        alarms.forEach { if (it.enabled) schedule(context, it) else cancel(context, it.id) }
    }

    private fun pendingIntentFor(context: Context, alarmId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            // Setting data makes each alarm's PendingIntent unique even if extras alone wouldn't.
            data = android.net.Uri.parse("worstalarm://alarm/$alarmId")
        }
        val flags = pendingIntentFlags() or (if (!create) PendingIntent.FLAG_NO_CREATE else 0)
        return PendingIntent.getBroadcast(context, alarmId.toInt(), intent, flags)
    }

    // Distinct action + data URI keep this PendingIntent separate from the alarm's own
    // schedule, so arming a step ring never clobbers the recurring alarm (or vice versa).
    private fun stepPendingIntent(context: Context, alarmId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_STEP_RING
            putExtra(EXTRA_ALARM_ID, alarmId)
            data = android.net.Uri.parse("worstalarm://alarm/$alarmId/step")
        }
        val flags = pendingIntentFlags() or (if (!create) PendingIntent.FLAG_NO_CREATE else 0)
        return PendingIntent.getBroadcast(context, alarmId.toInt(), intent, flags)
    }

    private fun awakeCheckPendingIntent(
        context: Context, alarmId: Long, dismissedCount: Int, create: Boolean
    ): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_AWAKE_CHECK
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_DISMISSED_COUNT, dismissedCount)
            data = android.net.Uri.parse("worstalarm://alarm/$alarmId/awake-check")
        }
        val flags = pendingIntentFlags() or (if (!create) PendingIntent.FLAG_NO_CREATE else 0)
        return PendingIntent.getBroadcast(context, alarmId.toInt(), intent, flags)
    }

    private fun awakeCheckTimeoutPendingIntent(
        context: Context, alarmId: Long, deadlineAtMs: Long, create: Boolean
    ): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_AWAKE_CHECK_TIMEOUT
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_DEADLINE, deadlineAtMs)
            data = android.net.Uri.parse("worstalarm://alarm/$alarmId/awake-check-timeout")
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
