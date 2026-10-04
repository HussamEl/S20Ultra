#!/usr/bin/env python3
"""
Makes app/src/main/assets/karlstad_districts.json: Karlstad's districts (stadsdelar) and the
names of the streets in each, the start of the app's own address archive. Street names only:
no house or building numbers, no names of people.

Sources:
  - the districts: Karlstads kommun's open data "Stadsdelar" (CC0), from its WFS, in SWEREF 99 TM;
  - the street names: OpenStreetMap (ODbL), from an extract of Värmland
    (https://download.openstreetmap.fr/extracts/europe/sweden/varmland-latest.osm.pbf).

A street belongs to a district when a twentieth of its length, or 150 m of it, lies there, or an
address on it does. Run by hand when the town changes (needs `pip install osmium shapely pyproj`):

    python3 tools/make-districts.py varmland-latest.osm.pbf [stadsdelar.json]
"""
import json
import sys
import urllib.parse
import urllib.request
from collections import defaultdict

import osmium
from pyproj import Transformer
from shapely import make_valid
from shapely.geometry import shape, LineString, Point, box
from shapely.strtree import STRtree

WFS = "https://gi.karlstad.se/geoserver/oppnadata/ows"
OUT = "app/src/main/assets/karlstad_districts.json"
# Douglas–Peucker tolerance in degrees (about 3 m): well inside a house's plot.
TOLERANCE = 0.00003
SHARE = 0.05
LENGTH_M = 150
# Names written in capitals by the source, as Karlstad writes them.
WRITTEN = {"YTTRE HAMN": "Yttre hamn"}


def fetch():
    query = urllib.parse.urlencode({
        "service": "WFS", "version": "1.0.0", "request": "GetFeature",
        "typeName": "oppnadata:karlstad_stadsdelar", "outputFormat": "application/json",
    })
    request = urllib.request.Request(WFS + "?" + query, headers={"User-Agent": "NastaStopp-build"})
    with urllib.request.urlopen(request, timeout=300) as answer:
        return json.load(answer)


def to_degrees(data):
    """The features in WGS 84 (the WFS rounds degrees to 0.01, so it is asked in metres)."""
    t = Transformer.from_crs(3006, 4326, always_xy=True)

    def conv(c):
        if isinstance(c[0], (int, float)):
            return list(t.transform(c[0], c[1]))
        return [conv(i) for i in c]
    for f in data["features"]:
        f["geometry"]["coordinates"] = conv(f["geometry"]["coordinates"])
    return data


def rings(geometry):
    polygons = geometry.geoms if hasattr(geometry, "geoms") else [geometry]
    out = []
    for p in polygons:
        if p.geom_type != "Polygon":
            continue
        for ring in [p.exterior, *p.interiors]:
            out.append([round(v, 6) for xy in ring.coords for v in xy])
    return out


def main():
    pbf = sys.argv[1]
    data = to_degrees(json.load(open(sys.argv[2])) if len(sys.argv) > 2 else fetch())
    areas = []
    for f in data["features"]:
        raw = f["properties"]["namn"].strip()
        areas.append((WRITTEN.get(raw, raw.title()), make_valid(shape(f["geometry"]))))
    geoms = [g for _, g in areas]
    tree = STRtree(geoms)
    bounds = [g.bounds for g in geoms]
    whole = box(min(b[0] for b in bounds), min(b[1] for b in bounds), max(b[2] for b in bounds), max(b[3] for b in bounds))
    metres = 111_320 * 0.51  # one degree of longitude here; latitude is scaled to it below

    length = defaultdict(lambda: defaultdict(float))
    addressed = defaultdict(set)

    def place(street, p):
        if not whole.contains(p):
            return
        for i in tree.query(p):
            if geoms[i].contains(p):
                addressed[street].add(areas[i][0])

    class Handler(osmium.SimpleHandler):
        def way(self, w):
            street = w.tags.get("addr:street")
            if street and w.tags.get("addr:housenumber"):
                try:
                    xs = [(n.lon, n.lat) for n in w.nodes]
                    place(street, Point(sum(x for x, _ in xs) / len(xs), sum(y for _, y in xs) / len(xs)))
                except Exception:
                    pass
            name = w.tags.get("name")
            if not name or "highway" not in w.tags:
                return
            try:
                line = LineString([(n.lon, n.lat) for n in w.nodes])
            except Exception:
                return
            if not whole.intersects(line):
                return
            for i in tree.query(line):
                part = geoms[i].intersection(line)
                if not part.is_empty:
                    length[name][areas[i][0]] += part.length

        def node(self, n):
            street = n.tags.get("addr:street")
            if street and n.tags.get("addr:housenumber"):
                place(street, Point(n.location.lon, n.location.lat))

    Handler().apply_file(pbf, locations=True)

    streets = defaultdict(set)
    for name, parts in length.items():
        total = sum(parts.values())
        for area, l in parts.items():
            if l / total >= SHARE or l * metres >= LENGTH_M:
                streets[area].add(name)
    for name, found in addressed.items():
        for area in found:
            streets[area].add(name)

    out = {
        "source": "Districts: Karlstads kommun, Stadsdelar (CC0). Street names: © OpenStreetMap contributors (ODbL).",
        "districts": [],
    }
    for name, g in sorted(areas, key=lambda a: a[0]):
        simple = g.simplify(TOLERANCE, preserve_topology=True)
        out["districts"].append({
            "name": name,
            "rings": rings(simple),
            "streets": sorted(streets[name]),
        })
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(OUT, len(out["districts"]), "districts,", sum(len(d["streets"]) for d in out["districts"]), "street names")


if __name__ == "__main__":
    main()
