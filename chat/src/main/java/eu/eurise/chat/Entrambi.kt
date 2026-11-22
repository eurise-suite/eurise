package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * «Entrambi» (tavola ChatAccountAlternative, scelta C del 22 novembre 2026): Personale e Lavoro in un elenco solo, con il
 * filo del colore dell'account. Resta aperto un account alla volta (chiavi e sincronizzazione di uno solo, come prima):
 * dell'altro si legge solo un riassunto leggero, nome, ora, non lette e l'anteprima se la stanza non è cifrata. Toccando
 * una sua chat si passa a quell'account.
 */
internal object Entrambi {
    /** Che cosa mostra l'elenco: l'account aperto, oppure tutti e due. Si sceglie dalla pillola in alto a destra. */
    enum class Show { One, Both }

    /** Una stanza dell'altro account nel riassunto: [lastKind] null = nessun messaggio letto. */
    data class Other(val room: Room, val lastKind: Event.Kind?)

    /**
     * Il filtro della /sync del riassunto: un messaggio per stanza, nome, membri essenziali e cifratura; niente presenze né
     * dati dell'account oltre a m.direct. Senza «since»: non sposta il punto di sincronizzazione dell'altro account.
     */
    const val FILTER = """{"room":{"timeline":{"limit":1},"state":{"types":["m.room.name","m.room.canonical_alias","m.room.encryption","m.room.member"],"lazy_load_members":true},"ephemeral":{"types":[]},"account_data":{"types":[]}},""" +
        """"presence":{"not_types":["*"]},"account_data":{"types":["m.direct"]}}"""

    /** Le stanze in cui l'altro account è entrato, dalla più recente; gli inviti restano nell'account. */
    fun parse(json: JSONObject, me: String): List<Other> {
        val direct = HashSet<String>()
        json.optJSONObject("account_data")?.optJSONArray("events")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                if (e.optString("type") != "m.direct") continue
                val c = e.optJSONObject("content") ?: continue
                c.keys().forEach { user -> c.optJSONArray(user)?.let { ids -> for (j in 0 until ids.length()) direct += ids.getString(j) } }
            }
        }
        val join = json.optJSONObject("rooms")?.optJSONObject("join") ?: return emptyList()
        return join.keys().asSequence().map { id ->
            val r = join.getJSONObject(id)
            val state = HashMap<String, JSONObject>()
            val names = HashMap<String, String>()
            var encrypted = false
            val heroes = r.optJSONObject("summary")?.optJSONArray("m.heroes")?.let { h -> (0 until h.length()).map { h.getString(it) } }.orEmpty()
            val tl = r.optJSONObject("timeline")?.optJSONArray("events")
            listOfNotNull(r.optJSONObject("state")?.optJSONArray("events"), tl).forEach { a -> stateFrom(a, state, names) { encrypted = true } }
            val last = Matrix.events(tl, names).lastOrNull { it.kind != Event.Kind.State && it.kind != Event.Kind.Beacon }
            val text = when (last?.kind) {
                null, Event.Kind.Encrypted -> ""
                Event.Kind.Text, Event.Kind.Notice -> last.text
                else -> ""
            }
            Other(
                Room(
                    id = id, name = Matrix.roomName(state, heroes.ifEmpty { names.keys.toList() }, names, me), lastText = text,
                    lastTime = last?.time ?: 0L, unread = r.optJSONObject("unread_notifications")?.optInt("notification_count") ?: 0,
                    encrypted = encrypted, direct = id in direct,
                ),
                last?.kind,
            )
        }.sortedByDescending { it.room.lastTime }.toList()
    }

    private fun stateFrom(a: JSONArray, state: MutableMap<String, JSONObject>, names: MutableMap<String, String>, onEncrypted: () -> Unit) {
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            if (!e.has("state_key")) continue
            val content = e.optJSONObject("content") ?: JSONObject()
            when (val type = e.optString("type")) {
                "m.room.member" -> content.optString("displayname").takeIf { it.isNotBlank() }?.let { names[e.optString("state_key")] = it }
                "m.room.encryption" -> onEncrypted()
                else -> if (e.optString("state_key").isEmpty()) state[type] = content
            }
        }
    }

    /** Una riga dell'elenco insieme: una stanza dell'account aperto o dell'altro, con l'account per il colore. */
    data class Row(val room: Room, val account: Account, val mine: Boolean, val lastKind: Event.Kind? = null)

    /**
     * L'elenco insieme, dal più recente: le stanze dell'account aperto e quelle dell'altro. Con [hideWork] (fuori orario e
     * «il lavoro tace» acceso) le stanze del Lavoro si fanno da parte.
     */
    fun merge(mine: List<Room>, active: Account, other: List<Other>, hideWork: Boolean): List<Row> {
        val rows = mine.map { Row(it, active, true) } + other.map { Row(it.room, active.other, false, it.lastKind) }
        return rows.filter { !(hideWork && it.account == Account.Work) }
            .sortedWith(compareByDescending<Row> { it.room.invite }.thenByDescending { it.room.lastTime })
    }
}
