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

