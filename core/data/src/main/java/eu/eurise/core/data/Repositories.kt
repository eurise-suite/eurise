package eu.eurise.core.data

import android.content.Context
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.local.EuriseDatabase
import eu.eurise.core.data.local.NoteDao
import eu.eurise.core.data.local.NoteTagEntity
import eu.eurise.core.data.local.NotebookDao
import eu.eurise.core.data.local.NotebookEntity
import eu.eurise.core.data.local.TaskDao
import eu.eurise.core.data.local.toEntity
import eu.eurise.core.data.local.toModel
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.NoteCollections
import eu.eurise.core.data.model.Notebook
import eu.eurise.core.data.model.NotebookSummary
import eu.eurise.core.data.model.TagCount
import eu.eurise.core.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Orologio sostituibile nei test. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

/** Tag di un testo: le parole precedute da `#` (lettere, cifre, trattino e trattino basso), in minuscolo. */
fun extractTags(text: String): Set<String> =
    TAG_REGEX.findAll(text).map { it.groupValues[1].lowercase() }.toSortedSet()

private val TAG_REGEX = Regex("""(?<![\p{L}\p{N}&/])#([\p{L}\p{N}][\p{L}\p{N}_-]*)""")

class NoteRepository internal constructor(
    private val dao: NoteDao,
    private val tasks: TaskDao,
    private val clock: Clock,
) {
    /** Note attive, filtrate per testo, taccuino o tag (tutti facoltativi). */
    fun observe(query: String = "", notebookId: Long? = null, tag: String? = null): Flow<List<Note>> =
        dao.observe(query.trim(), notebookId, tag).map { list -> list.map { it.toModel() } }

    fun observeRecent(limit: Int): Flow<List<Note>> =
        dao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    fun observeTrash(): Flow<List<Note>> = dao.observeTrash().map { list -> list.map { it.toModel() } }

    fun observeCollections(): Flow<NoteCollections> =
        dao.observeCollections().map { NoteCollections(it.all, it.pinned, it.trashed) }

    fun observeTags(): Flow<List<TagCount>> =
        dao.observeTagCounts().map { list -> list.map { TagCount(it.tag, it.count) } }

    suspend fun get(id: Long): Note? = dao.get(id)?.toModel()

    /**
     * Salva una nota di solo testo. Con [id] nullo crea una nota nuova e ne restituisce l'id;
     * altrimenti aggiorna quella esistente (mantenendo data di creazione, taccuino e stato "fissata").
     * Il testo semplice diventa la versione di riferimento e sostituisce l'eventuale testo ricco.
     */
    suspend fun save(id: Long?, title: String, body: String): Long =
        write(id, title, body, content = "", notebookId = null)

    /**
     * Salva una nota con testo ricco: [plain] è il testo senza formattazione (ricerca e anteprime),
     * [html] il contenuto completo. [notebookId] vale solo per una nota nuova.
     */
    suspend fun saveRich(id: Long?, title: String, plain: String, html: String, notebookId: Long? = null): Long =
        write(id, title, plain, html, notebookId)

    private suspend fun write(id: Long?, title: String, body: String, content: String, notebookId: Long?): Long {
        val now = clock.now()
        val existing = id?.let { dao.get(it) }
        val noteId = if (existing == null) {
            dao.upsert(
                Note(0, title, body, pinned = false, createdAt = now, updatedAt = now, content = content, notebookId = notebookId)
                    .toEntity()
            )
        } else {
            dao.upsert(existing.copy(title = title, body = body, content = content, updatedAt = now))
            existing.id
        }
        dao.clearTags(noteId)
        val tags = extractTags("$title\n$body")
        if (tags.isNotEmpty()) dao.insertTags(tags.map { NoteTagEntity(noteId, it) })
        return noteId
    }

    suspend fun tagsOf(id: Long): List<String> = dao.tagsOf(id)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun setNotebook(id: Long, notebookId: Long?) = dao.setNotebook(id, notebookId)

    /** Sposta nel cestino: la nota resta recuperabile per [TRASH_DAYS] giorni. */
    suspend fun trash(id: Long) = dao.setTrashed(id, clock.now())

    suspend fun restore(id: Long) = dao.setTrashed(id, null)

    /** Elimina subito e per sempre (per esempio una nota rimasta vuota). Le attività collegate restano. */
    suspend fun delete(id: Long) {
        tasks.detachNote(id)
        dao.clearTags(id)
        dao.delete(id)
    }

    suspend fun emptyTrash() = dao.allTrashed().forEach { delete(it) }

    /** Elimina le note nel cestino da più di [TRASH_DAYS] giorni. */
    suspend fun purgeTrash() {
        val limit = clock.now() - TRASH_DAYS * 24L * 60 * 60 * 1000
        dao.trashedBefore(limit).forEach { delete(it) }
    }

    companion object {
        const val TRASH_DAYS = 30
    }
}

