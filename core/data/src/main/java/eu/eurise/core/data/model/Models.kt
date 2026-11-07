package eu.eurise.core.data.model

import java.time.LocalDate

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
 * Una cosa da fare, con scadenza facoltativa (solo giorno, senza ora).
 *
 * @property noteId nota da cui nasce l'attività (una voce di un elenco di controllo), se c'è.
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
)
