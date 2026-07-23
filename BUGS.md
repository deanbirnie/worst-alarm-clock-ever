# Bug backlog & test-coverage audit

A standing list of **potential bugs** and **test-coverage gaps** found by reading
the whole codebase (first pass at v0.4.0; kept current since). Fixed entries are
marked **✅** inline (B1, B2, and B10 so far); everything else is the "document
now, fix later" ledger. Each entry says where it is, what goes wrong, why, a
proposed fix, and how we'd test it.

Severity is about user impact on an alarm you're trusting to wake you:
**High** = an alarm could fail to wake you or the app could get stuck;
**Medium** = wrong/confusing behaviour in a real scenario; **Low** = polish/edge.

Confidence is **Confirmed** (clear from the code) or **Needs-device** (plausible
from the code but needs a real device / instrumented test to be sure — our CI is
JVM-only, see the coverage section).

---

## Bugs

### B1 — Foreground service can get stuck after a sticky restart · High · Confirmed · ✅ Fixed in 0.4.3
- **Where:** `AlarmService.onStartCommand` (`app/src/main/java/com/worstalarm/clock/alarm/AlarmService.kt`)
- **What:** `onStartCommand` always calls `startForeground(...)` first, then
  dispatches on `when (intent?.action)` with **no `else` branch**, and returns
  `START_STICKY`. If the OS kills and restarts the service it re-delivers a
  **null** intent (or, in theory, an unknown action). Then no `handle*` branch
  runs, nothing calls `stopSelfSafely()`, and the service is left running in the
  foreground showing the persistent "Alarm / Waking you up…" notification
  forever — with no alarm actually active.
- **Why:** `START_STICKY` + unconditional `startForeground` + no null/default
  handling.
- **Fix (0.4.3):** `onStartCommand` now returns `START_NOT_STICKY` (the paths that
  must outlive process death — step-rings and awake checks — re-arm themselves via
  AlarmManager, per `onDestroy`, so nothing relies on an OS restart with a null
  intent). A new pure `ServiceStartPolicy.decide(action)` gates the dispatch: a
  null/unrecognised action foregrounds briefly (required within ~5s) then calls
  `stopSelfSafely()`. A defensive `else -> stopSelfSafely()` was also added to the
  `when` as a second layer. Verified by `ServiceStartPolicyTest`.
- **How it was tested:** `ServiceStartPolicyTest` (pure JVM) pins null → STOP,
  unknown action → STOP, and every real action → PROCEED, plus a guard asserting
  the handled-action set matches the service's `ACTION_*` constants. Full
  end-to-end (actual sticky-restart) remains device/instrumented-only.

### B2 — Two alarms at the same minute: the second silently clobbers the first · High · Confirmed · ✅ Fixed in 0.4.6
- **Where:** `AlarmService.handleRing` + `AlarmSession.start` (`AlarmSession.kt`).
- **What:** There is exactly one global `AlarmSession`. If alarm A is ringing (or
  mid-routine) and alarm B fires, `handleRing(B)` saw a different id and called
  `AlarmSession.start(B)`, **overwriting A's state**. A's routine was abandoned:
  its pending step-ring PendingIntent orphaned and, for a recurring alarm, A never
  rescheduled from this path (the reschedule only runs on completion), so it could
  silently stop firing.
- **Why:** The engine assumes a single active alarm; nothing serialised or preserved
  a concurrent trigger.
- **Fix (0.4.6):** `handleRing` now defers to a pure, unit-tested
  `AlarmAdmissionPolicy.decide(activeAlarmId, incomingAlarmId)`. The alarm already
  ringing **always wins**; a *different* incoming alarm is **deferred** — re-armed
  ~60s later via the new `AlarmScheduler.scheduleRingAt` (reusing the alarm's own
  ACTION_FIRE PendingIntent) — so it isn't lost and rings once the active alarm is
  done (each retry simply defers again while the active one is still ringing). A
  duplicate delivery of the *same* alarm re-rings its current step; an invalid id no
  longer stops the service out from under an active alarm.
