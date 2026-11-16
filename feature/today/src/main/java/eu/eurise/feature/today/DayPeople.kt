package eu.eurise.feature.today

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract

/** Una persona del giorno: [why] dice perché c'è («alle 9:30», «Chiamare per la cantina»). */
data class DayPerson(val lookupKey: String, val name: String, val why: String, val phone: String?)

/**
 * Le persone di un giorno lette sul telefono: i contatti collegati agli impegni e alle cose da fare, e gli invitati
 * degli appuntamenti che sono in rubrica. Senza il permesso della rubrica non c'è nessuno. Da chiamare fuori dal
 * thread principale.
 */
internal class DayPeople(context: Context) {
    private val app = context.applicationContext

    fun canRead() = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Gli invitati di un appuntamento che sono in rubrica, come chiavi della rubrica. */
    fun attendees(eventId: Long): List<String> {
        if (!canRead() || app.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val emails = runCatching {
            CalendarContract.Attendees.query(app.contentResolver, eventId, arrayOf(CalendarContract.Attendees.ATTENDEE_EMAIL))
                ?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.takeIf { it.contains('@') }?.let(::add) } }
        }.getOrNull().orEmpty()
        return emails.distinct().take(MAX).mapNotNull(::keyForEmail)
    }

    private fun keyForEmail(email: String): String? = runCatching {
        app.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(email)),
            arrayOf(ContactsContract.Data.LOOKUP_KEY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** Nome e primo numero di un contatto, o null se non c'è più. */
    fun person(lookupKey: String, why: String): DayPerson? = runCatching {
        if (!canRead()) return null
        val (id, name) = app.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(lookupKey)),
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) to c.getString(1).orEmpty() else null } ?: return null
        val phone = app.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        DayPerson(lookupKey, name, why, phone).takeIf { name.isNotBlank() }
    }.getOrNull()

    companion object {
        const val MAX = 8
    }
}
