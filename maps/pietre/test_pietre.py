"""Prove di pietre.py su un estratto finto: lettura del km, sigla dalla strada vicina, formato del file."""

import gzip
import os
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pietre  # noqa: E402

OSM = """<?xml version='1.0' encoding='UTF-8'?>
<osm version="0.6">
  <node id="1" lat="45.0000" lon="9.0000" version="1"/>
  <node id="2" lat="45.0000" lon="9.1000" version="1"/>
  <node id="3" lat="45.2000" lon="9.0000" version="1"/>
  <node id="4" lat="45.2000" lon="9.1000" version="1"/>
  <node id="10" lat="45.0002" lon="9.0100" version="1">
    <tag k="highway" v="milestone"/><tag k="distance" v="12.4"/></node>
  <node id="11" lat="44.9998" lon="9.0200" version="1">
    <tag k="highway" v="milestone"/><tag k="distance" v="13+200"/></node>
  <node id="12" lat="45.2002" lon="9.0500" version="1">
    <tag k="highway" v="milestone"/><tag k="distance" v="7,5"/></node>
  <node id="13" lat="45.1000" lon="9.0500" version="1">
    <tag k="highway" v="milestone"/><tag k="distance" v="3"/></node>
  <node id="14" lat="45.0001" lon="9.0300" version="1">
    <tag k="highway" v="milestone"/><tag k="railway" v="milestone"/><tag k="distance" v="99"/></node>
  <way id="100" version="1"><nd ref="1"/><nd ref="2"/>
    <tag k="highway" v="motorway"/><tag k="ref" v="A1; E 35"/></way>
  <way id="101" version="1"><nd ref="3"/><nd ref="4"/>
    <tag k="highway" v="secondary"/><tag k="ref" v="SP 12"/></way>
</osm>
"""


class PietreTest(unittest.TestCase):
    def test_parse_km(self):
        self.assertEqual(pietre.parse_km("12.4"), 12.4)
        self.assertEqual(pietre.parse_km("12,4"), 12.4)
        self.assertEqual(pietre.parse_km("km 12"), 12.0)
        self.assertEqual(pietre.parse_km("12+400"), 12.4)
        self.assertEqual(pietre.parse_km("12+4"), 12.4)
        self.assertEqual(pietre.parse_km("12400 m"), 12.4)
        self.assertIsNone(pietre.parse_km("A"))
        self.assertIsNone(pietre.parse_km(""))

    def test_file(self):
        with tempfile.TemporaryDirectory() as d:
            src = os.path.join(d, "t.osm")
            out = os.path.join(d, "t.ekm.gz")
            with open(src, "w") as f:
                f.write(OSM)
            self.assertEqual(pietre.build(src, out, date=20261115), 3)
            data = gzip.open(out).read()
            self.assertEqual(data[:4], b"EKM1")
            date, count, nrefs = struct.unpack_from(">iiH", data, 4)
            self.assertEqual((date, count, nrefs), (20261115, 3, 2))
            pos, refs = 14, []
            for _ in range(nrefs):
                (n,) = struct.unpack_from(">H", data, pos)
                refs.append(data[pos + 2:pos + 2 + n].decode())
                pos += 2 + n
            self.assertEqual(sorted(refs), ["A1;E35", "SP12"])
            rows = [struct.unpack_from(">iiiH", data, pos + 14 * i) for i in range(count)]
            self.assertEqual([r[0] for r in rows], sorted(r[0] for r in rows))
            got = {(r[2], refs[r[3]]) for r in rows}
            # La pietra a metà strada (id 13) è lontana da tutte e due: scartata; quella ferroviaria pure.
            self.assertEqual(got, {(12400, "A1;E35"), (13200, "A1;E35"), (7500, "SP12")})


if __name__ == "__main__":
    unittest.main()
