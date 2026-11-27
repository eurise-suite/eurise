package eu.eurise.postazione.data

/**
 * Come si apre da sola una linguetta: come il Lavoro (feriali in orario), come il Personale (sera e fine settimana), in
 * viaggio (in auto), oppure mai (solo a mano).
 */
enum class Tipo { Lavoro, Personale, Viaggio, Libera }

/** Una linguetta della Home: [nome] vuoto vuol dire il nome di partenza del suo [tipo]. */
data class Linguetta(val id: String, val nome: String, val tipo: Tipo)

/** I tipi di bolla di una linguetta (tavola «Bolle», 27 novembre 2026). */
enum class TipoBolla { Prossimo, Cercato, Rispondere, DaFare, Farmaci, Meteo, Ascolto, Notizie, Oroscopo, Preferiti, Auto }

/**
 * Le misure delle bolle, come nella Scrivania: Piccola e Media occupano mezza riga (Media è più alta, mostra di più),
 * Larga e Grande la riga intera (Grande mostra di più).
 */
enum class Misura(val colonne: Int, val alta: Boolean) { Piccola(1, false), Media(1, true), Larga(2, false), Grande(2, true) }

data class Bolla(val tipo: TipoBolla, val misura: Misura)

/** Una pagina a sinistra della Home: le sue app e l'AI che si apre con lo swipe dal basso. */
data class Pagina(val id: String, val nome: String, val ai: Ai)

enum class Verso { Su, Destra, Sinistra, Giu }

/** I gesti a due e tre dita, ognuno con un'azione a scelta. */
enum class Gesto(val dita: Int, val verso: Verso) {
    DueSu(2, Verso.Su), DueDestra(2, Verso.Destra), DueSinistra(2, Verso.Sinistra), DueGiu(2, Verso.Giu),
    TreSu(3, Verso.Su), TreDestra(3, Verso.Destra), TreSinistra(3, Verso.Sinistra), TreGiu(3, Verso.Giu);

    companion object {
        fun di(dita: Int, verso: Verso): Gesto? = entries.firstOrNull { it.dita == dita && it.verso == verso }
    }
}

/**
 * Le azioni dei gesti: `fn:` per una funzione, `app:` più la chiave per un'app, vuoto per nessuna. Le funzioni che
 * aprono app usano quelle della suite se ci sono, altrimenti quelle del telefono.
 */
object Azioni {
    const val TELEFONO = "fn:telefono"
    const val FOTOCAMERA = "fn:fotocamera"
    const val NOTA = "fn:nota"
    const val TORCIA = "fn:torcia"
    const val SCANNER = "fn:scanner"
    const val VOCE = "fn:voce"
    const val CERCA = "fn:cerca"
    const val AI = "fn:ai"
    const val TUTTE = "fn:tutte"
    const val FARMACI = "fn:farmaci"
    const val NESSUNA = ""

    val funzioni = listOf(TELEFONO, FOTOCAMERA, NOTA, TORCIA, SCANNER, VOCE, CERCA, AI, TUTTE, FARMACI)

    fun app(chiave: String) = "app:$chiave"
    fun eApp(a: String) = a.startsWith("app:")
    fun chiave(a: String) = a.removePrefix("app:")

    /** Due dita in su apre il Telefono (scelta del proprietario); gli altri di partenza come sulla tavola «Gesti». */
    fun iniziale(g: Gesto): String = when (g) {
        Gesto.DueSu -> TELEFONO
        Gesto.DueDestra -> FOTOCAMERA
        Gesto.DueSinistra -> NOTA
        Gesto.DueGiu -> TORCIA
        Gesto.TreSu -> SCANNER
        Gesto.TreDestra -> VOCE
        Gesto.TreSinistra -> TUTTE
        Gesto.TreGiu -> FARMACI
    }

    fun valida(a: String): Boolean = a == NESSUNA || a in funzioni || (eApp(a) && chiave(a).contains('/'))
}

object Pagine {
    const val MAX_LINGUETTE = 6
    const val MAX_PAGINE = 6

    val linguetteIniziali = listOf(Linguetta("lavoro", "", Tipo.Lavoro), Linguetta("personale", "", Tipo.Personale), Linguetta("viaggio", "", Tipo.Viaggio))

    fun leggiLinguette(testo: String?): List<Linguetta> {
        val lette = testo.orEmpty().lines().mapNotNull { riga ->
            val p = riga.split('\t')
            val tipo = p.getOrNull(2)?.let { t -> Tipo.entries.firstOrNull { it.name == t } }
            if (p.size < 3 || p[0].isBlank() || tipo == null) null else Linguetta(p[0], p[1].take(20), tipo)
        }.distinctBy { it.id }.take(MAX_LINGUETTE)
        return lette.ifEmpty { linguetteIniziali }
    }

