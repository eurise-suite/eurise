package eu.eurise.core.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** Un file o una cartella sul server. [etag] cambia ogni volta che il contenuto cambia. */
data class DavEntry(
    val name: String,
    val folder: Boolean,
    val etag: String?,
    val size: Long?,
)

/** Risposta inattesa dal server; [code] è lo stato HTTP (401 password errata, 412 cambiato nel frattempo…). */
class DavException(val code: Int, message: String) : Exception(message)

/** Un link pubblico; [path] è relativo alla cartella principale dell'utente. */
data class DavShare(val id: String, val path: String, val url: String, val expires: String?)

/**
 * Una condivisione con una persona («Condividi con…», collaborazione rapida): [with] è l'utente del server o l'email,
 * [edit] se può modificare, [name] come la mostra il server.
 */
data class DavPersonShare(val id: String, val path: String, val with: String, val name: String, val byEmail: Boolean, val edit: Boolean)

/**
 * Un cambio in una cartella (app Attività di Nextcloud, tavola CollSquadra): [user] chi l'ha fatto (nome utente),
 * [subject] la frase del server («Laura ha modificato Preventivo v3.pdf»), [path] il file, [at] il momento in millisecondi.
 */
data class DavActivity(val user: String, val subject: String, val path: String, val type: String, val at: Long)

/** Una versione precedente di un file; [modified] è la data nel formato del server. */
data class DavVersion(val href: String, val modified: String, val size: Long?)

/**
 * Un calendario del server; [todo] se accetta le attività. [ownerName] c'è se l'ha condiviso un'altra persona;
 * [members] sono le persone con cui chi lo possiede l'ha condiviso.
 */
data class DavCalendar(
    val slug: String,
    val name: String,
    val todo: Boolean,
    val ownerName: String? = null,
    val members: List<DavMember> = emptyList(),
    /** Vero se accetta gli appuntamenti (VEVENT). */
    val events: Boolean = !todo,
)

/** Un oggetto di un calendario: [path] relativo alla cartella dei calendari («proposte/abc.ics»), [etag], testo iCalendar. */
data class DavCalendarObject(val path: String, val etag: String, val data: String)

/**
 * Una persona con cui è condiviso un calendario: [href] come lo scrive il server («principal:principals/users/luca» o
 * «mailto:…»), [name] come la mostra, [edit] se può modificare.
 */
data class DavMember(val href: String, val name: String, val edit: Boolean)

/** Spazio usato e libero, in byte, quando il server lo dice. */
data class DavQuota(val used: Long?, val available: Long?)

/**
 * Client WebDAV essenziale: elenca, legge, scrive, crea cartelle ed elimina. Funziona con Nextcloud
 * (Murena compreso), kDrive e qualsiasi server WebDAV. I percorsi sono relativi a [baseUrl].
 */
