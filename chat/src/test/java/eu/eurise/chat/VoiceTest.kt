package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    @Test fun contentAndInfoRoundTrip() {
        val c = Voice.content("Messaggio vocale", "audio/mp4", 1234, 42_000, listOf(0, 512, 1024), null, "mxc://s/a")
        assertEquals("m.audio", c.getString("msgtype"))
        assertTrue(c.has(Voice.VOICE))
        assertEquals("mxc://s/a", c.getString("url"))
        val i = Voice.info(JSONObject(c.toString()))
        assertEquals(42_000L, i.durationMs)
        assertEquals(listOf(0, 512, 1024), i.wave)
        assertTrue(i.voice)
    }

    @Test fun infoFromElementWithoutInfoDuration() {
        val c = JSONObject("""{"msgtype":"m.audio","body":"Voice message","org.matrix.msc1767.audio":{"duration":3100,"waveform":[10,2000,-5]},"org.matrix.msc3245.voice":{}}""")
        val i = Voice.info(c)
        assertEquals(3100L, i.durationMs)
        assertEquals(listOf(10, 1024, 0), i.wave)
    }

    @Test fun waveformScalesToPeak() {
        val pcm = ShortArray(1000) { if (it < 500) 1000 else 4000 }
        val w = Voice.waveform(pcm, 2)
        assertEquals(listOf(256, 1024), w)
        assertEquals(List(4) { 0 }, Voice.waveform(ShortArray(100), 4))
    }

    @Test fun barsResample() {
        val b = Voice.bars(listOf(0, 1024), 4)
        assertEquals(4, b.size)
        assertEquals(0.15f, b[0], 0.001f)
        assertEquals(1f, b[3], 0.001f)
        assertEquals(List(3) { 0.3f }, Voice.bars(emptyList(), 3))
    }

    @Test fun durationText() {
        assertEquals("0:42", Voice.duration(42_000))
        assertEquals("12:05", Voice.duration(725_000))
        assertEquals("0:00", Voice.duration(-5))
    }

    @Test fun piecesCutInTheQuietestPoint() {
        val rate = 100
        // 60 secondi a volume alto con una pausa intorno al secondo 22.
        val pcm = ShortArray(60 * rate) { i -> if (i in 2200 until 2240) 0 else 10_000 }
        val p = Voice.pieces(pcm, rate)
        assertTrue(p.size >= 2)
        assertTrue(p[0].last + 1 in 2200..2240)
        assertEquals(pcm.size, p.sumOf { it.count() })
        p.forEach { assertTrue(it.count() <= 28 * rate) }
        assertEquals(listOf(0 until 10), Voice.pieces(ShortArray(10), rate))
    }

    @Test fun downmixAndResample() {
        val stereo = shortArrayOf(100, 300, 100, 300)
        assertEquals(listOf<Short>(200, 200), Voice.toMono16k(stereo, 2, 16_000).toList())
        val r = Voice.toMono16k(ShortArray(48_000) { 1000 }, 1, 48_000)
        assertEquals(16_000, r.size)
        assertTrue(r.all { it.toInt() == 1000 })
    }

    @Test fun littleEndianBytes() {
        val b = Voice.bytes(shortArrayOf(0x0102, -1))
        assertEquals(listOf<Byte>(2, 1, -1, -1), b.toList())
    }

    @Test fun voiceEventFromElement() {
        val o = JSONObject("""{"type":"m.room.message","event_id":"${'$'}v","sender":"@anna:x.it","origin_server_ts":5,"content":{"msgtype":"m.audio","body":"Voice message","file":{"url":"mxc://x.it/voc"},"info":{"mimetype":"audio/ogg","duration":2000},"org.matrix.msc3245.voice":{}}}""")
        val e = Matrix.event(o)!!
        assertEquals(Event.Kind.Audio, e.kind)
        assertEquals("mxc://x.it/voc", e.mediaUrl)
        assertEquals("audio/ogg", Voice.info(JSONObject(e.data!!)).mime)
    }

    @Test fun spokenText() {
        assertEquals("Anna: ciao", Voice.spoken("Anna", " ciao "))
        assertEquals("ciao", Voice.spoken("", "ciao"))
    }
}
