package com.worstalarm.clock.ui.components

/**
 * One-line summary of an alarm's repeat days for the alarm list, reading the
 * ISO daysMask (bit 0 = Monday … bit 6 = Sunday). Named patterns collapse to
 * words ("Every day", "Weekdays", "Weekends"); anything else lists the chosen
 * days Sunday-first, matching the selector's display order.
 *
 * Pure JVM (no Android imports) so tests can pin the wording down.
 */
object DaySummaryFormatter {

    private const val ALL_DAYS = 0b1111111
    private const val WEEKDAYS = 0b0011111 // Mon-Fri = bits 0-4
    private const val WEEKENDS = 0b1100000 // Sat, Sun = bits 5-6

    /** Sunday-first (bit, abbreviation) pairs — same order the selector displays. */
    private val SUNDAY_FIRST = listOf(
        6 to "Sun", 0 to "Mon", 1 to "Tue", 2 to "Wed", 3 to "Thu", 4 to "Fri", 5 to "Sat"
    )

    fun format(mask: Int): String = when (mask) {
        0 -> "One-time"
        ALL_DAYS -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        else -> SUNDAY_FIRST
            .filter { (bit, _) -> (mask shr bit) and 1 == 1 }
            .joinToString(" · ") { it.second }
    }
}
