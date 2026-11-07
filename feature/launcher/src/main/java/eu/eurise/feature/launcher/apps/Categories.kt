package eu.eurise.feature.launcher.apps

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.settings.IconPalette

/** Simboli tra cui scegliere per le categorie della barra. */
val CategoryIcons: Map<String, (Float) -> ImageVector> = linkedMapOf(
    "star" to { s -> EuriseIcons.star(s) }, "chat" to { s -> EuriseIcons.chat(s) },
    "calendar" to { s -> EuriseIcons.calendar(s) }, "folder" to { s -> EuriseIcons.folder(s) },
    "tools" to { s -> EuriseIcons.tools(s) }, "grid" to { s -> EuriseIcons.grid(s) },
    "heart" to { s -> EuriseIcons.heart(s) }, "home" to { s -> EuriseIcons.home(s) },
    "work" to { s -> EuriseIcons.work(s) }, "cart" to { s -> EuriseIcons.cart(s) },
    "ball" to { s -> EuriseIcons.ball(s) }, "plane" to { s -> EuriseIcons.plane(s) },
    "book" to { s -> EuriseIcons.book(s) }, "music" to { s -> EuriseIcons.music(s) },
    "bank" to { s -> EuriseIcons.bank(s) }, "train" to { s -> EuriseIcons.train(s) },
    "camera" to { s -> EuriseIcons.camera(s) }, "globe" to { s -> EuriseIcons.globe(s) },
    "news" to { s -> EuriseIcons.news(s) }, "shield" to { s -> EuriseIcons.shield(s) },
)

/**
 * Una categoria della barra in basso, come l'ha disposta l'utente. Le sei di partenza sono legate a un
 * tipo di app ([builtIn]); quelle aggiunte si riempiono spostandoci le app.
 */
data class BarCategory(val id: String, val label: String, val icon: String) {
    val builtIn: Category? get() = BuiltInIds.entries.firstOrNull { it.value == id }?.key

    fun iconVector(stroke: Float): ImageVector = (CategoryIcons[icon] ?: CategoryIcons.getValue("grid"))(stroke)

    /** Colore dei simboli delle app di questa categoria (variante B delle icone). */
    val tint: Color get() = builtIn?.tint?.dark ?: IconPalette[Math.floorMod(id.hashCode(), IconPalette.size)]

    val isFavorites: Boolean get() = id == FAVORITES
}

const val FAVORITES = "favorites"
const val MIN_CATEGORIES = 3
const val MAX_CATEGORIES = 7

private val BuiltInIds = linkedMapOf(
    Category.Favorites to FAVORITES,
    Category.Communication to "communication",
    Category.Organization to "organization",
    Category.PhotosFiles to "photos",
    Category.Tools to "tools",
    Category.Other to "other",
)

private val BuiltInIcons = mapOf(
    Category.Favorites to "star", Category.Communication to "chat", Category.Organization to "calendar",
    Category.PhotosFiles to "folder", Category.Tools to "tools", Category.Other to "grid",
)

val DefaultBarCategories: List<BarCategory> =
    Category.entries.map { BarCategory(BuiltInIds.getValue(it), it.label, BuiltInIcons.getValue(it)) }

fun List<BarCategory>.encodeCategories(): String = joinToString("|") { "${it.id};${it.label.replace("|", " ").replace(";", " ")};${it.icon}" }

fun decodeCategories(raw: String?): List<BarCategory> {
    if (raw.isNullOrBlank()) return DefaultBarCategories
    val list = raw.split('|').mapNotNull { part ->
        val bits = part.split(';')
        if (bits.size < 3) null else BarCategory(bits[0], bits[1], bits[2])
    }.distinctBy { it.id }
    return if (list.none { it.isFavorites }) DefaultBarCategories.take(1) + list else list
}

/**
 * Decide in quale categoria della barra sta un'app: prima la scelta dell'utente, poi il tipo riconosciuto,
 * poi "Altre app" o, se è stata tolta, l'ultima categoria.
 */
fun resolveCategory(entry: AppEntry, bar: List<BarCategory>, overrides: Map<String, String>): BarCategory? {
    val pages = bar.filterNot { it.isFavorites }
    if (pages.isEmpty()) return null
    overrides[entry.key]?.let { id -> pages.firstOrNull { it.id == id }?.let { return it } }
    pages.firstOrNull { it.builtIn == entry.category }?.let { return it }
    return pages.firstOrNull { it.id == "other" } ?: pages.last()
}
