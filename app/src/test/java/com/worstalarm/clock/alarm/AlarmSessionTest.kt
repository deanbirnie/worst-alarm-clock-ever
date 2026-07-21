package com.worstalarm.clock.alarm

import com.worstalarm.clock.data.dao.AlarmWithSteps
import com.worstalarm.clock.data.dao.StepWithBarcode
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the miss-triggered re-ring path for the awake-check feature: a missed check
 * restarts ringing pinned at the routine's LAST step, not step 0, so the user only has to
 * rescan the final location to try the awake checks again.
 */
class AlarmSessionTest {

    @After
    fun tearDown() {
        AlarmSession.clear()
    }

    private fun stepFixture(index: Int, name: String) = StepWithBarcode(
        step = RoutineStepEntity(
            id = index.toLong(),
            alarmId = 1L,
            stepIndex = index,
            locationLabel = name,
            barcodeId = index.toLong(),
            timeToNextRingSeconds = 30
        ),
        barcode = BarcodeEntity(id = index.toLong(), name = name, rawValue = "code-$index", format = 0)
    )

    private fun threeStepAlarm() = AlarmWithSteps(
        alarm = AlarmEntity(id = 1L, label = "Test", hour = 6, minute = 0, daysMask = 0, enabled = true),
        steps = listOf(
            stepFixture(0, "Bathroom"),
            stepFixture(1, "Kitchen"),
            stepFixture(2, "Desk")
        )
    )

    @Test
    fun `default start pins at step 0`() {
        AlarmSession.start(threeStepAlarm())
        val state = AlarmSession.state.value!!
        assertEquals(0, state.currentStepIndex)
        assertTrue(state.isRingingNow)
    }

    @Test
    fun `miss re-ring starts pinned at the final step`() {
        val alarm = threeStepAlarm()
        AlarmSession.start(alarm, startAtStepIndex = alarm.orderedSteps.size - 1)

        val state = AlarmSession.state.value!!
        assertEquals(2, state.currentStepIndex)
        assertTrue(state.isLastStep)
        assertEquals("Desk", state.currentStep.displayName)
    }

    @Test
    fun `scanning the pinned final step disarms, per ScanValidator`() {
        val alarm = threeStepAlarm()
        val lastIndex = alarm.orderedSteps.size - 1
        AlarmSession.start(alarm, startAtStepIndex = lastIndex)
        val state = AlarmSession.state.value!!

        val decision = ScanValidator.decide(
            currentStepIndex = state.currentStepIndex,
            totalSteps = state.totalSteps,
            expectedRawValue = state.currentStep.barcode.rawValue,
            expectedFormat = state.currentStep.barcode.format,
            scannedStepIndex = lastIndex,
            scannedRawValue = state.currentStep.barcode.rawValue,
            scannedFormat = state.currentStep.barcode.format
        )

        assertEquals(ScanValidator.Decision.DISARM, decision)
    }
}
