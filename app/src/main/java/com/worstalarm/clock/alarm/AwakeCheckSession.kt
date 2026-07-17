package com.worstalarm.clock.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mirrors [AlarmSession] but for the post-routine "are you awake" popups: just enough
 * live state for [AwakeCheckActivity] to know which alarm/check number to show, and to
 * auto-finish once [AlarmService] resolves it (dismissed, superseded, or missed).
 *
 * Unlike [AlarmSession], durable cross-process state (dismissed count, scheduled times,
 * the current popup's deadline) lives in Room (`AwakeCheckEntity`) so a killed process
 * doesn't lose a pending check — this object is only the in-memory signal for the UI.
 */
object AwakeCheckSession {

    data class State(val alarmId: Long, val checkNumber: Int)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    fun show(alarmId: Long, checkNumber: Int) {
        _state.value = State(alarmId, checkNumber)
    }

    fun clear() {
        _state.value = null
    }
}
