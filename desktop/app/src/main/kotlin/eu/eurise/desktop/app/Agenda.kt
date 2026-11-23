package eu.eurise.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.core.ponte.Json
import eu.eurise.core.sync.CalAction
import eu.eurise.core.sync.CalDav
import eu.eurise.core.sync.CalItem
import eu.eurise.core.sync.CalSync
import eu.eurise.core.sync.CalTime
import eu.eurise.core.sync.DavCalendar
import eu.eurise.core.sync.DavHttp
import eu.eurise.core.sync.ICal
import eu.eurise.core.sync.LocalObject
import eu.eurise.desktop.dataDir
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Calendario e Attività sul computer: una copia locale (file `agenda.jsonl`, leggibile solo dall'utente) dei
 * calendari e degli elenchi CalDAV del cloud collegato, gli stessi del telefono. Le modifiche fatte qui si segnano
 * «da mandare» e partono alla sincronizzazione seguente (subito, e poi ogni cinque minuti con la finestra aperta),
 * con le regole del telefono ([CalSync]): nulla si perde, nei conflitti vince la modifica più recente.
 */
class Agenda(private val dir: File = dataDir()) {
    /** Un oggetto del calendario come lo tiene il computer: [ics] è il file iCalendar intero. */
    data class Entry(
        val calendar: String,
        val href: String?,
        val uid: String,
        val etag: String?,
        val ics: String,
        val dirty: Boolean = false,
        val deleted: Boolean = false,
    ) {
        val items: List<CalItem> by lazy { runCatching { ICal.items(ics) }.getOrDefault(emptyList()) }
    }

    var account by mutableStateOf(Cloud.account())
        private set
    var calendars by mutableStateOf(readCalendars())
        private set
    var entries by mutableStateOf(readEntries())
        private set
    var syncing by mutableStateOf(false)
        private set
    var lastSync by mutableStateOf<Instant?>(readLastSync())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** L'accesso in corso nel browser (pagina aperta, si aspetta l'approvazione). */
    var connecting by mutableStateOf<String?>(null)
        private set
    /** Vero se la password per le app sta nel portachiavi (falso: solo fino alla chiusura). */
    var inKeyring by mutableStateOf(true)
        private set

    private val lock = Any()
    @Volatile private var started = false
    @Volatile private var cancelLogin = false

    /** Avvia la sincronizzazione periodica (una volta sola). */
    fun start() {
        if (started) return
        started = true
        thread(isDaemon = true, name = "agenda") {
            while (true) {
                if (account != null) sync()
                Thread.sleep(5 * 60_000L)
            }
        }
    }

    // --- Collegamento ---

    /** «Collega»: apre il browser sulla pagina di accesso del server e aspetta l'approvazione (al più dieci minuti). */
    fun connect(server: String) {
        cancelLogin = false
        connecting = Cloud.normalize(server)
        error = null
        thread(isDaemon = true, name = "accesso-cloud") {
            val result = runCatching {
                val start = Cloud.start(server)
                openBrowser(start.loginUrl)
                val until = System.currentTimeMillis() + 10 * 60_000L
                var got: Pair<CloudAccount, String>? = null
                while (got == null && !cancelLogin && System.currentTimeMillis() < until) {
                    Thread.sleep(2_000)
                    got = Cloud.poll(start)
                }
                got
            }
            connecting = null
            result.onFailure { error = it.message ?: "Il server non risponde" }
            result.getOrNull()?.let { (acc, password) ->
                inKeyring = Cloud.save(acc, password)
                account = acc
                sync()
            }
        }
    }

    fun cancelConnect() { cancelLogin = true; connecting = null }

    /** Scollega il computer: via il collegamento e la copia locale (sul cloud e sul telefono non cambia nulla). */
    fun disconnect() {
        Cloud.forget()
        synchronized(lock) {
            account = null
            calendars = emptyList()
            entries = emptyList()
            lastSync = null
            File(dir, FILE).delete(); File(dir, CALENDARS).delete(); File(dir, LAST).delete()
        }
    }

    // --- Modifiche fatte qui ---

    /** Un appuntamento o un'attività nuova nel calendario [calendar]. */
    fun create(calendar: String, todo: Boolean, summary: String, start: CalTime?, end: CalTime?, location: String = "", description: String = "") {
        val uid = UUID.randomUUID().toString()
        val ics = ICal.create(uid, todo, summary, start, end, location, description)
        change { it + Entry(calendar, null, uid, null, ics, dirty = true) }
    }

    /** Cambia i campi principali di [uid] tenendo tutto il resto del file (anche ciò che hanno scritto altre app). */
    fun update(uid: String, summary: String, start: CalTime?, end: CalTime?, location: String, description: String, completed: Boolean? = null) {
        change { list ->
            list.map { e ->
                if (e.uid != uid) return@map e
                val root = ICal.parse(e.ics)
                val main = root.children.firstOrNull { (it.name == "VEVENT" || it.name == "VTODO") && it.first("RECURRENCE-ID") == null } ?: return@map e
                ICal.update(main, summary, start, end, location, description, completed)
                e.copy(ics = ICal.write(root), dirty = true)
            }
        }
    }

    /** Spunta o toglie la spunta a un'attività. */
    fun complete(uid: String, done: Boolean) {
        val item = entries.firstOrNull { it.uid == uid }?.items?.firstOrNull() ?: return
        update(uid, item.summary, item.start, item.end, item.location, item.description, done)
    }

    /** Elimina: si segna e parte alla sincronizzazione; se intanto è cambiato sul server, torna (mai una perdita). */
    fun delete(uid: String) = change { list -> list.mapNotNull { e -> if (e.uid != uid) e else if (e.href == null) null else e.copy(deleted = true, dirty = true) } }

    private fun change(f: (List<Entry>) -> List<Entry>) {
        synchronized(lock) {
            entries = f(entries)
            writeEntries(entries)
        }
        if (account != null) thread(isDaemon = true, name = "agenda-invio") { sync() }
    }

    // --- Sincronizzazione ---

    fun sync() {
        val acc = account ?: return
        synchronized(syncLock) {
            syncing = true
            try {
                val password = Cloud.password(acc) ?: run { error = "Il portachiavi è chiuso o il collegamento è scaduto: collega di nuovo"; return }
                val dav = CalDav(DavHttp(acc.calDav, acc.davUser, password))
                val found = dav.calendars(dav.home())
                calendars = found
                writeCalendars(found)
                for (cal in found) syncCalendar(dav, cal)
                // Via la copia dei calendari che non ci sono più sul server.
                val hrefs = found.map { it.href }.toSet()
                synchronized(lock) { entries = entries.filter { it.calendar in hrefs || it.href == null && it.calendar in hrefs }; writeEntries(entries) }
                lastSync = Instant.now()
                File(dir, LAST).writeText(lastSync.toString())
                error = null
            } catch (e: Exception) {
                error = when (e.message?.contains("401") == true) {
                    true -> "Il cloud non accetta più il collegamento (password per le app revocata?): collega di nuovo"
                    false -> "Non riesco a raggiungere il cloud: riprovo tra poco"
                }
            } finally {
                syncing = false
            }
        }
    }

    private fun syncCalendar(dav: CalDav, cal: DavCalendar) {
        val remote = buildList {
            if (cal.events) addAll(dav.etags(cal.href, "VEVENT"))
            if (cal.todos) addAll(dav.etags(cal.href, "VTODO"))
        }.distinctBy { it.href }
        val local = synchronized(lock) { entries.filter { it.calendar == cal.href } }
        val actions = CalSync.plan(local.map { LocalObject(it.href, it.uid, it.etag, it.dirty, it.deleted) }, remote)
        val byUid = local.associateBy { it.uid }
        val downloads = ArrayList<String>()
        for (a in actions) runCatching {
            when (a) {
                is CalAction.Download -> downloads += a.href
                is CalAction.Restore -> downloads += a.href
                is CalAction.Create -> if (!cal.readOnly) byUid[a.local.uid]?.let { e ->
                    val (href, etag) = dav.create(cal.href, e.ics, e.uid)
                    replace(e.uid) { it.copy(href = href, etag = etag ?: fetchEtag(dav, href), dirty = false) }
                }
                is CalAction.Upload -> if (!cal.readOnly) byUid[a.local.uid]?.let { e ->
                    val etag = dav.update(e.href!!, e.ics, e.etag!!)
                    replace(e.uid) { it.copy(etag = etag ?: fetchEtag(dav, e.href), dirty = false) }
                }
                is CalAction.DeleteRemote -> {
                    if (!cal.readOnly) dav.delete(a.href, a.etag)
                    synchronized(lock) { entries = entries.filterNot { it.href == a.href } }
                }
                is CalAction.DeleteLocal -> synchronized(lock) { entries = entries.filterNot { it.uid == a.uid && it.calendar == cal.href } }
                is CalAction.Conflict -> {
                    val server = dav.fetch(cal.href, listOf(a.href)).firstOrNull()
                    val mine = byUid[a.local.uid]
                    if (server?.data != null && mine != null) {
                        val keepMine = CalSync.resolve(mine.items.firstOrNull(), runCatching { ICal.items(server.data!!).firstOrNull() }.getOrNull())
                        if (keepMine && !cal.readOnly) {
                            val etag = dav.update(a.href, mine.ics, server.etag)
                            replace(mine.uid) { it.copy(etag = etag ?: fetchEtag(dav, a.href), dirty = false) }
                        } else replace(mine.uid) { it.copy(etag = server.etag, ics = server.data!!, dirty = false, deleted = false) }
                    }
                }
            }
        }
        downloads.chunked(50).forEach { batch ->
            runCatching { dav.fetch(cal.href, batch) }.getOrNull()?.forEach { o ->
                val ics = o.data ?: return@forEach
                val uid = runCatching { ICal.items(ics).firstOrNull()?.uid }.getOrNull() ?: return@forEach
                synchronized(lock) {
                    entries = entries.filterNot { it.href == o.href || (it.uid == uid && it.calendar == cal.href) } +
                        Entry(cal.href, o.href, uid, o.etag, ics)
                }
            }
        }
        synchronized(lock) { writeEntries(entries) }
    }

    private fun fetchEtag(dav: CalDav, href: String): String? = runCatching { dav.fetch(href.substringBeforeLast('/') + "/", listOf(href)).firstOrNull()?.etag }.getOrNull()

    private fun replace(uid: String, f: (Entry) -> Entry) = synchronized(lock) { entries = entries.map { if (it.uid == uid) f(it) else it } }

    // --- Lettura per le schermate ---

    /** I calendari con gli appuntamenti, e gli elenchi con le attività. */
    val eventCalendars: List<DavCalendar> get() = calendars.filter { it.events }
    val taskLists: List<DavCalendar> get() = calendars.filter { it.todos }

    fun calendarOf(uid: String): DavCalendar? = entries.firstOrNull { it.uid == uid }?.let { e -> calendars.firstOrNull { it.href == e.calendar } }

    // --- File ---

    private fun readEntries(): List<Entry> = runCatching {
        File(dir, FILE).readLines().mapNotNull { line ->
            Json.read(line)?.let { m ->
                Entry(m["cal"] ?: return@let null, m["href"]?.ifEmpty { null }, m["uid"] ?: return@let null, m["etag"]?.ifEmpty { null },
                    m["ics"].orEmpty(), m["dirty"] == "1", m["deleted"] == "1")
            }
        }
    }.getOrDefault(emptyList())

    private fun writeEntries(list: List<Entry>) = Cloud.writePrivate(File(dir, FILE), list.joinToString("\n") { e ->
        Json.write(mapOf("cal" to e.calendar, "href" to e.href.orEmpty(), "uid" to e.uid, "etag" to e.etag.orEmpty(), "ics" to e.ics,
            "dirty" to if (e.dirty) "1" else "0", "deleted" to if (e.deleted) "1" else "0"))
    })

    private fun readCalendars(): List<DavCalendar> = runCatching {
        File(dir, CALENDARS).readLines().mapNotNull { line ->
            Json.read(line)?.let { m ->
                DavCalendar(m["href"] ?: return@let null, m["name"].orEmpty(), m["color"]?.ifEmpty { null }, m["events"] == "1", m["todos"] == "1",
                    m["ctag"]?.ifEmpty { null }, m["ro"] == "1")
            }
        }
    }.getOrDefault(emptyList())

    private fun writeCalendars(list: List<DavCalendar>) = Cloud.writePrivate(File(dir, CALENDARS), list.joinToString("\n") { c ->
        Json.write(mapOf("href" to c.href, "name" to c.name, "color" to c.color.orEmpty(), "events" to if (c.events) "1" else "0",
            "todos" to if (c.todos) "1" else "0", "ctag" to c.ctag.orEmpty(), "ro" to if (c.readOnly) "1" else "0"))
    })

    private fun readLastSync(): Instant? = runCatching { Instant.parse(File(dir, LAST).readText().trim()) }.getOrNull()

    companion object {
        private const val FILE = "agenda.jsonl"
        private const val CALENDARS = "calendari.jsonl"
        private const val LAST = "agenda-sincronizzata"
        private val syncLock = Any()

        /** Apre [url] nel browser predefinito (xdg-open; dal Flatpak passa dal portale). */
        fun openBrowser(url: String) {
            runCatching { ProcessBuilder("xdg-open", url).start() }
                .onFailure { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } }
        }
    }
}
