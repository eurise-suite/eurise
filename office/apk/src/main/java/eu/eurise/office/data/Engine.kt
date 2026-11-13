package eu.eurise.office.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import eu.eurise.office.BuildConfig
import eu.eurise.office.home.OfficePrefs
import eu.eurise.office.pdf.PdfActivity
import eu.eurise.office.rapida.QuickActivity
import eu.eurise.office.rapida.QuickView

/**
 * Il motore dei documenti (Collabora Online + LibreOffice) è l'APK compagno «Eurise Office Motore» (strada A, decisa il
 * 13 novembre 2026). Office lo considera presente solo se è firmato con la sua stessa chiave: un'app qualsiasi con lo
 * stesso nome di pacchetto non riceve mai i documenti. Si scarica la prima volta che serve ([EngineInstaller], OffMotore).
 */
object Engine {
    private const val LO_ACTIVITY = "org.libreoffice.androidlib.LOActivity"

    val packageName: String get() = BuildConfig.ENGINE_PACKAGE

    fun available(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        sameSigner(context, packageName)
    }.getOrDefault(false)

    /** Versione del motore installato, per «Informazioni». */
    fun version(context: Context): String? =
        runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

    /**
     * Apre [uri]: i PDF nell'editor nativo; Word, Excel e OpenDocument nella vista rapida (salvo [full] o la scelta
     * «Apri sempre nella modifica completa»); il resto nel motore. Lettura e, se Office può scrivere, scrittura passano
     * con l'intent: è così che LOActivity e la vista rapida decidono se il documento si salva al suo posto.
     * Restituisce false se serve il motore e non c'è.
     */
    fun open(context: Context, uri: Uri, mime: String?, kind: DocKind, writable: Boolean, name: String? = null, full: Boolean = false): Boolean {
        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        val engine = available(context)
        val quick = !full && QuickView.supports(name, mime) && !(OfficePrefs.alwaysFull(context) && engine)
        val intent = if (kind == DocKind.Pdf) {
            Intent(context, PdfActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        } else if (quick) {
            Intent(context, QuickActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, QuickView.mimeOf(name, mime))
        } else {
            if (!engine) return false
            Intent(Intent.ACTION_VIEW).setClassName(packageName, LO_ACTIVITY).setDataAndType(uri, mime ?: mimeOf(kind))
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

    /** Il pacchetto installato [other] è firmato con la stessa chiave di Office. */
    private fun sameSigner(context: Context, other: String): Boolean =
        context.packageManager.checkSignatures(context.packageName, other) == PackageManager.SIGNATURE_MATCH

    /** Le firme di Office, per confrontarle con quelle di un APK scaricato prima di installarlo. */
    internal fun ownSignatures(context: Context): Set<String> = signaturesOf(
        if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        },
    )

    @Suppress("DEPRECATION")
    internal fun signaturesOf(info: PackageInfo?): Set<String> {
        info ?: return emptySet()
        val signing = if (Build.VERSION.SDK_INT >= 28) info.signingInfo else null
        val list = when {
            signing == null -> info.signatures
            signing.hasMultipleSigners() -> signing.apkContentsSigners
            // Solo il certificato attuale: con la rotazione delle chiavi la cronologia ne avrebbe di più.
            else -> signing.signingCertificateHistory?.takeLast(1)?.toTypedArray()
        }
        return list.orEmpty().map { it.toCharsString() }.toSet()
    }
}
