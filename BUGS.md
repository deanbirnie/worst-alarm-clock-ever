# Bug backlog & test-coverage audit

A standing list of **potential bugs** and **test-coverage gaps** found by reading
the whole codebase (first pass at v0.4.0; kept current since). Fixed / verified
entries are marked **✅** inline (B1, B2, B5, B6, B7, B8, B9, B10, B11 so far);
everything else is the "document now, fix later" ledger. Each entry says where it is,
what goes wrong, why, a proposed fix, and how we'd test it.

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

### B5 — `QrGeneratorScreen` list key can collide on a duplicate random value · Low · Confirmed · ✅ Fixed in 0.4.8
- **Where:** `QrGeneratorScreen` — `items(items = codes, key = { it.value })`.
- **What:** Codes are 10 chars over a 36-char alphabet, so a duplicate was very
  unlikely but possible. Two identical values → Compose throws
  "Key … was already used" and the screen crashes.
- **Why:** Using the (non-unique-by-construction) value as the list key.
- **Fix (0.4.8):** Extracted a pure `QrCodeGenerator`. `GeneratedCode` now carries a
  stable, list-unique `id` (incrementing past the current max), and the list keys on
  `it.id` instead of `it.value`, so a value collision can never crash it. As a bonus,
  `newCodes` also dedupes values against the existing list, so the user never sees the
  same code twice.
- **How it was tested:** `QrCodeGeneratorTest` (JVM) — value format, unique/increasing
  ids across successive append-then-generate calls, no value repeats an existing one,
  and an all-unique 2000-code batch.

### B6 — Emergency "complete" uses `DisposableEffect` as a side-effect trigger · Low · Confirmed · ✅ Fixed in 0.4.8
- **Where:** `EmergencyScreen` (`app/src/main/java/com/worstalarm/clock/ui/emergency/EmergencyScreen.kt`)
- **What:** `DisposableEffect(taps) { if (taps >= TARGET_TAPS) onComplete(); onDispose {} }`
  fired `onComplete()` during composition. Any recomposition at `taps >= 500` re-invoked
  it, and because taps could still increment past 500 (the tile stayed clickable), a
  501st tap re-fired `onComplete()`.
- **Why:** Side effect in `DisposableEffect` instead of `LaunchedEffect`, and no
  "already completed" latch.
- **Fix (0.4.8):** Extracted a pure `EmergencyGamePolicy`. Completion now runs in a
  `LaunchedEffect(taps)` behind a one-shot `completed` latch, and the grid stops
  accepting taps at the target (`enabled = isLit && EmergencyGamePolicy.acceptsTap(taps)`),
  so taps can't advance to 501 — `onComplete()` fires exactly once. The idle watchdog also
  bails once completed, and the "next lit square" pick moved into the tested policy.
- **How it was tested:** `EmergencyGamePolicyTest` (JVM) — `isComplete`/`acceptsTap`
  thresholds, a simulation asserting completion fires exactly once under 600 tap attempts,
  idle-timeout boundary, and `nextLitIndex` (never the current cell, covers the rest).

### B7 — Redundant `SCHEDULE_EXACT_ALARM` declared alongside `USE_EXACT_ALARM` · Low · Confirmed · ✅ Fixed in 0.4.9
- **Where:** `AndroidManifest.xml`.
- **What:** Both `SCHEDULE_EXACT_ALARM` (API 31) and `USE_EXACT_ALARM` (API 33) were
  declared. Scheduling goes through `AlarmManager.setAlarmClock`, which is exempt from
  the exact-alarm permission, so at least one was redundant.
- **Correction to the original note:** the first pass suggested dropping
  *`USE_EXACT_ALARM`*. That's backwards. `USE_EXACT_ALARM` is the modern,
  install-granted "genuine alarm-clock app" permission, and — per Android 14's
  behaviour change — being classified as an alarm app is what **auto-grants
  `USE_FULL_SCREEN_INTENT`**, which the ringing screen relies on to surface over the
  lock screen (see the full-screen work in 0.4.4). Dropping it could silently break
  that. `SCHEDULE_EXACT_ALARM` is the one to drop: it's user-revocable,
  denied-by-default on 14, and `setAlarmClock` never needs it.
