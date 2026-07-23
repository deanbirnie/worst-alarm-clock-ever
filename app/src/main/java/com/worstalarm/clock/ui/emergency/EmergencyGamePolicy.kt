package com.worstalarm.clock.ui.emergency

import kotlin.random.Random

/**
 * Pure rules for the emergency disarm mini-game, extracted so they're unit-testable (**C1**) and
 * so completion is unambiguous (**B6**).
 *
 * B6: the screen used to fire `onComplete()` from a `DisposableEffect(taps)` — a side effect in
 * composition that re-ran on every recomposition at/above the target, so a tap past 500 fired
 * completion again. The screen now (a) stops accepting taps once [isComplete] and (b) latches
 * completion behind a one-shot flag, so `onComplete()` fires exactly once. [acceptsTap] is the
 * gate that enforces (a).
 */
object EmergencyGamePolicy {

    /** Taps required to disarm. */
    const val TARGET_TAPS = 500

    /** Idle window after which the counter resets and the alarm resumes. */
    const val IDLE_TIMEOUT_MS = 30_000L

    fun isComplete(taps: Int): Boolean = taps >= TARGET_TAPS

    /** Once complete, further taps are ignored — this is what stops a 501st tap re-firing completion. */
    fun acceptsTap(taps: Int): Boolean = taps < TARGET_TAPS

    fun isIdleTimedOut(nowMs: Long, lastTapAtMs: Long): Boolean =
        nowMs - lastTapAtMs >= IDLE_TIMEOUT_MS

    /**
     * The next square to light, chosen uniformly from the [cellCount] cells *except* [current],
     * so the lit square always visibly moves. Draws one of [cellCount] − 1 slots and skips over
     * the current index.
     */
    fun nextLitIndex(current: Int, cellCount: Int, random: Random = Random): Int {
        var next = random.nextInt(cellCount - 1)
        if (next >= current) next += 1
        return next
    }
}
