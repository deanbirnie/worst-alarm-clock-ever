# Building the APK

Three ways to get an installable APK, from easiest to most hands-on. If you
just want the app on your phone, use **Option A** — you never need to install
anything on your computer.

---

## Option A — Download the APK from GitHub Actions (no tools needed)

Every push to this repository triggers the [Build APK workflow](.github/workflows/build.yml),
which compiles the app on GitHub's servers and stores the result.

1. Open the repository on GitHub and click the **Actions** tab.
2. Click the most recent **Build APK** run (green check = success).
3. Scroll to the **Artifacts** section at the bottom of the run page.
4. Download **WorstAlarmEver-\<version\>-debug** and unzip it →
   `WorstAlarmEver-<version>-debug.apk` (e.g. `WorstAlarmEver-0.2.4-debug.apk`).
5. Install it on your phone (see [Installing on your phone](#installing-on-your-phone)).

Notes:
- GitHub always wraps artifact downloads in a zip — that part isn't
  configurable — but the `.apk` inside is named after the app and its
  version, not the generic `app-debug.apk`.
- Artifacts expire after 90 days; just re-run the workflow (Actions → Build
  APK → "Run workflow") to produce a fresh one.
- This is the *debug* build: fully functional, signed with a debug key, ideal
  for personal use. For a Play-Store-ready release build see
  [RELEASING.md](RELEASING.md).
- **Updates install over each other** because every build (local and CI) is
  signed with the repo's committed debug keystore (`app/debug.keystore`), so
  the signature is identical each time. That keystore uses Android's well-known
  default debug credentials — it is intentionally *not* secret and cannot sign
  a Play release. Proper release signing (Play App Signing) is a V1 task; see
  [RELEASING.md](RELEASING.md).

---

## Option B — Android Studio (recommended for development)

### One-time setup

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or
   newer). It bundles the JDK and Android SDK — no separate installs needed.
2. `File → Open…` → select this repository's root folder.
3. Wait for the Gradle sync to finish (first sync downloads dependencies;
   accept any SDK license prompts it shows).

### Build

- **Menu:** `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.
- When the "APK(s) generated" balloon appears, click *locate* — the file is at
  `app/build/outputs/apk/debug/WorstAlarmEver-<version>-debug.apk`.

### Run directly on your phone

1. On the phone: enable **Developer options** (Settings → About phone → tap
   *Build number* 7 times), then enable **USB debugging** in Developer options.
2. Plug the phone in via USB and accept the "Allow USB debugging?" prompt.
3. Pick your device in Android Studio's device dropdown and press **Run ▶**.
   The app installs and launches.

---

## Option C — Command line only

### Requirements

- **JDK 17** (`java -version` should say 17.x; Temurin builds work well).
- **Android SDK** with platform 34 + build-tools. Either point
  `ANDROID_HOME` at an existing SDK, or install the bare minimum:

  ```sh
  # download "command line tools" from https://developer.android.com/studio#command-line-tools-only
  mkdir -p ~/android-sdk/cmdline-tools
  unzip commandlinetools-*.zip -d ~/android-sdk/cmdline-tools
  mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
  export ANDROID_HOME=~/android-sdk
  yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
  $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
  ```

- No Gradle install needed — the checked-in wrapper (`./gradlew`) downloads
  the right Gradle version (8.7) automatically on first run.

### Build

```sh
./gradlew testDebugUnitTest    # run the unit tests (CI runs these on every PR)
./gradlew assembleDebug        # → app/build/outputs/apk/debug/WorstAlarmEver-<version>-debug.apk
./gradlew assembleRelease      # unsigned release APK (needs signing, see RELEASING.md)
./gradlew installDebug         # build + install straight onto a USB-connected phone
```

Tell Gradle where the SDK is with either the `ANDROID_HOME` environment
variable or a `local.properties` file in the repo root containing
`sdk.dir=/absolute/path/to/android-sdk`.

---

## Installing on your phone

Any of these once you have the `.apk` (e.g. `WorstAlarmEver-0.2.4-debug.apk`):

- **Direct download (simplest).** Put the APK somewhere the phone can reach
  (email it to yourself, Google Drive, or download the CI artifact directly
  on the phone's browser). Tap the file → Android asks to allow installs
  from that app (browser/files) → allow → Install.
- **USB + adb.**
  ```sh
  adb install app/build/outputs/apk/debug/WorstAlarmEver-<version>-debug.apk
  # after code changes, upgrade in place:
  adb install -r app/build/outputs/apk/debug/WorstAlarmEver-<version>-debug.apk
  ```

"Blocked by Play Protect" prompt? Choose *Install anyway* — it appears for
any APK not distributed through the Play Store.

### First-run checklist

On first launch, grant what the app asks for — each one has a job:

1. **Camera** — scanning barcodes. Grant this *before* your first alarm
   fires; you don't want to meet a permission dialog at 6 AM.
2. **Notifications** (Android 13+) — the alarm's foreground service and its
   full-screen wake-up.
3. **Display over other apps** — tap the banner on the home screen; this
   powers the overlay that stops you from just using the phone mid-alarm.
4. Recommended on aggressive OEMs (Xiaomi/Oppo/etc.): Settings → Battery →
   exempt *Worst Alarm* from optimization so nothing delays the alarm.

Then: add a barcode or two to the library, create an alarm ~2 minutes in the
future, lock the phone, and see for yourself.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=…` |
| `Unsupported class file major version` / JDK errors | You're on the wrong JDK. Use 17: `export JAVA_HOME=…` |
| Gradle sync fails on license | `yes \| sdkmanager --licenses` |
| First build is very slow | Normal — Gradle downloads ~1 GB of dependencies once, then caches |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / "App not installed" on update | Should no longer happen: all builds now sign with the committed debug keystore (same signature every time), so updates install over each other. **One-time exception:** the first build after this change (0.4.2+) won't install over an *older* build that was signed with a throwaway key — uninstall once, then future updates install cleanly. |
| Alarm doesn't ring on time on a Xiaomi/Oppo | Exempt the app from battery optimization; enable "autostart" if the OEM has it |
