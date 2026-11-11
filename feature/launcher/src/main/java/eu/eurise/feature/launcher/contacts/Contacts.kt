package eu.eurise.feature.launcher.contacts

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un contatto da mostrare nelle liste "recenti" e "preferiti". */
data class Person(
    val name: String,
    val contactId: Long?,
    val photo: Uri?,
    /** Da dove viene il contatto recente: "Telefono", "WhatsApp"… */
    val via: String? = null,
    val lastAt: Long = 0,
)

/** Un modo per raggiungere un contatto: chiamata, SMS, chat di un'app, email. */
data class ContactAction(val label: String, val detail: String, val kind: Kind, val intent: Intent) {
    enum class Kind { Call, Sms, Chat, Email }
}

/**
 * Contatti per la scheda Comunicazioni: i dieci più recenti (chiamate e chat viste nelle notifiche) e i
 * dieci preferiti con la stella in Rubrica. Tutto resta sul telefono.
 */
class ContactsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("eurise_people", Context.MODE_PRIVATE)

    fun hasContacts() = granted(Manifest.permission.READ_CONTACTS)
    fun hasCallLog() = granted(Manifest.permission.READ_CALL_LOG)

    private fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Ricorda chi ha scritto in chat, per la lista dei recenti (nome, app, ora). */
    fun remember(name: String, app: String, at: Long) {
        if (name.isBlank()) return
        val entries = history().filterNot { it.first.equals(name, ignoreCase = true) } + Triple(name, app, at)
        prefs.edit().putString("history", entries.sortedByDescending { it.third }.take(30)
            .joinToString("\n") { "${it.first.replace('\t', ' ')}\t${it.second}\t${it.third}" }).apply()
    }

    private fun history(): List<Triple<String, String, Long>> = prefs.getString("history", "").orEmpty().lines().mapNotNull { line ->
        val bits = line.split('\t')
        if (bits.size == 3) bits[2].toLongOrNull()?.let { Triple(bits[0], bits[1], it) } else null
    }

    suspend fun recent(limit: Int = 10): List<Person> = withContext(Dispatchers.IO) {
        val calls = if (hasCallLog()) runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val out = mutableListOf<Triple<String, String, Long>>()
                while (c.moveToNext() && out.size < 40) {
                    val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: c.getString(1).orEmpty()
                    out += Triple(name, "Telefono", c.getLong(2))
                }
                out
            }
        }.getOrNull().orEmpty() else emptyList()
        (calls + history())
            .filter { it.first.isNotBlank() }
            .sortedByDescending { it.third }
            .distinctBy { it.first.lowercase() }
            .take(limit)
            .map { (name, via, at) ->
                val match = if (hasContacts()) findContact(name) else null
                Person(name, match?.first, match?.second, via, at)
            }
    }

    suspend fun starred(limit: Int = 10): List<Person> = withContext(Dispatchers.IO) {
        if (!hasContacts()) return@withContext emptyList()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                "${ContactsContract.Contacts.STARRED} = 1", null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { c ->
                val out = mutableListOf<Person>()
                while (c.moveToNext() && out.size < limit) {
                    out += Person(c.getString(1).orEmpty(), c.getLong(0), c.getString(2)?.let(Uri::parse))
                }
                out
            }
        }.getOrNull().orEmpty()
    }

    /** Contatti della Rubrica il cui nome contiene [query] (prima i preferiti). */
    suspend fun search(query: String, limit: Int = 8): List<Person> = withContext(Dispatchers.IO) {
        if (!hasContacts() || query.isBlank()) return@withContext emptyList()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?", arrayOf("%" + query.trim() + "%"),
                "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.TIMES_CONTACTED} DESC, ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { c ->
                val out = mutableListOf<Person>()
                while (c.moveToNext() && out.size < limit) out += Person(c.getString(1).orEmpty(), c.getLong(0), c.getString(2)?.let(Uri::parse))
                out
            }
        }.getOrNull().orEmpty()
    }

    /** Ricorda che il contatto è stato aperto dalla ricerca: conta come un'interazione. */
    fun rememberOpened(person: Person) = remember(person.name, "Eurise", System.currentTimeMillis())

    /**
     * I contatti per la ricerca universale, prima quelli con cui si parla di più: chiamate, chat e aperture dalla
     * ricerca, ognuna pesata dalla sua età (dopo un mese conta la metà). Android non tiene più il conto dei contatti
     * (TIMES_CONTACTED è spento da Android 10), quindi il punteggio si calcola qui, sul telefono.
     * Con [query] vuota restituisce le persone più sentite.
     */
    suspend fun ranked(query: String, limit: Int = 15): List<Person> = withContext(Dispatchers.IO) {
        val scores = interactionScores()
        val q = query.trim()
        if (q.isEmpty()) {
            return@withContext scores.entries.sortedByDescending { it.value }.take(limit).map { (name, _) ->
                val match = if (hasContacts()) findContactIgnoringCase(name) else null
                Person(match?.third ?: name, match?.first, match?.second)
            }
        }
        val found = search(q, 40)
        found.sortedWith(
            compareByDescending<Person> { scores[it.name.lowercase()] ?: 0.0 }
                .thenByDescending { it.name.split(' ').any { w -> w.startsWith(q, ignoreCase = true) } },
        ).take(limit)
    }

    private fun interactionScores(): Map<String, Double> {
        val now = System.currentTimeMillis()
        val halfLife = 30L * 24 * 3600 * 1000
        fun weight(at: Long) = Math.pow(0.5, (now - at).coerceAtLeast(0).toDouble() / halfLife)
        val scores = HashMap<String, Double>()
        if (hasCallLog()) runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.CACHED_NAME} IS NOT NULL", null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < 300) {
                    val name = c.getString(0)?.trim()?.lowercase().orEmpty()
                    if (name.isNotEmpty()) scores.merge(name, weight(c.getLong(1)), Double::plus)
                }
            }
        }
        history().forEach { (name, _, at) -> scores.merge(name.trim().lowercase(), weight(at), Double::plus) }
        return scores
    }

    private fun findContactIgnoringCase(name: String): Triple<Long, Uri?, String>? = runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?", arrayOf(name), null,
        )?.use { c -> if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1)?.let(Uri::parse), c.getString(2).orEmpty()) else null }
    }.getOrNull()

    private fun findContact(name: String): Pair<Long, Uri?>? = runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?", arrayOf(name), null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) to c.getString(1)?.let(Uri::parse) else null }
    }.getOrNull()

    /**
     * I modi per raggiungere un contatto: numeri, email e le chat che le app (WhatsApp, Telegram, Signal…)
     * hanno collegato alla sua scheda in Rubrica.
     */
    suspend fun actions(person: Person): List<ContactAction> = withContext(Dispatchers.IO) {
        val id = person.contactId ?: return@withContext emptyList()
        val out = mutableListOf<ContactAction>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA3),
                "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(id.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val dataId = c.getLong(0)
                    val mime = c.getString(1).orEmpty()
                    val data1 = c.getString(2).orEmpty()
                    val data3 = c.getString(3).orEmpty()
                    when {
                        mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            out += ContactAction("Chiama", data1, ContactAction.Kind.Call, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(data1))))
                            out += ContactAction("SMS", data1, ContactAction.Kind.Sms, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(data1))))
                        }
                        mime == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                            out += ContactAction("Email", data1, ContactAction.Kind.Email, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + data1)))
                        mime.startsWith("vnd.android.cursor.item/vnd.") -> {
                            // Righe aggiunte dalle app di chat: aprire la riga apre la conversazione.
                            val label = ChatApps.entries.firstOrNull { mime.contains(it.key) }?.value ?: continue
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
                            out += ContactAction(label, data3.ifBlank { data1 }, ContactAction.Kind.Chat, Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime))
                        }
                    }
                }
            }
        }
        out.distinctBy { it.label + it.detail }
            .map { it.copy(intent = it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun start(action: ContactAction) {
        runCatching { context.startActivity(action.intent) }
    }

    /** Chiave stabile del contatto, per aprirne la scheda Persona. */
    suspend fun lookupKey(person: Person): String? = withContext(Dispatchers.IO) {
        val id = person.contactId ?: return@withContext null
        runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun openContact(person: Person) {
        val id = person.contactId ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private companion object {
        val ChatApps = linkedMapOf(
            // WhatsApp Business prima di WhatsApp: le sue righe contengono anche «whatsapp» e devono restare distinte.
            "whatsapp.w4b" to "WhatsApp Business", "whatsapp" to "WhatsApp", "telegram" to "Telegram", "thoughtcrime.securesms" to "Signal",
            "signal" to "Signal", "threema" to "Threema", "im.vector" to "Element", "simplex" to "SimpleX",
        )
    }
}
