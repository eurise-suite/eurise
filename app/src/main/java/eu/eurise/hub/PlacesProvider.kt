package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.files.EuriseFiles
import eu.eurise.core.suite.FilesContract as C

/**
 * I luoghi di Eurise File (telefono e cloud collegati) per le altre app della suite ([C.METHOD_PLACES]): Office li usa
 * per la dock «Telefono / Cloud» della schermata iniziale. Solo nomi e id degli account, mai credenziali. Solo app con la
 * stessa firma: il controllo è qui, `call` non guarda il manifesto.
 */
class PlacesProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        if (method != C.METHOD_PLACES) return null
        val places = EuriseFiles.get(context).places()
        return Bundle().apply {
            putStringArray(C.KEY_IDS, places.map { it.account.orEmpty() }.toTypedArray())
            putStringArray(C.KEY_LABELS, places.map { it.label }.toTypedArray())
            putStringArray(C.KEY_KINDS, places.map { it.kind?.name.orEmpty() }.toTypedArray())
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        val PERMISSIONS = listOf("eu.eurise.permission.SUITE", "eu.eurise.permission.SUITE_DEBUG")
    }
}
