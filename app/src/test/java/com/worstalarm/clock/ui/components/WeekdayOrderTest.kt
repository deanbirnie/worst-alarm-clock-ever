package com.worstalarm.clock.ui.components

import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.alarm.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Regression guard for the v0.4 redesign's Sunday-first day selector: the bubbles
 * changed DISPLAY order, but the persisted daysMask must keep its ISO layout
 * (bit 0 = Monday … bit 6 = Sunday) or every existing alarm would silently ring
 * on the wrong days.
 */
class WeekdayOrderTest {

    @Test
    fun `display runs Sunday-first`() {
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), WeekdayOrder.LETTERS)
        assertEquals("Sunday", WeekdayOrder.FULL_NAMES.first())
        assertEquals("Saturday", WeekdayOrder.FULL_NAMES.last())
        assertEquals(7, WeekdayOrder.DAY_COUNT)
    }

    @Test
    fun `display position maps to the ISO mask bit, not the display index`() {
        // Sunday is the FIRST bubble but the LAST mask bit — the crux of the mapping.
        assertEquals(6, WeekdayOrder.bitFor(0)) // Sunday
        assertEquals(0, WeekdayOrder.bitFor(1)) // Monday
        assertEquals(4, WeekdayOrder.bitFor(5)) // Friday
        assertEquals(5, WeekdayOrder.bitFor(6)) // Saturday
    }

    @Test
    fun `toggling the Sunday bubble sets bit 6, leaving Monday alone`() {
        val mask = WeekdayOrder.toggle(0, 0)
        assertEquals(0b1000000, mask)
        assertTrue(WeekdayOrder.isSelected(mask, 0))
        assertFalse(WeekdayOrder.isSelected(mask, 1))
    }

    @Test
    fun `toggle is its own inverse`() {
        var mask = 0b0011111 // weekdays
        mask = WeekdayOrder.toggle(mask, 3) // Wednesday off
        assertEquals(0b0011011, mask)
        mask = WeekdayOrder.toggle(mask, 3) // Wednesday back on
        assertEquals(0b0011111, mask)
    }

    @Test
    fun `selecting every bubble produces the full ISO mask`() {
        var mask = 0
        repeat(WeekdayOrder.DAY_COUNT) { mask = WeekdayOrder.toggle(mask, it) }
        assertEquals(0b1111111, mask)
    }

    @Test
    fun `a mask built through the selector fires on the day the user tapped`() {
        // End-to-end: tap the FIRST bubble (Sunday) and the scheduler must pick a
        // Sunday — proving display order and storage order stay decoupled.
        val sundayOnly = WeekdayOrder.toggle(0, 0)
        val alarm = AlarmEntity(
            id = 1L, label = "", hour = 7, minute = 0, daysMask = sundayOnly, enabled = true
        )
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 20, 12, 0, 0) // a Monday, noon
            set(Calendar.MILLISECOND, 0)
        }
        val triggerAt = AlarmScheduler.computeNextTriggerMs(alarm, now)!!
        val fires = Calendar.getInstance().apply { timeInMillis = triggerAt }
        assertEquals(Calendar.SUNDAY, fires.get(Calendar.DAY_OF_WEEK))
    }
}
