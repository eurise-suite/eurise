package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.suite.DestinationsContract as C
import kotlinx.coroutines.runBlocking

/**
 * «Dove devi andare» per Eurise Mappe ([C]): impegni dei prossimi giorni con un luogo, dal Calendario. Solo per le app
 * della suite; nulla esce dal telefono. `call` arriva su un thread del Binder: leggere il Calendario qui è lecito.
 */
class DestinationsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        if (method != C.METHOD_LIST) return null
        val calendar = EuriseData.create(context).calendar
        val now = System.currentTimeMillis()
        val events = if (calendar.hasPermission()) runBlocking {
            calendar.startingBetween(now - 30 * 60_000L, now + C.DAYS * 86_400_000L)
        }.filter { it.location.isNotBlank() }.distinctBy { it.title + it.location + it.beginMillis }.take(C.MAX) else emptyList()
        return Bundle().apply {
            putBoolean(C.KEY_CALENDAR, calendar.hasPermission())
            putStringArray(C.KEY_TITLE, events.map { it.title }.toTypedArray())
            putStringArray(C.KEY_PLACE, events.map { it.location.trim() }.toTypedArray())
            putLongArray(C.KEY_START, events.map { it.beginMillis }.toLongArray())
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