    fun scriviLinguette(l: List<Linguetta>): String =
        l.joinToString("\n") { listOf(it.id, pulisci(it.nome), it.tipo.name).joinToString("\t") }

    /** Le bolle di partenza di ogni tipo di linguetta (tavole del 27 novembre). */
    fun bolleIniziali(tipo: Tipo): List<Bolla> = when (tipo) {
        Tipo.Lavoro -> listOf(
            Bolla(TipoBolla.Prossimo, Misura.Larga), Bolla(TipoBolla.Cercato, Misura.Larga),
            Bolla(TipoBolla.Rispondere, Misura.Larga), Bolla(TipoBolla.DaFare, Misura.Grande),
        )
        Tipo.Personale -> listOf(
            Bolla(TipoBolla.Prossimo, Misura.Larga), Bolla(TipoBolla.Rispondere, Misura.Larga),
            Bolla(TipoBolla.Ascolto, Misura.Piccola), Bolla(TipoBolla.Meteo, Misura.Piccola),
            Bolla(TipoBolla.DaFare, Misura.Larga), Bolla(TipoBolla.Oroscopo, Misura.Larga), Bolla(TipoBolla.Notizie, Misura.Grande),
        )
        Tipo.Viaggio -> listOf(
            Bolla(TipoBolla.Prossimo, Misura.Larga), Bolla(TipoBolla.Cercato, Misura.Larga),
            Bolla(TipoBolla.Preferiti, Misura.Larga), Bolla(TipoBolla.Auto, Misura.Larga), Bolla(TipoBolla.Ascolto, Misura.Larga),
        )
        Tipo.Libera -> listOf(Bolla(TipoBolla.Prossimo, Misura.Larga), Bolla(TipoBolla.DaFare, Misura.Larga))
    }

    fun leggiBolle(testo: String?, tipo: Tipo): List<Bolla> {
        if (testo == null) return bolleIniziali(tipo)
        return testo.lines().mapNotNull { riga ->
            val p = riga.split('\t')
            val t = TipoBolla.entries.firstOrNull { it.name == p.getOrNull(0) } ?: return@mapNotNull null
            val m = Misura.entries.firstOrNull { it.name == p.getOrNull(1) } ?: Misura.Larga
            Bolla(t, m)
        }.distinctBy { it.tipo }
    }

    fun scriviBolle(b: List<Bolla>): String = b.joinToString("\n") { it.tipo.name + "\t" + it.misura.name }

    /** La misura dopo, girando tra tutte e quattro. */
    fun prossimaMisura(m: Misura): Misura = Misura.entries[(m.ordinal + 1) % Misura.entries.size]

    fun sposta(b: List<Bolla>, tipo: TipoBolla, verso: Int): List<Bolla> {
        val i = b.indexOfFirst { it.tipo == tipo }
        val j = i + verso
        if (i < 0 || j !in b.indices) return b
        return b.toMutableList().apply { add(j, removeAt(i)) }
    }

    /**
     * Le righe del foglio: una bolla larga sta da sola; due bolle da mezza riga consecutive stanno affiancate; una da mezza
     * riga senza compagna resta da sola, a metà (l'altra metà vuota).
     */
    fun righe(b: List<Bolla>): List<List<Bolla>> {
        val out = mutableListOf<List<Bolla>>()
        var i = 0
        while (i < b.size) {
            val x = b[i]
            if (x.misura.colonne == 1 && i + 1 < b.size && b[i + 1].misura.colonne == 1) { out += listOf(x, b[i + 1]); i += 2 }
            else { out += listOf(x); i += 1 }
        }
        return out
    }

    fun leggiPagine(testo: String?): List<Pagina> = testo.orEmpty().lines().mapNotNull { riga ->
        val p = riga.split('\t')
        if (p.size < 3 || p[0].isBlank()) null else Pagina(p[0], p[1].take(24), Ai.da(p[2]))
    }.distinctBy { it.id }.take(MAX_PAGINE)

    fun scriviPagine(p: List<Pagina>): String = p.joinToString("\n") { listOf(it.id, pulisci(it.nome), it.ai.name).joinToString("\t") }

    fun <T> sposta(lista: List<T>, i: Int, verso: Int): List<T> {
        val j = i + verso
        if (i !in lista.indices || j !in lista.indices) return lista
        return lista.toMutableList().apply { add(j, removeAt(i)) }
    }

    private fun pulisci(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()
}
