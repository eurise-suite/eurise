package eu.eurise.password.logic

import java.security.SecureRandom
import kotlin.math.ln

/** Quanto è robusta una password, dalla stima dei bit di casualità. */
enum class Strength { Weak, Fair, Strong, VeryStrong }

/** Una voce che conviene sistemare, e perché. */
data class HealthIssue(val uuid: String, val weak: Boolean, val reused: Boolean)

/** Controlli sulle carte, senza rete. */
object Cards {
    /** Controllo di Luhn: scopre gli errori di battitura del numero. */
    fun valid(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 12..19 || digits.length != number.count { !it.isWhitespace() && it != '-' }) return false
        val sum = digits.reversed().mapIndexed { i, c ->
            val d = c - '0'
            if (i % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
        }.sum()
        return sum % 10 == 0
    }

    fun lastFour(number: String): String = number.filter { it.isDigit() }.takeLast(4)

    /** Mesi da [today] alla fine del mese di scadenza "MM/AA" (0 è il mese in corso, negativo se scaduta); null se illeggibile. */
    fun monthsLeft(expiry: String, today: java.time.LocalDate): Int? {
        val parts = expiry.trim().split('/', '-')
        if (parts.size != 2) return null
        val month = parts[0].trim().toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val raw = parts[1].trim().toIntOrNull() ?: return null
        val year = if (raw < 100) 2000 + raw else raw
        return (year - today.year) * 12 + month - today.monthValue
    }

    fun expiringSoon(expiry: String, today: java.time.LocalDate): Boolean = monthsLeft(expiry, today)?.let { it <= 1 } ?: false
}

/** Generatore di password e frasi, sempre con il generatore casuale crittografico. */
object Generator {
    private val random = SecureRandom()

    /** 256 parole italiane: ogni parola vale esattamente 8 bit. */
    internal val WORDS: List<String> = (
        "abete acqua aereo agosto albero alce alga alloro alpaca ambra amico anatra " +
        "ancora anello anguria aquila arancia arco argento aria armadio arnia asino astro " +
        "atlante autunno avena azzurro baco bagno balena banco barca basilico bastone battello " +
        "becco betulla bicchiere biscotto bosco bottone bruco bussola cactus caffe calamaro calendula " +
        "camino campana candela cane canoa cappello capra carota carro casa castagna castello " +
        "cavallo cedro cervo cesta chiave chiodo chitarra cicala cigno ciliegia cinghiale cipolla " +
        "cipresso coda colibri collina colomba coltello conchiglia coniglio corda corno corvo cuscino " +
        "delfino diamante diario divano duna edera elefante elica faggio falco farfalla faro " +
        "fico finestra fiume foca foglia fontana formica forno fragola frassino freccia fungo " +
        "gabbiano gallina gatto gelso geranio ghianda giacca giardino ginepro giraffa girasole gomitolo " +
        "gomma grillo gufo iris isola istrice lago lampada lana lanterna larice lavanda " +
        "legno lepre libro lima limone lince lontra lucciola luna lupo macina magnolia " +
        "mandorla mango mare margherita marmo martello matita mela melone menta merlo miele " +
        "mirtillo molino mondo montagna mora mulino muschio nave neve nido nocciola noce " +
        "nuvola oca olivo ombrello onda orchidea orso ortica ostrica palma panca pane " +
        "papavero passero pavone pecora pennello pera pesca pettirosso pianeta pino pioggia piuma " +
        "polpo ponte porto prato quaderno quercia radice ragno rana remo riccio riso " +
        "rondine rosa rospo rubino sabbia salice salmone salvia scala scoglio scoiattolo secchio " +
        "sedano sedia sella sentiero serpente sole sorgente spiga stella stivale strada susina " +
        "tamburo tartaruga tasso tavolo teiera tigre timo tonno topo torre treno trota " +
        "tulipano uva vaso vela veliero vento verbena vigna viola violino volpe zaino " +
        "zattera zenzero zolla zucca"
        ).split(' ')

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!#%&*+-=?@_"

    /** Frase come "Tavolo-Riso-Gufo-41-Vela-Noce": sei parole e un numero, circa 55 bit. */
    fun passphrase(words: Int = 6): String {
        val parts = MutableList(words) { WORDS[random.nextInt(WORDS.size)].replaceFirstChar { c -> c.uppercase() } }
        parts.add(random.nextInt(parts.size + 1), (10 + random.nextInt(90)).toString())
        return parts.joinToString("-")
    }

    /** Caratteri casuali, con almeno uno per tipo. */
    fun password(length: Int = 20): String {
        val all = LOWER + UPPER + DIGITS + SYMBOLS
        val chars = MutableList(length) { all[random.nextInt(all.length)] }
        listOf(LOWER, UPPER, DIGITS, SYMBOLS).forEachIndexed { i, set -> chars[i] = set[random.nextInt(set.length)] }
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return chars.joinToString("")
    }

    /** Stima dal tipo di caratteri e dalla lunghezza: non conosce i dizionari, quindi è un limite superiore. */
    fun strength(password: String): Strength {
        if (password.length < 10) return Strength.Weak
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 20
        val bits = password.toSet().size.coerceAtLeast(password.length / 2) * ln(pool.coerceAtLeast(2).toDouble()) / ln(2.0)
        return when {
            bits < 45 -> Strength.Weak
            bits < 65 -> Strength.Fair
            bits < 90 -> Strength.Strong
            else -> Strength.VeryStrong
        }
    }

    /** Le password deboli o usate in più voci; le voci senza password (note sicure) non contano. */
    fun health(entries: List<Pair<String, String>>): List<HealthIssue> {
        val counts = entries.filter { it.second.isNotEmpty() }.groupingBy { it.second }.eachCount()
        return entries.filter { it.second.isNotEmpty() }.mapNotNull { (uuid, password) ->
            val weak = strength(password) == Strength.Weak
            val reused = (counts[password] ?: 0) > 1
            if (weak || reused) HealthIssue(uuid, weak, reused) else null
        }
    }
}
