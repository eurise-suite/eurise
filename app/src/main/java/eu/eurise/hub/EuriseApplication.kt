package eu.eurise.hub

import android.app.Application
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.data.EuriseData
import eu.eurise.feature.notes.sync.NotesSyncJob

/** Crea una sola volta l'accesso ai dati e lo condivide con tutte le schermate. */
class EuriseApplication : Application() {
    val data: EuriseData by lazy { EuriseData.create(this) }

    override fun onCreate() {
        super.onCreate()
        // I lavori di sincronizzazione vanno registrati prima che WorkManager ne avvii uno.
        CloudSync.register(NotesSyncJob(data.notes, data.notebooks))
        CloudSync.get(this).schedule()
    }
}
