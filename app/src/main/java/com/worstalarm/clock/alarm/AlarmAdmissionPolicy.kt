package com.worstalarm.clock.alarm

/**
 * Decides what happens when an alarm's scheduled ring is delivered, given whichever alarm
 * (if any) currently owns the single global [AlarmSession]. Pure and Android-free so the
 * collision rules are unit-testable (mirrors [ScanValidator] / [ServiceStartPolicy]).
 *
 * The engine has exactly one ringing session. Before this policy, a second alarm firing while
 * a first was still ringing / mid-routine called `AlarmSession.start()` and silently overwrote
 * the first — abandoning its routine, orphaning its pending step-ring PendingIntent, and (for a
 * recurring alarm) skipping the reschedule that only happens on completion, so it could stop
 * firing entirely (**BUG B2**). Now the alarm already ringing always wins; the incoming one is
 * **deferred** — re-armed a short time later via AlarmManager — rather than dropped, so it still
 * rings once the active alarm is done instead of being lost.
 */
object AlarmAdmissionPolicy {

    /**
     * How long to push a colliding incoming alarm out before it retries. Short enough that the
     * deferred alarm rings promptly once the active one is dealt with, long enough not to thrash:
     * while the active alarm is still ringing, each retry simply defers again.
     */
    const val DEFER_RETRY_MS = 60_000L

    enum class Decision {
        /** Nothing is active — start ringing the incoming alarm. */
        RING_NEW,

        /** The incoming alarm is the one already ringing (a duplicate delivery) — re-ring its current step. */
        RERING_CURRENT,

        /** A *different* alarm is already active — keep it, and defer the incoming one so it isn't lost. */
        DEFER_INCOMING,

        /** The incoming id is invalid (<= 0) — there is nothing to ring. */
        INVALID
    }

    fun decide(activeAlarmId: Long?, incomingAlarmId: Long): Decision = when {
        incomingAlarmId <= 0L -> Decision.INVALID
        activeAlarmId == null -> Decision.RING_NEW
        activeAlarmId == incomingAlarmId -> Decision.RERING_CURRENT
        else -> Decision.DEFER_INCOMING
    }

    /** Absolute time a deferred alarm should retry, given [nowMs]. */
    fun deferUntilMs(nowMs: Long): Long = nowMs + DEFER_RETRY_MS
}