class NotebookRepository internal constructor(
    private val dao: NotebookDao,
    private val notes: NoteDao,
) {
    /** Taccuini in ordine, ciascuno con il numero di note attive. */
    fun observe(): Flow<List<NotebookSummary>> =
        combine(dao.observeAll(), dao.observeCounts()) { books, counts ->
            val byId = counts.associate { it.notebookId to it.count }
            books.map { NotebookSummary(it.toModel(), byId[it.id] ?: 0) }
        }

    suspend fun get(id: Long): Notebook? = dao.get(id)?.toModel()

    /** Crea un taccuino in fondo all'elenco e ne restituisce l'id. */
    suspend fun add(name: String, tint: String, kind: String = Notebook.KIND_NORMAL): Long =
        dao.upsert(NotebookEntity(name = name.trim(), tint = tint, position = dao.nextPosition(), kind = kind))

    suspend fun update(notebook: Notebook) {
        dao.upsert(NotebookEntity(notebook.id, notebook.name.trim(), notebook.tint, notebook.position, notebook.kind))
    }

    /** Elimina il taccuino; le sue note restano, senza taccuino. */
    suspend fun delete(id: Long) {
        notes.clearNotebook(id)
        dao.delete(id)
    }
}

class TaskRepository internal constructor(
    private val dao: TaskDao,
    private val clock: Clock,
) {
    fun observeOpen(): Flow<List<Task>> = dao.observeOpen().map { list -> list.map { it.toModel() } }

    fun observeDone(): Flow<List<Task>> = dao.observeDone().map { list -> list.map { it.toModel() } }

    /** Da fare con scadenza oggi o già passata. */
    fun observeDueBy(day: LocalDate): Flow<List<Task>> =
        dao.observeDueBy(day.toEpochDay()).map { list -> list.map { it.toModel() } }

    /** Attività con scadenza nei giorni da [from] a [to] inclusi, anche quelle già fatte. */
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        dao.observeDueBetween(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toModel() } }

    fun observeCompletedSince(epochMillis: Long): Flow<List<Task>> =
        dao.observeCompletedSince(epochMillis).map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): Task? = dao.get(id)?.toModel()

    /** Le attività con questi id che esistono ancora. */
    suspend fun getAll(ids: Collection<Long>): List<Task> =
        if (ids.isEmpty()) emptyList() else dao.getAll(ids.toList()).map { it.toModel() }

    /** Crea un'attività e ne restituisce l'id. [noteId] la lega alla nota da cui nasce. */
    suspend fun add(title: String, due: LocalDate? = null, notes: String = "", noteId: Long? = null): Long =
        dao.upsert(
            Task(0, title.trim(), notes, due, done = false, completedAt = null, createdAt = clock.now(), noteId = noteId)
                .toEntity()
        )

    suspend fun update(task: Task) {
        dao.upsert(task.copy(title = task.title.trim()).toEntity())
    }

    suspend fun setDone(id: Long, done: Boolean) =
        dao.setDone(id, done, completedAt = if (done) clock.now() else null)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteCompleted() = dao.deleteCompleted()
}

/** Punto unico di accesso ai dati locali dell'app hub. */
class EuriseData private constructor(context: Context, database: EuriseDatabase, clock: Clock) {
    val notes = NoteRepository(database.noteDao(), database.taskDao(), clock)
    val notebooks = NotebookRepository(database.notebookDao(), database.noteDao())
    val tasks = TaskRepository(database.taskDao(), clock)
    val calendar = CalendarRepository(context)
    val dates = eu.eurise.core.data.dates.ImportantDatesRepository(context)
    val medicines = eu.eurise.core.data.meds.MedicinesRepository(context)

    companion object {
        @Volatile private var instance: EuriseData? = null

        /** Un solo accesso ai dati per processo: lo condividono schermate, Scrivania e promemoria. */
        fun create(context: Context, clock: Clock = Clock.System): EuriseData =
            instance ?: synchronized(this) {
                instance ?: EuriseData(context.applicationContext, EuriseDatabase.create(context), clock).also { instance = it }
            }
    }
}
