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
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.worstalarm.clock.R
import com.worstalarm.clock.WorstAlarmApp
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.AwakeCheckEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Live loop that emits the gentle awake-check nudges; separate from the alarm's [player]. */
    private var awakeNudgeJob: Job? = null
    private var nudgePlayer: MediaPlayer? = null

    /** Tone for the current session: per-alarm override, else the global Settings tone. */
    private var customToneUri: String? = null

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
        // Android requires startForeground() within ~5s of startForegroundService(), so we
        // foreground first — even for a start we're about to reject just below. The full-screen
        // intent on this notification is what actually surfaces the UI over the lock screen when
        // the device is asleep, so it must target the SAME screen this event shows: the awake
        // check's gentle popup for an awake-check show, the ringing lockdown for everything else.
        val notification = if (intent?.action == ACTION_AWAKE_CHECK_SHOW) {
            // Carry the alarm id so the popup can dismiss the right check even when it's the
            // full-screen/notification-tap launch that surfaces the activity (the receiver's
            // direct launch isn't allowed from a cold, locked screen).
            buildNotification(
                "Awake check", "Tap \"I'm awake\" to confirm you're up",
                AwakeCheckActivity::class.java, intent.getLongExtra(EXTRA_ALARM_ID, -1L)
            )
        } else {
            buildNotification("Alarm", "Waking you up…", AlarmActivity::class.java)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // B1: a start with no action we handle — a null intent (there's no sticky restart to
        // deliver one now, but defence in depth) or an unrecognised action — must not leave us
        // sitting in the foreground with no alarm active. ServiceStartPolicy is the unit-tested
        // source of truth for which actions are real.
        if (ServiceStartPolicy.decide(intent?.action) == ServiceStartPolicy.Decision.STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_RING -> handleRing(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
            ACTION_STEP_RING -> handleStepRing(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
            ACTION_SCAN_SUCCESS -> handleScanSuccess(intent)
            ACTION_ENTER_EMERGENCY -> handleEnterEmergency()
            ACTION_EXIT_EMERGENCY -> handleExitEmergency()
            ACTION_EMERGENCY_IDLE_RESET -> handleEmergencyIdleReset()
            ACTION_EMERGENCY_COMPLETE, ACTION_DISARM -> completeRoutine()
            ACTION_AWAKE_CHECK_SHOW -> handleAwakeCheckShow(intent)
            ACTION_AWAKE_CHECK_DISMISS -> handleAwakeCheckDismiss(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
            ACTION_AWAKE_CHECK_TIMEOUT -> handleAwakeCheckTimeout(intent)
            // Unreachable past the policy gate above, but staying foregrounded on an unmatched
            // action is the exact B1 failure mode, so stop rather than risk it.
            else -> stopSelfSafely()
        }
        // START_NOT_STICKY: everything that must outlive process death re-arms itself through
        // AlarmManager (step-rings, awake checks — see onDestroy), so we never depend on the OS
        // restarting us with a null intent, which is what used to strand this service (B1).
        return START_NOT_STICKY
    }

    private fun handleRing(alarmId: Long) {
        // Exactly one alarm can own the ringing session. AlarmAdmissionPolicy is the unit-tested
        // source of truth for what happens when a ring arrives while one is (or isn't) already
        // active — critically, a *different* alarm must not clobber the one the user is currently
        // dealing with (B2).
        val activeId = AlarmSession.state.value?.alarmWithSteps?.alarm?.id
        when (AlarmAdmissionPolicy.decide(activeId, alarmId)) {
            AlarmAdmissionPolicy.Decision.INVALID ->
                // Bogus ring with no valid id. If something is already ringing, leave it be;
                // otherwise there's nothing to do, so don't sit in the foreground (B1).
                if (activeId == null) stopSelfSafely()

            AlarmAdmissionPolicy.Decision.RERING_CURRENT ->
                // Duplicate delivery of the alarm already ringing — just re-ring the current step.
                ringCurrentStep()

            AlarmAdmissionPolicy.Decision.DEFER_INCOMING ->
                // A different alarm owns the session. Don't overwrite it (that was B2): re-arm
                // this one shortly so it rings once the active alarm is done, instead of being
                // silently dropped. The active alarm's ring is left completely untouched.
                AlarmScheduler.scheduleRingAt(
                    this, alarmId, AlarmAdmissionPolicy.deferUntilMs(System.currentTimeMillis())
                )

            AlarmAdmissionPolicy.Decision.RING_NEW ->
                startRinging(alarmId)
        }
    }

    /** Loads the alarm, claims the session, and starts ringing. Only called when nothing is active. */
    private fun startRinging(alarmId: Long) {
        val app = application as WorstAlarmApp
        serviceScope.launch {
            val alarm = withIO { app.repository.getAlarm(alarmId) }
            if (alarm == null || alarm.orderedSteps.isEmpty()) {
                // Malformed alarm: disable it and bail.
                withIO { app.repository.setEnabled(alarmId, false) }
                stopSelfSafely()
                return@launch
            }
            customToneUri = alarm.alarm.ringtoneUri
                ?: withIO { app.settings.globalRingtoneUri.first() }
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
            "Scan: ${s.currentStep.displayName}"
        )
    }

    private fun handleScanSuccess(intent: Intent) {
        val s = AlarmSession.state.value ?: return

        // Re-validate against the current step. The camera fires one event per frame the
        // barcode is visible in, so a single physical scan can emit several intents —
        // unchecked, each one advanced a step and silently skipped parts of the routine
        // (the reused-barcode bug). ringActive gates out scan-ahead during the countdown.
        // See ScanValidator.
        val decision = ScanValidator.decide(
            ringActive = s.isRingingNow,
            currentStepIndex = s.currentStepIndex,
            totalSteps = s.totalSteps,
            expectedRawValue = s.currentStep.barcode.rawValue,
            expectedFormat = s.currentStep.barcode.format,
            scannedStepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, -1),
            scannedRawValue = intent.getStringExtra(EXTRA_SCANNED_VALUE),
            scannedFormat = intent.getIntExtra(EXTRA_SCANNED_FORMAT, 0)
        )
        if (decision == ScanValidator.Decision.IGNORE) return

        stopAudioAndVibration()

        if (decision == ScanValidator.Decision.DISARM) {
            completeRoutine()
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
            "Next: ${AlarmSession.state.value?.currentStep?.displayName ?: ""}",
            "Rings in ${step.timeToNextRingSeconds}s"
        )
        // AlarmManager, not a Handler: a Handler's delay clock pauses in deep sleep, so
        // with the screen off the next ring would wait until the user woke the phone.
        AlarmScheduler.scheduleStepRing(this, s.alarmWithSteps.alarm.id, nextAt)
    }

    /** The between-step pause ended (delivered by AlarmManager, waking the device). */
    private fun handleStepRing(alarmId: Long) {
        if (AlarmSession.isActive) {
            ringCurrentStep()
        } else if (alarmId > 0) {
            // Process was killed mid-routine — restart the routine from the top rather
            // than staying silent.
            handleRing(alarmId)
        } else {
            stopSelfSafely()
        }
    }

    private fun handleEnterEmergency() {
        val s = AlarmSession.state.value ?: return
        AlarmScheduler.cancelStepRing(this, s.alarmWithSteps.alarm.id)
        // Idle out of the game too many times and it stops buying silence: the alarm
        // keeps ringing while the user taps.
        if (s.emergencyIdleResets >= AlarmSession.MAX_FREE_IDLE_RESETS) {
            startAudioAndVibration()
            updateNotification("Emergency mode", "Too many idle resets — alarm stays on")
        } else {
            stopAudioAndVibration()
            updateNotification("Emergency mode", "Complete the taps to disarm")
        }
        AlarmSession.update { it.copy(inEmergencyMode = true, isRingingNow = false) }
    }

    /** The user backed out of the mini-game voluntarily — resume ringing, no penalty. */
    private fun handleExitEmergency() {
        AlarmSession.update { it.copy(inEmergencyMode = false) }
        ringCurrentStep()
    }

    /** The 30 s idle timer in the mini-game expired — resume ringing and count it. */
    private fun handleEmergencyIdleReset() {
        AlarmSession.update {
            it.copy(
                inEmergencyMode = false,
                emergencyIdleResets = it.emergencyIdleResets + 1
            )
        }
        ringCurrentStep()
    }

    /**
     * The routine's final step was scanned (or the emergency game was completed) — reschedule
     * for next time and drop the lock-screen takeover. If this alarm has awake checks enabled
     * ([AlarmEntity.awakeCheckEnabled]), hand off to that cycle: the alarm isn't fully
     * disabled until two "I'm awake" popups are dismissed. Otherwise it's just done.
     */
    private fun completeRoutine() {
        stopAudioAndVibration()

        val s = AlarmSession.state.value
        val alarm = s?.alarmWithSteps?.alarm
        if (alarm != null) {
            AlarmScheduler.cancelStepRing(this, alarm.id)
            rescheduleOrDisableForNextOccurrence(alarm)
        }

        // Screen blocker removed the moment the routine is complete — awake checks (if
        // enabled and any remain this cycle) run silently in the background from here.
        AlarmSession.clear()

        if (alarm != null && alarm.awakeCheckEnabled) {
            serviceScope.launch { beginAwakeCheckCycle(alarm.id) }
        } else {
            stopSelfSafely()
        }
    }

    /** Idempotent: safe to call again on a miss-triggered re-ring without double effects. */
    private fun rescheduleOrDisableForNextOccurrence(alarm: AlarmEntity) {
        if (alarm.daysMask != 0) {
            AlarmScheduler.schedule(this, alarm)
        } else {
            val app = application as WorstAlarmApp
            serviceScope.launch { withIO { app.repository.setEnabled(alarm.id, false) } }
        }
    }

    // -------- Awake checks --------
    // After the routine's final step, two "are you awake?" popups must be dismissed, each
    // appearing at a random point 5-15 minutes after the previous one resolves, before the alarm
    // is fully off. While a popup shows, a gentle cue (soft chime + light buzz, never alarm-grade)
    // repeats every NUDGE_INTERVAL_MS so it's noticed without watching the screen. Missing a
    // popup's POPUP_TIMEOUT_MS dismiss deadline is a full reset: the cycle starts over from a
    // fresh re-ring that only requires the final step to be rescanned.
    // State is persisted in Room (AwakeCheckEntity) and scheduled via AlarmManager — same
    // reasoning as step-rings: a killed process or sleeping device must not silently drop it.

    private suspend fun beginAwakeCheckCycle(alarmId: Long) {
        val app = application as WorstAlarmApp
        val nextAt = System.currentTimeMillis() + AwakeCheckPolicy.randomIntervalMs()
        withIO {
            app.repository.saveAwakeCheck(
                AwakeCheckEntity(
                    alarmId = alarmId,
                    dismissedCount = 0,
                    nextCheckAtMs = nextAt,
                    popupDeadlineAtMs = 0L
                )
            )
        }
        AlarmScheduler.scheduleAwakeCheck(this, alarmId, nextAt, dismissedCount = 0)
        stopSelfSafely()
    }

    /**
     * An armed popup's time has come. Not the alarm — a *gentle* cue (soft chime + light buzz)
     * repeats across the ack window so the user notices without watching the screen, then stops
     * the instant "I'm awake" is tapped ([handleAwakeCheckDismiss]) or the window's AlarmManager
     * timeout re-rings ([handleAwakeCheckTimeout]).
     */
    private fun handleAwakeCheckShow(intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val dismissedCount = intent.getIntExtra(EXTRA_DISMISSED_COUNT, 0)
        if (alarmId <= 0) { stopSelfSafely(); return }

        // B3: if a different alarm is ringing right now, don't stack this gentle popup + nudge
        // over it — re-arm the show for a bit later. (AwakeCheckActivity self-finishes over a live
        // ring too, so the receiver's parallel launch doesn't flash on screen.)
        val activeId = AlarmSession.state.value?.alarmWithSteps?.alarm?.id
        if (AlarmAdmissionPolicy.deferAwakeCheckShow(activeId)) {
            reArmAwakeCheckShow(alarmId, dismissedCount)
            return
        }

        // Set synchronously so the activity (already being launched by AlarmReceiver) has
        // something to show without waiting on the DB round-trip below.
        AwakeCheckSession.show(alarmId, dismissedCount + 1)
        startAwakeCheckNudges()

        serviceScope.launch {
            val app = application as WorstAlarmApp
            val row = withIO { app.repository.getAwakeCheck(alarmId) }
            if (row == null) {
                // No pending row to wait on — don't leave the service nudging forever.
                stopAwakeCheckNudges()
                stopSelfSafely()
                return@launch
            }
            val deadline = System.currentTimeMillis() + AwakeCheckPolicy.POPUP_TIMEOUT_MS
            withIO { app.repository.saveAwakeCheck(row.copy(popupDeadlineAtMs = deadline)) }
            AlarmScheduler.scheduleAwakeCheckTimeout(this@AlarmService, alarmId, deadline)
            // Deliberately no stopSelfSafely() on the happy path: the service stays foreground to
            // run the nudge loop until the dismiss or the timeout resolves this check. The
            // AlarmManager timeout is authoritative even if the process is killed mid-window.
        }
    }

    private fun startAwakeCheckNudges() {
        awakeNudgeJob?.cancel()
        awakeNudgeJob = serviceScope.launch {
            val deadline = System.currentTimeMillis() + AwakeCheckPolicy.POPUP_TIMEOUT_MS
            // First nudge fires immediately (offset 0), then one every NUDGE_INTERVAL_MS until
            // the window closes or the popup is resolved (session cleared / job cancelled).
            while (isActive &&
                AwakeCheckSession.state.value != null &&
                System.currentTimeMillis() < deadline
            ) {
                playAwakeCheckNudge()
                delay(AwakeCheckPolicy.NUDGE_INTERVAL_MS)
            }
        }
    }

    /** One gentle, non-alarm nudge: a short soft chime plus a light double-tap buzz. */
    private fun playAwakeCheckNudge() {
        // Light haptic — deliberately short and soft, nothing like the alarm's long looping buzz.
        vibratorCompat()?.let { vibrator ->
            val pattern = longArrayOf(0, 120, 90, 120)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) // -1 = play once
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
            }
        }

        // Soft chime — the default *notification* sound, once, at low volume on the notification
        // stream, so it stays gentle and respects the user's notification volume. Vibration is
        // the reliable channel; the chime is a bonus (a silent profile / DND may mute it).
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            nudgePlayer?.let { old -> runCatching { old.release() } }
            nudgePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = false
                setVolume(0.4f, 0.4f)
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    if (nudgePlayer === mp) nudgePlayer = null
                }
                prepare()
                start()
            }
        }
    }

    private fun stopAwakeCheckNudges() {
        awakeNudgeJob?.cancel()
        awakeNudgeJob = null
        nudgePlayer?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        nudgePlayer = null
        runCatching { vibratorCompat()?.cancel() }
    }

    /** The user tapped "I'm awake". */
    private fun handleAwakeCheckDismiss(alarmId: Long) {
        if (alarmId <= 0) { stopSelfSafely(); return }
        stopAwakeCheckNudges()
        AlarmScheduler.cancelAwakeCheckTimeout(this, alarmId)
        AwakeCheckSession.clear()

        serviceScope.launch {
            val app = application as WorstAlarmApp
            val row = withIO { app.repository.getAwakeCheck(alarmId) }
            if (row == null) { stopSelfSafely(); return@launch }

            when (val outcome = AwakeCheckPolicy.onDismiss(row.dismissedCount)) {
                is AwakeCheckPolicy.DismissOutcome.CycleComplete -> {
                    withIO { app.repository.clearAwakeCheck(alarmId) }
                }
                is AwakeCheckPolicy.DismissOutcome.ScheduleNext -> {
                    val nextAt = System.currentTimeMillis() + AwakeCheckPolicy.randomIntervalMs()
                    withIO {
                        app.repository.saveAwakeCheck(
                            row.copy(
                                dismissedCount = outcome.newDismissedCount,
                                nextCheckAtMs = nextAt,
                                popupDeadlineAtMs = 0L
                            )
                        )
                    }
                    AlarmScheduler.scheduleAwakeCheck(
                        this@AlarmService, alarmId, nextAt, outcome.newDismissedCount
                    )
                }
            }
            stopSelfSafely()
        }
    }

    /** B3: an alarm is ringing, so re-arm this awake-check popup for a bit later instead of over it. */
    private fun reArmAwakeCheckShow(alarmId: Long, dismissedCount: Int) {
        serviceScope.launch {
            val app = application as WorstAlarmApp
            val row = withIO { app.repository.getAwakeCheck(alarmId) }
            if (row != null) {
                val nextAt = AlarmAdmissionPolicy.deferUntilMs(System.currentTimeMillis())
                withIO {
                    app.repository.saveAwakeCheck(row.copy(nextCheckAtMs = nextAt, popupDeadlineAtMs = 0L))
                }
                AlarmScheduler.scheduleAwakeCheck(this@AlarmService, alarmId, nextAt, dismissedCount)
            }
            stopSelfSafely()
        }
    }

    /** A shown popup's dismiss deadline expired — verify it's genuine, then re-ring. */
    private fun handleAwakeCheckTimeout(intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val expectedDeadline = intent.getLongExtra(EXTRA_DEADLINE, -1L)
        if (alarmId <= 0) { stopSelfSafely(); return }
        stopAwakeCheckNudges()

        serviceScope.launch {
            val app = application as WorstAlarmApp
            val row = withIO { app.repository.getAwakeCheck(alarmId) }
            if (row == null || !AwakeCheckPolicy.isGenuineMiss(row.popupDeadlineAtMs, expectedDeadline)) {
                // Already dismissed, or superseded by a later check — ignore.
                stopSelfSafely()
                return@launch
            }

            // B3: the miss re-rings the alarm — but a *different* alarm ringing right now must win,
            // or AlarmSession.start() below would clobber its session (the B2 harm on this path).
            val activeId = AlarmSession.state.value?.alarmWithSteps?.alarm?.id
            when (AlarmAdmissionPolicy.decideAwakeCheckReRing(activeId, alarmId)) {
                AlarmAdmissionPolicy.AwakeReRing.DEFER -> {
                    // Keep the check; push its miss deadline forward and re-arm so it retries the
                    // re-ring once the other alarm is done (each retry defers again if still busy).
                    val nextDeadline = AlarmAdmissionPolicy.deferUntilMs(System.currentTimeMillis())
                    withIO { app.repository.saveAwakeCheck(row.copy(popupDeadlineAtMs = nextDeadline)) }
                    AwakeCheckSession.clear()
                    AlarmScheduler.scheduleAwakeCheckTimeout(this@AlarmService, alarmId, nextDeadline)
                    stopSelfSafely()
                }
                AlarmAdmissionPolicy.AwakeReRing.ALREADY_RINGING -> {
                    // This alarm is already ringing (e.g. its next occurrence fired during the
                    // wait) — the stale check's re-ring is redundant. Drop it.
                    withIO { app.repository.clearAwakeCheck(alarmId) }
                    AwakeCheckSession.clear()
                    stopSelfSafely()
                }
                AlarmAdmissionPolicy.AwakeReRing.RE_RING -> {
                    withIO { app.repository.clearAwakeCheck(alarmId) }
                    AwakeCheckSession.clear()

                    val alarm = withIO { app.repository.getAlarm(alarmId) }
                    if (alarm == null || alarm.orderedSteps.isEmpty()) { stopSelfSafely(); return@launch }

                    customToneUri = alarm.alarm.ringtoneUri
                        ?: withIO { app.settings.globalRingtoneUri.first() }
                    AlarmSession.start(alarm, startAtStepIndex = alarm.orderedSteps.size - 1)
                    ringCurrentStep()
                }
            }
        }
    }

    private fun stopSelfSafely() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        stopSelf()
    }

    // -------- Audio / haptics --------

    private fun startAudioAndVibration() {
        if (player?.isPlaying == true) return
        // Try the user's custom tone first (per-alarm, then global — resolved into
        // customToneUri at ring time); if it's missing/unreadable, fall back to the
        // system alarm sound so the alarm always makes noise.
        val candidates = buildList {
            customToneUri?.let { runCatching { add(Uri.parse(it)) } }
            RingtoneManager.getActualDefaultRingtoneUri(
                this@AlarmService, RingtoneManager.TYPE_ALARM
            )?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
        }
        for (uri in candidates) {
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
                break
            } catch (_: Throwable) {
                try { player?.release() } catch (_: Throwable) {}
                player = null
            }
        }
        if (player != null) {
            // Max alarm volume so the user actually wakes up.
            val am = getSystemService(AudioManager::class.java)
            am?.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }

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

    private fun buildNotification(
        title: String,
        text: String,
        fullScreenTarget: Class<*> = AlarmActivity::class.java,
        alarmId: Long = -1L
    ): Notification {
        val openIntent = Intent(this, fullScreenTarget).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // AwakeCheckActivity reads this as a fallback for which check to dismiss; the alarm
            // ringing UI (AlarmActivity) uses AlarmSession instead and ignores it.
            if (alarmId > 0) putExtra(EXTRA_ALARM_ID, alarmId)
        }
        // Distinct request code per target so the two possible full-screen PendingIntents
        // (ringing lockdown vs awake-check popup) never alias each other under FLAG_UPDATE_CURRENT.
        val requestCode = if (fullScreenTarget == AwakeCheckActivity::class.java) 1 else 0
        val openPi = PendingIntent.getActivity(
            this, requestCode, openIntent,
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
        // Updates only happen mid-ring (step countdown / emergency), while AlarmActivity is
        // already up — so the default full-screen target (AlarmActivity) is correct here.
        nm?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        // Deliberately do NOT cancel a pending step ring here: if the OS kills the
        // service mid-countdown, the AlarmManager alarm survives and restarts us.
        stopAudioAndVibration()
        stopAwakeCheckNudges()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private suspend fun <T> withIO(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

    companion object {
        const val ACTION_RING = "com.worstalarm.RING"
        const val ACTION_STEP_RING = "com.worstalarm.STEP_RING"
        const val ACTION_SCAN_SUCCESS = "com.worstalarm.SCAN_SUCCESS"
        const val ACTION_ENTER_EMERGENCY = "com.worstalarm.ENTER_EMERGENCY"
        const val ACTION_EXIT_EMERGENCY = "com.worstalarm.EXIT_EMERGENCY"
        const val ACTION_EMERGENCY_IDLE_RESET = "com.worstalarm.EMERGENCY_IDLE_RESET"
        const val ACTION_EMERGENCY_COMPLETE = "com.worstalarm.EMERGENCY_COMPLETE"
        const val ACTION_DISARM = "com.worstalarm.DISARM"
        const val ACTION_AWAKE_CHECK_SHOW = "com.worstalarm.AWAKE_CHECK_SHOW"
        const val ACTION_AWAKE_CHECK_DISMISS = "com.worstalarm.AWAKE_CHECK_DISMISS"
        const val ACTION_AWAKE_CHECK_TIMEOUT = "com.worstalarm.AWAKE_CHECK_TIMEOUT"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_STEP_INDEX = "step_index"
        const val EXTRA_SCANNED_VALUE = "scanned_value"
        const val EXTRA_SCANNED_FORMAT = "scanned_format"
        const val EXTRA_DISMISSED_COUNT = "dismissed_count"
        const val EXTRA_DEADLINE = "deadline_at_ms"
        const val NOTIFICATION_ID = 0xA1A2
    }
}
