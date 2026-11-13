package eu.eurise.core.suite

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Eurise Office come editor dei documenti della suite. Il lettore PDF dell'APK principale serve a leggere; per annotare,
 * firmare, lavorare sulle pagine o preparare la condivisione passa il documento a Office (app PDF nativa), che lavora sul
 * file originale quando riceve anche il permesso di scrittura.
 */
object OfficeContract {
    /** Prima la versione ufficiale, poi quella di prova. */
    val PACKAGES = listOf("eu.eurise.office", "eu.eurise.office.debug")

    /** Il pacchetto di Office installato, o null. */
    fun installed(context: Context): String? = PACKAGES.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    /**
     * L'intent che apre [uri] in Office, o null se Office non c'è o non apre quel tipo. [writable]: Office può salvare sul
     * file stesso; altrimenti lavora su una copia e propone «Salva in…».
     */
    fun editIntent(context: Context, uri: Uri, mime: String, writable: Boolean): Intent? {
        val pkg = installed(context) ?: return null
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        val intent = Intent(Intent.ACTION_EDIT).setDataAndType(uri, mime).setPackage(pkg).addFlags(flags)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
            ?: Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).setPackage(pkg).addFlags(flags)
                .takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}
