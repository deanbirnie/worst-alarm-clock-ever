package com.worstalarm.clock.alarm

import com.worstalarm.clock.data.entity.AlarmEntity
import java.util.Calendar

/**
 * Formats "how long until this alarm rings" for the little confirmation note shown
 * after saving an alarm ("Rings in 7 h 32 min"). Delegates the WHEN to
 * [AlarmScheduler.computeNextTriggerMs] so the note can never disagree with what
 * AlarmManager was actually armed with.
 *
 * Kept free of Android types so plain JVM unit tests can pin the behavior down,
 * mirroring [ScanValidator] and [AwakeCheckPolicy].
 */
object NextRingFormatter {

    /**
     * Human-readable countdown to [alarm]'s next firing, or null when the alarm is
     * disabled (or has no next occurrence) — callers show nothing in that case.
     */
    fun format(alarm: AlarmEntity, now: Calendar = Calendar.getInstance()): String? {
        val triggerAt = AlarmScheduler.computeNextTriggerMs(alarm, now) ?: return null
        return formatDelta(triggerAt - now.timeInMillis)
    }

    /**
     * Renders a positive millisecond delta as "Rings in …". Minutes round UP so the
     * note never promises less time than the user actually has: an alarm 61 s out
     * says "2 min", not "1 min", and only a sub-minute delta says "less than a minute".
     */
    fun formatDelta(deltaMs: Long): String {
        if (deltaMs < 60_000L) return "Rings in less than a minute"

        val totalMinutes = (deltaMs + 59_999L) / 60_000L
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        val parts = buildList {
            if (days > 0) add(if (days == 1L) "1 day" else "$days days")
            if (hours > 0) add("$hours h")
            // Below a day the minutes matter; past a day they're noise ("2 days 3 h 7 min").
            if (days == 0L && minutes > 0) add("$minutes min")
        }
        return "Rings in ${parts.joinToString(" ")}"
    }
}
