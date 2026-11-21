package eu.eurise.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseModelTest {
    private fun model(times: Int = 3) = PhraseModel().apply { repeat(times) { learn("Ci vediamo domani alle 9 al solito posto") } }

    @Test fun proponeIlRestoDopoLInizio() {
        val m = model()
        assertEquals("domani alle 9 al solito posto", m.propose("Ci vediamo "))
        assertEquals("amo domani alle 9 al solito posto", m.propose("ci vedi"))
    }

    @Test fun servonoAbbastanzaVolte() {
        assertNull(model(2).propose("Ci vediamo "))
    }

    @Test fun servonoDueParoleEUnInizioLungo() {
        val m = model()
        assertNull(m.propose("Ci "))
        assertNull(m.propose("Ci v"))
    }

    @Test fun nonProponeSeNonCoincide() {
        val m = model()
        assertNull(m.propose("Ci sentiamo "))
        assertNull(m.propose("Ci vediamo domani alle 9 al solito posto"))
    }

    @Test fun vinceLaPiuUsata() {
        val m = model(3)
        repeat(5) { m.learn("Ci vediamo stasera da me") }
        assertEquals("stasera da me", m.propose("Ci vediamo "))
    }

    @Test fun togliereEPerSempre() {
        val m = model()
        m.forget("Ci vediamo ", "domani alle 9 al solito posto")
        assertNull(m.propose("Ci vediamo "))
    }

    @Test fun salvaERilegge() {
        val m = model()
        val again = PhraseModel().apply { load(m.save()) }
        assertEquals("domani alle 9 al solito posto", again.propose("Ci vediamo "))
    }

    @Test fun frasiCorteNonContano() {
        val m = PhraseModel()
        repeat(5) { m.learn("Va bene") }
        assertEquals(0, m.size)
    }

    @Test fun unireNonPerdeNulla() {
        val here = PhraseModel().apply { repeat(4) { learn("Arrivo tra dieci minuti circa") } }
        val backup = PhraseModel().apply {
            repeat(2) { learn("Arrivo tra dieci minuti circa") }
            repeat(3) { learn("Ci vediamo domani alle 9 al solito posto") }
        }
        here.merge(backup.save())
        assertEquals(2, here.size)
        assertEquals("dieci minuti circa", here.propose("Arrivo tra "))
        assertEquals("al solito posto", here.propose("Ci vediamo domani alle 9 "))
        // Il conto più alto resta: quattro volte, non due.
        assertTrue(here.save().contains("4\tArrivo tra dieci minuti circa"))
    }
}
