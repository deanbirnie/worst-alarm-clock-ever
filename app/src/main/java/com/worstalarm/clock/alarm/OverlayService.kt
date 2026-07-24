package com.worstalarm.clock.alarm

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen overlay that surfaces the alarm over the lock screen. Covers the screen and
 * intercepts all touches; the only usable control is a "CONTINUE" button that brings the alarm
 * UI to the foreground. It serves two jobs:
 *
 *  1. **Re-assert** (immediate): shown if the user backgrounds [AlarmActivity] while an alarm is
 *     still active (see `AlarmActivity.onUserLeaveHint`).
 *  2. **Ring-time fallback** ([EXTRA_AS_FALLBACK], delayed): on devices/OEMs that block the
 *     background *activity* launch but still allow an overlay *window* (e.g. ColorOS, MIUI), the
 *     ringing screen may not appear on its own. [AlarmService] asks for this overlay at ring time;
 *     after a short settle it draws **only if [AlarmActivity] didn't come up** ([AlarmActivity.isShowing]).
 *     On the majority of devices the activity surfaces normally, so this path self-cancels and
 *     nothing extra is drawn.
 *
 * Requires the "Display over other apps" permission ([Settings.canDrawOverlays]); without it this
 * service silently no-ops — the alarm still plays and its fullScreenIntent notification stands.
 */
class OverlayService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || !AlarmSession.isActive) {
            stopSelf(); return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_AS_FALLBACK, false) == true) {
            // Give the real activity a moment to surface (full-screen intent / direct launch);
            // draw only if it didn't. This is what keeps normal devices untouched.
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                if (AlarmSession.isActive && !AlarmActivity.isShowing) showOverlay() else stopSelf()
            }, FALLBACK_SETTLE_MS)
        } else {
            showOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6000000"))
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            isClickable = true  // swallow taps
        }
        container.addView(TextView(this).apply {
            text = "ALARM IN PROGRESS"
            textSize = 28f
            setTextColor(Color.parseColor("#FF5555"))
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = "You can't use the phone until the routine is complete."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        })
        container.addView(Button(this).apply {
            text = "CONTINUE"
            setOnClickListener {
                val i = Intent(this@OverlayService, AlarmActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(i)
                hideOverlay()
                stopSelf()
            }
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(container, params)
            overlayView = container
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    private fun hideOverlay() {
        val v = overlayView ?: return
        try { windowManager?.removeView(v) } catch (_: Throwable) {}
        overlayView = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    companion object {
        /** Start extra: draw only as a *delayed fallback* if the ringing activity doesn't surface. */
        const val EXTRA_AS_FALLBACK = "as_fallback"

        /** How long to wait for the real activity before falling back to the overlay window. */
        private const val FALLBACK_SETTLE_MS = 1500L
    }
}
