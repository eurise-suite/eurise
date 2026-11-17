package eu.eurise.chat

import android.content.Context

/**
 * Le stanze di La mia Bussola su questo telefono, per le notifiche: lì la notifica dice solo la frase fissa, mai il
 * contenuto (specifiche, «Le notifiche non contengono mai dati sanitari»). Solo identificativi di stanza, nelle
 * preferenze private dell'app.
 */
object BussolaRooms {
    private const val PREFS = "bussola"
    private const val KEY = "rooms"

    fun all(context: Context): Set<String> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()

    fun add(context: Context, roomId: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = p.getStringSet(KEY, emptySet()).orEmpty()
        if (roomId !in now) p.edit().putStringSet(KEY, now + roomId).apply()
    }
}

/**
 * I referti mandati da questo telefono e quando scadono (45 giorni, linee guida del Garante sui referti online): alla
 * scadenza chi li ha mandati li cancella dalla stanza (redaction), ad app aperta o dal lavoro ogni 15 minuti. Solo
 * identificativi e date, niente contenuti. Il file cifrato sul server lo toglie la regola di conservazione del server
 * dello studio (Synapse), che la redaction da sola non cancella.
 */
object BussolaReports {
    private const val PREFS = "bussola"
    private const val KEY = "reports"

    private data class Entry(val roomId: String, val eventId: String, val expiresAt: Long) {
        fun line() = "$expiresAt|$roomId|$eventId"
    }

    private fun parse(line: String) = line.split('|', limit = 3).takeIf { it.size == 3 }?.let { p ->
        p[0].toLongOrNull()?.let { Entry(p[1], p[2], it) }
    }

    @Synchronized
    fun add(context: Context, roomId: String, eventId: String, expiresAt: Long) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet(KEY, p.getStringSet(KEY, emptySet()).orEmpty() + Entry(roomId, eventId, expiresAt).line()).apply()
    }

    /** Cancella dalla stanza i referti scaduti; quelli già spariti (stanza lasciata, evento tolto) escono dall'elenco. */
    fun redactDue(context: Context, client: ChatClient, now: Long = System.currentTimeMillis()) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = synchronized(this) { p.getStringSet(KEY, emptySet()).orEmpty() }
        val done = all.mapNotNull { line ->
            val e = parse(line) ?: return@mapNotNull line
            if (e.expiresAt > now) return@mapNotNull null
            val ok = runCatching { client.redact(e.roomId, e.eventId, REASON) }
                .fold({ true }, { err -> err is MatrixException && err.status in listOf(403, 404) })
            line.takeIf { ok }
        }
        if (done.isNotEmpty()) synchronized(this) {
            p.edit().putStringSet(KEY, p.getStringSet(KEY, emptySet()).orEmpty() - done.toSet()).apply()
        }
    }

    private const val REASON = "Referto scaduto: dopo 45 giorni si cancella da solo."
}
