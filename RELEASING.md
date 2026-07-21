# Releasing & Long-Term Maintenance

How to go from "debug APK on my own phone" to "signed app on the Google Play
Store", and how to keep the project healthy over the years in between.

---

## 1. Release signing

> **Not to be confused with the debug keystore.** As of 0.4.2 the repo commits
> `app/debug.keystore` and signs *debug* builds with it, purely so sideloaded
> debug APKs install as updates over one another (same signature every build).
> That debug key uses Android's public default credentials, is **not** secret,
> and must **never** be used for a Play release. The release keystore below is a
> different, genuinely secret key — never commit it.

Debug builds are signed with the committed debug key (above). Anything you
distribute publicly — and *everything* you upload to Google Play — must be
signed with a release key you own and keep forever. **Losing the keystore means
losing the ability to update the app**, so treat it like a password vault item.
(Google **Play App Signing**, step 3 below, is the modern safety net for exactly
this — Google holds the app signing key and you sign uploads with an upload key.)

### Create the keystore (once)

```sh
keytool -genkeypair -v \
  -keystore worst-alarm-release.jks \
  -alias worst-alarm \
  -keyalg RSA -keysize 4096 -validity 10000
```

Store `worst-alarm-release.jks` and both passwords in a password manager and
an offline backup. Never commit it to git (`*.jks` should stay untracked).

### Wire it into the build

Create `keystore.properties` in the repo root (git-ignored):

```properties
storeFile=/absolute/path/to/worst-alarm-release.jks
storePassword=…
keyAlias=worst-alarm
keyPassword=…
```

Add to `app/build.gradle.kts`:

