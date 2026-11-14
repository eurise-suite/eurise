package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.suite.YourDaysContract as C
import java.time.LocalDate

/**
 * «I tuoi giorni» per Eurise Mappe ([C]): il proprio compleanno o anniversario oggi, dalle Date importanti. Solo per le
 * app della suite; esce solo il tipo di giorno, mai il nome. `call` arriva su un thread del Binder.
 */
class YourDaysProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        if (method != C.METHOD_TODAY) return null
        val dates = EuriseData.create(context).dates
        val today = LocalDate.now()
        val kind = when {
            dates.myBirthday()?.occursOn(today) == true -> C.BIRTHDAY
            dates.myAnniversary()?.occursOn(today) == true -> C.ANNIVERSARY
            else -> C.NONE
        }
        return Bundle().apply { putInt(C.KEY_KIND, kind) }
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
