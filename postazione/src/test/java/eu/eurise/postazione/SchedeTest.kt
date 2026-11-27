package eu.eurise.postazione

import eu.eurise.postazione.data.Cielo
import eu.eurise.postazione.data.Intervallo
import eu.eurise.postazione.data.Liberi
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Schede
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun fineGiornata() {
        assertEquals(false, Schede.fineGiornata(giovedi.withHour(17).withMinute(14)))
        assertEquals(true, Schede.fineGiornata(giovedi.withHour(17).withMinute(15)))
        assertEquals(false, Schede.fineGiornata(LocalDateTime.of(2026, 11, 28, 17, 30)))
    }

    private fun min(m: Int) = m * 60_000L

    @Test fun spazioPrimaDelProssimoImpegno() =
        assertEquals(Intervallo(min(0), min(50)), Liberi.primo(min(0), min(480), listOf(min(50) to min(110))))

    @Test fun spazioDopoLImpegnoInCorso() =
        assertEquals(Intervallo(min(60), min(110)), Liberi.primo(min(30), min(480), listOf(min(0) to min(60), min(110) to min(170))))

    @Test fun impegniAttaccatiNonLascianoSpazio() =
        assertEquals(Intervallo(min(120), min(480)), Liberi.primo(min(0), min(480), listOf(min(10) to min(60), min(70) to min(120))))

    @Test fun senzaImpegniNienteDaDire() = assertNull(Liberi.primo(min(0), min(480), emptyList()))

    @Test fun quanteCoseCiStanno() {
        assertEquals(2, Liberi.quante(Intervallo(min(0), min(50)), 5))
        assertEquals(1, Liberi.quante(Intervallo(min(0), min(120)), 1))
    }
}
