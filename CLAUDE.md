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
  - **The one exception, decided by the driver: a YouDrive trip's whole card** (`Stop.card`: every word on it, as written, phone numbers and codes too). It goes with the route only to the passenger display's trip card (235), laid out as YouDrive's details window (`core/youdrive/TripCardText`), which opens only on the driver's tap on that trip's person figure (231 under the next stop, 234 beside the other trips). The tablet is beside the driver; the passengers sit far behind and do not touch it. The card is **never said**, and never in an announcement, a notification, "Previous trips" or a log (`theTripCardGoesOnlyToTheDisplay`).
- An address may begin with the place YouDrive writes with it (`Stop.place`: a hospital, a health centre, a care home). The driver's screens show it. The passengers see and hear **only a place of care's own name** (`core/parse/Places`: hospital, health centre, dental care; never a department, ward or treatment), never a home's (care home, short-term housing). Centralsjukhuset is written "C-Sjukhuset" and said "Centralsjukhuset, …, Karlstad". An entrance (every word ending in "entré(n)" or "ingång": "Huvudentrén", "Dialysentrén") is a door, not a department: it is written and said with the place, as written ("C-Sjukhuset Dialysentrén", `Places.entrances`).
- **A town is never guessed.** A point is taken only when it matches the written postal code or town (`GeoLogic.choose`); an answer in another place is never used. A place written without a town or postal code is looked for in Värmland and taken only when every answer is in one town (`GeoLogic.inOneTown`); a few well-known places know their town (`Places.KNOWN`). Otherwise the stop says "town unknown" and the driver adds it; `PlaceMemory` keeps that town for the place (only places without a house number, never a home's address or a name).
- **Karlstad's districts** (`core/geo/Districts`): a district in Karlstad is the municipality's own (its open data "Stadsdelar", CC0) where the stop's point lies and its street runs (OSM street names, ODbL; no house numbers, no names), else its street's only district, else none. The geocoder's neighbourhood is never said in Karlstad.
- **The driver's entrances** (`route/Entrances`, 256–259): for an address he chooses, the stopping point he pastes (`core/geo/Coordinates`, inside Sweden only) and how to get in, kept by street + number + postal code or town (`Stop.entranceKey`), never a name; on the phone only (`noBackupFilesDir`), never logged, until he deletes them (Settings 259). Only the point goes to the tablet. The address's own point stays on the stop beside it.
- **One stop in Google Maps** (252–255): "Navigate here" and "Street photos" are Google Maps URLs (no key), by the stop's point (the driver's entrance, else the address's), never a search by name. The batch of 10 keeps the address text unless the driver set an entrance.
- **Lantmäteriet's address register** (Värmland + Örebro) is only proposed (`DECISIONS.md` §7): no download, account or terms before the driver has the access and has reviewed them.
- The first + last name is for the driver's own screens (review, route, the phone's floating panel). The passenger display gets **only the last name of each trip still to come** (`DisplayItem.lastName`; never a trip done's):
  - on the display itself only the next stop's: beside its time over its address there is only a small outlined figure (231): a tap on it opens the trip's card; a trip without a card shows the last name beside it for 15 s instead (236), and a tap on the name says it;
  - **on the tablet's map (decided by the driver)** each stop's last name is written under its address on its pin, on the page only (`MapWay.Stop.name`), never sent to Google;
  - never a first name, outside the trip card the driver opens (above). A name is never in an announcement, a notification or "Previous trips", and never logged (`namesStayOnTheDriversScreens` guards this).
- The floating panel on the tablet (switch 206 there, off by default) shows only what the tablet's passenger display receives: no name, and no street bar or speed (they stay on the phone). The tablet sends back only the button pressed (`LinkMessage.Command`: Next, Back, Repeat), the order the driver set on his map (`LinkMessage.Order`: the trips' numbers, `DisplayItem.id`; the phone refuses one that drops a passenger off before picking them up) and what its map shows (`LinkMessage.MapView`: trip numbers, legs' minutes and switches, never a name or a position). The phone's floating panel drives the display as if tapped there (`LinkMessage.Remote`: show a trip, open or close the map, its buttons, an order tried, used, the time said), carried out in `PassengerDisplayScreen`; a message of an unknown kind is skipped by either end. The phone sends nothing before the tablet says it is a passenger display of the same protocol (`LinkProtocol.isDisplayHello`), and the tablet counts the link only when the phone answers as a controller (`isControllerHello`). After a break the tablet keeps the last trips 2 minutes, then clears them; it clears them at once when the display is left or another phone connects.
- **The address log** (`route/AddressLog`, Settings 302–303, decided by the driver): every address the routes went to, once (`RouteController.logLine`: place, street and number, town; never a name, never the card), on the phone only (`noBackupFilesDir`), never logged; it leaves the phone only when the driver taps Share.
- Never log addresses or OCR text in release (use `util/DebugLog`). `allowBackup=false`.
- **On the phone, location only names the street the vehicle is on and shows its speed.** While-in-use only, never background (`ACCESS_BACKGROUND_LOCATION` stays removed). `service/StreetService` (LocationManager, no Play services) feeds `CurrentStreet` and nothing else: never the route (no automatic advance; only the driver's Next moves it), never stored, logged or sent. Asked at "Start route" and in Settings (119).
  - **The tablet's Google map** (approved by the driver) takes the car's position from the tablet's own GPS (`geo/TabletPosition`: LocationManager, high accuracy, every second, only while the passenger display is in sight, never in the background; the permission is asked when the map is first wanted, on a tap). The tablet sends that position and the stops of the way it draws (their points or addresses, never a name: the next stop and the two after it, or the trip before the one looked at and the two after it, and the trips the driver adds with + up to seven) to Google (Maps JavaScript API, Routes API with its route matrix, `ui/screens/RouteMap`) with the driver's own key, typed on the tablet (208). Nothing is stored or logged. The phone's position never leaves the phone.
  - One page and one map per display, never made again (a look change recolours it, `setLook`). The map never leaves northern Europe (`restriction`, `minZoom`; a point outside it is never fitted). **The map is never one of the display's moments and never flies (decided by the driver)**: it is asked for only when the driver opens it. The map the driver opens (219, a long press on a trip) is the same flat map under his touch (his fingers zoom) and small buttons at its left (270 Google's satellite picture of the same map, 239–241); no 3D or street photo is made on it: on his tap, 242 opens Google Earth flying to the stop looked at in 3D (else Google Maps' satellite view) and 244 opens Google Maps' street photos there, by the stop's point, never a name (`MapsLauncher.openEarth`, `MapsUrlBuilder.streetViewUrl`). These two buttons are the only way the display opens Google's apps.
  - Google is asked again only for what changed: the way when its stops or order change, or after 4 min **and** 1.5 km of driving; a way already given is reused for 15 min from within 1 km (`RouteMap.known`, in memory only); the route matrix only on "Suggest". Addresses are found by Android's geocoder, never Google's billed Geocoding API. Nothing from Google is stored.
  - Every map draws the way from the car through its stops, lettered A, B, C… (`DisplaySnapshot.around`), each stop in its own colour (`AppColors.wayStops`: dark by day, light by night), the way on from it in the same colour (legs along the same street thinner and side by side); over each stop's point its letter and its trip's time in one tag, under the point its address and its passenger's last name (all on the page only, never sent to Google); the stop looked at is larger, its time red and beating under a beating red marker (no ring; the same red beating time in the driver's lists); stops at the same place stand apart, each with a thin line back to where they meet. The borders of the counties, municipalities and districts of Värmland and Örebro are drawn thin and dashed from the app's own `assets/boundaries.json` (SCB's RegSO, CC0; `tools/make-boundaries.py`): nothing is asked for them. The map fills the screen to its edges. On his map the driver can try another order, ask Google's travel times for the best one (`OrderPlanner`: a pick-up stays before its drop-off, the fewest minutes late, then the shortest), and send it to the phone (245–251, 260, 268, 269: a small list on the map, `WayList`; a trip is dragged by its handle, or its line after a long press, or moved with its arrows; + adds the next trip in its place, "+ Earlier" the one before the way, × takes one off the way on this map only; the trip picked is the one looked at). The phone takes it by the trips' numbers (`RouteController.reorder`): the next stops are said again and Maps opens again when they changed. `DisplayItem.rider` is only a number shared by a passenger's pick-up and drop-off, never a name.
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
  - Each stop is said time first, as the display shows its time first: "Nästa stopp: Klockan åtta noll två. Storgatan 14, Karlstad. Därefter: Klockan åtta trettio. …" (`Announcements.nextStops`). **A time is read as a clock shows it, in words, never "minuter" and never a leading zero on the hour** (decided by the driver): 08:02 is "Klockan åtta noll två", 09:30 "Klockan nio trettio", 09:00 "Klockan nio" (`Announcements.timeWords`).
  - **One motion for every trip shown large** (README "الحركة الموحّدة للعناوين الكبيرة", M1–M11, `PassengerDisplayScreen.Show`; decided by the driver): at home now and then (silent), a trip tapped, and every announcement. The time grows to fill the screen on black, beats, shrinks and goes; the address (the street, its number under it) turns over into view to fill the screen, each line swells as it is read, beats slowly with a flame along its letters' edges, swells and turns away. No beam, glow or halo. What is said goes in steps, each trip's time then its address, with silences that fit (`Announcements.steps`, `silenceBefore`: `LEAD_MS`, `TO_ADDRESS_MS`, `TO_NEXT_MS`); the device that speaks moves its display on with its voice.
  - A shop is said by its short name ("ICA Maxi Bergvik", `Places.shopName`) and shown whole.
  - On the driver's tap, the panel says the current street + area (street bar) or the next stop's street + number (part 13).
  - The current street's name is also said by itself each time it changes (`geo/StreetCaller`). It is queued after any announcement. The panel's speaker (part 4) or Settings 137 switch it off.
  - **A passenger's name is never in an announcement.** A surname before the street is dropped (`RouteController.streetOf`). The passenger display says the next stop's last name only when it is tapped.
  - **Every name is said once.** A district or town already said in what comes before it ("Centralsjukhuset Karlstad", then "Karlstad") is left out (`GeoLogic.fullSpokenName`, word by word). Wherever "Centralsjukhuset" is written on a screen it is written "C-Sjukhuset" (`Places.written`); it is always said in full.
  - A place of care is said by its name first ("Provby Vårdcentral, Strandvägen 3, Karlstad"); a care home's name is never said, only its street.
  - The passenger display may show street + number (setting 114).
  - On the passenger display there is no speaker button: a tap on the next stop's address says the announcement, a tap on a trip shows it like an announcement and says "Därefter: Klockan åtta noll fem. street number, area" ("Sen: …" for the later ones; a trip done: "Klockan sju trettio. street number, area"), and a tap on the clock says the time. On the tablet the map sign and a long press on a trip show the way on the display's own full-screen map; the display opens Google's apps only from the driver's 242 and 244 on that map.
