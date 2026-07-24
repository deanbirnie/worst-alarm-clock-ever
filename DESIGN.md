# Design Specification

This documents the **exact current visual design** of Worst Alarm Clock Ever —
every color, type style, spacing value, icon, and screen layout as actually
implemented in code — so it can be reconstructed pixel-for-pixel in a design
tool. This is a description of what exists today, not a proposal.

The app is built with **Jetpack Compose + Material Design 3**. As of the
v0.4 redesign it customizes the **color scheme** (§2), a **softly rounded
shape set** (§4), and a handful of **type-scale weight overrides** (§3);
everything not listed in those sections is stock M3. Where this doc says
"Material 3 default," it means the component looks exactly like stock M3
with no overrides.

---

## 1. Concept in one line

A warm, calm, "sunrise" visual language wrapping a deliberately punishing
alarm mechanic — the UI should feel gentle and easy to read half-asleep, in
contrast to the alarm's uncompromising behavior.

---

## 2. Color palette

Two full color schemes, switching automatically with the system's light/dark
setting (`isSystemInDarkTheme()`). Both are warm/amber-toned — there is no
neutral gray/blue anywhere in the app.

### Dark scheme ("SunriseDark") — used when the system is in dark mode

| Role | Hex | Used for |
|---|---|---|
| `background` | `#1E1410` | Screen background — deep warm brown, never true black |
| `onBackground` | `#F0E0D0` | Text/icons on background |
| `surface` | `#271A14` | Card backgrounds, sheets |
| `onSurface` | `#F0E0D0` | Text/icons on surface |
| `surfaceVariant` | `#3B2B21` | Muted fills — e.g. unlit emergency-game tiles |
| `onSurfaceVariant` | `#D6BCA8` | Secondary/caption text |
| `primary` | `#F5A05C` | Primary buttons, key accents, links — "soft sunrise orange" |
| `onPrimary` | `#3B1C02` | Text/icons on primary-colored surfaces |
| `primaryContainer` | `#6B3A16` | Container variant of primary |
| `onPrimaryContainer` | `#FFDCC2` | Text on primaryContainer |
| `secondary` | `#E0B368` | Secondary accents — "early-morning gold" |
| `onSecondary` | `#3B2A00` | Text/icons on secondary |
| `secondaryContainer` | `#564314` | Container variant of secondary |
| `onSecondaryContainer` | `#FDDF9E` | Text on secondaryContainer |
| `tertiary` | `#D98A7B` | Tertiary accents — "dusky rose" |
| `onTertiary` | `#44110B` | Text/icons on tertiary |
| `outline` | `#9E8572` | Borders (outlined buttons/text fields) |
| `error` | `#FFB4AB` | Error text/icons |
| `onError` | `#690005` | Text/icons on error-colored surfaces |

### Light scheme ("SunriseLight") — used when the system is in light mode

| Role | Hex | Used for |
|---|---|---|
| `background` | `#FAF0E4` | Screen background — soft warm cream, not pure white |
| `onBackground` | `#241A12` | Text/icons on background |
| `surface` | `#F5E8D8` | Card backgrounds, sheets |
| `onSurface` | `#241A12` | Text/icons on surface |
| `surfaceVariant` | `#EEDCC6` | Muted fills |
| `onSurfaceVariant` | `#554434` | Secondary/caption text |
| `primary` | `#AB5327` | Primary buttons, key accents — "burnt sienna" |
| `onPrimary` | `#FFFFFF` | Text/icons on primary-colored surfaces |
| `primaryContainer` | `#FFDBC8` | Container variant of primary |
| `onPrimaryContainer` | `#380D00` | Text on primaryContainer |
| `secondary` | `#7A5C22` | Secondary accents — "ochre" |
| `onSecondary` | `#FFFFFF` | Text/icons on secondary |
| `secondaryContainer` | `#FCDFA6` | Container variant of secondary |
| `onSecondaryContainer` | `#271900` | Text on secondaryContainer |
| `tertiary` | `#8E4A40` | Tertiary accents — "clay rose" |
| `onTertiary` | `#FFFFFF` | Text/icons on tertiary |
| `outline` | `#877461` | Borders |
| `error` | `#BA1A1A` | Error text/icons (Material 3 stock error red) |
| `onError` | `#FFFFFF` | Text/icons on error-colored surfaces |

