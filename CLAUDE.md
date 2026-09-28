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
- Images are never copied or stored. Keep only addresses and times: no names, phone numbers or other text.
- Never log addresses or OCR text in release (use `util/DebugLog`). `allowBackup=false`.
- **No location permission at all** (removed with `tools:node="remove"`); only Google Maps uses location. The YouDrive WebView gets no geolocation.
- INTERNET is only for the YouDrive page. No Firebase, analytics, crash reporting or Hilt.
- Never use or store the driver's YouDrive credentials. Never disable TLS verification. Never unset HTTPS_PROXY.
- Spoken announcements name only the district or town. The passenger display may show street + number (setting 114).
- Nothing may appear on its own. Every dialog, toast, Maps launch or sound follows a driver action. The only exceptions are YouDrive trip alerts and the "open Maps" fallback notification.

## UI conventions
- Theme tokens live in `ui/theme/Theme.kt`: `Brand`, `TimeColor`, `Located`, `NotLocated`, `Hairline`.
- Use the components in `ui/Components.kt`: `AppButton`, `AppCard`, `ListRow`, `TopBar`, `SectionTitle`.
- Explanations never sit inline. Use `HelpDot(R.string.…)` (a tiny "?"); its text is Arabic while setting 104 is on.
- Every control or piece of info gets a reference number:
  - `Modifier.ref(n)` puts it in a strip above the element; use `centered = true` inside rows.
  - `Modifier.refCorner(n)` puts it in the corner of icon buttons and switches.
  - Inside card rows, pass `ListRow(ref = n)`.
  - Keep the README tables in sync.
- Strings: `values/` is **Arabic (default)**, `values-en/` is English (the current UI), `values-sv/` is Swedish. Add every key to all three.
- The floating panel (`overlay/OverlayManager`) is built with Views. Its colours are constants that mirror `Theme.kt`.

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
- Signing uses `keystore.properties` (gitignored), which points to `~/.nastastopp-signing/`. R8 stays disabled because it strips the ML Kit registrars.
