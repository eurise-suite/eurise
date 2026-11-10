package eu.eurise.core.suite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTextTest {
    @Test fun readsATypicalCard() {
        val text = """
            BANCA ESEMPIO
            debit
            4111 1111 1111 1111
            VALID FROM 01/24  VALID THRU 12/28
            MARIO ROSSI
            VISA
        """.trimIndent()
        val c = CardText.parse(text)!!
        assertEquals("4111 1111 1111 1111", c.number)
        assertEquals("12/28", c.expiry)
        assertEquals("MARIO ROSSI", c.holder)
    }

    @Test fun fixesOcrSwapsInsideNumbers() {
        assertEquals("5555555555554444", CardText.number("5555 S555 5555 4444"))
        assertEquals("4111111111111111", CardText.number("4lll 1111 IIII 1111"))
    }

    @Test fun refusesWrongNumbers() {
        assertNull(CardText.parse("4111 1111 1111 1112\n12/28"))
        assertNull(CardText.number("telefono 3331234567"))
    }

    @Test fun amexAndNoHolder() {
        val c = CardText.parse("3782 822463 10005\n05/27")!!
        assertEquals("3782 8224 6310 005", c.number)
        assertEquals("05/27", c.expiry)
        assertEquals("", c.holder)
    }
}
