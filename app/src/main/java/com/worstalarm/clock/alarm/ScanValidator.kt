package com.worstalarm.clock.alarm

/**
 * Pure decision logic for a "barcode scanned" event against the routine's current step.
 *
 * Two guards:
 *  - **Timed steps** ([ringActive]): a step's barcode only counts while that step is actually
 *    ringing. Between steps the routine is counting down to the next ring, and a scan then is
 *    ignored — otherwise someone could run around scanning every location at once and go back to
 *    bed. You must let each location's full pause elapse before its barcode will register.
 *  - **Reused-barcode guard** (v0.2.4): ML Kit detects the same barcode on several consecutive
 *    camera frames, so one physical scan used to emit several SCAN_SUCCESS intents — and the
 *    service advanced one step per intent, silently skipping steps. A path of bathroom → kitchen
 *    → bathroom → desk would jump from kitchen straight to desk. Scan events therefore carry the
 *    step index they were captured for plus the scanned barcode, and anything stale or mismatched
 *    is ignored.
 *
 * Kept free of Android types so plain JVM unit tests can pin the behavior down.
 */
object ScanValidator {

    enum class Decision {
        /** Correct barcode for the current step; more steps remain. */
        ADVANCE,
        /** Correct barcode for the final step; the alarm is done. */
        DISARM,
        /** Not ringing yet, stale duplicate, or wrong barcode; do nothing. */
        IGNORE
    }

    fun decide(
        ringActive: Boolean,
        currentStepIndex: Int,
        totalSteps: Int,
        expectedRawValue: String,
        expectedFormat: Int,
        scannedStepIndex: Int,
        scannedRawValue: String?,
        scannedFormat: Int
    ): Decision {
        // The step must be ringing right now. During the between-step countdown a scan is a
        // "scan-ahead" attempt — ignore it until the pause elapses and the step actually rings.
        if (!ringActive) return Decision.IGNORE
        // A duplicate frame of an already-accepted scan arrives labeled with the OLD
        // step index. This check is what stops it advancing a second step — matching
        // on barcode value alone can't, because paths may reuse the same code.
        if (scannedStepIndex != currentStepIndex) return Decision.IGNORE
        if (scannedRawValue == null || scannedRawValue != expectedRawValue) return Decision.IGNORE
        // Format 0 (FORMAT_UNKNOWN) on the saved barcode means "match any format".
        if (expectedFormat != 0 && scannedFormat != expectedFormat) return Decision.IGNORE
        return if (currentStepIndex >= totalSteps - 1) Decision.DISARM else Decision.ADVANCE
    }
}
