package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UscitaTest {
    private val card = Uscita.Card("u1", "Marco", "Pizza dopo la partita?",
        listOf(Uscita.Option("Ven 28 · 21:00", 1), Uscita.Option("Sab 29 · 20:30", 2)), "Da Gino", "Via Manzoni 12", 0)

    @Test fun roundTrip() {
        val c = JSONObject(Uscita.content("u1", "Marco", card.title, card.options, card.place, card.address, 0, "🍕 Proposta di uscita").toString())
        assertEquals(card, Uscita.card(c))
        listOf(Uscita.Say.Dates(setOf(0), setOf(0)), Uscita.Say.Offer(3, "Monza"), Uscita.Say.Need("Monza centro"), Uscita.Say.Join("@m"),
            Uscita.Say.Pick("@s"), Uscita.Say.NoRide, Uscita.Say.Fix(1), Uscita.Say.Place("A", "B")).forEach { say ->
            assertEquals("u1" to say, Uscita.parse(JSONObject(Uscita.answer("\$e", "u1", say, "x").toString())))
        }
    }

    @Test fun stateFold() {
        val s = Uscita.state(card, "@marco", listOf(
            "@marco" to Uscita.Say.Dates(setOf(0, 1), setOf(0, 1)),
            "@luca" to Uscita.Say.Dates(setOf(0), null),
            "@sara" to Uscita.Say.Dates(setOf(1), setOf(1)),
            "@sara" to Uscita.Say.Dates(setOf(0), setOf(0)),
            "@marco" to Uscita.Say.Offer(1, ""),
            "@luca" to Uscita.Say.Join("@marco"),
            "@gianni" to Uscita.Say.Join("@marco"),
            "@sara" to Uscita.Say.Need("Monza centro"),
            "@luca" to Uscita.Say.Fix(1),
        ))
        assertEquals(3, s.count(0))
        assertEquals(1, s.count(1))
        assertEquals(2, s.calendarFree(0))
        assertEquals(listOf("@luca"), s.cars.single().riders)
        assertEquals(0, s.cars.single().free)
        assertEquals("Monza centro", s.needs["@sara"])
        assertNull(s.fixed)
        assertEquals(0, Uscita.best(card, s))
        val fixed = Uscita.state(card, "@marco", listOf("@marco" to Uscita.Say.Fix(0)))
        assertEquals(0, fixed.fixed)
    }
}
