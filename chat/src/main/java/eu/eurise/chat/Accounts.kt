package eu.eurise.chat

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * I due account della Chat (tavole ChatDueAccount e ChatAccountAggiungi): uno personale e uno di lavoro, ognuno sul suo
 * server, con la sua sessione, le sue chiavi di cifratura e il suo punto di sincronizzazione. Nessun messaggio passa
 * dall'uno all'altro; nell'app se ne vede uno alla volta e dell'altro solo quante non lette ci sono.
 */
enum class Account(val prefs: String) {
    /** Il primo account di sempre: le sue preferenze restano dove stavano, chi aggiorna l'app non si ritrova fuori. */
    Personal("chat_session"),
    Work("chat_session_work");

    val other get() = if (this == Personal) Work else Personal
}

object ChatAccounts {
    private const val PREFS = "chat_accounts"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun active(context: Context): Account =
        runCatching { Account.valueOf(prefs(context).getString("active", null) ?: "") }.getOrDefault(Account.Personal)

    fun setActive(context: Context, a: Account) = prefs(context).edit().putString("active", a.name).apply()

    /** Gli account con una sessione su questo telefono. */
    fun signedIn(context: Context): List<Account> = Account.entries.filter { SessionStore(context, it).load() != null }

    /** Fuori orario il lavoro tace (notifiche senza suono né banner). Acceso di base, come nella tavola. */
    fun workQuiet(context: Context): Boolean = prefs(context).getBoolean("work_quiet", true)
    fun setWorkQuiet(context: Context, on: Boolean) = prefs(context).edit().putBoolean("work_quiet", on).apply()

    /** Il silenzio del lavoro vale adesso? */
    fun quietNow(context: Context, account: Account) = account == Account.Work && workQuiet(context) && Quiet.isQuiet(LocalDateTime.now())
}

/** L'orario del lavoro: dalle 8 alle 19 nei giorni feriali; fuori, il lavoro aspetta. Senza Android: ha i suoi test. */
object Quiet {
    const val FROM_HOUR = 19
    const val TO_HOUR = 8

    fun isQuiet(t: LocalDateTime): Boolean =
        t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY || t.hour >= FROM_HOUR || t.hour < TO_HOUR

    /**
     * Da un'email di lavoro il server da cercare: il dominio dopo la chiocciola (Matrix lo trova poi da sé con il file
     * «.well-known» del dominio). Null se non sembra un'email.
     */
    fun domainOf(email: String): String? {
        val d = email.trim().substringAfterLast('@', "").trim().trimEnd('.').lowercase()
        return d.takeIf { '@' in email && it.contains('.') && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }

    /**
     * Le tacchette di privacy (su 10) di un server: quelli pubblici e grandi vedono chi parla con chi e quando di molte
     * persone (5); quello del tuo lavoro o tuo, no (8). Una stima onesta, scritta accanto.
     */
    fun privacyOf(homeserver: String): Int {
        val host = homeserver.removePrefix("https://").removePrefix("http://").substringBefore('/').lowercase()
        return if (host in PUBLIC || PUBLIC.any { host.endsWith(".$it") }) 5 else 8
    }

    private val PUBLIC = setOf("matrix.org", "matrix-client.matrix.org", "tchncs.de", "envs.net", "mozilla.org", "gitter.im")
}
