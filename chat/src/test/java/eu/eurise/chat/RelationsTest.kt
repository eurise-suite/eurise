package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationsTest {
    private fun ev(id: String, type: String, sender: String, time: Long, content: JSONObject, extra: (JSONObject) -> Unit = {}) =
        JSONObject().put("event_id", id).put("type", type).put("sender", sender).put("origin_server_ts", time).put("content", content).also(extra)

    @Test fun reazioniUnaPerPersonaETolteConLaCancellazione() {
        val s = Relations.Store()
        val a = Relations.read(ev("\$r1", "m.reaction", "@anna:x", 1, Relations.reaction("\$m", "👍")))!!
        val b = Relations.read(ev("\$r2", "m.reaction", "@me:x", 2, Relations.reaction("\$m", "👍")))!!
        val c = Relations.read(ev("\$r3", "m.reaction", "@anna:x", 3, Relations.reaction("\$m", "👍")))!!
        val d = Relations.read(ev("\$r4", "m.reaction", "@luca:x", 4, Relations.reaction("\$m", "ok")))!!
        listOf(a, b, c, d).forEach { s.add(it) }
        assertEquals(listOf(Relations.Chip("👍", 2, "\$r2"), Relations.Chip("ok", 1, null)), s.chips("\$m", "@me:x"))
        // La stessa reazione arrivata due volte (sync e storia) conta una volta.
        assertFalse(s.add(a))
        // Tolgo la mia: resta quella di Anna.
        s.add(Relations.read(ev("\$x", "m.room.redaction", "@me:x", 5, JSONObject().put("redacts", "\$r2")))!!)
        assertEquals(Relations.Chip("👍", 1, null), s.chips("\$m", "@me:x").first())
    }

    @Test fun cancellazioneConRedactsFuoriDalContenuto() {
        val r = Relations.read(ev("\$x", "m.room.redaction", "@a:x", 1, JSONObject()) { it.put("redacts", "\$m") })
        assertEquals("\$m", (r as Relations.Redaction).target)
        val s = Relations.Store()
        s.add(r)
        assertTrue(s.redacted("\$m"))
    }

    @Test fun modificheSoloDiChiHaScritto() {
        val s = Relations.Store()
        s.add(Relations.read(ev("\$e1", "m.room.message", "@anna:x", 10, Relations.edit("\$m", "Prendo anche il ghiaccio")))!!)
        s.add(Relations.read(ev("\$e0", "m.room.message", "@anna:x", 5, Relations.edit("\$m", "vecchia")))!!)
        s.add(Relations.read(ev("\$e2", "m.room.message", "@intruso:x", 20, Relations.edit("\$m", "falsa")))!!)
        // Vale l'ultima modifica di chi ha scritto il messaggio; quella di un altro non la tocca.
        assertEquals("Prendo anche il ghiaccio", s.edited("\$m", "@anna:x"))
        assertNull(s.edited("\$m", "@luca:x"))
    }

    @Test fun risposte() {
        assertEquals("\$m", Relations.replyTo(Relations.reply("Io!", "\$m")))
        assertNull(Relations.replyTo(JSONObject().put("body", "ciao")))
        assertEquals("Io!", Relations.stripFallback("> <@giulia:x> Chi porta le bevande?\n> seconda riga\n\nIo!"))
        assertEquals("> citazione vera senza testo", Relations.stripFallback("> citazione vera senza testo"))
        assertEquals("ciao", Relations.stripFallback("ciao"))
    }

    @Test fun sondaggio() {
        val content = Relations.newPoll("Pizza o grigliata sabato?", listOf("Pizza", "Grigliata"), closes = 1000L)
        val p = Relations.poll(content)!!
        assertEquals("Pizza o grigliata sabato?", p.question)
        assertEquals(listOf("a0" to "Pizza", "a1" to "Grigliata"), p.answers)
        assertTrue(p.disclosed)
        assertEquals(1000L, p.closes)
        val s = Relations.Store()
        fun vote(id: String, who: String, t: Long, a: String) =
            s.add(Relations.read(ev(id, "org.matrix.msc3381.poll.response", who, t, Relations.vote("\$p", listOf(a))))!!)
        vote("\$v1", "@me:x", 10, "a1")
        vote("\$v2", "@me:x", 20, "a0") // cambio idea: vale l'ultimo
        vote("\$v3", "@anna:x", 30, "a0")
        vote("\$v4", "@luca:x", 40, "zz") // risposta che non c'è
        vote("\$v5", "@gio:x", 2000, "a1") // dopo la chiusura
        val open = s.tally("\$p", p, "@me:x", now = 500)
        assertEquals(mapOf("a0" to 2, "a1" to 1), open.counts)
        assertFalse(open.ended)
        val closed = s.tally("\$p", p, "@me:x", now = 5000)
        assertEquals(mapOf("a0" to 2, "a1" to 0), closed.counts)
        assertTrue(closed.ended)
        assertEquals(listOf("a0"), closed.mine)
        assertEquals(2, closed.voters)
        // La fine mandata chiude anche senza ora di chiusura.
        val q = Relations.poll(Relations.newPoll("Sì?", listOf("Sì", "No"), null))!!
        s.add(Relations.read(ev("\$end", "org.matrix.msc3381.poll.end", "@me:x", 25, Relations.endPoll("\$p", "chiuso")))!!)
        assertEquals(mapOf("a0" to 1, "a1" to 0), s.tally("\$p", q, "@me:x", now = 100).counts)
        assertTrue(s.endSent("\$p"))
    }

    @Test fun fissati() {
        assertEquals(listOf("\$a", "\$b"), Relations.pinned(Relations.pinnedState(listOf("\$a", "\$b"))))
        assertEquals(emptyList<String>(), Relations.pinned(null))
    }
}
