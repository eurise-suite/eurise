package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestTest {
    @Test fun timerDallaRisposta() {
        assertEquals(listOf(Suggest.Action(minutes = 9)), Suggest.actions("Cuocia la pasta per 9 minuti, poi la scoli."))
        assertEquals(listOf(Suggest.Action(minutes = 8)), Suggest.actions("Servono 8-10 minuti in forno."))
        assertEquals(listOf(Suggest.Action(minutes = 30)), Suggest.actions("Lasci riposare mezz'ora."))
    }

    @Test fun numeroDaChiamare() {
        assertEquals(listOf(Suggest.Action(number = "+390212345678")), Suggest.actions("Il numero è +39 02 1234 5678."))
    }

    @Test fun nienteSeNonServe() {
        assertEquals(emptyList<Suggest.Action>(), Suggest.actions("Roma è la capitale d'Italia dal 1871."))
    }
}
