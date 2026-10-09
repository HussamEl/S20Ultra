#!/usr/bin/env python3
"""
Makes app/src/main/assets/addresses.txt, the address part of the app's own address archive: every
address of the municipalities given (Värmland's 16 and Örebro's 12), from Lantmäteriet's address register ("Belägenhetsadresser",
CC BY 4.0), so the app finds an address on the phone, before Android's geocoder, and finds the
ones the geocoder does not know (village addresses like "Nolby 503", new streets). It also adds
the register's street names to the districts' streets in app/src/main/assets/karlstad_districts.json
(where an address on the street lies in the district), so the archive's streets are the register's
as well as OpenStreetMap's.

Each municipality is one GeoPackage from Geotorget (belagenhetsadresser_kn<code>.gpkg, inside its
zip). Addresses in force ("Gällande") and reserved for new buildings ("Reserverad") are kept: the
street (or the village's address area), the number and letter, the postcode, the post town and the
municipality, the point turned from SWEREF 99 TM (EPSG:3006) into WGS 84 with 5 decimals (about a
metre), and the place's popular name where it has one (a farm, a school, a church, a harbour). A
farm's address is kept under the farm's own name ("Väststugan 1"); an address written twice with
two points (one farm name in two villages of one postcode) is left out, never chosen. No names of
people, no property references. Run by hand when the driver brings newer files (needs
`pip install pyproj`):

    python3 tools/make-addresses.py belagenhetsadresser_kn17*.gpkg belagenhetsadresser_kn18*.gpkg

The file has a line per street of a post town, and a line per popular name of a post town:
    <street>|<post town>|<postcode>|<municipality>|<number><letter>:<lat>:<lng>;…
    @<popular name>|<post town>|<postcode>|<municipality>|<lat>:<lng>:<street> <number><letter>;…
"""
import json
import sqlite3
import struct
import sys
from collections import defaultdict

from pyproj import Transformer

OUT = "app/src/main/assets/addresses.txt"
ARCHIVE = "app/src/main/assets/karlstad_districts.json"
TO_DEGREES = Transformer.from_crs(3006, 4326, always_xy=True)
SOURCE = ("Districts: Karlstads kommun, Stadsdelar (CC0). Street names: © OpenStreetMap contributors (ODbL) "
          "and Lantmäteriet, Belägenhetsadresser (CC BY 4.0).")


def point(blob):
    """The x and y of a GeoPackage point (its header, then the WKB point)."""
    flags = blob[3]
    envelope = (0, 32, 48, 48, 64)[(flags >> 1) & 7]
    wkb = blob[8 + envelope:]
    little = wkb[0] == 1
    x, y = struct.unpack("<dd" if little else ">dd", wkb[5:21])
    return x, y


def inside(rings, lng, lat):
    """Even–odd over every ring (holes left out), as the app's Districts does."""
    hit = False
    for r in rings:
        j = len(r) - 2
        for i in range(0, len(r), 2):
            xi, yi, xj, yj = r[i], r[i + 1], r[j], r[j + 1]
            if (yi > lat) != (yj > lat) and lng < (xj - xi) * (lat - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
    return hit


def add_to_archive(street_points):
    """The register's streets into the districts where an address on them lies."""
    with open(ARCHIVE, encoding="utf-8") as f:
        archive = json.load(f)
    added = 0
    for d in archive["districts"]:
        rings = d["rings"]
        xs = [v for r in rings for v in r[0::2]]
        ys = [v for r in rings for v in r[1::2]]
        box = (min(xs), max(xs), min(ys), max(ys))
        known = set(d["streets"])
        # The same street written in another case by OpenStreetMap is the same street.
        folded = {s.casefold() for s in known}
        for street, points in street_points.items():
            if street.casefold() in folded:
                continue
            if any(box[0] <= lng <= box[1] and box[2] <= lat <= box[3] and inside(rings, lng, lat) for lat, lng in points):
                known.add(street)
                folded.add(street.casefold())
                added += 1
        d["streets"] = sorted(known)
    archive["source"] = SOURCE
    with open(ARCHIVE, "w", encoding="utf-8") as f:
        json.dump(archive, f, ensure_ascii=False, separators=(",", ":"))
    print(ARCHIVE, added, "street names added from the register")


def main():
    streets = defaultdict(dict)
    places = defaultdict(list)
    street_points = defaultdict(list)
    twice = set()
    count = 0
    for path in sys.argv[1:]:
        db = sqlite3.connect(path)
        rows = db.execute(
            "select adressomrade_faststalltnamn, adressomradestyp, gardsadressomrade_faststalltnamn, adressplatsnummer, "
            "bokstavstillagg, postnummer, postort, kommunnamn, popularnamn, adressplatspunkt from belagenhetsadress "
            "where statusforbelagenhetsadress in ('Gällande', 'Reserverad') and adressplatsnummer is not null"
        )
        for area, kind, farm, number, letter, postcode, town, municipality, popular, blob in rows:
            if not area or not blob:
                continue
            x, y = point(blob)
            lng, lat = TO_DEGREES.transform(x, y)
            lat, lng = round(lat, 5), round(lng, 5)
            # A farm's address is written by the farm's own name ("Väststugan 1", in the village
            # Ambjörby): the village's own number 1 is another house.
            street = (farm or area).strip()
            house = f"{number.strip()}{(letter or '').strip().upper()}"
            key = (street, (town or "").strip(), str(postcode or ""), (municipality or "").strip())
            known = streets[key].get(house)
            if known is not None and known != (lat, lng):
                # Written twice with two points: neither is taken (a house is never guessed).
                twice.add((key, house))
            streets[key][house] = (lat, lng)
            if popular and popular.strip():
                places[(popular.strip(),) + key[1:]].append((lat, lng, f"{street} {house}"))
            if kind == "Gatuadressområde" and not farm and (municipality or "").strip() == "Karlstad":
                street_points[street].append((lat, lng))
            count += 1
    for key, house in twice:
        streets[key].pop(house, None)
    streets = {k: v for k, v in streets.items() if v}
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("# Lantmäteriet, Belägenhetsadresser (CC BY 4.0). street|post town|postcode|municipality|number:lat:lng;…"
                  " — @popular name|post town|postcode|municipality|lat:lng:street number;…\n")
        for (street, town, postcode, municipality), spots in sorted(streets.items()):
            line = ";".join(f"{n}:{lat}:{lng}" for n, (lat, lng) in sorted(spots.items(), key=lambda p: (len(p[0]), p[0])))
            out.write(f"{street}|{town}|{postcode}|{municipality}|{line}\n")
        for (name, town, postcode, municipality), spots in sorted(places.items()):
            line = ";".join(f"{lat}:{lng}:{address}" for lat, lng, address in sorted(spots, key=lambda s: s[2]))
            out.write(f"@{name}|{town}|{postcode}|{municipality}|{line}\n")
    print(OUT, count, "addresses on", len(streets), "streets,", len(places), "popular names,", len(twice), "written twice left out")
    if street_points:
        add_to_archive(street_points)


if __name__ == "__main__":
    main()
