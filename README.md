# Worst Alarm Clock Ever

An Android alarm clock you **cannot turn off from bed**. The only way to silence
it is to physically walk to a barcode (or QR code) you registered earlier —
the toothpaste in the bathroom, the coffee tin in the kitchen, the QR code taped
to your monitor — and scan it with the phone's camera. No snooze button. No
"stop" button. The alarm keeps ringing until you get up and scan.

## How it works

1. **Register barcodes.** In the app's barcode library, scan (or type) any
   barcode or QR code and give it a name — "Bathroom toothpaste", "Kitchen
   coffee", "Desk QR". Any scannable code works: product barcodes, printed QR
   codes, book ISBNs.
2. **Create an alarm.** Pick a time, pick the days of the week it repeats
   (or none for a one-time alarm), and attach one or more *locations*, each
   pointing at a saved barcode.
3. **Wake up the hard way.** At alarm time the phone rings at full alarm
   volume with vibration, over the lock screen. The screen shows which barcode
   to scan. Scanning the right code stops the ringing; scanning anything else
   flashes "wrong barcode" and keeps ringing.

### Alarm paths (the advanced mode)

An alarm isn't limited to a single barcode — it's an ordered **routine**:

> Ring → scan **Bathroom** → 5 quiet minutes (brush teeth) → ring again →
> scan **Kitchen** → 5 quiet minutes (make coffee) → ring again →
> scan **Desk** → alarm fully disarmed. You're up, caffeinated, and at your desk.

Each step has its own configurable pause before the next ring. A single-step
alarm is just a path of length one. The pause countdown is shown on screen and
in the persistent notification.

## Features

- **Barcode library** — save any barcode/QR (scan with the camera or type the
  value manually); reuse the same code across multiple alarms and steps.
  Codes in use by an alarm are protected from deletion.
- **Multi-step routines ("alarm paths")** — ordered locations with per-step
  quiet periods between rings.
- **Recurring or one-shot alarms** — pick any subset of weekdays; with no days
  selected the alarm fires once and disables itself.
- **"Rings in …" note on save** — saving an enabled alarm shows a little note
  with the time remaining until it rings ("Rings in 7 h 32 min"), computed from
  the exact same schedule handed to the OS, so it can't lie.
- **Full lock-screen takeover** — the alarm UI appears over the keyguard,
  turns the screen on, and ignores the back button.
- **Re-assert overlay** — if you escape to the home screen while the alarm is
  active, a full-screen overlay blocks the phone until you return to the alarm
  (requires the "Display over other apps" permission).
- **Maximum volume, locked** — alarm plays on the alarm audio stream at max
  volume with a vibration pattern; the volume/mute buttons are swallowed
  while the alarm is active, so it can't be quieted without disarming it.
- **Survives reboots — including before you unlock.** Enabled alarms are
  rescheduled after a restart or app update, and the alarm engine runs in
  Android's **Direct Boot** mode: an alarm set for the early hours still fires
  after an overnight OS update reboots the phone, even though you haven't
  unlocked it yet. (You unlock as normal to scan and disarm.)
- **Doze-proof scheduling** — uses `AlarmManager.setAlarmClock`, the
  strongest alarm the OS offers; it fires exactly on time without needing
  battery-optimization exemptions.
- **Offline barcode scanning** — Google ML Kit with the bundled model:
  QR, EAN-13/8, UPC-A/E, Code 128/39/93, Codabar, ITF, PDF417, Aztec,
  Data Matrix. No internet needed, ever.
- **Custom alarm sounds** — pick any audio file on the device, globally in
  Settings or per alarm; falls back to the system alarm tone if the file
  goes missing.
- **QR generator** — create random QR codes in-app, add them straight to
  your library, and share/print the PNG (tape one to the bathroom mirror, or
  set one as your PC wallpaper so you must log in before you can scan it).
- **Locations, decoupled** — barcodes can carry an optional location
  ("Kitchen"); picking a location in the alarm editor auto-selects its
  barcode when only one lives there.
- **Warm sunrise theme** — muted ambers and dusky roses, easy on 6 AM eyes.
- **Emergency escape hatch** — for genuine emergencies there is exactly one
  way out without scanning: a mini-game that requires tapping the lit square
  on a 4×4 grid **500 times**. Going idle for 30 seconds aborts the attempt
  and the alarm resumes — and after three idle resets the game stops buying
  silence: the alarm keeps ringing while you tap. Annoying enough that going
  to the kitchen is easier.
