# DECISIONS.md — decisions made autonomously

Every choice below was made without asking, because the spec was ambiguous or the environment
required it. Each one says **what** was chosen and **why**.

## Environment & project layout

| # | Decision | Why |
|---|----------|-----|
| 1 | The project lives at the root of the Git repository `HussamEl/S20Ultra` (branch `claude/nasta-stopp-android-app-soeru7`). `~/NastaStopp` is a **symlink** to it, so every path in the spec (`~/NastaStopp/dist/NastaStopp.apk`, …) works. | The work ran in an ephemeral cloud container: only what is committed and pushed survives. A separate `~/NastaStopp` repo would have been lost. |
| 2 | OS detected: **Linux x86_64** (cloud container). Installed Android cmdline-tools 23.0, platform-tools 37.0.1, `platforms;android-37.0`, `build-tools;37.0.0`, with licences accepted. SDK in `/root/android-sdk`. The container already had JDK 21 (≥ 17). | Required by the spec. |
| 3 | `adb devices` showed **no device**: the phone is not connected to a cloud container. The APK was **not** installed; it is ready in `dist/`. | Spec: "If not, just finish with the APK ready." |
| 4 | No emulator: the container has no `/dev/kvm`. Runtime behaviour was instead verified with **Robolectric** tests (Android 13 / SDK 33, like the S20 Ultra). They cover the real `RouteController`, TTS phrases, Maps intents and batching, arrival/departure, expiry and the Compose screens. Two Robolectric limitations are worked around in the tests only: it ignores locale changes made with `createConfigurationContext` (so the UI test uses the `ar` resource qualifier), and it never idles with a TextField when a screen-size qualifier is forced. | This is the closest thing to a device available here. |

## Versions (verified against Google Maven / Maven Central on 2026-09-27)

| Component | Version | Note |
|-----------|---------|------|
| Gradle (wrapper) | **9.8.0** | latest stable (services.gradle.org), wrapper SHA-256 pinned |
| Android Gradle Plugin | **9.4.1** | latest stable (9.5.0 is alpha) |
| Kotlin | **2.4.20** | latest stable (2.5.0 is Beta). AGP 9 "built-in Kotlin" is used, so there is no `kotlin-android` plugin. |
| compileSdk / targetSdk | **37** (Android 17, `android-37.0`) | latest stable API level |
| minSdk | 29 | spec |
| Compose BOM | **2026.09.00** (Material3 1.4.0) | latest stable |
| ML Kit text-recognition (bundled) | **16.0.1** | latest stable |
| play-services-location | **21.4.0** | latest stable |
| core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.0 | latest stable | |
| kotlinx-coroutines 1.11.0, kotlinx-serialization-json 1.11.0 | latest stable (1.12.0 is RC) | |
| Robolectric 4.17, androidx.test core 1.7.0 / ext-junit 1.3.0 | test only | |

No fallback to an older version was needed: every latest stable version built.

## Architecture

- A single `app` module. The pure-Kotlin logic lives in `se.eldebosh.nastastopp.core.*` (parser, tiling, geo selection, Maps URLs, arrival state machine, phrases) with no Android imports, and is fully unit-tested.
- No DI framework: a manual `AppGraph` is created in `App.onCreate`. `RouteController` is the single source of truth (a `StateFlow`) for both the draft and the active route.
- Settings use `SharedPreferences` (no DataStore) to keep dependencies small. Route persistence uses `kotlinx.serialization` JSON in **`noBackupFilesDir`**, with an atomic write through a temp file.
- Icons are hand-written vector drawables (Material icon paths), so there is no `material-icons-extended` dependency.
- The floating button is a plain Android `View` in `WindowManager` (`TYPE_APPLICATION_OVERLAY`), because a Compose view inside an overlay would need a hand-made lifecycle owner.

## Parser (§5) interpretations