```kotlin
import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true   // enable R8 for smaller release builds
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

Then:

```sh
./gradlew assembleRelease    # signed APK  → app/build/outputs/apk/release/
./gradlew bundleRelease      # signed AAB  → app/build/outputs/bundle/release/  (Play Store wants this)
```

> Before shipping a minified release, test it on a device — R8 stripping can
> break reflection-heavy code. If something crashes only in release, check
> `app/proguard-rules.pro` keep rules first.

---

## 2. Versioning

`app/build.gradle.kts` → `defaultConfig`:

- `versionCode` — integer, **must increase** for every Play upload. Simple
  scheme: just increment (1, 2, 3, …).
- `versionName` — human-readable, e.g. `0.1.0` → `0.2.0` (features) →
  `0.2.1` (fixes). Follow [semver](https://semver.org) loosely.

Tag releases in git so any installed build can be traced to a commit:

```sh
git tag -a v0.2.0 -m "Alarm paths + overlay hardening"
git push origin v0.2.0
```

---

## 3. Publishing on Google Play

### One-time setup

1. Create a [Play Console](https://play.google.com/console) developer
   account ($25 one-time fee, needs identity verification).
2. Create the app in the console: name, default language, "App" (not game),
   free or paid. **The free/paid choice is permanent per app listing** — a
   free app can never become paid (paid → free is allowed). If you plan to
   sell it, either create it as paid from the start or plan to monetize a
   free listing with in-app purchases later.
3. Accept **Play App Signing** (default): you upload an AAB signed with your
   key; Google holds the actual distribution key. This is the recommended,
   effectively mandatory path for new apps.

### Store listing requirements (prepare in advance)

- App name (30 chars), short description (80), full description (4000).
- App icon 512×512 PNG, feature graphic 1024×500.
- At least 2 phone screenshots (up to 8; 16:9 or 9:16).
- Privacy policy URL — required because the app declares the CAMERA
  permission. This app processes everything on-device and sends nothing
  anywhere; a one-page static site saying exactly that is sufficient.
- Content rating questionnaire, target audience declaration ("not designed
  for children" keeps you out of Families policy requirements), data safety
  form (declare: no data collected, no data shared).

### Policy items specific to this app

- **`SYSTEM_ALERT_WINDOW` (overlay)** — allowed, but reviewers may ask why.
  The listing description should state plainly: "uses an overlay so the
  alarm cannot be dismissed without scanning your chosen barcode."
- **`USE_FULL_SCREEN_INTENT`** — permitted for alarm apps specifically;
  say "alarm clock" in the listing.
- **Camera** — the data safety form must say images are processed on-device
  and never stored or transmitted (ML Kit bundled model runs offline).
- **Target API level** — Google requires new apps/updates to target an API
  level within one year of the latest Android release (see maintenance
  below).

### Rollout

1. `./gradlew bundleRelease` → upload the `.aab` to **Internal testing**
   first, install it on your own phone via the opt-in link, live with it for
   a week of real mornings.
2. Promote to **Closed testing**. Note: personal (individual) developer
   accounts created after Nov 2023 must run a closed test with **at least 12
   testers for 14 days** before production access is granted.
3. Promote to **Production**, start at 20% staged rollout, watch crash
   stats (Play Console → Quality → Android vitals), then 100%.

### Selling it

- **Paid app:** set the price in Monetization setup. Requires a payments
  profile; Google takes 15% (up to $1M/yr).
- **Freemium (usually better for alarms):** free app + one-off in-app
  purchase ("unlock alarm paths / multiple alarms") via Play Billing. More
  work (billing library integration) but far better conversion than a paid
  wall — people want to try an alarm app before paying.

---

## 4. Long-term maintenance

### The yearly must-do

Google enforces target-SDK freshness: roughly every year you must bump
`targetSdk` (and usually `compileSdk`), read the
[behavior changes](https://developer.android.com/about/versions) page, fix
what applies, and ship an update — otherwise the app is eventually hidden
from new users. Foreground-service, alarm, and overlay rules are exactly the
areas Google keeps tightening, and they're this app's core. Budget a day or
two per year, expect the changes to touch `AlarmService` / `AlarmScheduler` /
the manifest.

### Dependency updates (quarterly-ish)

All versions live in `build.gradle.kts` (root: plugin versions; app: library
versions). The risky ones to update are the Compose BOM, AGP+Gradle (update
together — check the [compatibility matrix](https://developer.android.com/build/releases/gradle-plugin#updating-gradle)),
and Kotlin+KSP (versions must match, e.g. `1.9.24` ↔ `1.9.24-1.0.20`).
Update one axis at a time; CI (`.github/workflows/build.yml`) catches compile
breaks on every push. Consider enabling
[Renovate](https://docs.renovatebot.com/) or Dependabot so update PRs arrive
automatically.

### Database migrations

The Room schema is at version 1 with `exportSchema = false`. **Before the
first public release**, flip on schema export and version control the JSON
(`app/schemas/`), because after real users install the app, every schema
change needs a `Migration(from, to)` — destructive migrations wipe users'
alarms and earn 1-star reviews. While it's just your phone, wiping app data
on schema change is fine.

### Testing (currently absent — see TODO.md)

Highest-value additions, in order:
1. Unit tests for `AlarmScheduler.computeNextTriggerMs` (weekday masks, DST
   boundaries, "today or tomorrow" edge at the exact minute).
2. Unit tests for the `AlarmService` state machine transitions.
3. One Compose UI smoke test (create alarm → appears in list).

### Device-specific pain

Alarm apps live and die by OEM quirks. Keep [dontkillmyapp.com](https://dontkillmyapp.com)
bookmarked; when a user reports "alarm didn't fire" it's almost always a
vendor battery killer, and the fix is an in-app help page telling that
vendor's users which settings to flip.

### Release checklist (copy per release)

- [ ] `versionCode` +1, `versionName` bumped
- [ ] CHANGELOG entry / Play "What's new" text written
- [ ] `./gradlew bundleRelease` builds clean
- [ ] Release build installed and one real alarm fired end-to-end (ring →
      scan → path step → disarm), plus one reboot-survival check
- [ ] Git tag pushed
- [ ] Uploaded to internal track before production
