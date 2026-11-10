package eu.eurise.keyboard

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import helium314.keyboard.latin.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tutto ciò che la tastiera legge dal telefono o chiede alle altre app di Eurise. La tastiera non ha accesso a Internet:
 * calendario e rubrica si leggono qui, attività e note le crea l'app Eurise, i codici li dà Eurise Password,
 * sempre e solo tra app firmate con la stessa chiave (permesso della suite).
 */
class Bridge(context: Context) {
    private val app = context.applicationContext
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val debug = BuildConfig.BUILD_TYPE != "release" && BuildConfig.BUILD_TYPE != "nouserlib"
    val hubPackage = if (debug) "eu.eurise.hub.debug" else "eu.eurise.hub"
    val passwordPackage = if (debug) "eu.eurise.password.debug" else "eu.eurise.password"

    fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun canReadCalendar() = granted(Manifest.permission.READ_CALENDAR)
    fun canReadContacts() = granted(Manifest.permission.READ_CONTACTS)

    // --- Calendario ---

    /** Impegni che occupano tempo tra [from] e [to]: escludono gli eventi di tutto il giorno, quelli "libero" e quelli rifiutati. */
    fun busy(from: LocalDate, to: LocalDate): List<Span> {
        if (!canReadCalendar()) return emptyList()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin); ContentUris.appendId(it, end)
        }.build()
        val out = mutableListOf<Span>()
        runCatching {
            app.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.SELF_ATTENDEE_STATUS),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) == 1) continue
                    if (c.getInt(3) == CalendarContract.Events.AVAILABILITY_FREE) continue
                    if (c.getInt(4) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    out += Span(local(c.getLong(0)), local(c.getLong(1)))
                }
            }
        }
        return out
    }

    /** Il prossimo evento con un luogo che comincia entro [hours] ore: (inizio, titolo). */
    fun nextEventWithPlace(hours: Long = 6): Pair<LocalDateTime, String>? {
        if (!canReadCalendar()) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now); ContentUris.appendId(it, now + hours * 3_600_000)
        }.build()
        return runCatching {
            app.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                var found: Pair<LocalDateTime, String>? = null
                while (found == null && c.moveToNext()) {
                    if (c.getInt(3) == 1 || c.getLong(0) < now || c.getString(2).isNullOrBlank()) continue
                    found = local(c.getLong(0)) to c.getString(1).orEmpty()
                }
                found
            }
        }.getOrNull()
    }

    /** Segna gli orari proposti come eventi "forse" nel primo calendario scrivibile (quello principale, se c'è). */
    fun addTentative(slots: List<Slot>, title: String): Int {
        if (!granted(Manifest.permission.WRITE_CALENDAR) || !canReadCalendar()) return 0
        val calendarId = runCatching {
            app.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }.getOrNull() ?: return 0
        var n = 0
        slots.forEach { s ->
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, s.day.atTime(s.from).atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, s.day.atTime(s.to).atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_TENTATIVE)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_TENTATIVE)
            }
            if (runCatching { app.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull() != null) n++
        }
        return n
    }

    private fun local(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    // --- Rubrica ---

    data class Person(val id: Long, val name: String, val starred: Boolean)
    data class Detail(val label: String, val value: String)

    /** Preferiti per primi, poi tutti in ordine alfabetico; [query] filtra per nome. */
    fun people(query: String? = null, limit: Int = 300): List<Person> {
        if (!canReadContacts()) return emptyList()
        val uri = if (query.isNullOrBlank()) ContactsContract.Contacts.CONTENT_URI
        else Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        return runCatching {
            app.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.STARRED),
                "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1 OR ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL", null,
                "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { c ->
                buildList { while (c.moveToNext() && size < limit) c.getString(1)?.takeIf { it.isNotBlank() }?.let { add(Person(c.getLong(0), it, c.getInt(2) == 1)) } }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Numeri, email e indirizzi di un contatto, con il nome davanti per poterlo inserire intero. */
    fun details(person: Person): List<Detail> {
        val out = mutableListOf(Detail(person.name, person.name))
        runCatching {
            app.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(person.id.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val v = c.getString(1)?.trim().takeUnless { it.isNullOrEmpty() } ?: continue
                    when (c.getString(0)) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> out += Detail("☎", v)
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> out += Detail("@", v)
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> out += Detail("⌂", v.replace('\n', ' '))
                    }
                }
            }
        }
        return out.distinctBy { it.value }
    }

    // --- App Eurise ---

    /** Manda all'app Eurise una frase da trasformare in attività (la legge con lo stesso lettore del + unico). */
    fun sendTask(text: String): Boolean = sendToHub(ACTION_TASK, text)

    /** Manda all'app Eurise un testo da salvare come nota. */
    fun sendNote(text: String): Boolean = sendToHub(ACTION_NOTE, text)

    private fun sendToHub(action: String, text: String): Boolean {
        if (!installed(hubPackage)) return false
        app.sendBroadcast(Intent(action).setPackage(hubPackage).putExtra(Intent.EXTRA_TEXT, text), suitePermission())
        return true
    }

    private fun suitePermission() = if (debug) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"

    fun installed(pkg: String) = runCatching { app.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    data class Code(val title: String, val code: String, val remaining: Int)
    sealed interface Codes {
        data object Missing : Codes
        data object Locked : Codes
        data class Open(val codes: List<Code>) : Codes
    }

    /** Codici 2FA delle voci di Password collegate all'app [target]. */
    fun codes(target: String): Codes {
        if (!installed(passwordPackage)) return Codes.Missing
        val uri = Uri.parse("content://$passwordPackage.codes/otp").buildUpon().appendQueryParameter("package", target).build()
        val rows = runCatching {
            app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
                }
            }
        }.getOrNull() ?: return Codes.Missing
        if (rows.any { it[0] == "locked" }) return Codes.Locked
        return Codes.Open(rows.mapNotNull { r -> r[2]?.let { Code(r[1].orEmpty(), it, r[3]?.toIntOrNull() ?: 0) } })
    }

    fun openPassword() {
        app.packageManager.getLaunchIntentForPackage(passwordPackage)?.let {
            runCatching { app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun saveContactNumber(number: String) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    companion object {
        const val ACTION_TASK = "eu.eurise.action.KEYBOARD_TASK"
        const val ACTION_NOTE = "eu.eurise.action.KEYBOARD_NOTE"
    }
}
