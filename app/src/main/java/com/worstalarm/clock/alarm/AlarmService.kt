package com.worstalarm.clock.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.worstalarm.clock.R
import com.worstalarm.clock.WorstAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val nextRingRunnable = Runnable { ringCurrentStep() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WorstAlarm:AlarmService"
        )?.also { it.acquire(60 * 60 * 1000L) /* 1h safety cap */ }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Alarm", "Waking you up…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        when (intent?.action) {
            ACTION_RING -> handleRing(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
            ACTION_SCAN_SUCCESS -> handleScanSuccess()
            ACTION_ENTER_EMERGENCY -> handleEnterEmergency()
            ACTION_EXIT_EMERGENCY -> handleExitEmergency()
            ACTION_EMERGENCY_COMPLETE, ACTION_DISARM -> disarmAndStop()
        }
        return START_STICKY
    }

    private fun handleRing(alarmId: Long) {
        if (alarmId <= 0) { stopSelfSafely(); return }
        // If we're already ringing this alarm (duplicate intent), just re-ring current step.
        val existing = AlarmSession.state.value
        if (existing != null && existing.alarmWithSteps.alarm.id == alarmId) {
            ringCurrentStep()
            return
        }

        val app = application as WorstAlarmApp
        serviceScope.launch {
            val alarm = withIO { app.repository.getAlarm(alarmId) }
            if (alarm == null || alarm.orderedSteps.isEmpty()) {
                // Malformed alarm: disable it and bail.
                withIO { app.repository.setEnabled(alarmId, false) }
                stopSelfSafely()
                return@launch
            }
            AlarmSession.start(alarm)
            ringCurrentStep()
        }
    }

    private fun ringCurrentStep() {
        val s = AlarmSession.state.value ?: return
        AlarmSession.update { it.copy(isRingingNow = true, nextRingAtMs = 0L) }
        startAudioAndVibration()
        updateNotification(
            "Alarm ringing",
            "Scan: ${s.currentStep.step.locationLabel}"
        )
    }

    private fun handleScanSuccess() {
        val s = AlarmSession.state.value ?: return
        stopAudioAndVibration()

        if (s.isLastStep) {
            disarmAndStop()
            return
        }

        val step = s.currentStep.step
        val nextAt = System.currentTimeMillis() + step.timeToNextRingSeconds * 1000L
        AlarmSession.update {
            it.copy(
                currentStepIndex = it.currentStepIndex + 1,
                isRingingNow = false,
                nextRingAtMs = nextAt,
                scansCompleted = it.scansCompleted + 1
            )
        }
        updateNotification(
            "Next: ${AlarmSession.state.value?.currentStep?.step?.locationLabel ?: ""}",
            "Rings in ${step.timeToNextRingSeconds}s"
        )
        handler.removeCallbacks(nextRingRunnable)
        handler.postDelayed(nextRingRunnable, step.timeToNextRingSeconds * 1000L)
    }

    private fun handleEnterEmergency() {
        handler.removeCallbacks(nextRingRunnable)
        stopAudioAndVibration()
        AlarmSession.update { it.copy(inEmergencyMode = true, isRingingNow = false) }
        updateNotification("Emergency mode", "Complete the taps to disarm")
    }

    /** Called if the 30s idle timer in the mini-game expires. */
    private fun handleExitEmergency() {
        AlarmSession.update { it.copy(inEmergencyMode = false) }
        ringCurrentStep()
    }

    private fun disarmAndStop() {
        handler.removeCallbacks(nextRingRunnable)
        stopAudioAndVibration()

        // Reschedule if recurring.
        val s = AlarmSession.state.value
        if (s != null) {
            val alarm = s.alarmWithSteps.alarm
            if (alarm.daysMask != 0) {
                AlarmScheduler.schedule(this, alarm)
            } else {
                // One-shot alarm: disable it after firing.
                val app = application as WorstAlarmApp
                serviceScope.launch {
                    withIO { app.repository.setEnabled(alarm.id, false) }
                }
            }
        }

        AlarmSession.clear()
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        stopSelf()
    }

    // -------- Audio / haptics --------

    private fun startAudioAndVibration() {
        if (player?.isPlaying == true) return
        val uri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
            // Max alarm volume so the user actually wakes up.
            val am = getSystemService(AudioManager::class.java)
            am?.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        } catch (_: Throwable) { /* ignore — vibration still fires */ }

        startVibration()
    }

    private fun stopAudioAndVibration() {
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.release() } catch (_: Throwable) {}
        player = null
        stopVibration()
    }

    private fun startVibration() {
        val vibrator = vibratorCompat() ?: return
        val pattern = longArrayOf(0, 800, 400, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibratorCompat()?.cancel()
    }

    private fun vibratorCompat(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

    // -------- Notification --------

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, WorstAlarmApp.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        handler.removeCallbacks(nextRingRunnable)
        stopAudioAndVibration()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private suspend fun <T> withIO(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

    companion object {
        const val ACTION_RING = "com.worstalarm.RING"
        const val ACTION_SCAN_SUCCESS = "com.worstalarm.SCAN_SUCCESS"
        const val ACTION_ENTER_EMERGENCY = "com.worstalarm.ENTER_EMERGENCY"
        const val ACTION_EXIT_EMERGENCY = "com.worstalarm.EXIT_EMERGENCY"
        const val ACTION_EMERGENCY_COMPLETE = "com.worstalarm.EMERGENCY_COMPLETE"
        const val ACTION_DISARM = "com.worstalarm.DISARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val NOTIFICATION_ID = 0xA1A2
    }
}
