package eu.eurise.browser

import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.privacyScore
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Indirizzi del Browser, senza Android (si provano con i test): cosa scritto nella barra diventa un sito o una
 * ricerca, e i link da condividere perdono i parametri che servono solo a tracciare (tavola BrwCondividi).
 */
object Links {

    /** Motori di ricerca della barra (BrwMotore); Brave Search di base. [level]: grado di privacy su 10, [where]: il paese. */
    data class Engine(val id: String, val label: String, val url: String, val level: Int, val sub: String, val where: Jurisdiction)

    private val SEARCH = privacyScore(DataOut.Minimal, Jurisdiction.EU)
    private val SEARCH_US = privacyScore(DataOut.Minimal, Jurisdiction.US)
    private val PROFILED = privacyScore(DataOut.Profiled, Jurisdiction.US, closed = true)

    val ENGINES = listOf(
        Engine("brave", "Brave Search", "https://search.brave.com/search?q=%s", SEARCH_US, "indice proprio · USA", Jurisdiction.US),
        Engine("qwant", "Qwant", "https://www.qwant.com/?q=%s", SEARCH, "Francia, UE", Jurisdiction.EU),
        Engine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", SEARCH, "Germania, UE", Jurisdiction.EU),
        Engine("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s", SEARCH_US, "risultati Bing · USA", Jurisdiction.US),
        Engine("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s", SEARCH, "risultati Google anonimi · Paesi Bassi", Jurisdiction.EU),
        // Servizi chiusi: si possono scegliere, ma in fondo e con una tacchetta su dieci (regola del 12 novembre: decide la persona).
        Engine("google", "Google", "https://www.google.com/search?q=%s", PROFILED, "Google · USA · le ricerche legate a te e alla pubblicità", Jurisdiction.US),
        Engine("bing", "Bing", "https://www.bing.com/search?q=%s", PROFILED, "Microsoft · USA · le ricerche legate a te e alla pubblicità", Jurisdiction.US),
    )

    fun engine(id: String?): Engine = ENGINES.firstOrNull { it.id == id } ?: ENGINES.first()

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val HOST = Regex("^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|([\\p{L}\\d-]+\\.)+[\\p{L}]{2,})(:\\d+)?(/.*)?$")

    /** Ciò che si scrive nella barra: un indirizzo («eurise.eu», «https://…») o una ricerca col motore [engine]. */
    fun resolve(input: String, engine: Engine): String {
        val t = input.trim()
        if (t.isEmpty()) return "about:blank"
        if (t.contains(' ')) return search(t, engine)
        if (SCHEME.containsMatchIn(t) && (t.startsWith("http") || t.startsWith("about:") || t.startsWith("file:") || t.startsWith("data:"))) return t
        if (HOST.matches(t)) return "https://$t"
        return search(t, engine)
    }

    fun search(q: String, engine: Engine) = engine.url.format(URLEncoder.encode(q.trim(), "UTF-8").replace("+", "%20"))

    /** Se [url] è una pagina di risultati di un nostro motore, la ricerca fatta (per mostrarla nella barra). */
    fun queryOf(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (ENGINES.none { e -> runCatching { URI(e.url.replace("%s", "x")).host }.getOrNull() == host }) return null
        val q = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("q=") }?.substringAfter("q=") ?: return null
        return runCatching { URLDecoder.decode(q, "UTF-8") }.getOrNull()
    }

    /** Il nome da mostrare: «eurise.eu» invece di «https://www.eurise.eu/…». */
    fun host(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

    private val TRACKING = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "mc_eid", "mc_cid", "igshid", "igsh",
        "yclid", "twclid", "ttclid", "li_fat_id", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
        "vero_id", "rb_clickid", "s_cid", "ref_src", "ref_url", "spm", "scm", "si", "feature", "pp",
    )

    /** Il link senza utm_* e gli altri parametri di tracciamento (tavola BrwCondividi). L'ancora resta. */
    fun clean(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val query = uri.rawQuery ?: return url
        val host = uri.host.orEmpty()
        val kept = query.split('&').filter { p ->
            val key = p.substringBefore('=').lowercase()
            val youtubeSi = key == "si" || key == "feature" || key == "pp"
            !(key.startsWith("utm_") || (key in TRACKING && (!youtubeSi || host.contains("youtu"))))
        }
        val base = url.substringBefore('?')
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return if (kept.isEmpty()) base + fragment else base + "?" + kept.joinToString("&") + fragment
    }
}
