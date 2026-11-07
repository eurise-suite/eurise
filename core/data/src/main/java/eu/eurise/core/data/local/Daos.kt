package eu.eurise.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface NoteDao {

    /** Note che contengono [query] nel titolo o nel testo; le fissate per prime, poi le più recenti. */
    @Query(
        """
        SELECT * FROM notes
        WHERE :query = '' OR title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%'
        ORDER BY pinned DESC, updated_at DESC
        """
    )
    fun observe(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    /** Inserisce o aggiorna; per una nota nuova restituisce l'id assegnato, altrimenti -1. */
    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
internal interface TaskDao {

    /** Da fare: prima quelle con scadenza (dalla più vicina), poi le altre in ordine di creazione. */
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY due_day IS NULL, due_day, created_at")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE done = 1 ORDER BY completed_at DESC")
    fun observeDone(): Flow<List<TaskEntity>>

    /** Da fare con scadenza entro [day] incluso: quelle di oggi e quelle in ritardo. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND due_day IS NOT NULL AND due_day <= :day ORDER BY due_day, created_at")
    fun observeDueBy(day: Long): Flow<List<TaskEntity>>

    /** Tutte le attività (fatte o no) con scadenza tra [from] e [to] inclusi: servono al Calendario. */
    @Query("SELECT * FROM tasks WHERE due_day BETWEEN :from AND :to ORDER BY done, due_day, created_at")
    fun observeDueBetween(from: Long, to: Long): Flow<List<TaskEntity>>

    /** Completate dopo [since] (millisecondi): servono a "Oggi" per mostrare quelle appena spuntate. */
    @Query("SELECT * FROM tasks WHERE done = 1 AND completed_at >= :since ORDER BY completed_at DESC")
    fun observeCompletedSince(since: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET done = :done, completed_at = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun deleteCompleted()
}
