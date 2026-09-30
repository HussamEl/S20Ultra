# Decisions

The decisions that shape Nästa Stopp as it is, each with its reason. This is not a changelog. The
code and docs describe the app as it is now; the git history before the tag `v1.0` is an archive
and is not needed to work on the app.

## 1. Privacy and data

| Decision | Why |
|---|---|
| Only addresses, trip times, the trip kind (Pull-out / Pick-up / Drop-off) and the passenger's **first + last name** are kept. No phone numbers, middle names or other text. | The trips are paratransit rides and may reveal health information. The name helps the driver find the right person; nothing else is needed. |
| The name is shown **only on the driver's own screens** (review, route, floating panel). It is never spoken, sent to the passenger display, put in a notification or in "Previous trips", or logged. `namesStayOnTheDriversScreens` guards this. | The car and the tablet are shared with other passengers. |
| Screenshots are read in memory and never copied or stored. | Only the extracted text is needed. |
| The route expires **12 h after it was created** (checked on load, on resume and by an inexact alarm). Finished trips stay in "Previous trips" for 12 h, 24 h or 7 days (setting 116–118). | Short retention by default; the driver chooses how long to keep a history. |
| `allowBackup=false`, and the backup / data-extraction rules exclude everything. Route files live in `noBackupFilesDir`. | Nothing leaves the phone through backups. |
| No Firebase, analytics, crash reporting or Hilt. ML Kit's upload backend (`CctBackendFactory`) is removed from the merged manifest. | The app has internet access for YouDrive; this keeps ML Kit's own telemetry from using it. |
| Release builds log no addresses or OCR text (`util/DebugLog` logs in debug builds only). | Logs are readable by other tools on the phone. |

## 2. Network

| Decision | Why |
|---|---|
| `INTERNET` is used for the YouDrive page, for the street map's download (only after the driver taps Settings 138) and for the passenger display's weather. `ACCESS_NETWORK_STATE` is removed. | These are the only features that need the network. |
| **The weather** comes from SMHI's open forecast (`snow1g`, no key) for one fixed point (Karlstad, 59.38 N 13.5 E), never the phone's position or an address. The phone fetches it every 30 min, only while a passenger display shows a route (the tablet connected, or the display open on the phone), and sends the temperature and SMHI's weather symbol in the snapshot. | The passengers see the weather of the area they ride through; the request names a fixed place, so it reveals nothing about the car. |
| **The tablet's Google map** (approved by the driver as an exception to "the location is never sent"): off by default. Only while the driver turns on phone Settings 209 and a tablet shows the route, the phone sends the vehicle's position and the next stop (its point, or its address when not located; never a name) to the tablet over Bluetooth every 5 s (`LinkMessage.Where`). The tablet draws Google's map with the Maps JavaScript API in a WebView of its own (`RouteMap`, `assets/route_map.html`: no controls, no touch, no device location, console silenced, no navigation away) and asks Google's Routes API for the way (`RoutesApi`, basic tier, at most every 4 minutes for the same stop), with the driver's own key typed on the tablet (208). Nothing is stored or logged. | The driver wants the way to the next stop on the tablet the way Google draws it. Google needs the two places to draw it; the driver's own key keeps the use and its free quota his. |
| **The travel time** is read from Google Maps' own navigation notification (`nav/MapsNavigationListener`, a notification listener the driver allows in Settings 203). Only Google Maps' ongoing notification is read, and only its numbers are kept: the minutes left (or the arrival time turned into minutes) and the distance. Nothing is stored or logged; the value lives while the notification does. | Google Maps already knows the route and the traffic. Reading its notification needs no online service, no position and no Maps window on the display. |
| `res/xml/network_security_config.xml` adds the public **Telia Root CA v2** (from the Mozilla store) as an extra trust anchor for `regionvarmland.se` only. | The site chains to that root, which Android 13 and older do not ship, so YouDrive would not load. TLS verification stays complete: untrusted certificates are refused, and there is no `proceed()`. |

## 3. Location, the current street and the speed

