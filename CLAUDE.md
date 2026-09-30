# Nästa Stopp: working rules

An Android app for a Swedish shared-ride driver:
- It reads addresses and times from screenshots (bundled ML Kit OCR) or from the driver's YouDrive page.
- It orders the stops, opens Google Maps in batches of 10, and announces the next stops in Swedish.

**Read `docs/GUIDE.md` first.** It explains all the code, the data flow, the design system, the build/release steps and the hard rules. User-facing docs: `README.md`. Decisions and their reasons: `DECISIONS.md`. The code and docs describe the app as it is; the git history before the tag `v1.0` is an archive.

## The user
- Hussam, the driver and the project's owner, writes in Arabic. **Reply in Arabic**, concisely.
- **Never send app screenshots** (token cost). Screenshots you render for your own checks (`ScreenshotsRoboTest`) stay local.
- The phone is a Samsung Galaxy S20 Ultra (Android 13) and the passenger display is a Galaxy Tab S9+. You cannot reach the devices from the cloud.
- Deliver each build as a zip of `dist/NastaStopp.apk`, sent as a file.

## Hard rules (never break)
- Images are never copied or stored. Keep only addresses, times, the trip kind (the list's Pick-up / Drop-off / Pull-out label) and the passenger's **first + last name**: no middle names, phone numbers or other text.
- The name is for the driver's own screens only (review, route, the phone's floating panel). It is never spoken, sent to the passenger display, put in a notification or "Previous trips", or logged (`namesStayOnTheDriversScreens` guards this).
- The floating panel on the tablet (switch 206 there, off by default) shows only what the tablet's passenger display receives: no name, and no street bar or speed (they stay on the phone). The tablet sends back only the button pressed (`LinkMessage.Command`: Next, Back, Repeat).
- Never log addresses or OCR text in release (use `util/DebugLog`). `allowBackup=false`.
- **Location only names the street the vehicle is on and shows its speed.** While-in-use only, never background (`ACCESS_BACKGROUND_LOCATION` stays removed). `service/StreetService` (LocationManager, no Play services) feeds `CurrentStreet` and nothing else: never the route (no automatic advance; only the driver's Next moves it), never stored or logged. Asked at "Start route" and in Settings (119).
  - It is never sent, with one approved exception: the tablet's Google map. Only while the driver turns on phone Settings 209 (off by default) and a tablet shows the route, the phone sends the vehicle's position and the next stop (its point or address, never a name) to the tablet over Bluetooth every 5 s (`LinkMessage.Where`). The tablet sends them to Google (Maps JavaScript API and Routes API, `ui/screens/RouteMap`) with the driver's own key, typed on the tablet (208). Nothing is stored or logged.
- **The YouDrive WebView never gets location**: `setGeolocationEnabled(false)` and every page prompt is denied (`theYouDrivePageNeverGetsTheLocation`).
- INTERNET is only for the YouDrive page, the offline street map's download, the passenger display's weather and the tablet's Google map (above). No Firebase, analytics, crash reporting or Hilt.
  - The download starts only when the driver taps Settings 138. It fetches fixed OpenStreetMap tiles over Värmland from Overpass, never the position.
  - The weather is SMHI's forecast for one fixed point (Karlstad, `core/weather/SmhiForecast`). The phone fetches it every 30 min, only while a passenger display shows a route, and never sends the position or an address.
- The tablet may host a weather app's own widget on its display (207). The weather app draws and updates it; this app never reads it.
- Notification access (Settings 203, granted by the driver) reads **only Google Maps' navigation notification** (`nav/MapsNavigationListener`). Only its minutes left and distance are kept, in memory, for the display snapshot. Nothing from it is stored or logged.
- The street name must never be invented:
  - GPS with high accuracy; positions less exact than 25–30 m are not used;
  - with the downloaded map, the road is matched by distance and heading (`StreetMatcher`);
  - without the map, the geocoder's street counts only when its address is within 40 m;
  - a new street needs two agreeing readings (`StreetTracker`);
  - no match means no name.
- YouDrive credentials never go into code, the repo, logs or replies, and Claude never uses them. The app keeps them only if the driver typed them into Settings (157) on his phone:
  - `youdrive/YouDriveLogin`: encrypted with an Android Keystore key, in app-private storage with no backup;
  - filled only into YouDrive's own login form on `https://youdrive.regionvarmland.se` (`SignInScript`);
  - at most 2 tries in a row (`AutoSignIn`);
  - the automatic sign-in switch is 156, off by default.
- Never disable TLS verification. Never unset HTTPS_PROXY.
- Announcements:
  - The next stop is said in full: street + number, then district, then town (setting 136, the default). Settings 106 and 107 shorten it to the district or the town.
  - The stop after it is said with its street + number, then its district.
  - On the driver's tap, the panel says the current street + area (street bar) or the next stop's street + number (part 13).
  - The current street's name is also said by itself each time it changes (`geo/StreetCaller`). It is queued after any announcement. The panel's speaker (part 4) or Settings 137 switch it off.
  - **A passenger's name is never spoken.** A surname before the street is dropped (`RouteController.streetOf`).
  - The passenger display may show street + number (setting 114).
  - On the passenger display there is no speaker button: a tap on the next stop's address says the announcement, a tap on another trip says "Klockan 8 och 05: street number, area", and a tap on the clock says the time.
- Nothing may appear on its own. Every dialog, toast, Maps launch or sound follows a driver action. The only exceptions are:
  - YouDrive trip alerts;
  - the "open Maps" fallback notification;
  - the current street's name while the driver keeps "say the street" on (panel 4 / Settings 137).
- No real passenger data in the repo: tests and docs use the invented names and addresses in `testdata/README.md`.

## UI conventions
- Design system in `ui/theme/` (see GUIDE §9), in three layers:
  1. `Palette.kt`: raw colours.
  2. `AppColors.kt`: colour roles, as `DayColors` and `NightColors`.
  3. `AppEffects.kt`: shadows, press scale and colour fades.
- Screens use `AppTheme.colors.<role>` and `AppTheme.effects`. **No `Color(0x…)` outside `Palette.kt`/`AppColors.kt`.** The looks are Day / Night / Automatic (setting 130–132). `ThemeContrastTest` must stay green.
- Use the components in `ui/Components.kt`: `AppButton`, `AppCard`, `TripSurface` (every trip card), `ListRow`, `TopBar`, `SectionTitle`, `KindLabel`.
- Explanations never sit inline. Use `HelpDot(R.string.…)` (a tiny "?"); its text is Arabic while setting 104 is on.
- Every control or piece of info gets a reference number (hidden by default; Settings 105 shows them):
  - `Modifier.ref(n)` puts it in a strip above the element; use `centered = true` inside rows.
  - `Modifier.refCorner(n)` puts it in the corner of icon buttons and switches.
  - Inside card rows, pass `ListRow(ref = n)`.
  - Keep the README tables in sync.
- Strings: `values/` is **Arabic (default)**, `values-en/` is English (the default UI language), `values-sv/` is Swedish. Add every key to all three.
- The floating panel (`overlay/OverlayManager`) is built with Views. It has no colours of its own: `PanelColors` converts the `AppColors.panel` glass roles when the panel is built. It is smoked glass in both looks. Its text must stay readable over white, black, a park and a route line (`ThemeContrastTest.checkPanel`).
- Comments and docs describe the present: no version numbers, "since…", or who asked for what. Keep the reason when it is not obvious.

## Build and release
```bash
./gradlew test assembleRelease lintDebug lintRelease   # must exit 0: all tests pass, lint "No issues found"
```
- **Check the exit status before committing.** Never chain a commit after a build that may have failed.
- Bump `versionCode` (always +1) and `versionName` in `app/build.gradle.kts`. 1.0 is versionCode 1.
- Verify the APK (build tools in `/opt/android-sdk/build-tools/37.0.0/`):
  - `aapt2 dump badging`: version; FINE/COARSE location present, BACKGROUND location absent.
  - `apksigner verify --print-certs`: the SHA-256 starts `1ae627778bdd`.
  - `dexdump`: ML Kit `TextRegistrar` is present.
- Copy the APK to `dist/NastaStopp.apk` (gitignored), update `DECISIONS.md` and `README.md` when a decision or the UI changes, then commit and push to `claude/nasta-stopp-android-app-soeru7`.
- **Publish as a GitHub Release** (APKs are never committed):
  - write short English release notes (no passenger data) to a file in the scratchpad;
  - run `tools/publish-apk.sh <notes>`. It pushes the APK on a short-lived branch `apk-drop/v<versionName>`;
  - `.github/workflows/publish-apk.yml` then creates the Release `v<versionName>` on the source commit and deletes the branch;
  - check that the Release exists (`get_release_by_tag`) before posting the READY;
  - a wrong Release is removed with the `Delete release` workflow (`.github/workflows/delete-release.yml`, run by hand with the tag).
- Signing uses `keystore.properties` (gitignored), which points to `~/.nastastopp-signing/`. Hussam holds a private backup of the key; never commit it or post it anywhere. R8 stays disabled because it strips the ML Kit registrars.

## Device testing (with the local Claude session on the user's laptop)
- A second Claude Code session on the user's laptop has the S20 Ultra (SM-G988B, Android 13) on adb. It installs and tests every build. **Never try to reach the phone yourself.**
- **Delivery:** the GitHub Release `v<versionName>`. It is release-signed with the same key every time, so `adb install -r` updates in place. Always bump `versionCode`.
  - APK URL: `https://github.com/HussamEl/S20Ultra/releases/download/v<versionName>/NastaStopp.apk`. The repo is public.
- **Stable ids for UI Automator:**
  - Every numbered Compose control has resource-id `ref_<n>` (test tag + `testTagsAsResourceId` on the roots), also while the numbers are hidden. Floating-panel parts are `se.eldebosh.nastastopp:id/ref_<n>`.
  - The numbers are the README tables. Keep new controls numbered.
- **Test inputs:** the invented screenshots in `testdata/screenshots/`. `testdata/README.md` lists the expected stops and every invented term for the privacy search, and `DeviceFixturesTest` asserts the stops.
- **Floating-panel positions:** UI Automator can't see overlay windows. After `adb shell setprop log.tag.NastaStoppRefs DEBUG`, the panel logs its parts' screen bounds (`ref_<n>=[l,t][r,b]`), ids and bounds only.
- **Each READY lists:** the steps the change affects, a short smoke set, the steps that need Hussam's hands (sign-ins, terms, OS dialogs), and what is urgent for the driver.
- **Loop:**
  1. Post `DEVICE-TEST READY <sha>` on PR #1 with the APK link and a numbered checklist.
  2. The tester replies `DEVICE-TEST RESULT <sha>`.
  - **Hussam decides when the laptop session tests** and tells it himself. Post the READY on the PR and carry on. Don't write prompts for it, ask him to relay, or wait for it.
- **YouDrive:** it is the real dispatch site, so the user signs in manually. The tester never types credentials.