- **Residual (narrow):** if two alarms fire within the DB-read latency of *each
  other* (both before either claims the session), the admission decision is made
  before `AlarmSession.start`, so a sub-second double-fire can still race — far
  narrower than the reported "A already ringing when B fires" case this fixes. A
  synchronous session reservation would close it; deferred to the B3 work.
- **How it was tested:** `AlarmAdmissionPolicyTest` (JVM): no-active→RING_NEW,
  same-id→RERING_CURRENT, different-id→DEFER_INCOMING, invalid id→INVALID (with and
  without an active alarm), and the defer-time arithmetic. End-to-end two-alarm
  collision remains device/instrumented-only. This is coverage gap **C4**.

### B3 — Awake-check re-ring interacts badly with a second alarm / recurring reschedule · Medium · Needs-device
- **Where:** `AlarmService.completeRoutine` (`AlarmService.kt:208-237`),
  `handleAwakeCheckTimeout` (`AlarmService.kt:321-347`).
- **What:** After the final scan, `completeRoutine` reschedules a recurring alarm
  for its *next* occurrence **and** starts the awake-check cycle. If an awake check
  is later missed, `handleAwakeCheckTimeout` re-rings by pinning `AlarmSession` at
  the last step. During that awake-check window (session cleared, checks pending)
  a *different* alarm firing, or the same alarm's next occurrence arriving, can
  interleave with the pending checks. Same single-session root cause as B2, but the
  awake-check timeline widens the window to 10–30 min.
- **Why:** Awake-check state (Room + AlarmManager) is per-alarm, but the ringing
  session is a singleton, so overlapping timelines aren't reconciled.
- **Proposed fix:** Extend B2's `AlarmAdmissionPolicy` (0.4.6) to the awake-check
  timeline; explicitly define what happens when an awake check and a fresh ring
  collide (the admission policy currently only guards the ringing session, not a
  pending awake check).
- **How to test:** Model the timeline transitions as pure state and unit-test the
  collision cases; full confidence needs an instrumented/manual run.

### B4 — `saveAlarm` isn't atomic across the alarm row and its steps · Medium · Confirmed
- **Where:** `Repository.saveAlarm` (`app/src/main/java/com/worstalarm/clock/data/Repository.kt:36-42`)
- **What:** The alarm insert/update and `replaceSteps` are two separate suspend
  DAO calls, not one `@Transaction`. A crash/process-death between them can leave
  an alarm with **no steps** (or stale steps). `handleRing` partly masks this by
  disabling an alarm whose `orderedSteps` is empty — which means a save
  interrupted at the wrong moment could silently *disable* an alarm the user
  thought they saved.
- **Why:** No surrounding transaction.
- **Proposed fix:** Add a `@Transaction suspend fun` on `AlarmDao` that does the
  alarm upsert + `replaceSteps` in one transaction, and call that from the repo.
- **How to test:** Room in-memory instrumented test (or Robolectric) asserting the
  alarm+steps commit together; see coverage gap C3.

### B5 — `QrGeneratorScreen` list key can collide on a duplicate random value · Low · Confirmed
- **Where:** `QrGeneratorScreen` (`app/src/main/java/com/worstalarm/clock/ui/qr/QrGeneratorScreen.kt:145`)
  — `items(items = codes, key = { it.value })`.
- **What:** Codes are 10 chars over a 36-char alphabet, so a duplicate is very
  unlikely but possible. Two identical values → Compose throws
  "Key … was already used" and the screen crashes.
- **Why:** Using the (non-unique-by-construction) value as the list key.
- **Proposed fix:** Give `GeneratedCode` a stable unique id (e.g. an incrementing
  counter or `UUID`) and key on that; or dedupe on generation.
- **How to test:** Unit-test the generator/dedupe helper (uniqueness across N
  draws with a seeded `Random`).

### B6 — Emergency "complete" uses `DisposableEffect` as a side-effect trigger · Low · Confirmed
- **Where:** `EmergencyScreen` (`app/src/main/java/com/worstalarm/clock/ui/emergency/EmergencyScreen.kt:73-76`)
- **What:** `DisposableEffect(taps) { if (taps >= TARGET_TAPS) onComplete(); onDispose {} }`
  fires `onComplete()` during composition. It works today (taps only reaches 500
  once), but it's an anti-pattern: any recomposition at `taps >= 500` re-invokes
  `onComplete()`. Because taps can still increment past 500 (the tile stays
  clickable), a 501st tap re-fires `onComplete()`.
