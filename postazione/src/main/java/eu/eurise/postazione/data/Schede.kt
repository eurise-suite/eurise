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
