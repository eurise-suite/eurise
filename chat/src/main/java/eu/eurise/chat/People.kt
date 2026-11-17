package eu.eurise.chat

/**
 * «Una persona, tutti i canali» (tavole UniPersone, UniConversazione, UniCanale): una riga per persona invece che per
 * app, e un filo solo con Chat, SMS, email e chiamate. Logica pura, senza Android: si prova con [PeopleTest].
 */
object People {
    enum class Channel { Chat, Sms, Email, Call }

    /**
     * Una cosa del filo. [incoming] da parte della persona; per le chiamate [duration] in secondi, [note] scritta durante
     * la chiamata, [missed] se persa; per le email [subject] e [ref] («casella:uid») per aprirla in Email.
     */
    data class Entry(
        val id: String,
        val channel: Channel,
        val time: Long,
        val incoming: Boolean,
        val text: String = "",
        val subject: String = "",
        val duration: Long = 0,
        val note: String = "",
        val missed: Boolean = false,
        val ref: String = "",
        val address: String = "",
    )

    /** Chi è: dalla Rubrica (con [key]) o, se non c'è, solo l'indirizzo da cui scrive. [roomId] è la chat a due. */
    data class Who(
        val key: String,
        val name: String,
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val matrix: List<String> = emptyList(),
        val roomId: String? = null,
        /** Vero se la persona è nella Rubrica (si può unire, chiamare per nome…). */
        val contact: Boolean = false,
    )

    /** Una riga della vista Persone: l'ultima cosa, quante da leggere, i canali usati di recente. */
    data class Row(val who: Who, val last: Entry?, val unread: Int, val channels: Set<Channel>, val group: Boolean = false)

    /** L'ultima cosa con un indirizzo ([address]: numero, email o utente della Chat), con quante da leggere. */
    data class Seen(val channel: Channel, val address: String, val last: Entry, val unread: Int, val name: String = "", val roomId: String? = null)

    /** Una persona della Rubrica com'è arrivata dal Telefono o da Messaggi. */
    data class Contact(val key: String, val name: String, val phones: List<String>, val emails: List<String>, val matrix: List<String>)

