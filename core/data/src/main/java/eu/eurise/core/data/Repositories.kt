package eu.eurise.core.data

import android.content.Context
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.local.EuriseDatabase
import eu.eurise.core.data.local.NoteDao
import eu.eurise.core.data.local.NoteEntity
import eu.eurise.core.data.local.NoteTombstoneEntity
import eu.eurise.core.data.local.toSyncModel
import eu.eurise.core.data.local.NoteTagEntity
import eu.eurise.core.data.local.NotebookDao
import eu.eurise.core.data.local.NotebookEntity
import eu.eurise.core.data.local.LinkDao
import eu.eurise.core.data.local.TaskEntity
import eu.eurise.core.data.local.TaskTombstoneEntity
import eu.eurise.core.data.local.LinkEntity
import eu.eurise.core.data.local.TaskDao
import eu.eurise.core.data.local.TaskListDao
import eu.eurise.core.data.local.TaskListEntity
import eu.eurise.core.data.local.toEntity
import eu.eurise.core.data.local.toModel
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.NoteCollections
import eu.eurise.core.data.model.NoteSyncState
import eu.eurise.core.data.model.Notebook
import eu.eurise.core.data.model.NotebookSummary
import eu.eurise.core.data.model.TagCount
import eu.eurise.core.data.model.Link
import eu.eurise.core.data.model.LinkRef
import eu.eurise.core.data.model.Recurrence
import eu.eurise.core.data.model.StepProgress
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.data.model.TaskListSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    private val links: LinkDao,
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
                NoteEntity(
                    title = title, body = body, pinned = false, createdAt = now, updatedAt = now,
                    content = content, notebookId = notebookId,
                )
            )
        } else {
            dao.upsert(existing.copy(title = title, body = body, content = content, updatedAt = now))
            existing.id
        }
        retag(noteId, title, body)
        return noteId
    }

    private suspend fun retag(noteId: Long, title: String, body: String) {
        dao.clearTags(noteId)
        val tags = extractTags("$title\n$body")
        if (tags.isNotEmpty()) dao.insertTags(tags.map { NoteTagEntity(noteId, it) })
    }

    suspend fun tagsOf(id: Long): List<String> = dao.tagsOf(id)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun setNotebook(id: Long, notebookId: Long?) = dao.setNotebook(id, notebookId)

    /** Sposta nel cestino: la nota resta recuperabile per [TRASH_DAYS] giorni. */
    suspend fun trash(id: Long) = dao.setTrashed(id, clock.now())

    suspend fun restore(id: Long) = dao.setTrashed(id, null)

    /**
     * Elimina subito e per sempre (per esempio una nota rimasta vuota). Le attività collegate restano.
     * Se la nota era già sul cloud, alla prossima sincronizzazione sparisce anche da lì.
     */
    suspend fun delete(id: Long) {
        dao.get(id)?.let { note -> if (note.uid != null && note.remoteEtag != null) dao.addTombstone(NoteTombstoneEntity(note.uid, note.remoteEtag)) }
        tasks.detachNote(id)
        links.deleteAll(LinkRef.note(id).toString())
        dao.clearTags(id)
        dao.delete(id)
    }

    suspend fun emptyTrash() = dao.allTrashed().forEach { delete(it) }

    /** Elimina le note nel cestino da più di [TRASH_DAYS] giorni. */
    suspend fun purgeTrash() {
        val limit = clock.now() - TRASH_DAYS * 24L * 60 * 60 * 1000
        dao.trashedBefore(limit).forEach { delete(it) }
    }

    // Sincronizzazione

    /** Tutte le note, anche nel cestino, con il loro stato di sincronizzazione. */
    suspend fun syncStates(): List<NoteSyncState> = dao.allForSync().map { it.toSyncModel() }

    suspend fun syncState(id: Long): NoteSyncState? = dao.get(id)?.toSyncModel()

    suspend fun assignUid(id: Long, uid: String) = dao.setUid(id, uid)

    /** Registra che il cloud ha ora il file [base] con [etag]. */
    suspend fun markSynced(id: Long, etag: String?, base: String?) = dao.markSynced(id, etag, base)

    /** Mette da parte la versione del cloud che non si è potuta unire da sola. */
    suspend fun setConflict(id: Long, remote: String, etag: String?) = dao.setConflict(id, remote, etag)

    /**
     * Salva la nota con le scelte fatte sul conflitto. La versione del cloud diventa la base, così alla
     * prossima sincronizzazione parte la versione scelta, al posto di quella del cloud.
     */
    suspend fun resolveConflict(id: Long, title: String, plain: String, html: String) {
        val state = dao.get(id) ?: return
        write(id, title, plain, html, null)
        dao.markSynced(id, state.conflictEtag, state.conflict)
    }

    /**
     * Crea o aggiorna una nota con i dati arrivati dal cloud, senza cambiarne le date: [id] nullo la crea.
     * Restituisce l'id della nota.
     */
    suspend fun importRemote(
        id: Long?, uid: String, title: String, plain: String, html: String, pinned: Boolean,
        notebookId: Long?, createdAt: Long, updatedAt: Long, trashedAt: Long?,
    ): Long {
        val existing = id?.let { dao.get(it) }
        val entity = (existing ?: NoteEntity(title = title, body = plain, pinned = pinned, createdAt = createdAt, updatedAt = updatedAt))
            .copy(
                title = title, body = plain, content = html, pinned = pinned, notebookId = notebookId,
                createdAt = createdAt, updatedAt = updatedAt, trashedAt = trashedAt, uid = uid,
            )
        val noteId = dao.upsert(entity).let { if (existing != null) existing.id else it }
        retag(noteId, title, plain)
        return noteId
    }

    suspend fun tombstones(): List<Pair<String, String?>> = dao.tombstones().map { it.uid to it.etag }

    suspend fun removeTombstone(uid: String) = dao.removeTombstone(uid)

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

    /** Il taccuino con questo nome (senza badare a maiuscole), creato con [tint] se non c'è. */
    suspend fun findOrCreate(name: String, tint: String): Long =
        dao.byName(name.trim())?.id ?: add(name, tint)

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

