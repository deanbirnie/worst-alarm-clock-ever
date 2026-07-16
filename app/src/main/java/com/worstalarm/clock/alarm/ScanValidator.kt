package com.worstalarm.clock.alarm

/**
 * Pure decision logic for a "barcode scanned" event against the routine's current step.
 *
 * Regression guard (the reused-barcode bug, v0.2.4): ML Kit detects the same barcode on
 * several consecutive camera frames, so one physical scan used to emit several
 * SCAN_SUCCESS intents — and the service advanced one step per intent, silently skipping
 * steps. A path of bathroom → kitchen → bathroom → desk would jump from kitchen straight
 * to desk. Scan events therefore carry the step index they were captured for plus the
 * scanned barcode, and anything stale or mismatched is ignored.
 *
 * Kept free of Android types so plain JVM unit tests can pin the behavior down.
 */
object ScanValidator {

    enum class Decision {
        /** Correct barcode for the current step; more steps remain. */
        ADVANCE,
        /** Correct barcode for the final step; the alarm is done. */
        DISARM,
        /** Stale duplicate or wrong barcode; do nothing. */
        IGNORE
    }

    fun decide(
        currentStepIndex: Int,
        totalSteps: Int,
        expectedRawValue: String,
        expectedFormat: Int,
        scannedStepIndex: Int,
        scannedRawValue: String?,
        scannedFormat: Int
    ): Decision {
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
