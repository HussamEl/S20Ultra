# Test data (invented — no real passengers)

`screenshots/` holds dispatch-list screenshots (1080×2400) for testing the import on a real phone:
- The names, phone numbers and addresses are invented.
- The images are drawn by `ScreenshotsRoboTest.deviceFixtures` from the lines in `app/src/test/.../core/parse/DeviceFixtures.kt`.
- `DeviceFixturesTest` asserts what the parser reads from those lines.

| File | Expected stops after import (time · address; names and phone numbers dropped) |
|---|---|
| `fixture_time_above.png` | 07:30 Storgatan 14, 652 24 Karlstad · 08:05 Järnvägsgatan 3B, 688 30 Storfors · 08:40 Lindvägen 9, 664 30 Grums · 09:15 Kyrkogatan 2, 652 24 Karlstad |
| `fixture_same_line.png` | 10:20 Skolgatan 5, 664 30 Grums · 10:45 Västra Torggatan 12, 652 24 Karlstad · 11:10 Hamngatan 7, 663 30 Skoghall · 11:35 Kungsgatan 22, 681 31 Kristinehamn |
| `fixture_prefixes.png` | 08:10 Östra Storgatan 3, 652 24 Karlstad · 08:30 Norra allén 4, 652 25 Karlstad · 08:50 Södra Kyrkogatan 7, 681 30 Kristinehamn · 09:10 S:t Olofsgatan 2, 652 24 Karlstad · 09:30 Stora torget 1, 652 25 Karlstad · 09:50 Lilla Badhusgatan 5, 652 25 Karlstad · 10:10 Gamla Kyrkogatan 12, 664 30 Grums · 10:30 Övre Torggatan 8, 652 24 Karlstad · 10:50 Västra Skolgatan 9, 663 30 Skoghall · 11:10 Norra Strandvägen 21, 681 31 Kristinehamn |

`fixture_prefixes.png`:
- The last two rows are split after the prefix ("10:50  Västra" on one line, "Skolgatan 9, …" on the next), as ML Kit once split a row on the phone. The app must put the prefix back.
- "Norra allén" and "Stora torget" are written in lower case after the prefix, which is the Swedish style.

## All invented terms (for the privacy log search)
None of these may appear in logcat from the app, or anywhere in the app except the addresses it shows.
- **Names:** Anna Testsson, Bengt Provare, Cecilia Exempel, David Demo, Erik Påhittad, Frida Uppdiktad
- **Phone numbers:** 070-000 00 01, 070-000 00 02, 070-000 00 04, 070-000 00 05, 070-000 00 06
- **Streets:** Storgatan, Järnvägsgatan, Lindvägen, Kyrkogatan, Skolgatan, Västra Torggatan, Torggatan, Hamngatan, Kungsgatan, Östra Storgatan, Norra allén, Södra Kyrkogatan, S:t Olofsgatan, Stora torget, Lilla Badhusgatan, Gamla Kyrkogatan, Övre Torggatan, Strandvägen
- **Postcodes:** 652 24, 652 25, 688 30, 664 30, 663 30, 681 30, 681 31

## Floating panel positions (UI Automator cannot see overlay windows)
```
adb shell setprop log.tag.NastaStoppRefs DEBUG
adb logcat -s NastaStoppRefs
```
Every layout or move of the panel then logs `ref_<n>=[left,top][right,bottom]` in screen pixels for the visible parts 1–17. It logs ids and bounds only, never stop data. Turn it off with `adb shell setprop log.tag.NastaStoppRefs ""`.

To put them on the phone (the system photo picker indexes files under `Pictures/`):
```
adb push testdata/screenshots/fixture_time_above.png /sdcard/Pictures/NastaStoppTest/
adb push testdata/screenshots/fixture_same_line.png /sdcard/Pictures/NastaStoppTest/
adb push testdata/screenshots/fixture_prefixes.png /sdcard/Pictures/NastaStoppTest/
```
