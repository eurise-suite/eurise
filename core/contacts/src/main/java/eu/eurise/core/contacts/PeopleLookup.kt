package eu.eurise.core.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Website
import eu.eurise.core.suite.PeopleContract

/**
 * Le risposte della Rubrica per «Una persona, tutti i canali» ([PeopleContract]): chi c'è dietro un numero, un'email o
 * un utente della Chat. L'utente della Chat si riconosce se è scritto nel contatto (messaggistica o sito, anche come
 * link matrix.to) oppure, se no, dal nome uguale a quello di un solo contatto. Chiamate bloccanti: dal fornitore, fuori
 * dal thread principale.
 */
object PeopleLookup {
    private val MXID = Regex("@[A-Za-z0-9._=\\-/+]+:[A-Za-z0-9.\\-]+(:\\d+)?")

    fun resolve(context: Context, addresses: List<String>): Pair<List<PeopleContract.Person>, IntArray> {
        if (!Contacts.canRead(context)) return emptyList<PeopleContract.Person>() to IntArray(addresses.size) { -1 }
        val ids = addresses.map { a ->
            runCatching {
                when {
                    a.startsWith("tel:") -> byPhone(context, a.removePrefix("tel:"))
                    a.startsWith("mailto:") -> byEmail(context, a.removePrefix("mailto:"))
                    a.startsWith("mx:") -> a.removePrefix("mx:").split('|', limit = 2).let { byMatrix(context, it[0], it.getOrNull(1).orEmpty()) }
                    else -> null
                }
            }.getOrNull()
        }
        val byId = LinkedHashMap<Long, PeopleContract.Person>()
        ids.filterNotNull().distinct().forEach { id -> runCatching { person(context, id) }.getOrNull()?.let { byId[id] = it } }
        val people = byId.values.toList()
        val index = IntArray(addresses.size) { i -> ids[i]?.let { id -> byId[id]?.let(people::indexOf) } ?: -1 }
        return people to index
    }

    fun search(context: Context, query: String, limit: Int = 20): List<PeopleContract.Person> {
        if (!Contacts.canRead(context) || query.isBlank()) return emptyList()
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?", arrayOf("%" + query.trim() + "%"),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c -> while (c.moveToNext() && ids.size < limit) ids += c.getLong(0) }
        return ids.mapNotNull { person(context, it) }
    }

    private fun byPhone(context: Context, number: String): Long? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private fun byEmail(context: Context, address: String): Long? {
        if (address.isBlank()) return null
        val uri = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(address.trim()))
        return context.contentResolver.query(uri, arrayOf(Email.CONTACT_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private fun byMatrix(context: Context, userId: String, displayName: String): Long? {
        if (userId.isNotBlank()) {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} IN (?, ?) AND ${ContactsContract.Data.DATA1} LIKE ?",
                arrayOf(Im.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE, "%$userId%"), null,
            )?.use { if (it.moveToFirst()) return it.getLong(0) }
        }
        val name = displayName.trim()
        if (name.length < 3 || name.startsWith("@")) return null
        // Solo un contatto con quel nome: con due «Marco» non si indovina.
        return context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE", arrayOf(name), null,
        )?.use { c -> if (c.count == 1 && c.moveToFirst()) c.getLong(0) else null }
    }

    /** Il contatto [id] con numeri, email e utenti della Chat scritti nel contatto (bloccante). */
    fun forContact(context: Context, id: Long): PeopleContract.Person? = if (Contacts.canRead(context)) runCatching { person(context, id) }.getOrNull() else null

    private fun person(context: Context, id: Long): PeopleContract.Person? {
        val base = context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString()),
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null } ?: return null
        val phones = ArrayList<String>()
        val emails = ArrayList<String>()
        val matrix = ArrayList<String>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val v = c.getString(1)?.trim().orEmpty()
                if (v.isEmpty()) continue
                when (c.getString(0)) {
                    Phone.CONTENT_ITEM_TYPE -> if (phones.none { android.telephony.PhoneNumberUtils.compare(it, v) }) phones += v
                    Email.CONTENT_ITEM_TYPE -> if (emails.none { it.equals(v, true) }) emails += v
                    Im.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE -> MXID.find(v)?.value?.lowercase()?.let { if (it !in matrix) matrix += it }
                }
            }
        }
        return PeopleContract.Person(base.first.ifEmpty { "id:$id" }, base.second, phones, emails, matrix)
    }
}
