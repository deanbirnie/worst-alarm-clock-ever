package com.worstalarm.clock.alarm

import com.worstalarm.clock.data.dao.AlarmWithSteps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global state for the currently-ringing alarm. Shared between [AlarmService],
 * [AlarmActivity], and [OverlayService].
 *
 * Exactly one alarm can be ringing at a time. Fields are only mutated from the
 * main thread via [AlarmService].
 */
object AlarmSession {

    /** Idle resets of the emergency game allowed before it stops silencing the alarm. */
    const val MAX_FREE_IDLE_RESETS = 3

    data class State(
        val alarmWithSteps: AlarmWithSteps,
        /** Index into orderedSteps of the step the user is currently trying to scan. */
        val currentStepIndex: Int,
        /** True while we're ringing + waiting for a scan. False while counting down between rings. */
        val isRingingNow: Boolean,
        /** Unix ms when the next ring will start (relevant only while !isRingingNow). */
        val nextRingAtMs: Long,
        /** True while the user is in the 500-tap emergency mini-game (audio muted, no re-ring). */
        val inEmergencyMode: Boolean,
        /** Total number of times the user has successfully scanned a step (for the mini-game reset logic). */
        val scansCompleted: Int,
        /**
         * Times the emergency mini-game was reset by its 30 s idle timeout this session.
         * At [MAX_FREE_IDLE_RESETS] the game stops muting the alarm: it keeps ringing
         * while the user taps.
         */
        val emergencyIdleResets: Int
    ) {
        val currentStep get() = alarmWithSteps.orderedSteps[currentStepIndex]
        val totalSteps get() = alarmWithSteps.orderedSteps.size
        val isLastStep get() = currentStepIndex == totalSteps - 1
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value != null

    /**
     * [startAtStepIndex] defaults to the top of the routine. A missed awake check re-rings
     * pinned at the last step instead — the user already proved they can reach every
     * earlier location; only the final one needs rescanning to try the awake checks again.
     */
    fun start(alarmWithSteps: AlarmWithSteps, startAtStepIndex: Int = 0) {
        _state.value = State(
            alarmWithSteps = alarmWithSteps,
            currentStepIndex = startAtStepIndex,
            isRingingNow = true,
            nextRingAtMs = 0L,
            inEmergencyMode = false,
            scansCompleted = 0,
            emergencyIdleResets = 0
        )
    }

    fun update(block: (State) -> State) {
        val current = _state.value ?: return
        _state.value = block(current)
    }

    fun clear() {
        _state.value = null
    }
}
