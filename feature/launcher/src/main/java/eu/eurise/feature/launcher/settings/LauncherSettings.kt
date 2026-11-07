package eu.eurise.feature.launcher.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.launcher.summary.DefaultLeftCards
import eu.eurise.feature.launcher.summary.PlacedCard
import eu.eurise.feature.launcher.summary.decodeCards
import eu.eurise.feature.launcher.summary.encode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Colore d'accento della Scrivania: una delle otto tinte della suite oppure un colore scelto dall'utente.
 * Ogni tinta ha una versione per il giorno e una più chiara per la sera.
 */
/**
 * Tinte in più rispetto a quelle dei calendari: completano i colori secondari (un arancio vero e un viola
 * bluastro) e aggiungono cuoio, verde inglese e azzurro costiera. Servono per l'accento e per le icone.
 */
enum class ExtraTint(val light: Color, val dark: Color) {
    Arancio(Color(0xFFB8621E), Color(0xFFF2A866)),
    Viola(Color(0xFF5A4BA0), Color(0xFFB3A6F0)),
    Cuoio(Color(0xFF7A4A2A), Color(0xFFC9956B)),
    VerdeInglese(Color(0xFF1F4D3A), Color(0xFF7FB59A)),
    AzzurroCostiera(Color(0xFF1E78A8), Color(0xFF7CC4EA)),
}

/** Colori per i simboli delle icone quando si sceglie "Vario": tutte le tinte tranne la Grafite. */
val IconPalette: List<Color> =
    CalendarTint.entries.filter { it != CalendarTint.Grafite }.map { it.dark } + ExtraTint.entries.map { it.dark }

sealed interface Accent {
    data class Suite(val tint: CalendarTint) : Accent
    data class Extra(val tint: ExtraTint) : Accent
    data class Custom(val argb: Int) : Accent

    fun color(dark: Boolean): Color = when (this) {
        is Suite -> if (dark) tint.dark else tint.light
        is Extra -> if (dark) tint.dark else tint.light
        is Custom -> Color(argb).let { if (dark) lighten(it) else it }
    }

    fun encode(): String = when (this) {
        is Suite -> "suite:${tint.name}"
        is Extra -> "extra:${tint.name}"
        is Custom -> "custom:$argb"
    }

    companion object {
        val Default: Accent = Suite(CalendarTint.Ottone)

        fun decode(raw: String?): Accent = when {
            raw == null -> Default
            raw.startsWith("suite:") -> CalendarTint.entries.firstOrNull { it.name == raw.removePrefix("suite:") }?.let { Suite(it) } ?: Default
            raw.startsWith("extra:") -> ExtraTint.entries.firstOrNull { it.name == raw.removePrefix("extra:") }?.let { Extra(it) } ?: Default
            raw.startsWith("custom:") -> raw.removePrefix("custom:").toIntOrNull()?.let { Custom(it) } ?: Default
            else -> Default
        }

        /** Sopra le scene scure il colore scelto si schiarisce perché resti leggibile. */
        private fun lighten(c: Color): Color {
            val l = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            if (l > 0.55f) return c
            val k = 0.5f
            return Color(c.red + (1 - c.red) * k, c.green + (1 - c.green) * k, c.blue + (1 - c.blue) * k)
        }
    }
}

/** Impostazioni della home: griglia dei preferiti, icone, accento e schede del Riepilogo. */
data class LauncherSettings(
    val columns: Int = 5,
    val rows: Int = 1,
    val iconSize: Int = 56,
    val showLabels: Boolean = true,
    /** Simboli delle icone colorati per categoria (falso) o con colori vari per app (vero). */
    val variedIcons: Boolean = false,
    val accent: Accent = Accent.Default,
    val cards: List<PlacedCard> = decodeCards(null),
    /** Schede della pagina a sinistra della home. */
    val leftCards: List<PlacedCard> = DefaultLeftCards,
) {
    val maxFavorites: Int get() = columns * rows

    companion object {
        val ColumnChoices = 4..6
        val RowChoices = 1..4
        val IconSizes = 44..68
    }
}

/** Salva le impostazioni sul telefono; nessun account, nessuna sincronizzazione. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_launcher", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(_settings.value).let {
            it.copy(
                columns = it.columns.coerceIn(LauncherSettings.ColumnChoices),
                rows = it.rows.coerceIn(LauncherSettings.RowChoices),
                iconSize = it.iconSize.coerceIn(LauncherSettings.IconSizes),
            )
        }
        _settings.value = next
        prefs.edit()
            .putInt("grid_columns", next.columns)
            .putInt("grid_rows", next.rows)
            .putInt("icon_size", next.iconSize)
            .putBoolean("show_labels", next.showLabels)
            .putBoolean("varied_icons", next.variedIcons)
            .putString("accent", next.accent.encode())
            .putString("cards", next.cards.encode())
            .putString("left_cards", next.leftCards.encode())
            .apply()
    }

    private fun load() = LauncherSettings(
        columns = prefs.getInt("grid_columns", 5),
        rows = prefs.getInt("grid_rows", 1),
        iconSize = prefs.getInt("icon_size", 56),
        showLabels = prefs.getBoolean("show_labels", true),
        variedIcons = prefs.getBoolean("varied_icons", false),
        accent = Accent.decode(prefs.getString("accent", null)),
        cards = decodeCards(prefs.getString("cards", null)),
        leftCards = decodeCards(prefs.getString("left_cards", null), DefaultLeftCards),
    )
}

/** Colore personale da tonalità (0–360) e luminosità (0–1), saturazione fissa per restare nello stile. */
fun customColor(hue: Float, light: Float): Int =
    Color.hsl(hue.coerceIn(0f, 360f), 0.45f, light.coerceIn(0.2f, 0.75f)).toArgb()
