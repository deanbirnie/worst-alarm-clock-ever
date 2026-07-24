package com.worstalarm.clock.alarm

/**
 * Whether the alarm foreground service still has a reason to run (**C5**). It should hold the
 * foreground only while something is actually happening — an alarm ringing / mid-routine
 * (`sessionActive`) or an awake-check popup up and being nudged (`awakeActive`). With neither,
 * sitting in the foreground showing "Alarm / Waking you up…" with no alarm active is the **B1**
 * stuck state, so the service must stop.
 *
 * Pure and Android-free. Complements [ServiceStartPolicy], which guards the *incoming action*;
 * this guards the *live state* once a start has been handled.
 */
object ForegroundStartPolicy {

    /** True when nothing is active, so the foreground service should release and stop. */
    fun shouldStop(sessionActive: Boolean, awakeActive: Boolean): Boolean =
        !sessionActive && !awakeActive
}
