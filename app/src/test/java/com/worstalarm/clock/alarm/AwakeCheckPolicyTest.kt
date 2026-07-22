package com.worstalarm.clock.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pins down the awake-check feature's decision logic: 2 required dismissals, each next
 * check randomized 5-15 minutes out, and the race guard that stops an already-resolved
 * popup's timeout from being treated as a miss.
 */
class AwakeCheckPolicyTest {

    @Test
    fun `randomIntervalMs always falls within 5 to 15 minutes inclusive`() {
        repeat(1000) { seed ->
            val ms = AwakeCheckPolicy.randomIntervalMs(Random(seed))
            assertTrue("was $ms for seed $seed", ms >= AwakeCheckPolicy.MIN_INTERVAL_MS)
            assertTrue("was $ms for seed $seed", ms <= AwakeCheckPolicy.MAX_INTERVAL_MS)
        }
    }

    @Test
    fun `randomIntervalMs is not a constant (actually random across seeds)`() {
        val values = (0 until 50).map { seed -> AwakeCheckPolicy.randomIntervalMs(Random(seed)) }.toSet()
        assertTrue("expected varied intervals, got $values", values.size > 1)
    }

    @Test
    fun `required dismissals is exactly two, per spec`() {
        assertEquals(2, AwakeCheckPolicy.REQUIRED_DISMISSALS)
    }

    @Test
    fun `popup ack window is three minutes`() {
        assertEquals(3 * 60_000L, AwakeCheckPolicy.POPUP_TIMEOUT_MS)
    }

    @Test
    fun `nudge interval is 30 seconds`() {
        assertEquals(30_000L, AwakeCheckPolicy.NUDGE_INTERVAL_MS)
    }

    @Test
    fun `first nudge fires immediately on show`() {
        assertEquals(0L, AwakeCheckPolicy.nudgeOffsetsMs().first())
    }

    @Test
    fun `nudges repeat every interval and never reach the deadline`() {
        val offsets = AwakeCheckPolicy.nudgeOffsetsMs()
        // Evenly spaced by the interval.
        offsets.zipWithNext { a, b -> assertEquals(AwakeCheckPolicy.NUDGE_INTERVAL_MS, b - a) }
        // All strictly inside the ack window — a nudge at/after the deadline would race the miss.
        assertTrue("last nudge ${offsets.last()} must be < window", offsets.all { it < AwakeCheckPolicy.POPUP_TIMEOUT_MS })
    }

    @Test
    fun `the window fits several gentle nudges, not just one`() {
        // The whole point of the fix: you don't have to watch the screen for a single silent
        // popup. Expect a handful of repeats across the window.
        assertEquals(6, AwakeCheckPolicy.nudgeOffsetsMs().size)
    }

    @Test
    fun `first dismissal schedules the second check`() {
        val outcome = AwakeCheckPolicy.onDismiss(currentDismissedCount = 0)
        assertEquals(AwakeCheckPolicy.DismissOutcome.ScheduleNext(1), outcome)
    }

    @Test
    fun `second dismissal completes the cycle`() {
        val outcome = AwakeCheckPolicy.onDismiss(currentDismissedCount = 1)
        assertEquals(AwakeCheckPolicy.DismissOutcome.CycleComplete, outcome)
    }

    @Test
    fun `timeout matching the persisted deadline is a genuine miss`() {
        assertTrue(
            AwakeCheckPolicy.isGenuineMiss(
                persistedDeadlineAtMs = 1_000L,
                timeoutFiredForDeadlineAtMs = 1_000L
            )
        )
    }

    @Test
    fun `timeout for a stale deadline (already superseded) is ignored`() {
        // Regression guard: a dismiss that lands right as an old timeout is in flight must
        // not be misread as a miss for the popup the user just dismissed.
        assertFalse(
            AwakeCheckPolicy.isGenuineMiss(
                persistedDeadlineAtMs = 2_000L,
                timeoutFiredForDeadlineAtMs = 1_000L
            )
        )
    }

    @Test
    fun `no popup pending (deadline zero) is never a genuine miss`() {
        assertFalse(
            AwakeCheckPolicy.isGenuineMiss(
                persistedDeadlineAtMs = 0L,
                timeoutFiredForDeadlineAtMs = 0L
            )
        )
    }

    @Test
    fun `a full cycle is two dismissals from zero, not one`() {
        val first = AwakeCheckPolicy.onDismiss(0)
        check(first is AwakeCheckPolicy.DismissOutcome.ScheduleNext)
        val second = AwakeCheckPolicy.onDismiss(first.newDismissedCount)
        assertEquals(AwakeCheckPolicy.DismissOutcome.CycleComplete, second)
    }
}
