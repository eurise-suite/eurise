package eu.eurise.core.sync

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** Errore HTTP di un server DAV; 412 vuol dire «cambiato altrove nel frattempo». */
class DavException(val code: Int, message: String) : Exception(message)

/** Una risposta di un `multistatus`: [href] già decodificato, proprietà trovate (nome locale → testo). */
data class DavResponse(val href: String, val props: Map<String, String>, val collection: Boolean, val types: Set<String>)

/**
 * Il minimo di WebDAV (RFC 4918) che serve a CalDAV e alle Note: PROPFIND, REPORT, GET, PUT e DELETE, con le scritture
 * condizionate (`If-Match` sull'etag letto, `If-None-Match: *` per i file nuovi). Le chiamate sono bloccanti: l'app le
 * fa fuori dal thread dell'interfaccia. Nessun dato esce oltre il server scelto dall'utente.
 */
class DavHttp(
    base: String,
    private val user: String,
    private val password: String,
    private val client: OkHttpClient = defaultClient,
) {
    val base: HttpUrl = base.trimEnd('/').plus("/").toHttpUrl()
    private val auth = Credentials.basic(user, password)

    /** L'indirizzo completo di [path], relativo alla base o assoluto (`/remote.php/…` o `https://…`). */
    fun resolve(path: String): HttpUrl = when {
        path.startsWith("http://") || path.startsWith("https://") -> path.toHttpUrl()
        else -> base.resolve(path) ?: throw IllegalArgumentException(path)
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("Authorization", auth)
        .header("User-Agent", USER_AGENT)

    fun propfind(path: String, depth: Int, body: String): List<DavResponse> =
        multistatus("PROPFIND", path, depth, body)

    fun report(path: String, depth: Int, body: String): List<DavResponse> =
        multistatus("REPORT", path, depth, body)

    private fun multistatus(method: String, path: String, depth: Int, body: String): List<DavResponse> {
        val req = request(resolve(path)).header("Depth", depth.toString())
            .method(method, body.toRequestBody(XML)).build()
        client.newCall(req).execute().use { r ->
            if (r.code != 207 && !r.isSuccessful) throw DavException(r.code, "$method $path: ${r.code}")
            return parseMultistatus(r.body?.string().orEmpty())
        }
    }

    /** Il testo e l'etag di un file, o null se non c'è. */
    fun get(path: String): Pair<String, String?>? {
        client.newCall(request(resolve(path)).get().build()).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw DavException(r.code, "GET $path: ${r.code}")
            return r.body?.string().orEmpty() to r.header("ETag")
        }
    }

    /**
     * Scrive [text]. Con [ifMatch] solo se sul server c'è ancora quella versione, con [onlyIfNew] solo se il file non
     * esiste; altrimenti 412. Restituisce il nuovo etag se il server lo dice.
     */
    fun put(path: String, text: String, contentType: String, ifMatch: String? = null, onlyIfNew: Boolean = false): String? {
        val req = request(resolve(path)).put(text.toRequestBody(contentType.toMediaType())).apply {
            ifMatch?.let { header("If-Match", it) }
            if (onlyIfNew) header("If-None-Match", "*")
        }.build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw DavException(r.code, "PUT $path: ${r.code}")
            return r.header("ETag")
        }
    }

    fun delete(path: String, ifMatch: String? = null) {
        val req = request(resolve(path)).delete().apply { ifMatch?.let { header("If-Match", it) } }.build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful && r.code != 404) throw DavException(r.code, "DELETE $path: ${r.code}")
        }
    }

    /** Crea la cartella [path] e quelle sopra, se mancano. */
    fun mkdirs(path: String) {
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        var current = ""
        for (p in parts) {
            current += "$p/"
            val req = request(resolve(current)).method("MKCOL", null).build()
            client.newCall(req).execute().use { r ->
                // 405: esiste già.
                if (!r.isSuccessful && r.code != 405) throw DavException(r.code, "MKCOL $current: ${r.code}")
            }
        }
    }

    companion object {
        const val USER_AGENT = "Eurise (computer)"
        private val XML = "application/xml; charset=utf-8".toMediaType()
        const val DAV_NS = "DAV:"

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        /** Legge un `multistatus`: per ogni risposta le proprietà con stato 200 (testo, o il nome dei figli per i tipi). */
        fun parseMultistatus(xml: String): List<DavResponse> {
            if (xml.isBlank()) return emptyList()
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val responses = doc.getElementsByTagNameNS(DAV_NS, "response")
            val out = ArrayList<DavResponse>(responses.length)
            for (i in 0 until responses.length) {
                val resp = responses.item(i) as Element
                val href = resp.firstChild(DAV_NS, "href")?.textContent?.trim() ?: continue
                val props = HashMap<String, String>()
                var collection = false
                val types = HashSet<String>()
                val stats = resp.getElementsByTagNameNS(DAV_NS, "propstat")
                for (s in 0 until stats.length) {
                    val stat = stats.item(s) as Element
                    val status = stat.firstChild(DAV_NS, "status")?.textContent.orEmpty()
                    if (" 200 " !in "$status ") continue
                    val prop = stat.firstChild(DAV_NS, "prop") ?: continue
                    var n = prop.firstChild
                    while (n != null) {
                        if (n is Element) {
                            val name = n.localName ?: n.nodeName
                            if (name == "resourcetype" || name == "supported-calendar-component-set") {
                                var c = n.firstChild
                                while (c != null) {
                                    if (c is Element) {
                                        val t = c.localName ?: c.nodeName
                                        if (t == "collection") collection = true
                                        if (name == "resourcetype") types += t else types += "comp:" + c.getAttribute("name")
                                    }
                                    c = c.nextSibling
                                }
                            } else if (name == "current-user-principal" || name == "calendar-home-set") {
                                props[name] = n.firstChild(DAV_NS, "href")?.textContent?.trim().orEmpty()
                            } else {
                                props[name] = n.textContent.trim()
                            }
                        }
                        n = n.nextSibling
                    }
                }
                out += DavResponse(decode(href), props, collection, types)
            }
            return out
        }

        private fun Element.firstChild(ns: String, name: String): Element? {
            var n = firstChild
            while (n != null) {
                if (n is Element && n.namespaceURI == ns && n.localName == name) return n
                n = n.nextSibling
            }
            return null
        }

        private fun decode(href: String): String = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
    }
}
