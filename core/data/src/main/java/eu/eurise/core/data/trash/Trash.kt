package eu.eurise.core.data.trash

import eu.eurise.core.data.Clock
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.local.LinkDao
import eu.eurise.core.data.local.LinkEntity
import eu.eurise.core.data.local.TrashDao
import eu.eurise.core.data.local.TrashEntity
import eu.eurise.core.data.model.LinkRef
import eu.eurise.core.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Una cosa nel cestino unico. [key] la identifica: "trash:<id>" per attività ed eventi, "note:<id>" per le note. */
data class TrashEntry(
    val key: String,
    val kind: Kind,
    val title: String,
    val detail: String,
    val deletedAt: Long,
) {
    enum class Kind { Note, Task, Event }
}

/**
 * Cestino unico di note, attività ed eventi: tutto resta 30 giorni e torna con i suoi collegamenti. Le note usano il
 * loro cestino; attività ed eventi vengono copiati qui prima di essere tolti. File e foto hanno il loro cestino
 * (File e quello di Android) e li unisce la schermata.
 */
class TrashRepository internal constructor(
    private val dao: TrashDao,
    private val links: LinkDao,
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val calendar: CalendarRepository,
    private val clock: Clock,
) {
    fun observe(): Flow<List<TrashEntry>> = combine(dao.observe(), notes.observeTrash()) { rows, trashedNotes ->
        val own = rows.map { r ->
            TrashEntry("trash:${r.id}", if (r.kind == KIND_EVENT) TrashEntry.Kind.Event else TrashEntry.Kind.Task, r.title, r.detail, r.deletedAt)
        }
        val fromNotes = trashedNotes.map { n ->
            TrashEntry("note:${n.id}", TrashEntry.Kind.Note, n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }, n.body.take(80), n.trashedAt ?: 0L)
        }
        (own + fromNotes).sortedByDescending { it.deletedAt }
    }

    /** Mette nel cestino un'attività con i suoi passi; i collegamenti tornano con il ripristino. */
    suspend fun trashTask(id: Long) {
        val task = tasks.get(id) ?: return
        val steps = tasks.stepsOf(id)
        val payload = JSONObject()
            .put("task", task.toJson())
            .put("steps", JSONArray(steps.map { it.toJson() }))
            .put("links", linksJson(LinkRef.task(id)))
        dao.insert(TrashEntity(kind = KIND_TASK, title = task.title, detail = task.due?.toString().orEmpty(), deletedAt = clock.now(), payload = payload.toString()))
        tasks.delete(id)
    }

    /**
     * Mette nel cestino un evento (non ricorrente) e lo toglie dal calendario. Torna come evento nuovo nello stesso
     * calendario, con gli stessi collegamenti. Falso se il calendario rifiuta.
     */
    suspend fun trashEvent(eventId: Long, beginMillis: Long): Boolean {
        val e = calendar.getOccurrence(eventId, beginMillis) ?: return calendar.delete(eventId)
        val payload = JSONObject()
            .put("calendar", e.calendarId).put("title", e.title).put("location", e.location).put("description", e.description)
            .put("allDay", e.allDay).put("start", e.start.toString()).put("end", e.end.toString())
            .put("links", linksJson(LinkRef.event(eventId)))
        if (!calendar.delete(eventId)) return false
        links.deleteAll(LinkRef.event(eventId).toString())
        dao.insert(TrashEntity(kind = KIND_EVENT, title = e.title, detail = e.start.toString(), deletedAt = clock.now(), payload = payload.toString()))
        return true
    }

    /** Rimette a posto; restituisce il riferimento della cosa ripristinata (gli id di attività ed eventi cambiano). */
    suspend fun restore(entry: TrashEntry): LinkRef? {
        val noteId = entry.key.removePrefix("note:").takeIf { entry.key.startsWith("note:") }?.toLongOrNull()
        if (noteId != null) { notes.restore(noteId); return LinkRef.note(noteId) }
        val id = entry.key.removePrefix("trash:").toLongOrNull() ?: return null
        val row = dao.get(id) ?: return null
        val p = JSONObject(row.payload)
        val ref = when (row.kind) {
            KIND_TASK -> {
                val newId = tasks.restoreFromTrash(taskFromJson(p.getJSONObject("task")))
                val steps = p.optJSONArray("steps") ?: JSONArray()
                for (i in 0 until steps.length()) tasks.restoreFromTrash(taskFromJson(steps.getJSONObject(i)).copy(parentId = newId))
                LinkRef.task(newId)
            }
            else -> {
                val draft = EventDraft(
                    null, p.getLong("calendar"), p.getString("title"), p.optString("location"), p.optString("description"),
                    p.optBoolean("allDay"), LocalDateTime.parse(p.getString("start")), LocalDateTime.parse(p.getString("end")),
                )
                LinkRef.event(calendar.save(draft) ?: return null)
            }
        }
        val saved = p.optJSONArray("links") ?: JSONArray()
        for (i in 0 until saved.length()) {
            LinkRef.parse(saved.getString(i))?.let { other -> link(ref, other) }
        }
        dao.delete(id)
        return ref
    }

    suspend fun deleteForever(entry: TrashEntry) {
        if (entry.key.startsWith("note:")) entry.key.removePrefix("note:").toLongOrNull()?.let { notes.delete(it) }
        else entry.key.removePrefix("trash:").toLongOrNull()?.let { dao.delete(it) }
    }

    /** Toglie per sempre ciò che è nel cestino da più di 30 giorni (note comprese). */
    suspend fun purge() {
        val limit = clock.now() - DAYS * 86_400_000L
        dao.before(limit).forEach { dao.delete(it) }
        notes.purgeTrash()
    }

    private suspend fun linksJson(ref: LinkRef): JSONArray {
        val key = ref.toString()
        return JSONArray(links.all(key).map { if (it.a == key) it.b else it.a })
    }

    private suspend fun link(x: LinkRef, y: LinkRef) {
        if (x == y) return
        val (a, b) = if (x < y) x to y else y to x
        links.insert(LinkEntity(a.toString(), b.toString(), clock.now()))
    }

    private fun Task.toJson() = JSONObject()
        .put("title", title).put("notes", notes).put("due", due?.toString() ?: "").put("done", done)
        .put("completedAt", completedAt ?: -1L).put("createdAt", createdAt).put("noteId", noteId ?: -1L).put("listId", listId)
        .put("time", time?.toString() ?: "").put("remindBefore", remindBefore ?: -1).put("repeat", repeat ?: "").put("priority", priority)

    private fun taskFromJson(o: JSONObject) = Task(
        id = 0, title = o.getString("title"), notes = o.optString("notes"),
        due = o.optString("due").ifBlank { null }?.let(LocalDate::parse),
        done = o.optBoolean("done"), completedAt = o.optLong("completedAt", -1).takeIf { it >= 0 },
        createdAt = o.optLong("createdAt"), noteId = o.optLong("noteId", -1).takeIf { it >= 0 },
        listId = o.optLong("listId", 1), time = o.optString("time").ifBlank { null }?.let(LocalTime::parse),
        remindBefore = o.optInt("remindBefore", -1).takeIf { it >= 0 }, repeat = o.optString("repeat").ifBlank { null },
        priority = o.optInt("priority"),
    )

    companion object {
        const val DAYS = 30
        private const val KIND_TASK = "task"
        private const val KIND_EVENT = "event"
    }
}
