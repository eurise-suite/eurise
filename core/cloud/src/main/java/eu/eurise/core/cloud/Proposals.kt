package eu.eurise.core.cloud

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Agenda con l'assistente (collaborazione rapida, tavole CollAssistente e InsAssistente, 17 novembre 2026). Sul cloud
 * dell'utente (Murena o Nextcloud) c'è un calendario «Proposte», condiviso in modifica con l'assistente: lei ci scrive gli
 * appuntamenti che propone, con qualunque app di calendario. I calendari scelti dall'utente le si condividono in sola
 * lettura, così vede orari e titoli. Confermare porta l'appuntamento nel calendario dell'utente e lo toglie dalle proposte;
 * rifiutare lo lascia nelle proposte segnato come annullato (lei lo vede barrato). Eurise non ha server e non vede nulla.
 *
 * Qui c'è solo la parte che parla con il server; la lettura dell'iCalendar e lo schermo stanno nel Calendario.
 */
object Proposals {
    /** Il calendario delle proposte sul server (nome nella cartella e nome mostrato). */
    const val SLUG = "eurise-proposte"
    const val NAME = "Proposte"

    private const val WHO = "who"
    private const val WHO_NAME = "who_name"
    private const val SEES = "sees"
    private const val TARGET = "target"
    private const val CACHE = "cache"
    private const val NOTIFIED = "notified"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("assistant_agenda", Context.MODE_PRIVATE)

    /** L'assistente: email o nome utente sullo stesso server, e il nome da mostrare. Null se l'agenda non è condivisa. */
    fun assistant(context: Context): Pair<String, String>? =
        prefs(context).let { p -> p.getString(WHO, null)?.let { it to (p.getString(WHO_NAME, null) ?: it) } }

    fun active(context: Context): Boolean = prefs(context).getString(WHO, null) != null

    /** I calendari del server (per nome nella cartella) che l'assistente vede in sola lettura. */
    fun sees(context: Context): Set<String> = prefs(context).getStringSet(SEES, emptySet()).orEmpty()

    /** Il calendario del telefono dove vanno gli appuntamenti confermati (id del Calendar Provider), se scelto. */
    fun target(context: Context): Long? = prefs(context).getLong(TARGET, -1L).takeIf { it >= 0 }

    fun setTarget(context: Context, calendarId: Long) = prefs(context).edit().putLong(TARGET, calendarId).apply()

    /** L'account dove sta l'agenda: quello delle note se sa condividere, altrimenti il primo Murena o Nextcloud. */
    private fun account(context: Context): CloudAccount? {
        val sync = CloudSync.get(context)
        val shareable = setOf(CloudKind.Murena, CloudKind.Nextcloud)
        return sync.accounts.get(sync.settings.value.notesAccount)?.takeIf { it.kind in shareable }
            ?: sync.accounts.accounts.value.firstOrNull { it.kind in shareable }
    }

    fun cloudKind(context: Context): CloudKind? = account(context)?.kind

    private fun calendars(context: Context): WebDav? = account(context)?.let { CloudSync.get(context).accounts.dav(it.id)?.calendars() }

    /** I calendari di appuntamenti dell'utente sul server (non quelli condivisi da altri, non le proposte). */
    suspend fun ownCalendars(context: Context): List<DavCalendar> =
        calendars(context)?.listCalendars().orEmpty().filter { it.events && !it.todo && it.ownerName == null && it.slug != SLUG }

