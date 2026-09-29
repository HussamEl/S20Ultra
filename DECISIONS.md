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
- The release build packages only the `arm64-v8a` and `armeabi-v7a` ABIs (the OCR native library is about 11 MB per ABI). The debug build keeps all ABIs for emulators.
- **R8 is disabled for release (since 1.0.1).** See "Fix in 1.0.1" below. The APK is larger (about 46 MB instead of 23 MB) but runs exactly the code the debug build and tests run. `proguard-rules.pro` now carries the ML Kit keep rules, in case R8 is re-enabled later.
- `dist/NastaStopp.apk` is the **signed release APK** and is committed. `*.apk` is git-ignored except that file.
- The Gradle deprecation warning ("Configuration.setVisible") comes from a plugin, not from this project's scripts.

## Fix in 1.0.1: "could not read the image" on the phone

- **Symptom:** on the S20 Ultra, every imported screenshot failed with "could not read the image".
- **Cause:** in 1.0.0 the release APK was minified by R8 in **full mode** (the AGP 9 default). R8 removed the no-argument constructors of ML Kit's component registrars (`TextRegistrar`, `CommonComponentRegistrar`, `VisionCommonRegistrar`) and renamed `getComponents`. ML Kit creates these by reflection from manifest meta-data, so the text recognizer could not be created. The failure was invisible to every test: unit and Robolectric tests run un-minified code and cannot load ML Kit's native OCR. It was confirmed by inspecting the 1.0.0 release dex with `dexdump`.
- **Fix:** R8 is off for release; the 1.0.1 dex was checked to contain the constructors again. In addition:
  - the OCR falls back to ML Kit's own image loader (`InputImage.fromFilePath`) if the tiled decode fails;
  - any failure in one image (including errors from the native library) only skips that image;
  - a malformed OCR line can never fail an image (a per-line guard, plus a 40,000-line fuzz test of the parser);
  - a dialog shows **which step failed** (OPEN / DECODE / ENGINE / OCR / PARSE) with the technical reason, so a remaining problem can be reported from a screenshot. The reason never contains OCR text or addresses.
