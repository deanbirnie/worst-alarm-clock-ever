# TODO

Project task list, from empty repo to Play Store. Checked items are done;
work through the rest roughly top to bottom. Keep this file honest — tick
things only when they're verified working.

## Phase 1 — Core app (MVP)

- [x] Project scaffold: Gradle + AGP + Kotlin + Compose + Room + CameraX + ML Kit
- [x] Data model: alarms, saved barcodes, ordered routine steps (Room entities + DAOs + repository)
- [x] Barcode library screen: add/edit/delete codes, scan-to-fill via camera, manual entry
- [x] Alarm list screen: time/days/step summary, enable toggle, overlay-permission banner
- [x] Alarm editor screen: label, time picker, weekday chips, ordered steps with per-step delay
- [x] Alarm scheduling with `AlarmManager.setAlarmClock` (Doze-proof, exact)
- [x] Ringing engine: foreground service, max-volume alarm audio + vibration, wake lock
- [x] Lock-screen ringing UI: show over keyguard, turn screen on, block back button
- [x] Barcode scan-to-dismiss with wrong-barcode rejection
- [x] Alarm paths: multi-step routines with quiet countdown between steps
- [x] Re-assert overlay when the user escapes to home mid-alarm
- [x] Emergency escape hatch (500-tap mini-game with 30 s idle reset)
- [x] Reboot / app-update persistence (`BootReceiver` re-arms enabled alarms)
- [x] One-shot alarms disable themselves after firing; recurring alarms reschedule

## Phase 2 — Correctness & build infrastructure

- [x] Fix: UI callbacks (navigation, toasts) invoked from background thread in `AppViewModel`
- [x] Fix: scanner panel stayed open after a mid-routine scan instead of returning to the countdown screen
- [x] Fix: re-assert overlay fired on `onPause`, covering the camera-permission dialog (now `onUserLeaveHint` only)
- [x] Fix: overlay-permission banner didn't refresh after returning from system settings
- [x] Request camera permission up front on first launch (not at 6 AM with an alarm ringing)
- [x] Check in the Gradle wrapper so `./gradlew assembleDebug` works after a bare clone
- [x] GitHub Actions workflow: build debug APK on every push, upload as downloadable artifact
- [ ] Verify CI build is green and the artifact APK installs + runs on a real phone
- [x] Documentation: README (concept, features, architecture), BUILDING.md (APK how-to), RELEASING.md (signing, Play Store, maintenance)

## Phase 2.5 — v0.2 UX & feature batch (requested 2026-07-15)

- [x] Warm sunrise colour theme (dark + light), muted rather than bright
- [x] Move SCAN / EMERGENCY buttons clear of the gesture-navigation bar overlap
- [x] Keep the transparent red wrong-barcode flash (unchanged, by design)
- [x] Decouple barcode from location: optional location on library barcodes; location optional on alarm steps
- [x] Auto-select: picking a location with exactly one matching barcode selects that barcode; picking a barcode fills in its location; a single-barcode library preselects automatically
- [x] Custom alarm tone from a device audio file — global default in Settings
- [x] Custom alarm tone per alarm (falls back to global, then the system alarm sound)
- [x] Hamburger menu (navigation drawer): Barcode library, QR generator, Settings, About
- [x] Settings screen: global alarm sound picker, re-show welcome message
- [x] Warn when adding a second location that the phone is unusable until the final barcode is scanned (scan ahead early, or use Emergency stop)
- [x] First-launch intro dialog explaining the path mechanism, with a "Do not show this again" checkbox (checked by default)
- [x] Random QR code generator: create one or several codes, add them to the library, share/print as PNG
- [x] About screen: app version, developer contact, source link, feature-suggestion box
- [x] CI green on the v0.2 changes
- [x] Fix (0.2.1): next-step ring didn't fire with the screen off — the between-step
      countdown ran on a Handler whose clock pauses in deep sleep, so the ring waited
      until the phone was woken. Step rings are now armed via `AlarmManager.setAlarmClock`,
      which wakes the device exactly on time (and survives the process being killed
      mid-routine)
- [x] Fix (0.2.2): step-delay Min/Sec inputs now have -/+ stepper buttons, and tapping
      into the field selects its whole value so typing replaces it outright instead of
      leaving a stray leading "0"
