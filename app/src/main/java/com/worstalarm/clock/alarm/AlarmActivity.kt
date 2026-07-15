package com.worstalarm.clock.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.worstalarm.clock.ui.ringing.AlarmRingingRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Full-screen UI shown while an alarm is ringing. Hosts the ringing screen, the barcode
 * scanner, and the emergency mini-game via a shared Compose state machine.
 *
 * Lockdown behaviors:
 *   - Shown over the lock screen + turns the screen on.
 *   - Back press is intercepted and ignored.
 *   - Recents / Home can't be blocked from userland, but [OverlayService] re-asserts
 *     the alarm UI if the activity loses focus while [AlarmSession] is still active.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLockedAndTurnScreenOnCompat()

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }

        // Ignore back press entirely.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* locked */ }
        })

        setContent {
            val state by AlarmSession.state.collectAsState()
            AlarmRingingRoot(state = state)
        }

        // Auto-finish this activity when the session ends.
        lifecycleScope.launch {
            AlarmSession.state.distinctUntilChanged().collect { s ->
                if (s == null && !isFinishing) finish()
            }
        }
    }

    private fun setShowWhenLockedAndTurnScreenOnCompat() {
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
    }

    // Swallow the hardware menu / search / camera buttons so they can't trigger
    // anything while the alarm is ringing.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // Only re-assert via the overlay when the user deliberately navigates away (home /
    // recents / notification tap). Plain onPause also fires for system dialogs — e.g. the
    // runtime camera-permission prompt — and starting the overlay then would cover the
    // dialog and lock the user out of granting the permission.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (AlarmSession.isActive) startOverlayIfAllowed()
    }

    override fun onResume() {
        super.onResume()
        // Stop the overlay — we're the foreground now.
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun startOverlayIfAllowed() {
        if (!Settings.canDrawOverlays(this)) return
        startService(Intent(this, OverlayService::class.java))
    }
}
