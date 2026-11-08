package eu.eurise.core.data.model

/**
 * Riferimento universale a una cosa della suite, per i collegamenti: tipo più identificativo nel suo archivio.
 * Note e attività usano l'id del database dell'app, gli eventi l'id del calendario di sistema, i contatti la
 * chiave di ricerca di Android (stabile anche se il contatto viene unito o sincronizzato).
 */
data class LinkRef(val kind: Kind, val key: String) : Comparable<LinkRef> {

    enum class Kind { Note, Task, Event, Contact }

    /** Forma testuale stabile, per salvare e confrontare: "Note:12". */
    override fun toString(): String = "${kind.name}:$key"

    override fun compareTo(other: LinkRef): Int = toString().compareTo(other.toString())

    companion object {
        fun note(id: Long) = LinkRef(Kind.Note, id.toString())
        fun task(id: Long) = LinkRef(Kind.Task, id.toString())
        fun event(id: Long) = LinkRef(Kind.Event, id.toString())
        fun contact(lookupKey: String) = LinkRef(Kind.Contact, lookupKey)

        /** Legge la forma testuale; nullo se il tipo non è noto o la chiave è vuota. */
        fun parse(text: String): LinkRef? {
            val i = text.indexOf(':')
            if (i <= 0 || i == text.lastIndex) return null
            val kind = Kind.entries.firstOrNull { it.name == text.substring(0, i) } ?: return null
            return LinkRef(kind, text.substring(i + 1))
        }
    }
}

/**
 * Un collegamento come lo vede una delle due cose: l'altra cosa e se il legame è stato scelto a mano
 * ([removable]) o nasce da sé (per esempio una voce di un elenco di controllo diventata attività).
 */
data class Link(val other: LinkRef, val removable: Boolean, val createdAt: Long)