- **Why:** Side effect in `DisposableEffect` instead of `LaunchedEffect`, and no
  "already completed" latch.
- **Proposed fix:** Use `LaunchedEffect(taps >= TARGET_TAPS)` gated on a
  one-shot boolean, and stop accepting taps once complete.
- **How to test:** Extract the tap/idle/complete rules into a pure helper (see
  coverage gap C1) and unit-test "complete fires exactly once".

### B7 — `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` both declared; neither is needed · Low · Needs-device
- **Where:** `AndroidManifest.xml:7-8`.
- **What:** Scheduling uses `AlarmManager.setAlarmClock`, which is exempt from the
  exact-alarm permission. Declaring `USE_EXACT_ALARM` (a Play-policy-restricted
  permission) may draw Play Console review scrutiny for no functional gain.
- **Why:** Belt-and-braces permissions left in.
- **Proposed fix:** Confirm on-device that `setAlarmClock` works without them,
  then drop at least `USE_EXACT_ALARM`. (Revisit before the Play submission task in
  RELEASING.md.)
- **How to test:** Device check on API 31–34; not unit-testable.

### B8 — Silent awake-check popup starts a `mediaPlayback` foreground service · Low · Needs-device
- **Where:** `AlarmService.onStartCommand` (`AlarmService.kt:53-58`) reached via
  `ACTION_AWAKE_CHECK_SHOW`/`_TIMEOUT`.
- **What:** Every start — including the silent awake-check popup, which plays no
  audio — foregrounds the service as `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`. On
  Android 14 (targetSdk 34) the declared FGS type is supposed to match what the
  service actually does; a media type with no playback is at least misleading and
  could trip future FGS enforcement.
- **Why:** One-size-fits-all `startForeground` type.
- **Proposed fix:** Use an appropriate type (or a plain foreground notification)
  for the non-audio awake-check starts; keep `mediaPlayback` only for actual
  ringing. Confirm on a real device.
- **How to test:** Device/instrumented; not unit-testable.

### B9 — Scheduler DST / weekday-mask correctness is assumed, not proven · Medium · Needs-device (logic is unit-testable)
- **Where:** `AlarmScheduler.computeNextTriggerMs` (`AlarmScheduler.kt`).
- **What:** This is the single most safety-critical function (it decides when you
  wake up) and its trickiest cases are **unverified**: a spring-forward morning
  where the alarm's wall-clock time doesn't exist, a fall-back morning where it
  happens twice, and the exact-minute boundary (`candidate == now`). The existing
  `NextRingFormatterTest` deliberately uses July dates to *avoid* DST, so DST is a
  genuine hole, not just untested-by-omission.
- **Why:** No tests target these branches.
- **Proposed fix:** No code change until tests exist — first pin the current
  behaviour, then decide if any of it is wrong.
- **How to test:** See coverage gap **C2** — it's pure and JVM-testable with a
  fixed `Calendar`/timezone.

### B10 — "I'm awake" did nothing when the popup was launched from the notification/lock screen · High · Confirmed · ✅ Fixed in 0.4.5
- **Where:** `AwakeCheckActivity` (`app/src/main/java/com/worstalarm/clock/alarm/AwakeCheckActivity.kt`)
  + `AlarmService.buildNotification` (`AlarmService.kt`).
- **What:** A **regression introduced in 0.4.4** by the full-screen-intent launch
  (Phase 2.11). The awake-check popup can be surfaced three ways: the receiver's
  direct `startActivity` (carries `EXTRA_ALARM_ID`), the foreground notification's
  **full-screen intent**, and a **tap on that notification**. The latter two were
  launched by a PendingIntent that carried **no** `EXTRA_ALARM_ID`, so the activity
  read `-1`; tapping "I'm awake" sent `ACTION_AWAKE_CHECK_DISMISS` with id `-1`, and
  `handleAwakeCheckDismiss` drops anything with `alarmId <= 0`. The button silently
  did nothing, the check was scored a miss, and the alarm re-rang. Hit in the wild
  on a cold, locked-screen alarm (reported 2026-07-23).
