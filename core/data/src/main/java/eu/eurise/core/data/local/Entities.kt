package eu.eurise.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Task
import java.time.LocalDate

@Entity(tableName = "notes", indices = [Index("updated_at")])
internal data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val pinned: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** La scadenza è salvata come giorno dall'epoca (`LocalDate.toEpochDay`), senza fuso orario. */
@Entity(tableName = "tasks", indices = [Index("done"), Index("due_day")])
internal data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    @ColumnInfo(name = "due_day") val dueDay: Long?,
    val done: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

internal fun NoteEntity.toModel() = Note(id, title, body, pinned, createdAt, updatedAt)

internal fun Note.toEntity() = NoteEntity(id, title, body, pinned, createdAt, updatedAt)

internal fun TaskEntity.toModel() =
    Task(id, title, notes, dueDay?.let(LocalDate::ofEpochDay), done, completedAt, createdAt)

internal fun Task.toEntity() =
    TaskEntity(id, title, notes, due?.toEpochDay(), done, completedAt, createdAt)
