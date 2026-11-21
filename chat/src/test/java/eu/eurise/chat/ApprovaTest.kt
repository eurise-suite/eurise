package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovaTest {
    @Test fun requestAndAnswer() {
        val c = JSONObject(Approva.content("a1", "Luca", "Preventivo Villa Rossi", "48.200 €", "Preventivo_Rossi_v3.pdf", "", "✅ Richiesta di approvazione").toString())
        assertTrue(c.getString("body").contains("Preventivo Villa Rossi · 48.200 €"))
        val card = Approva.card(c)!!
        assertEquals("a1", card.id)
        assertEquals("Preventivo_Rossi_v3.pdf", card.file)
        val a = Approva.answerContent("\$ev", "a1", Approva.Verdict.Change, "togli la voce 14", "Chiede una modifica")
        assertEquals(Triple("a1", Approva.Verdict.Change, "togli la voce 14"), Approva.answer(a))
        assertTrue(a.getString("body").endsWith("«togli la voce 14»"))
        assertEquals("\$ev", Relations.replyTo(a))
        assertNull(Approva.card(JSONObject().put("body", "ciao")))
    }

    @Test fun listAndWaiting() {
        val r = Approva.Request("a1", "Work", "!r", "Ing. Ferri", "Trasferta", "312 €", 1000)
        assertTrue(Approva.waiting(r, 1000 + Approva.QUIET_MS))
        assertFalse(Approva.waiting(r, 2000))
        val changed = Approva.update(listOf(r), mapOf("a1" to Approva.Answer(Approva.Verdict.Change, "@f", "meno", 3000))).single()
        assertEquals(Approva.Verdict.Change, changed.verdict)
        val ok = Approva.update(listOf(changed), mapOf("a1" to Approva.Answer(Approva.Verdict.Approved, "@f", "", 4000))).single()
        assertEquals(Approva.Verdict.Approved, ok.verdict)
        assertFalse(Approva.waiting(ok, 10 * Approva.QUIET_MS))
        assertEquals(listOf(ok), Approva.decode(Approva.encode(listOf(ok))))
    }
}