| Decision | Why |
|---|---|
| Location is asked at "Start route" (and in Settings 119), **while in use only**. `ACCESS_BACKGROUND_LOCATION` is removed from the merged manifest. | It is needed only while the driver is on a route with the app open. |
| Positions feed `CurrentStreet` (the street and the speed) **and nothing else**. They never move the route on, and are never stored, logged or sent. Only the driver's Next advances. | Every announcement follows a driver action. An automatic advance would speak without a tap, and consecutive stops at the same place would be skipped. |
| `StreetService` uses the system `LocationManager`: the GPS provider when precise location is allowed, high accuracy, a fix every 2 s, also when standing still. | "Balanced" positions come from Wi-Fi and masts, 30–60 m off and without a speed, which names the wrong street. No Play services location library is needed. |
| **The street name is never invented.** With the offline map, `StreetMatcher` takes the nearest named road within 30 m. A road across the heading counts 25 m farther once the car moves faster than 3 m/s. Positions less exact than 25 m are not used. No road means no name. | At a crossing the road driven along must win over the road crossed. A wrong name is worse than none. |
| Without the map, the geocoder's street counts only when its address lies within **40 m** (`StreetLookup.pickNear`); otherwise only the area is shown. Lookups are throttled (≥ 8 s and ≥ 35 m apart, retry after 30 s) and run only while something shows the street. | The nearest address is often on a side street. |
| A new street needs **two agreeing readings** (`StreetTracker`); without any road for 20 s the name is cleared. | One stray position at a crossing must not change (or say) the name. |
| **Offline street map** (Settings 138 / 139): the named roads for cars in Värmland, from OpenStreetMap through the Overpass API. 48 fixed tiles over the region (with a margin), one at a time with a 1 s pause. The answer is read as a stream and saved as a compact binary file in `noBackupFilesDir/streetmap/`. A new download replaces the file only when complete. | Exact road names without sending the position: the requests name the region, never the car. The map lives on the phone, not in the app package. Attribution: © OpenStreetMap contributors (ODbL), shown in Settings. |
| Each finished tile is kept in `streetmap/parts/` until the map is built, so a download that stops continues from the tile where it stopped ("stopped at part n of 48: tap to continue"; parts older than 14 days are fetched again). A tile is tried 8 times over about 5 minutes (pauses of 10–60 s), alternating overpass-api.de and overpass.kumi.systems, and Settings says "the server is busy, waiting" meanwhile. | The public Overpass servers answer 429 / 504 in bursts; one busy answer must not throw away the tiles already fetched. |
| The speed is the GPS speed, or is worked out from two exact positions (both ≤ 20 m, 0.5–10 s apart). It is shown only when fresh (≤ 10 s), as a number in its own circle on the panel. | Some fixes carry no speed; a stale speed would mislead. |
| The YouDrive WebView **never gets location**: `setGeolocationEnabled(false)` and every page prompt is denied (`theYouDrivePageNeverGetsTheLocation`). | The app's permission must not reach the dispatch site. |

## 4. Announcements (Swedish TTS)

| Decision | Why |
|---|---|
| Full announcement by default (setting 136): "Nästa stopp: Storgatan 14, Herrhagen, Karlstad. Därefter: Kungsgatan 5, Kronoparken." Settings 106 / 107 name both stops by district or town only. | The driver wants the whole address. The shorter forms remain for other routes. |
| **A passenger's name is never spoken.** A surname some lists put before the street is dropped (`RouteController.streetOf`). | Other passengers hear the announcements. |
| A spoken area never contains digits or the stop's street. A district ending in a street suffix ("…gatan") falls back to the town (`GeoLogic.isSafeAreaName`). Only towns in the bundled list are spoken. | Geocoder districts are sometimes street names; a town is safer than a guess. |
| A tapped trip on the passenger display is said with its time first: "Klockan 8 och 05: Hamngatan 7, Skoghall." ("Klockan 8" on the hour). | A short word for the time makes clear the trip is at that time, and it is never taken for the next stop. |
| The current street is said by itself each time it changes (`geo/StreetCaller`), queued after any announcement and never over it. The panel's speaker (4) or Settings 137 switch it off. Tapping the street bar says the street and area; tapping the next stop's street says its street and number. | The driver hears where he is without looking. |
| If the default engine lacks Swedish but Google's TTS is installed, the app uses Google's engine. An announcement requested while the engine starts is spoken only if it is less than 20 s old. The English repeat uses `en-US` when available. Maps is ducked while speaking. | Samsung's engine often has no Swedish voice. A late announcement would be wrong. |

