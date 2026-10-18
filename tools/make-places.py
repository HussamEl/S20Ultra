#!/usr/bin/env python3
"""
Makes app/src/main/assets/places.txt, the place-name part of the app's own address archive: the
places a trip can go to in Värmland's and Örebro's municipalities (villages, farms and other
settlements, towns, churches, facilities) from Lantmäteriet's place names ("Ortnamn", CC BY 4.0),
so the app finds a place written by its name ("Glava, Arvika") on the phone, after the address
register and before Android's geocoder. Names of water, terrain and tracts are left out.

The file is the GeoPackage the driver brings (ortnamn_se.gpkg, inside its zip). Each name keeps its
municipality and its points, turned from SWEREF 99 TM (EPSG:3006) into WGS 84 with 5 decimals; points
of one name within a kilometre of each other are one place. Where a town (a "tätort") has the name,
the town is the place (Molkom, not the farm Molkom beside it). A name of two or more places in one
municipality (22 farms called Haget in Arvika) keeps each place's point: the app takes it alone
only when it is one place, and a farm written after its village ("Provby Provgården") only near that
village, never chosen between. No names of people. Run by hand when the driver brings a newer file (needs
`pip install pyproj`):

    python3 tools/make-places.py ortnamn_se.gpkg

The file has a line per name of a municipality, with one point per place of that name:
    <name>|<municipality>|<lat>:<lng>[;<lat>:<lng>…]
"""
import math
import sqlite3
import struct
import sys
from collections import defaultdict

from pyproj import Transformer

OUT = "app/src/main/assets/places.txt"
TO_DEGREES = Transformer.from_crs(3006, 4326, always_xy=True)

# The municipalities of Värmlands län (17) and Örebro län (18), by their codes, named as the
# address register names them.
MUNICIPALITIES = {
    "1715": "Kil", "1730": "Eda", "1737": "Torsby", "1760": "Storfors", "1761": "Hammarö",
    "1762": "Munkfors", "1763": "Forshaga", "1764": "Grums", "1765": "Årjäng", "1766": "Sunne",
    "1780": "Karlstad", "1781": "Kristinehamn", "1782": "Filipstad", "1783": "Hagfors",
    "1784": "Arvika", "1785": "Säffle",
    "1814": "Lekeberg", "1860": "Laxå", "1861": "Hallsberg", "1862": "Degerfors", "1863": "Hällefors",
    "1864": "Ljusnarsberg", "1880": "Örebro", "1881": "Kumla", "1882": "Askersund", "1883": "Karlskoga",
    "1884": "Nora", "1885": "Lindesberg",
}

# Lantmäteriet's kinds of name a trip can go to: settlements (a village, a farm, a house), towns,
# churches and facilities (a school, a station, a works).
TOWN = "BEBTÄTTX"
KINDS = ("BEBTX", TOWN, "KYRKATX", "ANLTX")

# One name's points lie this near each other to be one place.
SAME_PLACE_M = 1000.0


def point(blob):
    """The x and y of a GeoPackage point (its header, then the WKB point)."""
    flags = blob[3]
    envelope = (0, 32, 48, 48, 64)[(flags >> 1) & 7]
    wkb = blob[8 + envelope:]
    little = wkb[0] == 1
    x, y = struct.unpack("<dd" if little else ">dd", wkb[5:21])
    return x, y


def main(path):
    db = sqlite3.connect(path)
    marks = ",".join("?" * len(KINDS))
    rows = db.execute(
        f"SELECT ortnamn, kommunkod, detaljtyp, geom FROM ortnamn WHERE detaljtyp IN ({marks})",
        KINDS,
    )
    places = defaultdict(list)
    for name, code, kind, geom in rows:
        municipality = MUNICIPALITIES.get(code)
        name = (name or "").strip()
        if municipality is None or not name or "|" in name or geom is None:
            continue
        places[(name, municipality)].append((kind, *point(geom)))

    single = several = 0
    lines = ["# Lantmäteriet, Ortnamn (CC BY 4.0). name|municipality|lat:lng[;lat:lng…] (one point per place of the name)"]
    for (name, municipality), found in sorted(places.items()):
        towns = [(x, y) for kind, x, y in found if kind == TOWN]
        spots = places_of(towns or [(x, y) for _, x, y in found])
        written = ";".join(f"{lat:.5f}:{lng:.5f}" for lng, lat in (TO_DEGREES.transform(x, y) for x, y in spots))
        lines.append(f"{name}|{municipality}|{written}")
        if len(spots) == 1:
            single += 1
        else:
            several += 1
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    print(f"{single} names of one place, {several} names of several places in one municipality")


def places_of(xys):
    """The places a name's points make: points within SAME_PLACE_M of a place's first one are that place (their middle)."""
    groups = []
    for x, y in xys:
        for g in groups:
            if math.hypot(x - g[0][0], y - g[0][1]) <= SAME_PLACE_M:
                g.append((x, y))
                break
        else:
            groups.append([(x, y)])
    return [(sum(p[0] for p in g) / len(g), sum(p[1] for p in g) / len(g)) for g in groups]


if __name__ == "__main__":
    main(sys.argv[1])