1. **Truncation**: everything after the first `…` / `...` is dropped. The word right before the mark is also dropped, since it is probably cut, **unless** it is a known locality or a complete 5-digit postal code. This makes `…HAMMARÖ…` keep "Hammarö" while `…GRU…` drops "Gru", exactly as the tests require.
2. **Rule (a)** needs at least 2 letters before the postal code, so a lone "65224 Karlstad" line is not a stop by itself (it is joined to the preceding street line instead). A postal code alone, with its town truncated, is still recorded when rule (b) matches ("Lindvägen 9, 664 30").
3. **OCR fixes** in postal positions need at least 3 real digits among the 5 characters, and the result must lie in 100 00–989 99. This prevents words like "SOS 12" becoming postal codes.
4. **Phone numbers and times are removed** from a line instead of rejecting the whole line. Lines that are only a phone number or time end up with no letters and are rejected, as the spec requires, while "Storgatan 14, 65224 Karlstad 070-123 45 67" keeps its address.
5. **Money** ("KR/kr/Kr/SEK") rejects a line unless rule (a) matches, the same treatment as UI words.
6. **UI words**: the spec's list plus Swedish equivalents (avgift, ersättning, utförd, avgått). "Hämtning/Lämning" were deliberately **not** added, so Swedish address labels are not lost.
7. **Short codes** such as "SP1, HLI": an all-caps line of at most 5 tokens, each `[A-ZÅÄÖ0-9]{1,4}`.
8. **Joining lines**: a line is joined with the next when the next starts with a postal code, **or consists only of** a known locality. "Bara hämtning" is therefore not glued on, even though "Bara" is a town. A joined result must contain a house number, so "Kalle Karlsson" + "Karlstad" is never treated as an address.
9. **Street suffixes**: the spec list, plus `allé`, `alle`, `allen` and `vagen` for OCR that drops diacritics. A *standalone* suffix word ("gata", "plan", "väg") counts only after another word, so "Plan 2" is not a street but "Karl Johans gata 5" is. In Title Case a standalone suffix after a word stays lower case ("Karl Johans gata 5"), following Swedish convention.
10. **c/o** removes the name after it until a comma, a number, a street token or 3 words, so a street written right after the name survives. `vån N`, `N tr`, `port X`, `portkod N`, `uppg X` and `, plan N` (floor) are removed.
11. **Consecutive duplicates** are compared by the street "core" (from the street token on) plus a compatible postal code and town. "ANDERSSON STORGATAN 14, …" and "STORGATAN 14, …" therefore merge, and the more complete entry is kept. The same rule merges duplicates across two screenshots and against the last stop already in the list.
12. `ExtractedStop` has one extra field, `parsedTownKnown`: whether the town is in the bundled list. Only a known town may be spoken (see Privacy).
13. **Locality list** (`assets/localities_se.txt`, 677 names): all 290 municipalities (a unit test checks the count), about 110 Värmland tätorter, and about 270 common postal towns and tätorter elsewhere. Matching ignores case and diacritics.

## Geocoding (§6)

- **Candidate order** follows the spec. A result matching the postal code (or town) is used immediately. Otherwise the first non-empty answer is remembered and the remaining candidates are still tried for a better match (at most 6 candidates).
- After a successful geocode, the display text becomes the candidate that **starts at the street the geocoder found** (`thoroughfare`). This drops a leading surname ("Andersson Storgatan 14" becomes "Storgatan 14, 652 24 Karlstad") without the parser guessing. The stored candidate list is then reduced to that single string.
- A 15 s timeout per lookup; an error or timeout means "not located".
- **Retry**: the long-press menu on a not-located stop has "Locate again", and editing a stop always re-geocodes it.

## Spoken names / privacy (§2, §6)

- Order: `subLocality` (if different from `locality`) → `locality` → parsed town (**only if it is in the locality list**) → "nästa adress".
- Extra guard `isSafeAreaName`: a spoken name never contains digits and never contains the stop's street name. District names ending in a street suffix ("…gatan", "…vägen", "…liden") are rejected and fall back to the town, because a false fallback is safer than speaking a street. A unit test checks that all 677 bundled localities pass the guard.
- The persistent notification shows the address, but its **lock-screen (public) version shows only the area name**.
- ML Kit's internal telemetry component (datatransport) is still in the merged manifest. With INTERNET removed it cannot send anything, and it never receives recognised text.
- **12-hour expiry is measured from the route's creation** (strict reading). It is enforced when data is loaded, on every app resume, and by an inexact `AlarmManager` alarm, which needs no exact-alarm permission. A route still active after 12 h is ended.

