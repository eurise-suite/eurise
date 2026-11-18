package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/** Una stanza (chat a due o gruppo) come la mostra l'elenco. */
data class Room(
    val id: String,
    val name: String,
    val lastText: String,
    val lastTime: Long,
    val unread: Int,
    val encrypted: Boolean,
    val invite: Boolean = false,
    val direct: Boolean = false,
    /** Per caricare i messaggi più vecchi (/messages). */
    val prevBatch: String? = null,
)

/** Un messaggio nella conversazione. [encrypted]: cifrato end-to-end, non ancora leggibile da questa versione. */
data class Event(
    val id: String,
    val sender: String,
    val text: String,
    val time: Long,
    val kind: Kind,
    val mediaUrl: String? = null,
    /** Per spese e pagamenti del gruppo: il contenuto del messaggio (JSON), letto da [Expenses]. */
    val data: String? = null,
    /** Il messaggio a cui risponde (tavola ChatBasi). */
    val replyTo: String? = null,
) {
    enum class Kind { Text, Notice, Image, File, Encrypted, State, Expense, Settlement, Bussola, Shift, Poll }
}

/**
 * Lettura delle risposte del server Matrix (API client-server, https://spec.matrix.org), senza Android a parte org.json:
 * si prova con i test. Solo ciò che serve alla chat: stanze, messaggi, nomi.
 */
object Matrix {
    private val MXID = Regex("^@[a-z0-9._=\\-/+]+:[a-zA-Z0-9.\\-]+(:\\d+)?$")

    /** «@anna:studio.it», anche scritto «anna:studio.it» o «@Anna:studio.it ». */
    fun normalizeId(input: String): String? {
        val t = input.trim().lowercase().let { if (it.startsWith("@")) it else "@$it" }
        return t.takeIf { MXID.matches(it) }
    }

    /** Il nome da mostrare per un utente senza nome: «anna» da «@anna:studio.it». */
    fun localpart(userId: String) = userId.removePrefix("@").substringBefore(':')

    /** L'indirizzo del server da /.well-known/matrix/client, o null. */
    fun wellKnown(json: String): String? = runCatching {
        JSONObject(json).getJSONObject("m.homeserver").getString("base_url").trimEnd('/')
    }.getOrNull()

    /** I modi di accedere che il server offre (/login): «m.login.sso», «m.login.password»… */
    fun loginFlows(json: String): List<String> = runCatching {
        val a = JSONObject(json).getJSONArray("flows")
        (0 until a.length()).map { a.getJSONObject(it).getString("type") }
    }.getOrDefault(emptyList())

