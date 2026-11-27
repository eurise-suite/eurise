package eu.eurise.postazione.data

/**
 * Le categorie già pronte della dock: un'icona sola per categoria, un tocco apre la preferita (la prima), tenere premuto
 * apre il ventaglio con tutte, «Aggiungi un'app» e «Modifica la categoria». Ogni categoria funziona anche senza la suite:
 * se un'app Eurise manca, prende quella che il telefono usa per quel compito (telefono, SMS, posta, browser, mappe…).
 */
enum class Gruppo(val icona: String, val predefinite: List<String>) {
    Lavoro("work", listOf("eu.eurise.office", "eu.eurise.hub/eu.eurise.hub.NotesApp", "eu.eurise.hub/eu.eurise.hub.FileApp")),
    Foto("camera", listOf("eu.eurise.camera|@camera", "eu.eurise.gallery")),
    Mappe("map", listOf("eu.eurise.maps|@maps")),
    WebAI("globe", listOf("eu.eurise.browser|@browser", "eu.eurise.ai")),
    Musica("music", listOf("org.schabi.newpipe", "com.spotify.music", "com.google.android.youtube", "@music")),
    Comunica("chat", listOf("eu.eurise.phone|@dialer", "eu.eurise.messages|@sms", "eu.eurise.chat", "eu.eurise.mail|@email")),
    Casa("home", emptyList()),
    Appunti("note", listOf("eu.eurise.hub/eu.eurise.hub.NotesApp", "eu.eurise.hub/eu.eurise.hub.ScannerApp")),
    Agenda("calendar", listOf("eu.eurise.hub/eu.eurise.hub.CalendarApp", "eu.eurise.hub/eu.eurise.hub.TasksApp", "eu.eurise.hub/eu.eurise.hub.AgendaApp")),
    Banca("bank", emptyList()),
    Acquisti("cart", emptyList()),
    Viaggi("plane", listOf("eu.eurise.maps|@maps")),
    Salute("medical", emptyList()),
    Giochi("game", emptyList()),
    Lettura("book", listOf("eu.eurise.read")),
    Sport("fitness", emptyList()),
}

/**
 * Una categoria della dock: [id] è il nome di un [Gruppo] già pronto o `c` più un numero per quelle fatte a mano; [nome]
 * vuoto vuol dire il nome della categoria pronta; [icona] è una chiave delle icone della dock.
 */
data class Categoria(val id: String, val nome: String, val icona: String) {
    val pronta: Gruppo? get() = Gruppo.entries.firstOrNull { it.name == id }
}

object Gruppi {
    /** Quante categorie al più (cassetto a destra della Home); le prime [IN_DOCK] stanno nella dock accanto alla lente. */
    const val MAX = 10
    const val IN_DOCK = 8

    /**
     * Le categorie di partenza, le stesse in ogni linguetta: le icone non cambiano posto, così la mano impara dove sono.
     * Comunica è l'ultima, accanto alla lente.
     */
    val dockIniziale: List<Categoria> = listOf(
        Gruppo.Lavoro, Gruppo.Foto, Gruppo.Mappe, Gruppo.WebAI, Gruppo.Musica, Gruppo.Casa, Gruppo.Agenda, Gruppo.Comunica,
    ).map { Categoria(it.name, "", it.icona) }

    /**
     * Le categorie salvate (una per riga: id, nome, icona separati da tabulazioni); quelle di partenza se mancano. Le
     * dock salvate a sei (fino al 27 novembre 2026) prendono Casa e Agenda prima dell'ultima, come chiesto.
     */
    fun leggiDock(testo: String?): List<Categoria> {
        val lette = testo.orEmpty().lines().mapNotNull { riga ->
            val p = riga.split('\t')
            if (p.size < 3 || p[0].isBlank()) null else Categoria(p[0].trim(), p[1].trim().take(24), p[2].trim())
        }.distinctBy { it.id }.take(MAX)
        if (lette.isEmpty()) return dockIniziale
        if (lette.size == 6) {
            val nuove = listOf(Gruppo.Casa, Gruppo.Agenda).filter { g -> lette.none { it.id == g.name } }.map { Categoria(it.name, "", it.icona) }
            return lette.dropLast(1) + nuove + lette.last()
        }
        return lette
    }

