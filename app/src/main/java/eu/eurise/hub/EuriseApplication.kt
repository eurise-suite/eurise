package eu.eurise.hub

import android.app.Application
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.data.EuriseData
import eu.eurise.feature.notes.sync.NotesSyncJob
import kotlinx.coroutines.flow.first

/** Crea una sola volta l'accesso ai dati e lo condivide con tutte le schermate. */
class EuriseApplication : Application(), eu.eurise.feature.ponte.PonteToday.Source {
    val data: EuriseData by lazy { EuriseData.create(this) }

    /**
     * «Oggi sul computer» (pannello Eurise di Firefox): gli appuntamenti di oggi ancora da finire e le attività aperte.
     * La chiama il Ponte solo se l'utente l'ha acceso sul telefono; senza il permesso del Calendario, niente appuntamenti.
     */
    override suspend fun ponteToday(): eu.eurise.feature.ponte.PonteToday.Summary {
        val today = java.time.LocalDate.now()
        val events = if (!data.calendar.hasPermission()) emptyList() else
            runCatching { data.calendar.observeEvents(today, today).first() }.getOrDefault(emptyList())
        val open = runCatching { data.tasks.observeOpen().first() }.getOrDefault(emptyList())
            .filter { !it.done && it.parentId == null }
        val todos = eu.eurise.feature.ponte.PonteToday.pickTasks(open.map { eu.eurise.feature.ponte.PonteToday.Todo(it.title, it.due) }, today)
        return eu.eurise.feature.ponte.PonteToday.Summary(
            events.map { eu.eurise.feature.ponte.PonteToday.Item(it.start, it.end, it.allDay, it.title, it.location) },
            todos,
            open.size,
        )
    }

    override fun onCreate() {
        super.onCreate()
        eu.eurise.core.suite.PrivacyCenter.init(this)
        // Un ripristino in attesa prende il suo posto prima che si apra il database (di solito non c'è nulla da fare).
        eu.eurise.core.cloud.backup.BackupArchive.applyPending(this)
        // Le parole imparate dalla Tastiera (APK a parte) tornano alla tastiera appena è installata.
        Thread { eu.eurise.core.cloud.backup.KeyboardBackup.deliverPending(this) }.start()
        // I lavori di sincronizzazione vanno registrati prima che WorkManager ne avvii uno.
        CloudSync.register(NotesSyncJob(data.notes, data.notebooks))
        CloudSync.register(eu.eurise.feature.tasks.TasksSyncJob(data.tasks))
        CloudSync.register(eu.eurise.feature.calendar.assistant.AssistantSyncJob())
        data.tasks.onChanged = { CloudSync.get(this).requestSoon() }
        CloudSync.get(this).schedule()
        // Quando cambia un avviso di un'attività si ricalcola la prossima sveglia dei promemoria.
        data.tasks.onRemindersChanged = { eu.eurise.feature.calendar.reminders.Reminders.refresh(this) }
    }
}
