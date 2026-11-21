package eu.eurise.phone.backup

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.telecom.TelecomManager
import eu.eurise.core.suite.SuiteBackupProvider
import eu.eurise.phone.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Telefono nel backup della suite (area Backup, 21 novembre 2026): registro delle chiamate e numeri bloccati (stanno nel
 * database di Android) e le note delle chiamate. Il filtro dello spam vive nelle impostazioni di Eurise ed entra con lei.
 *
 * Al ripristino Android lascia scrivere i numeri bloccati solo all'app predefinita per le chiamate: finché Telefono non
 * lo è (o non ha il permesso del registro), [ready] dice di no ed Eurise riprova più tardi. Niente doppioni.
 */
class PhoneBackupProvider : SuiteBackupProvider() {
    override fun ready(context: Context): Boolean {
        val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        return dialer == context.packageName &&
            context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }

    override fun summary(context: Context): String? {
        val calls = runCatching { context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), null, null, null)?.use { it.count } }.getOrNull() ?: 0
        val blocked = blocked(context).size
        if (calls == 0 && blocked == 0) return null
        return context.getString(R.string.backup_summary_phone, calls, blocked)
    }

    override fun export(context: Context, zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry(CALLS_ENTRY))
        val w = zip.bufferedWriter()
        w.write("[")
        var first = true
        runCatching {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, CALL_COLUMNS, null, null, "${CallLog.Calls.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    CALL_COLUMNS.forEachIndexed { i, col -> if (!c.isNull(i)) o.put(col, if (c.getType(i) == Cursor.FIELD_TYPE_INTEGER) c.getLong(i) else c.getString(i)) }
                    if (!first) w.write(",")
                    first = false
                    w.write(o.toString())
                }
            }
        }
        w.write("]")
        w.flush()
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(BLOCKED_ENTRY))
        zip.write(JSONArray(blocked(context)).toString().toByteArray())
        zip.closeEntry()
        addPrefs(context, zip, *PREFS.toTypedArray())
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        val r = context.contentResolver
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            when (name) {
                CALLS_ENTRY -> {
                    val have = HashSet<String>()
                    r.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE), null, null, null)?.use { c ->
                        while (c.moveToNext()) have += "${c.getString(0)}\u0000${c.getLong(1)}"
                    }
                    val list = JSONArray(zip.bufferedReader().readText())
                    for (i in 0 until list.length()) {
                        val o = list.getJSONObject(i)
                        if (!have.add("${o.optString(CallLog.Calls.NUMBER)}\u0000${o.optLong(CallLog.Calls.DATE)}")) continue
                        val v = ContentValues()
                        CALL_COLUMNS.filter { o.has(it) }.forEach { col ->
                            when (val x = o.get(col)) { is Int -> v.put(col, x); is Long -> v.put(col, x); else -> v.put(col, x.toString()) }
                        }
                        runCatching { r.insert(CallLog.Calls.CONTENT_URI, v) }
                    }
                }
                BLOCKED_ENTRY -> {
                    val have = blocked(context).toHashSet()
                    val list = JSONArray(zip.bufferedReader().readText())
                    for (i in 0 until list.length()) {
                        val n = list.optString(i).takeIf { it.isNotBlank() && have.add(it) } ?: continue
                        runCatching {
                            r.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, ContentValues().apply {
                                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, n)
                            })
                        }
                    }
                }
                else -> restorePrefs(context, zip, name, PREFS)
            }
        }
    }

    private fun blocked(context: Context): List<String> = runCatching {
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) return@runCatching emptyList()
        context.contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, null,
        )?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }.orEmpty()
    }.getOrDefault(emptyList())

    companion object {
        private const val CALLS_ENTRY = "calls.json"
        private const val BLOCKED_ENTRY = "blocked.json"
        private val PREFS = setOf("call_notes")
        private val CALL_COLUMNS = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.NEW,
            CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.IS_READ, CallLog.Calls.FEATURES,
            CallLog.Calls.COUNTRY_ISO, CallLog.Calls.GEOCODED_LOCATION,
        )
    }
}
