package com.worstalarm.clock.alarm

/**
 * Which system broadcasts should trigger a re-arm of all enabled alarms (**B11**). Pure and
 * Android-free so it's unit-testable (mirrors [ServiceStartPolicy]).
 *
 * Alarms are armed with `AlarmManager.setAlarmClock` at an **absolute instant** computed from
 * the wall-clock time and zone in effect when they were armed. A timezone change (travel) or a
 * manual clock change leaves that instant pointing at the wrong local time, and — unlike a
 * reboot — nothing else re-arms them. `TimeChangeReceiver` listens for these actions and
 * recomputes every enabled alarm's next trigger.
 *
 * The literals are the values of `Intent.ACTION_TIMEZONE_CHANGED` and `Intent.ACTION_TIME_CHANGED`
 * (whose string is, historically, `..._SET`). Kept as literals here so the policy stays a pure
 * JVM object; the receiver registers the matching `<action>`s in the manifest.
 */
object TimeChangePolicy {

    const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
    const val ACTION_TIME_CHANGED = "android.intent.action.TIME_SET"

    val HANDLED_ACTIONS: Set<String> = setOf(ACTION_TIMEZONE_CHANGED, ACTION_TIME_CHANGED)

    fun shouldReArm(action: String?): Boolean = action != null && action in HANDLED_ACTIONS
}