    /**
     * Condivide l'agenda con l'assistente [who] (email o nome utente dello stesso server; [name] per mostrarla): crea
     * le proposte se mancano e le condivide in modifica; i calendari [sees] in sola lettura, togliendo quelli di prima.
     */
    suspend fun setUp(context: Context, who: String, name: String, sees: Set<String>) {
        val cal = calendars(context) ?: throw IllegalStateException("no cloud")
        val previous = assistant(context)?.first
        cal.mkCalendar(SLUG, NAME, events = true)
        if (previous != null && !previous.equals(who.trim(), ignoreCase = true)) unshareAll(cal, previous, sees(context) + SLUG)
        cal.shareCalendar(SLUG, who.trim(), edit = true)
        val removed = if (previous.equals(who.trim(), ignoreCase = true)) sees(context) - sees else emptySet()
        sees.forEach { cal.shareCalendar(it, who.trim(), edit = false) }
        if (removed.isNotEmpty()) unshareAll(cal, who.trim(), removed)
        prefs(context).edit().putString(WHO, who.trim()).putString(WHO_NAME, name.trim().ifEmpty { who.trim() })
            .putStringSet(SEES, sees).apply()
        CloudSync.get(context).schedule()
    }

    /** Smette di condividere: l'assistente non vede più nulla; il calendario delle proposte resta sul server. */
    suspend fun stop(context: Context) {
        val who = assistant(context)?.first ?: return
        calendars(context)?.let { unshareAll(it, who, sees(context) + SLUG) }
        prefs(context).edit().remove(WHO).remove(WHO_NAME).remove(SEES).remove(CACHE).remove(NOTIFIED).apply()
    }

    private suspend fun unshareAll(cal: WebDav, who: String, slugs: Set<String>) {
        val all = cal.listCalendars()
        for (slug in slugs) {
            val member = all.firstOrNull { it.slug == slug }?.members?.firstOrNull { m -> matches(m, who) } ?: continue
            runCatching { cal.unshareCalendar(slug, member.href) }
        }
    }

    /** Vero se il membro [m] è la persona [who] (per email o per nome utente). */
    fun matches(m: DavMember, who: String): Boolean {
        val w = who.trim().lowercase()
        val h = m.href.lowercase()
        return h == "mailto:$w" || h.trimEnd('/').substringAfterLast('/') == w || m.name.equals(who.trim(), ignoreCase = true)
    }

    /** Le proposte sul server (tutte, anche quelle annullate); le tiene anche da parte per mostrarle senza rete. */
    suspend fun fetch(context: Context): List<DavCalendarObject> {
        val cal = calendars(context) ?: return cached(context)
        val list = cal.calendarObjects(SLUG)
        prefs(context).edit().putString(
            CACHE, JSONArray(list.map { JSONObject().put("p", it.path).put("e", it.etag).put("d", it.data) }).toString(),
        ).apply()
        return list
    }

    /** Le ultime proposte lette dal server. */
    fun cached(context: Context): List<DavCalendarObject> = runCatching {
        val a = JSONArray(prefs(context).getString(CACHE, "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> DavCalendarObject(o.getString("p"), o.getString("e"), o.getString("d")) } }
    }.getOrDefault(emptyList())

    /** Toglie una proposta dal server (confermata o scaduta); solo se nessuno l'ha cambiata nel frattempo. */
    suspend fun remove(context: Context, item: DavCalendarObject) {
        calendars(context)?.delete(item.path, item.etag)
        dropFromCache(context, item.path)
    }

    /** Riscrive una proposta (per esempio annullata) sul server. */
    suspend fun replace(context: Context, item: DavCalendarObject, data: String) {
        calendars(context)?.put(item.path, data, "text/calendar; charset=utf-8", ifMatch = item.etag)
        dropFromCache(context, item.path)
    }

    private fun dropFromCache(context: Context, path: String) {
        val left = cached(context).filterNot { it.path == path }
        prefs(context).edit().putString(
            CACHE, JSONArray(left.map { JSONObject().put("p", it.path).put("e", it.etag).put("d", it.data) }).toString(),
        ).apply()
    }

    /** Le proposte già avvisate (per avvisare solo le nuove). */
    fun notified(context: Context): Set<String> = prefs(context).getStringSet(NOTIFIED, emptySet()).orEmpty()

    fun setNotified(context: Context, keys: Set<String>) = prefs(context).edit().putStringSet(NOTIFIED, keys).apply()
}