- The passenger display's moments (the time, the weather, the travel time; never the map) take turns, each 50 s after the one before has gone, and all rest 2 minutes after the car stops moving (`core/display/CarStillness`, `geo/CarMotion`: the tablet's accelerometer and GPS speed; no camera, nothing stored). The motion sign (297) on its top line shows the sensor working; the other signs there hide until a tap or a swipe down (298).
- Nothing may appear on its own. Every dialog, toast, Maps launch or sound follows a driver action. The only exceptions are:
  - YouDrive trip alerts;
  - the "open Maps" fallback notification;
  - the current street's name while the driver keeps "say the street" on (panel 4 / Settings 137).
- No real passenger data in the repo: tests and docs use the invented names and addresses in `testdata/README.md`.

## UI conventions
- The display's trip card and order list are windows (`ui/screens/FloatingWindow`, 261, 262, 264–266), like any window: moved by their bar; a side edge makes them wider or narrower, the top or bottom edge taller or lower, a corner both (width and height apart, the text keeps its size; no marks on the border); two fingers move and size them; kept inside the screen, and reopened where and as big as the driver left them (`WindowPlaces` in `SettingsStore`: place, width and height only).
- Design system in `ui/theme/` (see GUIDE §9), in three layers:
  1. `Palette.kt`: raw colours.
  2. `AppColors.kt`: colour roles, as `DayColors` and `NightColors`.
  3. `AppEffects.kt`: shadows, press scale and colour fades.