## Arrival / departure (§9)

- The **arrival-radius setting (25–150 m)** replaces the 75 m. So that thresholds never contradict each other, arming distance = max(150, radius + 75) and departure distance = max(100, radius + 25). With the default 75 m these are exactly the spec's 150 m and 100 m.
- **"No two automatic advances within 30 s"**: a departure that happens earlier is *deferred* until 30 s have passed (on the next fix), not dropped.
- **Speed**: `Location.speed` when available, otherwise derived from the previous fix.
- **Two consecutive stops at the same place** means both located and ≤ 30 m apart, or the same address text. Automatic detection is off for **both** stops of such a pair (the UI shows "manual advance"). Otherwise a departure would announce a stop the car is already at.
- After process death the detector restarts un-armed. The vehicle must be > 150 m away once before arrival can trigger again, which is the safe choice.

## Maps (§8)

- `%20` is used for spaces and `%7C` for the waypoint separator. A literal `|` inside an address is replaced by a space.
- **Automatic relaunch from the background**: Android blocks activity starts from the background unless the app has a visible overlay window. When the app is not in the foreground, a tap-to-open "Öppna Maps för nästa etapp" notification is posted as well, so the next leg is never lost.
- After editing an active route (the "Edit list" button), Maps is relaunched if the first 10 stops changed, and the announcement is repeated if the next stop changed.

## Foreground service (§9)

- The service is started only when location permission is granted. Without it (Android 14+ forbids a location-type FGS then), the route still works manually through a normal ongoing notification with the same actions.
- Notification actions go to a non-exported `BroadcastReceiver`, so they work whether or not the service runs.
- If the system restarts the service while the app is in the background and `startForeground` is refused, the service stops quietly. Tracking resumes the next time the app is opened (`onResume` calls `ensureServiceRunning`).

## TTS (§10)

- If the default engine (often Samsung's) lacks Swedish but **Google's TTS engine is installed, the app switches to Google's engine automatically**.
- English repeat uses `en-US`, only if that voice is available.
- An announcement requested while the TTS engine is still starting is spoken once it is ready, but only if it is less than 20 s old (never a stale announcement).
- "Upprepa" rebuilds the announcement from the current state, so it respects the current settings.
- "Avsluta" stops speech and says nothing. Finishing the last stop says "Rutten är klar.".

## UI (§13)

- **Arabic by default regardless of phone language**. On Android 13+ this uses the per-app language API (`LocaleManager`, with `locales_config.xml`, so the system settings list the app too). Contexts are also wrapped on Android 10–12, and as a no-op safety net on 13+. The language can be changed in Settings (العربية / Svenska / English).
- Driving buttons show Arabic plus the Swedish word, e.g. "التالي · Nästa", since the spec names the Swedish words. The notification actions are exactly "Nästa / Upprepa / Avsluta".
- Addresses are displayed with `TextDirection.Content`, so Swedish text stays left-to-right inside the RTL layout.
- Swipe-to-delete and "Delete all above" show an **Undo** snackbar, to guard against accidental swipes while driving.
- The home screen has a "Clear" button (with confirmation) that deletes everything.
- Photo picker limit: 30 images per pick.

## Build & delivery (§15)

- **Release signing**: the keystore is `/root/.nastastopp-signing/nastastopp-release.jks` (PKCS12, RSA 4096, alias `nastastopp`). It lives outside the repository and is referenced by the git-ignored `keystore.properties`. **If `keystore.properties` is missing** (a clean checkout), the release build signs with the debug key, so `assembleRelease` always produces an installable APK.
- The release build uses R8 (minify plus resource shrinking) and packages only the `arm64-v8a` and `armeabi-v7a` ABIs (the OCR native library is about 11 MB per ABI). The debug build keeps all ABIs for emulators.
- `dist/NastaStopp.apk` is the **signed release APK** and is committed. `*.apk` is git-ignored except that file.
- The Gradle deprecation warning ("Configuration.setVisible") comes from a plugin, not from this project's scripts.
