package eu.eurise.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Notebook
import eu.eurise.core.data.model.Task
import java.time.LocalDate

@Entity(tableName = "notes", indices = [Index("updated_at"), Index("notebook_id"), Index("trashed_at")])
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

/** La scadenza è salvata come giorno dall'epoca (`LocalDate.toEpochDay`), senza fuso orario. */
@Entity(tableName = "tasks", indices = [Index("done"), Index("due_day"), Index("note_id")])
internal data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    @ColumnInfo(name = "due_day") val dueDay: Long?,
    val done: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "note_id") val noteId: Long? = null,
)

internal fun NoteEntity.toModel() =
    Note(id, title, body, pinned, createdAt, updatedAt, content, notebookId, trashedAt)

internal fun Note.toEntity() =
    NoteEntity(id, title, body, pinned, createdAt, updatedAt, content, notebookId, trashedAt)

internal fun NotebookEntity.toModel() = Notebook(id, name, tint, position, kind)

internal fun TaskEntity.toModel() =
    Task(id, title, notes, dueDay?.let(LocalDate::ofEpochDay), done, completedAt, createdAt, noteId)

internal fun Task.toEntity() =
    TaskEntity(id, title, notes, due?.toEpochDay(), done, completedAt, createdAt, noteId)