    /** Un evento della cronologia come messaggio da mostrare, o null se non si mostra. */
    fun event(o: JSONObject, names: Map<String, String> = emptyMap()): Event? {
        val id = o.optString("event_id").ifEmpty { return null }
        val sender = o.optString("sender")
        val time = o.optLong("origin_server_ts")
        val content = o.optJSONObject("content") ?: JSONObject()
        if (o.has("redacted_because") || (o.optJSONObject("unsigned")?.has("redacted_because") == true)) return null
        val type = o.optString("type")
        // Un sondaggio (tavola ChatBasi): domanda e risposte nel contenuto, i voti arrivano come riferimenti (Relations).
        if (type in Relations.POLL_START) {
            val p = Relations.poll(content) ?: return null
            return Event(id, sender, p.question, time, Event.Kind.Poll, data = content.toString())
        }
        val reply = Relations.replyTo(content)
        return when (type) {
            "m.room.encrypted" -> Event(id, sender, "", time, Event.Kind.Encrypted)
            "m.room.message" -> {
                val body = if (reply != null) Relations.stripFallback(content.optString("body")) else content.optString("body")
                // Un file: «url» in chiaro, o dentro «file» (stanze cifrate: chiave e impronta, vedi Attachments).
                val media = content.optString("url").ifEmpty { content.optJSONObject("file")?.optString("url").orEmpty() }.ifEmpty { null }
                // Con la didascalia (Matrix 1.10) il nome del file sta in «filename» e «body» è il testo.
                val name = content.optString("filename").ifEmpty { body }
                // La mia Bussola: rinnovi, stati, inviti, orari, terapia e referti (data = il contenuto intero).
                if (eu.eurise.core.studio.Bussola.isBussola(content.optString("msgtype")) || eu.eurise.core.studio.Bussola.isReport(content)) {
                    return Event(id, sender, body, time, Event.Kind.Bussola, media, data = content.toString())
                }
                when (content.optString("msgtype")) {
                    "m.image" -> Event(id, sender, name, time, Event.Kind.Image, media, data = content.toString(), replyTo = reply)
                    "m.file", "m.video", "m.audio" -> Event(id, sender, name, time, Event.Kind.File, media, data = content.toString())
                    "m.notice" -> Event(id, sender, body, time, Event.Kind.Notice, replyTo = reply)
                    Expenses.EXPENSE -> Event(id, sender, body, time, Event.Kind.Expense, data = content.toString())
                    Expenses.SETTLEMENT -> Event(id, sender, body, time, Event.Kind.Settlement, data = content.toString())
                    // Turni del gruppo: piano, disponibilità, cambi e risposte (vedi Shifts).
                    in Shifts.TYPES -> Event(id, sender, body, time, Event.Kind.Shift, data = content.toString())
                    // La richiesta di verifica di una persona: una riga al centro, non un messaggio.
                    "m.key.verification.request" -> Event(id, sender, "verify:" + (names[sender] ?: localpart(sender)), time, Event.Kind.State)
                    "m.emote" -> Event(id, sender, "* " + (names[sender] ?: localpart(sender)) + " " + body, time, Event.Kind.Text)
                    else -> if (content.has("m.new_content")) null else Event(id, sender, body, time, Event.Kind.Text, replyTo = reply)
                }
            }
            "m.room.member" -> {
                val who = content.optString("displayname").ifEmpty { localpart(o.optString("state_key")) }
                val what = when (content.optString("membership")) {
                    "join" -> "join"
                    "leave" -> "leave"
                    "invite" -> "invite"
                    else -> return null
                }
                Event(id, sender, "$what:$who", time, Event.Kind.State)
            }
            else -> null
        }
    }

    /** I messaggi di un elenco di eventi (timeline di /sync o «chunk» di /messages). */
    fun events(a: JSONArray?, names: Map<String, String> = emptyMap()): List<Event> {
        a ?: return emptyList()
        return (0 until a.length()).mapNotNull { event(a.getJSONObject(it), names) }
    }

    /**
     * Il nome di una stanza: m.room.name, altrimenti l'alias, altrimenti i nomi delle persone («heroes»), come chiede la
     * specifica. [me] escluso dai nomi.
     */
    fun roomName(state: Map<String, JSONObject>, heroes: List<String>, names: Map<String, String>, me: String): String {
        state["m.room.name"]?.optString("name")?.takeIf { it.isNotBlank() }?.let { return it }
        state["m.room.canonical_alias"]?.optString("alias")?.takeIf { it.isNotBlank() }?.let { return it }
        val others = heroes.filter { it != me }.map { names[it] ?: localpart(it) }
        return when {
            others.isEmpty() -> "—"
            others.size <= 3 -> others.joinToString(", ")
            else -> others.take(3).joinToString(", ") + " +" + (others.size - 3)
        }
    }

    /** Da «mxc://server/id» all'indirizzo per scaricarlo (media autenticati, Matrix 1.11). */
    fun mediaUrl(homeserver: String, mxc: String, thumbnail: Boolean): String? {
        if (!mxc.startsWith("mxc://")) return null
        val path = mxc.removePrefix("mxc://")
        return if (thumbnail) "$homeserver/_matrix/client/v1/media/thumbnail/$path?width=640&height=640&method=scale"
        else "$homeserver/_matrix/client/v1/media/download/$path"
    }
}
