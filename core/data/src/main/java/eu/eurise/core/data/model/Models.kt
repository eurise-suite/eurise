package eu.eurise.core.data.model

import java.time.LocalDate

/** Una nota. Per ora il corpo è testo semplice; il formato HTML5 + SVG arriva con l'editor ricco. */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Una cosa da fare, con scadenza facoltativa (solo giorno, senza ora). */
data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val due: LocalDate?,
    val done: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
)