- [x] Emergency game escalation (0.2.3): after 3 idle-timeout resets, entering the game
      no longer silences the alarm — it keeps ringing until the 500 taps are done
      (voluntary "back to alarm" exits don't count; the counter resets per alarm session)
- [x] Fix (0.2.4): reused-barcode step skipping — one physical scan emitted several
      SCAN_SUCCESS events (ML Kit reports the code once per camera frame), and the
      service advanced a step per event, so a path like bathroom → kitchen → bathroom →
      desk jumped from kitchen straight to desk. Scan events now carry their step index
      and barcode; the service validates both (`ScanValidator`, unit-tested in
      `app/src/test/`), and the scanner UI fires at most once per step
- [x] CI: run unit tests in the workflow; triggers narrowed to pushes to main +
      pull requests targeting main (kills the duplicate push/PR double-runs while
      keeping pre-merge verification)
- [x] CI: name the built APK "WorstAlarmEver-\<version\>-\<buildType\>.apk" (was the
      generic app-debug.apk) via AGP's `applicationVariants` (old variant API — its
      newer public `VariantOutput` API doesn't actually expose a settable
      `outputFileName`, only an internal impl class does); artifact + docs updated to
      match. GitHub still wraps the download in a zip — that part isn't configurable —
      but the file inside is now properly named
- [x] Add DESIGN.md: full visual spec of colors, typography, spacing, icons, and every
      screen's layout, for recreating the current UI in an external design tool
- [x] Fix (0.2.6): volume/mute keys are now swallowed while the alarm activity is
      showing, so the alarm can't be quieted without disarming it — combined with
      `AlarmService` forcing STREAM_ALARM to max at the start of every ring. Known
      boundary: only guards this activity; if the user escapes via a gesture-nav route
      (not key-interceptable) before the re-assert overlay kicks in, volume can still be
      changed in system Settings — see README's Honest limitations
