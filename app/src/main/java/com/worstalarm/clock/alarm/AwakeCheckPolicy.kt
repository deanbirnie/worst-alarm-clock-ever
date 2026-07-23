package com.worstalarm.clock.alarm

import kotlin.random.Random

/**
 * Pure decision logic for the post-routine "are you still awake?" checks.
 *
 * After the final routine step is scanned, two popups must be dismissed — each appearing at a
 * random point 5-15 minutes after the previous one is resolved — before the alarm is considered
 * fully off. While a popup is showing, a *gentle* cue (a soft chime + light buzz, never
 * alarm-grade) repeats every [NUDGE_INTERVAL_MS] so the user notices without having to watch the
 * screen. Missing a popup (its [POPUP_TIMEOUT_MS] dismiss deadline expires) is a full reset: both
 * checks must be earned again, from a fresh re-ring.
 *
 * Kept free of Android types so plain JVM unit tests can pin the behavior down, mirroring
 * how [ScanValidator] isolates the routine's scan-decision logic.
 */
object AwakeCheckPolicy {

    /** Successful "I'm awake" dismissals required before the alarm is fully disabled. */
    const val REQUIRED_DISMISSALS = 2

    const val MIN_INTERVAL_MS = 5 * 60_000L
    const val MAX_INTERVAL_MS = 15 * 60_000L

    /**
     * How long a shown popup waits for the "I'm awake" tap before it counts as missed. The
     * gentle cue repeats across this whole window (see [nudgeOffsetsMs]), so it's long enough to
     * notice and respond to while up and moving, yet short enough that you can't meaningfully
     * have drifted back to sleep.
     */
    const val POPUP_TIMEOUT_MS = 3 * 60_000L

    /** Spacing between the gentle "still awake?" nudges within the [POPUP_TIMEOUT_MS] window. */
    const val NUDGE_INTERVAL_MS = 30_000L

    /** A random point 5-15 minutes out, inclusive, for when the next popup should appear. */
    fun randomIntervalMs(random: Random = Random.Default): Long =
        random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1)

    /**
     * Offsets from popup-show at which a gentle nudge fires: the first at 0 (immediately on
     * show), then one every [NUDGE_INTERVAL_MS] until the ack window closes — never at or past
     * the deadline. Pure so the "repeats a few times, always within the window" contract is
     * unit-testable independently of the service's live loop.
     */
    fun nudgeOffsetsMs(): List<Long> =
        generateSequence(0L) { it + NUDGE_INTERVAL_MS }
            .takeWhile { it < POPUP_TIMEOUT_MS }
            .toList()

    sealed class DismissOutcome {
        /** Another check remains; schedule it and persist the new dismissed count. */
        data class ScheduleNext(val newDismissedCount: Int) : DismissOutcome()
        /** Both checks are done — the alarm is fully disabled, nothing left to schedule. */
        data object CycleComplete : DismissOutcome()
    }

    fun onDismiss(currentDismissedCount: Int): DismissOutcome {
        val newCount = currentDismissedCount + 1
        return if (newCount >= REQUIRED_DISMISSALS) DismissOutcome.CycleComplete
        else DismissOutcome.ScheduleNext(newCount)
    }

    /**
     * A timeout alarm fires unconditionally once scheduled — it can't be "un-broadcast" if
     * the user dismisses right before it lands. This is the authoritative check for whether
     * a fired timeout is a genuine miss: only true if a popup is still actually pending
     * ([persistedDeadlineAtMs] non-zero) and this timeout was scheduled for that exact
     * popup, not one already resolved or superseded.
     */
    fun isGenuineMiss(persistedDeadlineAtMs: Long, timeoutFiredForDeadlineAtMs: Long): Boolean =
        persistedDeadlineAtMs != 0L && persistedDeadlineAtMs == timeoutFiredForDeadlineAtMs

    /**
     * The alarm id an "I'm awake" tap should dismiss.
     *
     * Regression guard (0.4.4→0.4.5): [AwakeCheckActivity] can be surfaced three ways — the
     * receiver's direct launch (carries the id), the foreground notification's full-screen
     * intent, and a tap on that notification. The latter two are launched by a PendingIntent
     * that, before this fix, carried **no** id, so the activity read -1 and the dismiss it sent
     * was silently dropped by [AlarmService.handleAwakeCheckDismiss] (`alarmId <= 0`) — the
     * button "did nothing" and the check was scored a miss. So the live [AwakeCheckSession] (set
     * the moment a popup is shown, independent of how the activity was launched) is the source of
     * truth; the launching intent's extra is only a fallback. A non-positive result means "no
     * active check to dismiss".
     */
    fun resolveDismissTarget(sessionAlarmId: Long?, intentAlarmId: Long): Long =
        sessionAlarmId?.takeIf { it > 0 }
            ?: intentAlarmId.takeIf { it > 0 }
            ?: -1L
}
