package com.worstalarm.clock.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C5: the foreground service stops **only** when nothing is active — neither a ringing/mid-routine
 * alarm nor an awake-check popup. Any active state keeps it foreground; both idle is the B1 stuck
 * state and must stop.
 */
class ForegroundStartPolicyTest {

    @Test
    fun `nothing active - stop`() {
        assertTrue(ForegroundStartPolicy.shouldStop(sessionActive = false, awakeActive = false))
    }

    @Test
    fun `a ringing session keeps it foreground`() {
        assertFalse(ForegroundStartPolicy.shouldStop(sessionActive = true, awakeActive = false))
    }

    @Test
    fun `an active awake check keeps it foreground`() {
        assertFalse(ForegroundStartPolicy.shouldStop(sessionActive = false, awakeActive = true))
    }

    @Test
    fun `both active keeps it foreground`() {
        assertFalse(ForegroundStartPolicy.shouldStop(sessionActive = true, awakeActive = true))
    }
}
