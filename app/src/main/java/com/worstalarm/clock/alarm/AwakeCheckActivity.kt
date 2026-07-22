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

        val alarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1L)

        setContent {
            val state by AwakeCheckSession.state.collectAsState()
            WorstAlarmTheme {
                AwakeCheckScreen(
                    checkNumber = state?.checkNumber ?: 1,
                    onConfirm = {
                        startService(
                            Intent(this, AlarmService::class.java)
                                .setAction(AlarmService.ACTION_AWAKE_CHECK_DISMISS)
                                .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                        )
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
