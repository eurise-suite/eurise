package eu.eurise.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Conteggio per tag letto dal database. */
internal data class TagCountRow(val tag: String, val count: Int)

@Dao
internal interface NoteDao {

    /**
     * Note attive (non nel cestino) che contengono [query] nel titolo o nel testo, eventualmente solo
     * quelle del taccuino [notebookId] o con il tag [tag]. Le fissate per prime, poi le più recenti.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE trashed_at IS NULL
          AND (:query = '' OR title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
          AND (:notebookId IS NULL OR notebook_id = :notebookId)
          AND (:tag IS NULL OR id IN (SELECT note_id FROM note_tags WHERE tag = :tag))
        ORDER BY pinned DESC, updated_at DESC
        """
    )
    fun observe(query: String, notebookId: Long?, tag: String?): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed_at IS NULL ORDER BY pinned DESC, updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed_at IS NOT NULL ORDER BY trashed_at DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM notes WHERE trashed_at IS NULL) AS `all`,
          (SELECT COUNT(*) FROM notes WHERE trashed_at IS NULL AND pinned = 1) AS pinned,
          (SELECT COUNT(*) FROM notes WHERE trashed_at IS NOT NULL) AS trashed
        """
    )
    fun observeCollections(): Flow<CollectionsRow>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    /** Inserisce o aggiorna; per una nota nuova restituisce l'id assegnato, altrimenti -1. */
    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET notebook_id = :notebookId WHERE id = :id")
    suspend fun setNotebook(id: Long, notebookId: Long?)

    @Query("UPDATE notes SET trashed_at = :at WHERE id = :id")
    suspend fun setTrashed(id: Long, at: Long?)

    @Query("UPDATE notes SET notebook_id = NULL WHERE notebook_id = :notebookId")
    suspend fun clearNotebook(notebookId: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM notes WHERE trashed_at IS NOT NULL AND trashed_at < :before")
    suspend fun trashedBefore(before: Long): List<Long>

    @Query("SELECT id FROM notes WHERE trashed_at IS NOT NULL")
    suspend fun allTrashed(): List<Long>

    // Tag

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun clearTags(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<NoteTagEntity>)

    @Query("SELECT tag FROM note_tags WHERE note_id = :noteId ORDER BY tag")
    suspend fun tagsOf(noteId: Long): List<String>

    @Query(
        """
        SELECT t.tag AS tag, COUNT(*) AS count FROM note_tags t
        JOIN notes n ON n.id = t.note_id
        WHERE n.trashed_at IS NULL
        GROUP BY t.tag ORDER BY count DESC, t.tag
        """
    )
    fun observeTagCounts(): Flow<List<TagCountRow>>
}

internal data class CollectionsRow(val all: Int, val pinned: Int, val trashed: Int)

/** Conteggio delle note attive di un taccuino. */
internal data class NotebookCountRow(
    @androidx.room.ColumnInfo(name = "notebook_id") val notebookId: Long,
    val count: Int,
)

@Dao
internal interface NotebookDao {

    @Query("SELECT * FROM notebooks ORDER BY position, id")
    fun observeAll(): Flow<List<NotebookEntity>>

    @Query(
        """
        SELECT notebook_id, COUNT(*) AS count FROM notes
        WHERE trashed_at IS NULL AND notebook_id IS NOT NULL
        GROUP BY notebook_id
        """
    )
    fun observeCounts(): Flow<List<NotebookCountRow>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun get(id: Long): NotebookEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM notebooks")
    suspend fun nextPosition(): Int

    @Upsert
    suspend fun upsert(notebook: NotebookEntity): Long

    @Query("DELETE FROM notebooks WHERE id = :id")
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

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET done = :done, completed_at = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)

    @Query("UPDATE tasks SET note_id = NULL WHERE note_id = :noteId")
    suspend fun detachNote(noteId: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun deleteCompleted()
}
