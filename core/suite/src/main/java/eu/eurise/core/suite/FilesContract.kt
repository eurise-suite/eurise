package eu.eurise.core.suite

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/**
 * «Salva in…» e «Apri da…» di Eurise File (core/files: FilesActivity nell'app Eurise) per gli altri APK della suite:
 * Galleria, Browser, Office. L'attività chiede il permesso della suite, quindi la possono aprire solo le app firmate
 * con la stessa chiave.
 */
object FilesContract {
    const val ACTION_SAVE_IN = "eu.eurise.action.SAVE_IN"
    const val ACTION_OPEN_FROM = "eu.eurise.action.OPEN_FROM"
    const val EXTRA_KIND = "eu.eurise.extra.KIND"
    const val EXTRA_FROM = "eu.eurise.extra.FROM"
    const val EXTRA_WHERE = "eu.eurise.extra.WHERE"
    const val EXTRA_EXTENSIONS = "eu.eurise.extra.EXTENSIONS"
    const val EXTRA_ORIGIN = "eu.eurise.extra.ORIGIN"
    private const val ACTIVITY = "eu.eurise.core.files.FilesActivity"

    /**
     * «Salva in…» per [uri] (un Uri del proprio FileProvider): il permesso di lettura passa con l'intent.
     * [kind] separa le proposte («gallery», «download», «office»), [from] è il nome mostrato («da Galleria»).
     */
    fun saveIn(hubPackage: String, uri: Uri, mime: String, name: String, kind: String, from: String): Intent =
        Intent(ACTION_SAVE_IN).setClassName(hubPackage, ACTIVITY)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, name)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_FROM, from)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newRawUri(name, uri) }

    /** «Apri da…»: torna con l'Uri del file scelto (in `data`) e, se viene da Eurise File, il posto d'origine. */
    fun openFrom(hubPackage: String, extensions: Collection<String>, from: String): Intent =
        Intent(ACTION_OPEN_FROM).setClassName(hubPackage, ACTIVITY)
            .putExtra(EXTRA_EXTENSIONS, extensions.joinToString(","))
            .putExtra(EXTRA_FROM, from)
}
