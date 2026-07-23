package com.worstalarm.clock.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pins **B5**: the generated-code list must have unique keys. Values are random and could (very
 * rarely) repeat, so the list keys on a unique id — and, as a bonus, values are deduped so the
 * user never sees the same code twice.
 */
class QrCodeGeneratorTest {

    private val alphabet = (('A'..'Z') + ('0'..'9')).toSet()

    @Test
    fun `randomValue has the prefix, fixed length, and only uses the code alphabet`() {
        repeat(200) { seed ->
            val v = QrCodeGenerator.randomValue(Random(seed))
            assertTrue(v.startsWith(QrCodeGenerator.PREFIX))
            val body = v.removePrefix(QrCodeGenerator.PREFIX)
            assertEquals(QrCodeGenerator.VALUE_LENGTH, body.length)
            assertTrue("unexpected chars in $body", body.all { it in alphabet })
        }
    }

    @Test
    fun `newCodes returns the requested count`() {
        assertEquals(1, QrCodeGenerator.newCodes(emptyList(), 1, Random(1)).size)
        assertEquals(3, QrCodeGenerator.newCodes(emptyList(), 3, Random(1)).size)
    }

    @Test
    fun `ids are unique and larger than any existing id`() {
        val existing = listOf(GeneratedCode(id = 7, value = "WACE-EXISTING0"))
        val added = QrCodeGenerator.newCodes(existing, 3, Random(42))
        // Every new id is greater than the current max (7) and strictly increasing.
        assertTrue(added.all { it.id > 7 })
        assertEquals(listOf(8L, 9L, 10L), added.map { it.id })
    }

    @Test
    fun `keys stay unique across successive append-then-generate calls`() {
        // Mirrors the screen: append each batch to the list, then generate more from it.
        var codes = emptyList<GeneratedCode>()
        val rnd = Random(99)
        repeat(50) {
            codes = codes + QrCodeGenerator.newCodes(codes, 2, rnd)
        }
        val ids = codes.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `a new code never repeats a value already in the list`() {
        val existing = QrCodeGenerator.newCodes(emptyList(), 500, Random(3))
        val existingValues = existing.map { it.value }.toSet()
        val more = QrCodeGenerator.newCodes(existing, 100, Random(4))
        assertTrue("new values must not collide with existing ones",
            more.none { it.value in existingValues })
        // …and the batch itself has no internal duplicates.
        val batchValues = more.map { it.value }
        assertEquals(batchValues.size, batchValues.toSet().size)
    }

    @Test
    fun `a large batch has all-unique ids and values`() {
        val codes = QrCodeGenerator.newCodes(emptyList(), 2000, Random(7))
        assertEquals(2000, codes.map { it.id }.toSet().size)
        assertEquals(2000, codes.map { it.value }.toSet().size)
    }

    @Test
    fun `an empty list starts ids at one`() {
        val added = QrCodeGenerator.newCodes(emptyList(), 1, Random(1))
        assertEquals(1L, added.single().id)
        assertFalse(added.single().value.isBlank())
    }
}
