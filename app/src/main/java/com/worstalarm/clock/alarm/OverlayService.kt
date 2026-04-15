package com.worstalarm.clock.alarm

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen re-assert overlay shown if the user manages to background [AlarmActivity]
 * while an alarm is still active. Covers the screen and intercepts all touches; the only
 * usable control is a "CONTINUE" button that brings the alarm UI back to the foreground.
 *
 * Requires the user to grant the "Display over other apps" permission
 * ([Settings.canDrawOverlays]). If the permission isn't granted this service silently
 * no-ops — the alarm is still playing via [AlarmService] and the fullScreenIntent on
 * the notification, so the user can still reach the alarm.
 */
class OverlayService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (!AlarmSession.isActive) { stopSelf(); return START_NOT_STICKY }
        showOverlay()
        return START_STICKY
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
        hideOverlay()
        super.onDestroy()
    }
}
