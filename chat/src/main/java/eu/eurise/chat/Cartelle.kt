package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cartelle delle chat e «Messaggi a me» (tavola ChatCartelle), senza Android: si prova con i test.
 *
 * Le cartelle le sceglie l'utente (Famiglia, Scuola, Clienti…) e una chat può stare in più cartelle. Restano sul
 * telefono, per account: i nomi delle cartelle non vanno al server. «Messaggi a me» è una stanza con dentro solo
 * l'utente: il suo id sta nei dati dell'account Matrix ([SELF]) così un secondo telefono ritrova la stessa.
 */
object Cartelle {
    /** Dati dell'account con l'id della stanza «Messaggi a me»: `{"room_id": "!…"}`. */
    const val SELF = "eu.eurise.self_room"
    const val MAX_NAME = 24

    data class Folder(val id: String, val name: String, val rooms: Set<String> = emptySet())

    fun encode(list: List<Folder>): String = JSONArray().apply {
        list.forEach { f -> put(JSONObject().put("id", f.id).put("name", f.name).put("rooms", JSONArray(f.rooms.toList()))) }
    }.toString()

    fun decode(s: String?): List<Folder> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val r = o.optJSONArray("rooms")
            Folder(o.optString("id"), o.optString("name"), if (r == null) emptySet() else (0 until r.length()).map { r.optString(it) }.filter { it.isNotEmpty() }.toSet())
                .takeIf { it.id.isNotEmpty() && it.name.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    /** Il nome pulito, o null se vuoto o già usato (senza badare alle maiuscole). */
    fun cleanName(list: List<Folder>, name: String, except: String? = null): String? {
        val n = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)
        if (n.isEmpty()) return null
        if (list.any { it.id != except && it.name.equals(n, ignoreCase = true) }) return null
        return n
    }

    fun add(list: List<Folder>, name: String, id: String, rooms: Set<String> = emptySet()): List<Folder> {
        val n = cleanName(list, name) ?: return list
        return list + Folder(id, n, rooms)
    }

    fun rename(list: List<Folder>, id: String, name: String): List<Folder> {
        val n = cleanName(list, name, except = id) ?: return list
        return list.map { if (it.id == id) it.copy(name = n) else it }
    }

    fun remove(list: List<Folder>, id: String): List<Folder> = list.filterNot { it.id == id }

    /** Mette o toglie la chat [roomId] dalla cartella [id]. */
    fun toggle(list: List<Folder>, id: String, roomId: String): List<Folder> = list.map { f ->
        if (f.id != id) f else f.copy(rooms = if (roomId in f.rooms) f.rooms - roomId else f.rooms + roomId)
    }

    /** Le cartelle in cui sta una chat. */
    fun of(list: List<Folder>, roomId: String): List<Folder> = list.filter { roomId in it.rooms }

    /** Le chat da mostrare: tutte, o solo quelle della cartella; «Messaggi a me» ha il suo posto in cima e non si ripete. */
    fun shown(rooms: List<Room>, folder: Folder?, self: String?): List<Room> =
        rooms.filter { it.id != self && (folder == null || it.id in folder.rooms) }

    /** Quante chat della cartella hanno messaggi da leggere («Famiglia · 3»). */
    fun unread(rooms: List<Room>, folder: Folder): Int = rooms.count { it.id in folder.rooms && it.unread > 0 }

    /** Toglie dalle cartelle le chat che non ci sono più (uscite, cancellate). */
    fun prune(list: List<Folder>, existing: Set<String>): List<Folder> = list.map { f -> f.copy(rooms = f.rooms.filter { it in existing }.toSet()) }

    fun selfRoom(accountData: JSONObject?): String? = accountData?.optString("room_id")?.takeIf { it.startsWith("!") }
}
