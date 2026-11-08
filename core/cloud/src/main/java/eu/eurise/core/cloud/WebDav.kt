package eu.eurise.core.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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

/** Spazio usato e libero, in byte, quando il server lo dice. */
data class DavQuota(val used: Long?, val available: Long?)

/**
 * Client WebDAV essenziale: elenca, legge, scrive, crea cartelle ed elimina. Funziona con Nextcloud
 * (Murena compreso), kDrive e qualsiasi server WebDAV. I percorsi sono relativi a [baseUrl].
 */
class WebDav(
    baseUrl: String,
    private val user: String,
    password: String,
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

    companion object {
        const val USER_AGENT = "Eurise (Android)"
        private const val DAV_NS = "DAV:"
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
