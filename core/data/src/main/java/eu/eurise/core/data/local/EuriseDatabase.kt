package eu.eurise.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class, TaskEntity::class, NotebookEntity::class, NoteTagEntity::class, NoteTombstoneEntity::class,
        TaskListEntity::class, LinkEntity::class, TaskTombstoneEntity::class, TrashEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
internal abstract class EuriseDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun linkDao(): LinkDao
    abstract fun notebookDao(): NotebookDao
    abstract fun trashDao(): TrashDao

    companion object {
        fun create(context: Context): EuriseDatabase =
            Room.databaseBuilder(context.applicationContext, EuriseDatabase::class.java, "eurise.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = insertDefaultTaskLists(db)
                })
                .build()
    }
}

/**
 * I tre elenchi di partenza, con id fissi: Attività (principale, sincronizzato quando c'è un cloud),
 * Farmaci e Date importanti (automatici, di base solo sul telefono).
 */
internal fun insertDefaultTaskLists(db: SupportSQLiteDatabase) {
    db.execSQL(
        "INSERT OR IGNORE INTO task_lists (id, name, tint, kind, sync, position) VALUES " +
            "(1, 'Attività', 'BluNotte', 'main', 1, 0), " +
            "(2, 'Farmaci', 'Borgogna', 'medicines', 0, 100), " +
            "(3, 'Date importanti', 'Prugna', 'dates', 0, 101)"
    )
}

/** Versione 4: elenchi delle attività, ora, promemoria, ripetizione, importanza e passi. Nessun dato si perde. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN list_id INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE tasks ADD COLUMN due_minute INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN remind_before INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN rrule TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN parent_id INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_list_id ON tasks (list_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_parent_id ON tasks (parent_id)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS task_lists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, tint TEXT NOT NULL, kind TEXT NOT NULL, sync INTEGER NOT NULL, position INTEGER NOT NULL)"
        )
        insertDefaultTaskLists(db)
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

/** Versione 3: identità e stato di sincronizzazione delle note, note eliminate da togliere anche dal cloud. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN uid TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN remote_etag TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN sync_base TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN conflict TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN conflict_etag TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_uid ON notes (uid)")
        db.execSQL("CREATE TABLE IF NOT EXISTS note_tombstones (uid TEXT NOT NULL, etag TEXT, PRIMARY KEY(uid))")
    }
}

/** Versione 6: identità sul server delle attività e attività da togliere dal server. Nessun dato si perde. */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN uid TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN remote_etag TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN sync_base TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_uid ON tasks (uid)")
        db.execSQL("CREATE TABLE IF NOT EXISTS task_tombstones (uid TEXT NOT NULL, etag TEXT, list_id INTEGER NOT NULL, PRIMARY KEY(uid))")
    }
}

/** Versione 5: collegamenti tra le cose della suite. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS links (a TEXT NOT NULL, b TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(a, b))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_links_b ON links (b)")
    }
}

/** Versione 7: cestino unico per attività ed eventi. Nessun dato si perde. */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS trash (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, " +
                "detail TEXT NOT NULL, deleted_at INTEGER NOT NULL, payload TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_deleted_at ON trash (deleted_at)")
    }
}
