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
        CardSize.entries.toList(), CardSize.Wide),
    Horoscope("horoscope", R.string.card_horoscope, R.string.card_horoscope_desc, { EuriseIcons.zodiac(4, 1.9f) },
        CardSize.entries.toList(), CardSize.Wide),
    Recipe("recipe", R.string.card_recipe, R.string.card_recipe_desc, { EuriseIcons.spoon(1.9f) },
        CardSize.entries.toList(), CardSize.Large);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Una scheda messa in una pagina, con la sua misura libera: [cols] colonne su 4 e [rows] righe da 56 dp.
 * Il contenuto si adatta alla classe di misura più vicina ([size]).
 */
data class PlacedCard(val kind: CardKind, val cols: Int, val rows: Int) {
    constructor(kind: CardKind, size: CardSize) : this(kind, size.columns, if (size.tall) 4 else 2)

    val size: CardSize
        get() = when {
            cols >= 3 && rows >= 4 -> CardSize.Large
            cols >= 3 -> CardSize.Wide
            rows >= 4 -> CardSize.Medium
            else -> CardSize.Small
        }

    fun resized(newCols: Int, newRows: Int) = copy(cols = newCols.coerceIn(MIN_COLS, GRID_COLS), rows = newRows.coerceIn(MIN_ROWS, MAX_ROWS))
}

const val GRID_COLS = 4
const val MIN_COLS = 1
const val MIN_ROWS = 2
const val MAX_ROWS = 9

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
    PlacedCard(CardKind.Horoscope, CardSize.Wide),
    PlacedCard(CardKind.Recipe, CardSize.Large),
)

fun List<PlacedCard>.encode(): String = joinToString("|") { "${it.kind.id}:${it.cols}:${it.rows}" }

/** Legge le schede salvate; accetta anche il formato precedente con le misure a nome (es. "calendar:Large"). */
fun decodeCards(raw: String?, default: List<PlacedCard> = DefaultCards): List<PlacedCard> {
    if (raw == null) return default
    return raw.split('|').mapNotNull { part ->
        val bits = part.split(':')
        val kind = CardKind.byId(bits[0]) ?: return@mapNotNull null
        when {
            bits.size >= 3 -> PlacedCard(kind, bits[1].toIntOrNull() ?: 4, bits[2].toIntOrNull() ?: 2).resized(bits[1].toIntOrNull() ?: 4, bits[2].toIntOrNull() ?: 2)
            bits.size == 2 -> PlacedCard(kind, CardSize.entries.firstOrNull { it.name == bits[1] } ?: kind.defaultSize)
            else -> PlacedCard(kind, kind.defaultSize)
        }
    }.distinctBy { it.kind }
}

/**
 * Dispone le schede in righe da 4 colonne, nell'ordine: una scheda va accanto alla precedente finché c'è
 * spazio, altrimenti passa alla riga sotto.
 */
fun layoutRows(cards: List<PlacedCard>): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var used = GRID_COLS
    cards.forEachIndexed { i, c ->
        if (used + c.cols > GRID_COLS) {
            rows += mutableListOf(i); used = c.cols
        } else {
            rows.last() += i; used += c.cols
        }
    }
    return rows
}
