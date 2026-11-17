package eu.eurise.core.studio

/**
 * I promemoria per l'assistito della ricetta dematerializzata, stampati in PDF da Millewin (tavola StuMillewin): dal
 * loro testo si prendono il codice NRE e i codici fiscali, per abbinare il promemoria al paziente e alla sua richiesta
 * di rinnovo e mandargli il codice con un tocco. Solo lettura di formati, nessuna interpretazione clinica. Il testo del
 * PDF lo estrae `core/pdf`; qui si lavora sul testo, e si prova con i test.
 */
object Millewin {

    /** Quello che serve di un promemoria: il codice NRE, i codici fiscali trovati (paziente e medico) e il testo. */
    data class Memo(val nre: String, val fiscalCodes: List<String>, val text: String)

    // NRE: 4 cifre, una lettera (il codice della regione, «1500A» in Campania) e 10 cifre. Si cerca anche nel testo
    // senza spazi, perché i PDF a volte spezzano il codice in gruppi.
    private val NRE = Regex("\\d{4}[A-Z]\\d{10}")
    private const val OMO = "[0-9LMNPQRSTUV]"
    private val CF = Regex("[A-Z]{6}$OMO{2}[ABCDEHLMPRST]$OMO{2}[A-Z]$OMO{3}[A-Z]")

    fun parse(text: String): Memo? {
        val upper = text.uppercase()
        val squashed = upper.filterNot { it.isWhitespace() }
        val nre = NRE.find(upper)?.value ?: NRE.find(squashed)?.value ?: return null
        val codes = (CF.findAll(upper).map { it.value } + CF.findAll(squashed).map { it.value })
            .filter(::validFiscalCode).distinct().toList()
        return Memo(nre, codes, text)
    }

    /** Un codice fiscale scritto a mano: maiuscolo, senza spazi; null se non è valido (anche il carattere di controllo). */
    fun fiscalCode(input: String): String? = input.uppercase().filterNot { it.isWhitespace() }.takeIf { CF.matches(it) && validFiscalCode(it) }

    /** Il carattere di controllo (il sedicesimo) torna con quelli prima: così un errore di battitura si vede subito. */
    fun validFiscalCode(cf: String): Boolean {
        if (cf.length != 16) return false
        var sum = 0
        for (i in 0 until 15) {
            val c = cf[i]
            sum += if (i % 2 == 0) ODD[if (c.isDigit()) c - '0' else c - 'A'] else if (c.isDigit()) c - '0' else c - 'A'
        }
        return cf[15] == 'A' + sum % 26
    }

    /** I valori dei caratteri in posizione dispari (prima, terza…): «0» e «A» valgono 1, «1» e «B» 0, e così via. */
    private val ODD = intArrayOf(1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23)

    /**
     * Il promemoria è di questo paziente? Con il codice fiscale nella scheda decide quello; senza, il nome intero
     * (cognome e nome in qualsiasi ordine) deve comparire nel testo.
     */
    fun forPatient(memo: Memo, fiscalCode: String?, name: String): Boolean {
        if (fiscalCode != null) return fiscalCode.uppercase() in memo.fiscalCodes
        val words = name.uppercase().split(Regex("\\s+")).filter { it.length >= 2 }
        val text = memo.text.uppercase()
        return words.size >= 2 && words.all { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }
    }

    /**
     * Quanti dei farmaci chiesti compaiono nel promemoria (per il nome commerciale o il principio attivo: basta la prima
     * parola di almeno quattro lettere). Serve a scegliere, tra più promemoria dello stesso paziente, quello giusto.
     */
    fun drugsFound(memo: Memo, drugs: List<String>): Int {
        val text = memo.text.uppercase()
        return drugs.count { d -> d.uppercase().split(Regex("[^A-ZÀ-Ù]+")).firstOrNull { it.length >= 4 }?.let { text.contains(it) } == true }
    }

    /** Tra i promemoria letti, quello per il paziente e la richiesta: più farmaci in comune, poi il più recente (primo). */
    fun best(memos: List<Memo>, fiscalCode: String?, name: String, drugs: List<String>): Memo? =
        memos.filter { forPatient(it, fiscalCode, name) }.maxByOrNull { drugsFound(it, drugs) }
}
