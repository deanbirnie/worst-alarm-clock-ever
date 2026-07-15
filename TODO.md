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
- [ ] Verify v0.2 on a real phone (install APK, run one multi-step alarm)

## Phase 3 — Hardening (before giving it to anyone else)

- [ ] Unit tests for `AlarmScheduler.computeNextTriggerMs` (weekday masks, DST, exact-minute edge)
- [ ] Unit tests for the `AlarmService` ring/scan/disarm state machine
- [ ] Compose UI smoke test: create alarm → shows in list → toggle works
- [ ] Enable Room `exportSchema` + committed schema JSON, adopt real migrations (required before first public release)
- [ ] Custom app icon (current one is a placeholder vector)
- [ ] Custom alarm sound picker (currently always the system default alarm tone)
- [ ] Gradually increasing volume option (start quiet, ramp to max)
- [ ] Show next-fire time ("Rings in 7 h 32 min") on the alarm list
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
