# Test data (invented — no real passengers)

`screenshots/` holds dispatch-list screenshots (1080×2400) for testing the import on a real phone:
- The names, phone numbers and addresses are invented.
- The images are drawn by `ScreenshotsRoboTest.deviceFixtures` from the lines in `app/src/test/.../core/parse/DeviceFixtures.kt`.
- `DeviceFixturesTest` asserts what the parser reads from those lines.

| File | Expected stops after import (time · address; names and phone numbers dropped) |
|---|---|
| `fixture_time_above.png` | 07:30 Storgatan 14, 652 24 Karlstad · 08:05 Järnvägsgatan 3B, 688 30 Storfors · 08:40 Lindvägen 9, 664 30 Grums · 09:15 Kyrkogatan 2, 652 24 Karlstad |
| `fixture_same_line.png` | 10:20 Skolgatan 5, 664 30 Grums · 10:45 Västra Torggatan 12, 652 24 Karlstad · 11:10 Hamngatan 7, 663 30 Skoghall · 11:35 Kungsgatan 22, 681 31 Kristinehamn |

To put them on the phone (the system photo picker indexes files under `Pictures/`):
```
adb push testdata/screenshots/fixture_time_above.png /sdcard/Pictures/NastaStoppTest/
adb push testdata/screenshots/fixture_same_line.png /sdcard/Pictures/NastaStoppTest/
```
