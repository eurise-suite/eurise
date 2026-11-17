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
    /** Dove si apre «Apri da…»: la chiave di una cartella («telefono:Documents», «<account>:Lavoro»…), come [EXTRA_ORIGIN]. */
    const val EXTRA_START = "eu.eurise.extra.START"
    /** Chiave della cartella principale del telefono per [EXTRA_START]. */
    const val START_PHONE = "phone"

    /** I luoghi di Eurise File (telefono e cloud collegati), per le altre app della suite: metodo [METHOD_PLACES]. */
    fun placesAuthority(hubPackage: String) = "$hubPackage.places"
    /** Risposta: [KEY_IDS] (id dell'account, vuoto per il telefono), [KEY_LABELS] e [KEY_KINDS] (Murena, Nextcloud…). */
    const val METHOD_PLACES = "places"
    const val KEY_IDS = "ids"
    const val KEY_LABELS = "labels"
    const val KEY_KINDS = "kinds"
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

    /**
     * «Apri da…»: torna con l'Uri del file scelto (in `data`) e, se viene da Eurise File, il posto d'origine. Con [start]
     * (chiave di una cartella o [START_PHONE]) si apre già lì invece che sui recenti.
     */
    fun openFrom(hubPackage: String, extensions: Collection<String>, from: String, start: String? = null): Intent =
        Intent(ACTION_OPEN_FROM).setClassName(hubPackage, ACTIVITY)
            .putExtra(EXTRA_EXTENSIONS, extensions.joinToString(","))
            .putExtra(EXTRA_FROM, from)
            .apply { if (start != null) putExtra(EXTRA_START, start) }
}
