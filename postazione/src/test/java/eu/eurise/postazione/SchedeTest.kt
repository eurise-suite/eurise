package eu.eurise.postazione

import eu.eurise.postazione.data.Cielo
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Schede
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SchedeTest {
    private val giovedi = LocalDateTime.of(2026, 11, 26, 9, 30)

    @Test fun lavoroNeiFeriali() = assertEquals(Scheda.Lavoro, Schede.automatica(giovedi))
    @Test fun personaleLaSera() = assertEquals(Scheda.Personale, Schede.automatica(giovedi.withHour(18)))
    @Test fun personaleIlMattinoPresto() = assertEquals(Scheda.Personale, Schede.automatica(giovedi.withHour(7)))
    @Test fun personaleNelWeekend() = assertEquals(Scheda.Personale, Schede.automatica(LocalDateTime.of(2026, 11, 28, 10, 0)))

    @Test fun laSceltaAManoTieneFinoAlCambio() {
        assertEquals(Scheda.Viaggio, Schede.attiva(Scheda.Lavoro, Scheda.Viaggio, Scheda.Lavoro))
        // Alle 18 l'automatica passa a Personale: la scelta fatta di giorno non vale più.
        assertEquals(Scheda.Personale, Schede.attiva(Scheda.Personale, Scheda.Viaggio, Scheda.Lavoro))
        assertEquals(Scheda.Lavoro, Schede.attiva(Scheda.Lavoro, null, null))
    }

    @Test fun cielo() {
        assertEquals(Cielo.Mattino, Schede.cielo(giovedi))
        assertEquals(Cielo.Tramonto, Schede.cielo(giovedi.withHour(17)))
        assertEquals(Cielo.Notte, Schede.cielo(giovedi.withHour(22)))
    }
}