/**
 * Preferenze delle Attività, condivise da tutte le schermate (anche dal Calendario).
 * Di base le attività fatte restano in fondo agli elenchi e quelle con una scadenza compaiono nel Calendario.
 */
class TaskSettings internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_tasks", Context.MODE_PRIVATE)
    private val _showDone = MutableStateFlow(prefs.getBoolean(KEY_SHOW_DONE, true))
    private val _inCalendar = MutableStateFlow(prefs.getBoolean(KEY_IN_CALENDAR, true))

    val showDone: StateFlow<Boolean> = _showDone
    val showInCalendar: StateFlow<Boolean> = _inCalendar

    fun setShowDone(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DONE, value).apply()
        _showDone.value = value
    }

    fun setShowInCalendar(value: Boolean) {
        prefs.edit().putBoolean(KEY_IN_CALENDAR, value).apply()
        _inCalendar.value = value
    }

    private companion object {
        const val KEY_SHOW_DONE = "show_done"
        const val KEY_IN_CALENDAR = "in_calendar"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
/** Attività da togliere dal server. */
data class TaskTombstone(val uid: String, val etag: String?, val listId: Long)

class TaskRepository internal constructor(
    private val dao: TaskDao,
    private val lists: TaskListDao,
    private val links: LinkDao,
    private val clock: Clock,
    val settings: TaskSettings,
) {
    /** Chiamata quando un'attività con un avviso cambia: l'app la usa per riprogrammare i promemoria. */
    @Volatile var onRemindersChanged: (() -> Unit)? = null

    /** Chiamata dopo ogni modifica fatta dalla persona: l'app la usa per chiedere una sincronizzazione. */
    @Volatile var onChanged: (() -> Unit)? = null

    /** Le righe di [rows] già sul server ne usciranno al prossimo giro: restano sul telefono senza legami col server. */
    private suspend fun detach(rows: List<TaskEntity>) {
        val synced = rows.filter { it.uid != null }
        synced.forEach { dao.addTombstone(TaskTombstoneEntity(it.uid!!, it.remoteEtag, it.listId)) }
        if (synced.isNotEmpty()) dao.clearSync(synced.map { it.id })
    }

    private suspend fun withSteps(id: Long): List<TaskEntity> = listOfNotNull(dao.get(id)) + dao.steps(id)

    // Sincronizzazione

    suspend fun listsToSync(): List<TaskList> = lists.all().filter { it.sync }.map { it.toModel() }
    suspend fun forSync(listId: Long): List<Task> = dao.inList(listId).map { it.toModel() }
    suspend fun tombstones(): List<TaskTombstone> = dao.tombstones().map { TaskTombstone(it.uid, it.etag, it.listId) }
    suspend fun removeTombstone(uid: String) = dao.removeTombstone(uid)
    suspend fun markSynced(id: Long, uid: String, etag: String?, base: String) = dao.markSynced(id, uid, etag, base)
    suspend fun deleteSynced(id: Long) = dao.delete(id).also { onRemindersChanged?.invoke() }
    suspend fun applySynced(task: Task, uid: String, etag: String?, base: String) {
        dao.upsert(task.copy(uid = uid, remoteEtag = etag, syncBase = base).toEntity())
        onRemindersChanged?.invoke()
    }
    suspend fun insertSynced(task: Task): Long = dao.upsert(task.toEntity())
    suspend fun setParent(id: Long, parent: Long) = dao.setParent(id, parent)

    private fun remindersChanged(vararg tasks: Task?) {
        if (tasks.any { it?.remindBefore != null }) onRemindersChanged?.invoke()
    }

    fun observeOpen(): Flow<List<Task>> = dao.observeOpen().map { list -> list.map { it.toModel() } }

    fun observeDone(): Flow<List<Task>> = dao.observeDone().map { list -> list.map { it.toModel() } }

    /** Tutte le attività (senza i passi): prima quelle da fare, ordinate per scadenza. */
    fun observeAll(): Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    /** Da fare con scadenza oggi o già passata. */
    fun observeDueBy(day: LocalDate): Flow<List<Task>> =
        dao.observeDueBy(day.toEpochDay()).map { list -> list.map { it.toModel() } }

    /** Attività con scadenza nei giorni da [from] a [to] inclusi, anche quelle già fatte. */
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        dao.observeDueBetween(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toModel() } }

    /** Come [observeDueBetween], ma vuoto se nelle Attività è spento "Attività anche nel Calendario". */
    fun observeForCalendar(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        settings.showInCalendar.flatMapLatest { on -> if (on) observeDueBetween(from, to) else flowOf(emptyList()) }

    fun observeCompletedSince(epochMillis: Long): Flow<List<Task>> =
        dao.observeCompletedSince(epochMillis).map { list -> list.map { it.toModel() } }

    fun observeSteps(parentId: Long): Flow<List<Task>> = dao.observeSteps(parentId).map { list -> list.map { it.toModel() } }

    /** Avanzamento dei passi per ogni attività che ne ha. */
    fun observeStepProgress(): Flow<Map<Long, StepProgress>> =
        dao.observeStepCounts().map { rows -> rows.associate { it.parentId to StepProgress(it.done, it.total) } }

    fun observeLists(): Flow<List<TaskListSummary>> =
        lists.observeWithCounts().map { rows -> rows.map { TaskListSummary(it.list.toModel(), it.open) } }

    suspend fun get(id: Long): Task? = dao.get(id)?.toModel()

    /** Attività il cui titolo contiene [text] (per collegarle). */
    suspend fun search(text: String, limit: Int = 8): List<Task> =
        if (text.isBlank()) emptyList() else dao.search(text.trim(), limit).map { it.toModel() }

    /** Le attività con questi id che esistono ancora. */
    suspend fun getAll(ids: Collection<Long>): List<Task> =
        if (ids.isEmpty()) emptyList() else dao.getAll(ids.toList()).map { it.toModel() }

    /** Crea un'attività e ne restituisce l'id. [noteId] la lega alla nota da cui nasce. */
    suspend fun add(
        title: String,
        due: LocalDate? = null,
        notes: String = "",
        noteId: Long? = null,
        listId: Long = TaskList.MAIN,
        time: LocalTime? = null,
        remindBefore: Int? = null,
        repeat: String? = null,
        priority: Int = Task.PRIORITY_NONE,
    ): Long {
        val task = Task(
            0, title.trim(), notes, due, done = false, completedAt = null, createdAt = clock.now(), noteId = noteId,
            listId = listId, time = time, remindBefore = remindBefore, repeat = repeat, priority = priority,
        )
        return dao.upsert(task.toEntity()).also { remindersChanged(task); onChanged?.invoke() }
    }

    /** Aggiunge un passo all'attività [parentId], nello stesso elenco. */
    suspend fun addStep(parentId: Long, title: String): Long {
        val parent = dao.get(parentId) ?: return 0
        return dao.upsert(
            Task(0, title.trim(), "", null, false, null, clock.now(), listId = parent.listId, parentId = parentId).toEntity()
        ).also { onChanged?.invoke() }
    }

    suspend fun update(task: Task) {
        val before = dao.get(task.id)?.toModel()
        dao.upsert(task.copy(title = task.title.trim()).toEntity())
        if (task.parentId == null) {
            if (before != null && before.listId != task.listId) detach(withSteps(task.id).map { it.copy(listId = before.listId) })
            dao.moveToList(task.id, task.listId)
        }
        remindersChanged(before, task)
        onChanged?.invoke()
    }

    /**
     * Spunta o toglie la spunta. Un'attività che si ripete, spuntata, lascia il posto alla prossima volta
     * (con i passi da rifare); quella fatta resta come storico, senza ripetizione.
     */
    suspend fun setDone(id: Long, done: Boolean) {
        val before = dao.get(id)?.toModel()
        val now = clock.now()
        dao.setDone(id, done, completedAt = if (done) now else null)
        val task = before ?: return
        remindersChanged(task)
        onChanged?.invoke()
        if (!done || task.done || task.parentId != null) return
        val rule = Recurrence.parse(task.repeat) ?: return
        val next = task.copy(
            id = 0, done = false, completedAt = null, createdAt = now, noteId = null,
            due = rule.next(task.due ?: LocalDate.now()), uid = null, remoteEtag = null, syncBase = null,
        )
        val nextId = dao.upsert(next.toEntity())
        dao.steps(id).forEach { step -> dao.upsert(step.copy(id = 0, done = false, completedAt = null, parentId = nextId, uid = null, remoteEtag = null, syncBase = null)) }
        dao.upsert(task.copy(done = true, completedAt = now, repeat = null).toEntity())
        remindersChanged(next)
    }

    suspend fun moveToList(id: Long, listId: Long) {
        detach(withSteps(id))
        dao.moveToList(id, listId)
        onChanged?.invoke()
    }

    /** Elimina l'attività con i suoi passi. */
    suspend fun delete(id: Long) {
        links.deleteAll(LinkRef.task(id).toString())
        detach(withSteps(id))
        dao.delete(id)
        onChanged?.invoke()
    }

    suspend fun deleteCompleted() {
        detach(dao.completedWithSteps())
        dao.deleteStepsOfCompleted()
        dao.deleteCompletedTasks()
        onChanged?.invoke()
    }

    /** Attività da fare con un avviso che cade tra [from] e [to]. */
    suspend fun remindersBetween(from: LocalDateTime, to: LocalDateTime): List<Task> =
        dao.withReminders(from.toLocalDate().toEpochDay(), to.toLocalDate().plusDays(1).toEpochDay())
            .map { it.toModel() }
            .filter { t -> t.reminderAt()?.let { !it.isBefore(from) && !it.isAfter(to) } == true }

    /** Crea un elenco in fondo a quelli dell'utente e ne restituisce l'id. */
    suspend fun addList(name: String, tint: String): Long =
        lists.upsert(TaskListEntity(0, name.trim(), tint, TaskList.KIND_USER, sync = true, position = lists.maxUserPosition() + 1))

    suspend fun updateList(list: TaskList) {
        lists.upsert(list.copy(name = list.name.trim()).toEntity())
    }

    suspend fun setListSync(id: Long, sync: Boolean) = lists.setSync(id, sync)

    /** Elimina un elenco dell'utente; le sue attività passano all'elenco principale. */
    suspend fun deleteList(id: Long) {
        val list = lists.get(id) ?: return
        if (list.kind != TaskList.KIND_USER) return
        detach(dao.inList(id))
        dao.moveAllToList(id, TaskList.MAIN)
        lists.delete(id)
    }
}

/**
 * Collegamenti tra le cose della suite (note, attività, eventi, contatti). Restano solo sul telefono.
 * Oltre a quelli scelti a mano include quelli che nascono da sé: le attività create dalle voci di una nota.
 */
class LinkRepository internal constructor(
    private val dao: LinkDao,
    private val tasks: TaskDao,
    private val clock: Clock,
) {
    /** I collegamenti di [ref], prima quelli impliciti, poi i più recenti. */
    fun observe(ref: LinkRef): Flow<List<Link>> {
        val key = ref.toString()
        val chosen = dao.observe(key).map { rows ->
            rows.mapNotNull { row -> LinkRef.parse(if (row.a == key) row.b else row.a)?.let { Link(it, removable = true, createdAt = row.createdAt) } }
        }
        val id = ref.key.toLongOrNull()
        val implicit: Flow<List<Link>> = when {
            id == null -> flowOf(emptyList())
            ref.kind == LinkRef.Kind.Note -> tasks.observeByNote(id).map { list ->
                list.map { Link(LinkRef.task(it.id), removable = false, createdAt = it.createdAt) }
            }
            ref.kind == LinkRef.Kind.Task -> tasks.observeNoteId(id).map { noteId ->
                listOfNotNull(noteId?.let { Link(LinkRef.note(it), removable = false, createdAt = 0) })
            }
            else -> flowOf(emptyList())
        }
        return combine(implicit, chosen) { i, c -> (i + c).distinctBy { it.other } }
    }

    /** Collega due cose; se sono già collegate non cambia nulla. */
    suspend fun link(x: LinkRef, y: LinkRef) {
        if (x == y) return
        val (a, b) = if (x < y) x to y else y to x
        dao.insert(LinkEntity(a.toString(), b.toString(), clock.now()))
    }

    suspend fun unlink(x: LinkRef, y: LinkRef) {
        val (a, b) = if (x < y) x to y else y to x
        dao.delete(a.toString(), b.toString())
    }

    /** Toglie tutti i collegamenti di una cosa che non esiste più (per esempio un evento cancellato altrove). */
    suspend fun forget(ref: LinkRef) = dao.deleteAll(ref.toString())
}

/** Punto unico di accesso ai dati locali dell'app hub. */
class EuriseData private constructor(context: Context, database: EuriseDatabase, clock: Clock) {
    val notes = NoteRepository(database.noteDao(), database.taskDao(), database.linkDao(), clock)
    val notebooks = NotebookRepository(database.notebookDao(), database.noteDao())
    val tasks = TaskRepository(database.taskDao(), database.taskListDao(), database.linkDao(), clock, TaskSettings(context))
    val links = LinkRepository(database.linkDao(), database.taskDao(), clock)
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
