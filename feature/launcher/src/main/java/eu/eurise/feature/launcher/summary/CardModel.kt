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
    /** Per le bolle della home messe in una pagina laterale: l'id del loro tipo (vedi HomeKind). */
    val bubbleId: String? = null,
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
        CardSize.entries.toList(), CardSize.Large),
    BubbleAgenda("b_agenda", R.string.welcome_events, R.string.home_agenda_desc, { EuriseIcons.calendar(1.9f) }, CardSize.entries.toList(), CardSize.Large, "agenda"),
    BubbleComms("b_comms", R.string.welcome_comms, R.string.home_comms_desc, { EuriseIcons.chat(1.9f) }, CardSize.entries.toList(), CardSize.Large, "comms"),
    BubbleHealth("b_health", R.string.welcome_health, R.string.home_health_desc, { EuriseIcons.heart(1.9f) }, CardSize.entries.toList(), CardSize.Large, "health"),
    BubbleWork("b_work", R.string.welcome_work, R.string.home_work_desc, { EuriseIcons.folder(1.9f) }, CardSize.entries.toList(), CardSize.Large, "work"),
    BubbleToday("b_today", R.string.bubble_today_name, R.string.bubble_today_desc, { EuriseIcons.task(1.9f) }, CardSize.entries.toList(), CardSize.Large, "today_tasks"),
    BubbleNext("b_next", R.string.bubble_next_title, R.string.bubble_next_desc, { EuriseIcons.map(1.9f) }, CardSize.entries.toList(), CardSize.Large, "next_event"),
    BubbleCodes("b_codes", R.string.bubble_codes_title, R.string.bubble_codes_desc, { EuriseIcons.key(1.9f) }, CardSize.entries.toList(), CardSize.Large, "codes"),
    BubbleMeds("b_meds", R.string.bubble_meds_title, R.string.bubble_meds_desc, { EuriseIcons.medical(1.9f) }, CardSize.entries.toList(), CardSize.Large, "meds"),
    BubbleClock("b_clock", R.string.bubble_clock_title, R.string.bubble_clock_desc, { EuriseIcons.alarm(1.9f) }, CardSize.entries.toList(), CardSize.Large, "clock"),
    BubbleMemo("b_memo", R.string.bubble_memo_title, R.string.bubble_memo_desc, { EuriseIcons.pen(1.9f) }, CardSize.entries.toList(), CardSize.Large, "memo"),
    BubbleVideos("b_videos", R.string.card_videos, R.string.card_videos_desc, { EuriseIcons.play2(1.9f) }, CardSize.entries.toList(), CardSize.Large, "videos");

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Una scheda messa in una pagina, con misura libera e fine: [cols] su [GRID_COLS] colonne sottili e [rows]
 * passi da [ROW_STEP_DP] dp, così il ridimensionamento è continuo e non a scatti. Le schede affiancate
 * hanno la stessa altezza, a meno che l'utente non sciolga il legame ([free]).
 * Il contenuto si adatta alla classe di misura più vicina ([size]).
 */
data class PlacedCard(val kind: CardKind, val cols: Int, val rows: Int, val free: Boolean = false) {
    constructor(kind: CardKind, size: CardSize) : this(kind, size.columns * 6, if (size.tall) TALL_ROWS else LOW_ROWS)

    val size: CardSize
        get() = when {
            cols >= 18 && rows >= 26 -> CardSize.Large
            cols >= 18 -> CardSize.Wide
            rows >= 26 -> CardSize.Medium
            else -> CardSize.Small
        }

    fun resized(newCols: Int, newRows: Int) = copy(cols = newCols.coerceIn(MIN_COLS, GRID_COLS), rows = newRows.coerceIn(MIN_ROWS, MAX_ROWS))
}

const val GRID_COLS = 24
const val MIN_COLS = 6
const val MIN_ROWS = 12
const val MAX_ROWS = 90
const val ROW_STEP_DP = 8
private const val LOW_ROWS = 15
private const val TALL_ROWS = 30

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

fun List<PlacedCard>.encode(): String = "v2|" + joinToString("|") { "${it.kind.id}:${it.cols}:${it.rows}:${if (it.free) 1 else 0}" }

/**
 * Legge le schede salvate. Accetta anche i formati precedenti: misure a nome (es. "calendar:Large") e la
 * griglia a scatti di 4 colonne e righe da 56 dp, che si converte nella griglia fine.
 */
fun decodeCards(raw: String?, default: List<PlacedCard> = DefaultCards): List<PlacedCard> {
    if (raw == null) return default
    val fine = raw.startsWith("v2|")
    return raw.removePrefix("v2|").split('|').mapNotNull { part ->
        val bits = part.split(':')
        val kind = CardKind.byId(bits[0]) ?: return@mapNotNull null
        when {
            fine && bits.size >= 3 -> PlacedCard(kind, 0, 0, bits.getOrNull(3) == "1")
                .resized(bits[1].toIntOrNull() ?: GRID_COLS, bits[2].toIntOrNull() ?: 15)
            bits.size >= 3 -> {
                val c = bits[1].toIntOrNull() ?: 4
                val r = bits[2].toIntOrNull() ?: 2
                PlacedCard(kind, 0, 0).resized(c * 6, Math.round((56f * r + 6f * (r - 1)) / ROW_STEP_DP))
            }
            bits.size == 2 -> PlacedCard(kind, CardSize.entries.firstOrNull { it.name == bits[1] } ?: kind.defaultSize)
            else -> PlacedCard(kind, kind.defaultSize)
        }
    }.distinctBy { it.kind }
}

/**
 * Dispone le schede in righe larghe [GRID_COLS], nell'ordine: una scheda va accanto alla precedente finché c'è
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
