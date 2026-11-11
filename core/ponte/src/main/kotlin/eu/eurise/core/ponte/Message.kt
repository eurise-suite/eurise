package eu.eurise.core.ponte

/**
 * Ciò che passa sul Ponte (tavola Ponte): ogni tipo si accende e si spegne a parte. Il corpo è testo; i file viaggiano
 * a pezzi con lo stesso [id].
 */
enum class Kind(val wire: String) {
    /** Scheda o documento aperto: «Continua qui» sull'altro lato. */
    Handoff("handoff"),
    /** Appunti copiati sul telefono, da incollare sul computer (e viceversa). */
    Clipboard("clip"),
    /** Testo dettato al telefono, da scrivere dove si trova il cursore sul computer. */
    Dictation("dict"),
    /** «Ti hanno cercato»: chiamata o messaggio arrivato sul telefono. */
    Notify("notify"),
    /** Risposta da mandare dal telefono a un messaggio arrivato. */
    Reply("reply"),
    /** Il computer chiede una password per un sito; il telefono approva con l'impronta. */
    PasswordAsk("pwd-ask"),
    PasswordGive("pwd-give"),
    /** Un pezzo di file. */
    File("file"),
    /** Sono qui (rete locale o cloud), con le capacità di chi scrive. */
    Hello("hello"),
    /** Una nota mandata dal computer (o dal telefono): finisce in Eurise Note. */
    Note("note"),
    /** Una cosa da fare, detta come si parla («pagare l'affitto venerdì»): finisce in Eurise Attività. */
    Task("task"),
    /** Il collegamento fisso è ancora vivo; non porta niente. Chi non lo conosce lo scarta. */
    Ping("ping"),
    ;

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire }
    }
}

/** Un messaggio del Ponte prima della cifratura. [fields] contiene solo testo (titolo, url, app, persona…). */
data class Message(val kind: Kind, val id: String, val time: Long, val fields: Map<String, String> = emptyMap()) {
    operator fun get(key: String) = fields[key]

    fun encode(): String = Json.write(mapOf("t" to kind.wire, "id" to id, "at" to time.toString()) + fields.mapKeys { "f." + it.key })

    companion object {
        fun decode(text: String): Message? {
            val m = Json.read(text) ?: return null
            val kind = Kind.of(m["t"] ?: return null) ?: return null
            val id = m["id"] ?: return null
            val time = m["at"]?.toLongOrNull() ?: return null
            return Message(kind, id, time, m.filterKeys { it.startsWith("f.") }.mapKeys { it.key.removePrefix("f.") })
        }
    }
}

/**
 * Niente messaggi ripetuti: chi li registrasse e li rimandasse viene scartato. Si accettano solo messaggi di meno di
 * [windowMs] (orologi non perfetti compresi) e mai due volte lo stesso id.
 */
class ReplayGuard(private val windowMs: Long = 10 * 60_000L, private val limit: Int = 2_000) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun accept(m: Message, now: Long): Boolean {
        if (m.time < now - windowMs || m.time > now + windowMs) return false
        if (seen.containsKey(m.id)) return false
        seen[m.id] = m.time
        val it = seen.entries.iterator()
        while (seen.size > limit && it.hasNext()) { it.next(); it.remove() }
        return true
    }
}

/** JSON piatto (solo stringhe) per i messaggi: niente librerie, uguale su telefono e computer. */
object Json {
    fun write(map: Map<String, String>): String = map.entries.joinToString(",", "{", "}") { (k, v) -> quote(k) + ":" + quote(v) }

    private fun quote(s: String) = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch < ' ' -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
        append('"')
    }

    fun read(text: String): Map<String, String>? {
        var i = 0
        val s = text.trim()
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun str(): String? {
            if (i >= s.length || s[i] != '"') return null
            i++
            val b = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (i >= s.length) return null
                        when (val e = s[i++]) {
                            'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                            'u' -> { if (i + 4 > s.length) return null; b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: return null); i += 4 }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
            return null
        }
        ws(); if (i >= s.length || s[i] != '{') return null; i++
        val out = LinkedHashMap<String, String>()
        ws()
        if (i < s.length && s[i] == '}') return out
        while (i < s.length) {
            ws(); val k = str() ?: return null
            ws(); if (i >= s.length || s[i] != ':') return null; i++
            ws(); val v = str() ?: return null
            out[k] = v
            ws()
            when {
                i < s.length && s[i] == ',' -> i++
                i < s.length && s[i] == '}' -> { i++; ws(); return if (i == s.length) out else null }
                else -> return null
            }
        }
        return null
    }
}