All other M3 roles not listed above (`inverseSurface`, `scrim`, elevation
tint, etc.) are Material 3's computed defaults derived from these seed
colors — not manually specified.

### One-off colors outside the theme

| Hex/value | Where | Purpose |
|---|---|---|
| `#55FF0000` (33% opaque red, `Color(0x55FF0000)`) | Scanning screen, full-bleed overlay | **"Wrong barcode" flash** — displayed for 600ms over the camera preview when a scanned code doesn't match. Keep this one exactly as-is; it's called out as a highlight of the current design. |
| `#FF5555` | Legacy app icon foreground (bell shape) | See §9, Icon |
| `#FFAAAA` | Legacy app icon foreground (bell handle) | See §9, Icon |
| `#101018` | Legacy launcher background color | See §9, Icon |
| Pure white (`Color.White`) | Text on the red wrong-barcode overlay; QR code image background | High-contrast text/QR backing regardless of theme |
| Pure black (module default) | QR code foreground modules | ZXing-rendered QR codes are always pure black-on-white, independent of app theme |

---

## 3. Typography

No custom font — Roboto (stock Android) throughout. As of the v0.4
redesign, a few M3 styles are overridden for more presence; everything
else is the stock M3 scale:

| Overridden style | Change from M3 default |
|---|---|
| `displaySmall` | weight → **SemiBold** (alarm-list clock time) |
| `headlineMedium` | weight → **SemiBold** |
| `titleMedium` | weight → **SemiBold**, letterSpacing +0.2sp |
| `titleSmall` | weight → **SemiBold** |

Styles actually in use:

| M3 style | Size / weight | Used for |
|---|---|---|
| `displaySmall` | 36sp / SemiBold | Alarm list row — the big HH:MM time |
| `headlineSmall` | 24sp / Regular | Ringing screen alarm label; Emergency screen title; Nav drawer title uses `titleMedium` not this |
| `titleLarge` | 22sp / Regular | About screen app name |
| `titleMedium` | 16sp / Medium | Section headers ("Routine locations (in order)"), nav-drawer header text |
| `titleSmall` | 14sp / Medium | "Step N" label in the alarm-step card |
| `bodyMedium` | 14sp / Regular | About screen's descriptive paragraph (default `Text` style) |
| `bodySmall` | 12sp / Regular | Nearly all secondary/caption text throughout — helper text, timestamps, format labels |
| `labelMedium` | 12sp / Medium | `NumberStepperField` label ("Min"/"Sec") |

### Ad-hoc (non-M3-style) text treatments

These bypass the type scale with explicit `fontSize`/`fontWeight` for
emphasis, all on the **ringing / emergency / scanning** screens where
maximum legibility at a glance matters most:

