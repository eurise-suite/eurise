package eu.eurise.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class, TaskEntity::class], version = 1, exportSchema = true)
internal abstract class EuriseDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): EuriseDatabase =
            Room.databaseBuilder(context.applicationContext, EuriseDatabase::class.java, "eurise.db").build()
    }
}
