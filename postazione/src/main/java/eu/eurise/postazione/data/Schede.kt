package eu.eurise.postazione.data

import java.time.DayOfWeek
import java.time.LocalDateTime

/** Le tre schede della Postazione: ognuna con le sue bolle e la sua barra (Comunica e la lente restano ferme). */
enum class Scheda { Lavoro, Personale, Viaggio }

/** Le fasce del cielo dietro la Postazione: seguono l'ora del telefono, senza rete. */
enum class Cielo { Notte, Mattino, Giorno, Tramonto }

object Schede {
    /** Il lavoro (o lo studio) davanti nei giorni feriali dalle [start] alle [end]; la sera e nel weekend il personale. */
    fun automatica(now: LocalDateTime, start: Int = 8, end: Int = 18): Scheda {
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        return if (!weekend && now.hour in start until end) Scheda.Lavoro else Scheda.Personale
    }

    /**
     * La scheda da mostrare. Una scelta a mano vale finché la scheda automatica resta quella di quando l'hai fatta
     * ([autoAllaScelta]): alle 18 o al cambio di giorno la Postazione torna a fare da sola.
     */
    fun attiva(auto: Scheda, manuale: Scheda?, autoAllaScelta: Scheda?): Scheda =
        if (manuale != null && autoAllaScelta == auto) manuale else auto

    /** Da tre quarti d'ora prima della fine del lavoro, nei feriali, la scheda Lavoro propone «Chiudi la giornata». */
    fun fineGiornata(now: LocalDateTime, end: Int = 18): Boolean {
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        return !weekend && now.hour * 60 + now.minute >= end * 60 - 45
    }

    fun cielo(now: LocalDateTime): Cielo = when (now.hour) {
        in 6..10 -> Cielo.Mattino
        in 11..15 -> Cielo.Giorno
        in 16..19 -> Cielo.Tramonto
        else -> Cielo.Notte
    }

    /** Buongiorno fino alle 13, buon pomeriggio fino alle 18, poi buonasera. */
    fun saluto(now: LocalDateTime): Saluto = when (now.hour) {
        in 5..12 -> Saluto.Giorno
        in 13..17 -> Saluto.Pomeriggio
        else -> Saluto.Sera
    }
}

enum class Saluto { Giorno, Pomeriggio, Sera }

/** Uno spazio libero tra due impegni, in millisecondi. */
data class Intervallo(val inizio: Long, val fine: Long) {
    val minuti: Int get() = ((fine - inizio) / 60_000L).toInt()
}

object Liberi {
    /**
     * Il primo spazio libero di almeno [minuti] tra [ora] e [limite], dati gli impegni con un orario (inizio, fine).
     * Senza impegni in quella finestra non c'è nulla da dire: tutta la giornata è libera.
     */
    fun primo(ora: Long, limite: Long, impegni: List<Pair<Long, Long>>, minuti: Int = 30): Intervallo? {
        val rilevanti = impegni.filter { it.second > ora && it.first < limite }.sortedBy { it.first }
        if (rilevanti.isEmpty()) return null
        val soglia = minuti * 60_000L
        var t = ora
        for ((inizio, fine) in rilevanti) {
            if (inizio - t >= soglia) return Intervallo(t, inizio)
            if (fine > t) t = fine
        }
        return if (limite - t >= soglia) Intervallo(t, limite) else null
    }

    /** Quante cose da fare ci stanno in uno spazio: una ogni 25 minuti, al più quelle aperte. */
    fun quante(spazio: Intervallo, aperte: Int): Int = minOf(aperte, spazio.minuti / 25)
}
