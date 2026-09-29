# Nästa Stopp: working rules

An Android app for a Swedish shared-ride driver:
- It reads addresses and times from screenshots (bundled ML Kit OCR) or from the driver's YouDrive page.
- It orders the stops, opens Google Maps in batches of 10, and announces the next stops in Swedish.

**Read `docs/GUIDE.md` first.** It explains all the code, the data flow, the design system, the build/release steps and the hard rules. User-facing docs: `README.md`. Decision history: `DECISIONS.md`.

## The user
- Writes in Arabic. **Reply in Arabic**, concisely.
- **Never send app screenshots** (token cost). Screenshots you render for your own checks (`ScreenshotsRoboTest`) stay local.
- The phone is a Samsung Galaxy S20 Ultra (Android 13) and the passenger display is a Galaxy Tab S9+. You cannot reach the devices from the cloud.
- Deliver each build as a zip of `dist/NastaStopp.apk`, sent as a file.

## Hard rules (never break)
- Images are never copied or stored. Keep only addresses, times, the trip kind (the list's Pick-up / Drop-off / Pull-out label) and the passenger's **first + last name** (Hussam's decision, 1.2): no middle names, phone numbers or other text.
- The name is for the driver's own screens only (review, route, floating panel). It is never spoken, sent to the passenger display, put in a notification or "Previous trips", or logged (`namesStayOnTheDriversScreens` guards this).
- Never log addresses or OCR text in release (use `util/DebugLog`). `allowBackup=false`.
- **No location permission at all** (removed with `tools:node="remove"`); only Google Maps uses location. The YouDrive WebView gets no geolocation.
- INTERNET is only for the YouDrive page. No Firebase, analytics, crash reporting or Hilt.
- Never use or store the driver's YouDrive credentials. Never disable TLS verification. Never unset HTTPS_PROXY.
- Spoken announcements name only the district or town. The passenger display may show street + number (setting 114).
- Nothing may appear on its own. Every dialog, toast, Maps launch or sound follows a driver action. The only exceptions are YouDrive trip alerts and the "open Maps" fallback notification.

## UI conventions
- Design system in `ui/theme/` (see GUIDE §6), in three layers:
  1. `Palette.kt`: raw colours.
  2. `AppColors.kt`: colour roles, as `DayColors` and `NightColors`.
  3. `AppEffects.kt`: shadows, press scale and colour fades.
- Screens use `AppTheme.colors.<role>` and `AppTheme.effects`. **No `Color(0x…)` outside `Palette.kt`/`AppColors.kt`.** The looks are Day / Night / Automatic (setting 130–132). `ThemeContrastTest` must stay green.
- Use the components in `ui/Components.kt`: `AppButton`, `AppCard`, `TripSurface` (every trip card), `ListRow`, `TopBar`, `SectionTitle`, `KindLabel`.
- Explanations never sit inline. Use `HelpDot(R.string.…)` (a tiny "?"); its text is Arabic while setting 104 is on.
- Every control or piece of info gets a reference number:
  - `Modifier.ref(n)` puts it in a strip above the element; use `centered = true` inside rows.
  - `Modifier.refCorner(n)` puts it in the corner of icon buttons and switches.
  - Inside card rows, pass `ListRow(ref = n)`.
  - Keep the README tables in sync.
- Strings: `values/` is **Arabic (default)**, `values-en/` is English (the current UI), `values-sv/` is Swedish. Add every key to all three.
- The floating panel (`overlay/OverlayManager`) is built with Views. It has no colours of its own: `PanelColors` converts the `AppColors` roles when the panel is built.

## Build and release
```bash
./gradlew test assembleRelease lintDebug lintRelease   # must exit 0: all tests pass, lint "No issues found"
```
- **Check the exit status before committing.** Never chain a commit after a build that may have failed.
- Bump `versionCode` (always +1) and `versionName` in `app/build.gradle.kts`.
- Verify the APK:
  - `aapt2 dump badging`: version, and no location permission.
  - `apksigner verify --print-certs`: the SHA-256 starts `1ae627778bdd`.
  - `dexdump`: ML Kit `TextRegistrar` is present.
- Copy the APK to `dist/NastaStopp.apk`, update `DECISIONS.md` and `README.md`, then commit and push to `claude/nasta-stopp-android-app-soeru7`.
- Signing uses `keystore.properties` (gitignored), which points to `~/.nastastopp-signing/`. Hussam holds a private backup of the key (since 2026-09-28); never commit it or post it anywhere. R8 stays disabled because it strips the ML Kit registrars.

## Device testing (with the local Claude session on the user's laptop)
- A second Claude Code session on the user's laptop has the S20 Ultra (SM-G988B, Android 13) on adb. It installs and tests every build. **Never try to reach the phone yourself.**
- **Delivery:** `dist/NastaStopp.apk` on this branch. It is release-signed with the same key every time, so `adb install -r` updates in place. Always bump `versionCode`.
  - Raw URL: `https://github.com/HussamEl/S20Ultra/raw/<sha>/dist/NastaStopp.apk`. The repo is public.
- **Stable ids for UI Automator:**
  - Every numbered Compose control has resource-id `ref_<n>` (test tag + `testTagsAsResourceId` on the roots). Floating-panel parts are `se.eldebosh.nastastopp:id/ref_<1..17>`.
  - The numbers are the README tables. Keep new controls numbered.
- **Test inputs:** the invented screenshots in `testdata/screenshots/`. `testdata/README.md` lists the expected stops and every invented term for the privacy search, and `DeviceFixturesTest` asserts the stops.
- **Floating-panel positions:** UI Automator can't see overlay windows. After `adb shell setprop log.tag.NastaStoppRefs DEBUG`, the panel logs its parts' screen bounds (`ref_<n>=[l,t][r,b]`), ids and bounds only.
- **Each READY lists:** the steps the change affects, a short smoke set, the steps that need Hussam's hands (sign-ins, terms, OS dialogs), and what is urgent for the driver.
- **Loop:**
  1. Post `DEVICE-TEST READY <sha>` with the APK link and a numbered checklist.
  2. The tester replies `DEVICE-TEST RESULT <sha>`.
  - Until a PR exists, the user relays messages.
  - **Hussam decides when the laptop session tests** and tells it himself. Post the READY on the PR and carry on. Don't write prompts for it, ask him to relay, or wait for it.
- **YouDrive:** it is the real dispatch site, so the user signs in manually. The tester never types credentials.
