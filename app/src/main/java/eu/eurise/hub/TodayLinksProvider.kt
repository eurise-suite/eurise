package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.suite.TodayLinksContract as C
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * «Per oggi, dalla suite» per il Browser ([C]): impegni delle prossime ore e attività dei prossimi giorni con un link.
 * Solo per le app della suite; nulla esce dal telefono. `call` arriva su un thread del Binder: leggere qui è lecito.
 */
class TodayLinksProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        if (method != C.METHOD_LIST) return null
        val data = EuriseData.create(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        data class Row(val title: String, val url: String, val kind: String, val time: Long)
        val rows = mutableListOf<Row>()
        if (data.calendar.hasPermission()) {
            runBlocking { data.calendar.startingBetween(now - 30 * 60_000L, now + C.HOURS * 3_600_000L) }
                .forEach { e ->
                    val url = C.firstUrl(e.location, e.description) ?: return@forEach
                    rows += Row(e.title, url, if (C.isMeeting(url)) C.KIND_MEETING else C.KIND_EVENT, e.beginMillis)
                }
        }
        val until = LocalDate.now().plusDays(C.TASK_DAYS.toLong())
        runCatching { runBlocking { data.tasks.observeDueBy(until).first() } }.getOrDefault(emptyList())
            .filter { !it.done }
            .forEach { t ->
                val url = C.firstUrl(t.title, t.notes) ?: return@forEach
                val title = t.title.replace(url, "").trim().ifBlank { t.title }
                rows += Row(title, url, C.KIND_TASK, t.due?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L)
            }
        val list = rows.distinctBy { it.url }.take(C.MAX)
        return Bundle().apply {
            putStringArray(C.KEY_TITLE, list.map { it.title }.toTypedArray())
            putStringArray(C.KEY_URL, list.map { it.url }.toTypedArray())
            putStringArray(C.KEY_KIND, list.map { it.kind }.toTypedArray())
            putLongArray(C.KEY_WHEN, list.map { it.time }.toLongArray())
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
