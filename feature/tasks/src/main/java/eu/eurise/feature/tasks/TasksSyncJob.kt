package eu.eurise.feature.tasks

import android.content.Context
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.cloud.DavEntry
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.SyncJob
import eu.eurise.core.cloud.WebDav
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.data.model.Vtodo
import eu.eurise.core.data.model.VtodoFields
import java.util.UUID

/**
 * Sincronizza con CalDAV gli elenchi con "Sincronizza" acceso: un elenco del telefono è un elenco di attività del server. Ogni
 * scrittura è condizionata all'etag letto. Se la stessa attività è cambiata da due parti si uniscono i campi; l'avviso resta sul telefono.
 */
class TasksSyncJob(private val tasks: TaskRepository) : SyncJob {

    private fun path(slug: String, name: String) = "$slug/$name.ics"
    private fun same(a: String?, b: String?) = a?.removePrefix("W/")?.trim('"') == b?.removePrefix("W/")?.trim('"')
    private fun fields(t: Task, parent: String?) = VtodoFields(
        t.title, t.notes, t.due, t.time, t.done, t.completedAt?.let { it / 1000 * 1000 }, t.priority, t.repeat, parent,
        t.collab.normalized(t.done),
    )

    /** Chi sono sul server (per «Affida a…»): nome ed email, letti a ogni giro e tenuti per l'interfaccia. */
    private var me: Pair<String, String?>? = null
    private lateinit var ctx: Context
    private val notes = ArrayList<Pair<String, String>>()

    private suspend fun send(cal: WebDav, path: String, ics: String, etag: String?, new: Boolean): String? {
        val tag = cal.put(path, ics, CONTENT_TYPE, ifMatch = etag.takeIf { !new }, onlyIfNew = new)
        return tag ?: cal.stat(path)?.etag
    }

    override suspend fun run(context: Context, cloud: CloudSync): List<String> {
        val prefs = cloud.settings.value
        if (!prefs.tasksEnabled) return emptyList()
        val cal = cloud.accounts.dav(prefs.notesAccount)?.calendars() ?: return emptyList()
        val store = context.getSharedPreferences("tasks_sync", Context.MODE_PRIVATE)
        ctx = context
        me = cal.me()?.also { (name, email) -> store.edit().putString(Collab.ME_NAME, name).putString(Collab.ME_EMAIL, email).apply() }
            ?: Collab.me(context)
        notes.clear()

        for (tomb in tasks.tombstones()) {
            store.getString("list_${tomb.listId}", null)?.let { slug ->
                try {
                    cal.delete(path(slug, tomb.uid), ifMatch = tomb.etag)
                } catch (e: DavException) {
                    if (e.code != 412) throw e // cambiata altrove nel frattempo: resta sul server.
                }
            }
            tasks.removeTombstone(tomb.uid)
        }

        val known = cal.listCalendars()
        // Elenchi che un'altra persona ha condiviso con te (tavola CollElenchi): diventano elenchi del telefono.
        val mapped = store.all.filterKeys { it.startsWith("list_") }.values.filterIsInstance<String>().toSet()
        known.filter { it.todo && it.ownerName != null && it.slug !in mapped }.forEach { c ->
            val id = tasks.addSharedList(c.name.ifBlank { c.slug }, c.ownerName)
            store.edit().putString("list_$id", c.slug).apply()
        }
        var sent = 0
        var received = 0
        for (list in tasks.listsToSync()) {
            val slug = store.getString("list_${list.id}", null) ?: (
                known.firstOrNull { it.todo && it.ownerName == null && it.name.equals(list.name, ignoreCase = true) }?.slug
                    ?: "eurise-${list.id}".also { cal.mkCalendar(it, list.name) }
                ).also { store.edit().putString("list_${list.id}", it).apply() }
            known.firstOrNull { it.slug == slug }?.let { c -> tasks.setListPeople(list.id, c.ownerName, c.members.map { it.name }) }
            Collab.flushShares(cal, store, list.id, slug)
            val (out, got) = syncList(cal, slug, list)
            sent += out
            received += got
        }
        notes.forEach { Collab.notify(context, it) }
        lateCheck(context, store)
        return if (sent + received > 0) listOf(context.getString(R.string.tasks_sync_done, sent, received)) else emptyList()
    }