- [x] Fix (0.2.7): camera stuck open / flashlight blocked after scanning. Root cause —
      CameraX's `bindToLifecycle` only auto-releases the camera when its `LifecycleOwner`
      reaches `ON_DESTROY`, but the screen/dialog hosting `BarcodeScanner` (a nav
      back-stack entry for the barcode-library "scan to fill value" dialog, or the whole
      `AlarmActivity` ringing session for the alarm's scan panel) routinely stays RESUMED
      long after the scanner itself is dismissed. The old cleanup only closed the ML Kit
      client and its executor — it never called `unbindAll()` on the CameraX provider —
      so the camera capture session (and the system's camera-in-use indicator, which
      blocks torch access on many devices) stayed open indefinitely between scans. Fixed
      with a new `CameraBindingGuard` that explicitly unbinds on Composable disposal and
      guards the async-provider-resolves-after-dispose race so an orphaned binding can
      never happen; unit-tested in `CameraBindingGuardTest` (JVM, no CameraX/Android deps)
- [ ] Verify v0.2 on a real phone (install APK, run one multi-step alarm)

## Phase 2.6 — Awake check (v0.3.0, requested 2026-07-17)

- [x] "Are you awake?" check after the routine's final scan: the screen blocker (AlarmActivity)
      is removed the instant the final barcode is scanned, but the alarm isn't fully off yet.
      Twice — each at a random point 5-15 minutes after the previous one resolves — a silent,
      screen-waking popup appears and must be dismissed with an explicit "I'm awake" tap. A
      popup that isn't dismissed within 90 seconds is a miss: BOTH checks reset, and the alarm
      rings again requiring only the final location to be rescanned (not the whole path),
      which re-enters the same awake-check cycle. Only after two dismissals in a row is the
      alarm actually done.
  - Persisted per-alarm in Room (`AwakeCheckEntity`: dismissed count, next-check time, current
    popup's deadline) and scheduled via `AlarmManager.setAlarmClock` — same reasoning as
    step-rings (0.2.1): a killed process or a sleeping device must not silently drop a pending
    check. Re-armed on reboot (`BootReceiver`); cleaned up (schedule cancelled, row cascades
    away) when an alarm is deleted.
  - New `AwakeCheckActivity` shows the popup: screen-on, but deliberately NOT a lockdown like
    the ringing screen — back/home work normally. Only the button counts as a response;
    backing out just leaves the check pending until the timeout fires.
  - Decision logic factored into a pure, unit-tested `AwakeCheckPolicy` (mirrors `ScanValidator`'s
    pattern): random 5-15 min interval, the 2-dismissals-to-complete state transition, and the
    race guard that stops an already-resolved popup's timeout from being misread as a miss.
  - **Per-alarm toggle** (`AlarmEntity.awakeCheckEnabled`, v3→v4 migration, default ON) — a
    `Switch` in the alarm editor turns the whole cycle off for that alarm. `AlarmService`
    only enters the cycle at routine-completion time if the alarm has it enabled; disabled
    alarms behave exactly like before this feature existed.
  - Remaining design calls made without an answered clarification (the spec didn't cover
    these; flagged in the PR for review/override):
    - **A miss is a full reset** (both checks must be re-earned), not partial credit for a
      check already dismissed — matches "this will again trigger the awake checks" read
      literally as restarting the pair, not resuming mid-pair.
    - **90-second dismiss timeout** for a shown popup — not specified in the request; long
      enough to actually notice and respond, short enough not to sit lit indefinitely.
    - **The emergency 500-tap escape hatch also enters the awake-check cycle** on completion,
      same as a real final scan — otherwise it would be an unlimited bypass of a feature whose
      entire purpose is catching people who fall back asleep.
  - Tests: `AwakeCheckPolicyTest` (interval bounds, dismiss-outcome transitions, the stale-
    timeout race guard), `AlarmSessionTest` (pinning `currentStepIndex` at the final step
    on a miss re-ring, and that scanning it correctly disarms via `ScanValidator`), and
    `AlarmEntityTest` (the toggle defaults on).

## Phase 2.7 — Save-time next-ring note (v0.3.2)

- [x] After saving an alarm, a little note (Toast) shows how long until it rings:
      "Rings in 7 h 32 min". The wording comes from `NextRingFormatter`, a pure JVM
      object that delegates the WHEN to `AlarmScheduler.computeNextTriggerMs` — the
      same computation that arms AlarmManager — so the note can never disagree with
      the actual schedule. Minutes round UP (an alarm 61 s out says "2 min") so the
      note never promises less time than the user really has; past a day, minutes are
      dropped as noise ("Rings in 2 days 3 h"). Saving a disabled alarm shows no note.
      Unit-tested in `NextRingFormatterTest` (delta formatting + end-to-end with a
      fixed clock: later-today, rolled-to-tomorrow, recurring-weekday, disabled).

## Phase 2.8 — v0.4.0 "classy" UI redesign

- [x] Theme tokens: custom softly-rounded `Shapes` (cards 18dp, dialogs 32dp) and a
      few type-scale weight overrides (displaySmall/headlineMedium/titleMedium/
      titleSmall → SemiBold) so the app reads as designed, not default. The warm
      sunrise **color palette is unchanged** — it was already the point.
- [x] Day-of-week selector rebuilt (`DayOfWeekSelector`): 7 circular single-letter
      bubbles running **Sunday-first** (S M T W T F S), each `weight(1f)` at 1:1
      aspect ratio so the row fits the exact width of any screen — replacing the
      Mon-first `FilterChip` row that overflowed. Selected = filled primary circle;
      unselected = outlined surfaceVariant. Full day names as contentDescription.
- [x] CRITICAL invariant, unit-tested: display order changed to Sunday-first but
      the stored `daysMask` keeps its ISO layout (bit 0 = Monday … bit 6 = Sunday),
      so no existing alarm shifts its firing days. `WeekdayOrder` is the single
      display↔storage mapping point; `WeekdayOrderTest` pins the bit mapping AND
      proves end-to-end (through `AlarmScheduler.computeNextTriggerMs`) that
      tapping the Sunday bubble produces an alarm that fires on a Sunday.
- [x] Alarm list redesign: whole-card-tappable rows (Edit button removed), time at
      `displaySmall` anchoring each card, tonal surfaceVariant container when
      enabled / dimmed 45%-alpha content when disabled, and one quiet summary line
      via new `DaySummaryFormatter` ("Weekdays · 3 locations"; named patterns
      collapse to "Every day"/"Weekdays"/"Weekends"/"One-time", others list
      Sunday-first: "Sun · Wed · Fri") — unit-tested in `DaySummaryFormatterTest`.
- [x] Friendly empty state (centered ☀️ + "No alarms yet") instead of a bare caption.
- [x] Existing JVM suites (scheduler math via `NextRingFormatterTest`, scan
      validation, awake-check policy, camera guard) all still pass — the redesign
      touched no alarm behavior.

## Phase 2.9 — Direct Boot: fire before first unlock (v0.4.1)

- [x] Alarms now fire in Android's **Direct Boot** window — after a reboot (e.g. an
      overnight OS auto-update) but before the user's first unlock. Previously the
      boot receiver couldn't read the alarm DB until unlock, so an early-morning alarm
      set before an overnight update would silently never ring.
- [x] Room DB moved to **device-protected storage** (`createDeviceProtectedStorageContext`)
      with a one-time `moveDatabaseFrom` migration so existing users keep their alarms.
      Same for the settings DataStore (best-effort file copy). Trade-off accepted with the
      user: device-key encryption at rest instead of credential-key (alarm times / barcode
      values, not secrets — the AOSP Clock approach).
- [x] `BootReceiver`, `AlarmReceiver`, `AlarmService`, `AlarmActivity`, `OverlayService`,
      `AwakeCheckActivity` marked `android:directBootAware="true"`; boot receiver already
      listened for `LOCKED_BOOT_COMPLETED`.
- [x] Scope confirmed with the user: the guaranteed win is that the alarm **fires**
      (rings/vibrates/lights the screen); scanning to disarm can require the user to unlock,
      which is fine. Custom tones in locked media storage fall back to the system alarm tone
      pre-unlock (existing fallback chain in `AlarmService.startAudioAndVibration`).
- [ ] **Needs on-device verification** (not testable in the JVM/CI sandbox): set an alarm,
      reboot, and confirm it fires while still on the lock screen without unlocking; verify
      the DB migration preserves alarms across the upgrade; confirm sound falls back
      gracefully pre-unlock. Track alongside the other device checks in BUGS.md.

## Phase 3 — Hardening (before giving it to anyone else)

> See **[BUGS.md](BUGS.md)** for the full bug backlog + test-coverage audit
> (v0.4.0). The three items below are tracked there in more detail (C2, C7, C8),
> alongside 9 potential bugs (B1–B9) found reading the whole repo.

- [ ] Fix B1 (foreground service can get stuck after a sticky restart) — see BUGS.md
- [ ] Fix B2 (two alarms at the same minute: second clobbers the first) — see BUGS.md
- [ ] Add Robolectric/instrumented infra so Room + service + Compose can be tested at all (BUGS.md C3/C7/C8)
- [ ] Unit tests for `AlarmScheduler.computeNextTriggerMs` (weekday masks, DST, exact-minute edge) — BUGS.md C2
- [ ] Unit tests for the `AlarmService` ring/scan/disarm state machine — BUGS.md C7
- [ ] Compose UI smoke test: create alarm → shows in list → toggle works — BUGS.md C8
- [ ] Enable Room `exportSchema` + committed schema JSON, adopt real migrations (required before first public release)
- [ ] Custom app icon (current one is a placeholder vector)
- [ ] Custom alarm sound picker (currently always the system default alarm tone)
- [ ] Gradually increasing volume option (start quiet, ramp to max)
- [ ] Show next-fire time ("Rings in 7 h 32 min") on the alarm list (the save-time
      note exists as of 0.3.2 — `NextRingFormatter` is ready to reuse here)
- [ ] Handle timezone/DST changes while an alarm is armed (`ACTION_TIMEZONE_CHANGED` receiver)
- [ ] In-app help page for OEM battery killers (Xiaomi/Oppo/Samsung instructions, dontkillmyapp.com links)
- [ ] Battery-optimization exemption prompt (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)

## Phase 4 — Nice-to-haves

- [ ] Generate & print QR codes from inside the app (share/print a code per location)
- [ ] Test-scan button in the barcode library ("will this scan in the dark bathroom?")
- [ ] Flashlight toggle on the scanner (dark rooms at 6 AM)
- [ ] Per-alarm ringtone and vibration pattern
- [ ] Statistics: wake-up times, time-to-first-scan, streaks
- [ ] Configurable emergency game difficulty (tap count / disable entirely)
- [ ] Widget / wear complication showing next alarm

## Phase 5 — Play Store (see RELEASING.md for the detailed walkthrough)

- [ ] Choose a license for the repo
- [ ] Release keystore created and backed up
- [ ] Release signing config + R8 minification enabled and smoke-tested
- [ ] Privacy policy page hosted (required: camera permission)
- [ ] Play Console account + app listing (icon 512², feature graphic, screenshots, descriptions)
- [ ] Data-safety + content-rating forms (all processing on-device, nothing collected)
- [ ] Internal testing track: live with a release build for a week of real mornings
- [ ] Closed testing (12 testers / 14 days — required for new personal accounts)
- [ ] Production rollout (staged 20% → 100%)
- [ ] Decide monetization: paid app vs free + one-off unlock IAP
