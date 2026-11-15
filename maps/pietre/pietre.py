#!/usr/bin/env python3
"""Prepara il file delle pietre miliari di un paese per Eurise Mappe («Sei su A51 · km 12,4»).

Uso: pietre.py ESTRATTO.osm.pbf USCITA.ekm.gz

L'estratto è già ridotto da `osmium tags-filter` alle pietre miliari (`highway=milestone`) e alle strade con un
riferimento (autostrade … strade terziarie). Ogni pietra prende la sigla della strada più vicina entro 40 m (tutte le
sigle, «A1;E35»); quelle senza strada vicina e senza una sigla propria si scartano, perché il telefono non saprebbe a
quale strada appartengono.

Formato (numeri big-endian, come li legge `DataInputStream`), poi compresso con gzip:
  «EKM1», int32 data dei dati (AAAAMMGG), int32 numero di pietre, uint16 numero di sigle,
  sigle: per ognuna uint16 lunghezza + UTF-8 («A1;E35», maiuscole, senza spazi),
  pietre ordinate per latitudine: int32 lat × 1e6, int32 lon × 1e6, int32 metri, uint16 indice della sigla.
Il calcolo del km sul telefono è in maps/android/…/eurise/MilestoneKm.java.
"""

import datetime
import gzip
import math
import re
import struct
import sys

import osmium

ROADS = {"motorway", "trunk", "primary", "secondary", "tertiary"}
CELL = 0.005  # gradi, circa 500 m
MATCH_M = 40.0
MARGIN = 0.0005  # gradi di latitudine, circa 55 m


def parse_km(value):
    """«12.4», «12,4», «km 12», «12+400», «12400 m» → chilometri; None se non si capisce."""
    if not value:
        return None
    v = value.strip().lower().replace("km", "").replace(" ", "")
    m = re.fullmatch(r"(\d+)\+(\d{1,3})", v)
    if m:
        return int(m.group(1)) + int(m.group(2).ljust(3, "0")) / 1000.0
    meters = v.endswith("m")
    v = v.rstrip("m").replace(",", ".")
    try:
        km = float(v)
    except ValueError:
        return None
    if meters:
        km /= 1000.0
    if not (0 <= km < 5000) or math.isnan(km):
        return None
    return km


def norm_refs(ref):
    parts = [p.strip().upper().replace(" ", "") for p in re.split(r"[;,]", ref or "")]
    return ";".join(p for p in parts if p)


def cell(lat, lon):
    return (int(math.floor(lat / CELL)), int(math.floor(lon / CELL)))


def seg_dist_m(plat, plon, alat, alon, blat, blon):
    k = math.cos(math.radians(plat))
    ax, ay = (alon - plon) * k * 111_320.0, (alat - plat) * 111_320.0
    bx, by = (blon - plon) * k * 111_320.0, (blat - plat) * 111_320.0
    dx, dy = bx - ax, by - ay
    ll = dx * dx + dy * dy
    t = 0.0 if ll == 0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / ll))
    x, y = ax + t * dx, ay + t * dy
    return math.hypot(x, y)


class Reader(osmium.SimpleHandler):
    """Nei .pbf i nodi vengono prima delle vie: quando arrivano le vie le pietre sono già tutte note."""

    def __init__(self):
        super().__init__()
        self.stones = []  # (lat, lon, km, sigla propria)
        self.cells = set()
        self.segments = {}  # cella → [(alat, alon, blat, blon, sigle)]

    def node(self, n):
        t = n.tags
        if t.get("highway") != "milestone" or "railway" in t or "waterway" in t:
            return
        km = parse_km(t.get("distance")) if "distance" in t else parse_km(t.get("pk"))
        if km is None or not n.location.valid():
            return
        lat, lon = n.location.lat, n.location.lon
        self.stones.append((lat, lon, km, norm_refs(t.get("highway:ref") or t.get("road_ref") or "")))
        self.cells.add(cell(lat, lon))

    def way(self, w):
        if w.tags.get("highway") not in ROADS:
            return
        refs = norm_refs(w.tags.get("ref"))
        if not refs:
            return
        pts = [(nd.location.lat, nd.location.lon) for nd in w.nodes if nd.location.valid()]
        for (alat, alon), (blat, blon) in zip(pts, pts[1:]):
            # Allargato di poco più di 40 m: una pietra appena oltre il bordo della cella trova lo stesso la strada.
            i0, j0 = cell(min(alat, blat) - MARGIN, min(alon, blon) - 2 * MARGIN)
            i1, j1 = cell(max(alat, blat) + MARGIN, max(alon, blon) + 2 * MARGIN)
            if (i1 - i0 + 1) * (j1 - j0 + 1) > 400:
                continue
            for i in range(i0, i1 + 1):
                for j in range(j0, j1 + 1):
                    if (i, j) in self.cells:
                        self.segments.setdefault((i, j), []).append((alat, alon, blat, blon, refs))


def nearest_refs(r, lat, lon):
    best, best_d = None, MATCH_M
    for seg in r.segments.get(cell(lat, lon), ()):
        d = seg_dist_m(lat, lon, *seg[:4])
        if d < best_d:
            best, best_d = seg[4], d
    return best


def build(pbf, out, date=None):
    r = Reader()
    r.apply_file(pbf, locations=True)
    refs, index, rows = [], {}, set()
    unmatched = 0
    for lat, lon, km, own in r.stones:
        ref = nearest_refs(r, lat, lon) or own
        if not ref:
            unmatched += 1
            continue
        if ref not in index:
            if len(refs) >= 65535:
                continue
            index[ref] = len(refs)
            refs.append(ref)
        rows.add((round(lat * 1e6), round(lon * 1e6), round(km * 1000), index[ref]))
    rows = sorted(rows)
    date = date or int(datetime.date.today().strftime("%Y%m%d"))
    body = bytearray(b"EKM1")
    body += struct.pack(">iiH", date, len(rows), len(refs))
    for ref in refs:
        b = ref.encode("utf-8")
        body += struct.pack(">H", len(b)) + b
    for row in rows:
        body += struct.pack(">iiiH", *row)
    with gzip.open(out, "wb", compresslevel=9) as f:
        f.write(body)
    print(f"{out}: {len(rows)} pietre, {len(refs)} sigle, {unmatched} senza strada vicina (scartate)")
    return len(rows)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2])
