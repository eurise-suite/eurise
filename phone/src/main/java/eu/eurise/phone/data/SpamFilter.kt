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
