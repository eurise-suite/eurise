package eu.eurise.core.sync

import java.util.UUID

/** Un calendario o un elenco di attività sul server. [ctag] cambia a ogni modifica: se è uguale, non c'è nulla da fare. */
data class DavCalendar(
    val href: String,
    val name: String,
    val color: String?,
    val events: Boolean,
    val todos: Boolean,
    val ctag: String?,
    val readOnly: Boolean = false,
)

/** Un oggetto del calendario sul server: indirizzo, etag e (se chiesto) il testo iCalendar. */
data class DavObject(val href: String, val etag: String, val data: String? = null)

/**
 * CalDAV (RFC 4791) per Calendario e Attività, come sul telefono: si trova la cartella dei calendari dell'utente,
 * si leggono gli etag di ogni oggetto (pochi byte) e si scarica solo ciò che è cambiato con un `calendar-multiget`.
 * Le scritture sono sempre condizionate: un appuntamento cambiato altrove non si sovrascrive mai (412).
 */
class CalDav(private val http: DavHttp) {

    /** La cartella dei calendari: `current-user-principal` → `calendar-home-set`; se il server non risponde, la base. */
    fun home(): String {
        val principal = runCatching {
            http.propfind("", 0, PRINCIPAL).firstNotNullOfOrNull { it.props["current-user-principal"]?.takeIf(String::isNotBlank) }
        }.getOrNull() ?: return http.base.encodedPath
        return runCatching {
            http.propfind(principal, 0, HOME_SET).firstNotNullOfOrNull { it.props["calendar-home-set"]?.takeIf(String::isNotBlank) }
        }.getOrNull() ?: principal
    }

    fun calendars(home: String = home()): List<DavCalendar> =
        http.propfind(home, 1, CALENDARS)
            .filter { "calendar" in it.types }
            .map { r ->
                val comps = r.types.filter { it.startsWith("comp:") }.map { it.removePrefix("comp:").uppercase() }
                DavCalendar(
                    href = r.href,
                    name = r.props["displayname"].orEmpty().ifBlank { r.href.trimEnd('/').substringAfterLast('/') },
                    color = r.props["calendar-color"]?.takeIf { it.isNotBlank() },
                    events = comps.isEmpty() || "VEVENT" in comps,
                    todos = comps.isEmpty() || "VTODO" in comps,
                    ctag = r.props["getctag"] ?: r.props["sync-token"],
                )
            }

    /** Gli etag di tutti gli oggetti di [component] (VEVENT o VTODO) nel calendario. */
    fun etags(calendar: String, component: String): List<DavObject> =
        http.report(calendar, 1, query(component))
            .filter { !it.collection && it.props["getetag"] != null }
            .map { DavObject(it.href, it.props.getValue("getetag")) }

    /** Scarica il testo degli oggetti [hrefs], a gruppi per non fare richieste enormi. */
    fun fetch(calendar: String, hrefs: List<String>): List<DavObject> =
        hrefs.chunked(50).flatMap { group ->
            http.report(calendar, 1, multiget(group))
                .mapNotNull { r ->
                    val etag = r.props["getetag"] ?: return@mapNotNull null
                    DavObject(r.href, etag, r.props["calendar-data"])
                }
        }

    /** Scrive un oggetto nuovo nel calendario e restituisce indirizzo ed etag (null se il server non lo dice). */
    fun create(calendar: String, ics: String, uid: String = UUID.randomUUID().toString()): Pair<String, String?> {
        val href = calendar.trimEnd('/') + "/" + safe(uid) + ".ics"
        return href to http.put(href, ics, ICS, onlyIfNew = true)
    }

    fun update(href: String, ics: String, etag: String): String? = http.put(href, ics, ICS, ifMatch = etag)

    fun delete(href: String, etag: String) = http.delete(href, ifMatch = etag)

    companion object {
        const val ICS = "text/calendar; charset=utf-8"

        private fun safe(uid: String) = uid.replace(Regex("[^A-Za-z0-9._-]"), "_")

        private const val PRINCIPAL =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>"""
        private const val HOME_SET =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><c:calendar-home-set/></d:prop></d:propfind>"""
        private const val CALENDARS =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:ic="http://apple.com/ns/ical/"><d:prop><d:resourcetype/><d:displayname/><c:supported-calendar-component-set/><cs:getctag/><d:sync-token/><ic:calendar-color/></d:prop></d:propfind>"""

        fun query(component: String) =
            """<?xml version="1.0" encoding="utf-8"?><c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><d:getetag/></d:prop><c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="$component"/></c:comp-filter></c:filter></c:calendar-query>"""

        fun multiget(hrefs: List<String>): String = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?><c:calendar-multiget xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><d:getetag/><c:calendar-data/></d:prop>""")
            hrefs.forEach { append("<d:href>").append(xmlEscape(it)).append("</d:href>") }
            append("</c:calendar-multiget>")
        }

        private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}

/** Lo stato di un oggetto sul computer: [etag] è quello dell'ultima sincronizzazione, [dirty] se cambiato qui da allora. */
data class LocalObject(val href: String?, val uid: String, val etag: String?, val dirty: Boolean, val deleted: Boolean)

/** Cosa fare per allineare computer e server. */
sealed interface CalAction {
    data class Download(val href: String) : CalAction
    data class Upload(val local: LocalObject) : CalAction
    data class Create(val local: LocalObject) : CalAction
    data class DeleteRemote(val href: String, val etag: String) : CalAction
    data class DeleteLocal(val uid: String) : CalAction
    /** Cambiato da entrambe le parti: decide [CalSync.resolve] guardando LAST-MODIFIED. */
    data class Conflict(val local: LocalObject, val href: String) : CalAction
    /** Eliminato qui ma cambiato sul server: vince il server (l'appuntamento torna), mai una perdita. */
    data class Restore(val href: String) : CalAction
}

/**
 * Il confronto tra computer e server, senza rete: dalle due liste esce l'elenco delle azioni. Le regole sono quelle
 * del telefono: ciò che è cambiato da una parte sola passa all'altra; se è cambiato da entrambe vince la modifica più
 * recente, e nulla si cancella se dall'altra parte è stato cambiato nel frattempo.
 */
object CalSync {

    fun plan(local: List<LocalObject>, remote: List<DavObject>): List<CalAction> {
        val out = ArrayList<CalAction>()
        val byHref = remote.associateBy { it.href }
        val known = HashSet<String>()
        for (l in local) {
            val href = l.href
            if (href == null) {
                if (!l.deleted) out += CalAction.Create(l)
                continue
            }
            known += href
            val r = byHref[href]
            when {
                r == null && l.etag == null -> if (!l.deleted) out += CalAction.Create(l)
                r == null -> if (!l.dirty || l.deleted) out += CalAction.DeleteLocal(l.uid) else out += CalAction.Create(l.copy(href = null))
                l.deleted -> out += if (r.etag == l.etag) CalAction.DeleteRemote(href, r.etag) else CalAction.Restore(href)
                r.etag == l.etag -> if (l.dirty) out += CalAction.Upload(l)
                l.dirty -> out += CalAction.Conflict(l, href)
                else -> out += CalAction.Download(href)
            }
        }
        for (r in remote) if (r.href !in known) out += CalAction.Download(r.href)
        return out
    }

    /** Per un conflitto: true se vince la versione del computer (modificata più di recente), false se quella del server. */
    fun resolve(local: CalItem?, remote: CalItem?): Boolean {
        val l = local?.lastModified ?: return false
        val r = remote?.lastModified ?: return true
        return l.isAfter(r)
    }
}
