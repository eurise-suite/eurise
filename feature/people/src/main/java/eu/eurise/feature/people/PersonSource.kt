package eu.eurise.feature.people

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.MonthDay

/** Quanto serve alla scheda, letto dalla Rubrica del telefono. */
internal data class PersonInfo(
    val name: String,
    val photo: Bitmap?,
    val phones: List<String>,
    val emails: List<String>,
    val birthday: MonthDay?,
)

/** Legge dalla Rubrica "AAAA-MM-GG" o "--MM-GG", le due forme usate per i compleanni. */
internal fun parseBirthday(text: String?): MonthDay? {
    val m = Regex("""^(?:\d{4}|-)-(\d{1,2})-(\d{1,2})""").find(text?.trim().orEmpty()) ?: return null
    return runCatching { MonthDay.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
}

/** Accesso alla Rubrica e agli invitati del calendario: tutto resta sul telefono. */
internal class PersonSource(context: Context) {
    private val app = context.applicationContext

    private fun contactUri(key: String): Uri =
        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(key))

    suspend fun load(key: String): PersonInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val (id, name, photoUri) = app.contentResolver.query(
                contactUri(key),
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1).orEmpty(), c.getString(2)) else null } ?: return@runCatching null

            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()
            var birthday: MonthDay? = null
            app.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2),
                "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(id.toString()),
                "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC, ${ContactsContract.Data.IS_PRIMARY} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val value = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    when (c.getString(0)) {
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phones += value
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails += value
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                            if (c.getInt(2) == CommonDataKinds.Event.TYPE_BIRTHDAY) birthday = parseBirthday(value)
                    }
                }
            }
            val photo = photoUri?.let { uri -> app.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream) }
            PersonInfo(name, photo, phones.distinct(), emails.distinct(), birthday)
        }.getOrNull()
    }

    /** Gli eventi a cui la persona è invitata, riconosciuta dall'indirizzo email; i più recenti per primi. */
    suspend fun eventsWith(emails: List<String>, limit: Int = 60): List<Long> = withContext(Dispatchers.IO) {
        if (emails.isEmpty()) return@withContext emptyList()
        runCatching {
            val marks = emails.joinToString(",") { "?" }
            app.contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                arrayOf(CalendarContract.Attendees.EVENT_ID),
                "${CalendarContract.Attendees.ATTENDEE_EMAIL} IN ($marks)", emails.toTypedArray(),
                "${CalendarContract.Attendees.EVENT_ID} DESC",
            )?.use { c ->
                buildList { while (c.moveToNext() && size < limit) add(c.getLong(0)) }.distinct()
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun open(intent: Intent) {
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun call(number: String) = open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
    fun message(number: String) = open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))
    fun email(address: String) = open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    fun openInContacts(key: String) = open(Intent(Intent.ACTION_VIEW, contactUri(key)))
}
