package com.worstalarm.clock.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the alarm-list day summary wording introduced in the v0.4 redesign. */
class DaySummaryFormatterTest {

    @Test
    fun `empty mask is one-time`() {
        assertEquals("One-time", DaySummaryFormatter.format(0))
    }

    @Test
    fun `named patterns collapse to words`() {
        assertEquals("Every day", DaySummaryFormatter.format(0b1111111))
        assertEquals("Weekdays", DaySummaryFormatter.format(0b0011111))
        assertEquals("Weekends", DaySummaryFormatter.format(0b1100000))
    }

    @Test
    fun `arbitrary selections list days Sunday-first`() {
        // Sunday (bit 6) + Wednesday (bit 2) + Friday (bit 4): Sunday leads even
        // though it's the highest bit, matching the selector's display order.
        val mask = (1 shl 6) or (1 shl 2) or (1 shl 4)
        assertEquals("Sun · Wed · Fri", DaySummaryFormatter.format(mask))
    }

    @Test
    fun `single day`() {
        assertEquals("Mon", DaySummaryFormatter.format(0b0000001))
        assertEquals("Sun", DaySummaryFormatter.format(0b1000000))
    }
}
