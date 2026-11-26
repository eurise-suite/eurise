package eu.eurise.postazione.data

/**
 * I gruppi della barra in basso: un'icona sola per gruppo, un tocco apre la preferita (la prima), tenere premuto apre il
 * ventaglio con tutte e con «Aggiungi un'app». Ogni gruppo funziona anche senza la suite: se un'app Eurise manca, prende
 * quella che il telefono usa per quel compito (telefono, SMS, posta, browser, mappe, fotocamera).
 */
enum class Gruppo(val predefinite: List<String>) {
    Lavoro(listOf("eu.eurise.office", "eu.eurise.hub/eu.eurise.hub.NotesApp", "eu.eurise.hub/eu.eurise.hub.FileApp")),
    Foto(listOf("eu.eurise.camera|@camera", "eu.eurise.gallery")),
    Mappe(listOf("eu.eurise.maps|@maps")),
    WebAI(listOf("eu.eurise.ai", "eu.eurise.browser|@browser")),
    Comunica(listOf("eu.eurise.phone|@dialer", "eu.eurise.messages|@sms", "eu.eurise.chat", "eu.eurise.mail|@email")),
    Musica(listOf("org.schabi.newpipe", "com.spotify.music", "com.google.android.youtube", "@music")),
    Casa(emptyList()),
    Appunti(listOf("eu.eurise.hub/eu.eurise.hub.NotesApp", "eu.eurise.hub/eu.eurise.hub.ScannerApp")),
}

object Gruppi {
    /** La barra di ogni scheda: Comunica è sempre la quinta, accanto alla lente, così la mano impara dov'è. */
    fun barra(scheda: Scheda): List<Gruppo> = when (scheda) {
        Scheda.Lavoro -> listOf(Gruppo.Lavoro, Gruppo.Foto, Gruppo.Mappe, Gruppo.WebAI, Gruppo.Comunica)
        Scheda.Personale -> listOf(Gruppo.Musica, Gruppo.Casa, Gruppo.Foto, Gruppo.WebAI, Gruppo.Comunica)
        Scheda.Viaggio -> listOf(Gruppo.Mappe, Gruppo.Foto, Gruppo.Appunti, Gruppo.Musica, Gruppo.Comunica)
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