- **Why:** The dismiss id came from the *launching intent*, which is absent on the
  full-screen / notification-tap paths; the direct-launch path (which has it) is
  blocked from a background/locked state, so the id-less paths are exactly the ones
  that run when it matters.
- **Fix (0.4.5):** Resolve the dismiss target from the live `AwakeCheckSession`
  (always set while a popup shows, independent of launch path) via the new pure
  `AwakeCheckPolicy.resolveDismissTarget`, with the intent extra as fallback; also
  embed `EXTRA_ALARM_ID` in the notification's PendingIntents, and use
  `startForegroundService` for the dismiss (robust from over the lock screen).
- **How it was tested:** `AwakeCheckPolicyTest.resolveDismissTarget*` (session wins,
  intent fallback, both-invalid → -1). End-to-end surfacing still needs a device
  (see the device list below).

### B11 — Armed alarms aren't re-armed on a timezone / manual clock change · Medium · Confirmed
- **Where:** `AndroidManifest.xml` (there is no time/timezone receiver); alarms are
  only ever re-armed by `BootReceiver` (`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` /
  `MY_PACKAGE_REPLACED`) and at save time.
- **What:** `AlarmScheduler` arms each alarm with `AlarmManager.setAlarmClock` at an
  **absolute instant** computed from the wall-clock time and timezone in effect *when it
  was armed*. If the user later changes timezone (travel) or sets the system clock, that
  pinned instant no longer matches the intended local time — the alarm fires at the old
  offset, which can be hours early or late. Nothing listens for
  `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` to recompute and re-arm, and a
  timezone change doesn't reboot, so the boot re-arm path never runs.
- **Why:** Re-arming is tied to boot / package-replace / explicit save only; there is no
  time- or timezone-change receiver.
- **Proposed fix:** Add a `directBootAware` receiver for `ACTION_TIMEZONE_CHANGED` (and
  `ACTION_TIME_CHANGED`) that re-arms all enabled alarms through the same path
  `BootReceiver` uses. (A single armed alarm that merely *crosses* a DST boundary is
  already resolved to the correct instant by `Calendar` at compute time — the gap here is
  a genuine timezone/manual-clock change. DST *logic* correctness inside
  `computeNextTriggerMs` is tracked separately in **B9**.)
- **How to test:** The re-arm fan-out (enabled alarms → re-arm requests) is the same pure
  logic BootReceiver drives and is JVM-testable; the receiver actually firing on a
  timezone change is device-only.

---

## Test-coverage audit

### What's covered today (all JVM/`src/test`, run in CI)
| Suite | Covers |
|---|---|
| `ScanValidatorTest` | Duplicate-frame / reused-barcode scan decisions |
| `AwakeCheckPolicyTest` | Awake-check intervals, dismiss transitions, stale-timeout guard |
| `AlarmSessionTest` | Session start + final-step pin for miss re-ring |
| `NextRingFormatterTest` | "Rings in …" formatting + a few scheduler paths (non-DST) |
| `AlarmEntityTest` | `awakeCheckEnabled` default |
| `WeekdayOrderTest` | Sunday-first display ↔ ISO storage mapping (+ 1 scheduler check) |
| `DaySummaryFormatterTest` | Alarm-list day summary wording |
| `CameraBindingGuardTest` | CameraX bind/dispose race guard |

Good instinct throughout: safety-critical *logic* is pulled into pure,
Android-free objects (`ScanValidator`, `AwakeCheckPolicy`, `WeekdayOrder`,
`NextRingFormatter`, `CameraBindingGuard`) so it's unit-testable. The gaps below
are mostly **logic that hasn't been extracted yet** and **layers CI can't reach**.

### The structural gap
CI runs `testDebugUnitTest` only — **pure JVM**. There are **no instrumented
(`androidTest`) tests and no Robolectric**, so nothing exercises Room migrations,
DAOs, the `AlarmService` state machine end-to-end, PendingIntent wiring, or any
Composable. Anything needing an Android runtime is currently unverified.

