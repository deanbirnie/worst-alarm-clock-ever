package com.worstalarm.clock.alarm

import com.worstalarm.clock.data.entity.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * Pins down the "Rings in …" note shown after saving an alarm: delta formatting
 * (round-up minutes, day/hour/minute composition) and the end-to-end path through
 * [AlarmScheduler.computeNextTriggerMs] with a fixed clock.
 */
class NextRingFormatterTest {

    // -------- formatDelta --------

    @Test
    fun `sub-minute delta says less than a minute`() {
        assertEquals("Rings in less than a minute", NextRingFormatter.formatDelta(0L))
        assertEquals("Rings in less than a minute", NextRingFormatter.formatDelta(59_999L))
    }

    @Test
    fun `exactly one minute`() {
        assertEquals("Rings in 1 min", NextRingFormatter.formatDelta(60_000L))
    }

    @Test
    fun `minutes round UP so the note never promises less time than the user has`() {
        // 61 s → "2 min", not "1 min": we'd rather overstate slightly than have the
        // alarm fire before the note said it would.
        assertEquals("Rings in 2 min", NextRingFormatter.formatDelta(61_000L))
        assertEquals("Rings in 25 min", NextRingFormatter.formatDelta(24 * 60_000L + 1))
    }

    @Test
    fun `whole hours omit the minutes part`() {
        assertEquals("Rings in 1 h", NextRingFormatter.formatDelta(60 * 60_000L))
        assertEquals("Rings in 8 h", NextRingFormatter.formatDelta(8 * 60 * 60_000L))
    }

    @Test
    fun `hours and minutes compose`() {
        val ms = (7 * 60 + 32) * 60_000L
        assertEquals("Rings in 7 h 32 min", NextRingFormatter.formatDelta(ms))
    }

    @Test
    fun `past a day, minutes are dropped as noise`() {
        val oneDay = 24 * 60 * 60_000L
        assertEquals("Rings in 1 day", NextRingFormatter.formatDelta(oneDay))
        assertEquals("Rings in 1 day 4 h", NextRingFormatter.formatDelta(oneDay + 4 * 60 * 60_000L))
        // 2 days 3 h 7 min → the 7 min is dropped, hours are kept.
        assertEquals(
            "Rings in 2 days 3 h",
            NextRingFormatter.formatDelta(2 * oneDay + 3 * 60 * 60_000L + 7 * 60_000L)
        )
    }

    // -------- format (end-to-end with computeNextTriggerMs) --------

    private fun fixedNow(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            // Land on a known Monday, then walk to the wanted weekday.
            set(2026, Calendar.JULY, 20, hour, minute, 0) // 2026-07-20 is a Monday
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != dayOfWeek) add(Calendar.DAY_OF_YEAR, 1)
        }

    private fun alarm(hour: Int, minute: Int, daysMask: Int = 0, enabled: Boolean = true) =
        AlarmEntity(id = 1L, label = "", hour = hour, minute = minute, daysMask = daysMask, enabled = enabled)

    @Test
    fun `one-shot alarm later today`() {
        val now = fixedNow(Calendar.MONDAY, 6, 28)
        assertEquals("Rings in 7 h 32 min", NextRingFormatter.format(alarm(14, 0), now))
    }

    @Test
    fun `one-shot alarm whose time already passed rolls to tomorrow`() {
        val now = fixedNow(Calendar.MONDAY, 8, 0)
        // 07:00 already passed → next occurrence is tomorrow 07:00, 23 h out.
        assertEquals("Rings in 23 h", NextRingFormatter.format(alarm(7, 0), now))
    }

    @Test
    fun `recurring alarm skips to its next enabled weekday`() {
        val now = fixedNow(Calendar.MONDAY, 12, 0)
        // Friday-only alarm (bit 4), Monday noon → Friday 07:00 is 3 days 19 h out.
        val fridayOnly = alarm(7, 0, daysMask = 1 shl 4)
        assertEquals("Rings in 3 days 19 h", NextRingFormatter.format(fridayOnly, now))
    }

    @Test
    fun `disabled alarm produces no note`() {
        val now = fixedNow(Calendar.MONDAY, 6, 0)
        assertNull(NextRingFormatter.format(alarm(7, 0, enabled = false), now))
    }
}
