package eu.eurise.core.data.messages

import eu.eurise.core.data.messages.MessageInsights.Category
import eu.eurise.core.data.messages.MessageInsights.Insight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MessageInsightsTest {
    // Martedì 11 novembre 2026, ore 9.
    private val now = LocalDateTime.of(2026, 11, 11, 9, 0)

    @Test fun appuntamento() {
        val i = MessageInsights.analyze("Giovedì alle 15 va bene? Porto io i documenti", now)
        val e = i.filterIsInstance<Insight.Event>().single()
        assertEquals(LocalDate.of(2026, 11, 12), e.date)
        assertEquals(LocalTime.of(15, 0), e.time)
    }

    @Test fun codiceDiVerifica() {
        assertEquals("482913", MessageInsights.code("Il tuo codice di verifica è 482913. Non condividerlo."))
        assertEquals("1234", MessageInsights.code("PIN 1234"))
        assertNull(MessageInsights.code("Ci vediamo nel 2026 alle 18"))
        val i = MessageInsights.analyze("Codice OTP 552190 per accedere", now)
        assertTrue(i.first() is Insight.Code)
        assertTrue(i.none { it is Insight.Phone })
        assertTrue(i.none { it is Insight.Event })
    }

    @Test fun numeriEmailIndirizzi() {
        val i = MessageInsights.analyze("Chiamami al 347 123 4567 o scrivi a mario.rossi@example.it, sono in via Roma 12", now)
        assertEquals("347 123 4567", i.filterIsInstance<Insight.Phone>().single().number)
        assertEquals("mario.rossi@example.it", i.filterIsInstance<Insight.Email>().single().address)
        assertEquals("via Roma 12", i.filterIsInstance<Insight.Address>().single().text)
    }

    @Test fun cosaDaFareEConsegna() {
        val i = MessageInsights.analyze("Ricordati di portare la planimetria firmata.", now)
        assertEquals("Ricordati di portare la planimetria firmata", i.filterIsInstance<Insight.Task>().single().text)
        assertTrue(MessageInsights.analyze("Il tuo pacco è in consegna oggi 10-13", now).any { it == Insight.Delivery })
    }

    @Test fun lingua() {
        assertEquals("en", MessageInsights.language("Hi, thanks for the quote, we will see you tomorrow at the office"))
        assertEquals("it", MessageInsights.language("Ciao, domani non ci sono, ti chiamo io per la pratica"))
        assertEquals("fr", MessageInsights.language("Bonjour, merci pour le devis, je suis dans le train"))
        assertNull(MessageInsights.language("Ok"))
        assertTrue(MessageInsights.analyze("Hi, thanks for the quote, we will see you tomorrow at the office", now).any { it == Insight.Foreign("en") })
    }

    @Test fun conferme() {
        assertTrue(MessageInsights.isConfirmation("Va bene, a giovedì"))
        assertTrue(MessageInsights.isConfirmation("perfetto"))
        assertFalse(MessageInsights.isConfirmation("Non riesco giovedì, facciamo venerdì?"))
    }

    @Test fun categorie() {
        assertEquals(Category.Codes, MessageInsights.category("+39347123456", "Codice di accesso 482913"))
        assertEquals(Category.Alerts, MessageInsights.category("INFO-BANCA", "Il tuo estratto conto è disponibile"))
        assertEquals(Category.Alerts, MessageInsights.category("+393331112222", "Il tuo conto è stato bloccato, clicca qui https://x.example"))
        assertEquals(Category.People, MessageInsights.category("+393471234567", "Ci vediamo dopo"))
    }
}
