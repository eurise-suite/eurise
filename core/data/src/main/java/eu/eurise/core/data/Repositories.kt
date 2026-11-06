package eu.eurise.core.data

import android.content.Context
import eu.eurise.core.data.local.EuriseDatabase
import eu.eurise.core.data.local.NoteDao
import eu.eurise.core.data.local.TaskDao
import eu.eurise.core.data.local.toEntity
import eu.eurise.core.data.local.toModel
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Orologio sostituibile nei test. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

class NoteRepository internal constructor(
    private val dao: NoteDao,
    private val clock: Clock,
) {
    fun observe(query: String = ""): Flow<List<Note>> =
        dao.observe(query.trim()).map { list -> list.map { it.toModel() } }

    fun observeRecent(limit: Int): Flow<List<Note>> =
        dao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): Note? = dao.get(id)?.toModel()

    /**
     * Salva titolo e testo. Con [id] nullo crea una nota nuova e ne restituisce l'id;
     * altrimenti aggiorna quella esistente (mantenendo data di creazione e stato "fissata").
     */
    suspend fun save(id: Long?, title: String, body: String): Long {
        val now = clock.now()
        val existing = id?.let { dao.get(it) }
        return if (existing == null) {
            dao.upsert(Note(0, title, body, pinned = false, createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.upsert(existing.copy(title = title, body = body, updatedAt = now))
            existing.id
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun delete(id: Long) = dao.delete(id)
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

    fun observeCompletedSince(epochMillis: Long): Flow<List<Task>> =
        dao.observeCompletedSince(epochMillis).map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): Task? = dao.get(id)?.toModel()

    /** Crea un'attività e ne restituisce l'id. */
    suspend fun add(title: String, due: LocalDate? = null, notes: String = ""): Long =
        dao.upsert(
            Task(0, title.trim(), notes, due, done = false, completedAt = null, createdAt = clock.now()).toEntity()
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
class EuriseData private constructor(database: EuriseDatabase, clock: Clock) {
    val notes = NoteRepository(database.noteDao(), clock)
    val tasks = TaskRepository(database.taskDao(), clock)

    companion object {
        fun create(context: Context, clock: Clock = Clock.System): EuriseData =
            EuriseData(EuriseDatabase.create(context), clock)
    }
}