    fun scriviDock(dock: List<Categoria>): String =
        dock.joinToString("\n") { listOf(it.id, it.nome.replace('\t', ' ').replace('\n', ' '), it.icona).joinToString("\t") }

    /** La dock con la categoria [vecchia] sostituita da [nuova] (stesso posto). */
    fun sostituisci(dock: List<Categoria>, vecchia: String, nuova: Categoria): List<Categoria> =
        if (nuova.id != vecchia && dock.any { it.id == nuova.id }) dock.map { if (it.id == vecchia) nuova.copy(id = "c" + System.currentTimeMillis()) else it }
        else dock.map { if (it.id == vecchia) nuova else it }

    /** Sposta la categoria [id] di [verso] posti (−1 a sinistra, +1 a destra). */
    fun sposta(dock: List<Categoria>, id: String, verso: Int): List<Categoria> {
        val i = dock.indexOfFirst { it.id == id }
        val j = i + verso
        if (i < 0 || j !in dock.indices) return dock
        return dock.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** Una categoria nuova in fondo (fino a [MAX]); null se non c'è posto. */
    fun aggiungiCategoria(dock: List<Categoria>, nome: String, icona: String, id: String): Pair<List<Categoria>, Categoria>? {
        if (dock.size >= MAX) return null
        val c = Categoria(id, nome.trim().take(24), icona)
        return (dock + c) to c
    }

    fun togliCategoria(dock: List<Categoria>, id: String): List<Categoria> = if (dock.size <= 1) dock else dock.filter { it.id != id }

    /** Sposta [chiavi] da [da] ad [a] (le toglie da [da]); con [copia] le lascia anche in [da]. */
    fun spostaApp(gruppi: Map<String, List<String>>, chiavi: List<String>, da: String?, a: String, copia: Boolean): Map<String, List<String>> {
        val dest = (gruppi[a].orEmpty() + chiavi).distinct()
        var out = gruppi + (a to dest)
        if (!copia && da != null && da != a) out = out + (da to gruppi[da].orEmpty().filter { it !in chiavi })
        return out
    }

    /**
     * Le app predefinite di un gruppo tra quelle installate ([installate]: chiavi `pacchetto/attività`). Una voce è
     * `pacchetto` (la prima attività di quel pacchetto, anche nella variante `.debug`), `pacchetto/attività` (esatta)
     * oppure `@ruolo` (l'app che il telefono usa per quel compito, da [ruoli]); `a|b` prende la prima che c'è.
     */
    fun risolvi(voci: List<String>, installate: List<String>, ruoli: Map<String, String>): List<String> {
        val out = mutableListOf<String>()
        for (voce in voci) {
            val trovata = voce.split('|').firstNotNullOfOrNull { trova(it.trim(), installate, ruoli) }
            if (trovata != null && trovata !in out) out += trovata
        }
        return out
    }

    private fun trova(voce: String, installate: List<String>, ruoli: Map<String, String>): String? {
        if (voce.startsWith("@")) {
            val pkg = ruoli[voce.removePrefix("@")] ?: return null
            return installate.firstOrNull { it.substringBefore('/') == pkg }
        }
        val pkg = voce.substringBefore('/')
        val cls = voce.substringAfter('/', "")
        val pacchetti = listOf(pkg, "$pkg.debug")
        return pacchetti.firstNotNullOfOrNull { p ->
            installate.firstOrNull { k -> k.substringBefore('/') == p && (cls.isEmpty() || k.substringAfter('/') == cls) }
        }
    }

    /** Il gruppo dopo aver reso preferita [chiave]: va in cima, le altre restano nell'ordine. */
    fun preferita(lista: List<String>, chiave: String): List<String> =
        if (chiave !in lista) lista else listOf(chiave) + lista.filter { it != chiave }

    fun aggiungi(lista: List<String>, chiave: String): List<String> = if (chiave in lista) lista else lista + chiave

    fun togli(lista: List<String>, chiave: String): List<String> = lista.filter { it != chiave }

    /** Le voci salvate di un gruppo, una per riga; tiene le app ancora installate e i siti. */
    fun leggi(testo: String?, installate: Collection<String>): List<String>? =
        testo?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() && (it in installate || Siti.eSito(it)) }

    fun scrivi(lista: List<String>): String = lista.joinToString("\n")
}

