package eu.eurise.mail

import android.content.Context
import eu.eurise.core.insieme.Assenza
import java.time.LocalDate

/**
 * L'assenza per email (tavola LavStato): la stessa della Chat (o scritta qui). Durante l'assenza, alle persone che scrivono a
 * una casella di lavoro parte una risposta automatica, una volta per persona e per giorno; mai agli avvisi né alle newsletter.
 */
object MailAbsence {
    private fun prefs(c: Context) = c.getSharedPreferences("mail_absence", Context.MODE_PRIVATE)

    fun load(c: Context): Assenza.State? = runCatching { prefs(c).getString("state", null)?.let { Assenza.decode(org.json.JSONObject(it)) } }.getOrNull()

    fun save(c: Context, s: Assenza.State?) { prefs(c).edit().apply { if (s == null) remove("state") else putString("state", Assenza.encode(s).toString()) }.apply() }

    /** Le risposte automatiche per le email nuove (fuori dal thread principale). */
    fun reply(c: Context, accounts: List<Account>, fresh: List<Summary>, work: Set<String>) {
        val s = load(c)?.takeIf { it.email } ?: return
        val today = LocalDate.now()
        if (!s.away(today)) return
        val p = prefs(c)
        val until = LocalDate.ofEpochDay(s.to).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.getDefault()))
        val text = Assenza.reply(s, until) { u, sub -> c.getString(if (sub.isBlank()) R.string.ml_ab_auto else R.string.ml_ab_auto_sub, u, sub) }
        fresh.filter { it.kind == Kind.People && (work.isEmpty() || it.accountId in work) }.distinctBy { it.fromAddress.lowercase() }.forEach { m ->
            val key = m.fromAddress.lowercase() + "|" + today.toEpochDay()
            if (p.getBoolean(key, false) || accounts.any { it.email.equals(m.fromAddress, true) }) return@forEach
            val a = accounts.firstOrNull { it.id == m.accountId } ?: return@forEach
            if (runCatching { MailClient(a).send(m.fromAddress, "", MailText.replySubject(m.subject), text, null) }.isSuccess) p.edit().putBoolean(key, true).apply()
        }
    }
}
