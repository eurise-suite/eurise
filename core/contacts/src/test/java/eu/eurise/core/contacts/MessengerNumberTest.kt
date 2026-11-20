package eu.eurise.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessengerNumberTest {
    @Test
    fun whatsappESignal() {
        assertEquals("+393331234567", Contacts.messengerNumber("393331234567@s.whatsapp.net"))
        assertEquals("+393331234567", Contacts.messengerNumber("+393331234567"))
        assertEquals("+393331234567", Contacts.messengerNumber("123", "Messaggio +39 333 123 4567"))
    }

    @Test
    fun nonScambiaGliIdentificativi() {
        // Telegram scrive il proprio identificativo, tutto cifre: non è un numero di telefono.
        assertNull(Contacts.messengerNumber("123456789", "Telegram"))
        assertNull(Contacts.messengerNumber(null, ""))
    }
}