- **Fix (0.4.9):** Removed `SCHEDULE_EXACT_ALARM`; kept `USE_EXACT_ALARM` (justified —
  this genuinely is an alarm clock, so it satisfies the Play "Exact Alarm API" policy).
  No runtime code referenced `SCHEDULE_EXACT_ALARM` / `canScheduleExactAlarms`, so
  nothing else changes. (Fully exiting the exact-alarm Play policy would mean dropping
  `USE_EXACT_ALARM` too, which isn't worth risking the full-screen surfacing.)
- **How to verify:** Device check on API 31–34 that alarms still fire (they should —
  `setAlarmClock` is exempt) and that full-screen alarms still surface. Not
  unit-testable; the manifest change is validated by CI's `assembleDebug`.

### B8 — Awake-check start uses a `mediaPlayback` foreground service · Low · ✅ Re-assessed in 0.4.9 (no change — premise no longer holds)
- **Where:** `AlarmService.onStartCommand` reached via `ACTION_AWAKE_CHECK_SHOW`/`_TIMEOUT`.
- **Original concern:** every start foregrounds as `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`,
  and the awake-check popup "plays no audio", so the media type looked misleading and might
  trip future FGS enforcement.
- **Why it's no longer a real issue:** since the 0.4.4 gentle-nudge change, the awake-check
  path **does** play audio — a soft notification chime (`MediaPlayer`, `USAGE_NOTIFICATION`)
  plus vibration, repeating across the window. So `mediaPlayback` is now a defensible fit for
  **both** paths (looping alarm audio when ringing; the nudge chime during a check). There is
  no dedicated "alarm" FGS type; `mediaPlayback` (with the declared
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission) is the standard choice for alarm apps, and
  Android 14 doesn't require a `MediaSession` for it.
- **Decision:** keep `mediaPlayback`. Switching the awake-check start to another type
  (`systemExempted`/`specialUse`) would add a permission and a Play declaration for marginal
  semantic gain, and a wrong/mismatched type can make `startForeground` throw — which would
  crash the **ringing** service, the one thing that must never fail. Not worth it without a
  device to verify against. Revisit only if Play flags it or FGS enforcement tightens.
- **How to test:** Device/instrumented; not unit-testable.

### B9 — Scheduler DST / weekday-mask correctness is assumed, not proven · Medium · ✅ Verified in 0.4.7 (no defect found)
- **Where:** `AlarmScheduler.computeNextTriggerMs` (`AlarmScheduler.kt`).
- **What:** This is the single most safety-critical function (it decides when you
  wake up) and its trickiest cases were **unverified**: a spring-forward morning
  where the alarm's wall-clock time doesn't exist, a fall-back morning where it
  happens twice, and the exact-minute boundary (`candidate == now`). The existing
  `NextRingFormatterTest` deliberately uses July dates to *avoid* DST, so DST was a
  genuine hole.
- **Outcome (0.4.7):** Added `AlarmSchedulerNextTriggerTest` (coverage gap **C2**),
  which pins the current behaviour — and it is **correct**, so no code change was
  needed: an alarm armed the day before a DST transition still fires at the intended
  **wall-clock** time the morning after (verified in `America/New_York` across both
  the March spring-forward and November fall-back); `candidate == now` correctly rolls
  forward (a one-shot to tomorrow, a weekly to next week) rather than re-firing
  instantly; and the weekday mask wraps to the next enabled day. The one inherently
  ambiguous case — an alarm set *inside* the nonexistent spring-forward hour — is
  asserted only to return a real, future, same-morning time (never null/past), since
  resolving a wall-clock time that doesn't exist is lenient-`Calendar` defined.
- **Related:** a timezone/clock change *while an alarm is armed* is a separate gap —
  see **B11** (fixed 0.4.7).

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

### B11 — Armed alarms aren't re-armed on a timezone / manual clock change · Medium · Confirmed · ✅ Fixed in 0.4.7
- **Where:** `AndroidManifest.xml` (there was no time/timezone receiver); alarms were
  only ever re-armed by `BootReceiver` (`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` /
  `MY_PACKAGE_REPLACED`) and at save time.
- **What:** `AlarmScheduler` arms each alarm with `AlarmManager.setAlarmClock` at an
  **absolute instant** computed from the wall-clock time and timezone in effect *when it
  was armed*. If the user later changes timezone (travel) or sets the system clock, that
  pinned instant no longer matches the intended local time — the alarm fires at the old
  offset, which can be hours early or late. Nothing listened for
  `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` to recompute and re-arm, and a
  timezone change doesn't reboot, so the boot re-arm path never ran.
- **Why:** Re-arming was tied to boot / package-replace / explicit save only; there was
  no time- or timezone-change receiver.
- **Fix (0.4.7):** New `directBootAware` `TimeChangeReceiver` listens for
  `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` (both protected system broadcasts a
  manifest receiver may still receive) and re-arms every enabled alarm via the same
  `AlarmScheduler.rescheduleAll` path `BootReceiver` uses. The which-actions decision is a
  pure, unit-tested `TimeChangePolicy`. (A single armed alarm that merely *crosses* a DST
  boundary is already resolved correctly by `Calendar` — verified in **B9**; this covers
  the distinct timezone/manual-clock change. Awake-check windows are short-lived and left
  out of scope.)
- **How it was tested:** `TimeChangePolicyTest` (JVM) pins which actions re-arm (timezone
  + clock change) and which don't (null / boot / `TIME_TICK`). The re-arm fan-out reuses
  `rescheduleAll` (shared with the boot path); the receiver actually firing on a real
  timezone change is device-only (see the device list).

---

## Test-coverage audit

### What's covered today (all JVM/`src/test`, run in CI)
| Suite | Covers |
|---|---|
| `ScanValidatorTest` | Duplicate-frame / reused-barcode scan decisions |
| `AlarmAdmissionPolicyTest` | Single-session admission: ring / re-ring / defer / invalid (B2) |
| `AlarmSchedulerNextTriggerTest` | `computeNextTriggerMs` DST (spring-forward / fall-back), exact-minute boundary, weekday wrap (B9/C2) |
| `TimeChangePolicyTest` | Which broadcasts re-arm alarms on a timezone / clock change (B11) |
| `QrCodeGeneratorTest` | QR code id uniqueness + value dedupe (B5) |
| `EmergencyGamePolicyTest` | Emergency mini-game: complete-once, tap gate, idle timeout, lit-cell move (B6/C1) |
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
| **C2** ✅ | `computeNextTriggerMs` DST + exact-minute + weekday-mask edges | `AlarmSchedulerNextTriggerTest` — fixed `Calendar` + `America/New_York` spring-forward & fall-back, `candidate == now`, weekday wrap (done 0.4.7 with B9) | JVM | B9 done |
| **C1** ✅ | Emergency mini-game rules (complete-once, tap gate, idle timeout, lit-cell move) | `EmergencyGamePolicyTest` — done in 0.4.8 with B6 | JVM | B6 done |
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
- **Timezone / clock-change re-arm (v0.4.7, B11):** change the phone's timezone (or set the
  clock forward) while an alarm is armed and confirm `TimeChangeReceiver` re-arms it so it
  still fires at the intended local time. Reproduce: arm an alarm a couple hours out → change
  the system timezone → check the next-alarm time.
- **Exact-alarm permission (v0.4.9, B7):** confirm alarms still fire on API 31–34 after
  dropping `SCHEDULE_EXACT_ALARM`, and that full-screen alarms still auto-surface (i.e.
  `USE_EXACT_ALARM` alone keeps the app classified as an alarm app on 14). B8 (mediaPlayback
  FGS type) was re-assessed and left as-is — see above.

### Cheapest high-value next steps
1. ~~**C2** (scheduler DST)~~ — done in 0.4.7 (B9 verified correct; B11 receiver added).
2. **C5 + B1** (foreground-start decision) — B1 is already fixed via `ServiceStartPolicy`;
   the remaining value is extracting the keep-foreground-vs-stop decision as a pure test.
3. ~~**C1 + B6** (emergency policy extract)~~ — done in 0.4.8 (`EmergencyGamePolicy`).
4. Stand up Robolectric once, then land **C3** (Room migrations/atomicity, unblocks B4).
