package com.worstalarm.clock.ui.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pins the emergency mini-game rules (**C1**) and, in particular, that completion happens exactly
 * once (**B6**): the tap gate stops the counter at the target, so it can never advance past it and
 * re-trigger disarm.
 */
class EmergencyGamePolicyTest {

    @Test
    fun `isComplete is true only at or above the target`() {
        assertFalse(EmergencyGamePolicy.isComplete(EmergencyGamePolicy.TARGET_TAPS - 1))
        assertTrue(EmergencyGamePolicy.isComplete(EmergencyGamePolicy.TARGET_TAPS))
        assertTrue(EmergencyGamePolicy.isComplete(EmergencyGamePolicy.TARGET_TAPS + 1))
    }

    @Test
    fun `taps are accepted until the target, then refused`() {
        assertTrue(EmergencyGamePolicy.acceptsTap(0))
        assertTrue(EmergencyGamePolicy.acceptsTap(EmergencyGamePolicy.TARGET_TAPS - 1))
        assertFalse(EmergencyGamePolicy.acceptsTap(EmergencyGamePolicy.TARGET_TAPS))
        assertFalse(EmergencyGamePolicy.acceptsTap(EmergencyGamePolicy.TARGET_TAPS + 1))
    }

    @Test
    fun `completion fires exactly once even under extra tap attempts (B6)`() {
        // Model the screen: the gate blocks taps past the target; the one-shot latch fires once.
        var taps = 0
        var latched = false
        var completeCount = 0
        repeat(EmergencyGamePolicy.TARGET_TAPS + 100) {
            if (EmergencyGamePolicy.acceptsTap(taps)) taps += 1
            if (!latched && EmergencyGamePolicy.isComplete(taps)) {
                latched = true
                completeCount += 1
            }
        }
        assertEquals("taps must never exceed the target", EmergencyGamePolicy.TARGET_TAPS, taps)
        assertEquals("onComplete must fire exactly once", 1, completeCount)
    }

    @Test
    fun `idle timeout triggers at or past the window, not before`() {
        assertFalse(EmergencyGamePolicy.isIdleTimedOut(nowMs = 1_000, lastTapAtMs = 1_000)) // just tapped
        assertFalse(
            EmergencyGamePolicy.isIdleTimedOut(
                nowMs = EmergencyGamePolicy.IDLE_TIMEOUT_MS - 1, lastTapAtMs = 0
            )
        )
        assertTrue(
            EmergencyGamePolicy.isIdleTimedOut(nowMs = EmergencyGamePolicy.IDLE_TIMEOUT_MS, lastTapAtMs = 0)
        )
        assertTrue(
            EmergencyGamePolicy.isIdleTimedOut(nowMs = EmergencyGamePolicy.IDLE_TIMEOUT_MS + 1, lastTapAtMs = 0)
        )
    }

    @Test
    fun `nextLitIndex always moves to a different in-range cell`() {
        val cellCount = 16
        for (current in 0 until cellCount) {
            repeat(200) { seed ->
                val next = EmergencyGamePolicy.nextLitIndex(current, cellCount, Random(seed + current * 1000))
                assertNotEquals("must move off the current cell", current, next)
                assertTrue("must stay in range", next in 0 until cellCount)
            }
        }
    }

    @Test
    fun `nextLitIndex can reach every cell except the current one`() {
        val cellCount = 16
        val current = 5
        val reached = (0 until 5000)
            .map { EmergencyGamePolicy.nextLitIndex(current, cellCount, Random(it)) }
            .toSet()
        assertEquals((0 until cellCount).toSet() - current, reached)
    }
}
