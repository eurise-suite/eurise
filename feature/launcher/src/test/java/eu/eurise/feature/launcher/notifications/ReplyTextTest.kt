package eu.eurise.feature.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReplyTextTest {

    @Test
    fun quoteIsNativeOnWhatsappAndGuillemetsElsewhere() {
        assertEquals("> Mi porti i campioni?\nSì, i grigi", ReplyText.quoted("com.whatsapp", " Mi porti  i campioni? ", "Sì, i grigi"))
        assertEquals("«Mi porti i campioni?»\nSì", ReplyText.quoted("org.telegram.messenger", "Mi porti i campioni?", "Sì"))
        assertEquals("Sì", ReplyText.quoted("com.whatsapp", "  ", " Sì "))
    }

    @Test
    fun longQuoteIsShortened() {
        val q = ReplyText.quoted("org.telegram.messenger", "a".repeat(300), "ok").lineSequence().first()
        assertEquals(160, q.length)
    }

    @Test
    fun contactAndPosition() {
        assertEquals("Mario Idraulico\n333 123 4567", ReplyText.contact("Mario Idraulico ", listOf("333 123 4567", "333 123 4567")))
        assertEquals(
            "Sono qui: https://www.openstreetmap.org/?mlat=45.58349&mlon=9.27400#map=17/45.58349/9.27400",
            ReplyText.position(45.583494, 9.274),
        )
    }

    @Test
    fun availabilitySentence() {
        val today = LocalDate.of(2026, 11, 11) // mercoledì
        val free = mapOf(
            today to listOf(LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 30), LocalTime.of(18, 0), LocalTime.of(19, 0)),
            today.plusDays(1) to listOf(LocalTime.of(9, 0)),
            today.plusDays(2) to emptyList(),
            today.plusDays(5) to listOf(LocalTime.of(10, 0)),
        )
        assertEquals(
            "Sono libero oggi alle 15 e alle 16:30 e alle 18, domani alle 9, lunedì 16 alle 10",
            ReplyText.availability(free, today),
        )
        assertNull(ReplyText.availability(mapOf(today to emptyList()), today))
    }

    @Test
    fun workDaysSkipWeekendAndLateEvening() {
        val friLate = LocalDateTime.of(2026, 11, 13, 20, 0)
        assertEquals(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 17)), ReplyText.nextWorkDays(friLate, 2))
        val wedMorning = LocalDateTime.of(2026, 11, 11, 9, 0)
        assertEquals(LocalDate.of(2026, 11, 11), ReplyText.nextWorkDays(wedMorning, 1).first())
    }

    @Test
    fun mentionedContactsFromConversation() {
        val contacts = listOf("Mario Idraulico", "Luca Bianchi", "Anna Rossi", "Studio Casa")
        assertEquals(
            listOf("Mario Idraulico"),
            ReplyText.mentionedContacts(listOf("Mi mandi il numero dell'idraulico e il preventivo?"), contacts, sender = "Luca Bianchi"),
        )
        assertEquals(listOf("Anna Rossi"), ReplyText.mentionedContacts(listOf("Hai sentito Anna?"), contacts, sender = "Luca"))
        assertEquals(emptyList<String>(), ReplyText.mentionedContacts(listOf("Ciao Luca, a casa domani?"), contacts, sender = "Luca"))
    }
}
