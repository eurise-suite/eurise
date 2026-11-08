package eu.eurise.feature.launcher.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.launcher.apps.BarCategory
import eu.eurise.feature.launcher.apps.DefaultBarCategories
import eu.eurise.feature.launcher.apps.decodeAppOrder
import eu.eurise.feature.launcher.apps.decodeCategories
import eu.eurise.feature.launcher.apps.encodeAppOrder
import eu.eurise.feature.launcher.apps.encodeCategories
import eu.eurise.feature.launcher.folders.AppFolder
import eu.eurise.feature.launcher.folders.decodeFolders
import eu.eurise.feature.launcher.folders.encodeFolders
import eu.eurise.feature.launcher.summary.CardKind
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
    /** Colore del simbolo preso dall'icona originale dell'app e portato alla tinta della suite più vicina. */
    val appIconColors: Boolean = true,
    /** Nome per il saluto della home ("Buongiorno, …!"); vuoto per il solo saluto. */
    val userName: String = "",
    val accent: Accent = Accent.Default,
    val cards: List<PlacedCard> = decodeCards(null),
    /** Schede della pagina a sinistra della home. */
    val leftCards: List<PlacedCard> = DefaultLeftCards,
    /** Le schede (bolle) della home, nell'ordine, con larghezza, altezza e legame. */
    val homeSlots: List<eu.eurise.feature.launcher.home.HomeSlot> = eu.eurise.feature.launcher.home.DefaultHomeSlots,
    /** Categorie della barra in basso, da 2 a 8, nell'ordine scelto. */
    val categories: List<BarCategory> = DefaultBarCategories,
    /** App spostate a mano in un'altra categoria: chiave dell'app → id della categoria. */
    val categoryOverrides: Map<String, String> = emptyMap(),
    /** Ordine delle app dentro ogni categoria, scelto trascinandole: id della categoria → chiavi delle app. */
    val appOrder: Map<String, List<String>> = emptyMap(),
    /** App scelte per i pulsanti accanto all'ora (chiave dell'app); nulle per quelle predefinite. */
    val quickGallery: String? = null,
    val quickCamera: String? = null,
    /** Cartelle di app, sulla home o nelle categorie. */
    val folders: List<AppFolder> = emptyList(),
    /** Cosa fa cambiare lo sfondo. */
    val wallpaper: eu.eurise.feature.launcher.home.WallpaperOptions = eu.eurise.feature.launcher.home.WallpaperOptions(),
    /** Tema: 0 segue lo sfondo, 1 sempre chiaro, 2 sempre scuro. */
    val themeMode: Int = 0,
    /** Bolla Codici e consegne: legge i testi delle notifiche per trovarli (spenta finché l'utente non la accende). */
    val readCodes: Boolean = false,
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
    private val _settings = MutableStateFlow(migrate(load()))

    /** Con l'accoglienza la home ospita due righe di preferiti con icone un poco più piccole (una volta sola). */
    private fun migrate(s: LauncherSettings): LauncherSettings {
        if (prefs.getBoolean("welcome_v1", false)) return migrateDaily(s)
        prefs.edit().putBoolean("welcome_v1", true).putInt("grid_rows", maxOf(2, s.rows)).putInt("icon_size", minOf(48, s.iconSize)).apply()
        return migrateDaily(s.copy(rows = maxOf(2, s.rows), iconSize = minOf(48, s.iconSize)))
    }

    /** Le sei bolle nuove arrivano sulla home, dopo quelle che ci sono già (una volta sola; si tolgono dal catalogo). */
    private fun migrateBubbles(s: LauncherSettings): LauncherSettings {
        if (prefs.getBoolean("bubbles_v1", false)) return s
        val kinds = listOf("today_tasks", "next_event", "meds", "clock", "memo", "codes")
            .mapNotNull { eu.eurise.feature.launcher.home.HomeKind.byId(it) }
        val slots = s.homeSlots + kinds.filter { k -> s.homeSlots.none { it.kind == k } }
            .map { eu.eurise.feature.launcher.home.HomeSlot(it, it.defaultWidth) }
        prefs.edit().putBoolean("bubbles_v1", true)
            .putString("home_slots", eu.eurise.feature.launcher.home.encodeHomeSlots(slots)).apply()
        return s.copy(homeSlots = slots)
    }

    /** Oroscopo e Ricetta del giorno arrivano nella pagina a sinistra (una volta sola). */
    private fun migrateDaily(s: LauncherSettings): LauncherSettings {
        if (prefs.getBoolean("daily_v1", false)) return migrateBubbles(s)
        val added = s.leftCards + listOf(CardKind.Horoscope, CardKind.Recipe)
            .filter { k -> s.leftCards.none { it.kind == k } && s.cards.none { it.kind == k } }
            .map { PlacedCard(it, it.defaultSize) }
        prefs.edit().putBoolean("daily_v1", true).putString("left_cards", added.encode()).apply()
        return migrateBubbles(s.copy(leftCards = added))
    }
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
            .putBoolean("app_icon_colors", next.appIconColors)
            .putString("user_name", next.userName)
            .putString("accent", next.accent.encode())
            .putString("cards", next.cards.encode())
            .putString("left_cards", next.leftCards.encode())
            .putString("home_slots", eu.eurise.feature.launcher.home.encodeHomeSlots(next.homeSlots))
            .putString("bar_categories", next.categories.encodeCategories())
            .putString("category_overrides", next.categoryOverrides.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString("app_order", next.appOrder.encodeAppOrder())
            .putString("quick_gallery", next.quickGallery)
            .putString("quick_camera", next.quickCamera)
            .putString("folders", next.folders.encodeFolders())
            .putBoolean("wp_season", next.wallpaper.season)
            .putBoolean("wp_weather", next.wallpaper.weather)
            .putBoolean("wp_holidays", next.wallpaper.holidays)
            .putBoolean("wp_moment", next.wallpaper.moment)
            .putString("wp_fixed", next.wallpaper.fixed)
            .putInt("theme_mode", next.themeMode)
            .putBoolean("read_codes", next.readCodes)
            .apply()
    }

    private fun load() = LauncherSettings(
        columns = prefs.getInt("grid_columns", 5),
        rows = prefs.getInt("grid_rows", 1),
        iconSize = prefs.getInt("icon_size", 56),
        showLabels = prefs.getBoolean("show_labels", true),
        variedIcons = prefs.getBoolean("varied_icons", false),
        appIconColors = prefs.getBoolean("app_icon_colors", true),
        userName = prefs.getString("user_name", "").orEmpty(),
        accent = Accent.decode(prefs.getString("accent", null)),
        cards = decodeCards(prefs.getString("cards", null)),
        leftCards = decodeCards(prefs.getString("left_cards", null), DefaultLeftCards),
        homeSlots = eu.eurise.feature.launcher.home.decodeHome(prefs.getString("home_slots", null)),
        categories = decodeCategories(prefs.getString("bar_categories", null)),
        categoryOverrides = prefs.getString("category_overrides", "").orEmpty().lines()
            .mapNotNull { line -> line.substringBeforeLast('=', "").takeIf { it.isNotEmpty() }?.let { it to line.substringAfterLast('=') } }
            .toMap(),
        appOrder = decodeAppOrder(prefs.getString("app_order", null)),
        quickGallery = prefs.getString("quick_gallery", null),
        quickCamera = prefs.getString("quick_camera", null),
        folders = decodeFolders(prefs.getString("folders", null)),
        wallpaper = eu.eurise.feature.launcher.home.WallpaperOptions(
            season = prefs.getBoolean("wp_season", true),
            weather = prefs.getBoolean("wp_weather", true),
            holidays = prefs.getBoolean("wp_holidays", true),
            moment = prefs.getBoolean("wp_moment", true),
            fixed = prefs.getString("wp_fixed", null),
        ),
        themeMode = prefs.getInt("theme_mode", 0),
        readCodes = prefs.getBoolean("read_codes", false),
    )
}

/** Colore personale da tonalità (0–360) e luminosità (0–1), saturazione fissa per restare nello stile. */
fun customColor(hue: Float, light: Float): Int =
    Color.hsl(hue.coerceIn(0f, 360f), 0.45f, light.coerceIn(0.2f, 0.75f)).toArgb()
