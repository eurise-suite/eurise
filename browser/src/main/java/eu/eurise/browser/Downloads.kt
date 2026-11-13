package eu.eurise.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import org.mozilla.geckoview.WebResponse
import java.net.URLDecoder

/**
 * I download del Browser (prova del proprietario, 13 novembre: «non scarica o scarica file illeggibili»).
 *
 * Prima l'indirizzo veniva passato al gestore dei download di Android, che lo richiedeva da capo: senza i cookie della
 * sessione (siti con accesso, banche, posta), senza il corpo dei moduli e senza gli indirizzi `blob:` e `data:`; il
 * file salvato era spesso la pagina d'errore o di accesso, con un nome preso dall'indirizzo. Ora si salva il corpo della
 * risposta che il motore ha già ricevuto, con il nome e il tipo che manda il sito (Content-Disposition, Content-Type).
 */
internal object Downloads {
    data class Saved(val uri: Uri, val name: String, val mime: String)

    /** Il tipo del file senza i parametri («text/csv; charset=utf-8» → «text/csv»); null se manca o è generico. */
    fun mime(contentType: String?): String? =
        contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /**
     * Il nome del file: prima `filename*=UTF-8''…` poi `filename="…"` di Content-Disposition, poi l'ultima parte
     * dell'indirizzo; si aggiunge l'estensione del tipo se manca. Mai «/» né caratteri che il telefono rifiuta.
     */
    fun fileName(url: String, disposition: String?, mime: String?, extensionOf: (String) -> String? = ::extension): String {
        val fromHeader = disposition?.let { d ->
            Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(d)?.let { m ->
                runCatching { URLDecoder.decode(m.groupValues[2].trim().trim('"'), m.groupValues[1].ifBlank { "UTF-8" }) }.getOrNull()
            } ?: Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(d)?.groupValues?.get(1)
            ?: Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(d)?.groupValues?.get(1)?.trim()
        }
        val fromUrl = if (url.startsWith("http", ignoreCase = true)) {
            url.substringBefore('#').substringBefore('?').substringAfterLast('/')
                .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        } else null
        var name = (fromHeader ?: fromUrl).orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001f:*?\"<>|]"), "_").trim().trimStart('.')
        if (name.isEmpty()) name = "download"
        if (!name.contains('.') && mime != null && mime != "application/octet-stream") {
            extensionOf(mime)?.let { name = "$name.$it" }
        }
        return name.take(120)
    }

    private fun extension(mime: String): String? = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)

    private fun header(response: WebResponse, name: String): String? =
        response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /**
     * Salva il corpo della risposta in Download (cartella pubblica, senza permessi da Android 10). Da chiamare fuori dal
     * thread principale. Null se non c'è corpo o se il telefono è più vecchio di Android 10 (allora decide il chiamante).
     */
    fun save(context: Context, response: WebResponse): Saved? {
        val body = response.body ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { runCatching { body.close() }; return null }
        val type = mime(header(response, "Content-Type"))
        val name = fileName(response.uri, header(response, "Content-Disposition"), type)
        val mime = type?.takeIf { it != "application/octet-stream" }
            ?: name.substringAfterLast('.', "").lowercase().let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "application/octet-stream"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: run { body.close(); return null }
        return try {
            body.use { input -> resolver.openOutputStream(uri)!!.use { input.copyTo(it, 64 * 1024) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            // Il nome vero, se il telefono l'ha cambiato (file con lo stesso nome già presente: «nome (1).pdf»).
            val real = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: name
            Saved(uri, real, mime)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
