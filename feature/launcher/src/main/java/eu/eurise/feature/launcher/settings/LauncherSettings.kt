package eu.eurise.feature.launcher.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import eu.eurise.core.designsystem.theme.CalendarTint
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
sealed interface Accent {
    data class Suite(val tint: CalendarTint) : Accent
    data class Custom(val argb: Int) : Accent

    fun color(dark: Boolean): Color = when (this) {
        is Suite -> if (dark) tint.dark else tint.light
        is Custom -> Color(argb).let { if (dark) lighten(it) else it }
    }

    fun encode(): String = when (this) {
        is Suite -> "suite:${tint.name}"
        is Custom -> "custom:$argb"
    }

    companion object {
        val Default: Accent = Suite(CalendarTint.Ottone)

        fun decode(raw: String?): Accent = when {
            raw == null -> Default
            raw.startsWith("suite:") -> CalendarTint.entries.firstOrNull { it.name == raw.removePrefix("suite:") }?.let { Suite(it) } ?: Default
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
    val accent: Accent = Accent.Default,
    val cards: List<PlacedCard> = decodeCards(null),
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
            .putString("accent", next.accent.encode())
            .putString("cards", next.cards.encode())
            .apply()
    }

    private fun load() = LauncherSettings(
        columns = prefs.getInt("grid_columns", 5),
        rows = prefs.getInt("grid_rows", 1),
        iconSize = prefs.getInt("icon_size", 56),
        showLabels = prefs.getBoolean("show_labels", true),
        accent = Accent.decode(prefs.getString("accent", null)),
        cards = decodeCards(prefs.getString("cards", null)),
    )
}

/** Colore personale da tonalità (0–360) e luminosità (0–1), saturazione fissa per restare nello stile. */
fun customColor(hue: Float, light: Float): Int =
    Color.hsl(hue.coerceIn(0f, 360f), 0.45f, light.coerceIn(0.2f, 0.75f)).toArgb()
