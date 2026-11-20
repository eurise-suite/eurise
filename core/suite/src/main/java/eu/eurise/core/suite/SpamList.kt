package eu.eurise.core.suite

/**
 * L'elenco Eurise dei numeri molesti (decisione del proprietario, 21 novembre 2026): un file di testo nel repository
 * della suite (`data/spam/elenco.txt`), uguale per tutti. Lo scarica l'app Eurise una volta a settimana col Wi-Fi, se la
 * persona l'ha acceso ([PrivacyService.SpamList]); il Telefono lo riceve dall'app Eurise ([SpamListContract]) e confronta
 * i numeri sul telefono. Nessun numero esce.
 *
 * Formato: la prima riga è [HEADER]; poi una riga per numero o prefisso, in forma internazionale con «+», seguito da
 * tabulazione, categoria e nota facoltativa. Le righe che cominciano con «#» sono commenti; le righe sbagliate si saltano.
 */
object SpamList {
    const val HEADER = "# eurise-spam 1"

    /** Oltre questa misura il file non si accetta (e non passerebbe tra due app). */
    const val MAX_BYTES = 400_000

    /** [prefix]: solo cifre, con il prefisso del paese («216», «390844»). */
    data class Entry(val prefix: String, val category: String, val note: String)

    /** Le voci del file, o null se non è un elenco Eurise. */
    fun parse(text: String): List<Entry>? {
        if (text.length > MAX_BYTES || !text.trimStart('﻿').startsWith(HEADER)) return null
        return text.lineSequence().mapNotNull { line ->
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#")) return@mapNotNull null
            val parts = l.split('\t')
            val number = parts[0].trim()
            if (!NUMBER.matches(number)) return@mapNotNull null
            val category = parts.getOrNull(1)?.trim()?.lowercase()?.takeIf { CATEGORY.matches(it) } ?: "molesto"
            Entry(number.drop(1), category, parts.getOrNull(2)?.trim()?.take(120).orEmpty())
        }.toList()
    }

    /**
     * Il numero in forma internazionale, solo cifre («+39 02 1234» → «39021234»). Senza prefisso vale il paese
     * [country]: in Italia, San Marino e Vaticano lo 0 iniziale resta (fa parte del numero), altrove si toglie.
     */
    fun international(number: String, country: String): String? {
        val d = number.filter { it.isDigit() || it == '+' }
        val digits = d.filter(Char::isDigit)
        if (digits.length < 3) return null
        return when {
            d.startsWith("+") -> digits
            d.startsWith("00") -> digits.drop(2)
            country in KEEP_ZERO -> country + digits
            else -> country + digits.removePrefix("0")
        }
    }

    /** La voce più precisa (il prefisso più lungo) che riguarda [number], o null. */
    fun match(number: String?, entries: List<Entry>, country: String): Entry? {
        val n = number?.let { international(it, country) } ?: return null
        return entries.filter { n.startsWith(it.prefix) }.maxByOrNull { it.prefix.length }
    }

    private val NUMBER = Regex("""\+\d{2,15}""")
    private val CATEGORY = Regex("""[a-z]{1,20}""")
    private val KEEP_ZERO = setOf("39", "378")
}

/**
 * L'elenco dei numeri molesti dall'app Eurise al Telefono. Solo app con la stessa firma (permesso della suite,
 * controllato dal fornitore). Accendere e spegnere passano di qui perché il Telefono non ha il centro privacy.
 */
object SpamListContract {
    fun authority(hubPackage: String) = "$hubPackage.spamlist"

    /** Stato: [KEY_ON], [KEY_UPDATED], [KEY_COUNT]. */
    const val METHOD_STATUS = "status"
    /** Acceso dalla persona dopo l'avviso nel Telefono: scarica subito (col Wi-Fi) e poi una volta a settimana. */
    const val METHOD_ENABLE = "enable"
    /** Spento, senza conferme: l'elenco scaricato si cancella. */
    const val METHOD_DISABLE = "disable"
    /** Scarica adesso, se acceso (col Wi-Fi). */
    const val METHOD_UPDATE = "update"
    /** L'elenco: [KEY_TEXT] (il file com'è) oltre allo stato; niente testo se spento. */
    const val METHOD_LIST = "list"

    const val KEY_ON = "on"
    /** Millisecondi dell'ultimo scaricamento riuscito (0 se mai). */
    const val KEY_UPDATED = "updated"
    const val KEY_COUNT = "count"
    const val KEY_TEXT = "text"
}
