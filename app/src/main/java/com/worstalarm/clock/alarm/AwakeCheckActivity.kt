package com.worstalarm.clock.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.worstalarm.clock.ui.awakecheck.AwakeCheckScreen
import com.worstalarm.clock.ui.theme.WorstAlarmTheme
import kotlinx.coroutines.launch

/**
 * Lightweight "are you awake?" popup shown 5-15 minutes (twice) after the final alarm
 * location is scanned. Unlike [AlarmActivity] this is NOT a lockdown: back and home work
 * normally, and there's no overlay re-assertion. While it's showing, [AlarmService] repeats a
 * gentle cue (soft chime + light buzz) so it's noticed without watching the screen. Only
 * tapping "I'm awake" counts as a genuine response — backing out just leaves the check
 * pending, and it's scored a miss if [AwakeCheckPolicy.POPUP_TIMEOUT_MS] elapses without that
 * tap (see [AlarmService.handleAwakeCheckTimeout]), which rings the alarm again.
 */
class AwakeCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // B3: never surface the awake-check popup over a live alarm. If a (different) alarm is
        // ringing, the service defers this check; the receiver launches us in parallel, so bail
        // here too rather than flash the popup over the ringing screen.
        if (AlarmSession.isActive) { finish(); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }

        // Fallback only — see resolveDismissTarget. When the activity is surfaced by the
        // notification's full-screen/content PendingIntent (cold, locked screen), this extra is
        // absent (-1); the live AwakeCheckSession is the real source of truth.
        val intentAlarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1L)

        setContent {
            val state by AwakeCheckSession.state.collectAsState()
            WorstAlarmTheme {
                AwakeCheckScreen(
                    checkNumber = state?.checkNumber ?: 1,
                    onConfirm = {
                        val id = AwakeCheckPolicy.resolveDismissTarget(
                            sessionAlarmId = AwakeCheckSession.state.value?.alarmId,
                            intentAlarmId = intentAlarmId
                        )
                        if (id > 0) {
                            // startForegroundService (not startService): a start from over the
                            // lock screen can be treated as a background start, and the service
                            // promotes itself to foreground in onStartCommand anyway.
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, AlarmService::class.java)
                                    .setAction(AlarmService.ACTION_AWAKE_CHECK_DISMISS)
                                    .putExtra(AlarmService.EXTRA_ALARM_ID, id)
                            )
                        }
                    }
                )
            }
        }

        // Auto-finish once the service resolves this check (dismissed, missed, or a fresh
        // one superseded it) — same pattern as AlarmActivity finishing when AlarmSession
        // clears.
        lifecycleScope.launch {
            AwakeCheckSession.state.collect { s ->
                if (s == null && !isFinishing) finish()
            }
        }
    }
}