| Text | Size | Weight | Color |
|---|---|---|---|
| Location name on ringing screen (e.g. "Kitchen") | 44sp | ExtraBold | `onBackground` |
| "SCAN BARCODE" button label | 22sp | Bold | `onPrimary` (button's content color) |
| "Wrong barcode" overlay text | 28sp | Regular | White |
| Emergency tap counter ("123 / 500") | 36sp | Regular | `onBackground` |
| "SCAN:" / "Next up:" label | default body size | SemiBold | `primary` |
| Countdown text ("Next ring in 3:24") | default body size | Bold | `secondary` |

---

## 4. Shape language

As of the v0.4 redesign, a custom `Shapes` set softens every corner —
"warm and pillowy rather than boxy":

| M3 shape slot | Radius (was M3 default) |
|---|---|
| `extraSmall` | 8dp (was 4dp) — text fields' top corners |
| `small` | 12dp (was 8dp) — chips |
| `medium` | **18dp** (was 12dp) — all `Card`s |
| `large` | 24dp (was 16dp) — FAB |
| `extraLarge` | 32dp (was 28dp) — dialogs, drawer sheet |

Component notes:

| Component | Effective corner radius |
|---|---|
| `Card` | 18dp (medium) all corners |
| `Button` / `OutlinedButton` / `TextButton` | Fully rounded ("stadium" / pill shape — unchanged, not shape-slot driven) |
| `FloatingActionButton` | 24dp (large) |
| `OutlinedTextField` | 8dp top corners (extraSmall) |
| `AlertDialog` | 32dp (extraLarge) |
| `AssistChip` | 12dp (small) |
| `NavigationDrawerItem` (selected pill) | Fully rounded |
| Day-of-week selector bubbles | `CircleShape` (perfect circles) |
| Emergency-game grid tiles | 12dp explicit `RoundedCornerShape` |

---

## 5. Spacing system

No formal spacing scale is declared, but usage is consistent throughout —
effectively an 4dp-based system:

- **Screen padding:** 16dp on all sides, applied after the `Scaffold`'s own
  content padding.
- **Card internal padding:** 12dp or 16dp (16dp for list-row cards, 12dp for
  denser stacked cards like alarm-step cards).
- **Vertical rhythm between stacked elements:** 4dp (tight), 8dp (standard),
  12dp (section groups), 16dp (major groups), 24dp (top-level column
  spacing on the ringing screen; end-of-screen breathing room).
- **Row item gaps:** 8dp between side-by-side buttons/chips; 12dp between
  larger side-by-side elements (e.g. QR thumbnail and its text column).

---

## 6. Iconography

All icons are stock **Material Symbols/Icons** (`Icons.Default.*` or
`Icons.AutoMirrored.Filled.*`), filled style, no custom icon assets except
the app launcher icon. Full inventory, by where each appears:

| Icon | Where |
|---|---|
| `Menu` | Home screen top bar, opens the navigation drawer |
| `QrCode` | Home screen top bar (shortcut to Barcode library) + drawer item |
| `QrCode2` | Drawer item — QR generator |
| `Settings` | Drawer item — Settings |
| `Info` | Drawer item — About |
| `Add` | FAB on Home (add alarm) and Barcode library (add barcode); "Add location" button in alarm editor |
| `AutoMirrored.Filled.ArrowBack` | Top-bar back button on every secondary screen (Alarm editor, Barcode library, Settings, About, QR generator) — RTL-aware chevron |
| `ArrowDropDown` | Trailing icon on the step "Location" field, opens the known-locations dropdown |
| `Delete` | Remove-step icon button (alarm editor); delete-barcode icon button (barcode library) |
| `QrCodeScanner` | "Scan to fill value" button in the barcode add/edit dialog |
| `Share` | Share button on each generated QR code card |
| `Remove` | Left/decrement button of `NumberStepperField` |
| (reuse of `Add`) | Right/increment button of `NumberStepperField` |

---

## 7. Navigation structure

```
Alarm List (home / start destination)
 ├── [+ FAB]                → Alarm Editor (new alarm, id=0)
 ├── [tap alarm row Edit]   → Alarm Editor (existing alarm)
 ├── [top-bar QR icon]      → Barcode Library
 └── [hamburger → drawer]
      ├── Barcode library    (same destination as the top-bar shortcut)
      ├── QR generator
      ├── Settings
      └── About
```

All secondary screens (Alarm Editor, Barcode Library, Settings, About, QR
Generator) are pushed via Jetpack Navigation Compose and back out via a
top-bar back arrow / `onDone`/`onBack` callback — standard single-`NavHost`
push/pop, no tabs, no bottom navigation.

**Separate from normal navigation:** when an alarm fires, a full-screen,
lock-screen-level activity (`AlarmActivity`) takes over the entire display,
outside the app's regular Compose navigation graph. It hosts three "panels"
swapped by local state, not the nav graph: **Ringing → Scanning → Emergency**
(see §11).

---

## 8. Screen-by-screen specification

### 8.1 Alarm List (Home)

- `TopAppBar`: title **"Worst Alarm Clock Ever"**; navigation icon = hamburger
  (`Menu`, opens drawer); one action icon on the right = `QrCode` (jumps
  straight to Barcode Library).
- `FloatingActionButton` bottom-right: `Add` icon, standard M3 FAB
  (primaryContainer-colored, 16dp corners).
- Body, top to bottom, 16dp screen padding:
  1. **Full-screen-alarm banner** (conditional — Android 14+ only, shown if
     `NotificationManager.canUseFullScreenIntent()` is false): a `Card`, bold
     title "Allow full-screen alarms", a `bodySmall` explanation, an 8dp
     spacer, and an `OutlinedButton` "Open settings" that deep-links to the
     per-app full-screen-intent setting. Shown first because without it the
     alarm can ring with no visible screen.
  2. **Overlay-permission banner** (conditional — only shown if "Display
     over other apps" isn't granted): a `Card` containing bold title
     "Grant 'Display over other apps'", a `bodySmall` explanation line, an
     8dp spacer, and an `OutlinedButton` "Open settings".
  3. **Empty state** (if no alarms): centered in the remaining space — a
     ☀️ glyph at 44sp, 12dp gap, "No alarms yet" in `titleMedium`, 4dp gap,
     "Tap + to build your first wake-up routine." in `onSurfaceVariant`.
  3. **Alarm list** (if alarms exist): a `LazyColumn`, 12dp vertical gaps
     between cards, 80dp bottom content padding (clears the FAB). Each row
     is a single **whole-card-tappable** `Card` (tap opens the editor — no
     separate "Edit" button) whose container is `surfaceVariant` when the
     alarm is enabled and plain `surface` when disabled. Inside, one `Row`
     (20dp horizontal / 16dp vertical padding), vertically centered:
     - Left column (`weight(1f)`): the time in `displaySmall` ("07:00") —
       the anchor of the card; optional label line in
       `titleSmall`/`primary`; 4dp gap; one quiet summary line in
       `bodySmall`/`onSurfaceVariant` combining the day summary and the
       location count with a middle dot: "Weekdays · 3 locations". Day
       summaries collapse named patterns to words — "Every day",
       "Weekdays", "Weekends", "One-time" — and otherwise list days
       Sunday-first: "Sun · Wed · Fri" (`DaySummaryFormatter`).
     - Right side: a `Switch` toggling enabled/disabled.
     - **Disabled dimming:** all text in a disabled alarm's card renders at
       45% alpha (the `Switch` stays full-strength).
- **Navigation drawer** (`ModalNavigationDrawer` + `ModalDrawerSheet`,
  slides from the left): header text "Worst Alarm Clock Ever" in
  `titleMedium` (16dp padding all around), then four `NavigationDrawerItem`
  rows, each with an icon + label, none marked selected:
  **Barcode library** (`QrCode`), **QR generator** (`QrCode2`),
  **Settings** (`Settings`), **About** (`Info`).
- **First-launch intro dialog** (`AlertDialog`, shown once — see §8.7 for
  persistence logic): title **"Welcome to the worst alarm clock ever"**;
  body is three paragraphs explaining the no-snooze mechanic and the
  multi-step "path" concept, then a `Checkbox` + label row reading
  **"Do not show this again"** (checked by default); confirm button
  **"Got it"**.

### 8.2 Alarm Editor

- `TopAppBar`: title "New alarm" or "Edit alarm"; back arrow nav icon;
  right-side action = `TextButton` "Save" (disabled until at least one
  step has a barcode chosen). Tapping Save (with the alarm enabled) shows
  a long `Toast` with the countdown to the next ring — "Rings in 7 h
  32 min" — then navigates back; saving a disabled alarm shows no note.
- Scrollable `Column`, 16dp screen padding, 12dp vertical gaps between
  top-level items:
  1. `OutlinedTextField` — "Label (optional)".
  2. `OutlinedButton`, full width — "Time: 07:00" (opens the platform's
     native `TimePickerDialog` on tap).
  3. "Repeat" label (`titleSmall`), then the **day-of-week selector**
     (`DayOfWeekSelector`): a full-width `Row` of 7 circular single-letter
     bubbles running **Sunday-first** — S M T W T F S — with 6dp gaps.
     Each bubble takes an equal `weight(1f)` of the row at 1:1 aspect
     ratio, so the selector always fits the exact screen width with
     perfectly round bubbles. Selected bubble: `primary` fill, `onPrimary`
     Bold letter. Unselected: `surfaceVariant` fill, 1dp 35%-alpha
     `outline` ring, `onSurfaceVariant` Medium letter. Bubbles carry the
     full day name as their accessibility `contentDescription`. (Display
     order is Sunday-first, but storage stays ISO bit 0 = Monday … bit 6 =
     Sunday — `WeekdayOrder` maps between the two and is unit-tested so
     the redesign can't shift existing alarms' firing days.) If no days
     are selected, a `bodySmall` helper line appears: "One-time: will fire
     at the next occurrence of this time, then disable itself."
  4. A `Card` (12dp inner padding) containing the **alarm-tone picker
     row** (§8.8): title "Alarm sound (this alarm)", current-value caption
     ("Use the global sound from Settings" or the picked file's display
     name), then a `Row` with an `OutlinedButton` "Choose audio file" and,
     if a custom file is set, a `TextButton` "Reset".
  5. A `Card` (12dp inner padding) containing the **awake-check toggle
     row**: a `Row`, center-aligned, with a `Column` (`weight(1f)`) holding
     title "Awake check" (`titleSmall`) and a `bodySmall` explanatory line
     ("After the final scan, two popups (5-15 min apart) must be dismissed
     before the alarm is fully off. Each nudges you gently (a soft chime and
     buzz, not the alarm) until you tap it. Miss one and the alarm rings
     again."), plus a trailing `Switch` — on by default for new alarms.
  6. Section header "Routine locations (in order)" (`titleMedium`) plus a
     `bodySmall` explanatory line underneath.
  7. One **Step Card** per routine step (§8.2.1), in order.
  8. `OutlinedButton`, full width, icon+label "+ Add location" — disabled
     (grey) if the barcode library is empty, in which case a red
     (`error`-colored) `bodySmall` line appears below it: "Add barcodes to
     your library first (QR icon on the home screen)."
  9. 24dp bottom spacer.
- **"Adding a second location" warning dialog** (`AlertDialog`, triggered
  only when going from 1 → 2 steps): explains the phone will stay locked
  to the alarm until the final barcode is scanned, and that scanning ahead
  or the Emergency stop are the ways out; confirm button "Add location",
  dismiss button "Cancel".

#### 8.2.1 Step Card

A `Card` (12dp padding, 8dp internal vertical gaps) per routine step:
- Header row: "Step N" (`titleSmall`) + spacer + `IconButton` (`Delete`).
- A `Row` with an `AssistChip` reading "Choose barcode" or "Barcode:
  <name>" — tapping opens a `DropdownMenu` listing every saved barcode
  (name, or raw value if unnamed, plus its location in parentheses if set).
- `OutlinedTextField` "Location (optional)", full width, with a trailing
  `ArrowDropDown` icon button (only shown if any barcode has a location
  set) opening a `DropdownMenu` of distinct known locations.
- **If not the last step:** a `Row` with two `NumberStepperField`s side by
  side (§8.9) labeled "Min" (0–999) and "Sec" (0–59), then a `bodySmall`
  caption "…then rings the next location."
- **If the last step:** a single `bodySmall` caption instead: "Final step
  — scanning this disarms the alarm."

### 8.3 Barcode Library

- `TopAppBar`: title "Barcode library"; back arrow.
- `FloatingActionButton`: `Add`, opens the barcode editor dialog blank.
- Body, 16dp padding:
  - Empty state: `onSurfaceVariant` text, "No saved barcodes. Tap + to add
    one — scan a real barcode or type a value manually."
  - Otherwise a `LazyColumn` (8dp gaps) of **barcode rows**: each a `Card`
    with a 16dp-padded `Row` — left column shows the name (`titleMedium`,
    "(unnamed)" if blank), the raw value (`bodySmall`/`onSurfaceVariant`),
    and a combined "Format: <format> · <location>" line (`bodySmall`,
    location segment omitted if not set); right side has a `TextButton`
    "Edit" and an `IconButton` (`Delete`).
- **Barcode add/edit dialog** (`AlertDialog`): title "New barcode" or "Edit
  barcode"; body column with `OutlinedTextField`s for Name, Value, and
  Location (each full width, 8dp gaps), a `bodySmall` "Format: <name>"
  line, then a full-width `OutlinedButton` "Scan to fill value"
  (`QrCodeScanner` icon) that reveals a 240dp-tall live camera preview
  (`BarcodeScanner`) inline when tapped. Confirm button "Save" (disabled
  until name + value are non-blank); dismiss button "Cancel".

### 8.4 QR Generator

- `TopAppBar`: title "QR generator"; back arrow.
- Body, 16dp padding:
  - Intro `bodySmall` paragraph (`onSurfaceVariant`) explaining the
    feature (print codes, or use one as a PC wallpaper as a login gate).
  - `Row` with two buttons: `Button` "Generate code" and `OutlinedButton`
    "Generate 3".
  - `LazyColumn` (12dp gaps) of **generated-code cards**, newest appended
    to the end.

#### Generated-code card

`Card`, 12dp padding, 8dp vertical gaps:
- `Row`: a 96dp square QR image (white background regardless of theme,
  black modules), 12dp gap, then a column with the code's value
  (`Bold`) and a status caption ("Saved to library" / "Not in library
  yet", `bodySmall`/`onSurfaceVariant`); trailing `IconButton` (`Share`).
- If not yet saved: `OutlinedTextField`s for Name and Location (optional),
  then a full-width `Button` "Add to barcode library" (disabled until Name
  is non-blank). Once added, these fields disappear and the status caption
  flips to "Saved to library".

Generated code values follow the pattern **`WACE-XXXXXXXXXX`** (10 random
uppercase A–Z/0–9 characters).

### 8.5 Settings

- `TopAppBar`: title "Settings"; back arrow.
- Scrollable body, 16dp padding, 16dp gaps between two `Card`s:
  1. **Alarm sound card**: the tone-picker row (§8.8) titled "Alarm
     sound" / default label "System default alarm sound", plus a
     `bodySmall` caption: "Used for every alarm unless an alarm sets its
     own sound."
  2. **Welcome message card**: bold title "Welcome message", `bodySmall`
     explanation, and an `OutlinedButton` "Show again" that re-arms the
     first-launch intro dialog (shows a confirmation `Toast`).

### 8.6 About

- `TopAppBar`: title "About"; back arrow.
- Scrollable body, 16dp padding, 16dp gaps between three `Card`s:
  1. **App identity card**: "Worst Alarm Clock Ever" (`titleLarge`, Bold),
     "Version <versionName>" (`bodySmall`/`onSurfaceVariant`, pulled from
     `BuildConfig.VERSION_NAME`), then a one-paragraph `bodyMedium`
     description.
  2. **Developer card**: bold "Developer" label, developer name, email
     (`bodySmall`/`onSurfaceVariant`), then a `Row` of two
     `OutlinedButton`s: "Contact" (opens an email composer via
     `ACTION_SENDTO`) and "Source code" (opens the GitHub repo in a
     browser).
  3. **Suggestion box card**: bold "Suggestion box" label, `bodySmall`
     explanation, a multi-line `OutlinedTextField` ("Your idea", min 3
     lines), and a `Button` "Send suggestion" (disabled until non-blank;
     opens an email composer pre-filled with the text).

### 8.7 First-launch intro dialog

Covered in §8.1 — appears over the Alarm List the first time the app
launches. Persistence: a `DataStore` boolean flag; checking "Do not show
this again" (checked by default) before dismissing persists it as seen.
Unchecking and dismissing hides it for the current app session only, and
it reappears on the next cold launch. Also re-triggerable from Settings.

### 8.8 Shared component — Alarm tone picker row

Used identically in both the Alarm Editor (per-alarm) and Settings
(global). Structure: title text (`Bold`), a caption line below showing
either the fallback label or the resolved display name of the picked audio
file (queried live via `ContentResolver`, falls back to "Custom sound (file
may have moved)" if unreadable), an 8dp spacer, then a `Row` with an
`OutlinedButton` "Choose audio file" (launches the system audio-file
picker) and, only when a custom file is set, a `TextButton` "Reset".

### 8.9 Shared component — Number stepper field

Used for the Min/Sec routine-delay inputs. A labeled column: small
`labelMedium`/`onSurfaceVariant` caption above a `Row` of
`FilledTonalIconButton` (`Remove`) — `OutlinedTextField` — `FilledTonalIconButton`
(`Add`). The text field is digit-only (max 3 characters), clamped to a
`min`/`max` range (Minutes 0–999, Seconds 0–59); tapping in selects the
entire current value so typing replaces it outright; the +/- buttons grey
out at their respective bound.

---

## 9. The alarm-ringing experience (full-screen, over the lock screen)

This is a separate visual mode from the rest of the app — no app bar, no
back button, no system chrome interaction. Background is always
`MaterialTheme.colorScheme.background` (so still the warm cream/brown, not
black). Content respects system-bar insets (`systemBarsPadding`/
`navigationBarsPadding`) so nothing sits under the gesture-navigation bar.

### 9.1 Ringing panel (default view)

Full-height `Column`, `SpaceBetween` arrangement, 24dp padding, everything
centered horizontally:
- **Top group:** "Step N of M" (70%-opacity `onBackground`), 6dp gap,
  alarm label or "Wake up" fallback (`headlineSmall`).
- **Middle group:** "SCAN:" (while ringing) or "Next up:" (during a
  between-step pause) in `SemiBold`/`primary`; 8dp gap; the location or
  barcode name at **44sp ExtraBold**; 8dp gap; "(barcode: <name>)" at
  70%-opacity `onBackground`; if in a pause, a 16dp gap then a countdown
  "Next ring in M:SS" in `Bold`/`secondary`.
- **Bottom group** (full width, 12dp gaps): a giant primary `Button`,
  72dp tall, full-width, containing "SCAN BARCODE" at 22sp Bold — this is
  the dominant, unmissable action. **While a step is only counting down (not
  ringing yet), this button is disabled** and reads "LOCKED — WAIT FOR THE
  RING" at 16sp: a location's barcode only counts once its step rings, so the
  path can't be scanned ahead. Below it, a 56dp-tall full-width `OutlinedButton`:
  "EMERGENCY STOP (500 taps)" (always enabled).

### 9.2 Scanning panel

Full-height `Column`, no padding at the root:
- Top ~fills-remaining-space `Box`: live camera preview
  (`BarcodeScanner`). On a wrong scan, a full-bleed **33%-opacity red
  overlay** (`Color(0x55FF0000)`) appears for 600ms with centered white
  28sp text "Wrong barcode" — **this exact treatment is a highlight of the
  design and should be preserved as-is.**
- Bottom `Column` (16dp padding, respects nav-bar inset): "Looking for:
  <location>", "Barcode: <name>" (70%-opacity `onBackground`), 8dp gap,
  full-width `OutlinedButton` "Back to ringing screen".
- If camera permission isn't yet granted, this panel is replaced by a
  centered column: "Camera permission required to scan.", a "Grant"
  `OutlinedButton`, and a "Back" `OutlinedButton`.

### 9.3 Emergency panel (the 500-tap mini-game)

Full-height `Column`, `systemBarsPadding`, 16dp padding, 12dp gaps:
- Title "Emergency disarm" (`headlineSmall`/`primary`).
- Explanatory `bodySmall` line (80%-opacity `onBackground`).
- **Conditional warning line** — only one of these shows, and only after
  at least one idle reset: if the free-reset budget (3) is exhausted, an
  `error`-colored line: "You've idled out N times — the alarm keeps
  ringing until you finish."; otherwise a `tertiary`-colored line: "Idle
  resets: N of 3. After 3, the alarm keeps ringing during the game."
- Tap counter "N / 500" at 36sp.
- Full-width `LinearProgressIndicator` tracking taps/500.
- **The grid**, centered in the remaining space: a 4×4 grid of square
  tiles (6dp gaps, 12dp rounded corners), exactly one tile lit
  (`primary`-colored) at a time, the rest `surfaceVariant`-colored. Tapping
  the lit tile increments the counter, relocates the lit tile to a
  different random cell, and resets the 30-second idle timer.
- Full-width, 48dp-tall `OutlinedButton`: "Back to alarm (resume
  ringing)" — a no-penalty voluntary exit.

Idle for 30 seconds with no taps → the game silently resets (counter to 0,
grid dismissed, ringing resumes) — this counts toward the 3-strike limit
above.

### 9.4 Awake-check popup (a separate, lighter-weight full-screen mode)

Shown by its own activity (`AwakeCheckActivity`), not `AlarmActivity` — it
appears twice, at random points after the routine's final scan, to confirm
the user hasn't drifted back to sleep. Visually it's the same warm
background/typography language as the ringing screens, but it is **not** a
lockdown: no back-button interception, no re-assert overlay.

While the popup is up, the service emits a **gentle cue** — a soft
notification-level chime plus a light double-tap buzz — repeating every
`AwakeCheckPolicy.NUDGE_INTERVAL_MS` (30s) across the `POPUP_TIMEOUT_MS`
(~3 min) ack window, so the user notices it without having to watch the
screen. It is deliberately *not* alarm-grade (low volume, short,
non-looping, capped — vibration is the reliable channel, the chime a bonus a
silent profile may mute); the whole point is to acknowledge, not to re-wake.
The cue stops the instant "I'm awake" is tapped or the miss-timeout re-rings.

Full-height `Column`, `SpaceBetween` arrangement, `systemBarsPadding`, 24dp
padding, everything centered horizontally:
- **Top:** "Awake check N of 2" (70%-opacity `onBackground`).
- **Middle group:** "Still awake?" at **40sp ExtraBold**; 12dp gap;
  center-aligned body copy at 80%-opacity `onBackground`: "Confirm you're
  up. If this isn't dismissed in time, the alarm rings again."
- **Bottom:** a giant primary `Button`, 72dp tall, full-width: "I'M AWAKE"
  at 22sp Bold — the only control on the screen, and the only action that
  counts as a response.

---

## 10. States and system feedback not tied to a specific screen

- **Toasts** (transient system snackbar-style text) are used for: barcode
  deletion blocked because it's in use by an alarm; idle-timeout
  notification on exiting the emergency game; confirmation that the
  welcome dialog will show again; "No email app found" / "No browser
  found" fallback errors; "Couldn't share the QR code."
- **Loading placeholder:** if the ringing session's state is momentarily
  null (session ending), a centered `CircularProgressIndicator` shows
  alone on the themed background.

---

## 11. App icon

The current launcher icon (`ic_launcher_foreground.xml`, adaptive icon
foreground layer, 108×108dp viewport) is a **simple bell-alarm glyph**:
a bell body drawn in `#FF5555` (a bright coral-red — notably NOT part of
the sunrise theme palette above; this is legacy and a candidate for
restyling to match) with a small lighter-red (`#FFAAAA`) handle/clapper
accent at the top. The adaptive-icon background color is `#101018` (near-
black navy), also inconsistent with the current warm theme. **If
recreating the brand identity in Claude Design, treat the icon as
placeholder/unstyled** — it predates the sunrise palette and is flagged in
this project's TODO as needing a proper redesign to match.

---

## 12. Summary reference table (quick lookup)

| Token | Dark | Light |
|---|---|---|
| Background | `#1E1410` | `#FAF0E4` |
| Surface | `#271A14` | `#F5E8D8` |
| Primary | `#F5A05C` | `#AB5327` |
| Secondary | `#E0B368` | `#7A5C22` |
| Tertiary | `#D98A7B` | `#8E4A40` |
| Text (on background) | `#F0E0D0` | `#241A12` |
| Caption text (on surface variant) | `#D6BCA8` | `#554434` |
| Error | `#FFB4AB` | `#BA1A1A` |

Special: wrong-barcode flash = `#FF0000` at 33% opacity, always, both
themes.
