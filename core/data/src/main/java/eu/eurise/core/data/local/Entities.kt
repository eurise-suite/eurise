package eu.eurise.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.NoteSyncState
import eu.eurise.core.data.model.Notebook
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "notes",
    indices = [Index("updated_at"), Index("notebook_id"), Index("trashed_at"), Index(value = ["uid"], unique = true)],
)
internal data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val pinned: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val content: String = "",
    @ColumnInfo(name = "notebook_id") val notebookId: Long? = null,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long? = null,
    /** Identità stabile della nota tra telefono e cloud (nome del file); nulla finché non si sincronizza. */
    val uid: String? = null,
    @ColumnInfo(name = "remote_etag") val remoteEtag: String? = null,
    /** Il file com'era all'ultima sincronizzazione: serve a capire chi ha cambiato cosa. */
    @ColumnInfo(name = "sync_base") val syncBase: String? = null,
    /** Versione del cloud in conflitto con quella del telefono, in attesa di una scelta. */
    val conflict: String? = null,
    @ColumnInfo(name = "conflict_etag") val conflictEtag: String? = null,
)

/** Nota eliminata per sempre sul telefono, da eliminare anche sul cloud alla prossima sincronizzazione. */
@Entity(tableName = "note_tombstones")
internal data class NoteTombstoneEntity(
    @PrimaryKey val uid: String,
    val etag: String?,
)

@Entity(tableName = "notebooks")
internal data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tint: String,
    val position: Int,
    val kind: String = Notebook.KIND_NORMAL,
)

/** Un tag di una nota, ricavato dalle parole con il cancelletto (#casa) nel testo. */
@Entity(tableName = "note_tags", primaryKeys = ["note_id", "tag"], indices = [Index("tag")])
internal data class NoteTagEntity(
    @ColumnInfo(name = "note_id") val noteId: Long,
    val tag: String,
)

/**
 * La scadenza è salvata come giorno dall'epoca (`LocalDate.toEpochDay`), senza fuso orario, e l'ora come minuti
 * dalla mezzanotte.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("done"), Index("due_day"), Index("note_id"), Index("list_id"), Index("parent_id")],
)
internal data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    @ColumnInfo(name = "due_day") val dueDay: Long?,
    val done: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "note_id") val noteId: Long? = null,
    @ColumnInfo(name = "list_id", defaultValue = "1") val listId: Long = TaskList.MAIN,
    @ColumnInfo(name = "due_minute") val dueMinute: Int? = null,
    @ColumnInfo(name = "remind_before") val remindBefore: Int? = null,
    val rrule: String? = null,
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
)

/**
 * Un collegamento tra due cose della suite, salvato una volta sola: [a] è sempre il riferimento minore
 * (vedi [eu.eurise.core.data.model.LinkRef]), così "nota–evento" ed "evento–nota" sono la stessa riga.
 */
@Entity(tableName = "links", primaryKeys = ["a", "b"], indices = [Index("b")])
internal data class LinkEntity(
    val a: String,
    val b: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Un elenco di attività (vedi [TaskList]). */
@Entity(tableName = "task_lists")
internal data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tint: String,
    val kind: String,
    val sync: Boolean,
    val position: Int,
)

/** Un elenco con il numero di attività ancora da fare (risultato di una query). */
internal data class TaskListWithCount(
    @androidx.room.Embedded val list: TaskListEntity,
    val open: Int,
)

/** Avanzamento dei passi di un'attività (risultato di una query). */
internal data class StepCountRow(
    @ColumnInfo(name = "parent_id") val parentId: Long,
    val total: Int,
    val done: Int,
)

internal fun TaskListEntity.toModel() = TaskList(id, name, tint, kind, sync, position)

internal fun TaskList.toEntity() = TaskListEntity(id, name, tint, kind, sync, position)

internal fun NoteEntity.toModel() =
    Note(id, title, body, pinned, createdAt, updatedAt, content, notebookId, trashedAt, uid, conflict != null)

internal fun NoteEntity.toSyncModel() = NoteSyncState(toModel(), uid, remoteEtag, syncBase, conflict, conflictEtag)

internal fun NotebookEntity.toModel() = Notebook(id, name, tint, position, kind)

internal fun TaskEntity.toModel() = Task(
    id, title, notes, dueDay?.let(LocalDate::ofEpochDay), done, completedAt, createdAt, noteId,
    listId = listId,
    time = dueMinute?.let { LocalTime.of(it / 60, it % 60) },
    remindBefore = remindBefore,
    repeat = rrule,
    priority = priority,
    parentId = parentId,
)

internal fun Task.toEntity() = TaskEntity(
    id, title, notes, due?.toEpochDay(), done, completedAt, createdAt, noteId,
    listId = listId,
    dueMinute = time?.let { it.hour * 60 + it.minute },
    remindBefore = remindBefore,
    rrule = repeat,
    priority = priority,
    parentId = parentId,
)