- Regex flags were made portable (`RegexOption.IGNORE_CASE` instead of inline `(?iu)`), and all 29 app regexes were checked to compile and match correctly with the ICU 74 engine (Android's regex engine). This was verified, not guessed: ICU also accepted the old patterns, so they were not the cause.
- Version bumped to **1.0.1 (versionCode 2)** and signed with the same key, so it installs over 1.0.0.

## Version 1.1.0: trip times, completed trips, passenger display, floating button

| # | Decision | Why |
|---|----------|-----|
| 1 | **Trip times are kept** with each stop ("12:48"). The original spec kept only addresses; the user explicitly asked for the base time, and a time is not personal data. It is deleted together with the route. | User request. |
| 2 | **Finding the time**: a time on the address line wins. Otherwise the time on the nearest line **above** or **below** the address, between it and the neighbouring addresses. The direction (above/below) is chosen **once per screenshot**, by majority, so one trip never takes the next trip's time. The first OCR line, if it is only a time (the status bar clock), is ignored. "12.50 km" / "12.50 kr" are not times. | Dispatch apps put the time either above or below each address. |
| 3 | **Completed trips stay** in the route (`RouteData.completed`) until the route ends. They are shown at the top of the route screen: small, 45 % opacity, one line (time · area · address). The list scrolls so that the latest completed trip stays visible above the current one. | User request. |
| 4 | **Sort by time** is an explicit button (⏱) in the review screen, not automatic: screenshot order is kept unless the driver asks. Trips without a time go last. | The driver's own order may be deliberate. |
| 5 | **Phone ↔ tablet link = Bluetooth RFCOMM (secure, paired devices only)**, not Wi-Fi or a server. The app must keep having **no INTERNET permission**, and local network sockets would need it. Bluetooth needs only `BLUETOOTH_CONNECT` (Android 12+) and no scanning, because the display picks the driver's device from the already-paired list. No extra library (Nearby Connections would add a Play Services dependency). | Privacy requirement. |
| 6 | **Roles**: every device can be *Full control* or *Passenger display*, chosen on the first screen (onboarding) or from Home / the display screen. A display device opens straight into the passenger display. | User request: "the user just picks it in the UI". |
| 7 | **What is sent to the display**: only the `DisplaySnapshot` (1 previous, the current and 3 upcoming trips: time + area name, plus the full address **only if** "Show full address on the passenger display" is on; the announcement text). **The default is area only**, the same privacy rule as the spoken announcements, because the tablet faces the passengers. Nothing is stored on the display device. | Passenger privacy. |
| 8 | **Speaker button** on the display speaks the current announcement **on that device**. Automatic announcements on the tablet are **off by default** (a switch on the display screen), so the car does not hear everything twice. | Avoids double audio. |
| 9 | The link reconnects automatically every 3 s. The controller pings every 10 s and the display drops a link that is silent for 30 s. The protocol is one JSON message per line (`hello`, `state`, `announce`, `ping`), unknown messages are ignored and lines over 64 KB close the link. | Robust in a car; covered by unit tests over pipes. |
| 10 | The controller only listens when the driver turns the link on. It runs in the app process, which stays alive during a route because of the foreground service. | No Bluetooth activity unless wanted. |
| 11 | **Floating button**: a small × hides it (stored as a setting). It is shown again from the 👁 button on the route screen or in Settings. Under the button, a translucent box shows the **current time** (updated each minute) and the **next trip's time and address**; tapping the box opens the app. | User request. |
| 12 | The passenger display uses auto-sized text (`TextAutoSize`, up to 280 sp, 2 lines), hides the system bars and keeps the screen on. | "Full-screen font". |
| 13 | Version **1.1.0 (versionCode 3)**, the same signing key, so it installs over 1.0.x. | |

Not testable here: the Bluetooth radio itself (no devices in the build container). The protocol, the display state, the role switching and the screens are covered by unit and Robolectric tests. The first real run between the phone and a tablet should be checked by hand.

## Fix in 1.1.1: phone stays on "Waiting for the display…"

- **Report:** the devices were paired, but the driver's phone (now a Galaxy S25 Ultra) kept showing "waiting".
- **Likely causes, all addressed:**
  1. The display only connected after a device was chosen manually. **The display now searches automatically**: the chosen or last-used device first, then paired phones, then tablets/computers, then uncategorised devices. Headsets, car kits and watches are skipped unless chosen. The device that answers is remembered.
  2. Roles could be confused (the link switched on on both devices). The phone's link card now says plainly what to do on the tablet and shows the phone's Bluetooth name.
  3. Only the secure RFCOMM channel was used. The server now also listens on a **fallback channel without link-key authentication** (a second UUID; still encrypted on Bluetooth 2.1+). The client tries secure, then fallback. On both channels **only devices paired with the phone are served**; others are disconnected at once.
  4. There was no diagnostics. The display shows the device it is trying and **the last connection error**.
- Saving the answering device as "preferred" never restarts a working link (`start` vs `choose`).
- New tests: device ordering (pure), automatic connection to the paired phone and not to a headset (Robolectric Bluetooth shadows), and the controller listening state. The real radio still needs checking on the two devices.
- Version **1.1.1 (versionCode 4)**.

## Version 1.2.0: trip history on Home, floating button can be shown again

| # | Decision | Why |
|---|----------|-----|
| 1 | **Trip history kept after "Avsluta"** (Home → "Previous trips"). This deliberately changes the original "route data cleared when a route ends" rule, because the user explicitly asked for it. Only time, area name, address and completion time are stored, in a separate `history.json` in app-private no-backup storage. The route itself (candidates, geo data) is still deleted when the route ends. | Explicit user request. |
| 2 | **Retention**: each history trip is deleted automatically **12 h** after it was finished by default (the spec's 12 h rule), or after **24 h / 7 days** if chosen in Settings. It is pruned on load, on app resume and by an inexact alarm, and cleared with "Clear history" (with confirmation). | Keeps the health-data privacy rule while giving the driver control. |
| 3 | Trips still open when "Avsluta" is pressed are recorded as **"not completed"**. Drivers often end the route instead of pressing "Nästa" at the last stop. The route-list "Clear" button (delete everything) does not record history. | |
| 4 | **Floating button "disappeared"**: the × (new in 1.1.0) sits where the button is grabbed, and a drag that started on it could hide the button; the hidden state was persistent. Now the × only reacts to a real tap (dragging from it moves the button), hiding shows a message saying where to bring it back, and **Home has a "Floating button over Maps" switch**. It also shows when the overlay permission is missing, with the Samsung "Allow restricted settings" hint for side-loaded apps. | User report. |
| 5 | Version **1.2.0 (versionCode 5)**. | |

## Version 1.3.0: new floating panel (Back / current street / Next) and driver helpers

| # | Decision | Why |
|---|----------|-----|
| 1 | **Floating panel layout**: `[Back] (current street) [Next]` in one row. Its direction follows the UI language: in Arabic (RTL) Back is on the **right** and Next on the **left**, as the user wrote ("رجوع و التالي من اليمين واليسار"). In Swedish/English it is mirrored. The arrow icons are auto-mirrored, so both point outwards. | User request. Natural reading order in each language. |
| 2 | **The big circle shows the street the vehicle is on now** (reverse `Geocoder.getFromLocation`, the same system service already used for stops; still **no INTERNET permission**). Lookups use the route's location fixes: the first good fix, then only after moving ≥ 35 m **and** ≥ 8 s since the last lookup, and a failed lookup is retried after 30 s. They run only while something shows the street (the expanded panel or the route screen), not while the panel is minimised or closed. | Street changes about every few hundred metres; this keeps Geocoder calls low. |
| 3 | **Speaking the street name** (small speaker under the circle, or tap the circle) is a deliberate, driver-requested exception to "spoken announcements never contain street names". It is **only on the driver's tap**, never automatic, and it is **not sent to passenger displays**. Automatic stop announcements are unchanged (area/town only). The current street is kept **in memory only**: never stored, logged or shown on the passenger display, and forgotten when the route ends. It is the vehicle's own position, not a passenger's address. | Explicit user request. The privacy intent of the original rule still holds for passengers. |
| 4 | **Back (undo "Nästa")**: the last completed trip becomes the current one again, its history entry is removed and the announcement is repeated. Google Maps is re-launched only if the batch it has starts at the current trip (after an automatic batch switch or "Open Maps"). This is tracked with a new `batchStartStopId` (older saved routes simply do not re-launch). Also on the route screen, next to "Next". The notification keeps exactly Nästa / Upprepa / Avsluta, as in the spec. | User request. It also fixes the most common real-world error: an accidental tap, or an automatic advance too early. |
| 5 | **Minimise**: "–" shrinks the panel to a 68 dp bubble showing the next trip's time and trip number. The ring colour shows the time status. Tap the bubble to expand. The state is remembered. | User request ("minimise, then expand if needed"). |
| 6 | **Close and quick restore**: "×" closes the panel, and three one-tap ways bring it back: (a) a **silent notification "Floating button closed — tap to bring it back"**, shown only while it is closed during a route; (b) a **Quick Settings tile "Floating button"** (on Android 13+ the Home screen can add it with one tap via `requestAddTileService`); (c) the Home switch and the 👁 button on the route screen. Showing it again always shows the full panel. | User request ("bring it back easily and quickly"). The notification is reachable in one swipe. The tile does not use any extra permission. |
| 7 | **Extra practical helpers (my own proposals)**: <br>• **Time status** of the next trip vs. now: "in 7 min" (green), "now/soon" within 5 min (yellow), "4 min late" (red), in the panel, the bubble ring and the route screen. Midnight wrap: a trip counts as late for at most 8 h, otherwise it is the next occurrence. <br>• **Waiting timer** "At the stop for 03:12" from the automatically detected arrival until departure. Useful for the waiting time rules of paratransit. <br>• **Trip number** (2/8) and **straight-line distance** to the next stop. <br>• A **repeat** button in the info box, and long-press on Next repeats too. <br>• A **"You are on …" bar** with a speaker on the route screen. | Things a driver needs at a glance without opening the app. Rejected ideas: calling or texting the passenger (the app stores no phone numbers, by design), a traffic ETA (needs internet), automatically hiding the panel while driving (the user was confused before when the button disappeared). |
| 8 | New numbers are shown with plain digits (e.g. "بعد 7 د") so they match the trip times from the screenshots. | Consistent reading next to "12:48". |
| 9 | Version **1.3.0 (versionCode 6)**, the same signing key. | |

Checked by rendering the panel on Robolectric (native graphics, Arabic): long street names fit (auto-size, up to 3 lines). New tests cover undo, the Maps re-launch rule, the time status and midnight wrap, street lookup throttling and choice, the waiting timer, and the panel: Next/Back, minimise/expand, close, notification restore. The route screen is covered too. The Quick Settings tile and the real Geocoder answers still need checking on the phone.

## Version 1.4.0: English UI, reference numbers, street address on the display, YouDrive alerts

| # | Decision | Why |
|---|----------|-----|
| 1 | **UI language English by default**, migrated once on upgrade (a settings schema version). The old per-app system language (Arabic, set by earlier versions through `LocaleManager`) is replaced instead of adopted on that first start. Later language choices are kept. | Driver's request: "the floating button and the app in English". |
| 2 | **Explanations stay Arabic** while setting up: a setting "Explanations in Arabic" (default on). Hints, help, onboarding, privacy and similar texts are read from Arabic resources (`LocalExplainResources` / `explain()` / `Hint`) and laid out by content direction (right-to-left inside the English layout). Labels, buttons, titles, statuses, dialogs and notifications follow the UI language. The TTS announcements stay Swedish. | "Leave the explanations in Arabic until set-up is finished." Switching it off later needs no new version. |
| 3 | **Reference numbers**: a small translucent number (9 sp, white 80 % on black 35 %) in the top-start corner of every button, switch and piece of information, in Compose (a `Modifier.Node` drawing after the content) and on the floating panel (a `ViewOverlay` drawable; texts get 17 dp start padding so the number does not cover them). Each screen has a number range (1–17 floating panel, 20–39 Home, 40–60 review, 61–84 route, 86–95 passenger display, 100–125 settings, 140–152 YouDrive, 160–199 help/onboarding/tablet). Repeated elements, such as list rows, share one number. The full list is in the README. A setting (105) turns the numbers off. | The driver can name any element by number instead of describing it. |
| 4 | **Passenger display shows the street address with the house number** as the big title ("Storgatan 14", the part before the first comma) with the area under it. This is on by default and migrated on for existing installs. The setting (114) can switch back to area only. The spoken announcements are unchanged (area/town only). | Driver's decision: passengers are driven to their door, so the address is not a secret in the car. |
| 5 | **YouDrive alerts inside the app** (the driver chose this over a browser add-on or a desktop Chrome extension). Chrome on Android has no extensions, so the app opens https://youdrive.regionvarmland.se in a WebView. The driver logs in once with BankID or password; `bankid:`/`intent:` links open the BankID app, and intent links are sanitised (no explicit component, BROWSABLE). While "watching" is on, a foreground service (type `specialUse`) keeps the page alive; without it, the page is closed when its screen closes. Every 60 s (15 s while shown) the app reads `document.body.innerText` and reloads the page every 5 min. There is no JavaScript bridge, and file/content access is off. | The only way to get alerts on the phone while Maps is in front. |
| 6 | **Change detection** (`TripWatch`, pure and unit-tested): the page text is parsed with the same extractor as screenshots, so only times and addresses are kept. A leading surname is dropped for alerts: the shortest candidate whose street contains a "…gatan / …vägen"-style word is used. The first reading is the baseline. A change must be seen in **two readings in a row**. Empty readings are ignored, and a login page is shown as "Logged out". A trip that disappears **more than 5 min after its time counts as done, not cancelled**. Trips are compared as a multiset of time + normalised address. | Avoids false alarms from half-loaded pages, from finished trips leaving the list, and from session timeouts. |
| 7 | **Alerts**: a high-importance channel "Trip changes" with sound and a vibration pattern. There is one notification per change (up to 5), then a summary. The lock screen shows only "new trip / trip cancelled". Tapping opens the YouDrive screen, where each change has **Add to list** (inserted in time order, the current trip of an active route stays first) or **Remove from list**, plus Dismiss. Changes are only applied when the driver chooses; route edits go through the existing re-announce / Maps re-launch logic. **Add all trips** builds the list from YouDrive without screenshots. | Driver keeps control; no silent changes to an active route. |
| 8 | **INTERNET permission added** (the driver's explicit decision; the original spec had none). `ACCESS_NETWORK_STATE` stays removed. To keep "no analytics", ML Kit's Google datatransport upload backend is removed from the merged manifest (`backend:…CctBackendFactory` meta-data, `tools:node="remove"`). Without a backend, events are dropped with a log warning; this was checked in the transport-runtime 2.2.6 bytecode, and on-device text recognition is unaffected. The YouDrive login (cookies, page storage) stays in app-private storage without backup, and "Log out" (152) deletes it. | Needed for YouDrive; everything else stays local. |
| 9 | Version **1.4.0 (versionCode 7)**, same signing key. | |

Not verifiable here: the logged-in YouDrive page (private to the driver's account), BankID login inside the WebView, and whether YouDrive allows a second session next to Chrome. The parser is the one proven on the driver's YouDrive screenshots. If trips are missed, a screenshot of the page is enough to adjust it.

## Fix in 1.4.1: YouDrive login page hidden / cut off

- **Report:** the YouDrive area showed an empty "Settings" page and not the whole login form, while the status said "Logged out".
- **Cause (from YouDrive's public bundle):** the login form is only shown when the page path is not `/settings`. The page had navigated to its Settings screen, which stays empty while logged out, and our Back key left the whole screen instead of going back inside the page. The page cannot scroll (`body { overflow: hidden }`), so in the half-height area under our controls the login form was cut off.
- **Fix:**
  - The phone's Back key goes back inside the page first, using `canGoBack`, which is updated from `doUpdateVisitedHistory` because YouDrive is a single-page app.
  - A **start page** button (153) loads the YouDrive root.
  - The page is shown **full screen** while no trips have been found (logging in), with a one-line status; the controls come back when trips are found, and button **154** switches either way.
  - The monitoring hint is hidden once watching is on.
- Version **1.4.1 (versionCode 8)**.

## Fix in 1.4.2: YouDrive page closer to Chrome, real reset, page problems shown

- **Report:** YouDrive still showed its frame ("Värmlandstrafik", menu with Settings / Font / About / Dark) but no login form.
- **Checked in YouDrive's public bundle:** that menu (with "Settings") appears only when not logged in; the password form is a slide inside this frame. The certificate chain is complete, and `cordova.js` is only the SPA fallback, the same as in Chrome. The live page could not be rendered here: the build container's browser does not trust the network proxy, and TLS checks were not disabled.
- **Changes:**
  - WebView behaves more like Chrome: the page's own viewport (`useWideViewPort`, `loadWithOverviewMode`), text at 100 % instead of the system font scale (Samsung's large text made the page huge), and a `WebChromeClient` so page dialogs work. Dialogs are dismissed while the page is in the background, and geolocation is always refused.
  - **Log out (152) really resets**: it clears the page's session and local storage via script, then cookies, web storage, cache and history, and reloads.
  - "Logged out" is shown only when a **visible password field** exists, not just from words in the text.
  - **Page problems** are shown in the status line (143): main-frame or API load errors, HTTP ≥ 400 from the YouDrive hosts, certificate errors (still cancelled) and script errors. The page's console is never written to the system log. This gives the exact cause if the form still does not appear.
- Version **1.4.2 (versionCode 9)**.

## Fix in 1.4.3: hidden YouDrive login form; the page gets the whole screen

- **Report (with a Chrome screenshot for comparison):** in the app the YouDrive frame showed but not the "Credentials" form that Chrome shows, and the screen layout was cramped.
- **Findings:**
  - The line-143 error `appTag.js: Unexpected token '<'` is harmless: YouDrive requests its app-only files (`appTag.js`, `cordova.js`) in Chrome too and gets the page HTML back.
  - A local copy of the public page renders the login form in a phone-sized browser, with or without the API.
  - In the app the form's text was present in the page (the earlier "Logged out" came from it) but not visible, which points to the form's slide-in container being left off screen in the WebView.
- **Fix:**
  - Each reading (first ~1 s after a page load or opening the screen, then every 15 s) checks the password field. If it is off screen or hidden, the transform, visibility and opacity of its containers are reset. Only then; normal pages are not touched.
  - The repair is reported in line 143, so the cause is visible.
  - Script errors from `appTag.js` / `cordova.js` are ignored.
- **Layout:** the YouDrive screen is now one slim bar: back, status (143), watch bell (142), start page (153), reload (141) and a ⋮ menu (155) with Add all trips (148), Check now (149) and Log out (152). The page gets the rest of the screen, and trip changes appear above the page only when there are any. The full-screen toggle (154) and the on-screen hint (150) are gone; the help text explains the screen.
- **Credentials:** the driver offered their YouDrive username and password. They were not used or stored: the build environment cannot reach the logged-in site, and a work login should stay with its owner. The driver was advised to change the password, because it appeared in a screenshot in the chat.
- Version **1.4.3 (versionCode 10)**.

## Fix in 1.4.4: YouDrive in its own full-screen window

- **Report:** with 1.4.3 the login form still did not show, and the keyboard opened with nothing visible. "Log out" seemed to do nothing.
- **Reading:** the keyboard opening meant the page had focused its username field, and the status "Logged out" meant a visible password field was on screen. The form was laid out but **not drawn**. That is a display problem of the embedded WebView (inside the Compose screen, first laid out off screen), not of the site. Logging out could not change what was shown.
- **Fix:**
  - YouDrive now opens in its **own Activity**, like a browser tab: a Compose bar on top and a **plain WebView in the window** below it, at the phone's real size. It keeps clear of the status bar, navigation bar and keyboard (insets; the keyboard pushes the page up).
  - The page is loaded only once it is on screen. A page loaded off screen for background watching is loaded again when shown.
  - **Log out** clears the page's storage, cookies and cache, **destroys the WebView** and shows a brand-new page.
  - Back goes back inside the page, then closes the window.
  - Trip-change notifications open this window directly. "Add all trips" opens the list review for a new list.
- Version **1.4.4 (versionCode 11)**.

## Version 1.4.5: no location permission at all

- **Driver's decision:** "I never want to give location permission to the app, nor to the YouDrive login page; only to Google Maps."
- **App:**
  - `ACCESS_FINE/COARSE/BACKGROUND_LOCATION` and `FOREGROUND_SERVICE_LOCATION` are removed from the merged manifest (`tools:node="remove"`), so Android can never grant them.
  - The location foreground service (`RouteService`) and the `play-services-location` library are deleted.
  - The location step is gone from onboarding, as are the Location row and the arrival-radius slider in Settings.
- **What changes for the driver:**
  - The next stop comes only with **Next**: in the app, on the floating button or in the notification.
  - Automatic arrival / departure detection, the current street name (the "You are on" bar), the distance and the waiting timer no longer appear. The floating panel's big circle shows the next stop's area, and its speaker repeats the announcement.
  - The route status line says location is off by choice.
  - The detection code (`ArrivalDetector`, `CurrentStreet`) stays in the source, unused, so it could come back if the driver ever changes their mind.
- **YouDrive page:** the WebView has geolocation disabled (`setGeolocationEnabled(false)`) in addition to refusing every prompt. YouDrive's web code only asks for a position when its own map "centre on me" button is tapped, and it ignores the refusal. The location text the driver saw is YouDrive's own information notice (Cordova-oriented background-location disclosure). It is shown once per page storage.
- Google Maps navigation is unchanged (Maps has its own permission).
- Version **1.4.5 (versionCode 12)**.

## Version 1.4.6: Chrome identity, nothing appears unexpectedly, smaller text

- **Driver's request:**
  - Remove the "wv" marker.
  - Keep the 5-minute reload as is, with no setting.
  - Check that nothing appears unexpectedly.
  - Make the text a little smaller.
- **Browser identity:**
  - The WebView's user agent is Chrome's reduced Android user agent (`BrowserIdentity.chromeUserAgent`: `Android 10; K`, `Chrome/<major>.0.0.0`, no `; wv`, no `Version/4.0`). The Chrome major version is the installed WebView's, which is the same engine.
  - UA client hints: the "Android WebView" brand is replaced by "Google Chrome" (`WebSettingsCompat.setUserAgentMetadata`, when supported).
  - X-Requested-With: current WebView no longer sends this header. The androidx switch for it (`REQUESTED_WITH_HEADER_ALLOW_LIST`) is deprecated and restricted in webkit 1.17, so it is not used.
  - Reading stays passive: only the page's visible text, no requests of our own to YouDrive's servers.
- **Review for things that appear unexpectedly (and fixes):**
  - *Another view or day in YouDrive* (tomorrow, a trip's details) produced "added / cancelled" alerts. Now `TripWatch.isNewList` treats the reading as a new list, taken over silently, when either:
    - none of the earlier trips is left (with at least 2 before), or
    - more than 3 and more than half of the list changed at once.
    Single changes still alert.
  - *Reload while in use:* the 5-minute reload runs only while YouDrive's window is closed. It is a constant (`RELOAD_MS`), and the unused setting was removed.
  - *Status flicker:* during a reload the page shows its login or nothing for a moment. The status leaves WATCHING only after 20 s without trips, so the ongoing notification no longer flashes "Logged out".
  - *Problem line:* only failures of the page itself are shown: main-frame network error, main-frame HTTP ≥ 400, certificate. Sub-request errors, the page's script errors and the login-form repair go to the debug log only (debug builds).
  - *Background page:* opens no other app (intent / BankID links) and shows no JS dialogs unless the window is shown. Media again needs a user gesture (the WebView default).
  - *Orphan notification:* `YouDriveService` cancels its status observers before removing the foreground notification, and cancels it in `onDestroy`, so "Watching YouDrive" cannot be re-posted after watching is switched off. The notification is only re-posted when its text changes.
  - *Renderer crash / OOM:* `onRenderProcessGone` throws the page away and opens a new one (in the window if shown, otherwise in the background while watching) instead of the app crashing. The page storage login is kept.
  - Checked and left as is:
    - Route announcements and Maps launches happen only on the driver's actions (Start, Next, Back, edits of an active route).
    - The "Open Maps" notification appears only when Android blocks a background start at a batch change.
    - The floating-button reminder appears only when the driver hid the button during a route.
    - Every dialog and toast follows a tap.
- **Smaller text:**
  - The Compose typography is scaled by 0.9 (font size and line height).
  - The floating panel's texts are scaled by 0.9, and the street circle auto-sizes between 10 and 18 sp.
  - Touch targets are unchanged.
- Version **1.4.6 (versionCode 13)**.


## Release 1.0: new visual identity, explanations behind "?", project guide

- **Driver's request:**
  - The UI looked poor, and the numbers at the top of YouDrive did not show which button they belonged to.
  - Text overlapped the numbers.
  - Make everything clear and easy on the eyes, with a modern, elegant identity and smaller buttons.
  - Replace the Arabic explanations with a very small "?".
  - Write a full project guide and make this release number 1.
- **Bug behind "numbers without buttons":** YouDrive's Compose bar had no `Surface`, so `LocalContentColor` stayed black. Its icons and title were drawn black on black. The bar is now inside a `Surface` with the theme's content colour.
- **Visual identity "Night transit"** (`ui/theme/Theme.kt`):
  - Colours:
    - Background: dark blue-grey `#0B0F17`.
    - Surfaces: `#131A26` / `#1A2231`, with hairline borders `#243044`.
    - Brand: sky blue `#6EA8FF`, for actions.
    - Times: amber `#FFC56B`.
    - Status: green `#4ADE80`, amber `#FBBF24`, red `#F87171`.
  - A compact Material 3 type scale (body 16/14 sp, titles 20/16 sp).
  - Shapes 10 / 14 / 20 dp.
  - The time-status colours (`TimeLabels`) and the floating panel's colours follow the same palette.
- **Components** (`ui/Components.kt`):
  - `AppButton`: primary (filled) or tonal with a hairline. 48 dp; 52 dp for main actions; 60 dp for Next / Back.
  - `AppCard` and `ListRow` for grouped settings and cards.
  - A slimmer `TopBar`, and a small `SectionTitle`.
- **Explanations:**
  - Inline hint paragraphs are gone. `HelpDot` is a 16 dp "?" (32 dp touch area) that opens the explanation in a dialog, in Arabic while setting 104 is on.
  - Help became closed topic cards that open on tap.
  - Onboarding shows the step title with a "?".
- **Reference numbers:** they no longer cover anything.
  - `Modifier.ref(n)` = a draw node plus an 11 dp strip reserved above the element. The draw node comes first, so it draws over the strip.
  - `centered = true` inside rows.
  - `refCorner(n)` = a small pill in the empty corner of icon buttons and switches.
  - Card rows put the number inside their padding (`ListRow(ref = …)`), clear of the rounded corner.
  - Swipe rows put it outside the swipe box.
- **Screens:**
  - Home: header with "?" plus Settings and Help as icons; cards for YouDrive, the display and the floating button; history in one card.
  - Review: compact stop cards.
  - Active route:
    - A gradient card for the current stop.
    - A slim bottom bar with Back / Next and four small action tiles.
  - YouDrive: one slim bar (back, title + status dot, bell, ⋮). Start page and Reload moved into the menu with labels.
  - Passenger display:
    - Brand colours.
    - The big title never splits a word: the max size is capped so the widest word fits one line.
- **App icon:** a white stop pin with a "next" chevron on a blue diagonal gradient, plus a route line. Adaptive, with a monochrome version.
- **Version:** `versionName "1.0"`. `versionCode` stays increasing (14) so it installs over 1.4.6.
- **Docs:**
  - `docs/GUIDE.md`: the whole code explained, in Arabic.
  - `CLAUDE.md`: working rules loaded in every new conversation.
  - `README.md`: rewritten as a Release 1.0 manual with updated number tables.
- **Tests:**
  - `ScreenshotsRoboTest` renders every screen, the "?" popup and the floating panel to `app/build/screenshots/` for visual checks.
  - `UiSmokeRoboTest` follows the new layout: icons for Settings / Help, and the YouDrive menu.

## 1.0 (versionCode 15): device-test support

- A second Claude session on the user's laptop now installs and tests every build on the S20 Ultra over adb. It asked for stable ids, test inputs and a fixed delivery place.
- **Stable ids:**
  - `Modifier.ref(n)` / `refCorner(n)` also set the test tag `ref_<n>`, and the Compose roots (`AppRoot`, the YouDrive bar) set `testTagsAsResourceId`. UI Automator therefore sees every numbered control as resource-id `ref_<n>`, the same numbers as the README tables.
  - The floating panel's views get `R.id.ref_1..ref_17` (`res/values/ids.xml`), even when the numbers are hidden.
- **Test inputs:** two invented dispatch-list screenshots (`testdata/screenshots/`), drawn by `ScreenshotsRoboTest.deviceFixtures`. `DeviceFixturesTest` asserts what the parser reads from them.
- **Delivery:** the release-signed `dist/NastaStopp.apk` on the branch (same key every time, so updates install in place), not a debug APK. The cloud's debug key changes between sessions.
- The version line (125) shows the versionCode too: "Version 1.0 (15)".
- **Test race fixed:** `RouteControllerRoboTest` set-up deleted the route file on a background thread while the next test saved it. `RouteController.awaitPersisted()` (tests only) now waits for queued writes.

## 1.0.1 (versionCode 16): fixes from the first real-device test (PR #1)

The device session tested build 15 on the S20 Ultra: 10 pass, 2 pass with issues, 1 fail, 1 blocked.
- **B6 (blocker), YouDrive never loaded on Android 13 ("certificate 3" = `SSL_UNTRUSTED`):**
  - The site chains to **Telia Root CA v2**, which Android 13 and older don't ship; they only have TeliaSonera v1.
  - Fix: `res/xml/network_security_config.xml` adds that public root as an extra trust anchor **for `regionvarmland.se` only**, next to the system store.
  - The certificate comes from the Mozilla store (Debian ca-certificates). Its serial `01675F27D6FE7AE3E4ACBE095B059E` and SHA-256 `24:2B:69:74:…:B8:2C` match what the tester saw from the server.
  - TLS verification is unchanged; untrusted certificates are still refused. There is no `proceed()`.
- **B1, "Västra Torggatan 12" read as "Torggatan 12":**
  - ML Kit split the row after "Västra" ("10:45 Västra" | "Torggatan 12, …").
  - A street prefix word (Västra, Östra, Norra, Södra, Stora, Lilla, Gamla, Nya, Övre, Nedre, Yttre, Inre, Sankt, S:t) left capitalised at the end of the previous line is now put back in front of the street.
  - Such prefixes are never dropped as if they were a surname in the geocoder candidates.
  - Tests are in `DeviceFixturesTest`.
- **B2, floating panel over the passenger display:** `OverlayManager.suppress(key, on)` keeps the panel away while the passenger display (Screen.DISPLAY_LOCAL) is shown.
- **B3 / B5, end-route dialog:** the text now says what really happens:
  - The route and its list are deleted.
  - Its trips stay under "Previous trips" until they expire or are cleared.
  - Google Maps keeps navigating until it is closed there.
- **B4, `ref_83` missing in the dialog:** dialogs and menus are separate windows, so each numbered element now sets `testTagsAsResourceId` itself. `ref_<n>` therefore works everywhere.
- **V1:** the corner number pill sits 4 dp up and out from the control, beside a switch track or "?" ring instead of notching it.
- **V2:** the floating panel's surfaces are nearly opaque (`0xFA…`), so Maps labels don't show through.
- **Not changed:**
  - Maps' own picture-in-picture over the app (V3).
  - Maps' first-run sign-in and terms screens (V4).

## 1.0.2 (versionCode 17): test aids asked for by the device session

- **Floating-panel positions in logcat:** UI Automator's dump can't see overlay windows, so the tester had to tap the panel by coordinates.
  - With `adb shell setprop log.tag.NastaStoppRefs DEBUG`, every layout or move of the panel logs one line with each visible part's screen bounds, e.g. `ref_5=[l,t][r,b]`.
  - It logs ids and bounds only, never stop data, and stays silent unless switched on (`Log.isLoggable`).
- **`fixture_prefixes.png`:** ten invented stops with street prefixes and multi-word names. It covers Östra, Norra, Södra, S:t, Stora, Lilla, Gamla and Övre, plus two rows split after the prefix. `testdata/README.md` now lists every invented name, number, street and postcode for the privacy search.
- **Test for B2:** the panel leaves while the passenger display suppresses it, and no "closed" reminder is posted.

## 1.0.3 (versionCode 18): notes from the second device test (PR #1)

The device session tested build 17: 7 pass, 1 pass with issue, sign-in not run yet, 0 fail. **B6 is confirmed fixed:** the YouDrive login page loads on the S20 Ultra.
- **V5, the panel's Back at stop 1:** it was faded as a whole view (alpha 0.35), so the map showed through it and its number pill. Now only its icon and caption are dimmed; the button stays opaque.
- **Test aid:** the Home confirmation dialogs get numbers: 96 / 97 (Delete / Cancel after Clear, 34) and 98 / 99 (Delete / Cancel after Clear history, 39). Home's own range (20–39) is full.
- **V3, Maps' picture-in-picture covers End (82) and part of Next (78):** the driver left the choice to the build session.
  - **End moved to the top bar** of the route screen: a red × icon, still number 82, still behind the confirmation dialog (83 / 84). The picture-in-picture window sits in the bottom-right corner by default, and ending is rare, so it doesn't belong beside Next. The bottom row keeps Repeat, Open Maps and Edit list.
  - The bottom button area also sets `Modifier.preferKeepClear()`, which asks the system to keep floating windows off it. Systems that support keep-clear areas move the picture-in-picture window away; others ignore it.
  - Not chosen: moving Next or the whole button block, which would break the Back-left / Next-right order shared with the floating panel.
- **V6, the announcement names a district instead of the address's town:** intended. The default is 106 "District when available"; 107 "Town only" announces the town.

## 1.1 (versionCode 19): trip kinds from YouDrive and the "Route cards" identity

The driver showed a YouDrive day list and asked for two things: understand its cards, and a complete new look in YouDrive's colours plus yellow. The screenshot had real passengers, so nothing from it was copied; only its colours and layout were used, and every test uses invented data.

**Trip kinds**
- YouDrive's cards are: a grey **Pull-out** (the depot, the same start point every day), then green **Pick-up** and white **Drop-off** cards, and at the end a **Pull-in** back to the depot.
- `core/parse/TripKinds.kt`: `TripKind` and `labelIn`. Only a whole label counts ("Pick-up", "12:48 Pick-up", "Drop off", "Hämtning", "Lämning"); a sentence containing the word does not.
- The label sits in the same card as the time, so `AddressExtractor.timesAndKinds` chooses **one direction for both** (above or below the address) by the majority of times and labels together. A trip therefore never takes its neighbour's kind. This also fixes a time tie: a YouDrive screenshot, where the depot's time sits beside its address, had equal counts in both directions and took the times from above (the wrong card).
- Consecutive identical addresses are still merged, except when their kinds differ: a drop-off and then a pick-up at the same place are two stops.
- **The Pull-out becomes `RouteData.depot`**, the start point: a grey "Start" card above the trips (review 127, route 85, "?" 129). It is not a stop, so it is not sent to Maps, announced or counted. A Pull-in stays a normal (grey) stop, because the driver does drive back.
- `Stop.kind` is saved with the route (a new field with a default, so older saved routes still load). Editing a stop's text keeps its kind.
- Privacy: the kind is the list's label, not personal data. The rule is now "addresses, times and the trip kind only".
- Test aid: `testdata/screenshots/fixture_youdrive.png`, drawn in YouDrive's two-column card layout with invented data. Expected: start point Depågatan 1 + 4 stops (pick-up, drop-off, pick-up, drop-off). `fixture_time_above.png` now also gives kinds (its "Hämtning" / "Lämning" labels).

**Visual identity "Route cards" (replaces "Night transit")**
- Light, from YouDrive's own colours (sampled from the screenshot):
  - page `#F4F5F7`, white cards;
  - pick-up green `#9CD39C`, depot grey `#CACACA`;
  - black `Ink #17171A` for text and main actions, like YouDrive's "Arrive" button.
- **Taxi yellow `#FFC61A`** (`Accent`) marks the next action: Next, Start route, the current trip's time, the logo.
- Every trip card has its YouDrive colour (`kindColor`) and a white `KindLabel` pill. The current trip has a thick black border, like YouDrive's active card.
- Time status on white: green `#1E7A34`, amber `#B45309`, red `#C62828`; the status pill now has white text.
- System bars: dark icons always (`SystemBarStyle.light`), also when the phone is in dark mode. The framework theme is now `Theme.Material.Light`, so the YouDrive page's own form controls are light too.
- Floating panel: white buttons with black icons, yellow Next, a black circle with a yellow ring, and an info card in the next trip's colour (`infoColor`). The disabled Back (V5) stays opaque.
- Passenger display: light, with "Next stop" on a yellow pill and a yellow speaker button.
- Icon: a black pin with a yellow chevron on a yellow gradient. Home shows the same mark before the app name.
- Not done: a separate dark (night) variant. The driver asked for YouDrive's colours; a night variant can follow if the light screens are too bright in the dark.

## 1.2 (versionCode 20): YouDrive read card by card, passenger names, night look, design tokens

The driver compared the app's list with the real YouDrive page and found mix-ups. **The app reads YouDrive as page text, not as a screenshot.** The mix-ups came from interpreting that text as one long list. They were reproduced with an invented copy of the same structure (see `YouDriveCardsTest`):
1. **A drop-off at a place without a house number was lost** (a hospital's main entrance), so pick-ups and drop-offs no longer paired up.
2. **The wrong one of a card's two times was taken.** The app took the booked time (🤝 pick-up) or the latest arrival (🔒 drop-off) instead of the scheduled time (🕘) that YouDrive orders the route by: 09:30 instead of 09:35, 10:00 instead of 09:48.
3. **The start point showed a repeated word**: the depot's name, which is also its street, before the street and number.

**Fix: read the page card by card.**
- `READ_PAGE_JS` now also returns each trip card's own text (`c`). It finds a card without knowing the page's markup: from each visible kind label, it climbs to the largest element that holds no other kind label.
- `YouDriveCards` parses each card on its own:
  - kind;
  - the **first time** (scheduled) as the trip's time, and the second (booked) time as the change-detection key, because it does not move when the schedule is re-planned;
  - the address: the first line the parser accepts, else the line after the passenger's name (a place), with the list's most common town tried first;
  - the name;
  - done trips ("Performed", "Departed").
- The old whole-text reading stays as a fallback, and for screenshots.
- A repeated word in an address is shown once ("Depågatan Depågatan 1" → "Depågatan 1"). This is generic: `withoutRepeatedWords`.
- **Add all trips skips trips that are already done.** The start point is always kept.
- Screenshots of YouDrive still go through the whole-text reader, where a place without a number is not recognised. The in-app YouDrive page is the reliable path.

**Passenger names (the driver's decision):**
- The rule "no names" becomes "first + last name only": "Per Johan Albin Stenbäck" → "Per Stenbäck".
- `AddressExtractor.personName` accepts a line only when it is clearly a name: 2–6 capitalised words, with no digits, commas, streets, towns, kind or status words.
- It is shown on the driver's own screens only: review 134, route 135, upcoming rows, the floating panel, and YouDrive's change rows.
- It is **never** spoken, sent to the passenger display over Bluetooth, put in a notification or "Previous trips", or logged. `RouteControllerRoboTest.namesStayOnTheDriversScreens` guards all of these.

**Design tokens, a night look, and the old light blue:**
- `ui/theme` is now three layers:
  - `Palette`: raw colours;
  - `AppColors`: roles, with `DayColors` / `NightColors`;
  - `AppEffects`: shadows, press scale, colour fades.
- `Theme.kt` maps the roles onto Material 3. Screens use `AppTheme.colors.<role>`. No hex value is left outside the two colour files.
- The floating panel has no colours of its own any more (`PanelColors` converts the roles). The same goes for the reference numbers (`LocalAppColors`) and the system bars (`SystemBarsFollowTheme`).
- The Appearance setting: Day (default) / Night / Automatic (130–132).
- **Night** is the first design's calm blue-grey. Main buttons are its light blue; pick-ups dark green, the depot dark grey; yellow still marks the next action, and the current trip's border is yellow.
- **The first design's light blue `#6EA8FF` is back**:
  - by day as the selection/information colour (switches, section titles, "Then", links; a darker `#2563EB` for text contrast) and the panel's ring;
  - by night as the main action colour.
- **Effects**, all set in `AppEffects`:
  - a soft card shadow by day;
  - buttons shrink to 97 % while pressed;
  - a trip card's colour fades over 250 ms;
  - the current card lifts (6 dp).
- `TripSurface` is the one trip-card component: review, route, start point.
- `ThemeContrastTest` checks every text/background pair of both looks against WCAG: 4.5 for text, 3 for borders and bold status text on trip cards. It caught two weak pairs, now fixed:
  - the day "soon" amber on green: `#B45309` → `#A04A07`;
  - the night secondary text on a green card: → `#AEB8C9`.
- Not done: YouDrive's own page stays light at night, because it is their site.

## 1.3 (versionCode 21): the name on the card's first line, the street we are on, no duplicated trips

The driver compared the app's list with YouDrive again: "look at the original and what you transferred".

**Where the mix-ups came from.** The list held two imports at once: one from 1.1 (booked times, no names, notes read as stops) and one from 1.2. The same trip was then listed twice, at 16:15 and at 16:25.
- **Add all trips now syncs instead of adding.** A trip already in the list is brought up to date instead of added again: its time, kind and name, and its address while it is not located yet (`RouteController.syncTrips`).
  - A trip matches when it has the same address, a compatible kind and name, and a time no more than 45 min apart.
  - Duplicate copies left by an older import are removed, and a stop whose time moved is put back in time order.
  - The toast says "Trips updated from YouDrive: n".
- **A note is not an address.** A line with four or more lower-case words is a sentence ("… så är det vid …"), even when it mentions a street. This does not apply to text the driver types.
- **The name is the card's first line.** On a YouDrive card the name is always the first line of the right column, so it is taken from there even when written in lower case ("anna testsson" → "Anna Testsson").
- An existing list keeps its old rows until the next Add all trips. Clearing the list once and adding again is the quickest clean start.

**The passenger's name is on each card's first line, level with the time**, as on YouDrive:
- review (134);
- route: the current trip (135) and the coming trips;
- YouDrive's change rows;
- the floating panel: a new part **18** on the time line, cut short when long. The address line no longer carries the name.

**Location, only to name the street we are on (the driver's decision).** It reverses 1.4.5's "no location at all":
- The floating button's circle shows the street the vehicle is on, and tapping it says the street. Without permission the circle shows the next stop's area, as before.
- Location is asked at **Start route** (after the driver's tap) and in Settings (**119**, with "?").
  - "While using the app" only: `ACCESS_BACKGROUND_LOCATION` stays removed from the manifest.
  - `service/StreetService` is a foreground service of type location, running only while a route is active. It uses the system's `LocationManager` (no Play services), with a new position every 4 s or 15 m.
- **Positions only name the street** (`CurrentStreet`). They never reach `RouteController.onLocation`, so the route never moves on by itself: Next stays the driver's tap.
  - They are never stored, logged or sent. `Geocoding.reverse` uses the system's geocoder.
  - `StreetServiceRoboTest` drives up to a stop, waits there and drives off, and checks that the route stays where it was.
- **The YouDrive page never gets the location.**
  - Its WebView keeps `setGeolocationEnabled(false)`, and every request from the page is refused.
  - `YouDriveRoboTest.theYouDrivePageNeverGetsTheLocation` guards this.
  - Android permissions belong to one app: the YouDrive app, if installed, never inherits ours.
- The route screen's status line is now always "Tap Next when you leave a stop". The distance and waiting timer stay hidden, because they came from the arrival detector.

**Also:**
- The privacy note now lists the trip kind and the passenger's first + last name among what is kept.
- A stale colour comment was removed from `OverlayManager`.

## 1.4 (versionCode 22): the floating panel as one card of smoked glass

The driver sent a photo of the panel over the home screen and asked for a big visual step: a modern design that shows clearly on most backgrounds, a rectangle instead of the street circle, and transparency. The photo showed three problems:
- the circle broke a long street name inside the word, over two lines;
- the loose round buttons (Back, ×, –, speaker) got lost among the app icons;
- the white Back disappeared on a light background.

**One card of smoked glass**, the same in both looks, because it floats over any map or wallpaper:
- **Frame**: translucent (74 % by day, 78 % by night), so the map shows through. A soft sheen fades down from the top edge.
- **Two hairline edges**: dark outside and light inside. They outline the card on a light day map and on a dark wallpaper or night map alike.
- **Deeper glass** (`well`) under everything that carries text: the street bar, the trip and Back. The text stays readable whatever is behind.
- **Readability is tested.** `ThemeContrastTest.checkPanel` checks every text on the panel over white, black, a green park and a blue route line (4.5), and both edges on white and black (3).
  - It caught two weak pairs, now fixed: the grey trip number on its pill (now white), and the light edge on black (30 % → 40 %).
- **Colours** are new roles, `AppColors.panel` (`PanelRoles`: `DayPanel` / `NightPanel`), in the design system. The panel still has no colours of its own.
- **Effects** in `AppEffects`:
  - `panelShadow` (12 dp lift);
  - `panelPressedScale` (buttons shrink to 93 % while pressed, over `panelPressMs`).

**Layout (300 dp wide):**
1. **Header:** the clock, the trip number on a small pill, and round glass – and ×.
2. **The street bar**, a wide rectangle instead of the circle (ref 2).
   - The street is on one line, at up to 22 sp, shrinking to fit. It is never broken inside a word.
   - Below it is the area (3), with a yellow arrow before the street and the speaker (4) at the end.
   - Tap = say the street; long-press = repeat.
3. **The next trip** (16), with a stripe in its kind's YouDrive colour:
   - the time on a yellow pill (10) with the passenger's name level with it (18), with the whole line for the name;
   - the kind as a chip in YouDrive's colour (**new part 19**) and the on-time status with a coloured dot (11);
   - the address on its own line (13).
4. **Actions:** Back (1) and repeat (15) on glass, and **Next** (5) as the one bright thing on the card, a wide yellow bar 56 dp high.
- **Minimised:** a glass capsule with the time and the trip number (17). Its ring and dot are the on-time colour.
- **Arabic:** the card mirrors, and Latin street names and addresses align with the panel's side (`TEXT_ALIGNMENT_VIEW_START`).

**Not possible:** real background blur. Android blurs only behind an activity's own window (`setBackgroundBlurRadius`), not behind an overlay added with `WindowManager.addView`. The frosted look therefore comes from translucency, the sheen and the edges.

**Test renders** (local only, `ScreenshotsRoboTest`):
- `floating`, `floating_night`, `floating_ar` and `floating_bubble` draw the panel over half a light map (with a park and a route line) and half a dark wallpaper.
- The street comes from an invented lookup.

## 1.5 (versionCode 23): a simpler panel, with the next stop's street beside its time

The driver's changes to the 1.4 glass panel:
- **The repeat button is gone.** Long-press Next (or the street bar) still repeats the announcement. Its number, 15, is not reused.
- **The street bar has no speaker button any more.** A tap anywhere on the bar says the current street. The speaker's number, 4, is not reused.
- **The next stop's street and number sit beside the time, two steps larger** (13 → 17 sp, bold, shrinking to fit on one line). Tapping them says them (`RouteController.speakStopStreet`, part 13).
- **No "Pick-up" / "Drop-off" word** on the panel: "the time is enough". The kind keeps only its coloured stripe. Part 19 is now the stop's postal code and town, on the third line.
- The second line is the passenger's name and the on-time status.
- **Back and Next are compact pills, 44 dp high** instead of 56. Each has its icon beside its label; Next still fills the rest of the row.

**Speaking a street:** automatic announcements still name only the district or town. The app says a street only on the driver's tap:
- the current street, from the street bar;
- the next stop's street and number, from part 13.

It never says a passenger's name, and it is not sent to the passenger display. `FloatingPanelRoboTest.tappingTheStopsStreetSaysStreetAndNumberOnly` checks this with a named stop.

The kind chip's colour role (`onKind`) went with the chip. `ThemeContrastTest` now checks that the kind stripe stands out on the glass.

**YouDrive login:** at the same time the driver sent his YouDrive username and password and asked for them to be typed in whenever the login page appears. They were **not** stored or used:
- the repository and the APK are public, so anything built in could be read by anyone;
- the cloud session cannot reach the phone;
- the hard rule stands until the driver chooses one of the two options offered:
  1. find why the "Remember Me" login is lost when leaving the page, and fix it;
  2. an on-phone login that he types once, encrypted with the Android Keystore.

The driver was again advised to change the password.

## 1.6 (versionCode 24): the full next stop in the announcement, and YouDrive's automatic sign-in

**Announcements (the driver's request):** "When I press Next, say the street and its number, the area, then the city."
- New option `AnnouncementDetail.FULL`, Settings 136, the default: "Nästa stopp: Storgatan 14, Herrhagen, Karlstad. Därefter: Kronoparken."
  - The stop after it stays short, by district or town.
  - 106 (district) and 107 (town) remain for a shorter announcement.
  - `GeoLogic.fullSpokenName` says each part once and drops an apartment number ("lgh 1102").
- The setting moved to a new key (`announcement_detail_v2`), so every phone starts on the full announcement.
- **A passenger's name is never spoken.** Some lists put a surname before the street ("Andersson Storgatan 14").
  - `RouteController.streetOf` takes the address candidate that starts at the street, the same rule the YouDrive alerts use.
  - The announcement, the tap on the panel's street (13) and the panel's line all use it.
  - `fullRouteFlowAnnouncesOnlyAreaNames` now checks that the surname is not said.
- A tap on the street bar says the street **and** the area ("Drottninggatan, Centrum").
- The panel's town line (19) no longer shows the postal code.

**YouDrive's automatic sign-in (the driver's decision).** The driver found that YouDrive forgets its login whenever its window closes, in Chrome as well, so that part cannot be fixed in the app. He chose the sign-in offered in 1.5.
- **Settings → YouDrive → Sign in automatically** (156), off by default.
  - When YouDrive shows its login form, the app presses Login.
  - It uses what YouDrive's own "Remember me" filled in. If the fields are empty, it uses the login the driver saved on the phone (157).
- **The saved login** (`youdrive/YouDriveLogin`):
  - It is typed once in a Settings dialog (150 / 151, Save 158, Delete 159, Cancel 154).
  - It is encrypted with AES-256-GCM, with a key made inside the Android Keystore that never leaves the phone.
  - It is kept in app-private storage with no backup.
  - It is never shown again: the dialog always starts empty. It is never logged or sent.
- **Filling it** (`core/youdrive/SignInScript`):
  - It runs only when the WebView is on `https://youdrive.regionvarmland.se`, and the script checks the address again inside the page.
  - It fills only empty fields.
  - The values go in as JSON string literals, so they cannot run as code.
  - It returns a status word only.
- **Limits** (`AutoSignIn`):
  - At most 2 tries in a row, 20 s apart, so a changed password never locks the account.
  - Then the status line says the sign-in did not work.
  - After "Log out" nothing is tried until the driver has signed in himself.
- **Tests:** `AutoSignInTest` checks the tries, the pause after log-out, the host check and the escaping. `YouDriveLoginRoboTest` checks that nothing readable is stored and that Delete removes the login.
- **Rules updated** (CLAUDE.md, GUIDE §8):
  - credentials still never go into code, the repo, logs or replies;
  - the cloud session never uses them;
  - the laptop tester never types them.
  - The login the driver sent in the chat was not used anywhere, and he was advised to change that password.

## 1.7 (versionCode 25): the stop after next in full, the street said when it changes, the speed

The driver's three requests:
1. **"Därefter" also names the street.** With the full announcement (136, the default), the stop after the next one is said with its street and number, then its district: "Nästa stopp: Storgatan 14, Herrhagen, Karlstad. Därefter: Kungsgatan 5, Kronoparken." (`RouteController.thenSpokenName`). A surname before the street is still dropped. 106 / 107 keep both short.
2. **The current street is said by itself whenever it changes** (`geo/StreetCaller`).
   - Each new street is said once, and the area alone never is.
   - It is queued after any announcement (`Announcer.speak(interrupt = false)`), and dropped rather than kept when the voice is not ready.
   - Only during a route, and not sent to the passenger display.
   - A quick switch sits on the panel's street bar: the speaker button, part **4**. It shows a crossed-out speaker when off. The same switch is Settings **137**. It is on by default, at the driver's request.
   - A tap on the bar still says the street and area.
   - This is a new, deliberate exception to "nothing appears on its own", recorded in the rules.
3. **The car's speed on the panel** (part **15**, beside the clock):
   - The speed comes from the positions the street service already receives (`CurrentStreet.speedNow`), in km/h with Western digits in every language.
   - It is hidden when no position arrived in the last 10 s.
   - The service now asks for a position every 2 s, also when standing still, so the speed stays current. Street lookups keep their own throttle (8 s / 35 m).
   - Positions still go to `CurrentStreet` only: nothing moves the route on, and nothing is stored, logged or sent.

Numbers 4 and 15, retired in 1.5, are reused for the new parts. The README tables say what each number is now.

Tests:
- `FloatingPanelRoboTest.theStreetIsSaidWhenItChangesUnlessSwitchedOff`: said once, queued, silent when switched off;
- `theSpeedIsShownWhilePositionsComeIn`: 12.5 m/s → "45 km/h", gone after 10 s;
- `theStreetBarsSpeakerSwitchesStreetSpeech`;
- the announcement tests now expect the street after "Därefter".

## 1.8 (versionCode 26): the street named exactly, and the speed in its own circle

The driver's report on 1.7: "the speed did not show", and "the current street was better before; now it often invents streets". He asked for it to be exact, with an offline map on the phone (not inside the app) if needed.

**Causes found:**
- The street service asked for "balanced power" positions. The phone then often gives Wi-Fi/mast positions, 30–60 m off (up to 60 m was accepted) and without a speed. That made the speed empty and the street wrong.
- The street came from the reverse geocoder's **nearest address**. On a main road without addresses, or near a crossing, that address is on a side street. Since 1.7 every such change was also **said aloud**, so the wrong names stood out.

**Fix (street):**
- **GPS itself** when precise location is allowed, with high accuracy, plus the heading.
  - Positions less exact than 25 m (map) / 30 m (geocoder) are not used.
  - Settings 119 now says in red when location is only approximate. Start route and 119 ask for precise location.
- **The offline street map** (the driver's decision), Settings **138** (download or update) and **139** (delete):
  - the named roads a car can use in Värmland, from OpenStreetMap (© OpenStreetMap contributors), downloaded once when the driver taps;
  - 48 fixed tiles over the region (a margin included) from the Overpass API, one at a time with a pause, with a retry and a second server;
  - the requests name the region, never the position;
  - read as a stream and saved in a compact file in app-private storage without backup (`noBackupFilesDir/streetmap`), not in the app. A new download replaces it only when complete.
- **Matching** (`core/geo/StreetMatcher`):
  - every position is matched to the nearest named road within 30 m;
  - at a crossing, the road along the heading wins (a road across it counts 25 m farther), once the car moves faster than 3 m/s;
  - no road within reach means **no name**, never a guess.
- **Without the map**, the geocoder's street counts only when its address lies within 40 m of the position (`StreetLookup.pickNear`); otherwise only the area is shown.
- **Either way a new street needs two agreeing readings** (`StreetTracker`), so one stray position never changes (or says) the name. Without the map, the confirming lookup is made after 8 s even when standing still.
- The area (district) still comes from the geocoder, throttled as before.

**Fix (speed):**
- The speed comes from GPS. When a position has none, it is worked out from the previous position (both better than 20 m, 0.5–10 s apart).
- It sits in **a big circle of its own** (60 dp, yellow ring) beside the street bar (part 15): the number only, "–" until a position has one, shown only with location allowed.
- The small arrow before the street was removed to give the bar room.

**Rules updated:** the internet is now also for the map download (driver's tap only), and the street must never be invented.

**Not verifiable here:** the build environment's network policy blocks `overpass-api.de`, so the real download was not run. It is tested with invented Overpass answers (`StreetMapDownloadRoboTest`) and on the device.

**Tests:**
- `StreetMatcherTest`: nearest road, heading at a crossing, nothing far away or with a poor position, the file round trip, confirmations and hold;
- `StreetMapDownloadRoboTest`: tiles, query, streamed reading, a cut-short answer refused;
- `FloatingPanelRoboTest.withTheStreetMapTheRoadComesFromTheMap`;
- the speed circle and the worked-out speed;
- `StreetLookupTest.aFarAddressGivesTheAreaButNoStreet`.
