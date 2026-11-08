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
    "image" to { s -> EuriseIcons.image(s) }, "play" to { s -> EuriseIcons.play2(s) },
    "phone" to { s -> EuriseIcons.phone(s) }, "mail" to { s -> EuriseIcons.mail(s) },
    "map" to { s -> EuriseIcons.map(s) }, "clock" to { s -> EuriseIcons.clock(s) },
    "chart" to { s -> EuriseIcons.chart(s) }, "calculator" to { s -> EuriseIcons.calculator(s) },
    "person" to { s -> EuriseIcons.person(s) }, "mic" to { s -> EuriseIcons.mic(s) },
    "bag" to { s -> EuriseIcons.bag(s) }, "spoon" to { s -> EuriseIcons.spoon(s) },
    "cloud" to { s -> EuriseIcons.cloud(s) }, "moon" to { s -> EuriseIcons.moon(s) },
    "game" to { s -> EuriseIcons.game(s) }, "car" to { s -> EuriseIcons.car(s) },
    "paw" to { s -> EuriseIcons.paw(s) }, "gift" to { s -> EuriseIcons.gift(s) },
    "school" to { s -> EuriseIcons.school(s) }, "medical" to { s -> EuriseIcons.medical(s) },
    "fitness" to { s -> EuriseIcons.fitness(s) }, "leaf" to { s -> EuriseIcons.leaf(s) },
    "code" to { s -> EuriseIcons.code(s) }, "palette" to { s -> EuriseIcons.palette(s) },
    "lock" to { s -> EuriseIcons.lock(s) }, "coffee" to { s -> EuriseIcons.coffee(s) },
    "baby" to { s -> EuriseIcons.baby(s) }, "pen" to { s -> EuriseIcons.pen(s) },
    "euro" to { s -> EuriseIcons.euro(s) }, "compass" to { s -> EuriseIcons.compass(s) },
    "spark" to { s -> EuriseIcons.spark(s) }, "browser" to { s -> EuriseIcons.browser(s) },
)

/**
 * Una categoria della barra in basso, come l'ha disposta l'utente. Quelle di partenza sono legate a un
 * tipo di app ([builtIn]); quelle aggiunte si riempiono spostandoci le app.
 */
data class BarCategory(val id: String, val label: String, val icon: String) {
    val builtIn: Category? get() = BuiltInIds.entries.firstOrNull { it.value == id }?.key ?: LegacyIds[id]

    fun iconVector(stroke: Float): ImageVector = (CategoryIcons[icon] ?: CategoryIcons.getValue("grid"))(stroke)

    /** Colore dei simboli delle app di questa categoria (variante B delle icone). */
    val tint: Color get() = builtIn?.tint?.dark ?: IconPalette[Math.floorMod(id.hashCode(), IconPalette.size)]

    val isFavorites: Boolean get() = id == FAVORITES

    /** "Tutte": mostra ogni app in ordine alfabetico, senza contenerne nessuna in proprio. */
    val isAll: Boolean get() = id == ALL_APPS

    /** Vero se le app possono stare qui per scelta dell'utente, cioè se ci si può spostare un'app. */
    val holdsApps: Boolean get() = !isFavorites && !isAll
}

const val FAVORITES = "favorites"
const val ALL_APPS = "all"
const val MIN_CATEGORIES = 2
const val MAX_CATEGORIES = 8

private val BuiltInIds = linkedMapOf(
    Category.Favorites to FAVORITES,
    Category.All to ALL_APPS,
    Category.Money to "money",
    Category.Tools to "tools",
    Category.Media to "media",
    Category.Work to "work",
    Category.Communication to "communication",
    Category.Browser to "browser",
    Category.Ai to "ai",
    Category.Other to "other",
)

/** Categorie della prima barra: chi le ha ancora continua a trovarci le app del tipo più vicino. */
private val LegacyIds = mapOf("organization" to Category.Work, "photos" to Category.Media)

/** Le otto di partenza. Preferiti non c'è: quelle app restano ancorate in fondo alla home. */
val DefaultBarCategories: List<BarCategory> = listOf(
    BarCategory(ALL_APPS, Category.All.label, "grid"),
    BarCategory("money", Category.Money.label, "euro"),
    BarCategory("tools", Category.Tools.label, "compass"),
    BarCategory("media", Category.Media.label, "image"),
    BarCategory("work", Category.Work.label, "work"),
    BarCategory("communication", Category.Communication.label, "chat"),
    BarCategory("browser", Category.Browser.label, "browser"),
    BarCategory("ai", Category.Ai.label, "spark"),
)

/** La barra com'era in origine: chi non l'ha mai toccata passa da solo a quella nuova. */
private val FirstBar = listOf(
    BarCategory(FAVORITES, "Preferiti", "star"), BarCategory("communication", "Comunicazione", "chat"),
    BarCategory("organization", "Organizzazione", "calendar"), BarCategory("photos", "Foto e file", "folder"),
    BarCategory("tools", "Strumenti", "tools"), BarCategory("other", "Altre app", "grid"),
)

fun List<BarCategory>.encodeCategories(): String = joinToString("|") { "${it.id};${it.label.replace("|", " ").replace(";", " ")};${it.icon}" }

fun decodeCategories(raw: String?): List<BarCategory> {
    if (raw.isNullOrBlank()) return DefaultBarCategories
    val list = raw.split('|').mapNotNull { part ->
        val bits = part.split(';')
        if (bits.size < 3) null else BarCategory(bits[0], bits[1], bits[2])
    }.distinctBy { it.id }
    // I Preferiti si possono togliere come le altre categorie: la home resta raggiungibile con il tasto Home.
    return if (list.isEmpty() || list == FirstBar) DefaultBarCategories else list
}

/**
 * Decide in quale categoria della barra sta un'app: prima la scelta dell'utente, poi il tipo riconosciuto,
 * poi "Altre app" o, se è stata tolta, l'ultima categoria. Con "Tutte" nella barra un'app non riconosciuta
 * non finisce in una categoria a caso: nullo, e si trova in "Tutte".
 */
fun resolveCategory(entry: AppEntry, bar: List<BarCategory>, overrides: Map<String, String>): BarCategory? {
    val pages = bar.filter { it.holdsApps }
    if (pages.isEmpty()) return null
    overrides[entry.key]?.let { id -> pages.firstOrNull { it.id == id }?.let { return it } }
    pages.firstOrNull { it.builtIn == entry.category }?.let { return it }
    if (bar.any { it.isAll }) return null
    return pages.firstOrNull { it.id == "other" } ?: pages.last()
}

/**
 * Le app nell'ordine scelto trascinandole: prima quelle di [order], poi le altre (nuove o mai spostate)
 * nell'ordine in cui arrivano, che è alfabetico.
 */
fun List<AppEntry>.inUserOrder(order: List<String>?): List<AppEntry> {
    if (order.isNullOrEmpty()) return this
    val position = order.withIndex().associate { (i, key) -> key to i }
    return sortedBy { position[it.key] ?: Int.MAX_VALUE }
}

fun Map<String, List<String>>.encodeAppOrder(): String =
    entries.joinToString("\n") { (category, keys) -> "$category=${keys.joinToString(",")}" }

fun decodeAppOrder(raw: String?): Map<String, List<String>> = raw.orEmpty().lines().mapNotNull { line ->
    val category = line.substringBefore('=', "")
    if (category.isEmpty()) null else category to line.substringAfter('=').split(',').filter { it.isNotEmpty() }
}.toMap()
