package eu.eurise.core.links

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.model.LinkRef
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.calendar.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Una cosa collegabile, pronta da mostrare. La riga di dettagli la compone l'interfaccia (tipo, quando).
 *
 * @property at quando: scadenza di un'attività o inizio di un evento; nullo per note e contatti.
 * @property withTime se [at] ha un'ora significativa (no per le scadenze in giornata e gli eventi di tutto il giorno).
 * @property beginMillis per gli eventi, l'inizio dell'occorrenza mostrata: serve ad aprirla.
 */
data class LinkItem(
    val ref: LinkRef,
    val title: String,
    val at: LocalDateTime? = null,
    val withTime: Boolean = false,
    val beginMillis: Long? = null,
    val color: Int? = null,
    val done: Boolean = false,
    val trashed: Boolean = false,
)

/** Esito della lettura di un riferimento. */
sealed interface Resolved {
    data class Found(val item: LinkItem) : Resolved

    /** La cosa non esiste più: il collegamento si può togliere. */
    data object Gone : Resolved

    /** Non si può leggere ora (manca un permesso): il collegamento resta, solo non si mostra. */
    data object Unavailable : Resolved
}

/**
 * Legge e cerca le cose collegabili in tutta la suite: note, attività, eventi del calendario e contatti.
 * Tutto avviene sul telefono; contatti e calendario si leggono solo se il permesso è già stato dato, senza chiederlo.
 */
class LinkResolver(context: Context, private val data: EuriseData) {

    private val app = context.applicationContext

    fun canReadContacts(): Boolean =
        app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun canReadCalendar(): Boolean = data.calendar.hasPermission()

    suspend fun resolve(ref: LinkRef): Resolved = withContext(Dispatchers.IO) {
        val id = ref.key.toLongOrNull()
        when (ref.kind) {
            LinkRef.Kind.Note -> id?.let { data.notes.get(it) }?.let { Resolved.Found(it.toItem()) } ?: Resolved.Gone
            LinkRef.Kind.Task -> id?.let { data.tasks.get(it) }?.let { Resolved.Found(it.toItem()) } ?: Resolved.Gone
            LinkRef.Kind.Event ->
                if (!canReadCalendar()) Resolved.Unavailable
                else id?.let { data.calendar.getForLink(it) }?.let { Resolved.Found(it.toItem()) } ?: Resolved.Gone
            LinkRef.Kind.Contact ->
                if (!canReadContacts()) Resolved.Unavailable
                else contactName(ref.key)?.let { Resolved.Found(LinkItem(ref, it)) } ?: Resolved.Gone
        }
    }

    /** Cerca in tutta la suite; [kinds] limita i tipi; ogni tipo porta al massimo [perKind] risultati. */
    suspend fun search(
        query: String,
        kinds: Set<LinkRef.Kind> = LinkRef.Kind.entries.toSet(),
        perKind: Int = 6,
    ): List<LinkItem> = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.length < 2) return@withContext emptyList()
        buildList {
            if (LinkRef.Kind.Note in kinds) addAll(data.notes.observe(text).first().take(perKind).map { it.toItem() })
            if (LinkRef.Kind.Task in kinds) addAll(data.tasks.search(text, perKind).map { it.toItem() })
            if (LinkRef.Kind.Event in kinds && canReadCalendar()) addAll(data.calendar.search(text, perKind).map { it.toItem() })
            if (LinkRef.Kind.Contact in kinds && canReadContacts()) {
                addAll(searchContacts(text, perKind).map { (key, name) -> LinkItem(LinkRef.contact(key), name) })
            }
        }
    }

    /** Apre la scheda di sistema di un contatto. */
    fun openContact(lookupKey: String) {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(lookupKey))
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun Note.toItem() = LinkItem(
        LinkRef.note(id),
        title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() },
        trashed = trashedAt != null,
    )

    private fun Task.toItem() = LinkItem(
        LinkRef.task(id), title,
        at = due?.atTime(time ?: java.time.LocalTime.MIDNIGHT), withTime = time != null, done = done,
    )

    private fun CalendarEvent.toItem() = LinkItem(
        LinkRef.event(eventId), title,
        at = start, withTime = !allDay, beginMillis = beginMillis, color = color,
    )

    private fun contactName(lookupKey: String): String? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(lookupKey))
        app.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun searchContacts(text: String, limit: Int): List<Pair<String, String>> = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(text))
        app.contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE",
        )?.use { c ->
            buildList { while (c.moveToNext() && size < limit) c.getString(0)?.let { add(it to c.getString(1).orEmpty()) } }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