- **Awake check** (per-alarm toggle, on by default) — scanning the final
  barcode drops the lock-screen takeover, but the alarm isn't done: twice, at
  a random point 5-15 minutes apart, a popup appears and gently nudges you (a
  soft chime + light buzz, repeating so you notice without watching the
  screen — never alarm-grade), and must be tapped "I'm awake" within ~3
  minutes. Miss one and the alarm rings again — rescan just the final location
  to try the pair again. Only two dismissals in a row actually turns it off,
  so drifting back to sleep after
  "disarming" doesn't work. Turn it off per alarm in the alarm editor if you
  don't want it (e.g. a short nap).

## Documentation map

| File | What's in it |
|---|---|
| [BUILDING.md](BUILDING.md) | Every way to produce and install the APK: download from CI, Android Studio, command line. |
| [RELEASING.md](RELEASING.md) | Signing, versioning, long-term maintenance, and the full Google Play publishing path. |
| [DESIGN.md](DESIGN.md) | Exact colors, typography, spacing, and screen-by-screen layout of the current UI. |
| [TODO.md](TODO.md) | Project task list — what's done and what's next. |
| [BUGS.md](BUGS.md) | Known-bug backlog and test-coverage audit (found by code review, fix later). |

## The user interface

Three screens, kept deliberately simple:

- **Alarm list (home).** Every alarm with its time, days, step count, and an
  on/off switch. A banner prompts for the overlay permission until granted.
  `+` creates an alarm; the QR icon in the top bar opens the barcode library.
- **Barcode library.** Your saved codes with name, value, and format.
  Add/edit via a dialog that lets you scan with the camera or type a value.
- **Alarm editor.** Label, time picker, a Sunday-first row of round
  single-letter day bubbles that fits any screen width, and the ordered list
  of routine steps — each step names a location, picks a barcode from the
  library, and (except the last) sets the quiet delay before the next ring.

Plus the **ringing screen** you'll meet every morning: which location to scan,
step progress, a giant SCAN BARCODE button that opens the camera, and the
countdown between steps.

## Architecture

Native Android, single module, 100% Kotlin.

| Layer | Tech |
|---|---|
| UI | Jetpack Compose + Material 3, Navigation Compose |
| State | `ViewModel` + Kotlin `StateFlow` |
| Persistence | Room (SQLite) |
| Scanning | CameraX + ML Kit barcode scanning (bundled model) |
| Scheduling | `AlarmManager.setAlarmClock` + `BroadcastReceiver` |
| Ringing | Foreground `Service` + `MediaPlayer` on the alarm stream |

### Data model (Room)

```
AlarmEntity        id, label, hour, minute, daysMask (bit0=Mon … bit6=Sun; 0 = one-shot), enabled
BarcodeEntity      id, name, rawValue, format (ML Kit format int)
RoutineStepEntity  id, alarmId → AlarmEntity, stepIndex, locationLabel,
                   barcodeId → BarcodeEntity, timeToNextRingSeconds
AwakeCheckEntity   alarmId → AlarmEntity (PK), dismissedCount, nextCheckAtMs, popupDeadlineAtMs
```

Steps cascade-delete with their alarm; barcodes referenced by any step are
protected (`RESTRICT`) and the UI refuses to delete them.

### The alarm lifecycle

```
AlarmScheduler.schedule()            computes next trigger, arms AlarmManager.setAlarmClock
        │  (alarm time)
        ▼
AlarmReceiver (broadcast)            starts AlarmService (foreground); the service's
                                     full-screen-intent notification surfaces AlarmActivity
                                     over the lock screen (needs USE_FULL_SCREEN_INTENT)
        │
        ▼
AlarmService                         owns the state machine (AlarmSession StateFlow):
   ring step N  ──────────────►      audio (max alarm volume, looping) + vibration
   user scans correct barcode ─►     stop audio; if last step → complete routine;
                                     else wait timeToNextRingSeconds → ring step N+1
   complete routine ───────────►     reschedule (recurring) or disable (one-shot);
                                     drop the lock screen; begin the awake-check cycle
        │
        ▼
AlarmActivity (Compose)              lock-screen UI: ringing panel ⇄ camera scanner ⇄ emergency game
OverlayService                       full-screen overlay if the user escapes to home mid-alarm
BootReceiver                         re-arms all enabled alarms (+ pending awake checks) after reboot

Awake-check cycle (AwakeCheckEntity, AlarmScheduler.scheduleAwakeCheck/-Timeout):
   schedule popup 5-15 min out ─►    AwakeCheckActivity shows + gentle cue repeats (chime+buzz)
   "I'm awake" tapped in time ─►     2nd dismissal this cycle? done : schedule the next popup
   ~3 min dismiss deadline expires ► full reset; re-ring pinned at the FINAL step only
```

