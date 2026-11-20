package eu.eurise.phone.data

import android.content.Context

/**
 * Il filtro antispam del Telefono, tutto sul telefono: nessuna lista esterna, nessun numero che esce. Decide per ogni
 * chiamata in arrivo se lasciarla squillare, farla arrivare in silenzio (resta tra i recenti, segnata «filtrata») o
 * rifiutarla. La Rubrica e chi si è già chiamato passano sempre; i numeri bloccati li rifiuta il sistema.
 */
object SpamFilter {
    enum class Verdict { Ring, Silence, Reject }
    enum class Reason { Hidden, ForeignUnknown, Unknown, Repeated }

    /** Le scelte dell'utente (Telefono › Filtro e numeri bloccati). */
    data class Rules(
        val enabled: Boolean = true,
        /** Numeri nascosti: in silenzio. */
        val hidden: Boolean = false,
        /** Numeri esteri che non sono in Rubrica né chiamati prima (le truffe «squillo e richiama»): in silenzio. */
        val foreignUnknown: Boolean = true,
        /** Tutti gli sconosciuti in silenzio: suona solo chi è in Rubrica o è già stato chiamato. */
        val unknown: Boolean = false,
        /**
         * Chi insiste: un numero estero già fermato due volte in un quarto d'ora viene rifiutato; uno italiano che
         * richiama per la terza volta invece suona (potrebbe essere urgente).
         */
        val repeated: Boolean = true,
    )

    /**
     * La decisione, senza Android di mezzo (si prova nei test). [country] è il prefisso del proprio paese ("39");
     * [recentRejects] quante chiamate dello stesso numero sono state rifiutate o filtrate negli ultimi 15 minuti.
     */
    fun decide(
        rules: Rules,
        number: String?,
        inContacts: Boolean,
        calledBefore: Boolean,
        country: String,
        recentRejects: Int = 0,
    ): Pair<Verdict, Reason?> {
        if (!rules.enabled) return Verdict.Ring to null
        val digits = number?.filter { it.isDigit() || it == '+' }.orEmpty()
        if (digits.isEmpty()) return if (rules.hidden) Verdict.Silence to Reason.Hidden else Verdict.Ring to null
        if (inContacts || calledBefore) return Verdict.Ring to null
        val foreign = isForeign(digits, country)
        if (foreign && rules.foreignUnknown) {
            return if (rules.repeated && recentRejects >= 2) Verdict.Reject to Reason.Repeated else Verdict.Silence to Reason.ForeignUnknown
        }
        if (rules.unknown) return if (rules.repeated && recentRejects >= 2) Verdict.Ring to null else Verdict.Silence to Reason.Unknown
        return Verdict.Ring to null
    }

    /** Estero: prefisso internazionale diverso dal proprio («+216…», «00373…»). I numeri senza prefisso sono nazionali. */
    fun isForeign(digits: String, country: String): Boolean {
        val intl = when {
            digits.startsWith("+") -> digits.drop(1)
            digits.startsWith("00") -> digits.drop(2)
            else -> return false
        }
        return country.isNotEmpty() && !intl.startsWith(country)
    }

    private const val PREFS = "eurise_phone_filter"

    fun rules(context: Context): Rules {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Rules()
        return Rules(
            enabled = p.getBoolean("enabled", d.enabled),
            hidden = p.getBoolean("hidden", d.hidden),
            foreignUnknown = p.getBoolean("foreign", d.foreignUnknown),
            unknown = p.getBoolean("unknown", d.unknown),
            repeated = p.getBoolean("repeated", d.repeated),
        )
    }

