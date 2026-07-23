package com.worstalarm.clock.alarm

import com.worstalarm.clock.data.entity.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Pins **B9**: `computeNextTriggerMs` decides when you wake up, and its trickiest branches —
 * the exact-minute boundary, weekday-mask wrap, and the two DST transitions — were previously
 * unverified (the older `NextRingFormatterTest` deliberately uses July dates to *avoid* DST).
 *
 * The DST cases assert the **wall-clock time** the alarm fires at (rendered back in the same
 * zone), because that's what a user actually cares about: "my 6:30 alarm still goes off at 6:30
 * the morning the clocks change." The one genuinely ambiguous case — an alarm set inside the
 * nonexistent spring-forward hour — only asserts the safe invariants (fires, in the future,
 * same morning), since resolving a wall-clock time that doesn't exist is lenient-Calendar
 * defined, not something we want to hard-code.
 */
class AlarmSchedulerNextTriggerTest {

    // Day-of-week bits, matching AlarmScheduler.dayMatches (bit 0 = Monday … bit 6 = Sunday).
    private val WED = 1 shl 2
    private val EVERY_DAY = 0b111_1111

    private fun alarm(hour: Int, minute: Int, daysMask: Int) =
        AlarmEntity(id = 1, label = "t", hour = hour, minute = minute, daysMask = daysMask, enabled = true)

    /** Runs [block] with the JVM default zone pinned — [computeNextTriggerMs] builds its
     *  candidate in the default zone, so the test's `now` must share it. */
    private fun <T> withZone(id: String, block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            return block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /** A Calendar in the current default zone for the given wall-clock fields (seconds settable). */
    private fun cal(year: Int, month0: Int, day: Int, hour: Int, min: Int, sec: Int = 0): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month0, day, hour, min, sec)
        }

    // ---- Exact-minute boundary (UTC, no DST so the arithmetic is exact) ----

    @Test
    fun `one-shot alarm exactly at now rolls to tomorrow, never fires immediately`() = withZone("UTC") {
        val now = cal(2025, Calendar.JUNE, 10, 7, 0)
        val result = AlarmScheduler.computeNextTriggerMs(alarm(7, 0, daysMask = 0), now)
        // candidate == now is NOT "after now", so it must advance a full day.
        assertEquals(now.timeInMillis + 24L * 60 * 60 * 1000, result)
    }

    @Test
    fun `one-shot alarm a minute before now still fires today`() = withZone("UTC") {
        val now = cal(2025, Calendar.JUNE, 10, 6, 59)
        val result = AlarmScheduler.computeNextTriggerMs(alarm(7, 0, daysMask = 0), now)
        assertEquals(now.timeInMillis + 60L * 1000, result)
    }

    @Test
    fun `one-shot alarm one second past its minute rolls to tomorrow`() = withZone("UTC") {
        // Seconds are zeroed in the candidate, so 07:00:00 is already behind now=07:00:01.
        val now = cal(2025, Calendar.JUNE, 10, 7, 0, 1)
        val result = AlarmScheduler.computeNextTriggerMs(alarm(7, 0, daysMask = 0), now)
        val r = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(11, r.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, r.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, r.get(Calendar.MINUTE))
        assertEquals(0, r.get(Calendar.SECOND))
    }

    // ---- Weekday-mask wrap (UTC) ----

    @Test
    fun `recurring alarm returns the next matching weekday`() = withZone("UTC") {
        // 2025-06-09 is a Monday; the alarm is Wednesdays only.
        val now = cal(2025, Calendar.JUNE, 9, 8, 0)
        val result = AlarmScheduler.computeNextTriggerMs(alarm(7, 0, daysMask = WED), now)
        val r = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.WEDNESDAY, r.get(Calendar.DAY_OF_WEEK))
        assertEquals(11, r.get(Calendar.DAY_OF_MONTH)) // 2025-06-11
        assertEquals(7, r.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `recurring weekly alarm exactly at now skips to next week, not this instant`() = withZone("UTC") {
        // now IS this week's Wednesday 07:00 — the next trigger must be a week out.
        val now = cal(2025, Calendar.JUNE, 11, 7, 0)
        val result = AlarmScheduler.computeNextTriggerMs(alarm(7, 0, daysMask = WED), now)
        assertEquals(now.timeInMillis + 7L * 24 * 60 * 60 * 1000, result)
    }

    // ---- DST: America/New_York ----

    @Test
    fun `daily alarm fires at the right wall time the morning after spring-forward`() = withZone("America/New_York") {
        // Clocks jump 02:00 -> 03:00 on 2025-03-09. A 06:30 alarm is safely outside the gap.
        val now = cal(2025, Calendar.MARCH, 8, 7, 0) // Sat, before the change
        val result = AlarmScheduler.computeNextTriggerMs(alarm(6, 30, daysMask = EVERY_DAY), now)
        val r = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(9, r.get(Calendar.DAY_OF_MONTH))     // Sunday the 9th
        assertEquals(6, r.get(Calendar.HOUR_OF_DAY))      // still 06:30, not 05:30 or 07:30
        assertEquals(30, r.get(Calendar.MINUTE))
        assertTrue(result!! > now.timeInMillis)
    }

    @Test
    fun `daily alarm fires at the right wall time the morning after fall-back`() = withZone("America/New_York") {
        // Clocks fall 02:00 -> 01:00 on 2025-11-02. A 06:30 alarm is outside the repeated hour.
        val now = cal(2025, Calendar.NOVEMBER, 1, 7, 0)
        val result = AlarmScheduler.computeNextTriggerMs(alarm(6, 30, daysMask = EVERY_DAY), now)
        val r = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(2, r.get(Calendar.DAY_OF_MONTH))
        assertEquals(6, r.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, r.get(Calendar.MINUTE))
        assertTrue(result!! > now.timeInMillis)
    }

    @Test
    fun `alarm inside the nonexistent spring-forward hour still returns a valid future time`() =
        withZone("America/New_York") {
            // 02:30 does not exist on 2025-03-09. The exact resolved wall time is lenient-Calendar
            // defined; what must hold is that we return a real, future, same-morning time (not null,
            // not the past) — returning null here would mean the alarm silently never fires.
            val now = cal(2025, Calendar.MARCH, 8, 12, 0)
            val result = AlarmScheduler.computeNextTriggerMs(alarm(2, 30, daysMask = EVERY_DAY), now)
            assertNotNull(result)
            assertTrue("must be in the future", result!! > now.timeInMillis)
            assertTrue("must be within the next day", result < now.timeInMillis + 24L * 60 * 60 * 1000)
        }

    // ---- Disabled ----

    @Test
    fun `a disabled alarm never returns a trigger`() = withZone("UTC") {
        val disabled = alarm(7, 0, daysMask = EVERY_DAY).copy(enabled = false)
        assertEquals(null, AlarmScheduler.computeNextTriggerMs(disabled, cal(2025, Calendar.JUNE, 10, 6, 0)))
    }
}
