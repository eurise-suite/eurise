package eu.eurise.keyboard

import java.math.BigInteger

/** Riconoscimenti leggeri sul testo scritto o copiato: tutto avviene sulla tastiera, senza Internet. */
object Detect {

    enum class Command(val word: String) { Availability("disp"), File("file"), Person("persona"), Codes("codici") }

    /** "/disp" scritto in fondo (anche "/disponibilità"): apre il pannello; restituisce il pezzo da togliere. */
    fun command(before: String): Pair<Command, String>? {
        val m = Regex("""(?:^|\s)(/([a-zàèéìòù]+))$""").find(before.lowercase()) ?: return null
        val word = m.groupValues[2]
        val cmd = Command.entries.firstOrNull { word.length >= 3 && it.word.startsWith(word.take(it.word.length)) && (word.startsWith(it.word) || it.word.startsWith(word)) }
            ?: return null
        return cmd to m.groupValues[1]
    }

    /** "@Ann" in fondo al testo: almeno due lettere, non dentro un indirizzo email. */
    fun mention(before: String): String? =
        Regex("""(?:^|[\s(])@(\p{L}[\p{L}'’-]{1,30})$""").find(before)?.groupValues?.get(1)

    private val DAY_WORDS = Regex(
        """(?iu)(?<![\p{L}])(oggi|stasera|domani|dopodomani|lunedì|lunedi|martedì|martedi|mercoledì|mercoledi|giovedì|giovedi|venerdì|venerdi|sabato|domenica|alle\s+\d{1,2}|entro\s+il\s+\d{1,2}|il\s+\d{1,2}\s+(gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre))(?![\p{L}])"""
    )
    private val TODO_VERBS = Regex(
        """(?iu)(?<![\p{L}])(chiam\p{L}*|pag\p{L}*|compr\p{L}*|mand\p{L}*|scriv\p{L}*|prenot\p{L}*|ricord\p{L}*|port\p{L}*|ritir\p{L}*|consegn\p{L}*|fare|faccio|devo|dobbiamo|passo\s+a)(?![\p{L}])"""
    )

    /** La frase che si sta scrivendo sembra una cosa da fare con una data ("domani alle 9 chiamo il notaio"). */
    fun looksLikeTask(sentence: String): Boolean {
        val s = sentence.trim()
        return s.length in 8..200 && DAY_WORDS.containsMatchIn(s) && TODO_VERBS.containsMatchIn(s)
    }

    /** L'ultima frase prima del cursore (dopo l'ultimo punto, punto esclamativo, interrogativo o a capo). */
    fun lastSentence(before: String): String = before.split(Regex("[.!?\n]")).last().trim()

    enum class Kind { Iban, Phone, Email }

    /** Riconosce un IBAN (con il controllo), un numero di telefono o un indirizzo email copiati. */
    fun kindOf(text: String): Kind? {
        val t = text.trim()
        if (t.length > 80 || '\n' in t) return null
        val compact = t.replace(" ", "").uppercase()
        return when {
            Regex("""[A-Z]{2}\d{2}[A-Z0-9]{11,30}""").matches(compact) && ibanOk(compact) -> Kind.Iban
            Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""").matches(t) -> Kind.Email
            Regex("""\+?[0-9][0-9 ./-]{6,18}[0-9]""").matches(t) && t.count { it.isDigit() } in 8..15 -> Kind.Phone
            else -> null
        }
    }

    fun ibanOk(iban: String): Boolean {
        val moved = iban.substring(4) + iban.substring(0, 4)
        val digits = buildString { moved.forEach { c -> if (c.isDigit()) append(c) else append(c - 'A' + 10) } }
        return runCatching { BigInteger(digits).mod(BigInteger.valueOf(97)) == BigInteger.ONE }.getOrDefault(false)
    }

    /** Abbreviazioni dell'utente nel formato "ind=Via Roma 1, Torino", una per riga o separate da ";". */
    fun parseAbbreviations(text: String): Map<String, String> = text.split('\n', ';').mapNotNull { line ->
        val i = line.indexOf('=')
        if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
    }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toMap()

    /** L'ultima parola prima del cursore, se il testo finisce proprio con lei. */
    fun lastWord(before: String): String? = Regex("""(?:^|\s)([\p{L}\p{N}]+)$""").find(before)?.groupValues?.get(1)
}
