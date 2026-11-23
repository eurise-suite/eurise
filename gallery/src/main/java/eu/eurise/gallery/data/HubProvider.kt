package eu.eurise.gallery.data

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import eu.eurise.core.suite.AppHubContract as C
import eu.eurise.core.suite.ServiceLog
import eu.eurise.core.suite.SuiteAccess
import eu.eurise.gallery.R
import eu.eurise.gallery.ui.GalPrivacy

/** I nomi dei servizi della Galleria nel centro privacy e nel registro ([ServiceLog]). */
object GalHub {
    const val NAMES = "names"
    const val UPLOAD = "upload"
}

/**
 * La Galleria nell'app Eurise ([C]): Luoghi (nomi da OpenStreetMap) e caricamento sul cloud con le tacchette, lo stato e
 * il registro dei contatti, spegnimento senza conferme; le foto e i video nel cestino di Android per il cestino unico,
 * anche quando l'app Eurise non ha il permesso di leggere le foto. Rimettere o eliminare per sempre lo chiede Android
 * alla persona: lo fa l'app Eurise con le richieste di MediaStore, qui si danno solo i nomi. Anche gli originali messi da
 * parte con «Sostituisci» (30 giorni): si eliminano qui; per rimetterli l'app Eurise apre la foto nella Galleria, che
 * chiede ad Android il permesso di riscriverla. Solo app della suite.
 */
class HubProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!SuiteAccess.callerAllowed(context)) throw SecurityException("Eurise: solo le app della suite")
        val store = GalleryStore.get(context)
        return when (method) {
            C.METHOD_PRIVACY -> {
                val s = store.settings.value
                C.servicesBundle(
                    listOf(
                        C.Service(GalHub.NAMES, context.getString(R.string.gpv_names), context.getString(R.string.gpv_names_d), GalPrivacy.NAMES, "EU", s.placesOn && s.namesOn),
                        C.Service(GalHub.UPLOAD, context.getString(R.string.gpv_upload), context.getString(R.string.gpv_upload_d), GalPrivacy.UPLOAD, "EU", s.uploadOn),
                    ),
                    ServiceLog.text(context),
                )
            }
            C.METHOD_DISABLE -> {
                when (arg) {
                    GalHub.NAMES -> { store.update { it.copy(namesOn = false) }; runCatching { store.clearPlaceNames() } }
                    GalHub.UPLOAD -> { store.update { it.copy(uploadOn = false) }; UploadWorker.schedule(context, store.settings.value) }
                    else -> return null
                }
                Bundle().apply { putBoolean(C.KEY_OK, true) }
            }
            C.METHOD_TRASH -> C.trashBundle(C.Trash(trashed(context.contentResolver) + if (extras?.getBoolean(C.KEY_ORIGINALS) == true) originals(context) else emptyList(), locked = false, count = 0).let { it.copy(count = it.items.size) })
            // Un originale messo da parte è un file privato della Galleria: si toglie qui, senza chiedere ad Android.
            C.METHOD_DELETE -> Bundle().apply { putBoolean(C.KEY_OK, arg != null && dropOriginal(context, arg)) }
            // Foto: Android chiede conferma alla persona, lo fa l'app Eurise (MediaStore.createTrashRequest /
            // createDeleteRequest). Originali: serve il permesso di riscrivere la foto, chiesto dalla Galleria quando si apre.
            C.METHOD_RESTORE -> Bundle().apply { putBoolean(C.KEY_OK, false) }
            else -> null
        }
    }

    /** Foto e video nel cestino di Android (dal 11): id = indirizzo content://, quando sono stati tolti, peso. */
    private fun trashed(resolver: ContentResolver): List<C.Item> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
            )
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_EXPIRES,
        )
        return runCatching {
            resolver.query(uri, projection, args, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        // Android tiene le cose nel cestino 30 giorni: la scadenza dice quando sono state tolte.
                        val expires = c.getLong(3) * 1000L
                        val at = if (expires > 0) expires - 30 * 86_400_000L else 0L
                        add(C.Item(ContentUris.withAppendedId(uri, c.getLong(0)).toString(), c.getString(1).orEmpty(), at, c.getLong(2)))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /** Gli originali tenuti 30 giorni dopo «Sostituisci»: id con [C.ORIGINAL_PREFIX], nome della foto, peso del file. */
    private fun originals(context: android.content.Context): List<C.Item> = GalleryStore.get(context).kept().mapNotNull { k ->
        val file = java.io.File(k.file)
        if (!file.isFile) return@mapNotNull null
        val name = runCatching {
            context.contentResolver.query(Uri.parse(k.uri), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: Uri.parse(k.uri).lastPathSegment.orEmpty()
        C.Item(originalId(k), name, k.savedAt, file.length())
    }

    private fun originalId(k: eu.eurise.gallery.logic.KeptOriginal) = "${C.ORIGINAL_PREFIX}${k.savedAt}:${k.uri}"

    /** Toglie l'originale [id], solo se è tra quelli messi da parte: nessun altro file si può nominare da fuori. */
    private fun dropOriginal(context: android.content.Context, id: String): Boolean {
        val store = GalleryStore.get(context)
        val k = store.kept().firstOrNull { originalId(it) == id } ?: return false
        java.io.File(k.file).delete()
        store.removeKept(k)
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