/** Un sito messo in un gruppo come se fosse un'app: la voce è `web:` più l'indirizzo. */
object Siti {
    const val PREFISSO = "web:"

    fun eSito(chiave: String) = chiave.startsWith(PREFISSO)
    fun indirizzo(chiave: String) = chiave.removePrefix(PREFISSO)
    fun chiave(indirizzo: String) = PREFISSO + indirizzo

    /**
     * L'indirizzo scritto a mano, messo in ordine: senza schema diventa https; solo http e https (mai `intent:`,
     * `file:`, `javascript:` o altro), con un nome di sito vero e senza nome utente dentro. Null se non va.
     */
    fun normalizza(testo: String): String? {
        val t = testo.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val con = if (t.contains("://")) t else if (t.contains(':') && !t.substringBefore(':').contains('.')) return null else "https://$t"
        val minuscolo = con.lowercase()
        if (!minuscolo.startsWith("https://") && !minuscolo.startsWith("http://")) return null
        val host = con.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty() || '@' in host || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        return con
    }

    /** Il nome da proporre: il sito senza `www.`. */
    fun nomeDa(indirizzo: String): String =
        indirizzo.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.")
}

/** Quante volte hai aperto ogni app dalla Postazione: resta sul telefono, serve per «Più usate». */
object Usi {
    fun leggi(testo: String?): Map<String, Int> = testo.orEmpty().lines().mapNotNull { riga ->
        val n = riga.substringAfterLast('\t', "").toIntOrNull() ?: return@mapNotNull null
        riga.substringBeforeLast('\t').takeIf { it.isNotEmpty() }?.let { it to n }
    }.toMap()

    fun scrivi(usi: Map<String, Int>): String = usi.entries.joinToString("\n") { it.key + "\t" + it.value }

    /** Le [n] più usate tra quelle installate, nell'ordine dell'uso; a parità resta l'ordine alfabetico di [installate]. */
    fun piuUsate(usi: Map<String, Int>, installate: List<String>, n: Int): List<String> =
        installate.filter { (usi[it] ?: 0) > 0 }.sortedByDescending { usi[it] ?: 0 }.take(n)
}

/**
 * Le app di una scheda (Lavoro, Personale, Viaggio): prima quelle fissate (di partenza o scelte da te), poi le più aperte
 * mentre quella scheda era davanti, senza quelle che hai tolto. Tutto resta sul telefono.
 */
object Contesto {
    const val QUANTE = 8

    fun predefinite(scheda: Scheda): List<String> = when (scheda) {
        Scheda.Lavoro -> listOf(
            "eu.eurise.mail|@email", "eu.eurise.office", "eu.eurise.hub/eu.eurise.hub.CalendarApp", "eu.eurise.hub/eu.eurise.hub.TasksApp",
            "eu.eurise.hub/eu.eurise.hub.NotesApp", "eu.eurise.hub/eu.eurise.hub.FileApp", "eu.eurise.hub/eu.eurise.hub.ScannerApp", "eu.eurise.chat",
        )
        Scheda.Personale -> listOf(
            "eu.eurise.messages|@sms", "eu.eurise.gallery", "eu.eurise.browser|@browser", "eu.eurise.chat",
            "@music", "eu.eurise.read", "eu.eurise.hub/eu.eurise.hub.AgendaApp", "eu.eurise.ai",
        )
        Scheda.Viaggio -> listOf(
            "eu.eurise.maps|@maps", "eu.eurise.camera|@camera", "eu.eurise.phone|@dialer", "eu.eurise.messages|@sms",
            "@music", "eu.eurise.hub/eu.eurise.hub.NotesApp", "eu.eurise.gallery", "eu.eurise.browser|@browser",
        )
    }

    /** Le app da mostrare: [fissate] nell'ordine, poi le [usi] più alte tra le installate, al più [QUANTE]. */
    fun mostra(fissate: List<String>, usi: Map<String, Int>, nascoste: Set<String>, installate: List<String>): List<String> {
        val fisse = fissate.filter { it in installate || Siti.eSito(it) }
        val altre = Usi.piuUsate(usi, installate, QUANTE * 2).filter { it !in fisse && it !in nascoste }
        return (fisse + altre).distinct().take(QUANTE)
    }
}
