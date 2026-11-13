package eu.eurise.core.suite

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test fun keepsApostrophesAndPunctuation() =
        assertEquals("Ho preparato l'appuntamento: attende soltanto il suo assenso.",
            SpeechText.clean("Ho preparato l’appuntamento: attende soltanto il suo assenso."))

    @Test fun quotesAndSeparatorsBecomePauses() =
        assertEquals("Dentista, martedì alle 9:30, Via Roma 12, B",
            SpeechText.clean("«Dentista» · martedì alle 9:30 — Via Roma 12/B"))

    @Test fun dropsEmojiAndEllipsis() =
        assertEquals("Temo che WhatsApp non mi lasci inviare da qui. le apro la conversazione",
            SpeechText.clean("Temo che WhatsApp non mi lasci inviare da qui… le apro la conversazione 😊"))

    @Test fun readsSymbols() =
        assertEquals("Prezzo 1,739 euro al litro, self, 13/11, 50 per cento",
            SpeechText.clean("Prezzo 1,739 €/l (self) → 13/11, 50%"))

    @Test fun plainQuotes() = assertEquals("Subito.", SpeechText.clean("\"Subito.\""))
}
