package eu.eurise.core.cloud.backup

import kotlin.math.ln

/**
 * Quanto resiste una frase segreta, da 0 a 10, per le tacchette sotto il campo. Stima prudente: i bit dell'insieme di
 * caratteri usato, ridotti di molto perché le frasi vere sono parole, non lettere a caso; e una frase sotto i
 * [MIN_LENGTH] caratteri non basta comunque. Non sostituisce un controllo vero, serve a spingere verso frasi lunghe.
 */
object PhraseStrength {
    const val MIN_LENGTH = 10

    fun score(phrase: String): Int {
        if (phrase.isEmpty()) return 0
        var pool = 0
        if (phrase.any { it.isLowerCase() }) pool += 26
        if (phrase.any { it.isUpperCase() }) pool += 26
        if (phrase.any { it.isDigit() }) pool += 10
        if (phrase.any { !it.isLetterOrDigit() }) pool += 33
        val distinct = phrase.lowercase().toSet().size
        // Pochi caratteri diversi («aaaaaaaaaaaa», «abcabcabc»): vale quanto quei pochi.
        val length = minOf(phrase.length, distinct * 3)
        val bits = length * (ln(pool.toDouble()) / ln(2.0)) * 0.6
        val score = (bits / 10).toInt().coerceIn(1, 10)
        return if (phrase.length < MIN_LENGTH) minOf(score, 3) else score
    }
}
