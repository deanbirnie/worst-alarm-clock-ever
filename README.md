# Worst Alarm Clock Ever

An Android alarm clock that won't let you go back to sleep. You configure a
routine of locations around your home (bathroom, kitchen, desk, …). At wake
time the alarm rings and the only way to silence it is to physically walk to
each location and scan its barcode. After scanning, a configurable delay runs
down before the next location's alarm fires. Strict order. No snooze.

An **emergency stop** mini-game is provided for the "I'm on fire" case: tap
the lit square on a 4×4 grid 500 times. Any 30-second idle resets the counter
and resumes the alarm.

## Features

- **Barcode library** — save any barcode (scan or type) with a name; reuse
  across alarms and locations.
- **Multi-step routines** — order and per-step delays fully configurable.
- **Recurring alarms** — pick any subset of weekdays, or leave all unchecked
  for a one-shot alarm.
- **Lock-screen takeover** — alarm activity shows over the keyguard, turns on
  the screen, blocks back/home/menu keys, and re-asserts itself via an overlay
  if you manage to navigate away.
- **ML Kit barcode scanning** — QR, EAN-13, UPC-A, Code 128, PDF417, Data
  Matrix, etc. Works offline (bundled model).
- **Survives reboot** — enabled alarms are rescheduled on boot.
- **Uses `AlarmManager.setAlarmClock`** — bypasses Doze without needing the
  `SCHEDULE_EXACT_ALARM` allowlist on Android 12+.

## Building the APK

### Requirements

- Android Studio Hedgehog (2023.1) or newer, **or** a standalone Gradle 8.7+
  install with the Android command-line tools and SDK (platform 34).
- JDK 17.

### From Android Studio (easiest)

1. `File → Open…` → select the repo root.
2. Let Gradle sync (first run will download dependencies + the Gradle
   wrapper). Accept any SDK license prompts.
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. The debug APK lands at
   `app/build/outputs/apk/debug/app-debug.apk`.
5. Plug in an Android device with USB debugging enabled and either
   `Run ▶` it, or `adb install app/build/outputs/apk/debug/app-debug.apk`.

### From the command line

The Gradle wrapper JAR is not checked in. Run this once after cloning:

```sh
gradle wrapper --gradle-version 8.7
```

(Requires a system `gradle` on PATH — Android Studio, Homebrew's `gradle`,
or a manual install will all do.)

Then:

```sh
./gradlew assembleDebug            # debug APK
./gradlew assembleRelease          # unsigned release APK
./gradlew installDebug             # build + install on connected device
```

## First-run setup

On first launch the app will ask for a few permissions. Grant all of them or
the alarm can't do its job:

1. **Notifications** (Android 13+) — required for the foreground alarm service.
2. **Camera** — required for barcode scanning.
3. **Display over other apps** — used by the re-assert overlay. The home
   screen shows a banner with a one-tap button to open the right settings page.
4. **Ignore battery optimizations** — not strictly required because we use
   `setAlarmClock`, but recommended on aggressive OEMs (Xiaomi, Samsung,
   Oppo, …).

## Usage

1. Open the **Barcode library** (QR icon in the top bar). Add one entry per
   barcode you plan to leave around the house. Scan or type the value and
   give it a name (e.g. "Bathroom QR").
2. Tap **+** on the home screen to create an alarm.
3. Set time, pick days, add locations in order. Each location needs a label
   and a reference to a saved barcode. For every step except the last, set
   the delay between "user scanned this step" and "next ring fires".
4. Save. Toggle the alarm on.
5. Print/place your barcodes. (Any barcode works — product codes on
   cereal boxes are fine. You can even reuse the same barcode at multiple
   locations, in which case the alarm just advances through steps as you
   scan it repeatedly.)

## Architecture notes

- `com.worstalarm.clock.alarm.AlarmScheduler` — schedules the next firing
  time per alarm via `AlarmManager.setAlarmClock`.
- `AlarmReceiver` (BroadcastReceiver) — receives the system broadcast, starts
  `AlarmService` as a foreground service and launches `AlarmActivity`.
- `AlarmService` — owns the ringing state machine, plays the system alarm
  sound on `STREAM_ALARM`, drives the between-step countdown with a `Handler`,
  and reschedules or disables the alarm once disarmed.
- `AlarmSession` — a `StateFlow<State?>` singleton shared between the service
  and the activity.
- `AlarmActivity` — Compose UI: `RingingPanel` → `ScanningPanel` (CameraX +
  ML Kit) → `EmergencyScreen` (500-tap game).
- `OverlayService` — full-screen `TYPE_APPLICATION_OVERLAY` shown if the user
  backgrounds the alarm activity; tapping "CONTINUE" brings it back.
- `BootReceiver` — reschedules enabled alarms after reboot / app upgrade.

## Known limits

- **No true kiosk mode.** A determined user can still reach the notification
  shade and long-press to go to settings, or boot into safe mode. Real
  "can't uninstall" behavior requires provisioning the app as Device Owner
  via ADB on a factory-reset device. That's a future option; the current
  build is the aggressive-overlay variant you picked during setup.
- **iOS port not started.** Stack is native Kotlin/Jetpack Compose; an iOS
  port would be a rewrite (SwiftUI + AVFoundation's `AVCaptureMetadataOutput`
  for barcodes + a UserNotifications time-interval trigger, with the
  important caveat that iOS does not let third-party apps block the home
  gesture).