- Screens use `AppTheme.colors.<role>` and `AppTheme.effects`. **No `Color(0x…)` outside `Palette.kt`/`AppColors.kt`.** The looks are Day / Night / Automatic (setting 130–132); the passenger display is black by default (`DisplayTheme` with `DisplayColors`), light on its look sign (223). `ThemeContrastTest` must stay green.
- Passenger display fonts: `DisplayFont` (Barlow Semi Condensed) for words and addresses, `DigitFont` (Atkinson Hyperlegible Next) for every number.
- Use the components in `ui/Components.kt`: `AppButton`, `AppCard`, `TripSurface` (every trip card), `ListRow`, `TopBar`, `SectionTitle`, `KindLabel`.
- Explanations never sit inline. Use `HelpDot(R.string.…)` (a tiny "?"); its text is Arabic while setting 104 is on.
- Every control or piece of info gets a reference number (hidden by default; Settings 105 shows them):
  - `Modifier.ref(n)` puts it in a strip above the element; use `centered = true` inside rows.
  - `Modifier.refCorner(n)` puts it in the corner of icon buttons and switches.
  - Inside card rows, pass `ListRow(ref = n)`.
  - Keep the README tables in sync.
- Strings: `values/` is **Arabic (default)**, `values-en/` is English (the default UI language), `values-sv/` is Swedish. Add every key to all three.
- The floating panel (`overlay/OverlayManager`):
  - full, it is `overlay/FloatingPanel` (Compose, `DisplayTheme`: the passenger display's look, fonts and time face) in a window like the display's windows: moved by its bar, sized from any edge or corner (width and height apart, also larger than its content, which scrolls; `PanelFrame`), its place and size kept (`SettingsStore.overlaySize`); `PanelOwner` gives the window its lifecycle;
  - minimised, it is the capsule, built with Views. The capsule has no colours of its own: `PanelColors` converts the `AppColors.panel` glass roles. It is smoked glass in both looks, readable over white, black, a park and a route line (`ThemeContrastTest.checkPanel`).
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
  - Every numbered Compose control has resource-id `ref_<n>` (test tag + `testTagsAsResourceId` on the roots, the floating panel's too), also while the numbers are hidden. The capsule's parts are `se.eldebosh.nastastopp:id/ref_<n>`.
  - The numbers are the README tables. Keep new controls numbered.
- **Test inputs:** the invented screenshots in `testdata/screenshots/`. `testdata/README.md` lists the expected stops and every invented term for the privacy search, and `DeviceFixturesTest` asserts the stops.
- **Positions UI Automator can't get:** it can't see overlay windows, and it never finds the passenger display idle (its clock and parts keep moving). After `adb shell setprop log.tag.NastaStoppRefs DEBUG` (then start the app again), the floating panel logs its parts' screen bounds and the app logs every numbered element's (`ui/Refs.kt`, `RefBounds`, at most once a second while they move), as `ref_<n>=[l,t][r,b]`: ids and bounds only.
- **Each READY lists:** the steps the change affects, a short smoke set, the steps that need Hussam's hands (sign-ins, terms, OS dialogs), and what is urgent for the driver.
- **Loop:**
  1. Post `DEVICE-TEST READY <sha>` on PR #1 with the APK link and a numbered checklist.
  2. The tester replies `DEVICE-TEST RESULT <sha>`.
  - **Hussam decides when the laptop session tests** and tells it himself. Post the READY on the PR and carry on. Don't write prompts for it, ask him to relay, or wait for it.
- **YouDrive:** it is the real dispatch site, so the user signs in manually. The tester never types credentials.
- **The local partner's proposals** (on PR #1): the official branch is always the base; the partner rebuilds its experiment on the latest release and posts findings as `NSGPT-PROP-YYYYMMDD-NNN`. Answer each on PR #1 with `CLAUDE REPLY <id>`: ADOPTED (version and commit), DEFERRED, REJECTED (why) or NEEDS-HUSSAM. Adopt one only after Hussam approves it in this session.
