package com.worstalarm.clock.alarm

/**
 * Decides whether a given [AlarmService] start is a real one we know how to handle, or a stray
 * start that must not be allowed to sit in the foreground.
 *
 * Regression guard (B1): [AlarmService.onStartCommand] must call `startForeground()` within a
 * few seconds of being started via `startForegroundService()`, so it foregrounds *before* it
 * looks at the intent. The old code then dispatched on `when (intent?.action)` with no `else`
 * branch and returned `START_STICKY`. If the OS killed and restarted the service it re-delivered
 * a **null** intent (and an unrecognised action is possible too) — no branch ran, nothing called
 * `stopSelfSafely()`, and the service was left foregrounded with the persistent "Waking you up…"
 * notification and no alarm actually active. This object is the single, unit-tested source of
 * truth for "is this a real start?", so that decision can't silently regress.
 *
 * Kept free of Android types so plain JVM unit tests can pin the behavior down. The
 * [AlarmService] action names it references are Kotlin `const val`s, inlined at compile time, so
 * this object carries no runtime dependency on the (Android-only) service class.
 */
object ServiceStartPolicy {

    enum class Decision {
        /** A recognised action — run the matching handler. */
        PROCEED,
        /** Null/unknown action (e.g. a sticky restart): foreground was already required, so stop. */
        STOP
    }

    /** Every action [AlarmService.onStartCommand] knows how to handle. */
    val HANDLED_ACTIONS: Set<String> = setOf(
        AlarmService.ACTION_RING,
        AlarmService.ACTION_STEP_RING,
        AlarmService.ACTION_SCAN_SUCCESS,
        AlarmService.ACTION_ENTER_EMERGENCY,
        AlarmService.ACTION_EXIT_EMERGENCY,
        AlarmService.ACTION_EMERGENCY_IDLE_RESET,
        AlarmService.ACTION_EMERGENCY_COMPLETE,
        AlarmService.ACTION_DISARM,
        AlarmService.ACTION_AWAKE_CHECK_SHOW,
        AlarmService.ACTION_AWAKE_CHECK_DISMISS,
        AlarmService.ACTION_AWAKE_CHECK_TIMEOUT
    )

    fun decide(action: String?): Decision =
        if (action != null && action in HANDLED_ACTIONS) Decision.PROCEED else Decision.STOP
}
