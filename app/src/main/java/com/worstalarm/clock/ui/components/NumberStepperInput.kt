package com.worstalarm.clock.ui.components

/**
 * Pure text↔value rules for [NumberStepperField], extracted so the fiddly normalisation
 * (digits-only, length cap, empty field, clamping) is unit-testable without Compose (**C6**).
 */
object NumberStepperInput {

    /** Longest number the field accepts (matches `max = 999`). */
    const val MAX_DIGITS = 3

    /** What the field keeps from a raw keystroke edit: digits only, capped at [maxDigits]. */
    fun sanitize(raw: String, maxDigits: Int = MAX_DIGITS): String =
        raw.filter(Char::isDigit).take(maxDigits)

    /**
     * The value [digits] represents, clamped into [[min], [max]]. An empty or unparseable field
     * reads as [min] (so a momentarily-cleared field never emits a stale or negative number).
     */
    fun valueOf(digits: String, min: Int, max: Int): Int =
        (digits.toIntOrNull() ?: min).coerceIn(min, max)

    /** Clamp an arbitrary value (e.g. the result of a -/+ step) into [[min], [max]]. */
    fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)
}
