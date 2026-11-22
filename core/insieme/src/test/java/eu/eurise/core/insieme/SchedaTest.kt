package eu.eurise.core.insieme

import eu.eurise.core.insieme.Scheda.Answer
import eu.eurise.core.insieme.Scheda.Card
import eu.eurise.core.insieme.Scheda.Kind
import eu.eurise.core.insieme.Scheda.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedaTest {
    private val w = SchedaMail.Words(
        tag = { if (it == Kind.Affida) "Da fare" else it.name },
        intro = { _, from -> "$from ti ha affidato questa cosa." },
        howTo = { "Rispondi «fatto»." },
        button = { it.name },
        reply = { when (it) { State.Done -> "fatto"; State.Declined -> "non posso"; State.Seen -> "vista"; else -> it.name.lowercase() } },
        due = { "entro $it" },
        option = { i, o -> "${i + 1} · $o" },
        choose = { "Le date:" },
        amount = { "Importo: $it" },
        footer = "Scheda mandata con Eurise",
    )

    private val card = Card("c42", Kind.Affida, "Preparare il preventivo Rossi", "Usa il listino 2026", "ven 22", 0, "Mario", "mario@studio.it")

    @Test fun jsonRoundTrip() {
        val back = Scheda.card(Scheda.content(card.copy(options = listOf("a", "b")), "testo"))
        assertEquals(card.copy(options = listOf("a", "b")), back)
        val a = Answer(State.Done, setOf(2, 0), "ecco", "mailto:anna@x.it")
        val parsed = Scheda.answer(Scheda.answerContent("\$ev", "c42", a, "Fatta"))
        assertEquals("c42", parsed!!.first)
        assertEquals(a, parsed.second)
    }

    @Test fun subjectAndRef() {
        val s = SchedaMail.subject(card, w)
        assertEquals("[Da fare] Preparare il preventivo Rossi · ven 22", s)
        assertEquals(Kind.Affida, SchedaMail.kindOf("Re: R: $s", w))
        assertEquals(SchedaMail.core(s), SchedaMail.core("RE: $s"))
        val text = SchedaMail.text(card, w)
        assertTrue(text.contains("eurise-c42"))
        assertEquals("c42", SchedaMail.ref("> " + text.lines().last()))
        val html = SchedaMail.html(card, w, "mario@studio.it")
        assertTrue(html.contains("mailto:mario@studio.it?subject=Re%3A%20%5BDa%20fare%5D"))
        assertTrue(html.contains("body=fatto"))
        assertFalse(html.contains("<script"))
    }

    @Test fun repliesAffida() {
        assertEquals(State.Done, Risposte.read(Kind.Affida, "Fatto!\n\nIl giorno 21 Mario ha scritto:\n> fatto o non posso")?.state)
        assertEquals(State.Declined, Risposte.read(Kind.Affida, "Non posso, sono fuori\n> ...")?.state)
        assertEquals("", Risposte.read(Kind.Affida, "Non posso, sono fuori")?.note)
        assertEquals(State.Seen, Risposte.read(Kind.Affida, "ok")?.state)
        assertEquals(State.Working, Risposte.read(Kind.Affida, "Ci lavoro oggi")?.state)
        assertNull(Risposte.read(Kind.Affida, "Non l'ho ancora fatto, domani"))
        assertNull(Risposte.read(Kind.Affida, "Ti chiamo dopo"))
        assertNull(Risposte.read(Kind.Affida, "> fatto"))
        assertEquals(State.Done, Risposte.read(Kind.Faccenda, "fatta")?.state)
    }

    @Test fun repliesChoices() {
        assertEquals(setOf(0, 2), Risposte.read(Kind.Uscita, "1 e 3\n> 1 · sab 29", 3)?.choices)
        assertEquals(emptySet<Int>(), Risposte.read(Kind.Uscita, "Nessuna, mi spiace", 3)?.choices)
        assertEquals(setOf(1), Risposte.read(Kind.Uscita, "va bene la seconda", 3)?.choices)
        assertEquals(setOf(0, 1, 2), Risposte.read(Kind.Uscita, "tutte!", 3)?.choices)
        assertNull(Risposte.read(Kind.Uscita, "vediamo", 3))
        assertEquals(setOf(1), Risposte.read(Kind.Orari, "2 o 4", 5)?.choices)
        assertEquals(setOf(3), Risposte.read(Kind.Orari, "4", 5)?.choices)
        assertNull(Risposte.read(Kind.Orari, "9", 5))
    }

    @Test fun repliesOthers() {
        assertEquals(State.Late, Risposte.read(Kind.Cena, "Tardi, verso le 21")?.state)
        assertEquals(State.No, Risposte.read(Kind.Cena, "No, sono da Giulia")?.state)
        assertEquals(State.Yes, Risposte.read(Kind.Cena, "Sì")?.state)
        assertEquals(State.Paid, Risposte.read(Kind.Spesa, "Ho pagato ieri")?.state)
        assertNull(Risposte.read(Kind.Spesa, "quanto?"))
    }

    @Test fun foldOnBehalf() {
        val f = Scheda.fold("@mario:x", listOf(
            "@anna:x" to Answer(choices = setOf(0)),
            "@luca:x" to Answer(choices = setOf(1), forWho = "mailto:finto@x.it"),
            "@mario:x" to Answer(choices = setOf(2), forWho = "mailto:sara@x.it"),
            "@anna:x" to Answer(choices = setOf(1, 2)),
        ))
        assertEquals(setOf("@anna:x", "mailto:sara@x.it"), f.keys)
        assertEquals(setOf(1, 2), f["@anna:x"]?.choices)
        assertEquals(listOf(0, 1, 2), Scheda.counts(Card("u", Kind.Uscita, "Pizza", options = listOf("a", "b", "c")), f))
    }

    @Test fun mailPeople() {
        val p = Scheda.mailPerson("Anna@X.it", "Anna")
        assertTrue(Scheda.isMailPerson(p))
        assertEquals("anna@x.it", Scheda.mailAddress(p))
        assertEquals("Anna", Scheda.mailName(p))
        assertEquals("Sara", Scheda.mailName(Scheda.mailPerson("sara.bianchi@x.it")))
        assertNull(Scheda.mailAddress("@anna:matrix.org"))
    }

    @Test fun backAndShown() {
        assertTrue(Scheda.back(null, 1000, 86_400_000L, 1000 + 86_400_000L))
        assertFalse(Scheda.back(State.Seen, 1000, 86_400_000L, 1000 + 86_400_000L))
        assertFalse(Scheda.back(null, 1000, 0, Long.MAX_VALUE))
        assertEquals(State.Seen, Scheda.shown(null, 1000, 2000))
        assertNull(Scheda.shown(null, 3000, 2000))
        assertNotNull(Scheda.shown(State.Done, 3000, 0))
    }

    @Test fun contractPack() {
        val list = listOf(SchedaContract.Got("c1", "anna@x.it", Answer(State.Done, note = "ok"), 5L))
        assertEquals(list, SchedaContract.unpack(SchedaContract.pack(list)))
    }

    @Test fun roles() {
        assertEquals("3331234567", Ruoli.norm("+39 333 123 4567"))
        assertEquals("3331234567", Ruoli.norm("0039 333-1234567"))
        assertEquals("3331234567", Ruoli.norm("333 1234567"))
        assertEquals("anna@x.it", Ruoli.norm("mailto:Anna@X.it"))
        assertEquals("@paolo:matrix.org", Ruoli.norm("@Paolo:matrix.org"))
        var list = Ruoli.set(emptyList(), "k1", "Paolo Monti", Ruoli.Role.Collaboratore, null, listOf("+39 333 1234567", "@paolo:x.org", "paolo@studio.it"))
        list = Ruoli.set(list, "k2", "Ing. Ferri", Ruoli.Role.Capo, Ruoli.Level.Vede, listOf("ferri@studio.it"))
        assertEquals("k1", Ruoli.find(list, "3331234567")?.key)
        assertEquals("k1", Ruoli.find(list, "@paolo:x.org")?.key)
        assertEquals(Ruoli.Level.Modifica, Ruoli.find(list, "paolo@studio.it")?.shown)
        assertEquals(Ruoli.Level.Vede, Ruoli.find(list, "FERRI@studio.it")?.shown)
        assertEquals(Ruoli.Space.Lavoro, Ruoli.space(Ruoli.Role.Capo))
        assertEquals(list, Ruoli.decode(Ruoli.encode(list)))
        assertEquals(listOf("k2"), Ruoli.set(list, "k1", "", null, null, emptyList()).map { it.key })
    }

    @Test fun freeSlots() {
        val zone = java.time.ZoneId.of("Europe/Rome")
        val monday = java.time.LocalDate.of(2026, 11, 23)
        assertEquals(monday, Disponibilita.nextWeek(java.time.LocalDate.of(2026, 11, 19)))
        fun ms(d: java.time.LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        // Lunedì occupato 9–10:30 e tutto il pomeriggio; martedì libero.
        val busy = listOf(Disponibilita.Busy(ms(monday, 9), ms(monday, 10, 30)), Disponibilita.Busy(ms(monday, 14), ms(monday, 19)))
        val slots = Disponibilita.free(busy, monday, 2, 60, zone, now = 0)
        assertEquals(listOf(ms(monday, 10, 30), ms(monday.plusDays(1), 9), ms(monday.plusDays(1), 14, 30)), slots.map { it.start })
        assertTrue(slots.all { it.end - it.start == 3_600_000L })
        assertEquals("Lun 23 · 10:30", Disponibilita.label(slots[0].start, zone))
        // Sabato e domenica restano fuori; mai prima di adesso.
        assertTrue(Disponibilita.free(emptyList(), monday.plusDays(5), 2, 30, zone, now = 0).isEmpty())
        assertTrue(Disponibilita.free(emptyList(), monday, 1, 30, zone, now = ms(monday, 20)).isEmpty())
    }

    @Test fun ics() {
        val text = Ics.event("u1", "Incontro, progetto; fase 1", 1_795_000_000_000L, 1_795_003_600_000L, "Via dei Mille 4", "riga\nseconda", "mario@x.it", "anna@x.it", now = 0)
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.contains("SUMMARY:Incontro\\, progetto\\; fase 1\r\n"))
        assertTrue(text.contains("UID:u1@eurise"))
        assertTrue(text.contains("DTSTAMP:19700101T000000Z"))
        assertTrue(text.contains("ATTENDEE;RSVP=FALSE;PARTSTAT=ACCEPTED:mailto:anna@x.it"))
        assertTrue(text.lines().all { it.trimEnd('\r').length <= 75 })
    }

    @Test fun proposal() {
        assertEquals(State.Confirmed, Risposte.read(Kind.Proposta, "Confermo, grazie")?.state)
        assertEquals(State.Other, Risposte.read(Kind.Proposta, "Altro orario per favore")?.state)
    }

    @Test fun meeting() {
        val today = java.time.LocalDate.of(2026, 11, 19) // giovedì
        val people = listOf("@paolo:x" to "Paolo Monti", "@giulia:x" to "Giulia Bassi", "@gianni:x" to "Gianni Rossi")
        val note = """Presenti: Ing. Ferri, Giulia, Paolo, io
            |Cucina
            |Il cliente conferma il top in quarzo.
            |@Paolo manda il computo aggiornato ven 20
            |@Giulia: chiede i campioni al fornitore lun 23
            |@io fisso il sopralluogo con i Rossi domani
            |@Gi prova
            |Decidiamo che il top è in quarzo""".trimMargin()
        val t = Riunione.tasks(note, people, "@me:x", today)
        assertEquals(4, t.size)
        assertEquals("@paolo:x", t[0].who)
        assertEquals("Manda il computo aggiornato", t[0].text)
        assertEquals(java.time.LocalDate.of(2026, 11, 20), t[0].due)
        assertEquals("@giulia:x", t[1].who)
        assertEquals(java.time.LocalDate.of(2026, 11, 23), t[1].due)
        assertEquals("@me:x", t[2].who)
        assertEquals(today.plusDays(1), t[2].due)
        assertNull(t[3].who) // «Gi»: Giulia o Gianni, non si sa
        assertEquals(listOf("Il top è in quarzo"), Riunione.decisions(note))
        assertEquals("Manda il PDF" to java.time.LocalDate.of(2026, 12, 2), Riunione.dueOf("Manda il PDF entro 2/12", today))
    }

    @Test fun amounts() {
        assertEquals(52800L, Importi.total("Prenotazione MB-48211 confermata. Livorno → Olbia, 4 passeggeri, auto. Totale pagato:\n528,00 €."))
        assertEquals(128400L, Importi.total("Riepilogo\nCasa 1.084,00 €\nPulizie 200,00 €\nTotale: € 1.284,00"))
        assertEquals(3850L, Importi.total("Trattoria Il Ponte\nCoperto 2,00 EUR\nIMPORTO 38,50 EUR"))
        assertEquals(1200L, Importi.total("Uscita didattica, quota 12 €, autorizzazione entro il 25"))
        assertEquals(null, Importi.total("Ci vediamo il 25 alle 15"))
        assertEquals(52800L, Importi.cents("528.00"))
        assertEquals(null, Importi.cents("12,5"))
    }
}