## 5. Nothing appears on its own

Every dialog, toast, sound and Maps launch follows a driver action. The only exceptions are:
- YouDrive trip alerts (their purpose);
- the "open Maps" notification when Android blocks a background start at a batch change;
- the current street's name while "say the street" is on.

## 6. Reading addresses (`core/parse`)

- **Rules**: a line is an address when it has (a) a postal code with a town, (b) a street with a house number, or (c) a known town. Rule (a) needs at least 2 letters before the postal code, so a lone "65224 Karlstad" is joined to the street line above it.
- **Noise**: phone numbers, times, apartment parts ("lgh", "vån N", "N tr", "port", "portkod"), c/o names and money lines are removed. UI words (in Swedish too) reject a line. Short all-caps codes ("SP1, HLI") are skipped.
- **Truncation**: everything after the first "…" is dropped, and so is the cut word before it, unless it is a known town or a complete postal code.
- **OCR fixes** apply only inside the 5 postal-code positions, need at least 3 real digits and must give 100 00–989 99.
- **Joining lines**: the next line is joined when it starts with a postal code or is only a known town. The result must contain a house number.
- **Street words**: the suffix list plus `allé/alle/allen/vagen` (OCR without diacritics). A standalone suffix ("gata", "väg") counts only after another word. A street prefix word (Västra, Östra, Norra, Södra, Stora, Lilla, Gamla, Nya, Övre, Nedre, Yttre, Inre, Sankt, S:t) left at the end of the previous line is put back in front of the street, because ML Kit sometimes splits a row there.
- **Duplicates**: consecutive stops with the same street core, a compatible postal code and town merge, unless their kinds differ (a drop-off then a pick-up at one place are two stops).
- **Times**: a time on the address line wins; otherwise the nearest time above or below it, never past a neighbouring address. The direction is chosen **once per screenshot** by majority, together with the trip kind, so a trip never takes its neighbour's time or kind. A stop with nothing in that direction takes the time on its other side when the neighbouring trip does not use that line: YouDrive's start point has its time beside (read before) its address, while every trip card has it below. A first line that is only a time (the status-bar clock) is ignored.
- **Names**: the line right above the address when it is clearly a name; first and last name only.
- **Towns**: `assets/localities_se.txt` (677 names: all 290 municipalities, Värmland's localities and common postal towns), matched without case or diacritics.
- The parser never throws on a line (a per-line guard and a 40,000-line fuzz test).

## 7. Geocoding

- The system `Geocoder` (no internet library): candidates in order (at most 6), a result matching the postal code or town wins at once, a 15 s timeout, and "not located" otherwise. "Locate again" retries.
- After a match, the display text starts at the street the geocoder found, which drops a leading surname without guessing.

## 8. The route and Google Maps

- Maps opens with at most **10 stops** per launch (its waypoint limit); the next batch opens at the end of each batch. `%20` for spaces, `%7C` between waypoints.
- Android blocks activity starts from the background, so a tap-to-open "Öppna Maps" notification is posted as well.
- Editing an active route re-launches Maps if the first 10 stops changed, and re-announces if the next stop changed.
- **Back** undoes the last Next: the previous trip becomes current, its history entry is removed, and Maps is re-launched only if its batch starts at the current trip (`batchStartStopId`).
- A **Pull-out** card is the start point (`RouteData.depot`): shown above the list, never sent to Maps or announced.
- "Sort by time" is a button, not automatic: the driver's order may be deliberate. Deletes show an Undo snackbar.
- "Avsluta" keeps the open trips in "Previous trips" as "not completed"; drivers often end instead of pressing Next at the last stop.

## 9. YouDrive

| Decision | Why |
|---|---|
| YouDrive (https://youdrive.regionvarmland.se) opens in the app's **own full-screen Activity** with a WebView at the phone's real size. | Chrome on Android has no extensions, and alerts are needed while Maps is in front. A WebView embedded in a Compose screen did not draw the login form. |
| The WebView presents itself as Chrome (reduced user agent without `; wv`, "Google Chrome" in the client hints). It reads only the page's visible text: no JavaScript bridge, no requests of its own, file access off. | The site sees a normal browser; the app stays passive. |
| The page is read card by card (`READ_PAGE_JS` → `YouDriveCards`): the first time on the card is the scheduled one, the second (booked / latest) is kept for comparing. Every trip is added: those with status Performed / Departed are marked done in YouDrive (`youDriveDone`) but stay in the route until Next passes them; a trip already done here is not added again, only its YouDrive mark follows. Without cards the whole text is parsed like a screenshot. | One card's time or name must never mix with its neighbour's. A driver may mark trips done in YouDrive before reaching them, so only Next moves the route. |
| Readings every 60 s (15 s while the window is shown); a reload every 5 min, only while the window is closed. A `specialUse` foreground service keeps the page alive only while watching is on. | Never reload under the driver's fingers. |
| `TripWatch`: the first reading is the baseline; a change must be seen in **two readings in a row**; empty readings are ignored; a trip gone more than 5 min after its time is done, not cancelled; another day or view (none of the earlier trips left, or more than 3 and more than half changed) is a new list without alerts. | No false alarms from half-loaded pages, finished trips or browsing. |
| Alerts: a "Trip changes" channel with sound and vibration, at most 5 then a summary. Changes are applied only when the driver taps Add / Remove. "Add all trips" is a sync: a trip already in the list (same address, kind and name, times ≤ 45 min apart) is refreshed, not doubled. | The driver keeps control of the route. |
| A hidden login form (its slide-in container left off screen) is reset to visible; certificate errors are shown and refused; a crashed renderer is replaced by a new page; "Log out" clears storage, cookies and cache and destroys the WebView. | These are the failure modes seen with the real page. |
| **Automatic sign-in** (switch 156, off by default). YouDrive forgets its login whenever its window closes, even in Chrome. The login is kept only if the driver types it into Settings 157 on his phone: AES-GCM with an Android Keystore key that never leaves the phone, in app-private storage without backup. It is filled only into YouDrive's own form on the exact host (checked in the page too), only into empty fields, as JSON literals. At most **2 tries**, 20 s apart; after "Log out" nothing is tried until the driver signs in himself. Claude never sees or uses the credentials. | Convenience without risk: a wrong password cannot lock the account, and the secret never leaves the phone. |

## 10. The floating panel

| Decision | Why |
|---|---|
| Built with Views in a `TYPE_APPLICATION_OVERLAY` window. | A Compose view in an overlay would need a hand-made lifecycle owner. |
| One card of smoked glass in both looks: a translucent frame, texts on deeper glass "wells", a light and a dark edge. It has no colours of its own: `PanelColors` converts `AppColors.panel`. `ThemeContrastTest.checkPanel` checks every text over white, black, a park green and a route blue. | It floats over any map or wallpaper. |
| Street bar (2, with area 3 and the speech switch 4) beside the speed circle (15); the next trip: time (10) with its street (13), name (18) and time status (11), town without postal code (19); Back (1) and Next (5); clock (8), progress (9), minimise (6), close (7); bubble (17). | Everything the driver needs at a glance, nothing more. |
| "–" shrinks it to a bubble; "×" closes it, and a silent notification, a Quick Settings tile and the switches bring it back. "×" and "–" react only to real taps (a drag moves the panel). It stays away while the passenger display is shown on the phone. | A panel hidden by accident must come back in one tap. |
| The bubble counts down to the next trip's time each second ("7:42", "1:04:30", "+3:10" once late) beside that time, in the display clock's status colours (`TimeStatus`): green, orange within five minutes (ring and dot beating at its minute), red once late (beating from five minutes). The countdown sits on the deeper glass, like the panel's text. | Shrunk, the panel still says how much time is left, and in the same colours as the passengers' clock. |
| The tablet's bubble is twice the phone's, its countdown larger; in the trip's last minute it swells to twice that and blinks, then settles. | Read from further away, and hard to miss when the time comes. |
| **The same panel on the tablet** (switch 206 on the tablet, off by default; needs the tablet's overlay permission): it shows the phone's next trip as the tablet's passenger display receives it (`LinkPanelSource`), and its Next, Back and Repeat go to the phone as a `command` message on the Bluetooth link, where the phone carries them out as if pressed on its own panel. No passenger name, and no street bar or speed: those come from the phone's location, which never leaves the phone. A tap on the stop's street says it on the tablet. The panel's code serves both (`PanelSource`); the phone's is `RoutePanelSource`. | The driver can work the route from the tablet too, while the name and the location stay on the phone. |
| The card's top stays at least 100 dp below the screen's top, and the window's transparent margin around the card is small (2 dp above, 6 dp beside, 12 dp below for the shadow). | The window takes every tap in its rectangle, margin included: it must not cover the top bars of the app or of Maps (the turn banner). |
| Its parts carry view ids `ref_<n>`, and with `setprop log.tag.NastaStoppRefs DEBUG` it logs their screen bounds (ids and bounds only). | UI Automator cannot see overlay windows. |

## 11. The passenger display (tablet)

| Decision | Why |
|---|---|
| Bluetooth RFCOMM between paired devices; no Wi-Fi, server or extra library. The phone listens on a secure and a fallback channel and serves only devices paired with it. The tablet finds the phone by itself (the last device first, then phones, then tablets and computers; headsets and car kits are skipped). | Works in a car without a network. |
| Only a `DisplaySnapshot` is sent: up to 7 trips done, the current and up to 7 upcoming trips, time and street + number with the area under it (setting 114; area only when off), and where each was marked done. Never names. | Passengers are driven to their doors; names stay private. |
| The display moves with the route. On Next, the "Därefter" card grows out of its place into the new next stop (a shared-bounds transition; Back runs it the other way). While an announcement is spoken, a spotlight follows it: "NÄSTA" and its time spring up and settle, the address slowly grows (up to 1.25×, as far as its page allows) and lights up in the highlight colour while the rest steps back, then the "Därefter" card rises out of the strip to 1.6× when its name comes (timed from the text, about 75 ms a letter) and settles. | The passengers see what is said as it is said. |
| The passengers can look around the route on the display: in the middle they page from the next stop on to the following trips and back to the ones done, sideways on a tablet and up and down on the phone. A page shows only its trip's time above the address (no "Därefter" / "Tidigare" word); the time pops up noticeably as the page comes in, then settles. The strip of cards below scrolls sideways through the same trips (up to 7 each way), follows the page and tints its card; dots (201) mark the place. It only changes what the display shows, never the route. A small floating Home button (200) brings both back, and so do any announcement and 30 s left alone. | A passenger can look ahead; the next stop is never lost for long, and only the driver moves the route. |
| A tap on any card, coming or done, swells it a little (1.08×) and tints it, shows its trip's page in the middle (address and time, large) and says it ("Klockan 8 och 05: Hamngatan 7, Skoghall."); after 6 s the display goes back to the next stop by itself. A tap on a page's address says that trip the same way; a tap on the next stop's address says the announcement; a tap on the clock says the time ("Klockan är 8 och 05"). Each speaks on the device tapped. There is no speaker button. A card is never called "Nästa". | Passengers can ask the screen by touching what they want to hear; "Nästa" is only the next stop. |
| The clock has no frame. The minutes are the large part, the hours smaller beside them, the seconds thin (Barlow Light) in the highlight colour right under the hours, and the colon, in the accent yellow, level with the hours, beats with the seconds; hours and seconds together are centred on the minutes, so the clock is no taller than its minutes. On a tablet it hangs down in the corner instead of pushing the stop down; the address starts below it, a little above the middle, across the whole width. When the minute changes, the time (hours, minutes and seconds as one) moves to the middle of the screen and grows for five seconds (to 5×, or 90 % of the screen if less), in a new colour each time (`showHues`: ember, violet, teal, rose, emerald, fuchsia), while the rest fades back; as it nears its largest the rest fades gradually behind a solid ground (1.8 s), which stays two seconds; then the time returns in 1.2 s while the ground clears gradually (1.8 s). | A clock the passengers read from their seats that leaves the next stop high and large, and a minute change they notice. |
| A thin outline on the minutes' digits themselves, set a little apart from them (a wide stroke in the status colour, a narrower one in the page's colour, then the digits), says how the next stop's time stands (`TimeStatus`): green (`success`) while more than 5 min are left or the trip has no time; 5 min or less left, orange (`soon`); its minute, orange beating; past, red (`danger`); 5 min or more past, red beating. The same outline is on the minutes of "NÄSTA"'s time. Where a trip was marked done sits as tiny dots fixed at its time's left, clear of the time's breathing. A tap on the clock says the time and springs the whole time out to fill the screen (a soft spring, the solid ground following), holds it four seconds, then brings it back; a tap anywhere sends it back sooner. | Driver and passengers see at a glance whether the trip is on time, without words or frames. |
| A weather app's own widget (for example Samsung Weather) can take the weather's place on the tablet (207): the app hosts it (`WeatherWidgets`, an `AppWidgetHost`; Android asks the driver once to allow it) and shows it large for the weather's moment; taps on it only bring the screen back. The weather app draws and updates it; this app never reads it. Without one, SMHI's weather is drawn. | A richer, living weather than the app's own drawing, without this app using the network or a position for it. |
| From second 45 the tablet with a map shows it for 10 s instead of the travel time alone: the map at the left (rounded, no frame or controls), the minutes and the distance beside it (Google Maps' own from its notification when it navigates, else the Routes API's). The map lies under everything, unseen until then, so it loads once. | The way itself, not just a number, without opening Google Maps. |
| In the middle of each minute (from second 27) the weather shows for 7 s: a drawn symbol (sun, cloud, fog, rain, sleet, snow, thunder), the temperature and SMHI's condition in Swedish ("Halvklart"). From second 45, while Google Maps navigates, the travel time shows the same way: a route symbol, the minutes and the distance "till nästa stopp". No frames or buttons. A tap anywhere while the time, the weather or the travel time fills the screen sends it back at once. None of them shows while something is being said, and anything said sends them back. | Calm, glanceable information for the passengers, never over an announcement. |
| The tablet speaks each announcement the phone makes when the driver taps Next (on by default; the switch 198 on the tablet's setup screen turns it off). | The tablet is where the passengers sit; the driver works everything from the phone. |
| The display is laid out for the passengers: the top line has the trip just done at the left, then the connection line and exit; the next stop fills the middle (the street and number as large as fits, the area under it); above the address at the left floats "NÄSTA" on a translucent yellow chip with a hairline yellow edge (written "NÄSTA", said "Nästa stopp") with its time in the clock's style (hours, a yellow colon, minutes) that breathes smoothly (1 → 1.2 over 2.2 s: drawn at its largest in a layer of its own and scaled down, because text scaled directly steps between glyph sizes); it takes no line of its own, so the address stays high. The display is always laid out left to right, whatever the app's language. The clock is large in the corner; the following trips are cards below (side by side in landscape; the first, "Därefter", larger and edged in the highlight colour). The first "Därefter" card is large and bold; the others are small and thin, so it stands out. Where a trip was marked done shows as small coloured check dots, no words: green in YouDrive, blue in this app. Its words for the passengers are Swedish, like the announcements; the connection line follows the app's language. The typeface is Barlow Semi Condensed (SIL OFL, bundled with its licence in `assets/licenses`), in the style of road and transit signs. | Read from the seats at a glance; narrow letters keep long street names large. |
| The link reconnects every 3 s; the phone pings every 10 s and a link silent for 30 s is dropped; lines over 64 KB close it. | Robust in a moving car. |

## 12. The interface

- The UI is **English by default**, Arabic (RTL) and Swedish are available (101–103). Explanations stay Arabic while setting 104 is on. `values/` holds the Arabic strings, `values-en/` English and `values-sv/` Swedish; every key exists in all three.
- Design system in three layers (`Palette` → `AppColors` → `AppEffects`), Day / Night / Automatic looks (130–132). No colour values outside `Palette.kt` / `AppColors.kt`. `ThemeContrastTest` checks every text/background pair (WCAG 4.5, and 3 for borders and large text).
- Colours follow YouDrive's trip cards (green pick-up, white drop-off, grey start point) with taxi yellow for the next action.
- Explanations sit behind a small "?" (`HelpDot`), never inline.
- **Reference numbers** (a small number on every control) are **hidden by default** and switched on in Settings 105. The test tags / resource-ids `ref_<n>` are always present for UI Automator.
- Maps' picture-in-picture window covers the bottom-right third of the screen, and not every system moves it for `preferKeepClear`. So on the route screen the driving buttons (Back 77, Next 78, Repeat 79) sit in the left 60 % of the bottom bar in every language; Open Maps (80) and Edit list (81) sit on the right, where the small window only hides what is not needed while it is shown. End (82) sits in the top bar.
- The passenger display's title never splits a word: its size is capped so the longest word fits on one line. Times and the clock use equal-width digits.
- Deliberately not built: calling or texting passengers (no phone numbers are stored), a traffic ETA of our own (the display takes Google Maps' own), hiding the panel automatically while driving.

## 13. Build and delivery

| Decision | Why |
|---|---|
| One `app` module. Pure logic in `core/` (no Android imports, unit-tested). A manual `AppGraph`, no DI framework. Settings in `SharedPreferences`; the route and history as `kotlinx.serialization` JSON in `noBackupFilesDir`, written atomically. Hand-written vector icons. | Small, fast to test, few dependencies. |
| **R8 is off** for release. `proguard-rules.pro` keeps ML Kit's registrars for the day it is switched on again. Every release is checked with `dexdump` for `TextRegistrar`. | R8 full mode removed the constructors ML Kit creates by reflection, and text recognition failed on every image. Tests cannot catch it: they run un-minified code. |
| Release packages only `arm64-v8a` and `armeabi-v7a`. | The OCR native library is about 11 MB per ABI; the target devices are phones and tablets. |
| The release key is kept outside the repository (`keystore.properties` → `~/.nastastopp-signing/`); Hussam holds a private backup. Every build is signed with it. | Updates install over the previous build only with the same key. |
| **The version starts at 1.0, versionCode 1.** 1.0 is installed fresh (any earlier app removed first); from then on versionCode rises by one each build. The Releases of earlier builds were deleted, so no old tag clashes with a new version. | A clean start: no data or settings from earlier builds to carry over, so the app has no migration code. |
| Builds are published as **GitHub Releases** `v<versionName>`: `tools/publish-apk.sh` pushes the APK on a short-lived branch `apk-drop/v<version>`, and `.github/workflows/publish-apk.yml` creates the Release on the source commit and deletes the branch. APKs are never committed. A wrong Release is removed with `.github/workflows/delete-release.yml` (run by hand). | The signing key never reaches GitHub, and the repository does not grow with every build. |
| Every build is tested on the S20 Ultra by a second Claude session on the driver's laptop (adb), using invented screenshots (`testdata/`). | The cloud session cannot reach the devices. |

## Tool versions

| Component | Version |
|---|---|
| Gradle wrapper | 9.8.0 (SHA-256 pinned) |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin; no `kotlin-android` plugin) |
| Kotlin | 2.4.20 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29 |
| Compose BOM | 2026.09.00 (Material 3) |
| ML Kit text-recognition (bundled model) | 16.0.1 |
| core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.0, webkit 1.17.1 | |
| kotlinx-coroutines 1.11.0 (with `-play-services` for ML Kit's `Task.await()`), kotlinx-serialization-json 1.11.0 | |
| Robolectric 4.17 (Android 13, sdk 33), JUnit 4.13.2, androidx.test core 1.7.0 / ext-junit 1.3.0 | tests only |
