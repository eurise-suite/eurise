package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modalità semplice (tavola ChatSemplice), senza Android: si prova con i test.
 *
 * Per chi fa fatica con i telefoni: al posto dell'elenco, poche persone grandi (al massimo [MAX]), «Manda un vocale» e
 * il tasto rosso «Aiuto», che scrive a una persona scelta e le fa vedere dove sei per un'ora. Si accende e si toglie dal
 * foglio dell'account; resta scritto sempre in fondo che è accesa e come toglierla.
 */
object Semplice {
    const val MAX = 4

    data class Config(val on: Boolean = false, val rooms: List<String> = emptyList(), val help: String? = null, val name: String = "")

    fun encode(c: Config): String = JSONObject().put("on", c.on).put("rooms", JSONArray(c.rooms)).put("help", c.help ?: "").put("name", c.name).toString()

    fun decode(s: String?): Config = runCatching {
        val o = JSONObject(s ?: return Config())
        val r = o.optJSONArray("rooms") ?: JSONArray()
        val rooms = (0 until r.length()).map { r.optString(it) }.filter { it.isNotEmpty() }.distinct().take(MAX)
        Config(o.optBoolean("on"), rooms, o.optString("help").ifEmpty { null }?.takeIf { it in rooms }, o.optString("name"))
    }.getOrDefault(Config())

    /** Mette o toglie una chat, fino a [MAX]; se si toglie la persona dell'aiuto, l'aiuto passa alla prima rimasta. */
    fun toggle(c: Config, roomId: String): Config {
        val rooms = if (roomId in c.rooms) c.rooms - roomId else if (c.rooms.size < MAX) c.rooms + roomId else c.rooms
        val help = c.help?.takeIf { it in rooms } ?: rooms.firstOrNull()
        return c.copy(rooms = rooms, help = help)
    }

    /** Le chat da mostrare, nell'ordine scelto, solo quelle che ci sono ancora. */
    fun shown(c: Config, rooms: List<Room>): List<Room> = c.rooms.mapNotNull { id -> rooms.firstOrNull { it.id == id } }

    /** Il nome per «Ciao Bruna»: quello scritto, se no il nome dell'account con l'iniziale maiuscola. */
    fun greetingName(c: Config, userId: String): String =
        c.name.trim().ifEmpty { Matrix.localpart(userId).substringBefore('.').replaceFirstChar { it.uppercase() } }
}
