package eu.eurise.core.data.model

/**
 * Come va una cosa affidata (tavola CollAffida): affidata, vista, iniziata, fatta. Corrisponde al PARTSTAT di chi la
 * riceve in iCalendar, così anche le altre app di attività capiscono lo stato.
 */
enum class Handoff(val partstat: String) {
    Assigned("NEEDS-ACTION"),
    Seen("ACCEPTED"),
    Started("IN-PROCESS"),
    Done("COMPLETED");

    companion object {
        fun of(partstat: String?): Handoff? = entries.firstOrNull { it.partstat.equals(partstat?.trim(), ignoreCase = true) }
    }
}

/**
 * La parte condivisa di un'attività (pagina Collaborazione della tela, 17 novembre 2026):
 * - negli elenchi condivisi chi l'ha presa ([takenBy], [takenAt]) e chi l'ha fatta ([doneBy]);
 * - in «Affida a…» a chi è affidata ([assignee], [assigneeEmail]), da chi ([owner], [ownerEmail]), con la riga per lei
 *   ([message]), se avvisare chi l'ha affidata ([notify]) e i momenti dei passi ([assignedAt], [seenAt], [startedAt]).
 * I momenti sono in millisecondi arrotondati al secondo, come li porta il server.
 */
data class TaskCollab(
    val takenBy: String? = null,
    val takenAt: Long? = null,
    val doneBy: String? = null,
    val assignee: String? = null,
    val assigneeEmail: String? = null,
    val handoff: Handoff? = null,
    val assignedAt: Long? = null,
    val seenAt: Long? = null,
    val startedAt: Long? = null,
    val message: String? = null,
    val owner: String? = null,
    val ownerEmail: String? = null,
    val notify: Boolean = false,
) {
    val isEmpty: Boolean get() = this == TaskCollab()

    /** Vero se è affidata a qualcuno. */
    val assigned: Boolean get() = assigneeEmail != null

    /** Il passo raggiunto, tenendo conto di un'attività spuntata. */
    fun step(done: Boolean): Handoff? = if (!assigned) null else if (done) Handoff.Done else handoff ?: Handoff.Assigned

    /** Come la scrive e la rilegge il server: il passo segue la spunta, «fatta da» c'è solo se è fatta. */
    fun normalized(done: Boolean): TaskCollab = copy(handoff = step(done), doneBy = doneBy?.takeIf { done })

    /** Il testo da salvare nel database; null se non c'è nulla. */
    fun encode(): String? {
        if (isEmpty) return null
        return buildList {
            fun put(key: String, value: Any?) { if (value != null) add(key + "=" + value.toString().replace("\\", "\\\\").replace("\n", "\\n")) }
            put("takenBy", takenBy); put("takenAt", takenAt); put("doneBy", doneBy)
            put("assignee", assignee); put("assigneeEmail", assigneeEmail); put("handoff", handoff?.name)
            put("assignedAt", assignedAt); put("seenAt", seenAt); put("startedAt", startedAt)
            put("message", message); put("owner", owner); put("ownerEmail", ownerEmail)
            if (notify) put("notify", 1)
        }.joinToString("\n")
    }

    companion object {
        /** Il momento [millis] arrotondato al secondo, come lo restituisce il server. */
        fun stamp(millis: Long): Long = millis / 1000 * 1000

        fun decode(raw: String?): TaskCollab {
            if (raw.isNullOrBlank()) return TaskCollab()
            val map = raw.split('\n').mapNotNull { line ->
                val key = line.substringBefore('=', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val value = StringBuilder()
                val text = line.substringAfter('=')
                var i = 0
                while (i < text.length) {
                    val c = text[i]
                    if (c == '\\' && i + 1 < text.length) { value.append(if (text[i + 1] == 'n') '\n' else text[i + 1]); i += 2 } else { value.append(c); i++ }
                }
                key to value.toString()
            }.toMap()
            return TaskCollab(
                takenBy = map["takenBy"], takenAt = map["takenAt"]?.toLongOrNull(), doneBy = map["doneBy"],
                assignee = map["assignee"], assigneeEmail = map["assigneeEmail"],
                handoff = map["handoff"]?.let { name -> Handoff.entries.firstOrNull { it.name == name } },
                assignedAt = map["assignedAt"]?.toLongOrNull(), seenAt = map["seenAt"]?.toLongOrNull(), startedAt = map["startedAt"]?.toLongOrNull(),
                message = map["message"], owner = map["owner"], ownerEmail = map["ownerEmail"], notify = map["notify"] == "1",
            )
        }

        /**
         * Unisce a gruppi: chi l'ha presa, chi l'ha fatta, l'affidamento (persona, riga, avviso) e il passo raggiunto.
         * Ciò che è cambiato da una parte sola passa; se è cambiato da tutte e due vale il telefono, tranne il passo,
         * che va sempre avanti (vista, iniziata, fatta non tornano indietro da sole).
         */
        fun merge(base: TaskCollab, local: TaskCollab, remote: TaskCollab): TaskCollab {
            fun <T> pick(b: T, l: T, r: T) = if (l == b) r else l
            val taken = pick(base.takenBy to base.takenAt, local.takenBy to local.takenAt, remote.takenBy to remote.takenAt)
            val who = { c: TaskCollab -> listOf(c.assignee, c.assigneeEmail, c.message, c.owner, c.ownerEmail, c.notify, c.assignedAt) }
            val assignment = pick(who(base), who(local), who(remote))
            val from = if (assignment == who(local)) local else remote
            val sameAssignee = local.assigneeEmail == remote.assigneeEmail
            val stepOf = { c: TaskCollab -> Triple(c.handoff, c.seenAt, c.startedAt) }
            val step = when {
                !sameAssignee -> stepOf(from)
                (local.handoff?.ordinal ?: -1) >= (remote.handoff?.ordinal ?: -1) -> Triple(local.handoff, local.seenAt ?: remote.seenAt, local.startedAt ?: remote.startedAt)
                else -> Triple(remote.handoff, remote.seenAt ?: local.seenAt, remote.startedAt ?: local.startedAt)
            }
            return TaskCollab(
                takenBy = taken.first, takenAt = taken.second,
                doneBy = pick(base.doneBy, local.doneBy, remote.doneBy),
                assignee = from.assignee, assigneeEmail = from.assigneeEmail, message = from.message,
                owner = from.owner, ownerEmail = from.ownerEmail, notify = from.notify, assignedAt = from.assignedAt,
                handoff = step.first, seenAt = step.second, startedAt = step.third,
            )
        }
    }
}
