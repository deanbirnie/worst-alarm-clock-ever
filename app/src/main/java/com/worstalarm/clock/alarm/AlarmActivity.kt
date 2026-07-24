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
import kotlinx.coroutines.launch

/**
 * Full-screen UI shown while an alarm is ringing. Hosts the ringing screen, the barcode
 * scanner, and the emergency mini-game via a shared Compose state machine.
 *
 * Lockdown behaviors:
 *   - Shown over the lock screen + turns the screen on.
 *   - Back press is intercepted and ignored.
 *   - Volume keys are swallowed so the user can't quiet or mute the alarm — only
 *     disarming it (scan / emergency game) stops the sound. [AlarmService]
 *     separately forces STREAM_ALARM to max at the start of every ring, so even a
 *     route around this activity (e.g. a paired Bluetooth remote, Assistant) is
 *     undone by the next ring.
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

        // Auto-finish this activity when the session ends. (StateFlow already skips
        // equal values, so no distinctUntilChanged needed — it's a compile error on it.)
        lifecycleScope.launch {
            AlarmSession.state.collect { s ->
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
    // anything while the alarm is ringing, and the volume keys so the alarm can't be
    // quieted or muted without actually disarming it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // Volume keys must be consumed on BOTH down and up — some OEM skins still flash
    // the volume overlay / nudge the stream if only onKeyDown returns true.
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> super.onKeyUp(keyCode, event)
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

    override fun onStart() {
        super.onStart()
        // Mark the ringing UI visible so the overlay fallback (OverlayService) knows the real
        // activity surfaced and suppresses itself. onStart (not onResume) so it stays true behind
        // a system dialog — e.g. the camera-permission prompt — which the overlay must not cover.
        isShowing = true
    }

    override fun onStop() {
        super.onStop()
        isShowing = false
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

    companion object {
        /**
         * True while this activity is at least started (visible). The alarm-surfacing fallback
         * ([OverlayService]) reads this after a short settle delay: if the ringing activity came
         * up on its own (the normal case on most devices), the overlay never draws.
         */
        @Volatile
        var isShowing: Boolean = false
    }
}
