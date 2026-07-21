package com.worstalarm.clock.alarm

import kotlin.random.Random

/**
 * Pure decision logic for the post-routine "are you still awake?" checks.
 *
 * After the final routine step is scanned, two silent popups must be dismissed — each
 * appearing at a random point 5-15 minutes after the previous one is resolved — before
 * the alarm is considered fully off. Missing either one (an 90s dismiss deadline expires)
 * is a full reset: both checks must be earned again, from a fresh re-ring.
 *
 * Kept free of Android types so plain JVM unit tests can pin the behavior down, mirroring
 * how [ScanValidator] isolates the routine's scan-decision logic.
 */
object AwakeCheckPolicy {

    /** Successful "I'm awake" dismissals required before the alarm is fully disabled. */
    const val REQUIRED_DISMISSALS = 2

    const val MIN_INTERVAL_MS = 5 * 60_000L
    const val MAX_INTERVAL_MS = 15 * 60_000L

    /** How long a shown popup waits for a tap before it counts as missed. */
    const val POPUP_TIMEOUT_MS = 90_000L

    /** A random point 5-15 minutes out, inclusive, for when the next popup should appear. */
    fun randomIntervalMs(random: Random = Random.Default): Long =
        random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1)

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
}
