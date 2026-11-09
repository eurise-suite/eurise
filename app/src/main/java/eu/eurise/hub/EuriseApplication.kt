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
        eu.eurise.core.suite.PrivacyCenter.init(this)
        // Un ripristino in attesa prende il suo posto prima che si apra il database (di solito non c'è nulla da fare).
        eu.eurise.core.cloud.backup.BackupArchive.applyPending(this)
        // I lavori di sincronizzazione vanno registrati prima che WorkManager ne avvii uno.
        CloudSync.register(NotesSyncJob(data.notes, data.notebooks))
        CloudSync.register(eu.eurise.feature.tasks.TasksSyncJob(data.tasks))
        data.tasks.onChanged = { CloudSync.get(this).requestSoon() }
        CloudSync.get(this).schedule()
        // Quando cambia un avviso di un'attività si ricalcola la prossima sveglia dei promemoria.
        data.tasks.onRemindersChanged = { eu.eurise.feature.calendar.reminders.Reminders.refresh(this) }
    }
}
