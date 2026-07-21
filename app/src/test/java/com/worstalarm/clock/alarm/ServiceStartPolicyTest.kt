package com.worstalarm.clock.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down B1: AlarmService must reject a start it can't handle instead of sitting in the
 * foreground with no alarm active. A null action (what a sticky restart would have delivered)
 * and any unrecognised action both resolve to STOP; every real action resolves to PROCEED.
 */
class ServiceStartPolicyTest {

    @Test
    fun `null action stops (the sticky-restart case B1)`() {
        assertEquals(ServiceStartPolicy.Decision.STOP, ServiceStartPolicy.decide(null))
    }

    @Test
    fun `unrecognised action stops`() {
        assertEquals(ServiceStartPolicy.Decision.STOP, ServiceStartPolicy.decide("com.worstalarm.NOPE"))
        assertEquals(ServiceStartPolicy.Decision.STOP, ServiceStartPolicy.decide(""))
        // A real Android system action that isn't ours (kept as a literal so this stays a pure
        // JVM test with no Android classpath dependency).
        assertEquals(
            ServiceStartPolicy.Decision.STOP,
            ServiceStartPolicy.decide("android.intent.action.BOOT_COMPLETED")
        )
    }

    @Test
    fun `every handled action proceeds`() {
        for (action in ServiceStartPolicy.HANDLED_ACTIONS) {
            assertEquals(
                "expected PROCEED for $action",
                ServiceStartPolicy.Decision.PROCEED,
                ServiceStartPolicy.decide(action)
            )
        }
    }

    @Test
    fun `each real AlarmService action is recognised as handled`() {
        // If someone wires a new ACTION_* into onStartCommand's when-block, it must be added
        // here too, or the policy gate will silently stop it. This is the guard for that.
        val expected = setOf(
            AlarmService.ACTION_RING,
            AlarmService.ACTION_STEP_RING,
            AlarmService.ACTION_SCAN_SUCCESS,
            AlarmService.ACTION_ENTER_EMERGENCY,
            AlarmService.ACTION_EXIT_EMERGENCY,
            AlarmService.ACTION_EMERGENCY_IDLE_RESET,
            AlarmService.ACTION_EMERGENCY_COMPLETE,
            AlarmService.ACTION_DISARM,
            AlarmService.ACTION_AWAKE_CHECK_SHOW,
            AlarmService.ACTION_AWAKE_CHECK_DISMISS,
            AlarmService.ACTION_AWAKE_CHECK_TIMEOUT
        )
        assertEquals(expected, ServiceStartPolicy.HANDLED_ACTIONS)
    }
}
