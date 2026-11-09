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

    // Sincronizzazione

    @Query("SELECT * FROM notes")
    suspend fun allForSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE uid = :uid")
    suspend fun byUid(uid: String): NoteEntity?

    @Query("UPDATE notes SET uid = :uid WHERE id = :id")
    suspend fun setUid(id: Long, uid: String)

    @Query("UPDATE notes SET remote_etag = :etag, sync_base = :base, conflict = NULL, conflict_etag = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, etag: String?, base: String?)

    @Query("UPDATE notes SET conflict = :conflict, conflict_etag = :etag WHERE id = :id")
    suspend fun setConflict(id: Long, conflict: String?, etag: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTombstone(tombstone: NoteTombstoneEntity)

    @Query("SELECT * FROM note_tombstones")
    suspend fun tombstones(): List<NoteTombstoneEntity>

    @Query("DELETE FROM note_tombstones WHERE uid = :uid")
    suspend fun removeTombstone(uid: String)

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

    @Query("SELECT * FROM notebooks WHERE name = :name COLLATE NOCASE ORDER BY id LIMIT 1")
    suspend fun byName(name: String): NotebookEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM notebooks")
    suspend fun nextPosition(): Int

    @Upsert
    suspend fun upsert(notebook: NotebookEntity): Long

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
internal interface TaskDao {

    /** Da fare: prima quelle con scadenza (dalla più vicina), poi le altre in ordine di creazione. Senza i passi. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND parent_id IS NULL ORDER BY due_day IS NULL, due_day, due_minute IS NULL, due_minute, created_at")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE done = 1 AND parent_id IS NULL ORDER BY completed_at DESC")
    fun observeDone(): Flow<List<TaskEntity>>

    /** Tutte le attività (non i passi), da fare e fatte: servono alla schermata Attività. */
    @Query("SELECT * FROM tasks WHERE parent_id IS NULL ORDER BY done, due_day IS NULL, due_day, due_minute IS NULL, due_minute, priority = 0, priority, created_at")
    fun observeAll(): Flow<List<TaskEntity>>

    /** Da fare con scadenza entro [day] incluso: quelle di oggi e quelle in ritardo. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND parent_id IS NULL AND due_day IS NOT NULL AND due_day <= :day ORDER BY due_day, due_minute IS NULL, due_minute, created_at")
    fun observeDueBy(day: Long): Flow<List<TaskEntity>>

    /** Tutte le attività (fatte o no) con scadenza tra [from] e [to] inclusi: servono al Calendario. */
    @Query("SELECT * FROM tasks WHERE parent_id IS NULL AND due_day BETWEEN :from AND :to ORDER BY done, due_day, due_minute IS NULL, due_minute, created_at")
    fun observeDueBetween(from: Long, to: Long): Flow<List<TaskEntity>>

    /** Completate dopo [since] (millisecondi): servono a "Oggi" per mostrare quelle appena spuntate. */
    @Query("SELECT * FROM tasks WHERE done = 1 AND parent_id IS NULL AND completed_at >= :since ORDER BY completed_at DESC")
    fun observeCompletedSince(since: Long): Flow<List<TaskEntity>>

    /** I passi di un'attività, nell'ordine in cui sono stati aggiunti. */
    @Query("SELECT * FROM tasks WHERE parent_id = :parentId ORDER BY id")
    fun observeSteps(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parent_id = :parentId ORDER BY id")
    suspend fun steps(parentId: Long): List<TaskEntity>

    @Query("SELECT parent_id, COUNT(*) AS total, SUM(done) AS done FROM tasks WHERE parent_id IS NOT NULL GROUP BY parent_id")
    fun observeStepCounts(): Flow<List<StepCountRow>>

    /** Da fare con un avviso e scadenza nei giorni da [from] a [to]: servono ai promemoria. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND remind_before IS NOT NULL AND due_day BETWEEN :from AND :to")
    suspend fun withReminders(from: Long, to: Long): List<TaskEntity>

    /** Attività (non passi) il cui titolo contiene [text]: prima quelle da fare, poi le più recenti. */
    @Query("SELECT * FROM tasks WHERE parent_id IS NULL AND title LIKE '%' || :text || '%' ORDER BY done, created_at DESC LIMIT :limit")
    suspend fun search(text: String, limit: Int): List<TaskEntity>

    /** Le attività nate dalle voci di una nota (collegamenti impliciti). */
    @Query("SELECT * FROM tasks WHERE note_id = :noteId AND parent_id IS NULL ORDER BY created_at")
    fun observeByNote(noteId: Long): Flow<List<TaskEntity>>

    @Query("SELECT note_id FROM tasks WHERE id = :id")
    fun observeNoteId(id: Long): Flow<Long?>

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET done = :done, completed_at = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)

    @Query("UPDATE tasks SET list_id = :listId WHERE id = :id OR parent_id = :id")
    suspend fun moveToList(id: Long, listId: Long)

    @Query("UPDATE tasks SET list_id = :to WHERE list_id = :from")
    suspend fun moveAllToList(from: Long, to: Long)

    @Query("UPDATE tasks SET note_id = NULL WHERE note_id = :noteId")
    suspend fun detachNote(noteId: Long)

    /** Elimina l'attività insieme ai suoi passi. */
    @Query("DELETE FROM tasks WHERE id = :id OR parent_id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM tasks WHERE list_id = :listId")
    suspend fun inList(listId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE done = 1 AND parent_id IS NULL OR parent_id IN (SELECT id FROM tasks WHERE done = 1 AND parent_id IS NULL)")
    suspend fun completedWithSteps(): List<TaskEntity>

    @Query("UPDATE tasks SET uid = NULL, remote_etag = NULL, sync_base = NULL WHERE id IN (:ids)")
    suspend fun clearSync(ids: List<Long>)

    @Query("UPDATE tasks SET uid = :uid, remote_etag = :etag, sync_base = :base WHERE id = :id")
    suspend fun markSynced(id: Long, uid: String, etag: String?, base: String)

    @Query("UPDATE tasks SET parent_id = :parent WHERE id = :id")
    suspend fun setParent(id: Long, parent: Long)

    @Query("SELECT * FROM task_tombstones")
    suspend fun tombstones(): List<TaskTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTombstone(tombstone: TaskTombstoneEntity)

    @Query("DELETE FROM task_tombstones WHERE uid = :uid")
    suspend fun removeTombstone(uid: String)

    @Query("DELETE FROM tasks WHERE parent_id IN (SELECT id FROM tasks WHERE done = 1 AND parent_id IS NULL)")
    suspend fun deleteStepsOfCompleted()

    @Query("DELETE FROM tasks WHERE done = 1 AND parent_id IS NULL")
    suspend fun deleteCompletedTasks()

}

@Dao
internal interface TaskListDao {

    @Query(
        "SELECT l.*, (SELECT COUNT(*) FROM tasks t WHERE t.list_id = l.id AND t.done = 0 AND t.parent_id IS NULL) AS open " +
            "FROM task_lists l ORDER BY l.position, l.id"
    )
    fun observeWithCounts(): Flow<List<TaskListWithCount>>

    @Query("SELECT * FROM task_lists ORDER BY position, id")
    fun observe(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists ORDER BY position, id")
    suspend fun all(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun get(id: Long): TaskListEntity?

    @Query("SELECT COALESCE(MAX(position), 0) FROM task_lists WHERE kind IN ('main', 'user')")
    suspend fun maxUserPosition(): Int

    @Upsert
    suspend fun upsert(list: TaskListEntity): Long

    @Query("UPDATE task_lists SET sync = :sync WHERE id = :id")
    suspend fun setSync(id: Long, sync: Boolean)

    @Query("DELETE FROM task_lists WHERE id = :id AND kind = 'user'")
    suspend fun delete(id: Long)
}

@Dao
internal interface LinkDao {

    /** Tutti i collegamenti in cui compare [ref] (forma testuale di un LinkRef), da una parte o dall'altra. */
    @Query("SELECT * FROM links WHERE a = :ref OR b = :ref ORDER BY created_at DESC")
    fun observe(ref: String): Flow<List<LinkEntity>>

    @Query("SELECT COUNT(*) FROM links WHERE a = :ref OR b = :ref")
    suspend fun count(ref: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkEntity)

    @Query("DELETE FROM links WHERE a = :a AND b = :b")
    suspend fun delete(a: String, b: String)

    /** Toglie ogni collegamento di una cosa eliminata. */
    @Query("DELETE FROM links WHERE a = :ref OR b = :ref")
    suspend fun deleteAll(ref: String)
}
