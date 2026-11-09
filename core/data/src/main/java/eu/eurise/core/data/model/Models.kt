package eu.eurise.core.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Una nota.
 *
 * @property body testo semplice della nota, senza formattazione: serve a ricerca, anteprime e Scrivania.
 * @property content la nota completa in HTML (testo ricco, elenchi di controllo, tabelle). Vuoto per le
 *   note scritte come testo semplice (per esempio dalla cattura rapida): in quel caso fa fede [body].
 * @property notebookId taccuino della nota; nullo se non è in nessun taccuino.
 * @property trashedAt quando è finita nel cestino; nullo se la nota è attiva.
 */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val content: String = "",
    val notebookId: Long? = null,
    val trashedAt: Long? = null,
    val uid: String? = null,
    /** Vero se la nota è cambiata in due posti e aspetta che si scelga quale versione tenere. */
    val hasConflict: Boolean = false,
)

/**
 * Lo stato di sincronizzazione di una nota.
 *
 * @property base il file della nota com'era all'ultima sincronizzazione (null se non è mai stata sincronizzata).
 * @property conflict il file del cloud in conflitto, con il suo [conflictEtag].
 */
data class NoteSyncState(
    val note: Note,
    val uid: String?,
    val remoteEtag: String?,
    val base: String?,
    val conflict: String?,
    val conflictEtag: String?,
)

/**
 * Un taccuino che raccoglie note.
 *
 * @property tint nome della tinta della suite (per esempio "Salvia"), vedi `CalendarTint`.
 * @property kind tipo di taccuino: [KIND_NORMAL] per ora; il diario di viaggio arriverà con Mappe e Galleria.
 */
data class Notebook(
    val id: Long,
    val name: String,
    val tint: String,
    val position: Int,
    val kind: String = KIND_NORMAL,
) {
    companion object {
        const val KIND_NORMAL = "normal"
        const val KIND_TRAVEL = "travel"
    }
}

/** Un taccuino con il numero di note attive che contiene. */
data class NotebookSummary(val notebook: Notebook, val count: Int)

/** Un tag con il numero di note attive che lo usano. */
data class TagCount(val tag: String, val count: Int)

/** Conteggi delle raccolte della schermata Organizza. */
data class NoteCollections(val all: Int, val pinned: Int, val trashed: Int)

/**
 * Una cosa da fare, con scadenza facoltativa (giorno e, se serve, ora).
 *
 * @property noteId nota da cui nasce l'attività (una voce di un elenco di controllo), se c'è.
 * @property listId elenco a cui appartiene ([TaskList.MAIN] se non scelto).
 * @property time ora della scadenza; nulla per le attività "in giornata".
 * @property remindBefore minuti di anticipo dell'avviso rispetto alla scadenza; nullo senza avviso.
 * @property repeat ripetizione come regola RRULE di iCalendar (vedi [Recurrence]); nulla se non si ripete.
 * @property priority importanza con i valori di CalDAV: 0 nessuna, 5 media, 1 alta.
 * @property parentId attività di cui questa è un passo; nulla per le attività vere e proprie.
 */
data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val due: LocalDate?,
    val done: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val noteId: Long? = null,
    val listId: Long = TaskList.MAIN,
    val time: LocalTime? = null,
    val remindBefore: Int? = null,
    val repeat: String? = null,
    val priority: Int = PRIORITY_NONE,
    val parentId: Long? = null,
    /** Identità sul server (nome del file); nulla finché non si sincronizza. */
    val uid: String? = null,
    val remoteEtag: String? = null,
    /** L'attività com'era all'ultima sincronizzazione. */
    val syncBase: String? = null,
) {
    val important: Boolean get() = priority in 1..4
    val medium: Boolean get() = priority == PRIORITY_MEDIUM

    /** Momento dell'avviso: l'ora della scadenza (o le 9 se manca) meno l'anticipo. */
    fun reminderAt(): LocalDateTime? {
        val day = due ?: return null
        val before = remindBefore ?: return null
        return day.atTime(time ?: LocalTime.of(9, 0)).minusMinutes(before.toLong())
    }

    companion object {
        const val PRIORITY_NONE = 0
        const val PRIORITY_HIGH = 1
        const val PRIORITY_MEDIUM = 5
    }
}

/**
 * Un elenco di attività. "Attività" è l'elenco principale; Farmaci e Date importanti sono automatici
 * (le voci le crea la suite) e di base restano sul telefono.
 *
 * @property tint nome di una delle otto tinte della suite.
 * @property sync se l'elenco va sincronizzato con il servizio cloud collegato.
 */
data class TaskList(
    val id: Long,
    val name: String,
    val tint: String,
    val kind: String,
    val sync: Boolean,
    val position: Int,
) {
    val automatic: Boolean get() = kind == KIND_MEDICINES || kind == KIND_DATES

    companion object {
        const val MAIN = 1L
        const val MEDICINES = 2L
        const val DATES = 3L
        const val KIND_MAIN = "main"
        const val KIND_USER = "user"
        const val KIND_MEDICINES = "medicines"
        const val KIND_DATES = "dates"
    }
}

/** Un elenco con il numero di attività ancora da fare. */
data class TaskListSummary(val list: TaskList, val open: Int)

/** Passi di un'attività: quanti in tutto e quanti fatti. */
data class StepProgress(val done: Int, val total: Int)
