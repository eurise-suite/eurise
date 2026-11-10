package eu.eurise.core.suite

/**
 * Accordo tra Eurise Password ed Eurise Leggi per leggere una carta con la fotocamera. Password apre l'attività di
 * Leggi (solo app con la stessa firma: permesso della suite); Leggi inquadra, legge sul telefono con Tesseract e
 * restituisce numero, scadenza e intestatario nel risultato. Leggi non salva nulla.
 */
object CardContract {
    const val ACTION_SCAN_CARD = "eu.eurise.action.SCAN_CARD"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_EXPIRY = "expiry"
    const val EXTRA_HOLDER = "holder"

    fun readPackage(debug: Boolean) = if (debug) "eu.eurise.read.debug" else "eu.eurise.read"
}

/** Numero, scadenza ("MM/AA") e intestatario letti dal testo di una carta. */
data class CardFields(val number: String, val expiry: String, val holder: String)

/**
 * Dal testo letto sulla faccia di una carta ai suoi campi. Corregge gli scambi tipici della lettura (O e 0, I e 1,
 * S e 5, B e 8) solo dentro i gruppi di cifre; il numero deve superare il controllo di Luhn. Tra più date si tiene la
 * più lontana (le carte scrivono anche "valida dal"). Senza librerie: si prova con i test.
 */
object CardText {
    private val notNames = setOf(
        "VISA", "MASTERCARD", "MAESTRO", "DEBIT", "CREDIT", "DEBITO", "CREDITO", "PREPAID", "PREPAGATA", "BANK", "BANCA",
        "VALID", "THRU", "FROM", "GOOD", "MONTH", "YEAR", "CLASSIC", "GOLD", "PLATINUM", "BUSINESS", "ELECTRON", "CONTACTLESS",
        "POSTEPAY", "POSTE", "NEXI", "INTESA", "SANPAOLO", "UNICREDIT", "AMERICAN", "EXPRESS", "CARD", "CARTA", "WORLD", "ELITE",
        "INFINITE", "SIGNATURE", "MEMBER", "SINCE", "VPAY", "BANCOMAT", "PAGOBANCOMAT", "EVOLUTION", "FINECO", "REVOLUT", "N26",
    )

    fun parse(text: String): CardFields? {
        val number = number(text) ?: return null
        return CardFields(number.chunked(4).joinToString(" "), expiry(text).orEmpty(), holder(text).orEmpty())
    }

    /** Il numero della carta, solo cifre, o null. */
    fun number(text: String): String? {
        // Su una riga con molte cifre, i gruppi fatti solo di cifre e di lettere che le somigliano diventano cifre.
        val fixed = text.lines().joinToString("\n") { line ->
            if (line.count(Char::isDigit) < 8) line
            else line.split(' ').joinToString(" ") { token -> if (token.length >= 3 && token.all { it.isDigit() || it in confusable }) digits(token) else token }
        }
        val candidates = Regex("""(?<!\d)(?:\d[ \-]?){12,18}\d(?!\d)""").findAll(fixed).map { it.value.filter(Char::isDigit) }
        return candidates.firstOrNull { it.length in 13..19 && luhn(it) }
    }

    fun expiry(text: String): String? = Regex("""(?<!\d)(0[1-9]|1[0-2])\s?[/\-]\s?(\d{2})(?!\d)""").findAll(text)
        .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        .maxByOrNull { (m, y) -> y * 12 + m }
        ?.let { (m, y) -> "%02d/%02d".format(m, y) }

    fun holder(text: String): String? = text.lines().map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { line ->
            val words = line.split(' ')
            line.length in 5..26 && words.size in 2..4 && words.all { w -> w.length >= 2 && w.all { it.isLetter() && it.isUpperCase() || it == '\'' || it == '.' } } &&
                words.none { it.trimEnd('.') in notNames }
        }
        .maxByOrNull { it.length }

    private const val confusable = "OoDQIl|LSsBZG"

    private fun digits(token: String) = token.map {
        when (it) { 'O', 'o', 'D', 'Q' -> '0'; 'I', 'l', '|', 'L' -> '1'; 'S', 's' -> '5'; 'B' -> '8'; 'Z' -> '2'; 'G' -> '6'; else -> it }
    }.joinToString("")

    fun luhn(digits: String): Boolean = digits.reversed().mapIndexed { i, c ->
        val d = c - '0'
        if (i % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
    }.sum() % 10 == 0
}