class WebDav(
    baseUrl: String,
    private val user: String,
    private val password: String,
    private val client: OkHttpClient = sharedClient,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val auth = Credentials.basic(user, password, Charsets.UTF_8)

    fun url(path: String, folder: Boolean = false): HttpUrl = base.newBuilder().apply {
        path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
        if (folder) addPathSegment("")
    }.build()

    private fun request(url: HttpUrl) = Request.Builder().url(url).header("Authorization", auth).header("User-Agent", USER_AGENT)

    private suspend fun <T> call(request: Request, block: (Response) -> T): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use(block)
    }

    /** Verifica indirizzo e password: vero se il server risponde alla cartella principale. */
    suspend fun check(): Boolean = runCatching { quota(); true }.getOrDefault(false)

    /** Contenuto di una cartella (senza la cartella stessa). Cartella inesistente: lista vuota. */
    suspend fun list(path: String): List<DavEntry> {
        val folderUrl = url(path, folder = true)
        val req = request(folderUrl).header("Depth", "1").method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).build()
        return call(req) { res ->
            when (res.code) {
                404 -> emptyList()
                207 -> parse(res.body?.string().orEmpty()).filterNot { samePath(it.first, folderUrl.encodedPath) }.map { it.second }
                else -> throw DavException(res.code, "PROPFIND $path: ${res.code}")
            }
        }
    }

    /** Legge un file di testo e il suo etag; null se non esiste. */
    suspend fun get(path: String): Pair<String, String?>? {
        val req = request(url(path)).get().build()
        return call(req) { res ->
            when {
                res.code == 404 -> null
                res.isSuccessful -> res.body?.string().orEmpty() to res.header("ETag")
                else -> throw DavException(res.code, "GET $path: ${res.code}")
            }
        }
    }

    /**
     * Scrive un file di testo e restituisce il nuovo etag (se il server lo dà).
     * Con [ifMatch] scrive solo se il file sul server è ancora quello; con [onlyIfNew] solo se non esiste:
     * altrimenti [DavException] con codice 412, e nessun cambio fatto altrove va perso.
     */
    suspend fun put(path: String, text: String, contentType: String, ifMatch: String? = null, onlyIfNew: Boolean = false): String? {
        val req = request(url(path)).apply {
            ifMatch?.let { header("If-Match", it) }
            if (onlyIfNew) header("If-None-Match", "*")
        }.put(text.toRequestBody(contentType.toMediaType())).build()
        val etag = call(req) { res ->
            if (!res.isSuccessful) throw DavException(res.code, "PUT $path: ${res.code}")
            res.header("ETag") ?: res.header("OC-ETag")
        }
        return etag ?: stat(path)?.etag
    }

    /** Scrive un file binario (per esempio un backup cifrato). */
    suspend fun putBytes(path: String, bytes: ByteArray, contentType: String = "application/octet-stream") {
        val req = request(url(path)).put(bytes.toRequestBody(contentType.toMediaType())).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "PUT $path: ${res.code}") }
    }

    /**
     * Scrive un file binario solo se sul server è ancora quello di [ifMatch] (o, con [onlyIfNew], solo se non
     * esiste), e restituisce il nuovo etag. Se nel frattempo è cambiato: [DavException] con codice 412.
     */
    suspend fun putBytesIf(path: String, bytes: ByteArray, ifMatch: String? = null, onlyIfNew: Boolean = false): String? {
        val req = request(url(path)).apply {
            ifMatch?.let { header("If-Match", it) }
            if (onlyIfNew) header("If-None-Match", "*")
        }.put(bytes.toRequestBody("application/octet-stream".toMediaType())).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "PUT $path: ${res.code}") }
        return stat(path)?.etag
    }

    /** Legge un file binario; null se non esiste. */
    suspend fun getBytes(path: String): ByteArray? {
        val req = request(url(path)).get().build()
        return call(req) { res ->
            when {
                res.code == 404 -> null
                res.isSuccessful -> res.body?.bytes()
                else -> throw DavException(res.code, "GET $path: ${res.code}")
            }
        }
    }

    /** Scarica su disco senza tenere il file in memoria, per i file grandi; falso se sul server non c'è. */
    suspend fun download(path: String, target: java.io.File): Boolean {
        val req = request(url(path)).get().build()
        return call(req) { res ->
            when {
                res.code == 404 -> false
                res.isSuccessful -> {
                    target.parentFile?.mkdirs()
                    res.body?.byteStream()?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    true
                }
                else -> throw DavException(res.code, "GET $path: ${res.code}")
            }
        }
    }

    /** Carica un file da disco senza tenerlo in memoria. */
    suspend fun upload(path: String, source: java.io.File) {
        val req = request(url(path)).put(source.asRequestBody("application/octet-stream".toMediaType())).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "PUT $path: ${res.code}") }
    }

    /** Carica da un flusso (una foto del telefono) senza copie su disco; [open] può essere chiamato più volte. */
    suspend fun uploadStream(path: String, size: Long, open: () -> java.io.InputStream) {
        val body = object : okhttp3.RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: okio.BufferedSink) {
                open().use { it.copyTo(sink.outputStream()) }
            }
        }
        val req = request(url(path)).put(body).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "PUT $path: ${res.code}") }
    }

    /** Lo stesso account sulla cartella dei calendari (CalDAV); null fuori da Nextcloud e Murena. */
    fun calendars(): WebDav? = if (isNextcloud) WebDav(base.toString().replace("/remote.php/dav/files/", "/remote.php/dav/calendars/"), user, password, client) else null

    /** I calendari dell'account, da chiamare su [calendars]. */
    suspend fun listCalendars(): List<DavCalendar> {
        val req = request(url("", folder = true)).header("Depth", "1").method("PROPFIND", CALENDARS_BODY.toRequestBody(XML)).build()
        return call(req) { res ->
            if (res.code != 207) throw DavException(res.code, "PROPFIND calendari: ${res.code}")
            val responses = parseXml(res.body?.string().orEmpty()).getElementsByTagNameNS(DAV_NS, "response")
            (0 until responses.length).mapNotNull { i ->
                val r = responses.item(i) as Element
                if (r.getElementsByTagNameNS(CALDAV_NS, "calendar").length == 0) return@mapNotNull null
                val slug = r.child("href")?.textContent?.trim()?.trimEnd('/')?.substringAfterLast('/')?.let { URLDecoder.decode(it, "UTF-8") } ?: return@mapNotNull null
                val comps = r.getElementsByTagNameNS(CALDAV_NS, "comp")
                val names = (0 until comps.length).map { (comps.item(it) as Element).getAttribute("name").uppercase() }
                val todo = "VTODO" in names
                val events = names.isEmpty() || "VEVENT" in names
                val owner = r.getElementsByTagNameNS(OC_NS, "owner-principal").item(0)?.textContent?.trim()?.trimEnd('/')?.substringAfterLast('/')
                val ownerName = if (owner != null && owner != calendarUser) {
                    r.getElementsByTagNameNS(NC_NS, "owner-displayname").item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: owner
                } else null
                val users = r.getElementsByTagNameNS(OC_NS, "user")
                val members = (0 until users.length).mapNotNull { u ->
                    val user = users.item(u) as Element
                    val href = user.child("href")?.textContent?.trim() ?: return@mapNotNull null
                    val name = user.getElementsByTagNameNS(OC_NS, "common-name").item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                        ?: href.substringAfterLast('/').substringAfter("mailto:")
                    DavMember(href, name, user.getElementsByTagNameNS(OC_NS, "read-write").length > 0)
                }
                DavCalendar(slug, r.child("displayname")?.textContent?.trim().orEmpty(), todo, ownerName, members, events)
            }
        }
    }

    /** Il nome utente sul server per i calendari: la cartella dopo «/calendars/». */
    private val calendarUser: String get() = base.encodedPath.substringAfter("/remote.php/dav/calendars/").trim('/').substringBefore('/')

    /**
     * Condivide il calendario [slug] con una persona (elenchi condivisi, collaborazione rapida), da chiamare su
     * [calendars]: [email] di un utente dello stesso server oppure il suo nome utente. [edit]: può spuntare e aggiungere.
     */
    suspend fun shareCalendar(slug: String, email: String, edit: Boolean) {
        val href = if ('@' in email) "mailto:" + email.trim() else "principal:principals/users/" + email.trim()
        val body = """<?xml version="1.0" encoding="utf-8"?><o:share xmlns:d="DAV:" xmlns:o="$OC_NS"><o:set><d:href>${xmlText(href)}</d:href>""" +
            (if (edit) "<o:read-write/>" else "") + "</o:set></o:share>"
        val req = request(url(slug, folder = true)).post(body.toRequestBody(XML)).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "share calendar: ${res.code}") }
    }

    /** Toglie la condivisione del calendario [slug] con la persona [href] (come la dà [DavMember.href]). */
    suspend fun unshareCalendar(slug: String, href: String) {
        val body = """<?xml version="1.0" encoding="utf-8"?><o:share xmlns:d="DAV:" xmlns:o="$OC_NS"><o:remove><d:href>${xmlText(href)}</d:href></o:remove></o:share>"""
        val req = request(url(slug, folder = true)).post(body.toRequestBody(XML)).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "unshare calendar: ${res.code}") }
    }

    /**
     * Nome e indirizzo email dell'account sul server, da chiamare su [calendars] (per «preso da» e «Affida a…»).
     * Null se il server non risponde.
     */
    suspend fun me(): Pair<String, String?>? {
        val url = base.toString().substringBefore("/remote.php/") + "/remote.php/dav/principals/users/" + calendarUser + "/"
        val req = request(url.toHttpUrl()).header("Depth", "0").method("PROPFIND", ME_BODY.toRequestBody(XML)).build()
        return runCatching {
            call(req) { res ->
                if (res.code != 207) return@call null
                val doc = parseXml(res.body?.string().orEmpty())
                val name = doc.getElementsByTagNameNS(DAV_NS, "displayname").item(0)?.textContent?.trim().orEmpty().ifEmpty { calendarUser }
                val email = doc.getElementsByTagNameNS(SABRE_NS, "email-address").item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                name to email
            }
        }.getOrNull()
    }

    private fun xmlText(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Crea un calendario di sole attività (o di soli appuntamenti con [events]), da chiamare su [calendars]. */
    suspend fun mkCalendar(slug: String, name: String, events: Boolean = false) {
        val safe = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val comp = if (events) "VEVENT" else "VTODO"
        val body = """<?xml version="1.0" encoding="utf-8"?><c:mkcalendar xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:set><d:prop><d:displayname>$safe</d:displayname><c:supported-calendar-component-set><c:comp name="$comp"/></c:supported-calendar-component-set></d:prop></d:set></c:mkcalendar>"""
        val req = request(url(slug, folder = true)).method("MKCALENDAR", body.toRequestBody(XML)).build()
        call(req) { res -> if (!res.isSuccessful && res.code != 405) throw DavException(res.code, "MKCALENDAR $slug: ${res.code}") }
    }

    /**
     * Gli appuntamenti del calendario [slug] con il loro testo, da chiamare su [calendars] (un `calendar-query`
     * con `calendar-data`: va bene per i calendari piccoli, come quello delle proposte dell'assistente).
     */
    suspend fun calendarObjects(slug: String): List<DavCalendarObject> {
        val req = request(url(slug, folder = true)).header("Depth", "1").method("REPORT", EVENTS_QUERY.toRequestBody(XML)).build()
        return call(req) { res ->
            if (res.code == 404) return@call emptyList()
            if (res.code != 207) throw DavException(res.code, "REPORT $slug: ${res.code}")
            val responses = parseXml(res.body?.string().orEmpty()).getElementsByTagNameNS(DAV_NS, "response")
            (0 until responses.length).mapNotNull { i ->
                val r = responses.item(i) as Element
                val name = r.child("href")?.textContent?.trim()?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val etag = r.child("getetag")?.textContent?.trim() ?: return@mapNotNull null
                val data = r.getElementsByTagNameNS(CALDAV_NS, "calendar-data").item(0)?.textContent ?: return@mapNotNull null
                DavCalendarObject(slug + "/" + URLDecoder.decode(name, "UTF-8"), etag, data)
            }
        }
    }

    /** Vero sui server Nextcloud, Murena compreso: solo loro hanno link, versioni e condivisioni. */
    val isNextcloud: Boolean get() = "/remote.php/dav/files/" in base.encodedPath

    private val serverRoot: String get() = base.toString().substringBefore("/remote.php/")
    private val sharesUrl: String get() = "$serverRoot/ocs/v2.php/apps/files_sharing/api/v1/shares"
    private val davUser: String get() = base.encodedPath.substringAfter("/remote.php/dav/files/").trim('/')

    /**
     * File il cui nome contiene [term], in tutte le cartelle, i più recenti prima (ricerca WebDAV di Nextcloud).
     * Restituisce il percorso relativo alla cartella principale e la voce. Fuori da Nextcloud: lista vuota.
     */
    suspend fun search(term: String, limit: Int = 20): List<Pair<String, DavEntry>> {
        if (!isNextcloud || term.isBlank()) return emptyList()
        val literal = term.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("%", "")
        val body = """<?xml version="1.0" encoding="UTF-8"?><d:searchrequest xmlns:d="DAV:"><d:basicsearch>""" +
            """<d:select><d:prop><d:displayname/><d:getetag/><d:getcontentlength/><d:resourcetype/></d:prop></d:select>""" +
            """<d:from><d:scope><d:href>/files/$davUser</d:href><d:depth>infinity</d:depth></d:scope></d:from>""" +
            """<d:where><d:like><d:prop><d:displayname/></d:prop><d:literal>%$literal%</d:literal></d:like></d:where>""" +
            """<d:orderby><d:order><d:prop><d:getlastmodified/></d:prop><d:descending/></d:order></d:orderby>""" +
            """<d:limit><d:nresults>$limit</d:nresults></d:limit></d:basicsearch></d:searchrequest>"""
        val req = Request.Builder().url("$serverRoot/remote.php/dav/").header("Authorization", auth).header("User-Agent", USER_AGENT)
            .method("SEARCH", body.toRequestBody("text/xml; charset=utf-8".toMediaType())).build()
        val root = base.encodedPath
        return call(req) { res ->
            if (res.code != 207) throw DavException(res.code, "SEARCH: ${res.code}")
            parse(res.body?.string().orEmpty())
                .filter { !it.second.folder && it.first.startsWith(root) }
                .map { (path, entry) -> URLDecoder.decode(path.removePrefix(root).replace("+", "%2B"), "UTF-8") to entry }
        }
    }

    /** Crea un link pubblico, facoltativamente con password e scadenza (AAAA-MM-GG), e ne restituisce l'indirizzo. */
    suspend fun createLink(path: String, password: String?, expires: String?): String {
        val form = okhttp3.FormBody.Builder().add("path", "/" + path.trim('/')).add("shareType", "3")
        password?.let { form.add("password", it) }
        expires?.let { form.add("expireDate", it) }
        val req = request("$sharesUrl?format=json".toHttpUrl()).header("OCS-APIRequest", "true").post(form.build()).build()
        return call(req) { res ->
            if (!res.isSuccessful) throw DavException(res.code, "share: ${res.code}")
            org.json.JSONObject(res.body?.string().orEmpty()).getJSONObject("ocs").getJSONObject("data").getString("url")
        }
    }

    /** I link pubblici creati da questo account. */
    suspend fun links(): List<DavShare> {
        val req = request("$sharesUrl?format=json".toHttpUrl()).header("OCS-APIRequest", "true").get().build()
        return call(req) { res ->
            if (!res.isSuccessful) throw DavException(res.code, "shares: ${res.code}")
            val data = org.json.JSONObject(res.body?.string().orEmpty()).getJSONObject("ocs").getJSONArray("data")
            (0 until data.length()).map { data.getJSONObject(it) }.filter { it.optInt("share_type") == 3 }.map {
                DavShare(it.getString("id"), it.optString("path").trimStart('/'), it.optString("url"), it.optString("expiration").takeUnless { e -> e.isBlank() || e == "null" })
            }
        }
    }

    suspend fun removeLink(id: String) {
        val req = request("$sharesUrl/$id?format=json".toHttpUrl()).header("OCS-APIRequest", "true").delete().build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "unshare: ${res.code}") }
    }

    /**
     * Condivide [path] con una persona: un utente dello stesso server o, se [byEmail], un indirizzo email che riceve
     * il link personale dal server. [edit]: può modificare (per una cartella anche aggiungere ed eliminare).
     */
    suspend fun shareWith(path: String, with: String, byEmail: Boolean, edit: Boolean, folder: Boolean) {
        val type = if (byEmail) "4" else "0"
        val form = okhttp3.FormBody.Builder().add("path", "/" + path.trim('/')).add("shareType", type).add("shareWith", with.trim())
            .add("permissions", permissions(edit, folder).toString())
        val req = request("$sharesUrl?format=json".toHttpUrl()).header("OCS-APIRequest", "true").post(form.build()).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "share with: ${res.code}") }
    }

    /** Le persone con cui è condiviso [path] (utenti, federati, email), senza i link pubblici. */
    suspend fun sharesOf(path: String): List<DavPersonShare> {
        val url = "$sharesUrl?format=json&reshares=true&path=" + java.net.URLEncoder.encode("/" + path.trim('/'), "UTF-8")
        val req = request(url.toHttpUrl()).header("OCS-APIRequest", "true").get().build()
        return call(req) { res ->
            if (res.code == 404) return@call emptyList()
            if (!res.isSuccessful) throw DavException(res.code, "shares of: ${res.code}")
            val data = org.json.JSONObject(res.body?.string().orEmpty()).getJSONObject("ocs").optJSONArray("data") ?: return@call emptyList()
            (0 until data.length()).map { data.getJSONObject(it) }.filter { it.optInt("share_type") in PERSON_TYPES }.map {
                DavPersonShare(
                    id = it.getString("id"), path = it.optString("path").trimStart('/'), with = it.optString("share_with"),
                    name = it.optString("share_with_displayname").ifBlank { it.optString("share_with") },
                    byEmail = it.optInt("share_type") == 4, edit = (it.optInt("permissions") and 2) != 0,
                )
            }
        }
    }

    /** Cambia «vede» / «modifica» di una condivisione. */
    suspend fun setShareEdit(id: String, edit: Boolean, folder: Boolean) {
        val form = okhttp3.FormBody.Builder().add("permissions", permissions(edit, folder).toString()).build()
        val req = request("$sharesUrl/$id?format=json".toHttpUrl()).header("OCS-APIRequest", "true").put(form).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "share update: ${res.code}") }
    }

    /**
     * I cambi ai file dentro [folder] dal momento [since] (millisecondi), i più recenti prima, dall'app Attività di
     * Nextcloud. Lista vuota se l'app non c'è o fuori da Nextcloud.
     */
    suspend fun activity(folder: String, since: Long, limit: Int = 200): List<DavActivity> {
        if (!isNextcloud) return emptyList()
        val url = "$serverRoot/ocs/v2.php/apps/activity/api/v2/activity/files?format=json&limit=$limit"
        val req = request(url.toHttpUrl()).header("OCS-APIRequest", "true").get().build()
        val prefix = "/" + folder.trim('/') + "/"
        return call(req) { res ->
            // 304: nessuna attività; 404/503: app spenta sul server.
            if (!res.isSuccessful) return@call emptyList()
            val data = runCatching { org.json.JSONObject(res.body?.string().orEmpty()).getJSONObject("ocs").optJSONArray("data") }.getOrNull()
                ?: return@call emptyList()
            (0 until data.length()).map { data.getJSONObject(it) }.mapNotNull { a ->
                val at = runCatching { java.time.OffsetDateTime.parse(a.optString("datetime")).toInstant().toEpochMilli() }.getOrNull() ?: return@mapNotNull null
                val objects = a.optJSONObject("objects")
                val paths = buildList {
                    add(a.optString("object_name"))
                    objects?.keys()?.forEach { k -> add(objects.optString(k)) }
                }.filter { it.isNotBlank() }
                val path = paths.firstOrNull { it.startsWith(prefix) } ?: return@mapNotNull null
                if (at < since) return@mapNotNull null
                DavActivity(a.optString("user"), a.optString("subject"), path, a.optString("type"), at)
            }.sortedByDescending { it.at }
        }
    }

    /** Toglie una condivisione (persona o link): da quel momento non vede più nulla. */
    suspend fun removeShare(id: String) = removeLink(id)

    /** Le versioni precedenti di un file, dalla più recente. */
    suspend fun versions(path: String): List<DavVersion> {
        val idReq = request(url(path)).header("Depth", "0").method("PROPFIND", FILEID_BODY.toRequestBody(XML)).build()
        val fileId = call(idReq) { res ->
            if (res.code != 207) throw DavException(res.code, "fileid: ${res.code}")
            parseXml(res.body?.string().orEmpty()).getElementsByTagNameNS(OC_NS, "fileid").item(0)?.textContent?.trim()
        } ?: return emptyList()
        val listUrl = "$serverRoot/remote.php/dav/versions/$davUser/versions/$fileId/".toHttpUrl()
        val req = request(listUrl).header("Depth", "1").method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).build()
        return call(req) { res ->
            if (res.code == 404) return@call emptyList()
            if (res.code != 207) throw DavException(res.code, "versions: ${res.code}")
            val doc = parseXml(res.body?.string().orEmpty())
            val responses = doc.getElementsByTagNameNS(DAV_NS, "response")
            (0 until responses.length).mapNotNull { i ->
                val r = responses.item(i) as Element
                val href = r.child("href")?.textContent?.trim() ?: return@mapNotNull null
                if (samePath(href, listUrl.encodedPath)) return@mapNotNull null
                DavVersion(href, r.child("getlastmodified")?.textContent?.trim().orEmpty(), r.child("getcontentlength")?.textContent?.trim()?.toLongOrNull())
            }
        }
    }

    /** Riporta il file alla versione scelta; Nextcloud conserva quella attuale tra le versioni. */
    suspend fun restoreVersion(version: DavVersion) {
        val target = "$serverRoot/remote.php/dav/versions/$davUser/restore/target"
        val from = serverRoot.toHttpUrl().newBuilder().encodedPath(version.href).build()
        val req = request(from).header("Destination", target).method("MOVE", null).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "restore: ${res.code}") }
    }

    /** Sposta o rinomina un file sul server, sostituendo quello che c'è già al nuovo posto. */
    suspend fun move(from: String, to: String) {
        val req = request(url(from)).header("Destination", url(to).toString()).header("Overwrite", "T").method("MOVE", null).build()
        call(req) { res -> if (!res.isSuccessful) throw DavException(res.code, "MOVE $from: ${res.code}") }
    }

    /** Informazioni su un solo file; null se non esiste. */
    suspend fun stat(path: String): DavEntry? {
        val req = request(url(path)).header("Depth", "0").method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).build()
        return call(req) { res ->
            when (res.code) {
                404 -> null
                207 -> parse(res.body?.string().orEmpty()).firstOrNull()?.second
                else -> throw DavException(res.code, "PROPFIND $path: ${res.code}")
            }
        }
    }

    suspend fun delete(path: String, ifMatch: String? = null) {
        val req = request(url(path)).apply { ifMatch?.let { header("If-Match", it) } }.delete().build()
        call(req) { res ->
            if (!res.isSuccessful && res.code != 404) throw DavException(res.code, "DELETE $path: ${res.code}")
        }
    }

    /** Crea la cartella e quelle che la contengono, se mancano. */
    suspend fun mkdirs(path: String) {
        var current = ""
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            current = if (current.isEmpty()) segment else "$current/$segment"
            val req = request(url(current, folder = true)).method("MKCOL", null).build()
            call(req) { res ->
                // 405: esiste già.
                if (!res.isSuccessful && res.code != 405) throw DavException(res.code, "MKCOL $current: ${res.code}")
            }
        }
    }

    suspend fun quota(): DavQuota {
        val req = request(url("", folder = true)).header("Depth", "0").method("PROPFIND", QUOTA_BODY.toRequestBody(XML)).build()
        return call(req) { res ->
            if (res.code != 207) throw DavException(res.code, "PROPFIND quota: ${res.code}")
            val doc = parseXml(res.body?.string().orEmpty())
            fun value(name: String) = doc.getElementsByTagNameNS(DAV_NS, name).item(0)?.textContent?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
            DavQuota(value("quota-used-bytes"), value("quota-available-bytes"))
        }
    }

    private fun samePath(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')

    /** Le risposte di un PROPFIND: percorso codificato e voce. */
    private fun parse(xml: String): List<Pair<String, DavEntry>> {
        val doc = parseXml(xml)
        val responses = doc.getElementsByTagNameNS(DAV_NS, "response")
        return (0 until responses.length).mapNotNull { i ->
            val r = responses.item(i) as Element
            val href = r.child("href")?.textContent?.trim() ?: return@mapNotNull null
            val path = runCatching { href.toHttpUrl().encodedPath }.getOrDefault(href)
            val folder = r.getElementsByTagNameNS(DAV_NS, "collection").length > 0
            val name = URLDecoder.decode(path.trimEnd('/').substringAfterLast('/').replace("+", "%2B"), "UTF-8")
            path to DavEntry(
                name = name,
                folder = folder,
                etag = r.getElementsByTagNameNS(DAV_NS, "getetag").item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() },
                size = r.getElementsByTagNameNS(DAV_NS, "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull(),
            )
        }
    }

    private fun Element.child(name: String): Element? = getElementsByTagNameNS(DAV_NS, name).item(0) as? Element

    private fun parseXml(xml: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(xml.byteInputStream())

    private fun permissions(edit: Boolean, folder: Boolean): Int = when {
        !edit -> 1                 // lettura
        folder -> 1 or 2 or 4 or 8 // lettura, modifica, aggiunta, eliminazione
        else -> 1 or 2             // lettura e modifica
    }

    companion object {
        /** Utente (0), email (4), federato (6): le condivisioni con una persona. */
        private val PERSON_TYPES = setOf(0, 4, 6)
        const val USER_AGENT = "Eurise (Android)"
        private const val DAV_NS = "DAV:"
        private const val CALDAV_NS = "urn:ietf:params:xml:ns:caldav"
        private const val CALENDARS_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:o="http://owncloud.org/ns" xmlns:n="http://nextcloud.com/ns"><d:prop><d:resourcetype/><d:displayname/><c:supported-calendar-component-set/><o:owner-principal/><n:owner-displayname/><o:invite/></d:prop></d:propfind>"""
        private const val EVENTS_QUERY =
            """<?xml version="1.0" encoding="utf-8"?><c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><d:getetag/><c:calendar-data/></d:prop><c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT"/></c:comp-filter></c:filter></c:calendar-query>"""
        private const val OC_NS = "http://owncloud.org/ns"
        private const val NC_NS = "http://nextcloud.com/ns"
        private const val SABRE_NS = "http://sabredav.org/ns"
        private const val ME_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns"><d:prop><d:displayname/><s:email-address/></d:prop></d:propfind>"""
        private const val FILEID_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:prop><oc:fileid/></d:prop></d:propfind>"""
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getetag/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        private const val QUOTA_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:quota-used-bytes/><d:quota-available-bytes/></d:prop></d:propfind>"""

        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
