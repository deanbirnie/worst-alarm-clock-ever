package com.worstalarm.clock.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down B2: with a single global [AlarmSession], a second alarm firing while a first is
 * ringing must NOT overwrite the first. The active alarm wins; the incoming one is deferred
 * (re-armed later) rather than dropped. A duplicate delivery of the active alarm just re-rings.
 */
class AlarmAdmissionPolicyTest {

    @Test
    fun `no active alarm rings the incoming one`() {
        assertEquals(
            AlarmAdmissionPolicy.Decision.RING_NEW,
            AlarmAdmissionPolicy.decide(activeAlarmId = null, incomingAlarmId = 5L)
        )
    }

    @Test
    fun `same alarm re-delivered re-rings the current step, not a fresh start`() {
        assertEquals(
            AlarmAdmissionPolicy.Decision.RERING_CURRENT,
            AlarmAdmissionPolicy.decide(activeAlarmId = 5L, incomingAlarmId = 5L)
        )
    }

    @Test
    fun `a different alarm firing while one is active is deferred, never allowed to clobber it`() {
        // The whole point of B2: alarm 7 must not replace the ringing alarm 5.
        assertEquals(
            AlarmAdmissionPolicy.Decision.DEFER_INCOMING,
            AlarmAdmissionPolicy.decide(activeAlarmId = 5L, incomingAlarmId = 7L)
        )
    }

    @Test
    fun `an invalid incoming id is never rung`() {
        for (bad in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertEquals(
                "id $bad should be INVALID with no active alarm",
                AlarmAdmissionPolicy.Decision.INVALID,
                AlarmAdmissionPolicy.decide(activeAlarmId = null, incomingAlarmId = bad)
            )
            assertEquals(
                "id $bad should be INVALID and must not disturb the active alarm",
                AlarmAdmissionPolicy.Decision.INVALID,
                AlarmAdmissionPolicy.decide(activeAlarmId = 5L, incomingAlarmId = bad)
            )
        }
    }

    @Test
    fun `deferUntilMs pushes the retry out by the retry interval`() {
        assertEquals(1_000L + AlarmAdmissionPolicy.DEFER_RETRY_MS, AlarmAdmissionPolicy.deferUntilMs(1_000L))
    }

    @Test
    fun `the defer interval is a real, forward delay`() {
        // A non-positive interval would re-fire immediately and busy-loop against the active alarm.
        assertTrue(AlarmAdmissionPolicy.DEFER_RETRY_MS > 0L)
        assertTrue(AlarmAdmissionPolicy.deferUntilMs(0L) > 0L)
    }

    // ---- B3: awake-check timeline collisions ----

    @Test
    fun `a missed check re-rings when nothing else is ringing`() {
        assertEquals(
            AlarmAdmissionPolicy.AwakeReRing.RE_RING,
            AlarmAdmissionPolicy.decideAwakeCheckReRing(activeAlarmId = null, awakeAlarmId = 5L)
        )
    }

    @Test
    fun `a missed check defers rather than clobber a different ringing alarm`() {
        // The B3 fix: the miss re-ring must not overwrite alarm 9's live session.
        assertEquals(
            AlarmAdmissionPolicy.AwakeReRing.DEFER,
            AlarmAdmissionPolicy.decideAwakeCheckReRing(activeAlarmId = 9L, awakeAlarmId = 5L)
        )
    }

    @Test
    fun `a missed check for the alarm already ringing is dropped, not double-started`() {
        assertEquals(
            AlarmAdmissionPolicy.AwakeReRing.ALREADY_RINGING,
            AlarmAdmissionPolicy.decideAwakeCheckReRing(activeAlarmId = 5L, awakeAlarmId = 5L)
        )
    }

    @Test
    fun `the awake-check popup is deferred whenever any alarm is ringing, shown when none is`() {
        assertTrue(AlarmAdmissionPolicy.deferAwakeCheckShow(activeAlarmId = 9L))
        assertTrue(AlarmAdmissionPolicy.deferAwakeCheckShow(activeAlarmId = 5L))
        assertFalse(AlarmAdmissionPolicy.deferAwakeCheckShow(activeAlarmId = null))
    }
}
