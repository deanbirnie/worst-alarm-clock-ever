package com.worstalarm.clock.alarm

import com.worstalarm.clock.alarm.ScanValidator.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the reused-barcode step-skipping bug (v0.2.4).
 *
 * ML Kit reports a barcode once per camera frame it appears in, so one physical scan
 * can emit several SCAN_SUCCESS events. Before validation was added, the service
 * advanced one step per event: with the path bathroom(B) → kitchen(K) → bathroom(B) →
 * desk(D), a duplicate frame of the kitchen scan silently consumed the second bathroom
 * step and the routine went bathroom → kitchen → desk.
 */
class ScanValidatorTest {

    private data class StepDef(val raw: String, val format: Int)

    // The reporter's exact path: barcode reused at steps 0 and 2.
    private val bathroomKitchenBathroomDesk = listOf(
        StepDef("BATHROOM-CODE", QR),
        StepDef("KITCHEN-CODE", QR),
        StepDef("BATHROOM-CODE", QR),
        StepDef("DESK-CODE", QR)
    )

    private fun decide(
        path: List<StepDef>,
        currentStepIndex: Int,
        scannedStepIndex: Int,
        scannedRaw: String?,
        scannedFormat: Int = QR
    ): Decision = ScanValidator.decide(
        currentStepIndex = currentStepIndex,
        totalSteps = path.size,
        expectedRawValue = path[currentStepIndex].raw,
        expectedFormat = path[currentStepIndex].format,
        scannedStepIndex = scannedStepIndex,
        scannedRawValue = scannedRaw,
        scannedFormat = scannedFormat
    )

    @Test
    fun `correct scan advances mid-path step`() {
        assertEquals(
            Decision.ADVANCE,
            decide(bathroomKitchenBathroomDesk, 1, 1, "KITCHEN-CODE")
        )
    }

    @Test
    fun `reused-barcode regression - duplicate kitchen frame cannot consume the second bathroom step`() {
        // Frame 1 of the kitchen scan was accepted; the session is now at step 2
        // (bathroom again). Frame 2 arrives still labeled step 1 with the kitchen code.
        // Value check alone rejects it here (kitchen != bathroom), but the step-index
        // check is what documents the staleness.
        assertEquals(
            Decision.IGNORE,
            decide(bathroomKitchenBathroomDesk, 2, 1, "KITCHEN-CODE")
        )
    }

    @Test
    fun `duplicate frame is ignored even when consecutive steps share the same barcode`() {
        // Path with the SAME code on steps 0 and 1: value matching cannot tell a
        // duplicate frame from a genuine second scan — only the step index can.
        val doubleBathroom = listOf(
            StepDef("BATHROOM-CODE", QR),
            StepDef("BATHROOM-CODE", QR),
            StepDef("DESK-CODE", QR)
        )
        // Step 0 accepted, session now at step 1; a stale frame labeled step 0 arrives
        // carrying the very barcode step 1 expects. It must NOT advance.
        assertEquals(Decision.IGNORE, decide(doubleBathroom, 1, 0, "BATHROOM-CODE"))
        // A fresh scan labeled with the current step is what advances.
        assertEquals(Decision.ADVANCE, decide(doubleBathroom, 1, 1, "BATHROOM-CODE"))
    }

    @Test
    fun `wrong barcode is ignored`() {
        assertEquals(
            Decision.IGNORE,
            decide(bathroomKitchenBathroomDesk, 0, 0, "DESK-CODE")
        )
        assertEquals(
            Decision.IGNORE,
            decide(bathroomKitchenBathroomDesk, 0, 0, null)
        )
    }

    @Test
    fun `format must match unless the saved barcode's format is unknown`() {
        assertEquals(
            Decision.IGNORE,
            decide(bathroomKitchenBathroomDesk, 0, 0, "BATHROOM-CODE", scannedFormat = EAN13)
        )
        val anyFormatPath = listOf(StepDef("BATHROOM-CODE", FORMAT_UNKNOWN), StepDef("DESK-CODE", QR))
        assertEquals(
            Decision.ADVANCE,
            decide(anyFormatPath, 0, 0, "BATHROOM-CODE", scannedFormat = EAN13)
        )
    }

    @Test
    fun `correct scan on the final step disarms`() {
        assertEquals(
            Decision.DISARM,
            decide(bathroomKitchenBathroomDesk, 3, 3, "DESK-CODE")
        )
    }

    @Test
    fun `full walk of the reported path advances exactly one step per accepted scan`() {
        val path = bathroomKitchenBathroomDesk
        var current = 0
        // Simulate each physical scan arriving as THREE duplicate frames (all labeled
        // with the step index the UI was on when the camera saw the code).
        while (current < path.size) {
            val labeled = current
            val raw = path[current].raw
            val decisions = (1..3).map { decide(path, current, labeled, raw) }
            // First frame acts...
            when (decisions[0]) {
                Decision.ADVANCE -> current += 1
                Decision.DISARM -> current = path.size
                Decision.IGNORE -> throw AssertionError("genuine scan ignored at step $labeled")
            }
            // ...and once the session has moved on, the remaining duplicates must be inert.
            if (current < path.size) {
                (1..2).forEach { i ->
                    assertEquals(
                        "duplicate frame $i after step $labeled must be ignored",
                        Decision.IGNORE,
                        decide(path, current, labeled, raw)
                    )
                }
            }
        }
        assertEquals(path.size, current)
    }

    private companion object {
        // Mirrors ML Kit's Barcode.FORMAT_* ints without an Android dependency.
        const val QR = 256
        const val EAN13 = 32
        const val FORMAT_UNKNOWN = 0
    }
}