    private suspend fun syncList(cal: WebDav, slug: String, list: TaskList): Pair<Int, Int> {
        val remote: Map<String, DavEntry> = cal.list(slug).filter { !it.folder && it.name.endsWith(EXT) }.associateBy { it.name.removeSuffix(EXT) }
        val now = System.currentTimeMillis()
        val local = tasks.forSync(list.id).sortedBy { it.parentId != null }
        val icsUid = HashMap<Long, String>()
        val assigned = local.map { t ->
            val uid = t.uid ?: UUID.randomUUID().toString()
            icsUid[t.id] = t.syncBase?.let { Vtodo.parse(it)?.uid } ?: uid
            t to uid
        }
        val byIcs = HashMap<String, Long>().apply { icsUid.forEach { (id, uid) -> put(uid, id) } }
        val gone = HashSet<Long>()
        var sent = 0
        var received = 0

        for ((task, uid) in assigned) {
            if (task.id in gone || task.parentId in gone) continue
            val mine = fields(task, task.parentId?.let(icsUid::get))
            val base = task.syncBase?.let { Vtodo.parse(it)?.fields }
            val entry = remote[uid]
            val target = path(slug, uid)
            try {
                when {
                    task.uid == null || (entry == null && base != null && mine != base) -> {
                        val ics = Vtodo.render(task.syncBase, icsUid.getValue(task.id), mine, now)
                        tasks.markSynced(task.id, uid, send(cal, target, ics, null, new = true), ics)
                        sent++
                    }
                    entry == null -> { tasks.deleteSynced(task.id); gone += task.id; received++ }
                    same(entry.etag, task.remoteEtag) -> if (mine != base) {
                        val ics = Vtodo.render(task.syncBase, icsUid.getValue(task.id), mine, now)
                        tasks.markSynced(task.id, uid, send(cal, target, ics, task.remoteEtag, new = false), ics)
                        sent++
                    }
                    else -> {
                        val (text, tag) = cal.get(target) ?: continue
                        val theirs = Vtodo.parse(text)?.fields ?: continue
                        val merged = Vtodo.merge(base, mine, theirs)
                        noteChange(task, merged)
                        val parent = merged.parentUid?.let(byIcs::get)
                        tasks.applySynced(task.apply(merged, parent), uid, tag ?: entry.etag, text)
                        received++
                        if (merged != theirs) {
                            val ics = Vtodo.render(text, icsUid.getValue(task.id), merged, now)
                            tasks.markSynced(task.id, uid, send(cal, target, ics, tag ?: entry.etag, new = false), ics)
                            sent++
                        }
                    }
                }
            } catch (e: DavException) {
                // 412: cambiata nel frattempo, si riprova al prossimo giro. 403: elenco condiviso in sola lettura.
                if (e.code != 412 && e.code != 403) throw e
            }
        }

        val known = assigned.map { it.second }.toSet()
        val fresh = ArrayList<Pair<Long, String?>>()
        for ((name, entry) in remote) {
            if (name in known) continue
            val (text, tag) = cal.get(path(slug, name)) ?: continue
            val parsed = Vtodo.parse(text) ?: continue // gli eventi non sono attività
            val f = parsed.fields
            val task = Task(0, f.summary, f.description, f.due, f.done, f.completedAt, now, listId = list.id, time = f.time, repeat = f.rrule, priority = f.priority, uid = name, remoteEtag = tag ?: entry.etag, syncBase = text, collab = f.collab)
            if (f.collab.assigned && !f.done && mine(f.collab.assigneeEmail)) notes += Collab.assignedToYou(ctx, f.collab.owner, f.summary)
            val id = tasks.insertSynced(task)
            byIcs[parsed.uid] = id
            fresh += id to f.parentUid
            received++
        }
        fresh.forEach { (id, parent) -> parent?.let(byIcs::get)?.let { tasks.setParent(id, it) } }
        return sent to received
    }

    private fun Task.apply(f: VtodoFields, parent: Long?) = copy(
        title = f.summary, notes = f.description, due = f.due, time = f.time, done = f.done, completedAt = f.completedAt,
        priority = f.priority, repeat = f.rrule, parentId = parent ?: parentId, collab = f.collab,
    )

    private fun mine(email: String?) = email != null && me?.second?.equals(email, ignoreCase = true) == true

    /** Avvisi di «Affida a…»: a chi l'ha affidata quando è fatta (se l'ha chiesto), a chi la riceve quando arriva. */
    private fun noteChange(before: Task, after: VtodoFields) {
        val c = after.collab
        if (!c.assigned) return
        if (after.done && !before.done && c.notify && mine(c.ownerEmail)) notes += Collab.doneBy(ctx, c.assignee, after.summary)
        if (!after.done && before.collab.assigneeEmail != c.assigneeEmail && mine(c.assigneeEmail)) notes += Collab.assignedToYou(ctx, c.owner, after.summary)
    }

    /** «E se il giorno della scadenza alle 9 non è ancora partita»: un avviso solo, a chi l'ha affidata. */
    private suspend fun lateCheck(context: Context, store: android.content.SharedPreferences) {
        val now = java.time.LocalDateTime.now()
        if (now.hour < 9) return
        val warned = store.getStringSet("late", emptySet())!!.toMutableSet()
        for (list in tasks.listsToSync()) for (t in tasks.forSync(list.id)) {
            val c = t.collab
            val due = t.due ?: continue
            if (t.done || !c.assigned || !c.notify || !mine(c.ownerEmail) || due.isAfter(now.toLocalDate())) continue
            if ((c.step(false)?.ordinal ?: 0) >= eu.eurise.core.data.model.Handoff.Started.ordinal) continue
            val key = "${t.uid ?: t.id}@$due"
            if (warned.add(key)) Collab.notify(context, Collab.notStarted(context, c.assignee, t.title))
        }
        store.edit().putStringSet("late", warned).apply()
    }

    private companion object {
        const val EXT = ".ics"
        const val CONTENT_TYPE = "text/calendar; charset=utf-8"
    }
}