    /**
     * Le cifre che contano di un numero, per riconoscere lo stesso numero scritto in modi diversi: «+39 347 123 4567»,
     * «0039 3471234567» e «347 1234567» danno lo stesso risultato (le ultime nove cifre).
     */
    fun phoneKey(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 9) digits.takeLast(9) else digits
    }

    fun samePhone(a: String, b: String) = phoneKey(a).length >= 5 && phoneKey(a) == phoneKey(b)

    /** La chiave di un indirizzo che non è in Rubrica. */
    fun addressKey(channel: Channel, address: String) = when (channel) {
        Channel.Sms, Channel.Call -> "tel:" + phoneKey(address)
        Channel.Email -> "mail:" + address.trim().lowercase()
        Channel.Chat -> "mx:" + address.trim().lowercase()
    }

    /**
     * Mette insieme le ultime cose di ogni canale per persona: chi è in Rubrica ([contacts], una voce per ogni [seen],
     * null se non c'è) diventa una riga sola con tutti i canali; gli altri restano per indirizzo (due SMS dallo stesso
     * numero scritto in due modi fanno una riga). Dalla più recente.
     */
    fun group(seen: List<Seen>, contacts: List<Contact?>): List<Row> {
        class Acc(var who: Who, var last: Entry?, var unread: Int, val channels: MutableSet<Channel>)
        val acc = LinkedHashMap<String, Acc>()
        seen.forEachIndexed { i, s ->
            val c = contacts.getOrNull(i)
            val key = c?.let { "c:" + it.key } ?: addressKey(s.channel, s.address)
            val a = acc.getOrPut(key) {
                val who = if (c != null) Who(c.key, c.name, c.phones, c.emails, c.matrix, contact = true)
                else Who(
                    key, s.name.ifBlank { s.address },
                    phones = if (s.channel == Channel.Sms || s.channel == Channel.Call) listOf(s.address) else emptyList(),
                    emails = if (s.channel == Channel.Email) listOf(s.address) else emptyList(),
                    matrix = if (s.channel == Channel.Chat) listOf(s.address) else emptyList(),
                )
                Acc(who, null, 0, mutableSetOf())
            }
            if (s.roomId != null && a.who.roomId == null) a.who = a.who.copy(roomId = s.roomId)
            // Chi non è in Rubrica prende il nome più parlante (il nome della Chat o di chi scrive l'email, non il numero).
            if (c == null && s.name.isNotBlank() && a.who.name == s.address) a.who = a.who.copy(name = s.name)
            if (c != null && s.channel == Channel.Chat && s.address !in a.who.matrix) a.who = a.who.copy(matrix = a.who.matrix + s.address)
            a.unread += s.unread
            a.channels += s.channel
            if (a.last == null || s.last.time > a.last!!.time) a.last = s.last
        }
        return acc.map { (k, a) -> Row(a.who.copy(key = if (a.who.contact) a.who.key else k), a.last, a.unread, a.channels) }
            .sortedByDescending { it.last?.time ?: 0L }
    }

    /** Il filo: tutto insieme in ordine di tempo, senza doppioni. */
    fun merge(vararg lists: List<Entry>): List<Entry> = lists.flatMap { it }.distinctBy { it.id }.sortedBy { it.time }

    /** I canali per scrivere a [who] (le chiamate non sono un canale di scrittura). */
    fun writable(who: Who): List<Channel> = buildList {
        if (who.roomId != null || who.matrix.isNotEmpty()) add(Channel.Chat)
        if (who.phones.isNotEmpty()) add(Channel.Sms)
        if (who.emails.isNotEmpty()) add(Channel.Email)
    }

    /** Oltre questi caratteri un testo è «lungo»: con la scelta automatica va per email, se c'è. */
    const val LONG_TEXT = 600

    /**
     * Il canale con cui parte un messaggio con un tocco su Invia. Con la scelta automatica: un testo lungo va per email,
     * altrimenti si risponde dove la persona ha scritto per ultima. Poi il canale con la stellina, poi Chat, SMS, email.
     */
    fun choose(available: List<Channel>, preferred: Channel?, auto: Boolean, lastIncoming: Channel?, textLength: Int): Channel? {
        if (available.isEmpty()) return null
        if (auto) {
            if (textLength > LONG_TEXT && Channel.Email in available) return Channel.Email
            if (lastIncoming != null && lastIncoming != Channel.Call && lastIncoming in available) return lastIncoming
        }
        if (preferred != null && preferred in available) return preferred
        return listOf(Channel.Chat, Channel.Sms, Channel.Email).first { it in available }
    }

    /** Il canale dell'ultima cosa scritta dalla persona (le chiamate non contano). */
    fun lastIncoming(entries: List<Entry>): Channel? = entries.lastOrNull { it.incoming && it.channel != Channel.Call }?.channel

    /**
     * Chi invitare sulla Chat (tavola ChatInvitaElenco): persone della Rubrica con cui si scrive o si parla, ma non sulla
     * Chat, nell'ordine dell'elenco (le più recenti prima).
     */
    fun inviteCandidates(rows: List<Row>): List<Row> = rows.filter { r ->
        !r.group && r.who.contact && Channel.Chat !in r.channels && r.who.roomId == null && r.who.matrix.isEmpty() &&
            (r.who.phones.isNotEmpty() || r.who.emails.isNotEmpty())
    }

    /** Il canale dell'invito: quello che si usa già con la persona (SMS o email), altrimenti SMS se c'è un numero. */
    fun inviteChannel(who: Who, used: Set<Channel>): Channel? = when {
        who.phones.isNotEmpty() && (Channel.Sms in used || Channel.Call in used) -> Channel.Sms
        who.emails.isNotEmpty() && Channel.Email in used -> Channel.Email
        who.phones.isNotEmpty() -> Channel.Sms
        who.emails.isNotEmpty() -> Channel.Email
        else -> null
    }

    /** Il link dell'invito: apre una chat con me in qualunque app Matrix (matrix.to, nessun server nostro in mezzo). */
    fun inviteLink(userId: String) = "https://matrix.to/#/$userId"

    /** Privacy su dieci tacchette: la Chat cifrata da un capo all'altro, l'email, la voce dell'operatore, l'SMS. */
    fun privacy(channel: Channel, encrypted: Boolean): Int = when (channel) {
        Channel.Chat -> if (encrypted) 9 else 6
        Channel.Email -> 6
        Channel.Call -> 4
        Channel.Sms -> 3
    }
}
