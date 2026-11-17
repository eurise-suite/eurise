package eu.eurise.feature.launcher.home

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import eu.eurise.core.data.modes.Mode
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R

/**
 * Una pagina della Scrivania (tavole ScrPagGestisci, ScrPagNuova, approvate il 17 novembre). Le tre di sempre hanno id
 * fissi ([LEFT], [HOME], [RIGHT]); le altre le crea l'utente, con nome e simbolo. [mode] non nullo: la pagina si vede
 * solo con quella modalità accesa.
 */
data class DeskPage(val id: String, val name: String = "", val symbol: String = "", val mode: Mode? = null) {
    val builtIn: Boolean get() = id == LEFT || id == HOME || id == RIGHT

    companion object {
        const val LEFT = "left"
        const val HOME = "home"
        const val RIGHT = "right"
        val Defaults = listOf(DeskPage(LEFT), DeskPage(HOME), DeskPage(RIGHT))

        /** Un id nuovo, diverso da quelli che ci sono. */
        fun newId(existing: Collection<DeskPage>): String {
            var n = existing.size + 1
            while (existing.any { it.id == "p$n" }) n++
            return "p$n"
        }
    }
}

/** I simboli che si possono dare a una pagina, nella fila delle pagine e in «Le tue pagine». */
enum class PageSymbol(val id: String, val icon: () -> ImageVector) {
    Star("star", { EuriseIcons.star() }),
    Work("work", { EuriseIcons.work() }),
    Home("home", { EuriseIcons.home() }),
    Travel("plane", { EuriseIcons.plane() }),
    Sport("ball", { EuriseIcons.ball() }),
    Study("school", { EuriseIcons.school() }),
    Music("music", { EuriseIcons.music() }),
    Game("game", { EuriseIcons.game() }),
    Family("baby", { EuriseIcons.baby() }),
    Leaf("leaf", { EuriseIcons.leaf() });

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: Star
    }
}

/** Il simbolo di una pagina: per le tre di sempre il loro, per le altre quello scelto. */
fun DeskPage.icon(): ImageVector = when (id) {
    DeskPage.LEFT -> EuriseIcons.news()
    DeskPage.HOME -> EuriseIcons.home()
    DeskPage.RIGHT -> EuriseIcons.today()
    else -> PageSymbol.byId(symbol).icon()
}

/** Il nome delle tre pagine di sempre, se l'utente non gliene ha dato uno. */
@StringRes
fun DeskPage.defaultName(): Int? = when (id) {
    DeskPage.LEFT -> R.string.page_left
    DeskPage.HOME -> R.string.page_home
    DeskPage.RIGHT -> R.string.page_right
    else -> null
}

/** «left\t\t\t|home…»: una riga per pagina con id, nome, simbolo e modalità. */
fun List<DeskPage>.encodePages(): String = joinToString("\n") { p ->
    listOf(p.id, p.name.replace('\t', ' ').replace('\n', ' '), p.symbol, p.mode?.name.orEmpty()).joinToString("\t")
}

/** Le pagine salvate; senza niente di salvato, le tre di sempre. La home c'è sempre. */
fun decodePages(raw: String?): List<DeskPage> {
    if (raw.isNullOrBlank()) return DeskPage.Defaults
    val list = raw.lines().mapNotNull { line ->
        val f = line.split('\t')
        val id = f.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        DeskPage(id, f.getOrNull(1).orEmpty(), f.getOrNull(2).orEmpty(), f.getOrNull(3)?.let { m -> Mode.entries.firstOrNull { it.name == m } })
    }.distinctBy { it.id }
    return if (list.none { it.id == DeskPage.HOME }) list + DeskPage(DeskPage.HOME) else list
}

/** Le bolle delle pagine dell'utente: una riga per pagina, «id\tbolle». */
fun Map<String, List<HomeSlot>>.encodePageSlots(): String =
    entries.joinToString("\n") { (id, slots) -> id + "\t" + slots.encodeHome() }

fun decodePageSlots(raw: String?): Map<String, List<HomeSlot>> =
    raw.orEmpty().lines().mapNotNull { line ->
        val id = line.substringBefore('\t').takeIf { it.isNotBlank() && '\t' in line } ?: return@mapNotNull null
        id to decodeHome(line.substringAfter('\t'))
    }.toMap()

/** Le pagine che si vedono adesso: senza modalità sempre, con una modalità solo quando è accesa. */
fun List<DeskPage>.visible(active: Mode?): List<DeskPage> = filter { it.mode == null || it.mode == active || it.id == DeskPage.HOME }

/** Un'app come bolla di una pagina. */
fun appSlot(key: String) = HomeSlot(HomeKind.App, HomeKind.App.defaultWidth, app = key)

/** Le bolle di partenza di una pagina nuova (tavola ScrPagNuova, «Parti da»). */
enum class PageStart { Empty, AppsAndBubbles, Bubbles, Copy }

fun startSlots(start: PageStart): List<HomeSlot> = when (start) {
    PageStart.Empty, PageStart.Copy -> emptyList()
    PageStart.AppsAndBubbles -> listOf(HomeSlot(HomeKind.NextEvent, 1f), HomeSlot(HomeKind.Memo, 1f))
    PageStart.Bubbles -> listOf(HomeSlot(HomeKind.Agenda, 1f), HomeSlot(HomeKind.TodayTasks, 1f), HomeSlot(HomeKind.Memo, 1f))
}

/** Scorciatoie per la Scrivania (nomi corti dove le funzioni di estensione si confondono con altre). */
object PagesHelper {
    fun visible(pages: List<DeskPage>, active: Mode?): List<DeskPage> = pages.visible(active)

    /** Il simbolo da dare alla copia di una pagina di sempre. */
    fun symbolFor(page: DeskPage): String = when (page.id) {
        DeskPage.HOME -> PageSymbol.Home.id
        else -> PageSymbol.Star.id
    }
}
