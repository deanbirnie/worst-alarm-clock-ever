package com.worstalarm.clock.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins B11's trigger: alarms are re-armed on a timezone change and a manual clock change, and
 * on nothing else. A stray broadcast (or a null action from a malformed delivery) must not kick
 * off a re-arm.
 */
class TimeChangePolicyTest {

    @Test
    fun `a timezone change triggers a re-arm`() {
        assertTrue(TimeChangePolicy.shouldReArm("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun `a manual clock change triggers a re-arm`() {
        // Note the historical action string: ACTION_TIME_CHANGED == "...TIME_SET".
        assertTrue(TimeChangePolicy.shouldReArm("android.intent.action.TIME_SET"))
    }

    @Test
    fun `null and unrelated actions never trigger a re-arm`() {
        assertFalse(TimeChangePolicy.shouldReArm(null))
        assertFalse(TimeChangePolicy.shouldReArm(""))
        assertFalse(TimeChangePolicy.shouldReArm("android.intent.action.BOOT_COMPLETED"))
        assertFalse(TimeChangePolicy.shouldReArm("android.intent.action.TIME_TICK"))
    }

    @Test
    fun `the handled set is exactly the timezone and clock-change actions`() {
        assertEquals(
            setOf("android.intent.action.TIMEZONE_CHANGED", "android.intent.action.TIME_SET"),
            TimeChangePolicy.HANDLED_ACTIONS
        )
    }
}
