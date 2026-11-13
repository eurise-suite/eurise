package eu.eurise.office.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.eurise.office.BuildConfig
import eu.eurise.office.home.OfficePrefs
import eu.eurise.office.pdf.PdfActivity
import eu.eurise.office.rapida.QuickActivity
import eu.eurise.office.rapida.QuickView

/**
 * Il motore dei documenti (Collabora Online + LibreOffice): apre testo, fogli e presentazioni in LOActivity.
 * Oggi è dentro l'APK quando office-app.yml lo aggiunge (EURISE_OFFICE_AAR); il passo dopo lo scarica a richiesta
 * (tavola OffMotore) e allora [available] guarderà anche i file scaricati.
 */
object Engine {
    private const val LO_ACTIVITY = "org.libreoffice.androidlib.LOActivity"

    fun available(): Boolean = BuildConfig.ENGINE_IN_APK && runCatching { Class.forName(LO_ACTIVITY) }.isSuccess

    /**
     * Apre [uri]: i PDF nell'editor nativo; Word, Excel e OpenDocument nella vista rapida (salvo [full] o la scelta
     * «Apri sempre nella modifica completa»); il resto nel motore. Lettura e, se Office può scrivere, scrittura passano
     * con l'intent: è così che LOActivity e la vista rapida decidono se il documento si salva al suo posto.
     */
    fun open(context: Context, uri: Uri, mime: String?, kind: DocKind, writable: Boolean, name: String? = null, full: Boolean = false): Boolean {
        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        val quick = !full && QuickView.supports(name, mime) && !(OfficePrefs.alwaysFull(context) && available())
        val intent = if (kind == DocKind.Pdf) {
            Intent(context, PdfActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        } else if (quick) {
            Intent(context, QuickActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, QuickView.mimeOf(name, mime))
        } else {
            if (!available()) return false
            Intent(Intent.ACTION_VIEW).setClassName(context, LO_ACTIVITY).setDataAndType(uri, mime ?: mimeOf(kind))
        }
        context.startActivity(intent.addFlags(grants))
        return true
    }

    fun mimeOf(kind: DocKind): String = when (kind) {
        DocKind.Text -> "application/vnd.oasis.opendocument.text"
        DocKind.Sheet -> "application/vnd.oasis.opendocument.spreadsheet"
        DocKind.Slides -> "application/vnd.oasis.opendocument.presentation"
        DocKind.Drawing -> "application/vnd.oasis.opendocument.graphics"
        DocKind.Pdf -> "application/pdf"
        DocKind.Other -> "application/octet-stream"
    }
}
