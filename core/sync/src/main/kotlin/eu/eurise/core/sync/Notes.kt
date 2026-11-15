package eu.eurise.core.sync

/**
 * Una nota come file del cloud, nello stesso formato del telefono (`Eurise/Note/<id>.html`): un documento HTML che si
 * apre in qualsiasi browser, con i dati dell'app nei `<meta name="eurise:…">` e un blocco del testo per riga dopo
 * il titolo. Qui non si interpreta l'HTML dei blocchi: lo si confronta e si unisce riga per riga.
 */
data class NoteDoc(
    val title: String,
    val meta: Map<String, String>,
    val blocks: List<String>,
    val style: String = "",
) {
    val uid: String? get() = meta["id"]

    fun write(): String = buildString {
        append("<!doctype html>\n<html lang=\"it\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<meta name=\"generator\" content=\"Eurise Note\">\n")
        meta.forEach { (k, v) -> append("<meta name=\"eurise:").append(k).append("\" content=\"").append(escape(v)).append("\">\n") }
        append("<title>").append(escape(title)).append("</title>\n<style>").append(style).append("</style>\n</head>\n<body>\n")
        append("<h1>").append(escape(title)).append("</h1>\n")
        blocks.forEach { append(it).append('\n') }
        append("</body>\n</html>\n")
    }

    companion object {
        private val META = Regex("""<meta\s+name="eurise:([a-z-]+)"\s+content="([^"]*)"""")
        private val TITLE = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
        private val STYLE = Regex("""<style>(.*?)</style>""", RegexOption.DOT_MATCHES_ALL)
        private val H1 = Regex("""^\s*<h1>.*</h1>\s*$""")

        fun read(html: String): NoteDoc {
            val head = html.substringBefore("<body", html.take(4_000))
            val meta = LinkedHashMap<String, String>()
            META.findAll(head).forEach { meta[it.groupValues[1]] = decode(it.groupValues[2]) }
            val title = TITLE.find(head)?.groupValues?.get(1)?.let { decode(it).trim() }.orEmpty()
            val style = STYLE.find(head)?.groupValues?.get(1).orEmpty()
            val body = html.substringAfter("<body>", "").substringBeforeLast("</body>")
            val lines = body.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
            val blocks = if (lines.firstOrNull()?.let(H1::matches) == true) lines.drop(1) else lines
            return NoteDoc(title, meta, blocks, style)
        }

        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        fun decode(s: String) = s.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").replace("&amp;", "&")

        /**
         * Unisce tre versioni della stessa nota. Titolo e dati: vale quello cambiato da una parte sola; se il titolo è
         * cambiato in due modi o un blocco è stato riscritto da entrambe le parti, il risultato è null (scelta all'utente).
         */
        fun merge(base: NoteDoc, local: NoteDoc, remote: NoteDoc): NoteDoc? {
            val title = when {
                local.title == remote.title -> local.title
                local.title == base.title -> remote.title
                remote.title == base.title -> local.title
                else -> return null
            }
            val blocks = Merge3.clean(Merge3.merge(base.blocks, local.blocks, remote.blocks)) ?: return null
            val keys = LinkedHashSet<String>().apply { addAll(local.meta.keys); addAll(remote.meta.keys) }
            val meta = LinkedHashMap<String, String>()
            for (k in keys) {
                val b = base.meta[k]
                val l = local.meta[k]
                val r = remote.meta[k]
                val v = if (l != b) l else r
                if (v != null) meta[k] = v
            }
            // L'ora dell'ultima modifica è la più recente delle due (le date ISO si confrontano come testo).
            listOfNotNull(local.meta["updated"], remote.meta["updated"]).maxOrNull()?.let { meta["updated"] = it }
            return NoteDoc(title, meta, blocks, local.style.ifEmpty { remote.style })
        }
    }
}

/** Una nota sul computer: il file com'è ora, com'era all'ultima sincronizzazione ([base]) e l'etag di allora. */
data class NoteState(val uid: String, val text: String, val base: String?, val etag: String?, val deleted: Boolean = false)

/** Dove il computer tiene le note; l'app la implementa sul suo database o sui file. */
interface NoteStore {
    fun states(): List<NoteState>
    /** La nota [uid] ora è [text], uguale al cloud con [etag] (nuova se non c'era). */
    fun synced(uid: String, text: String, etag: String?)
    /** La nota aspetta una scelta tra la versione di qui e [remote]. */
    fun conflict(uid: String, remote: String, etag: String?)
    fun remove(uid: String)
}

/** Cosa è successo in un giro, per la riga «Sincronizzate…» e il registro. */
sealed interface NoteEvent {
    val uid: String
    data class Sent(override val uid: String) : NoteEvent
    data class Received(override val uid: String) : NoteEvent
    data class Merged(override val uid: String) : NoteEvent
    data class Conflict(override val uid: String) : NoteEvent
    data class Deleted(override val uid: String) : NoteEvent
}

/**
 * Sincronizzazione delle note con la cartella `Eurise/Note` del cloud, con le regole del telefono: tre versioni per
 * nota (qui, cloud, ultima volta), ogni scrittura condizionata all'etag letto, unione a tre vie e conflitto solo se la
 * stessa parte è stata cambiata in due modi.
 */
class NotesSync(private val dav: DavHttp, private val folder: String = FOLDER) {

    fun run(store: NoteStore): List<NoteEvent> {
        val out = ArrayList<NoteEvent>()
        dav.mkdirs(folder)
        val remote = dav.propfind(folder, 1, LIST)
            .filter { !it.collection && it.href.endsWith(EXT) }
            .associate { it.href.substringAfterLast('/').removeSuffix(EXT) to it.props["getetag"] }
        val seen = HashSet<String>()
        for (s in store.states()) {
            seen += s.uid
            val path = path(s.uid)
            val inCloud = remote.containsKey(s.uid)
            val etag = remote[s.uid]
            try {
                when {
                    s.deleted -> {
                        if (inCloud && (s.etag == null || etag == s.etag)) dav.delete(path, s.etag)
                        store.remove(s.uid); out += NoteEvent.Deleted(s.uid)
                    }
                    !inCloud && s.etag == null -> {
                        store.synced(s.uid, s.text, dav.put(path, s.text, HTML, onlyIfNew = true)); out += NoteEvent.Sent(s.uid)
                    }
                    !inCloud -> if (s.text == s.base) {
                        store.remove(s.uid); out += NoteEvent.Deleted(s.uid)
                    } else {
                        store.synced(s.uid, s.text, dav.put(path, s.text, HTML, onlyIfNew = true)); out += NoteEvent.Sent(s.uid)
                    }
                    etag != null && etag == s.etag -> if (s.text != s.base) {
                        store.synced(s.uid, s.text, dav.put(path, s.text, HTML, ifMatch = etag)); out += NoteEvent.Sent(s.uid)
                    }
                    else -> {
                        val (remoteText, newEtag) = dav.get(path) ?: continue
                        val e = newEtag ?: etag
                        when {
                            remoteText == s.text -> store.synced(s.uid, s.text, e)
                            s.text == s.base -> { store.synced(s.uid, remoteText, e); out += NoteEvent.Received(s.uid) }
                            else -> {
                                val merged = s.base?.let { NoteDoc.merge(NoteDoc.read(it), NoteDoc.read(s.text), NoteDoc.read(remoteText)) }
                                if (merged == null) {
                                    store.conflict(s.uid, remoteText, e); out += NoteEvent.Conflict(s.uid)
                                } else {
                                    val text = merged.write()
                                    store.synced(s.uid, text, dav.put(path, text, HTML, ifMatch = e)); out += NoteEvent.Merged(s.uid)
                                }
                            }
                        }
                    }
                }
            } catch (e: DavException) {
                // 412: cambiata tra la lettura e la scrittura; si riprova al prossimo giro.
                if (e.code != 412) throw e
            }
        }
        for ((uid, etag) in remote) {
            if (uid in seen) continue
            val (text, e) = dav.get(path(uid)) ?: continue
            store.synced(uid, text, e ?: etag)
            out += NoteEvent.Received(uid)
        }
        return out
    }

    private fun path(uid: String) = folder.trimEnd('/') + "/" + uid + EXT

    companion object {
        const val FOLDER = "Eurise/Note/"
        const val EXT = ".html"
        const val HTML = "text/html; charset=utf-8"
        private const val LIST =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getetag/></d:prop></d:propfind>"""
    }
}