    fun save(context: Context, r: Rules) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", r.enabled).putBoolean("hidden", r.hidden).putBoolean("foreign", r.foreignUnknown)
            .putBoolean("unknown", r.unknown).putBoolean("repeated", r.repeated).apply()
    }

    /** Una chiamata fermata dal filtro: resta in un piccolo registro sul telefono per segnarla tra i recenti. */
    data class Filtered(val number: String, val at: Long, val verdict: Verdict, val reason: Reason)

    fun filtered(context: Context): List<Filtered> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "").orEmpty().lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 4) null else runCatching {
                Filtered(p[0], p[1].toLong(), Verdict.valueOf(p[2]), Reason.valueOf(p[3]))
            }.getOrNull()
        }

    fun record(context: Context, f: Filtered) {
        val week = System.currentTimeMillis() - 7 * 86_400_000L
        val kept = (listOf(f) + filtered(context)).filter { it.at >= week }.take(200)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("log", kept.joinToString("\n") { "${it.number}|${it.at}|${it.verdict}|${it.reason}" }).apply()
    }

    /**
     * Prefissi dei call center decisi dall'AGCOM (delibera 156/18/CIR): 0843 per sondaggi e statistiche, 0844 per vendite
     * e ricerche di mercato. Molti call center usano ancora numeri normali: per quelli ci sono i prefissi segnati a mano.
     */
    val CALL_CENTER_PREFIXES = listOf("0843", "0844")

    /** Il numero in forma nazionale, solo cifre: «+39 02 1234» e «0039021234» diventano «021234». */
    fun national(number: String, country: String = "39"): String {
        val d = number.filter { it.isDigit() || it == '+' }
        val intl = when {
            d.startsWith("+") -> d.drop(1)
            d.startsWith("00") -> d.drop(2)
            else -> return d.filter(Char::isDigit)
        }
        return if (intl.startsWith(country)) intl.drop(country.length) else "+$intl"
    }

    /**
     * «Sospetto spam» (richiesta del proprietario, 20 novembre): non blocca, avvisa. Il prefisso che lo fa scattare, o
     * null. Solo per chi non è in rubrica: lo decide chi chiama.
     */
    fun suspect(number: String?, mine: Set<String>, country: String = "39"): String? {
        if (number.isNullOrBlank()) return null
        val n = national(number, country)
        if (n.startsWith("+")) return null
        return (CALL_CENTER_PREFIXES + mine).filter { it.length >= 3 }.firstOrNull { n.startsWith(it) }
    }

    /** Il prefisso del proprio paese, dalla rete o dalla SIM («39» in Italia). */
    fun countryCode(context: Context): String {
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        val iso = (tm?.networkCountryIso?.takeIf { it.isNotBlank() } ?: tm?.simCountryIso ?: "it").uppercase(java.util.Locale.ROOT)
        return CODES[iso] ?: "39"
    }

    private val CODES = mapOf(
            "IT" to "39", "SM" to "378", "VA" to "39", "CH" to "41", "FR" to "33", "DE" to "49", "AT" to "43", "ES" to "34",
            "PT" to "351", "GB" to "44", "IE" to "353", "BE" to "32", "NL" to "31", "LU" to "352", "SI" to "386", "HR" to "385",
            "GR" to "30", "MT" to "356", "PL" to "48", "CZ" to "420", "SE" to "46", "DK" to "45", "FI" to "358", "NO" to "47",
            "RO" to "40", "HU" to "36", "SK" to "421", "BG" to "359", "US" to "1", "CA" to "1",
        )

    /** I prefissi segnati a mano come sospetti (Filtro › Sospetto spam), solo cifre in forma nazionale. */
    fun suspectPrefixes(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("suspect", emptySet()).orEmpty()

    fun setSuspectPrefix(context: Context, prefix: String, on: Boolean) {
        val clean = national(prefix).filter(Char::isDigit)
        if (clean.length < 3) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = suspectPrefixes(context).let { if (on) it + clean else it - clean }
        p.edit().putStringSet("suspect", set).apply()
    }

    /** «Non è un disturbatore»: il numero non passa più dal filtro (resta sul telefono). */
    fun trust(context: Context, number: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet("trusted", emptySet()).orEmpty() + number
        p.edit().putStringSet("trusted", set).putString("log", filtered(context).filterNot { PhoneData.same(it.number, number) }
            .joinToString("\n") { "${it.number}|${it.at}|${it.verdict}|${it.reason}" }).apply()
    }

    fun trusted(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("trusted", emptySet()).orEmpty()
}
