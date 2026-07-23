package com.worstalarm.clock.ui.qr

import kotlin.random.Random

/** One generated code. [id] is a stable, list-unique key; [value] is the scannable payload. */
data class GeneratedCode(val id: Long, val value: String)

/**
 * Pure, Android-free generator for the QR-code screen. Extracted so the uniqueness guarantee
 * behind **B5** is unit-testable.
 *
 * The screen keys its list on [GeneratedCode.id], not [value], because two random draws *could*
 * (astronomically unlikely, but possible) produce the same value — and a duplicate Compose key
 * crashes the screen. On top of that, [newCodes] never emits a value already in the list, so the
 * user also never sees two identical codes.
 */
object QrCodeGenerator {

    private val CODE_CHARS: List<Char> = ('A'..'Z') + ('0'..'9')
    const val PREFIX = "WACE-"
    const val VALUE_LENGTH = 10

    fun randomValue(random: Random = Random): String =
        PREFIX + (1..VALUE_LENGTH).map { CODE_CHARS.random(random) }.joinToString("")

    /**
     * Returns [count] fresh codes to append to [existing]. Each has an id greater than any already
     * present (so keys stay unique even if a value repeats) and a value distinct from every value
     * already in the list and from the others in this batch.
     */
    fun newCodes(existing: List<GeneratedCode>, count: Int, random: Random = Random): List<GeneratedCode> {
        val values = existing.mapTo(mutableSetOf()) { it.value }
        var nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1
        val additions = ArrayList<GeneratedCode>(count)
        repeat(count) {
            var v = randomValue(random)
            while (!values.add(v)) v = randomValue(random) // add() is false if already present → redraw
            additions.add(GeneratedCode(id = nextId, value = v))
            nextId += 1
        }
        return additions
    }
}