### Proposed new coverage (prioritised)

| # | Gap | Proposed test | Type | Unblocks |
|---|---|---|---|---|
| **C2** | `computeNextTriggerMs` DST + exact-minute + weekday-mask edges | Pure JVM tests with fixed `Calendar` + explicit `TimeZone` (incl. a DST zone like `America/New_York` around Mar/Nov), asserting spring-forward, fall-back, `candidate == now`, and "next enabled weekday" wrap | JVM | B9 |
| **C1** | Emergency mini-game rules (idle reset counting, complete-once, alarm-stays-on after N idles) | Extract a pure `EmergencyGamePolicy` (taps, idle, resets → state) and unit-test it | JVM | B6 |
| **C4** ✅ | Single-active-alarm / admission policy | `AlarmAdmissionPolicyTest` — ring / re-ring / defer / invalid (done in 0.4.6 with B2) | JVM | B2 done; B3 still open |
| **C5** | Foreground-start decision (keep foreground vs stop) | Pure function over `(action, sessionActive, awakeActive)`; unit-test incl. null action | JVM | B1 |
| **C3** | Room: migrations 1→2→3→4 and `saveAlarm` atomicity + `usageCount`/RESTRICT delete guard | `androidTest` with in-memory DB (or Robolectric) — needs new test infra | Instrumented | B4 |
| **C6** | `NumberStepperField` clamping / empty-field / round-trip behaviour | Extract the text↔value normalisation into a pure helper and unit-test; optional Compose UI test | JVM (+ optional UI) | — |
| **C7** | `AlarmService` state machine (ring → scan → step → complete → awake → miss re-ring) | Instrumented or a refactor that drives the transitions through a pure reducer | Instrumented / refactor | B1, B2, B3 |
| **C8** | Compose smoke tests (list renders, toggle persists, editor saves, day bubbles fit) | `androidTest` with Compose test rule — needs new test infra | Instrumented | — |

### Infrastructure to add before the instrumented rows are possible
- A `src/androidTest` source set + `androidTestImplementation` deps
  (`androidx.test`, `room-testing`, `compose-ui-test-junit4`), **or** Robolectric
  for JVM-hosted Android tests.
- A CI job (or step) that runs them — today's workflow only runs
  `testDebugUnitTest`. Instrumented tests need an emulator on CI (slower) or a
  Robolectric path (JVM, keeps CI fast). Recommend Robolectric for the Room/DAO
  and migration coverage (C3) so it stays in the existing fast lane.

### Device-only behaviours to verify (no JVM coverage possible)
- **Direct Boot (v0.4.1):** alarm fires on the lock screen after a reboot without a
  first unlock; the `moveDatabaseFrom` migration preserves existing alarms on upgrade;
  pre-unlock sound falls back to the system alarm tone when a custom tone is unreadable.
  Reproduce with: set an alarm a few minutes out → reboot → don't unlock.
- **Full-screen alarm + awake check surfacing (v0.4.4/0.4.5):** the ringing UI appears
  over the lock screen when the phone is asleep (needs `USE_FULL_SCREEN_INTENT`, and on
  Android 14+ the in-app grant); tapping "I'm awake" on the popup — whether it auto-showed
  or was opened from the notification — actually dismisses the check (B10). Note: a
  full-screen intent only *auto-launches* full-screen while the screen is locked/off; when
  the screen is already on it surfaces as a heads-up the user taps. Reproduce: alarm →
  scan through → let the phone sleep → wait for the awake check.
- B7 (redundant exact-alarm permissions) and B8 (mediaPlayback FGS type) from above.

### Cheapest high-value next steps
1. **C2** (scheduler DST) — pure, highest safety value, no new infra.
2. **C5 + B1** (foreground-start decision) — tiny extract, kills a real stuck-state bug.
3. **C1 + B6** (emergency policy extract) — removes the last untested piece of the
   disarm path.
4. Stand up Robolectric once, then land **C3** (Room migrations/atomicity).
