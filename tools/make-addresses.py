#!/usr/bin/env python3
"""
Makes app/src/main/assets/addresses.txt: every address of the municipalities given, from
Lantmäteriet's address register ("Belägenhetsadresser", CC BY 4.0), so the app finds an address
on the phone, before Android's geocoder, and finds the ones the geocoder does not know.

Each municipality is one GeoPackage from Geotorget (belagenhetsadresser_kn<code>.gpkg, inside its
zip). Only addresses in force ("Gällande") are kept: the street (or the village's address area),
the number and letter, the postcode, the post town and the municipality, and the point turned
from SWEREF 99 TM (EPSG:3006) into WGS 84 with 5 decimals (about a metre). No names of people,
no property references. Run by hand when the driver brings newer files (needs `pip install pyproj`):

    python3 tools/make-addresses.py belagenhetsadresser_kn1780.gpkg [more.gpkg …]

The file has a line per street of a post town:
    <street>|<post town>|<postcode>|<municipality>|<number><letter>:<lat>:<lng>;…
"""
import sqlite3
import struct
import sys
from collections import defaultdict

from pyproj import Transformer

OUT = "app/src/main/assets/addresses.txt"
TO_DEGREES = Transformer.from_crs(3006, 4326, always_xy=True)


def point(blob):
    """The x and y of a GeoPackage point (its header, then the WKB point)."""
    flags = blob[3]
    envelope = (0, 32, 48, 48, 64)[(flags >> 1) & 7]
    wkb = blob[8 + envelope:]
    little = wkb[0] == 1
    x, y = struct.unpack("<dd" if little else ">dd", wkb[5:21])
    return x, y


def main():
    streets = defaultdict(dict)
    count = 0
    for path in sys.argv[1:]:
        db = sqlite3.connect(path)
        rows = db.execute(
            "select adressomrade_faststalltnamn, adressplatsnummer, bokstavstillagg, postnummer, postort, "
            "kommunnamn, adressplatspunkt from belagenhetsadress "
            "where statusforbelagenhetsadress = 'Gällande' and adressplatsnummer is not null"
        )
        for street, number, letter, postcode, town, municipality, blob in rows:
            if not street or not blob:
                continue
            x, y = point(blob)
            lng, lat = TO_DEGREES.transform(x, y)
            key = (street.strip(), (town or "").strip(), str(postcode or ""), (municipality or "").strip())
            streets[key][f"{number.strip()}{(letter or '').strip().upper()}"] = (round(lat, 5), round(lng, 5))
            count += 1
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("# Lantmäteriet, Belägenhetsadresser (CC BY 4.0). street|post town|postcode|municipality|number:lat:lng;…\n")
        for (street, town, postcode, municipality), places in sorted(streets.items()):
            spots = ";".join(f"{n}:{lat}:{lng}" for n, (lat, lng) in sorted(places.items(), key=lambda p: (len(p[0]), p[0])))
            out.write(f"{street}|{town}|{postcode}|{municipality}|{spots}\n")
    print(OUT, count, "addresses on", len(streets), "streets")


if __name__ == "__main__":
    main()
