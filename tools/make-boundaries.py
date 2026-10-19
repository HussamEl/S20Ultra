#!/usr/bin/env python3
"""
Makes app/src/main/assets/boundaries.json: the borders the tablet's map draws, thin and dashed,
for Värmland (17) and Örebro (18): the counties (län), the municipalities (kommuner) and SCB's
regional statistical areas (RegSO, the towns' districts). Each border is drawn once, at its
highest level.

Source: SCB's open geodata, RegSO 2025 (CC0), from its WFS. RegSO follows the county and
municipality borders, so those are the RegSO areas joined. Run by hand when SCB publishes a new
division (needs `pip install shapely`):

    python3 tools/make-boundaries.py [regso.json]

Without a file it fetches the areas from SCB's WFS.
"""
import json
import sys
import urllib.parse
import urllib.request

from shapely.geometry import shape, MultiLineString, LineString
from shapely.ops import unary_union, linemerge

WFS = "https://geodata.scb.se/geoserver/stat/wfs"
COUNTIES = ("17", "18")
# Douglas–Peucker tolerance in degrees (about 15–25 m here): finer than any street the map shows
# at the zooms where a district border matters.
TOLERANCE = 0.0002
# Pieces shorter than this (degrees) are slivers between two sources' borders, not borders.
MIN_LENGTH = 0.0006
OUT = "app/src/main/assets/boundaries.json"


def fetch():
    query = urllib.parse.urlencode({
        "service": "WFS", "request": "GetFeature", "version": "1.1.0",
        "typeName": "stat:RegSO_2025", "outputFormat": "application/json", "srsName": "EPSG:4326",
        "CQL_FILTER": "lanskod IN (%s)" % ",".join("'%s'" % c for c in COUNTIES),
    })
    request = urllib.request.Request(WFS + "?" + query, headers={"User-Agent": "NastaStopp-build"})
    with urllib.request.urlopen(request, timeout=300) as answer:
        return json.load(answer)


def lines(geometry):
    """The line work of a (multi)line geometry, as lists of points."""
    if geometry.is_empty:
        return []
    parts = geometry.geoms if hasattr(geometry, "geoms") else [geometry]
    out = []
    for part in parts:
        if isinstance(part, LineString):
            out.append(part)
        elif hasattr(part, "geoms"):
            out.extend(p for p in part.geoms if isinstance(p, LineString))
    return out


def tidy(geometry):
    merged = linemerge(MultiLineString(lines(geometry))) if lines(geometry) else geometry
    result = []
    for line in lines(merged):
        line = line.simplify(TOLERANCE, preserve_topology=False)
        if line.length < MIN_LENGTH:
            continue
        flat = []
        for lng, lat in line.coords:
            flat += [round(lat, 4), round(lng, 4)]
        result.append(flat)
    return result


def main():
    data = json.load(open(sys.argv[1])) if len(sys.argv) > 1 else fetch()
    areas = [(f["properties"], shape(f["geometry"]).buffer(0)) for f in data["features"]
             if f["properties"]["lanskod"] in COUNTIES]
    by_county, by_town = {}, {}
    for props, area in areas:
        by_county.setdefault(props["lanskod"], []).append(area)
        by_town.setdefault(props["kommunkod"], []).append(area)
    county = unary_union([unary_union(a).boundary for a in by_county.values()])
    town = unary_union([unary_union(a).boundary for a in by_town.values()])
    district = unary_union([a.boundary for _, a in areas])
    # A little wider than the simplification, so a border is not drawn again one level down.
    near = TOLERANCE / 2
    town_only = town.difference(county.buffer(near))
    district_only = district.difference(town.buffer(near))
    out = {
        "source": "SCB, RegSO 2025 (CC0); counties 17 and 18",
        "county": tidy(county),
        "town": tidy(town_only),
        "district": tidy(district_only),
    }
    with open(OUT, "w") as f:
        json.dump(out, f, separators=(",", ":"))
    points = sum(len(l) // 2 for k in ("county", "town", "district") for l in out[k])
    print("%s: %d county, %d town, %d district lines, %d points" % (OUT, len(out["county"]), len(out["town"]), len(out["district"]), points))


if __name__ == "__main__":
    main()
