package eu.eurise.feature.launcher.summary

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R

/**
 * Misure delle schede sulla griglia del Riepilogo, larga 4 colonne: le schede da 2 colonne si affiancano
 * a due a due, quelle da 4 occupano la riga intera.
 */
enum class CardSize(val columns: Int, val tall: Boolean, @StringRes val label: Int) {
    Small(2, false, R.string.size_small),
    Medium(2, true, R.string.size_medium),
    Wide(4, false, R.string.size_wide),
    Large(4, true, R.string.size_large);

    val half: Boolean get() = columns == 2
}

/** I tipi di scheda disponibili, con le misure che hanno senso per ciascuno. */
enum class CardKind(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: () -> ImageVector,
    val sizes: List<CardSize>,
    val defaultSize: CardSize,
) {
    Calendar("calendar", R.string.card_calendar, R.string.card_calendar_desc, { EuriseIcons.calendar(1.9f) },
        CardSize.entries.toList(), CardSize.Large),
    Messages("messages", R.string.card_messages, R.string.card_messages_desc, { EuriseIcons.chat(1.9f) },
        listOf(CardSize.Medium, CardSize.Wide, CardSize.Large), CardSize.Large),
    Music("music", R.string.card_music, R.string.card_music_desc, { EuriseIcons.music(1.9f) },
        listOf(CardSize.Small, CardSize.Medium, CardSize.Wide), CardSize.Wide),
    Weather("weather", R.string.weather_title, R.string.card_weather_desc, { EuriseIcons.sun(1.9f) },
        CardSize.entries.toList(), CardSize.Medium),
    PinnedNotes("notes", R.string.card_notes, R.string.card_notes_desc, { EuriseIcons.note(1.9f) },
        listOf(CardSize.Medium, CardSize.Wide, CardSize.Large), CardSize.Medium),
    News("news", R.string.card_news, R.string.card_news_desc, { EuriseIcons.news(1.9f) },
        listOf(CardSize.Medium, CardSize.Wide, CardSize.Large), CardSize.Large),
    Stocks("stocks", R.string.card_stocks, R.string.card_stocks_desc, { EuriseIcons.chart(1.9f) },
        CardSize.entries.toList(), CardSize.Wide);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Una scheda messa nel Riepilogo, con la misura scelta. */
data class PlacedCard(val kind: CardKind, val size: CardSize)

/** Schede di partenza a destra della home: Calendario e Comunicazioni. */
val DefaultCards = listOf(
    PlacedCard(CardKind.Calendar, CardSize.Large),
    PlacedCard(CardKind.Messages, CardSize.Large),
)

/** Schede di partenza a sinistra della home: Notizie, Borsa e Musica. */
val DefaultLeftCards = listOf(
    PlacedCard(CardKind.News, CardSize.Large),
    PlacedCard(CardKind.Stocks, CardSize.Wide),
    PlacedCard(CardKind.Music, CardSize.Wide),
)

fun List<PlacedCard>.encode(): String = joinToString("|") { "${it.kind.id}:${it.size.name}" }

fun decodeCards(raw: String?, default: List<PlacedCard> = DefaultCards): List<PlacedCard> {
    if (raw == null) return default
    return raw.split('|').mapNotNull { part ->
        val kind = CardKind.byId(part.substringBefore(':')) ?: return@mapNotNull null
        val size = CardSize.entries.firstOrNull { it.name == part.substringAfter(':') } ?: kind.defaultSize
        PlacedCard(kind, if (size in kind.sizes) size else kind.defaultSize)
    }.distinctBy { it.kind }
}

/**
 * Dispone le schede in righe: una scheda da 4 colonne sta da sola, due schede da 2 si affiancano.
 * Una scheda da 2 rimasta sola occupa metà riga.
 */
fun layoutRows(cards: List<PlacedCard>): List<List<Int>> {
    val rows = mutableListOf<List<Int>>()
    var pending: Int? = null
    cards.forEachIndexed { i, c ->
        if (!c.size.half) {
            pending?.let { rows += listOf(it); pending = null }
            rows += listOf(i)
        } else if (pending == null) {
            pending = i
        } else {
            rows += listOf(pending!!, i)
            pending = null
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}
