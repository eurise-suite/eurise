package eu.eurise.core.contacts

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Note
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Un contatto della rubrica di sistema, come lo mostrano Telefono e Messaggi. */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photo: Uri?,
    val starred: Boolean,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val addresses: List<LabeledValue> = emptyList(),
    val company: String? = null,
    val note: String? = null,
) {
    val initials: String get() = Contacts.initials(name)
}

/** Un numero, un'email o un indirizzo con la sua etichetta già tradotta («Cellulare», «Lavoro»…). */
data class LabeledValue(val value: String, val label: String)

/** Ciò che si scrive nell'editor della rubrica. */
data class ContactDraft(
    val id: Long? = null,
    val name: String = "",
    val phones: List<String> = listOf(""),
    val emails: List<String> = emptyList(),
    val company: String = "",
    val address: String = "",
    val note: String = "",
)

/**
 * La rubrica di sistema (ContactsContract): Eurise non tiene una copia sua, così i contatti restano quelli che
 * sincronizzi con il tuo account (Murena, Nextcloud con DAVx⁵…). Le funzioni leggono dal disco: da chiamare fuori dal
 * thread principale (sono già sospese su [Dispatchers.IO]).
 */
object Contacts {

    fun canRead(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun canWrite(context: Context) = context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Tutti i contatti della rubrica, in ordine di nome. Senza i dettagli (vedi [detail]). */
    suspend fun all(context: Context): List<Contact> = withContext(Dispatchers.IO) {
        if (!canRead(context)) return@withContext emptyList()
        val out = ArrayList<Contact>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.STARRED,
            ),
            null, null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(2)?.trim().orEmpty()
                if (name.isNotEmpty()) out += Contact(c.getLong(0), c.getString(1).orEmpty(), name, c.getString(3)?.let(Uri::parse), c.getInt(4) == 1)
            }
        }
        out
    }

    /** Il primo numero di ogni contatto (per chiamare con un tocco dall'elenco), per id. */
    suspend fun firstNumbers(context: Context): Map<Long, String> = withContext(Dispatchers.IO) {
        if (!canRead(context)) return@withContext emptyMap()
        val out = HashMap<Long, String>()
        context.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY), null, null, "${Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { c -> while (c.moveToNext()) { val id = c.getLong(0); if (id !in out) c.getString(1)?.let { out[id] = it } } }
        out
    }

    /** Il contatto con numeri, email, indirizzi, azienda e nota. */
    suspend fun detail(context: Context, id: Long): Contact? = withContext(Dispatchers.IO) {
        if (!canRead(context)) return@withContext null
        val base = context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI, ContactsContract.Contacts.STARRED),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) Contact(id, c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2)?.let(Uri::parse), c.getInt(3) == 1) else null }
            ?: return@withContext null
        val phones = ArrayList<LabeledValue>()
        val emails = ArrayList<LabeledValue>()
        val addresses = ArrayList<LabeledValue>()
        var company: String? = null
        var note: String? = null
        val res = context.resources
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
            "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val value = c.getString(1)?.trim().orEmpty()
                if (value.isEmpty()) continue
                val type = c.getInt(2)
                val custom = c.getString(3)
                when (c.getString(0)) {
                    Phone.CONTENT_ITEM_TYPE -> if (phones.none { PhoneNumberUtils.compare(it.value, value) })
                        phones += LabeledValue(value, Phone.getTypeLabel(res, type, custom).toString())
                    Email.CONTENT_ITEM_TYPE -> if (emails.none { it.value.equals(value, true) })
                        emails += LabeledValue(value, Email.getTypeLabel(res, type, custom).toString())
                    StructuredPostal.CONTENT_ITEM_TYPE -> addresses += LabeledValue(value, StructuredPostal.getTypeLabel(res, type, custom).toString())
                    Organization.CONTENT_ITEM_TYPE -> company = value
                    Note.CONTENT_ITEM_TYPE -> note = value
                }
            }
        }
        base.copy(phones = phones, emails = emails, addresses = addresses, company = company, note = note)
    }

    /** Chi ha questo numero, se è in rubrica (confronto all'italiana: «+39 347…» = «347…»). */
    suspend fun byNumber(context: Context, number: String): Contact? = withContext(Dispatchers.IO) {
        if (!canRead(context) || number.isBlank()) return@withContext null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.STARRED),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) Contact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.let(Uri::parse), c.getInt(4) == 1) else null }
        }.getOrNull()
    }

    /** Nomi per molti numeri insieme (elenchi di chiamate e conversazioni), con una cache per giro. */
    suspend fun names(context: Context, numbers: Collection<String>): Map<String, Contact> {
        val out = HashMap<String, Contact>()
        for (n in numbers.distinct()) byNumber(context, n)?.let { out[n] = it }
        return out
    }

    /** Stella dei preferiti (compaiono in cima a Telefono). */
    suspend fun setStarred(context: Context, id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext
        val values = android.content.ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        context.contentResolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), values, null, null)
    }

    suspend fun delete(context: Context, id: Long) = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext
        context.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), null, null)
    }

    /** La bozza per modificare un contatto che c'è già. */
    suspend fun draft(context: Context, id: Long): ContactDraft? = detail(context, id)?.let { c ->
        ContactDraft(
            id = c.id, name = c.name, phones = c.phones.map { it.value }.ifEmpty { listOf("") }, emails = c.emails.map { it.value },
            company = c.company.orEmpty(), address = c.addresses.firstOrNull()?.value.orEmpty(), note = c.note.orEmpty(),
        )
    }

    /**
     * Salva la bozza: un contatto nuovo nel conto locale del telefono (o in quello predefinito della rubrica), oppure
     * sostituisce nome, numeri, email, azienda, indirizzo e nota di quello esistente. Ritorna l'id del contatto.
     */
    suspend fun save(context: Context, d: ContactDraft): Long? = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext null
        val ops = ArrayList<ContentProviderOperation>()
        val phones = d.phones.map { it.trim() }.filter { it.isNotEmpty() }
        val emails = d.emails.map { it.trim() }.filter { it.isNotEmpty() }
        if (d.id == null) {
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build()
            fun row(mime: String) = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0).withValue(ContactsContract.Data.MIMETYPE, mime)
            ops += row(StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, d.name.trim()).build()
            phones.forEach { ops += row(Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, it).withValue(Phone.TYPE, Phone.TYPE_MOBILE).build() }
            emails.forEach { ops += row(Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, it).withValue(Email.TYPE, Email.TYPE_HOME).build() }
            if (d.company.isNotBlank()) ops += row(Organization.CONTENT_ITEM_TYPE).withValue(Organization.COMPANY, d.company.trim()).build()
            if (d.address.isNotBlank()) ops += row(StructuredPostal.CONTENT_ITEM_TYPE).withValue(StructuredPostal.FORMATTED_ADDRESS, d.address.trim()).build()
            if (d.note.isNotBlank()) ops += row(Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, d.note.trim()).build()
            val result = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawId = result.firstOrNull()?.uri?.let(ContentUris::parseId) ?: return@withContext null
            return@withContext contactIdOf(context, rawId)
        }
        // Modifica: si lavora sul primo contatto «grezzo» (quello del conto che lo contiene).
        val rawId = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(d.id.toString()), null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@withContext null
        val mimes = listOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
            Organization.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE)
        ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (${mimes.joinToString { "?" }})",
                arrayOf(rawId.toString()) + mimes).build()
        fun row(mime: String) = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId).withValue(ContactsContract.Data.MIMETYPE, mime)
        ops += row(StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, d.name.trim()).build()
        phones.forEach { ops += row(Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, it).withValue(Phone.TYPE, Phone.TYPE_MOBILE).build() }
        emails.forEach { ops += row(Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, it).withValue(Email.TYPE, Email.TYPE_HOME).build() }
        if (d.company.isNotBlank()) ops += row(Organization.CONTENT_ITEM_TYPE).withValue(Organization.COMPANY, d.company.trim()).build()
        if (d.address.isNotBlank()) ops += row(StructuredPostal.CONTENT_ITEM_TYPE).withValue(StructuredPostal.FORMATTED_ADDRESS, d.address.trim()).build()
        if (d.note.isNotBlank()) ops += row(Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, d.note.trim()).build()
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        d.id
    }

    private fun contactIdOf(context: Context, rawId: Long): Long? = context.contentResolver.query(
        ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId), arrayOf(ContactsContract.RawContacts.CONTACT_ID), null, null, null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Iniziali per l'avatar: «Anna Rossi» → «AR», «studio tecnico» → «ST», «+39 347…» → «#». */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { w -> w.firstOrNull()?.isLetter() == true }
        return when {
            words.isEmpty() -> "#"
            words.size == 1 -> words[0].take(1).uppercase(Locale.getDefault())
            else -> (words[0].take(1) + words[1].take(1)).uppercase(Locale.getDefault())
        }
    }

    /** La lettera sotto cui sta un nome nell'elenco (accenti tolti, cifre e simboli sotto «#»). */
    fun letter(name: String): String {
        val first = java.text.Normalizer.normalize(name.trim().take(1), java.text.Normalizer.Form.NFD).firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    /** Numero da mostrare: quello in rubrica com'è scritto, o il formato del paese per gli sconosciuti. */
    fun format(number: String): String =
        PhoneNumberUtils.formatNumber(number, Locale.getDefault().country.ifEmpty { "IT" }) ?: number
}