Source lives under `app/src/main/java/com/worstalarm/clock/`:
`data/` (Room entities, DAOs, repository), `alarm/` (scheduler, receivers,
services, ringing activity), `ui/` (Compose screens, view model, scanner,
theme).

## Permissions

| Permission | Why | When asked |
|---|---|---|
| Camera | Scanning barcodes | First launch (and again at scan time if denied) |
| Notifications (Android 13+) | The foreground service's persistent notification + full-screen alarm intent | First launch |
| Full-screen intent (Android 14+) | Launches the ringing UI over the lock screen when the phone's asleep — without it the alarm rings but no screen appears | Auto-granted to alarm apps; a banner deep-links to the setting if not |
| Display over other apps | The re-assert overlay that blocks escaping the alarm | Banner on the home screen (optional but recommended) |
| Exact alarms / boot / vibrate / wake lock | Core alarm behavior (boot receiver + engine run in Direct Boot so alarms fire before first unlock) | Install time (no prompt; `setAlarmClock` needs no special exemption) |

## Honest limitations

- **A determined user can still cheat.** Force-stop or uninstall via Settings,
  reboot into safe mode, or power the phone off. True kiosk-grade lockdown
  requires device-owner provisioning, which is out of scope for a consumer
  app (and would make Play Store review much harder). The overlay +
  lock-screen takeover makes cheating *inconvenient*, which is the point:
  at 6 AM, walking to the kitchen is easier than fighting the phone.
- **Don't force-stop the app.** A force-stopped app's alarms won't fire until
  it's opened again — Android withholds boot/alarm broadcasts from a
  force-stopped app, so this is the one reboot case Direct Boot can't rescue.
  Normal swipes from recents are fine.
- **Locked-boot sound falls back to the system alarm tone.** During Direct
  Boot (before first unlock) a *custom* alarm tone that lives in your locked
  media storage may not be readable yet, so the alarm rings with the system
  default alarm sound instead. It still rings, vibrates, and lights the screen.
- **Volume lock has a boundary.** The physical/software volume keys are
  blocked while the alarm screen has focus, and `AlarmService` re-forces max
  volume at the start of every ring regardless. But it only guards this
  activity — if the user escapes to the home screen (gesture nav can't be
  key-intercepted) and reaches system Settings or a Quick Settings volume
  slider before the re-assert overlay kicks in, volume can be changed there.
  Same "inconvenient, not impossible" tradeoff as the rest of the lockdown.
- **OEM battery killers.** Aggressive vendors (Xiaomi, Oppo, some Samsung
  modes) can delay even `setAlarmClock`. If alarms are late, exempt the app
  from battery optimization in system settings.
- **Awake check details beyond the on/off toggle aren't independently
  configurable.** The 90-second dismiss window and "a miss resets both
  checks, not just the missed one" are fixed behavior, not settled
  requirements — reasonable defaults picked without a confirmed spec.
- **iOS is not possible** in this form — iOS does not let third-party apps
  take over the lock screen or play unstoppable audio from the background.

## Quick start for developers

```sh
git clone <this repo>
cd worst-alarm-clock-ever
./gradlew assembleDebug          # needs JDK 17 + Android SDK (see BUILDING.md)
adb install app/build/outputs/apk/debug/WorstAlarmEver-<version>-debug.apk
```

No Android SDK on your machine? Push to GitHub and download the APK from the
**Actions** tab instead — see [BUILDING.md](BUILDING.md).

## License

No license file yet — all rights reserved by default. Pick a license before
publishing the source (see TODO.md).
