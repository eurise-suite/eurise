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
    )

    private suspend fun send(cal: WebDav, path: String, ics: String, etag: String?, new: Boolean): String? {
        val tag = cal.put(path, ics, CONTENT_TYPE, ifMatch = etag.takeIf { !new }, onlyIfNew = new)
        return tag ?: cal.stat(path)?.etag
    }

    override suspend fun run(context: Context, cloud: CloudSync): List<String> {
        val prefs = cloud.settings.value
        if (!prefs.tasksEnabled) return emptyList()
        val cal = cloud.accounts.dav(prefs.notesAccount)?.calendars() ?: return emptyList()
        val store = context.getSharedPreferences("tasks_sync", Context.MODE_PRIVATE)

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

        var known: List<eu.eurise.core.cloud.DavCalendar>? = null
        var sent = 0
        var received = 0
        for (list in tasks.listsToSync()) {
            val slug = store.getString("list_${list.id}", null) ?: (
                (known ?: cal.listCalendars().also { known = it }).firstOrNull { it.todo && it.name.equals(list.name, ignoreCase = true) }?.slug
                    ?: "eurise-${list.id}".also { cal.mkCalendar(it, list.name) }
                ).also { store.edit().putString("list_${list.id}", it).apply() }
            val (out, got) = syncList(cal, slug, list)
            sent += out
            received += got
        }
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
                if (e.code != 412) throw e // cambiata nel frattempo: si riprova al prossimo giro.
            }
        }

        val known = assigned.map { it.second }.toSet()
        val fresh = ArrayList<Pair<Long, String?>>()
        for ((name, entry) in remote) {
            if (name in known) continue
            val (text, tag) = cal.get(path(slug, name)) ?: continue
            val parsed = Vtodo.parse(text) ?: continue // gli eventi non sono attività
            val f = parsed.fields
            val task = Task(0, f.summary, f.description, f.due, f.done, f.completedAt, now, listId = list.id, time = f.time, repeat = f.rrule, priority = f.priority, uid = name, remoteEtag = tag ?: entry.etag, syncBase = text)
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
        priority = f.priority, repeat = f.rrule, parentId = parent ?: parentId,
    )

    private companion object {
        const val EXT = ".ics"
        const val CONTENT_TYPE = "text/calendar; charset=utf-8"
    }
}
