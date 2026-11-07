package eu.eurise.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, TaskEntity::class, NotebookEntity::class, NoteTagEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class EuriseDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun notebookDao(): NotebookDao

    companion object {
        fun create(context: Context): EuriseDatabase =
            Room.databaseBuilder(context.applicationContext, EuriseDatabase::class.java, "eurise.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Versione 2: note in HTML, taccuini, tag, cestino e attività nate da una nota. Nessun dato si perde. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN content TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN notebook_id INTEGER")
        db.execSQL("ALTER TABLE notes ADD COLUMN trashed_at INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_notebook_id ON notes (notebook_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_trashed_at ON notes (trashed_at)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS notebooks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, tint TEXT NOT NULL, position INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'normal')"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS note_tags (note_id INTEGER NOT NULL, tag TEXT NOT NULL, PRIMARY KEY(note_id, tag))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_tag ON note_tags (tag)")
        db.execSQL("ALTER TABLE tasks ADD COLUMN note_id INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_note_id ON tasks (note_id)")
    }
}
