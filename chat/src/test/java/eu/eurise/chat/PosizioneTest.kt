package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PosizioneTest {
    @Test fun geo() {
        val p = Posizione.parseGeo("geo:45.46421,9.19003;u=12")!!
        assertEquals(45.46421, p.lat, 1e-9)
        assertEquals(12.0, p.accuracy!!, 1e-9)
        assertEquals("geo:45.46421,9.19003;u=12", Posizione.geoUri(p))
        assertNull(Posizione.parseGeo("geo:95,9"))
        assertNull(Posizione.parseGeo("http://x"))
        assertEquals("geo:1.00000,2.00000", Posizione.geoUri(Posizione.Pos(1.0, 2.0, null, 0)))
    }

    @Test fun infoAndBeaconRoundTrip() {
        val i = Posizione.info(JSONObject(Posizione.infoContent("Giulia", 1000, 60_000, true).toString()))!!
        assertTrue(i.isLive(30_000))
        assertFalse(i.isLive(61_000))
        assertEquals(61_000L, i.endsAt)
        val b = JSONObject(Posizione.beaconContent("\$info", Posizione.Pos(45.0, 9.0, 5.0, 2000)).toString())
        assertEquals("\$info", Posizione.beaconOf(b))
        assertEquals(2000L, Posizione.pos(b)!!.ts)
        // m.location di Element (una posizione sola).
        assertEquals(9.5, Posizione.pos(JSONObject("""{"msgtype":"m.location","geo_uri":"geo:45.1,9.5"}"""))!!.lon, 1e-9)
    }

    @Test fun eventsFromElement() {
        val info = JSONObject("""{"type":"org.matrix.msc3672.beacon_info","state_key":"@g:x.it","event_id":"${'$'}i","sender":"@g:x.it",
            "origin_server_ts":5,"content":{"description":"Giulia","live":true,"timeout":3600000,"org.matrix.msc3488.ts":5}}""")
        assertEquals(Event.Kind.Location, Matrix.event(info)!!.kind)
        val beacon = JSONObject("""{"type":"org.matrix.msc3672.beacon","event_id":"${'$'}b","sender":"@g:x.it","origin_server_ts":9,
            "content":{"m.relates_to":{"rel_type":"m.reference","event_id":"${'$'}i"},"org.matrix.msc3488.location":{"uri":"geo:45,9"},"org.matrix.msc3488.ts":9}}""")
        val e = Matrix.event(beacon)!!
        assertEquals(Event.Kind.Beacon, e.kind)
        assertEquals("\$i", e.ref)
        val stop = JSONObject(info.toString()).apply { getJSONObject("content").put("live", false) }
        assertEquals(Event.Kind.State, Matrix.event(stop)!!.kind)
    }

    @Test fun choices() {
        val evening = LocalDateTime.of(2026, 11, 21, 21, 30)
        assertEquals(4, Posizione.choices(evening).size)
        assertEquals(90 * 60_000L, Posizione.duration(Posizione.Until.Evening, evening))
        assertFalse(Posizione.Until.Evening in Posizione.choices(LocalDateTime.of(2026, 11, 21, 22, 10)))
    }

    @Test fun arrival() {
        val a = Posizione.Arrival()
        var t = 0L
        fun at(lat: Double) = Posizione.Pos(lat, 9.0, null, t)
        assertFalse(a.feed(at(45.0)))
        // Si parte: un chilometro più a nord.
        t += 60_000; assertFalse(a.feed(at(45.009)))
        // Fermi lì per cinque minuti.
        t += 120_000; assertFalse(a.feed(at(45.0091)))
        t += 120_000; assertFalse(a.feed(at(45.0090)))
        t += 61_000; assertTrue(a.feed(at(45.0091)))
        // Chi non si è mai mosso non è «arrivato».
        val b = Posizione.Arrival()
        t = 0; b.feed(at(45.0)); t += 600_000
        assertFalse(b.feed(at(45.0)))
        assertEquals(1000.0, Posizione.meters(Posizione.Pos(45.0, 9.0, null, 0), Posizione.Pos(45.008993, 9.0, null, 0)), 2.0)
    }
}
