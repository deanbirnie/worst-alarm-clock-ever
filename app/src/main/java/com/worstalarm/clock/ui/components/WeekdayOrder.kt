package com.worstalarm.clock.ui.components

/**
 * Display-order mapping for the day-of-week selector: the bubbles run
 * Sunday-first (S M T W T F S) while the persisted `AlarmEntity.daysMask`
 * stays ISO (bit 0 = Monday … bit 6 = Sunday) — the exact layout
 * `AlarmScheduler.dayMatches` reads when deciding whether an alarm fires.
 *
 * Changing the DISPLAY order must never change the STORAGE order, or every
 * existing alarm would silently shift which days it rings on. This object is
 * the single place the two orders meet, kept free of Android/Compose imports
 * so plain JVM tests can pin the mapping down.
 */
object WeekdayOrder {

    const val DAY_COUNT = 7

    /** Mask bit for each display position: Sun=6, Mon=0, Tue=1 … Sat=5. */
    private val MASK_BITS = intArrayOf(6, 0, 1, 2, 3, 4, 5)

    /** Single-letter bubble labels, Sunday-first. */
    val LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")

    /** Full names for accessibility (contentDescription), same order as [LETTERS]. */
    val FULL_NAMES = listOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    fun bitFor(displayIndex: Int): Int = MASK_BITS[displayIndex]

    fun isSelected(mask: Int, displayIndex: Int): Boolean =
        (mask shr MASK_BITS[displayIndex]) and 1 == 1

    fun toggle(mask: Int, displayIndex: Int): Int =
        mask xor (1 shl MASK_BITS[displayIndex])
}
