package eu.eurise.core.data.alfred

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import eu.eurise.core.data.weather.MeteoText
import eu.eurise.core.data.weather.Now
import eu.eurise.core.data.weather.Sky
import java.time.LocalDate
import java.time.LocalTime

/** Che cosa dice Alfred quando sveglia (tavola AlfSveglia): saluto con ora e meteo, una massima, un brano. */
data class AlfredWakeUp(val greeting: String, val motto: String, val pearl: Pearl) {
    /** Il testo letto ad alta voce, prima della musica. */
    val spoken: String get() = "$greeting ${motto.trim('«', '»')} Le propongo ${pearl.artist}, «${pearl.title}». ${pearl.comment}"
}

/**
 * Le parole della sveglia di Alfred. Tutto sul telefono: l'ora, il meteo già scaricato (se il servizio è acceso) e il
 * catalogo di Alfred, che cambia brano e massima ogni giorno.
 */
object AlfredWake {
    private val HOURS = listOf(
        "mezzanotte", "l'una", "le due", "le tre", "le quattro", "le cinque", "le sei", "le sette", "le otto", "le nove",
        "le dieci", "le undici", "mezzogiorno",
    )

    private val asides = listOf(
        "il caffè non si fa da solo e, temo, nemmeno io posso farlo.",
        "la giornata l'aspetta, con la pazienza che le è propria.",
        "le tende si aprono meglio con un po' di musica.",
        "il mondo è già sveglio; nulla che non possa aspettare un minuto.",
        "un buon inizio è mezza giornata, come dicevano le nonne.",
    )

    /** L'ora come la direbbe una persona: «Sono le sette», «Sono le sei e mezza», «Sono le 6:40». */
    fun hour(t: LocalTime): String {
        val h = t.hour % 12
        val word = if (t.hour == 12) HOURS[12] else if (t.hour == 0) HOURS[0] else HOURS[h]
        val verb = if (word.startsWith("le ")) "Sono" else "È"
        return when (t.minute) {
            0 -> "$verb $word"
            15 -> "$verb $word e un quarto"
            30 -> "$verb $word e mezza"
            45 -> "$verb $word e tre quarti"
            else -> "Sono le %d:%02d".format(t.hour, t.minute)
        }
    }

    /** Cosa mettersi, in due parole, dalla temperatura e dal cielo. */
    fun dress(temp: Int, sky: Sky): String = when {
        sky == Sky.Rain || sky == Sky.Showers || sky == Sky.Storm -> "giornata da ombrello"
        sky == Sky.Snow -> "giornata da scarponi"
        temp <= 4 -> "giornata da cappotto pesante"
        temp <= 12 -> "giornata da cappotto leggero"
        temp <= 19 -> "basta una giacca"
        temp <= 26 -> "maniche corte, con giudizio"
        else -> "giornata da ombra e acqua fresca"
    }

    /** [address] è l'appellativo già pronto («, Giulia»); [now] il meteo di adesso, se c'è. */
    fun compose(day: LocalDate, time: LocalTime, address: String, now: Now?): AlfredWakeUp {
        val hello = if (time.hour < 12) "Buongiorno" else if (time.hour < 18) "Buon pomeriggio" else "Buonasera"
        val aside = asides[Math.floorMod(day.dayOfYear * 3 + day.year, asides.size)]
        val weather = now?.let { " Fuori ${it.temp}°, ${MeteoText.sky(it.sky).lowercase()}: ${dress(it.temp, it.sky)}." }.orEmpty()
        val greeting = "$hello$address. ${hour(time)}, $aside$weather"
        return AlfredWakeUp(greeting, AlfredCatalog.motto(day, evening = false), AlfredCatalog.pearl(day, evening = false))
    }

    /**
     * Il brano tra quelli del telefono, se c'è (titolo e artista simili). Serve il permesso di leggere la musica: senza,
     * null. Non esce nulla.
     */
    fun localTrack(context: Context, pearl: Pearl): Uri? = runCatching {
        val words = pearl.title.substringBefore(',').trim()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.TITLE} LIKE ? AND ${MediaStore.Audio.Media.ARTIST} LIKE ?",
            arrayOf("%$words%", "%${pearl.artist.substringAfterLast(' ')}%"),
            null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null }
    }.getOrNull()
}
