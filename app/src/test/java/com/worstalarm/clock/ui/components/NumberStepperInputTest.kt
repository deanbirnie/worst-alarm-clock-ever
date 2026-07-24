package com.worstalarm.clock.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** C6: pins the digit-field normalisation (digits-only, length cap, empty → min, clamping). */
class NumberStepperInputTest {

    @Test
    fun `sanitize keeps only digits`() {
        assertEquals("123", NumberStepperInput.sanitize("1a2b3"))
        assertEquals("", NumberStepperInput.sanitize("abc"))
        assertEquals("", NumberStepperInput.sanitize(""))
        assertEquals("007", NumberStepperInput.sanitize("007"))
        assertEquals("12", NumberStepperInput.sanitize(" 1 2 "))
    }

    @Test
    fun `sanitize caps the length at three digits`() {
        assertEquals("123", NumberStepperInput.sanitize("12345"))
        assertEquals("99", NumberStepperInput.sanitize("99"))
        // A custom cap (used by callers with a different max).
        assertEquals("12", NumberStepperInput.sanitize("12345", maxDigits = 2))
    }

    @Test
    fun `valueOf reads an empty or unparseable field as min`() {
        assertEquals(0, NumberStepperInput.valueOf("", min = 0, max = 999))
        assertEquals(5, NumberStepperInput.valueOf("", min = 5, max = 999))
    }

    @Test
    fun `valueOf parses and clamps into range`() {
        assertEquals(7, NumberStepperInput.valueOf("7", min = 0, max = 10))
        assertEquals(10, NumberStepperInput.valueOf("999", min = 0, max = 10))
        assertEquals(3, NumberStepperInput.valueOf("1", min = 3, max = 10)) // below min → min
        assertEquals(7, NumberStepperInput.valueOf("007", min = 0, max = 10)) // leading zeros
    }

    @Test
    fun `clamp keeps a value within range`() {
        assertEquals(0, NumberStepperInput.clamp(-4, min = 0, max = 10))
        assertEquals(10, NumberStepperInput.clamp(50, min = 0, max = 10))
        assertEquals(6, NumberStepperInput.clamp(6, min = 0, max = 10))
    }

    @Test
    fun `clamp models the minus and plus steppers hitting the bounds`() {
        // -/+ buttons call clamp(value ± step); at the edges the value should pin, not overflow.
        assertEquals(0, NumberStepperInput.clamp(0 - 1, min = 0, max = 10)) // minus at floor
        assertEquals(10, NumberStepperInput.clamp(10 + 1, min = 0, max = 10)) // plus at ceiling
        assertEquals(5, NumberStepperInput.clamp(4 + 1, min = 0, max = 10)) // normal step
    }
}
