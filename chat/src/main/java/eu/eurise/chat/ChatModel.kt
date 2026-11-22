package eu.eurise.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Come si accede al server scelto. */
sealed interface LoginStep {
    /** Accesso dal sito del server (SSO): si apre il browser. */
    data class Browser(val homeserver: String, val url: String) : LoginStep
    /** Il server chiede nome e password (nella nostra schermata). */
    data class Password(val homeserver: String) : LoginStep
    data class Failed(val reason: Int) : LoginStep
}

/** Lo stato di una stanza mentre arrivano le tornate di /sync. */
private class RoomState(val id: String) {
    val state = HashMap<String, JSONObject>()
    val names = HashMap<String, String>()
    var heroes: List<String> = emptyList()
    var encrypted = false
    var unread = 0
    var prevBatch: String? = null
    var invite = false
    var direct = false
}

/**
 * La Chat: accesso, stanze e messaggi. Mentre l'app è aperta una tornata di /sync dopo l'altra (long polling: il server
 * risponde quando c'è qualcosa); chiusa, ci pensa [ChatWorker] ogni 15 minuti.
 */
class ChatModel(private val context: Context, private val scope: CoroutineScope) {
    /** L'account che si sta guardando (personale o lavoro); l'altro resta collegato e conta solo le non lette. */
    var account by mutableStateOf(ChatAccounts.active(context))
        private set
    private var store = SessionStore(context, account)
    var session by mutableStateOf(store.load())
        private set
    /** Gli account con una sessione su questo telefono (per la barra in basso). */
    var accounts by mutableStateOf(ChatAccounts.signedIn(context))
        private set
    /** Le non lette dell'altro account (null = non collegato o non ancora contate). */
    var otherUnread by mutableStateOf<Int?>(null)
        private set
    /** La sessione dell'altro account, per la barra e il foglio degli account (null = non collegato). */
    var otherSession by mutableStateOf(SessionStore(context, account.other).load())
        private set
    private val client get() = session?.let(::ChatClient)

    val rooms = mutableStateListOf<Room>()
    /** «Una persona, tutti i canali»: la vista Persone e il filo di una persona con SMS, chiamate ed email. */
    val uni by lazy { Uni(context, scope, this) }
    /** Vocali: ascolto, trascrizione sul telefono, lettura ad alta voce (tavola ChatVocali). */
    val voice by lazy { VoiceHub(context, scope).also { it.onTranscribed = { publish() } } }
    private val data = HashMap<String, RoomState>()
    private val timelines = HashMap<String, SnapshotStateList<Event>>()
    /** Nomi delle persone (per i messaggi dei gruppi). */
    val people = mutableStateMapOf<String, String>()
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var openRoom by mutableStateOf<String?>(null)
        private set
    private var loop: Job? = null
    private var since: String? = null
    /** La cifratura end-to-end: nasce con la sessione (sul thread di rete). */
    private var crypto: ChatCrypto? = null
    /** Messaggi cifrati arrivati prima della loro chiave: si riprovano a ogni tornata. Solo sul thread di rete. */
    private val pending = HashMap<String, MutableList<JSONObject>>()

    private fun cryptoFor(s: Session): ChatCrypto? = crypto ?: runCatching { ChatCrypto(context, s) }.getOrNull().also { crypto = it }

    /**
     * Sul thread di rete, prima di mostrare una tornata: chiavi e dispositivi al modulo crittografico, messaggi cifrati
     * decifrati al loro posto, quelli rimasti in attesa riprovati, richieste del modulo mandate al server.
     */
    private suspend fun prepare(c: ChatClient, json: JSONObject): List<Pair<String, JSONObject>> {
        val cr = cryptoFor(c.session) ?: return emptyList()
        runCatching { cr.receiveSync(json) }
        json.optJSONObject("rooms")?.optJSONObject("join")?.let { join ->
            join.keys().forEach { roomId ->
                val a = join.getJSONObject(roomId).optJSONObject("timeline")?.optJSONArray("events") ?: return@forEach
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    if (e.optString("type") != "m.room.encrypted") continue
                    val clear = cr.decrypt(e, roomId)
                    if (clear != null) a.put(i, clear) else pending.getOrPut(roomId) { mutableListOf() }.add(e)
                }
                // La verifica di una persona passa dalla chat: i suoi passi vanno al modulo crittografico.
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    if (!ChatCrypto.isVerificationEvent(e)) continue
                    e.optString("sender").takeIf { it != c.session.userId }?.let { verifyPeers += it }
                    runCatching { cr.receiveVerification(c, e, roomId) }
                }
            }
        }
        val solved = mutableListOf<Pair<String, JSONObject>>()
        pending.forEach { (roomId, list) ->
            val it = list.iterator()
            while (it.hasNext()) {
                val e = it.next()
                cr.decrypt(e, roomId)?.let { clear -> solved += roomId to clear; it.remove() }
            }
        }
        runCatching { cr.flush(c) }
        return solved
    }

    fun timeline(roomId: String): SnapshotStateList<Event> = timelines.getOrPut(roomId) { mutableStateListOf() }

    fun room(id: String) = rooms.firstOrNull { it.id == id }

    fun nameOf(userId: String): String = people[userId]
        ?: if (eu.eurise.core.insieme.Scheda.isMailPerson(userId)) eu.eurise.core.insieme.Scheda.mailName(userId) else Matrix.localpart(userId)

    // --- Sincronizzazione ---

    /** Accende il ciclo di /sync (app aperta). */
    fun start() {
        if (session == null || loop?.isActive == true) return
        active = true
        cleanCache()
        client?.let { c -> scope.launch(Dispatchers.IO) { BussolaReports.redactDue(context, c) } }
        startOther()
        startPlans()
        startFolders()
        // Con la Chat aperta, i punti della posizione dal vivo di questo account li manda lei (tiene le chiavi).
        LiveShare.sink = { acc, room, content ->
            if (acc != account.name || client == null) false else { sendEvent(room, Posizione.BEACON, content) {}; true }
        }
        // Sono arrivato (tavola FamArrivato): con la Chat aperta la frase la manda lei.
        scope.launch(Dispatchers.IO) { runCatching { Arrivo.register(context) } }
        Arrivo.sink = { acc, room, content -> if (acc != account.name || client == null) false else { sendContent(room, content, Event.Kind.Notice, {}); true } }
        loop = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                val c = client ?: break
                val first = since == null
                val r = withContext(Dispatchers.IO) {
                    runCatching { c.sync(since, if (first) 0 else 30_000) }.map { json ->
                        runCatching { ProgrammaStore.receipts(context, account, c.session.userId, json) }
                        // Presa visione (tavola ChatPresaVisione): chi ha letto, per persona.
                        runCatching { Ack.readers(json).forEach { (room, m) -> readers[room] = (readers[room].orEmpty() + m).let { all -> all.keys.associateWith { k -> maxOf(all[k] ?: 0L, m[k] ?: 0L) } } } }
                        json to prepare(c, json)
                    }
                }
                r.onSuccess { (json, solved) ->
                    apply(json)
                    resolve(solved)
                    since = json.optString("next_batch").ifEmpty { since }
                    ready = true
                    error = null
                    backoff = 2_000L
                    checkIncoming()
                    if (security == null) refreshSecurity()
                }.onFailure { e ->
                    if (e is MatrixException && e.code == "M_UNKNOWN_TOKEN") { forget(); return@launch }
                    error = context.getString(R.string.ch_offline)
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    fun stop() {
        loop?.cancel(); loop = null; otherLoop?.cancel(); otherLoop = null; planLoop?.cancel(); planLoop = null; active = false
        LiveShare.sink = null
        Arrivo.sink = null
    }

    // --- Arriva quando l'altro lavora (tavola ChatQuandoLavora) ---

    /** L'orario di lavoro che l'altra persona della chat a due ha reso visibile, o null. */
    fun peerHours(roomId: String): Orari.Hours? {
        val peer = peerOf(roomId) ?: return null
        return Orari.decode(data[roomId]?.state?.get(Orari.TYPE + "|" + peer))
    }

    /** Fai sapere il tuo orario di lavoro (solo l'account di lavoro, spento di base). */
    var shareHours by mutableStateOf(simplePrefs.getBoolean("hours_" + account.name, false))
        private set

    /**
     * Accende o spegne: accesa, l'orario 8–19 dei feriali va nelle chat a due di questo account (uno stato per chat, con
     * la chiave del tuo id); spenta, lo stato si svuota.
     */
    fun changeShareHours(on: Boolean) {
        shareHours = on
        simplePrefs.edit().putBoolean("hours_" + account.name, on).apply()
        publishHours(all = true)
    }

    private fun publishHours(all: Boolean) {
        val c = client ?: return
        val me = session?.userId ?: return
        val content = if (shareHours) Orari.encode(offRules.hours) else JSONObject()
        val rooms = data.values.filter { it.direct && !it.invite && (all || (shareHours && !it.state.containsKey(Orari.TYPE + "|" + me))) }.map { it.id }
        if (rooms.isEmpty()) return
        scope.launch(Dispatchers.IO) { rooms.forEach { id -> runCatching { c.setState(id, Orari.TYPE, content, me) } } }
    }

    // --- Passa la palla (tavole InsPalla e InsPallaChat) ---

    private val ballPrefs get() = context.getSharedPreferences("chat_balls", Context.MODE_PRIVATE)
    /** Le palle passate da questo account (sul telefono). */
    var balls by mutableStateOf(Palla.decode(ballPrefs.getString("list", null)).filter { it.account == account.name })
        private set

    private fun saveBalls(mine: List<Palla.Ball>) {
        val others = Palla.decode(ballPrefs.getString("list", null)).filter { it.account != account.name }
        ballPrefs.edit().putString("list", Palla.encode(others + mine)).apply()
        balls = mine
    }

    /** Le persone a cui passarla: chi ha una chat a due con te in questo account. */
    fun colleagues(): List<Pair<String, String>> = rooms.filter { it.direct && !it.invite && it.id != selfRoom }
        .mapNotNull { r -> peerOf(r.id)?.let { r.id to it } }

    /**
     * Passa la palla: nella chat a due con [toUser] la scheda con il messaggio citato, la riga e la scadenza; se [notify], nella
     * chat di partenza una risposta «Se ne occupa Marta».
     */
    fun passBall(fromRoom: String, e: Event, toRoom: String, toUser: String, task: String, due: java.time.LocalDateTime?, notify: Boolean, failed: () -> Unit) {
        val c = client ?: return
        val me = session?.userId ?: return
        val id = "b" + System.nanoTime()
        val name = nameOf(toUser)
        val dueLabel = due?.let { Programma.time(it.toLocalTime()).let { t ->
            when (Programma.day(it, java.time.LocalDateTime.now())) {
                Programma.Day.Today -> context.getString(R.string.pr_today, t)
                Programma.Day.Tomorrow -> context.getString(R.string.pr_tomorrow, t)
                else -> context.getString(R.string.pr_date, Programma.shortDate(it.toLocalDate()), t)
            } } }
        val content = Palla.content(id, nameOf(me), shownTextOf(e), task, dueLabel, context.getString(R.string.pa_fallback, nameOf(me)))
        val encrypted = data[toRoom]?.encrypted == true
        scope.launch {
            val ev = withContext(Dispatchers.IO) {
                runCatching { ProgrammaStore.sendNow(c, if (encrypted) cryptoFor(c.session) else null, toRoom, content, encrypted) }.getOrNull()
            }
            if (ev == null) { failed(); return@launch }
            saveBalls(balls + Palla.Ball(id, account.name, toRoom, toUser, name, shownTextOf(e).take(200), task, due?.let { Programma.toMs(it) } ?: 0L,
                System.currentTimeMillis(), ev))
            if (notify && fromRoom != toRoom) reply(fromRoom, e.id, context.getString(R.string.pa_notice, name)) {}
        }
    }

    private fun shownTextOf(e: Event) = relations.edited(e.id, e.sender) ?: e.text

    /** Chi ha preso la palla risponde: In corso, Fatto. */
    fun answerBall(roomId: String, e: Event, id: String, state: Palla.State, failed: () -> Unit) {
        val body = context.getString(if (state == Palla.State.Done) R.string.pa_done_body else R.string.pa_working_body)
        sendContent(roomId, Palla.statusContent(e.id, id, state, body), Event.Kind.Notice, failed)
    }

    /** «Riprendi»: la palla torna a te (lo si dice anche all'altro). */
    fun retakeBall(id: String) {
        val b = balls.firstOrNull { it.id == id } ?: return
        saveBalls(balls.map { if (it.id == id) it.copy(state = Palla.State.Taken, stateAt = System.currentTimeMillis()) else it })
        if (b.eventId.isNotEmpty()) sendContent(b.toRoom, Palla.statusContent(b.eventId, id, Palla.State.Taken, context.getString(R.string.pa_taken_body)), Event.Kind.Notice, {})
    }

    fun forgetBall(id: String) = saveBalls(balls.filterNot { it.id == id })

    /** Lo stato più recente di ogni palla dalle risposte arrivate nelle chat a due. */
    private fun refreshBalls() {
        if (balls.isEmpty()) return
        val updates = HashMap<String, Pair<Palla.State, Long>>()
        balls.map { it.toRoom }.distinct().forEach { room ->
            timelines[room].orEmpty().forEach { e ->
                Palla.status(e.data?.let { runCatching { JSONObject(it) }.getOrNull() })?.let { (id, st) ->
                    val b = balls.firstOrNull { it.id == id } ?: return@let
                    if (e.sender == b.toUser && (updates[id]?.second ?: 0L) <= e.time) updates[id] = st to e.time
                }
            }
        }
        if (updates.isEmpty()) return
        val next = Palla.update(balls, updates)
        if (next != balls) saveBalls(next)
    }

    // --- Persone e ruoli (tavola InsRuoli): dalla Rubrica, per le proposte e gli spazi ---

    /** I ruoli della Rubrica (Eurise Telefono); vuoti se manca. Si rileggono quando servono (fogli di Affida, Condividi…). */
    var roles by mutableStateOf(emptyList<eu.eurise.core.insieme.Ruoli.Person>())
        private set

    fun loadRoles() {
        scope.launch { roles = withContext(Dispatchers.IO) { eu.eurise.core.insieme.RuoliContract.all(context) } }
    }

    fun roleOf(userId: String): eu.eurise.core.insieme.Ruoli.Role? = eu.eurise.core.insieme.Ruoli.find(roles, userId)?.role

    /** Il ruolo di una persona della Chat (o di un indirizzo email): lo salva la Rubrica. */
    fun setRole(userId: String, role: eu.eurise.core.insieme.Ruoli.Role?, failed: () -> Unit) {
        val existing = eu.eurise.core.insieme.Ruoli.find(roles, userId)
        val key = existing?.key ?: ("chat:" + userId.lowercase())
        val addresses = (existing?.addresses.orEmpty() + userId).distinct()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { eu.eurise.core.insieme.RuoliContract.set(context, key, existing?.name ?: nameOf(userId), role, existing?.level, addresses) }
            if (!ok) failed()
            roles = withContext(Dispatchers.IO) { eu.eurise.core.insieme.RuoliContract.all(context) }
        }
    }

    // --- La stessa scheda in Chat e in Email (tavola InsStessaScheda) e Affida a… (tavola InsAffida) ---

    private val givenPrefs get() = context.getSharedPreferences("chat_given", Context.MODE_PRIVATE)
    /** Le cose affidate da questo account (sul telefono), in Chat o per email. */
    var given by mutableStateOf(Affida.decode(givenPrefs.getString("list", null)).filter { it.account == account.name })
        private set

    private fun saveGiven(mine: List<Affida.Given>) {
        val others = Affida.decode(givenPrefs.getString("list", null)).filter { it.account != account.name }
        givenPrefs.edit().putString("list", Affida.encode(others + mine)).apply()
        given = mine
    }

    /** Il testo per chi usa Element o un'altra app: chi, cosa, entro quando, la riga. */
    private fun cardBody(c: eu.eurise.core.insieme.Scheda.Card): String {
        val w = eu.eurise.core.insieme.SchedaMail.words(context)
        return "📋 " + w.intro(c.kind, c.from) + "\n" + c.title + (if (c.due.isNotBlank()) " · " + w.due(c.due) else "") +
            (if (c.note.isNotBlank()) "\n«" + c.note + "»" else "") +
            c.options.mapIndexed { i, o -> "\n" + w.option(i, o) }.joinToString("")
    }

    /**
     * Affida a… : nella chat a due con [toUser] (o per email a [email], da Eurise Email) la scheda con la cosa, la scadenza e la
     * riga; se non risponde entro [backAfter] millisecondi torna a te.
     */
    fun entrust(toRoom: String?, toUser: String, email: String, title: String, note: String, due: java.time.LocalDateTime?, backAfter: Long, failed: (Int) -> Unit) {
        val c = client ?: return
        val me = session?.userId ?: return
        val id = "c" + System.nanoTime()
        val dueLabel = due?.let { dueLabel(it) }.orEmpty()
        val card = eu.eurise.core.insieme.Scheda.Card(id, eu.eurise.core.insieme.Scheda.Kind.Affida, title.trim(), note.trim(), dueLabel,
            due?.let { Programma.toMs(it) } ?: 0L, nameOf(me).substringBefore(' '), origin = "chat:affida")
        scope.launch {
            if (email.isNotBlank()) {
                val ok = withContext(Dispatchers.IO) { runCatching { eu.eurise.core.insieme.SchedaContract.send(context, card, listOf(email.trim())) }.getOrDefault(false) }
                if (!ok) { failed(R.string.af_mail_failed); return@launch }
                saveGiven(given + Affida.Given(id, account.name, "", "", email.trim(), title.trim(), dueLabel, System.currentTimeMillis(), backAfter = backAfter, email = email.trim()))
                return@launch
            }
            val room = toRoom ?: run { failed(R.string.ch_send_failed); return@launch }
            val encrypted = data[room]?.encrypted == true
            val ev = withContext(Dispatchers.IO) {
                runCatching { ProgrammaStore.sendNow(c, if (encrypted) cryptoFor(c.session) else null, room, eu.eurise.core.insieme.Scheda.content(card, cardBody(card)), encrypted) }.getOrNull()
            }
            if (ev == null) { failed(R.string.ch_send_failed); return@launch }
            saveGiven(given + Affida.Given(id, account.name, room, toUser, nameOf(toUser).substringBefore(' '), title.trim(), dueLabel, System.currentTimeMillis(), ev, backAfter))
        }
    }

    private fun dueLabel(it: java.time.LocalDateTime): String = Programma.time(it.toLocalTime()).let { t ->
        when (Programma.day(it, java.time.LocalDateTime.now())) {
            Programma.Day.Today -> context.getString(R.string.pr_today, t)
            Programma.Day.Tomorrow -> context.getString(R.string.pr_tomorrow, t)
            else -> context.getString(R.string.pr_date, Programma.shortDate(it.toLocalDate()), t)
        }
    }

    /** Chi ha ricevuto la scheda risponde con un tasto (Non posso, Vista, Fatta, Sì…). */
    fun answerCard(roomId: String, e: Event, id: String, answer: eu.eurise.core.insieme.Scheda.Answer, failed: () -> Unit) {
        val body = answer.state?.let { context.getString(eu.eurise.core.insieme.SchedaMail.label(it)) }
            ?: answer.choices?.sorted()?.joinToString(", ") { (it + 1).toString() }.orEmpty()
        sendContent(roomId, eu.eurise.core.insieme.Scheda.answerContent(e.id, id, answer, "📋 " + body), Event.Kind.Notice, failed)
    }

    /** Le risposte a una scheda nella stanza, per persona (vale l'ultima). */
    fun cardAnswers(roomId: String, e: Event, id: String): Map<String, eu.eurise.core.insieme.Scheda.Answer> =
        eu.eurise.core.insieme.Scheda.fold(e.sender, timeline(roomId).mapNotNull { ev ->
            if (ev.kind != Event.Kind.Notice || ev.data?.contains(eu.eurise.core.insieme.Scheda.ANSWER) != true) return@mapNotNull null
            eu.eurise.core.insieme.Scheda.answer(runCatching { JSONObject(ev.data) }.getOrNull())?.takeIf { it.first == id }?.let { ev.sender to it.second }
        })

    /** «Riprendi»: torna a te (lo si dice anche all'altro, se era in Chat). */
    fun retakeGiven(id: String) {
        val g = given.firstOrNull { it.id == id } ?: return
        saveGiven(given.map { if (it.id == id) it.copy(retaken = true) else it })
        if (g.eventId.isNotEmpty()) reply(g.room, g.eventId, context.getString(R.string.af_retaken_body, g.title)) {}
    }

    fun forgetGiven(id: String) = saveGiven(given.filterNot { it.id == id })

    /** «Sollecita»: una risposta gentile alla scheda (in Chat) e il tempo di ritorno riparte. */
    fun remindGiven(id: String, failed: () -> Unit) {
        val g = given.firstOrNull { it.id == id } ?: return
        if (g.eventId.isNotEmpty()) reply(g.room, g.eventId, context.getString(R.string.af_remind_body, g.title), failed)
        saveGiven(given.map { if (it.id == id) it.copy(sentAt = System.currentTimeMillis(), retaken = false) else it })
    }

    /** Lo stato più recente di ogni cosa affidata, dalle risposte nelle chat a due. */
    private fun refreshGiven() {
        if (given.isEmpty()) return
        val updates = HashMap<String, Triple<eu.eurise.core.insieme.Scheda.State, Long, String>>()
        given.filter { !it.byMail }.map { it.room }.distinct().forEach { room ->
            timeline(room).forEach { e ->
                if (e.kind != Event.Kind.Notice || e.data?.contains(eu.eurise.core.insieme.Scheda.ANSWER) != true) return@forEach
                eu.eurise.core.insieme.Scheda.answer(runCatching { JSONObject(e.data) }.getOrNull())?.let { (id, a) ->
                    val g = given.firstOrNull { it.id == id } ?: return@let
                    val st = a.state ?: return@let
                    if (e.sender == g.toUser && (updates[id]?.second ?: 0L) <= e.time) updates[id] = Triple(st, e.time, a.note)
                }
            }
        }
        if (updates.isEmpty()) return
        val next = Affida.update(given, updates)
        if (next != given) saveGiven(next)
    }

    /** Le risposte arrivate per email alle schede della Chat (da Eurise Email): cose affidate, faccende, uscite. */
    private var mailTick = 0
    private fun refreshMailAnswers() {
        if (mailTick++ % 4 != 0) return
        val me = session?.userId ?: return
        scope.launch {
            val got = withContext(Dispatchers.IO) { runCatching { eu.eurise.core.insieme.SchedaContract.answers(context, "chat:") }.getOrDefault(emptyList()) }
            if (got.isEmpty()) return@launch
            val prefs = context.getSharedPreferences("chat_mail_answers", Context.MODE_PRIVATE)
            val done = prefs.getStringSet(account.name, emptySet()).orEmpty().toMutableSet()
            val updates = HashMap<String, Triple<eu.eurise.core.insieme.Scheda.State, Long, String>>()
            got.forEach { g ->
                val key = g.id + "|" + g.address + "|" + g.at
                given.firstOrNull { it.id == g.id && it.email.equals(g.address, true) }?.let { x ->
                    g.answer.state?.let { st -> if ((updates[x.id]?.second ?: 0L) <= g.at) updates[x.id] = Triple(st, g.at, g.answer.note) }
                }
                if (key in done) return@forEach
                val person = eu.eurise.core.insieme.Scheda.mailPerson(g.address)
                // Spese (tavola SpeEmail): «ho pagato» per email salda il suo debito nel gruppo.
                if (g.answer.state == eu.eurise.core.insieme.Scheda.State.Paid) { expensePaidByMail(g.id); giftPaidByMail(g.id) }
                // Faccenda: «fatto» per email passa il turno, se toccava a lui.
                val choreId = g.id.substringBeforeLast('-')
                data.values.firstOrNull { r -> Faccende.decode(r.state[Faccende.TYPE]).any { it.id == choreId && it.by == me } }?.let { r ->
                    val list = Faccende.decode(r.state[Faccende.TYPE])
                    val c = list.first { it.id == choreId }
                    val whose = Faccende.whose(c)
                    if (g.answer.state == eu.eurise.core.insieme.Scheda.State.Done && whose != null && eu.eurise.core.insieme.Scheda.mailAddress(whose).equals(g.address, true)) {
                        val next = Faccende.done(c, whose, g.at)
                        saveChores(r.id, list.map { if (it.id == choreId) next else it }) {}
                        Faccende.whose(next)?.let { who -> sendContent(r.id, JSONObject().put("msgtype", "m.notice")
                            .put("body", context.getString(R.string.fa_done_notice, c.title, nameOf(who).substringBefore(' '))), Event.Kind.Notice, {}) }
                    }
                }
                // Uscita: le date scelte per email entrano nel gruppo, per conto di chi ha risposto.
                g.answer.choices?.let { ch ->
                    data.keys.forEach { roomId ->
                        val ev = timeline(roomId).firstOrNull { it.sender == me && it.data?.contains(Uscita.CARD) == true &&
                            Uscita.card(runCatching { JSONObject(it.data) }.getOrNull())?.id == g.id } ?: return@forEach
                        sendContent(roomId, Uscita.answer(ev.id, g.id, Uscita.Say.Dates(ch, null), context.getString(R.string.us_mail_vote, eu.eurise.core.insieme.Scheda.mailName(person)), person),
                            Event.Kind.Notice, {})
                    }
                }
                done += key
            }
            prefs.edit().putStringSet(account.name, done.toList().takeLast(300).toSet()).apply()
            if (updates.isNotEmpty()) { val next = Affida.update(given, updates); if (next != given) saveGiven(next) }
        }
    }

    // --- Disponibilità (tavola InsDisponibilita), Condividi (InsCondividi), agenda con l'assistente (InsAssistente) ---

    private val zone get() = java.time.ZoneId.systemDefault()

    /** Gli orari liberi della settimana prossima dal calendario del telefono (fuori dal thread principale). */
    fun freeSlots(durationMin: Int, done: (List<eu.eurise.core.insieme.Disponibilita.Slot>) -> Unit) {
        scope.launch {
            val from = eu.eurise.core.insieme.Disponibilita.nextWeek(java.time.LocalDate.now())
            val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val slots = withContext(Dispatchers.IO) {
                val busy = eu.eurise.core.insieme.Agenda.busy(context, start, start + 7 * 86_400_000L)
                eu.eurise.core.insieme.Disponibilita.free(busy, from, 5, durationMin, zone, System.currentTimeMillis())
            }
            done(slots)
        }
    }

    /** Manda gli orari scelti: all'altro arrivano solo quelli; scade fra 7 giorni o quando sceglie. */
    fun sendSlots(roomId: String, durationMin: Int, slots: List<eu.eurise.core.insieme.Disponibilita.Slot>, failed: () -> Unit) {
        val me = session?.userId ?: return
        if (slots.isEmpty()) return
        val card = eu.eurise.core.insieme.Scheda.Card("o" + System.nanoTime(), eu.eurise.core.insieme.Scheda.Kind.Orari,
            context.getString(R.string.di_card_title, nameOf(me).substringBefore(' '), durationLabel(durationMin)),
            dueAt = System.currentTimeMillis() + 7 * 86_400_000L, from = nameOf(me).substringBefore(' '),
            options = slots.map { eu.eurise.core.insieme.Disponibilita.label(it.start, zone) }, times = slots.map { it.start }, durationMin = durationMin,
            origin = "chat:$roomId")
        sendContent(roomId, eu.eurise.core.insieme.Scheda.content(card, cardBody(card)), Event.Kind.Text, failed)
    }

    fun durationLabel(min: Int): String = if (min < 60) context.getString(R.string.di_min, min) else context.getString(R.string.di_hours, min / 60)

    /** Scrive un appuntamento nel calendario (o apre il Calendario già compilato, se manca il permesso). */
    fun toCalendar(title: String, start: Long, end: Long, place: String, note: String, open: (android.content.Intent) -> Unit, done: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { eu.eurise.core.insieme.Agenda.insert(context, title, start, end, place, note) }
            if (!ok) open(eu.eurise.core.insieme.Agenda.insertIntent(title, start, end, place, note))
            done(ok)
        }
    }

    private val slotPrefs get() = context.getSharedPreferences("chat_slots", Context.MODE_PRIVATE)

    /** Chi ha mandato gli orari: quando l'altro sceglie, l'appuntamento entra da solo nel calendario (una volta) e si dice in chat. */
    private fun refreshSlots() {
        val me = session?.userId ?: return
        val done = slotPrefs.getStringSet(account.name, emptySet()).orEmpty()
        val todo = mutableListOf<Triple<String, Event, eu.eurise.core.insieme.Scheda.Card>>()
        data.keys.forEach { roomId ->
            timeline(roomId).forEach { e ->
                if (e.sender != me || e.data?.contains(eu.eurise.core.insieme.Scheda.FIELD) != true) return@forEach
                val card = eu.eurise.core.insieme.Scheda.card(runCatching { JSONObject(e.data) }.getOrNull()) ?: return@forEach
                if (card.kind != eu.eurise.core.insieme.Scheda.Kind.Orari || card.id in done) return@forEach
                todo += Triple(roomId, e, card)
            }
        }
        if (todo.isEmpty()) return
        val added = mutableSetOf<String>()
        todo.forEach { (roomId, e, card) ->
            val (who, a) = cardAnswers(roomId, e, card.id).entries.firstOrNull { it.key != me && !it.value.choices.isNullOrEmpty() }?.toPair() ?: return@forEach
            val i = a.choices!!.min()
            val start = card.times.getOrNull(i) ?: return@forEach
            val end = start + card.durationMin * 60_000L
            added += card.id
            scope.launch {
                withContext(Dispatchers.IO) { eu.eurise.core.insieme.Agenda.insert(context, context.getString(R.string.di_event_title, nameOf(who)), start, end) }
            }
            sendContent(roomId, JSONObject().put("msgtype", "m.notice").put("body", context.getString(R.string.di_fixed, nameOf(who).substringBefore(' '),
                eu.eurise.core.insieme.Disponibilita.range(start, end, zone))), Event.Kind.Notice, {})
        }
        if (added.isNotEmpty()) slotPrefs.edit().putStringSet(account.name, done + added).apply()
    }

    /**
     * Condividi con… un appuntamento: a ognuno nella sua chat a due (con «Aggiungi al mio calendario») e per email a chi non usa
     * Eurise. [edit]: vede o modifica (lo dice la scheda; la modifica vera passa dal calendario condiviso sul cloud).
     */
    fun shareEvent(item: eu.eurise.core.insieme.Agenda.Item, to: List<Pair<String, String>>, emails: List<String>, edit: Boolean, failed: (Int) -> Unit) {
        val me = session?.userId ?: return
        val card = eu.eurise.core.insieme.Scheda.Card("s" + System.nanoTime(), eu.eurise.core.insieme.Scheda.Kind.Condividi, item.title.ifBlank { "—" },
            item.place, eu.eurise.core.insieme.Disponibilita.range(item.start, item.end, zone), item.start, nameOf(me).substringBefore(' '),
            edit = edit, endAt = item.end, origin = "chat:share")
        to.forEach { (room, _) -> sendContent(room, eu.eurise.core.insieme.Scheda.content(card, cardBody(card)), Event.Kind.Text, { failed(R.string.ch_send_failed) }) }
        if (emails.isNotEmpty()) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { eu.eurise.core.insieme.SchedaContract.send(context, card, emails) }.getOrDefault(false) }
            if (!ok) failed(R.string.af_mail_failed)
        }
    }

    /** Gli impegni dei prossimi 14 giorni, per sceglierne uno da condividere. */
    fun upcoming(done: (List<eu.eurise.core.insieme.Agenda.Item>) -> Unit) {
        scope.launch {
            val now = System.currentTimeMillis()
            done(withContext(Dispatchers.IO) { eu.eurise.core.insieme.Agenda.items(context, now, now + 14 * 86_400_000L).filter { !it.allDay }.take(40) })
        }
    }

    // Agenda condivisa con l'assistente.

    private val agendaPrefs get() = context.getSharedPreferences("chat_agenda", Context.MODE_PRIVATE)

    /** Per ogni chat a due in cui la condividi, cosa esce di ogni calendario. */
    fun agendaModes(roomId: String): Map<Long, Assistente.Mode> = Assistente.decodeModes(agendaPrefs.getString(account.name + "|" + roomId, null))

    fun calendars(done: (List<eu.eurise.core.insieme.Agenda.Cal>) -> Unit) { scope.launch { done(withContext(Dispatchers.IO) { eu.eurise.core.insieme.Agenda.calendars(context) }) } }

    /** Accende, cambia o spegne (tutto «mai») l'agenda condivisa nella chat a due. */
    fun shareAgenda(roomId: String, modes: Map<Long, Assistente.Mode>) {
        val shown = modes.filterValues { it != Assistente.Mode.Never }
        agendaPrefs.edit().apply { if (shown.isEmpty()) remove(account.name + "|" + roomId) else putString(account.name + "|" + roomId, Assistente.encodeModes(shown)) }.apply()
        publishAgenda(roomId)
    }

    private var agendaAt = 0L

    /** Mette (o aggiorna) gli occupati nello stato della stanza; vuoto se spenta. */
    private fun publishAgenda(roomId: String) {
        val c = client ?: return
        val me = session?.userId ?: return
        val modes = agendaModes(roomId)
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val content = if (modes.isEmpty()) JSONObject() else {
                val items = eu.eurise.core.insieme.Agenda.items(context, now, now + Assistente.DAYS * 86_400_000L, modes.keys)
                    .filter { it.busy && !it.allDay }.map { it.calendar to eu.eurise.core.insieme.Disponibilita.Busy(it.start, it.end, it.title) }
                Assistente.encode(Assistente.Shared(now, now, now + Assistente.DAYS * 86_400_000L, Assistente.blocks(items, modes)))
            }
            runCatching { c.setState(roomId, Assistente.TYPE, content, me) }
        }
    }

    /** Ogni mezz'ora, ad app aperta: l'agenda condivisa si aggiorna. */
    private fun refreshAgendas() {
        val now = System.currentTimeMillis()
        if (now - agendaAt < 30 * 60_000L) return
        agendaAt = now
        val prefix = account.name + "|"
        agendaPrefs.all.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.filter { data.containsKey(it) }.forEach { publishAgenda(it) }
    }

    /** L'agenda che l'altro condivide con te in questa chat a due, se lo fa. */
    fun peerAgenda(roomId: String): Assistente.Shared? {
        val peer = peerOf(roomId) ?: return null
        return Assistente.decode(data[roomId]?.state?.get(Assistente.TYPE + "|" + peer))?.takeIf { it.blocks.isNotEmpty() || it.updated > 0 }
    }

    /** La segreteria propone un appuntamento: arriva come scheda da confermare con un tocco. */
    fun propose(roomId: String, title: String, note: String, start: Long, end: Long, failed: () -> Unit) {
        val me = session?.userId ?: return
        val card = eu.eurise.core.insieme.Scheda.Card("p" + System.nanoTime(), eu.eurise.core.insieme.Scheda.Kind.Proposta, title.trim(), note.trim(),
            eu.eurise.core.insieme.Disponibilita.range(start, end, zone), start, nameOf(me).substringBefore(' '), endAt = end, origin = "chat:$roomId")
        sendContent(roomId, eu.eurise.core.insieme.Scheda.content(card, cardBody(card)), Event.Kind.Text, failed)
    }

    // --- Elenchi condivisi (tavola InsElenchi) ---

    fun lists(roomId: String): List<Elenchi.Lista> = Elenchi.decode(data[roomId]?.state?.get(Elenchi.TYPE)).ifEmpty {
        Elenchi.defaults(listOf(context.getString(R.string.el_spesa), context.getString(R.string.el_casa), context.getString(R.string.el_ufficio)))
    }

    fun saveLists(roomId: String, lists: List<Elenchi.Lista>, failed: () -> Unit) {
        val c = client ?: return
        val content = Elenchi.encode(lists, System.currentTimeMillis())
        val s = data[roomId]
        val before = s?.state?.get(Elenchi.TYPE)
        s?.state?.set(Elenchi.TYPE, content)
        relationsTick++
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { c.setState(roomId, Elenchi.TYPE, content) }.isSuccess }
            if (!ok) { if (before != null) s?.state?.set(Elenchi.TYPE, before) else s?.state?.remove(Elenchi.TYPE); relationsTick++; failed() }
        }
    }

    /** L'elenco a chi non usa la Chat, per email (le voci da fare). */
    fun mailList(roomId: String, l: Elenchi.Lista, emails: List<String>, failed: () -> Unit) {
        val me = session?.userId ?: return
        val card = eu.eurise.core.insieme.Scheda.Card("e" + System.nanoTime(), eu.eurise.core.insieme.Scheda.Kind.Elenco, l.name,
            l.items.filter { it.doneAt == 0L }.joinToString("\n") { "☐ " + it.text }, from = nameOf(me).substringBefore(' '), origin = "chat:$roomId")
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { eu.eurise.core.insieme.SchedaContract.send(context, card, emails) }.getOrDefault(false) }
            if (!ok) failed()
        }
    }

    // --- La nota della riunione (tavola InsRiunione) ---

    data class Meeting(val title: String, val text: String, val by: String, val at: Long)

    private val MEETING = "eu.eurise.meeting"

    fun meeting(roomId: String): Meeting? = data[roomId]?.state?.get(MEETING)?.let { o ->
        Meeting(o.optString("title"), o.optString("text"), o.optString("by"), o.optLong("at")).takeIf { it.text.isNotBlank() || it.title.isNotBlank() }
    }

    fun saveMeeting(roomId: String, title: String, text: String, failed: () -> Unit) {
        val c = client ?: return
        val me = session?.userId ?: return
        val content = JSONObject().put("title", title.trim()).put("text", text).put("by", me).put("at", System.currentTimeMillis())
        data[roomId]?.state?.set(MEETING, content)
        relationsTick++
        scope.launch { if (withContext(Dispatchers.IO) { runCatching { c.setState(roomId, MEETING, content) }.isFailure }) failed() }
    }

    /** «Assegna tutte»: ogni cosa trovata nella nota diventa «Affida a…» nel gruppo, rivolta alla sua persona, con la scadenza. */
    fun assignMeeting(roomId: String, tasks: List<eu.eurise.core.insieme.Riunione.Task>, failed: () -> Unit) {
        val c = client ?: return
        val me = session?.userId ?: return
        val title = meeting(roomId)?.title.orEmpty()
        tasks.filter { it.who != null }.forEach { t ->
            val who = t.who!!
            val due = t.due?.atTime(12, 0)
            val dueLabel = due?.let { dueLabel(it) }.orEmpty()
            val id = "c" + System.nanoTime()
            val card = eu.eurise.core.insieme.Scheda.Card(id, eu.eurise.core.insieme.Scheda.Kind.Affida, t.text, if (title.isNotBlank()) context.getString(R.string.ri_from_note, title) else "",
                dueLabel, due?.let { Programma.toMs(it) } ?: 0L, nameOf(me).substringBefore(' '), origin = "chat:affida", to = who)
            if (who == me) {
                // Le tue: direttamente nelle Attività, con la scadenza.
                val (hubPkg, permission) = hub()
                eu.eurise.core.suite.HubBridge.send(context, hubPkg, permission, eu.eurise.core.suite.HubBridge.ACTION_TASK, t.text, due = t.due,
                    notes = card.note.ifBlank { null })
                return@forEach
            }
            val encrypted = data[roomId]?.encrypted == true
            scope.launch {
                val ev = withContext(Dispatchers.IO) {
                    runCatching { ProgrammaStore.sendNow(c, if (encrypted) cryptoFor(c.session) else null, roomId, eu.eurise.core.insieme.Scheda.content(card, cardBody(card)), encrypted) }.getOrNull()
                }
                if (ev == null) { failed(); return@launch }
                saveGiven(given + Affida.Given(id, account.name, roomId, who, nameOf(who).substringBefore(' '), t.text, dueLabel, System.currentTimeMillis(), ev, 86_400_000L))
            }
        }
    }

    // --- La cartella di squadra (tavola InsSquadra) ---

    private val TEAM = "eu.eurise.team"

    fun teamFolder(roomId: String): String? = data[roomId]?.state?.get(TEAM)?.optString("url")?.takeIf { it.startsWith("https://") }

    fun setTeamFolder(roomId: String, url: String, failed: () -> Unit) {
        val c = client ?: return
        val content = JSONObject().put("url", url.trim())
        data[roomId]?.state?.set(TEAM, content)
        relationsTick++
        scope.launch { if (withContext(Dispatchers.IO) { runCatching { c.setState(roomId, TEAM, content) }.isFailure }) failed() }
    }

    // --- La casa (tavole FamScadenze, FamDocumenti, FamCena, FamGenitori) ---

    /** Mette uno stato della stanza (lo stesso per tutti), subito anche qui; se non parte torna com'era. */
    fun putState(roomId: String, type: String, content: JSONObject, failed: () -> Unit) {
        val c = client ?: return
        val s = data[roomId]
        val before = s?.state?.get(type)
        s?.state?.set(type, content)
        relationsTick++
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { c.setState(roomId, type, content) }.isSuccess }
            if (!ok) { if (before != null) s?.state?.set(type, before) else s?.state?.remove(type); relationsTick++; failed() }
        }
    }

    fun deadlines(roomId: String) = Casa.decodeDeadlines(data[roomId]?.state?.get(Casa.DEADLINES))

    fun saveDeadlines(roomId: String, list: List<Casa.Deadline>, failed: () -> Unit) = putState(roomId, Casa.DEADLINES, Casa.encodeDeadlines(list), failed)

    /** «Pagata»: segnata, e se va divisa entra nelle spese del gruppo (pagata da chi paga, divisa fra tutti). */
    fun payDeadline(roomId: String, id: String, failed: () -> Unit) {
        val list = deadlines(roomId)
        val d = list.firstOrNull { it.id == id } ?: return
        saveDeadlines(roomId, Casa.paid(list, id, System.currentTimeMillis()), failed)
        if (d.split && d.cents > 0) {
            val payer = d.payer.ifBlank { session?.userId.orEmpty() }
            val people = members[roomId].orEmpty().ifEmpty { listOf(payer) }
            val e = Expenses.Expense(java.util.UUID.randomUUID().toString(), d.title, d.cents, payer, Expenses.equal(d.cents, people), Expenses.Mode.Equal)
            sendExpense(roomId, e, context.getString(R.string.ex_body, d.title, Expenses.money(d.cents), nameOf(payer), people.size), failed)
        }
    }

    /** Il promemoria in Attività (o nel Calendario) per una scadenza: il giorno dell'avviso. */
    fun remindDeadline(d: Casa.Deadline, calendar: Boolean, open: (android.content.Intent) -> Unit): Boolean {
        val day = Casa.warnDay(d)
        if (calendar) {
            val start = java.time.LocalDate.ofEpochDay(d.due).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            toCalendar(d.title, start, start + 1_800_000L, "", if (d.cents > 0) Expenses.money(d.cents) else "", open)
            return true
        }
        val (hubPkg, permission) = hub()
        return eu.eurise.core.suite.HubBridge.send(context, hubPkg, permission, eu.eurise.core.suite.HubBridge.ACTION_TASK,
            d.title + (if (d.cents > 0) " · " + Expenses.money(d.cents) else ""), due = day)
    }

    fun menu(roomId: String) = Casa.decodeMenu(data[roomId]?.state?.get(Casa.MENU))

    fun saveMenu(roomId: String, menu: List<Casa.Dish>, failed: () -> Unit) = putState(roomId, Casa.MENU, Casa.encodeMenu(menu), failed)

    /** «In Spesa»: gli ingredienti che mancano vanno nell'elenco Spesa del gruppo. */
    fun menuToShopping(roomId: String, failed: () -> Unit) {
        val me = session?.userId ?: return
        var lists = lists(roomId)
        val spesa = lists.firstOrNull { it.name.equals(context.getString(R.string.el_spesa), true) } ?: lists.firstOrNull() ?: return
        val now = System.currentTimeMillis()
        Casa.missing(menu(roomId), spesa).forEachIndexed { i, x -> lists = Elenchi.add(lists, spesa.id, x, me, now + i, from = context.getString(R.string.fc_from_menu)) }
        saveLists(roomId, lists, failed)
    }

    /** Chi c'è a cena? (tavola FamCena): la domanda con Sì, Tardi, No; se [daily], il tuo telefono la rifà ogni giorno alle 17. */
    fun askDinner(roomId: String, daily: Boolean, failed: () -> Unit) {
        val me = session?.userId ?: return
        val card = eu.eurise.core.insieme.Scheda.Card("d" + System.nanoTime(), eu.eurise.core.insieme.Scheda.Kind.Cena, context.getString(R.string.fc_question),
            due = java.time.LocalTime.now().let { "%d:%02d".format(it.hour, it.minute) }, from = nameOf(me).substringBefore(' '), origin = "chat:$roomId")
        sendContent(roomId, eu.eurise.core.insieme.Scheda.content(card, cardBody(card)), Event.Kind.Text, failed)
        dinnerPrefs.edit().putLong(account.name + "|" + roomId, java.time.LocalDate.now().toEpochDay()).apply()
        if (daily) putState(roomId, Casa.DINNER, JSONObject().put("by", me).put("hour", 17), failed)
    }

    private val dinnerPrefs get() = context.getSharedPreferences("chat_dinner", Context.MODE_PRIVATE)

    fun dinnerDaily(roomId: String): Boolean = data[roomId]?.state?.get(Casa.DINNER)?.optString("by") == session?.userId

    fun stopDinner(roomId: String, failed: () -> Unit) = putState(roomId, Casa.DINNER, JSONObject(), failed)

    /** Ad app aperta: dopo le 17, la domanda della cena parte da sola (una volta al giorno, dal telefono di chi l'ha chiesto). */
    private fun refreshDinner() {
        val me = session?.userId ?: return
        val now = java.time.LocalDateTime.now()
        data.values.forEach { r ->
            val o = r.state[Casa.DINNER] ?: return@forEach
            if (o.optString("by") != me || now.hour < o.optInt("hour", 17) || now.hour >= 21) return@forEach
            val key = account.name + "|" + r.id
            if (dinnerPrefs.getLong(key, 0) >= now.toLocalDate().toEpochDay()) return@forEach
            askDinner(r.id, daily = false) {}
        }
    }

    fun meds(roomId: String) = Casa.decodeMeds(data[roomId]?.state?.get(Casa.MEDS))

    fun saveMeds(roomId: String, m: Casa.Meds?, failed: () -> Unit) {
        putState(roomId, Casa.MEDS, m?.let(Casa::encodeMeds) ?: JSONObject(), failed)
        saveMedsSnapshot()
    }

    fun takeMed(roomId: String, medId: String, failed: () -> Unit) {
        val m = meds(roomId) ?: return
        saveMeds(roomId, Casa.take(m, medId, java.time.LocalDate.now(), System.currentTimeMillis()), failed)
    }

    /** La copia sul telefono per i promemoria dei farmaci ad app chiusa (MedsWorker). */
    private fun saveMedsSnapshot() {
        val me = session?.userId ?: return
        val rooms = data.values.mapNotNull { r -> Casa.decodeMeds(r.state[Casa.MEDS])?.takeIf { it.by == me || it.user == me }?.let { r.id to it } }
        runCatching { MedsWorker.save(context, account, me, rooms.map { (id, m) -> Triple(id, room(id)?.name.orEmpty(), m) }) }
    }

    // --- Sono arrivato (tavola FamArrivato) ---

    fun arrival(roomId: String): Arrivo.Rule? = Arrivo.rules(context).firstOrNull { it.account == account.name && it.roomId == roomId }

    /** Accende (o spegne, con null) l'avviso di arrivo in questa chat; gli altri vedono che è acceso, mai dove sei. */
    fun saveArrival(roomId: String, rule: Arrivo.Rule?) {
        val me = session?.userId ?: return
        val rest = Arrivo.rules(context).filterNot { it.account == account.name && it.roomId == roomId }
        Arrivo.save(context, rest + listOfNotNull(rule))
        val c = client ?: return
        val content = if (rule == null) JSONObject() else JSONObject().put("on", true).put("places", org.json.JSONArray(rule.places.map { it.name })).put("when", rule.whenever.name)
        scope.launch(Dispatchers.IO) { runCatching { c.setState(roomId, Arrivo.STATE, content, me) } }
    }

    /** Chi ha l'avviso di arrivo acceso in questa chat (e per quali posti). */
    fun arrivalsOn(roomId: String): List<Pair<String, List<String>>> = data[roomId]?.state.orEmpty().entries
        .filter { it.key.startsWith(Arrivo.STATE + "|") && it.value.optBoolean("on") }
        .map { e -> e.key.substringAfter('|') to (e.value.optJSONArray("places")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()) }

    /** Dove sei ora (ultima posizione nota del telefono), per segnare un posto: resta sul telefono. */
    @android.annotation.SuppressLint("MissingPermission")
    fun hereNow(): Pair<Double, Double>? = runCatching {
        val lm = context.getSystemService(android.location.LocationManager::class.java)
        listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }.getOrNull()

    // --- Viaggio di gruppo e regalo collettivo (tavole AmiViaggio, AmiRegalo) ---

    fun trip(roomId: String) = Amici.decodeTrip(data[roomId]?.state?.get(Amici.TRIP))
    fun saveTrip(roomId: String, list: List<Amici.Stop>, failed: () -> Unit) = putState(roomId, Amici.TRIP, Amici.encodeTrip(list), failed)
    fun gift(roomId: String) = Amici.decodeGift(data[roomId]?.state?.get(Amici.GIFT))
    fun saveGift(roomId: String, g: Amici.Gift, failed: () -> Unit) = putState(roomId, Amici.GIFT, Amici.encodeGift(g), failed)

    private val giftMailPrefs get() = context.getSharedPreferences("chat_gift_mail", Context.MODE_PRIVATE)

    /** Agli amici senza Chat la quota per email; rispondono «ho versato» e risultano pagati. */
    fun mailGift(roomId: String, quota: Long, failed: () -> Unit) {
        val me = session?.userId ?: return
        val g = gift(roomId) ?: return
        val todo = g.people.filter { it !in g.paid }
        if (todo.isEmpty()) return
        scope.launch {
            var ok = true
            todo.forEach { p ->
                val address = eu.eurise.core.insieme.Scheda.mailAddress(p) ?: return@forEach
                val id = "r" + System.nanoTime()
                val card = eu.eurise.core.insieme.Scheda.Card(id, eu.eurise.core.insieme.Scheda.Kind.Regalo, context.getString(R.string.rg_for, g.forName),
                    if (g.iban.isNotBlank()) context.getString(R.string.rg_iban, g.iban) else "", from = nameOf(me).substringBefore(' '),
                    amount = Expenses.money(quota), origin = "chat:regalo")
                giftMailPrefs.edit().putString(id, roomId + "|" + p).apply()
                ok = ok && withContext(Dispatchers.IO) { runCatching { eu.eurise.core.insieme.SchedaContract.send(context, card, listOf(address)) }.getOrDefault(false) }
            }
            if (!ok) failed()
        }
    }

    private fun giftPaidByMail(id: String) {
        val (roomId, p) = giftMailPrefs.getString(id, null)?.split('|', limit = 2)?.takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return
        val g = gift(roomId) ?: return
        if (p !in g.paid) saveGift(roomId, g.copy(paid = g.paid + p)) {}
        giftMailPrefs.edit().remove(id).apply()
    }

    // --- Il registro delle decisioni (tavola LavDecisioni) ---

    data class Decision(val id: String, val text: String, val who: String, val at: Long, val source: String, val ref: String)

    private val DECISIONS = "eu.eurise.decisions"

    fun decisions(roomId: String): List<Decision> = data[roomId]?.state?.get(DECISIONS)?.optJSONArray("items")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o ->
            Decision(o.optString("id"), o.optString("text"), o.optString("who"), o.optLong("at"), o.optString("source"), o.optString("ref")).takeIf { it.text.isNotBlank() } } }
    }.orEmpty().sortedByDescending { it.at }

    /** «Deciso»: il messaggio, la frase della riunione o l'email vanno nel registro del gruppo; chi entra dopo trova la storia. */
    fun addDecision(roomId: String, text: String, who: String, source: String, ref: String, failed: () -> Unit) {
        val list = decisions(roomId) + Decision("k" + System.nanoTime(), text.trim().take(300), who, System.currentTimeMillis(), source, ref)
        putState(roomId, DECISIONS, JSONObject().put("items", org.json.JSONArray().apply {
            list.sortedBy { it.at }.takeLast(300).forEach { d -> put(JSONObject().put("id", d.id).put("text", d.text).put("who", d.who).put("at", d.at).put("source", d.source).put("ref", d.ref)) }
        }), failed)
    }

    fun removeDecision(roomId: String, id: String, failed: () -> Unit) {
        val list = decisions(roomId).filterNot { it.id == id }
        putState(roomId, DECISIONS, JSONObject().put("items", org.json.JSONArray().apply {
            list.sortedBy { it.at }.forEach { d -> put(JSONObject().put("id", d.id).put("text", d.text).put("who", d.who).put("at", d.at).put("source", d.source).put("ref", d.ref)) }
        }), failed)
    }

    // --- Traduzione per colleghi stranieri (tavola SpaTraduci) ---

    private val translatePrefs get() = context.getSharedPreferences("chat_translate", Context.MODE_PRIVATE)

    /** Per questa chat: leggere i suoi messaggi in italiano; in che lingua mandare i tuoi (null = in italiano). */
    fun translateRead(roomId: String) = translatePrefs.getBoolean(account.name + "|r|" + roomId, false)
    fun translateSend(roomId: String): String? = translatePrefs.getString(account.name + "|s|" + roomId, null)

    fun setTranslate(roomId: String, read: Boolean, send: String?) {
        translatePrefs.edit().putBoolean(account.name + "|r|" + roomId, read).apply {
            if (send == null) remove(account.name + "|s|" + roomId) else putString(account.name + "|s|" + roomId, send)
        }.apply()
        relationsTick++
    }

    /** Le traduzioni fatte (id del messaggio → testo, lingua), solo in memoria. */
    val translations = androidx.compose.runtime.mutableStateMapOf<String, Pair<String, String>>()
    private val translating = mutableSetOf<String>()

    /** Traduce in italiano un messaggio in un'altra lingua (con Eurise Leggi, sul telefono). */
    fun translateEvent(e: Event, force: Boolean = false, failed: (Int) -> Unit = {}) {
        if (e.id in translations || e.id in translating) return
        val text = shownTextOf(e)
        val lang = eu.eurise.core.data.messages.MessageInsights.language(text) ?: if (force) "en" else return
        if (lang == "it") return
        translating += e.id
        eu.eurise.core.insieme.Traduci.translate(context, text, lang, "it") { r ->
            translating -= e.id
            when (r) {
                is eu.eurise.core.insieme.Traduci.Result.Done -> translations[e.id] = r.text to lang
                eu.eurise.core.insieme.Traduci.Result.Missing -> failed(R.string.tr_missing)
                eu.eurise.core.insieme.Traduci.Result.NoApp -> failed(R.string.tr_no_app)
                eu.eurise.core.insieme.Traduci.Result.Failed -> failed(R.string.tr_failed)
            }
        }
    }

    /** Manda il messaggio nella lingua dell'altro, con l'italiano sotto; se la traduzione non riesce parte in italiano. */
    fun sendTranslated(roomId: String, text: String, to: String, failed: () -> Unit) {
        eu.eurise.core.insieme.Traduci.translate(context, text, "it", to) { r ->
            if (r is eu.eurise.core.insieme.Traduci.Result.Done && r.text.isNotBlank()) send(roomId, r.text.trim() + "\n\n🇮🇹 " + text, failed) else send(roomId, text, failed)
        }
    }

    // --- Spese dall'email e per email (tavola SpeEmail) ---

    /** Una spesa arrivata dall'Email (importo, cosa): si sceglie il gruppo e si apre «Nuova spesa» già compilata. */
    var pendingExpense by mutableStateOf<Pair<Long, String>?>(null)
    /** Una scadenza dall'Email (cosa, importo, giorno) e una domanda per la famiglia (domanda, risposte): si sceglie il gruppo. */
    var pendingDeadline by mutableStateOf<Triple<String, Long, Long>?>(null)
    var pendingAsk by mutableStateOf<Pair<String, List<String>>?>(null)
    var pendingStop by mutableStateOf<Amici.Stop?>(null)
    /** Una decisione presa per email (testo, chi): va nel registro del gruppo scelto. */
    var pendingDecision by mutableStateOf<Pair<String, String>?>(null)

    /** Entrando nel gruppo scelto: la scadenza si aggiunge, la domanda parte come sondaggio. */
    fun usePending(roomId: String) {
        pendingDeadline?.let { (title, cents, day) ->
            pendingDeadline = null
            if (day > 0) saveDeadlines(roomId, deadlines(roomId) + Casa.Deadline("s" + System.nanoTime(), title, if (cents > 0) Casa.Kind.Bolletta else Casa.Kind.Altro, day,
                cents, session?.userId.orEmpty(), source = context.getString(R.string.fs_from_mail), by = session?.userId.orEmpty())) {}
        }
        pendingAsk?.let { (q, opts) -> pendingAsk = null; sendPoll(roomId, q, opts, null) {} }
        pendingStop?.let { st -> pendingStop = null; saveTrip(roomId, trip(roomId) + st.copy(by = session?.userId.orEmpty())) {} }
        pendingDecision?.let { (t, who) -> pendingDecision = null; addDecision(roomId, t, who, "mail", "") {} }
    }

    private val EXPENSE_PEOPLE = "eu.eurise.expense.people"

    /** Chi partecipa alle spese del gruppo senza la Chat: persone «mailto:», ricevono il riepilogo per email. */
    fun expenseMailPeople(roomId: String): List<String> = data[roomId]?.state?.get(EXPENSE_PEOPLE)?.optJSONArray("people")?.let { a ->
        (0 until a.length()).map { a.optString(it) }.filter { eu.eurise.core.insieme.Scheda.isMailPerson(it) }
    }.orEmpty()

    fun addExpenseMailPerson(roomId: String, address: String, failed: () -> Unit) {
        val c = client ?: return
        val p = eu.eurise.core.insieme.Scheda.mailPerson(address)
        val list = (expenseMailPeople(roomId) + p).distinct()
        val content = JSONObject().put("people", org.json.JSONArray(list))
        data[roomId]?.state?.set(EXPENSE_PEOPLE, content)
        relationsTick++
        scope.launch { if (withContext(Dispatchers.IO) { runCatching { c.setState(roomId, EXPENSE_PEOPLE, content) }.isFailure }) failed() }
    }

    private val expenseMailPrefs get() = context.getSharedPreferences("chat_expense_mail", Context.MODE_PRIVATE)

    /** Il riepilogo per email a chi non usa la Chat: l'ultima spesa, la sua quota, il saldo; risponde «ho pagato». */
    fun mailExpenseSummary(roomId: String, failed: () -> Unit) {
        val me = session?.userId ?: return
        val mailPeople = expenseMailPeople(roomId)
        if (mailPeople.isEmpty()) return
        val expenses = expenses(roomId)
        val people = Expenses.people(expenses, settlements(roomId), members[roomId].orEmpty() + mailPeople)
        val last = expenses.lastOrNull()
        val group = room(roomId)?.name.orEmpty()
        scope.launch {
            var ok = true
            mailPeople.forEach { p ->
                val address = eu.eurise.core.insieme.Scheda.mailAddress(p) ?: return@forEach
                val bal = people.firstOrNull { it.id == p }?.balance ?: 0L
                val id = "x" + System.nanoTime()
                val card = eu.eurise.core.insieme.Scheda.Card(id, eu.eurise.core.insieme.Scheda.Kind.Spesa,
                    group + (last?.let { " · " + it.title.ifBlank { "—" } + " " + Expenses.money(it.cents) } ?: ""),
                    context.getString(when { bal < 0 -> R.string.se_you_owe; bal > 0 -> R.string.se_owed; else -> R.string.se_even }, Expenses.money(kotlin.math.abs(bal))),
                    from = nameOf(me).substringBefore(' '), amount = last?.owed?.get(p)?.let { Expenses.money(it) }.orEmpty(), origin = "chat:spese")
                expenseMailPrefs.edit().putString(id, roomId + "|" + p).apply()
                ok = ok && withContext(Dispatchers.IO) { runCatching { eu.eurise.core.insieme.SchedaContract.send(context, card, listOf(address)) }.getOrDefault(false) }
            }
            if (!ok) failed()
        }
    }

    /** «Ho pagato» per email: chi l'ha detto salda quello che deve a te (per conto suo). */
    private fun expensePaidByMail(id: String) {
        val (roomId, p) = expenseMailPrefs.getString(id, null)?.split('|', limit = 2)?.takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return
        val people = Expenses.people(expenses(roomId), settlements(roomId), members[roomId].orEmpty() + expenseMailPeople(roomId))
        // Un pagamento lo segna chi l'ha ricevuto: qui solo quelli verso di te (gli altri li segna chi li riceve).
        val me = session?.userId ?: return
        Expenses.settle(people).filter { it.from == p && it.to == me }.forEach { t ->
            val s = Expenses.Settlement(t.from, t.to, t.cents)
            sendSettlement(roomId, s, context.getString(R.string.se_paid_body, nameOf(p), Expenses.money(t.cents), nameOf(t.to))) {}
        }
        expenseMailPrefs.edit().remove(id).apply()
    }

    // --- Approvazioni (tavola LavApprova) ---

    private val approvalPrefs get() = context.getSharedPreferences("chat_approvals", Context.MODE_PRIVATE)
    /** Le richieste di approvazione mandate da questo account (sul telefono). */
    var approvals by mutableStateOf(Approva.decode(approvalPrefs.getString("list", null)).filter { it.account == account.name })
        private set

    private fun saveApprovals(mine: List<Approva.Request>) {
        val others = Approva.decode(approvalPrefs.getString("list", null)).filter { it.account != account.name }
        approvalPrefs.edit().putString("list", Approva.encode(others + mine)).apply()
        approvals = mine
    }

    /** Manda nella stanza la richiesta: titolo, importo, file citato (dal messaggio da cui parte, se è un file). */
    fun askApproval(roomId: String, title: String, amount: String, file: String, note: String, failed: () -> Unit) {
        val c = client ?: return
        val me = session?.userId ?: return
        val id = "a" + System.nanoTime()
        val content = Approva.content(id, nameOf(me), title, amount, file, note, context.getString(R.string.ap_fallback))
        val encrypted = data[roomId]?.encrypted == true
        scope.launch {
            val ev = withContext(Dispatchers.IO) {
                runCatching { ProgrammaStore.sendNow(c, if (encrypted) cryptoFor(c.session) else null, roomId, content, encrypted) }.getOrNull()
            }
            if (ev == null) { failed(); return@launch }
            val name = rooms.firstOrNull { it.id == roomId }?.name.orEmpty()
            saveApprovals(approvals + Approva.Request(id, account.name, roomId, name, title.trim(), amount.trim(), System.currentTimeMillis(), ev))
        }
    }

    /** Approva, Chiedi modifica, Rifiuta: la risposta si riferisce alla richiesta. */
    fun answerApproval(roomId: String, e: Event, id: String, verdict: Approva.Verdict, note: String, failed: () -> Unit) {
        val body = context.getString(when (verdict) {
            Approva.Verdict.Approved -> R.string.ap_body_ok
            Approva.Verdict.Change -> R.string.ap_body_change
            Approva.Verdict.Rejected -> R.string.ap_body_no
        })
        sendContent(roomId, Approva.answerContent(e.id, id, verdict, note, body), Event.Kind.Text, failed)
    }

    /** «Sollecita»: una risposta gentile alla richiesta. */
    fun remindApproval(id: String, failed: () -> Unit) {
        val r = approvals.firstOrNull { it.id == id } ?: return
        if (r.eventId.isEmpty()) return
        reply(r.room, r.eventId, context.getString(R.string.ap_remind_body, r.title), failed)
        saveApprovals(approvals.map { if (it.id == id) it.copy(sentAt = System.currentTimeMillis()) else it })
    }

    fun forgetApproval(id: String) = saveApprovals(approvals.filterNot { it.id == id })

    /** L'ultimo verdetto di ogni richiesta, dalle risposte arrivate (di chiunque altro nella stanza). */
    private fun refreshApprovals() {
        if (approvals.isEmpty()) return
        val me = session?.userId
        val answers = HashMap<String, Approva.Answer>()
        approvals.map { it.room }.distinct().forEach { room ->
            timelines[room].orEmpty().forEach { e ->
                if (e.sender == me) return@forEach
                Approva.answer(e.data?.let { runCatching { JSONObject(it) }.getOrNull() })?.let { (id, v, note) ->
                    if ((answers[id]?.at ?: 0L) <= e.time) answers[id] = Approva.Answer(v, e.sender, note, e.time)
                }
            }
        }
        if (answers.isEmpty()) return
        val next = Approva.update(approvals, answers)
        if (next != approvals) saveApprovals(next)
    }

    // --- Faccende a turno (tavola FamFaccende) ---

    fun chores(roomId: String): List<Faccende.Chore> = Faccende.decode(data[roomId]?.state?.get(Faccende.TYPE))

    private fun saveChores(roomId: String, list: List<Faccende.Chore>, failed: () -> Unit) {
        val c = client ?: return
        val content = Faccende.encode(list)
        val s = data[roomId]
        val before = s?.state?.get(Faccende.TYPE)
        s?.state?.set(Faccende.TYPE, content)
        relationsTick++
        saveChoresSnapshot()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { c.setState(roomId, Faccende.TYPE, content) }.isSuccess }
            if (!ok) { if (before != null) s?.state?.set(Faccende.TYPE, before) else s?.state?.remove(Faccende.TYPE); relationsTick++; failed() }
        }
    }

    fun addChore(roomId: String, title: String, days: Set<Int>, every: Int, time: String, people: List<String>, failed: () -> Unit) {
        val me = session?.userId ?: return
        val chore = Faccende.Chore("f" + System.nanoTime(), title.trim(), days, every, java.time.LocalDate.now().toEpochDay(), time, people, by = me)
        saveChores(roomId, chores(roomId) + chore, failed)
    }

    fun removeChore(roomId: String, id: String, failed: () -> Unit) = saveChores(roomId, chores(roomId).filterNot { it.id == id }, failed)

    /** «Fatto»: il turno passa al prossimo; nella chat una riga breve per chi segue. */
    fun choreDone(roomId: String, id: String, failed: () -> Unit) {
        val me = session?.userId ?: return
        val list = chores(roomId)
        val c = list.firstOrNull { it.id == id } ?: return
        val next = Faccende.done(c, me, System.currentTimeMillis())
        saveChores(roomId, list.map { if (it.id == id) next else it }, failed)
        Faccende.whose(next)?.let { who -> sendContent(roomId, JSONObject().put("msgtype", "m.notice")
            .put("body", context.getString(R.string.fa_done_notice, c.title, nameOf(who).substringBefore(' '))), Event.Kind.Notice, {}) }
    }

    /** Scambio: «Mi fai tu la spazzatura oggi?» a chi viene dopo; chi accetta tocca «Faccio io» (segna fatta al posto tuo). */
    fun askChoreSwap(roomId: String, id: String, failed: () -> Unit) {
        val c = chores(roomId).firstOrNull { it.id == id } ?: return
        val other = Faccende.next(c) ?: return
        send(roomId, context.getString(R.string.fa_swap_body, nameOf(other).substringBefore(' '), c.title.lowercase()), failed)
    }

    /** La copia sul telefono per i promemoria ad app chiusa. */
    private fun saveChoresSnapshot() {
        val me = session?.userId ?: return
        val rooms = data.values.mapNotNull { r -> Faccende.decode(r.state[Faccende.TYPE]).takeIf { it.isNotEmpty() }?.let { r.id to ((rooms.firstOrNull { x -> x.id == r.id }?.name ?: "") to it) } }.toMap()
        runCatching { FaccendeWorker.save(context, account, me, rooms) }
    }

    // --- Organizza un'uscita (tavola AmiUscita) ---

    /** [emails]: chi ha solo l'email riceve la stessa proposta da Eurise Email e vota rispondendo (tavola InsStessaScheda). */
    fun createOuting(roomId: String, title: String, options: List<Uscita.Option>, place: String, address: String, closes: Long,
                     emails: List<String> = emptyList(), mailFailed: () -> Unit = {}, failed: () -> Unit) {
        val me = session?.userId ?: return
        val id = "u" + System.nanoTime()
        val content = Uscita.content(id, nameOf(me), title, options, place, address, closes, context.getString(R.string.us_fallback, nameOf(me)))
        sendEvent(roomId, "m.room.message", content, failed)
        if (emails.isEmpty()) return
        val card = eu.eurise.core.insieme.Scheda.Card(id, eu.eurise.core.insieme.Scheda.Kind.Uscita, title.trim(),
            listOf(place, address).filter { it.isNotBlank() }.joinToString(", "), from = nameOf(me).substringBefore(' '),
            options = options.map { it.label }, origin = "chat:$roomId")
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { eu.eurise.core.insieme.SchedaContract.send(context, card, emails) }.getOrDefault(false) }
            if (!ok) mailFailed()
        }
    }

    /** Una risposta all'uscita (date, passaggio, «Fissa»): per chi usa Element, una riga di testo. */
    fun sayOuting(roomId: String, e: Event, id: String, say: Uscita.Say, body: String, failed: () -> Unit) =
        sendContent(roomId, Uscita.answer(e.id, id, say, body), Event.Kind.Notice, failed)

    /** Lo stato dell'uscita dalle risposte nella stanza (in ordine). */
    fun outingState(roomId: String, e: Event, card: Uscita.Card): Uscita.State {
        val answers = timeline(roomId).mapNotNull { ev ->
            if (ev.kind != Event.Kind.Notice || ev.data?.contains(Uscita.ANSWER) != true) return@mapNotNull null
            val json = runCatching { JSONObject(ev.data) }.getOrNull()
            // Le date scelte per email: le manda il telefono di chi ha proposto, per conto di chi ha risposto.
            val who = Uscita.forWho(json)?.takeIf { ev.sender == e.sender } ?: ev.sender
            Uscita.parse(json)?.takeIf { it.first == card.id }?.let { who to it.second }
        }
        return Uscita.state(card, e.sender, answers)
    }

    // --- Gruppi a tempo (tavola ChatGruppoTempo) ---

    fun timebox(roomId: String): Tempo.Box? = Tempo.decode(data[roomId]?.state?.get(Tempo.TYPE))

    /** Mette (o toglie, con null) il periodo del gruppo: uno stato della stanza, lo vedono tutti. */
    fun setTimebox(roomId: String, box: Tempo.Box?, failed: () -> Unit) {
        val c = client ?: return
        val content = box?.let(Tempo::encode) ?: JSONObject()
        val s = data[roomId]
        val before = s?.state?.get(Tempo.TYPE)
        s?.state?.set(Tempo.TYPE, content)
        relationsTick++
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { c.setState(roomId, Tempo.TYPE, content) }.isSuccess }
            if (!ok) { if (before != null) s?.state?.set(Tempo.TYPE, before) else s?.state?.remove(Tempo.TYPE); relationsTick++; failed() }
        }
    }

    /** I gruppi a tempo tenuti aperti oltre la scadenza (su questo telefono). */
    var keptOpen by mutableStateOf(simplePrefs.getStringSet("keep_" + account.name, emptySet()).orEmpty())
        private set

    fun keepOpen(roomId: String) {
        keptOpen = keptOpen + roomId
        simplePrefs.edit().putStringSet("keep_" + account.name, keptOpen).apply()
    }

    /** I gruppi a tempo scaduti: stanno nell'archivio dell'elenco. */
    fun archived(): Set<String> {
        val today = java.time.LocalDate.now()
        return data.values.filter { it.id !in keptOpen && Tempo.decode(it.state[Tempo.TYPE])?.let { b -> Tempo.archived(b, today) } == true }.map { it.id }.toSet()
    }

    // --- Modalità semplice (tavola ChatSemplice) ---

    private val simplePrefs get() = context.getSharedPreferences("chat_ui", Context.MODE_PRIVATE)
    /** Per account: accesa, le persone (al più quattro), chi riceve «Aiuto». */
    var simple by mutableStateOf(Semplice.decode(simplePrefs.getString("simple_" + account.name, null)))
        private set

    fun changeSimple(c: Semplice.Config) {
        simple = c
        simplePrefs.edit().putString("simple_" + account.name, Semplice.encode(c)).apply()
    }

    /** «Aiuto»: un messaggio alla persona scelta e, se si può, la posizione dal vivo per un'ora. */
    fun askHelp(withLocation: Boolean, done: (Boolean) -> Unit) {
        val room = simple.help ?: return done(false)
        val me = session?.userId.orEmpty()
        send(room, context.getString(R.string.sm_help_text, Semplice.greetingName(simple, me))) { done(false) }
        if (withLocation) startLive(room, Posizione.Until.Hour) {}
        done(true)
    }

    // --- Presa visione (tavola ChatPresaVisione) ---

    /** Per stanza: fino a quando ha letto ognuno (conferme di lettura). */
    val readers = mutableStateMapOf<String, Map<String, Long>>()

    /** Manda [text] chiedendo la presa visione (gli altri confermano con ✅, anche da Element). */
    fun sendAck(roomId: String, text: String, failed: () -> Unit) {
        sendContent(roomId, Ack.content(text, context.getString(R.string.pv_fallback)), Event.Kind.Text, failed)
        loadMembers(roomId)
    }

    /** «Ricevuto»: la reazione ✅. */
    fun confirmAck(roomId: String, e: Event, failed: () -> Unit) {
        if (session?.userId in relations.reactors(e.id, Ack.KEY)) return
        react(roomId, e.id, Ack.KEY, failed)
    }

    /** I membri della stanza, se non ci sono già. */
    fun loadMembers(roomId: String) {
        if (members.containsKey(roomId)) return
        val c = client ?: return
        scope.launch { withContext(Dispatchers.IO) { runCatching { c.joinedMembers(roomId) }.getOrNull() }?.let { members[roomId] = it.sorted() } }
    }

    /** «Ricorda a chi manca»: una risposta al messaggio con i nomi di chi non ha ancora confermato. */
    fun remindAck(roomId: String, e: Event, missing: List<String>, failed: () -> Unit) {
        if (missing.isEmpty()) return
        reply(roomId, e.id, context.getString(R.string.pv_remind_text, missing.joinToString(", ") { nameOf(it) }), failed)
    }

    // --- Difesa dalle truffe (tavola ChatTruffa) ---

    /**
     * Blocca chi ha scritto: lo si ignora (lista `m.ignored_user_list` dell'account, come Element: i suoi messaggi e
     * inviti non arrivano più) e si esce dalla chat.
     */
    fun block(roomId: String, userId: String, done: (Boolean) -> Unit) {
        val c = client ?: return done(false)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val cur = runCatching { c.accountData("m.ignored_user_list") }.getOrNull() ?: JSONObject()
                    val users = cur.optJSONObject("ignored_users") ?: JSONObject()
                    users.put(userId, JSONObject())
                    c.putAccountData("m.ignored_user_list", JSONObject().put("ignored_users", users))
                    c.leave(roomId)
                }.isSuccess
            }
            if (ok) open(null)
            done(ok)
        }
    }

    // --- Posizione dal vivo a tempo (tavola ChatPosizione) ---

    /** La condivisione accesa (una alla volta), o null. */
    var live by mutableStateOf(LiveShare.active(context))
        private set

    fun refreshLive() { live = LiveShare.active(context) }

    /**
     * Accende la posizione dal vivo in [roomId] per la scelta [until]: lo stato `beacon_info` (senza posizioni), poi il
     * servizio in primo piano con l'avviso fisso. Una condivisione già accesa altrove si spegne prima.
     */
    fun startLive(roomId: String, until: Posizione.Until, failed: () -> Unit) {
        val c = client ?: return
        val me = session?.userId ?: return
        val now = System.currentTimeMillis()
        val duration = Posizione.duration(until, java.time.LocalDateTime.now())
        val name = room(roomId)?.name ?: roomId
        val myName = data[roomId]?.names?.get(me) ?: Matrix.localpart(me)
        if (live != null) LiveShare.stop(context)
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { c.setState(roomId, Posizione.INFO, Posizione.infoContent(myName, now, duration, true), me) }.getOrNull()
            }
            if (id.isNullOrEmpty()) { failed(); return@launch }
            LiveShare.start(context, LiveShare.Active(account.name, roomId, name, id, now + duration, until == Posizione.Until.Arrival,
                data[roomId]?.encrypted == true))
            refreshLive()
        }
    }

    fun stopLive() { LiveShare.stop(context); live = null }

    /** L'ultimo punto di una condivisione (o la posizione di un messaggio con una posizione sola). */
    fun lastPosition(roomId: String, e: Event): Posizione.Pos? {
        val points = timeline(roomId).filter { it.kind == Event.Kind.Beacon && it.ref == e.id }
            .mapNotNull { b -> Posizione.pos(b.data?.let { runCatching { JSONObject(it) }.getOrNull() })?.let { p -> if (p.ts > 0) p else p.copy(ts = b.time) } }
        return points.maxByOrNull { it.ts } ?: Posizione.pos(e.data?.let { runCatching { JSONObject(it) }.getOrNull() })?.takeIf { it.lat != 0.0 || it.lon != 0.0 }
    }

    // --- Cartelle e «Messaggi a me» (tavola ChatCartelle) ---

    private val folderPrefs get() = context.getSharedPreferences("chat_folders", Context.MODE_PRIVATE)
    /** Le cartelle di questo account, sul telefono. */
    var folders by mutableStateOf<List<Cartelle.Folder>>(emptyList())
        private set
    /** La cartella scelta nell'elenco (null = Tutte). */
    var folder by mutableStateOf<String?>(null)
    /** La stanza «Messaggi a me» (null finché non si usa la prima volta). */
    var selfRoom by mutableStateOf<String?>(null)
        private set

    private fun startFolders() {
        folders = Cartelle.decode(folderPrefs.getString(account.name, null))
        if (folder != ARCHIVE && folders.none { it.id == folder }) folder = null
        val c = client ?: return
        scope.launch {
            selfRoom = withContext(Dispatchers.IO) { runCatching { Cartelle.selfRoom(c.accountData(Cartelle.SELF)) }.getOrNull() }
        }
    }

    private fun saveFolders(list: List<Cartelle.Folder>) {
        folders = list
        folderPrefs.edit().putString(account.name, Cartelle.encode(list)).apply()
    }

    /** Nuova cartella; null se il nome è vuoto o c'è già. */
    fun addFolder(name: String, rooms: Set<String> = emptySet()): String? {
        if (Cartelle.cleanName(folders, name) == null) return null
        val id = "c" + System.nanoTime()
        saveFolders(Cartelle.add(folders, name, id, rooms))
        return id
    }

    fun renameFolder(id: String, name: String): Boolean {
        if (Cartelle.cleanName(folders, name, except = id) == null) return false
        saveFolders(Cartelle.rename(folders, id, name)); return true
    }

    fun removeFolder(id: String) { saveFolders(Cartelle.remove(folders, id)); if (folder == id) folder = null }

    fun toggleFolder(id: String, roomId: String) = saveFolders(Cartelle.toggle(folders, id, roomId))

    /** Apre «Messaggi a me», creandola la prima volta (stanza cifrata con dentro solo te). */
    fun openSelf(failed: () -> Unit) {
        selfRoom?.let { if (data.containsKey(it)) { open(it); return } }
        val c = client ?: return
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    // Prima si guarda se un altro telefono l'ha già creata.
                    Cartelle.selfRoom(c.accountData(Cartelle.SELF)) ?: c.createGroup(context.getString(R.string.cr_self), emptyList()).also {
                        c.putAccountData(Cartelle.SELF, JSONObject().put("room_id", it))
                    }
                }.getOrNull()
            }
            if (id == null) failed() else { selfRoom = id; open(id) }
        }
    }

    /** Il pacchetto dell'app Eurise e il permesso della suite (prova o ufficiale). */
    private fun hub(): Pair<String, String> {
        val debug = context.packageName.endsWith(".debug")
        return (if (debug) "eu.eurise.hub.debug" else "eu.eurise.hub") to (if (debug) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE")
    }

    /** Da «Messaggi a me» a Note o ad Attività (lì si capiscono date e ore: «pane domani alle 8»). Falso senza l'app Eurise. */
    fun sendToHub(text: String, task: Boolean): Boolean {
        val (pkg, permission) = hub()
        return eu.eurise.core.suite.HubBridge.send(context, pkg, permission,
            if (task) eu.eurise.core.suite.HubBridge.ACTION_TASK else eu.eurise.core.suite.HubBridge.ACTION_NOTE, text)
    }

    // --- Invia più tardi, quando lavora, a scomparsa (tavola ChatProgramma) ---

    private var planLoop: Job? = null
    /** I messaggi programmati di questo account, in attesa sul telefono. */
    var planned by mutableStateOf<List<Programma.Planned>>(emptyList())
        private set
    /** I messaggi a scomparsa mandati da qui (per l'etichetta «sparisce…»). */
    var fading by mutableStateOf<Map<String, Programma.Fading>>(emptyMap())
        private set

    private fun refreshPlans() {
        planned = ProgrammaStore.planned(context).filter { it.account == account.name }.sortedBy { it.at }
        fading = ProgrammaStore.fading(context).filter { it.account == account.name }.associateBy { it.eventId }
    }

    /** Ad app aperta, ogni 30 secondi: i programmati arrivati all'ora partono, quelli a scomparsa scaduti si cancellano. */
    private fun startPlans() {
        planLoop?.cancel()
        refreshPlans()
        planLoop = scope.launch {
            while (isActive) {
                val c = client
                if (c != null) {
                    withContext(Dispatchers.IO) {
                        val cr = if (ProgrammaStore.planned(context).any { it.account == account.name && it.encrypted }) cryptoFor(c.session) else null
                        runCatching { ProgrammaStore.work(context, account, c, cr) }
                    }
                    refreshPlans()
                    refreshBalls()
                    refreshApprovals()
                    refreshGiven()
                    refreshMailAnswers()
                    refreshSlots()
                    refreshAgendas()
                    refreshDinner()
                    saveMedsSnapshot()
                    saveChoresSnapshot()
                    if (shareHours) publishHours(all = false)
                }
                delay(30_000)
            }
        }
    }

    /** Programma [text] per l'ora [at]; [work] per l'etichetta «quando lavora», [fadeMs] anche a scomparsa. */
    fun plan(roomId: String, text: String, at: Long, work: Boolean = false, fadeMs: Long = 0) {
        if (text.isBlank()) return
        ProgrammaStore.addPlanned(context, Programma.Planned("p" + System.nanoTime(), account.name, roomId, text.trim(), at,
            data[roomId]?.encrypted == true, fadeMs, work))
        refreshPlans()
    }

    fun cancelPlanned(id: String) { ProgrammaStore.removePlanned(context, id); refreshPlans() }

    /** Il programmato parte adesso (eco locale come un messaggio normale). */
    fun sendPlannedNow(id: String, failed: () -> Unit) {
        val p = planned.firstOrNull { it.id == id } ?: return
        cancelPlanned(id)
        if (p.fadeMs > 0) sendFading(p.roomId, p.text, failed) else send(p.roomId, p.text, failed)
    }

    /** Un messaggio a scomparsa: parte adesso e, un giorno dopo che l'altro l'ha letto, lo cancella dal server chi l'ha mandato. */
    fun sendFading(roomId: String, text: String, failed: () -> Unit, fadeMs: Long = Programma.FADE_MS) {
        val c = client ?: return
        val me = session?.userId.orEmpty()
        val content = Programma.content(text.trim(), fadeMs)
        val local = Event("local:" + System.nanoTime(), me, text.trim(), System.currentTimeMillis(), Event.Kind.Text, data = content.toString())
        timeline(roomId).add(local)
        val encrypted = data[roomId]?.encrypted == true
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { ProgrammaStore.sendNow(c, if (encrypted) cryptoFor(c.session) else null, roomId, content, encrypted) }.getOrNull()
            }
            if (id == null) { timeline(roomId).remove(local); failed(); return@launch }
            ProgrammaStore.addFading(context, Programma.Fading(account.name, roomId, id, System.currentTimeMillis(), fadeMs))
            refreshPlans()
        }
    }

    /**
     * Ricordamelo (tavola ChatProgramma): un'attività in Attività dell'app Eurise per l'ora [at], con chi l'ha scritto, il
     * testo e il link al messaggio. Falso se l'app Eurise non c'è.
     */
    fun remind(roomId: String, e: Event, at: java.time.LocalDateTime): Boolean {
        val (hub, permission) = hub()
        val who = nameOf(e.sender)
        val text = e.text.replace('\n', ' ').trim().let { if (it.length > 80) it.take(79) + "…" else it }
        val title = context.getString(R.string.pr_remind_title, who, text)
        val link = "eurisechat://messaggio?account=${account.name}&room=${Uri.encode(roomId)}&event=${Uri.encode(e.id)}"
        val notes = context.getString(R.string.pr_remind_notes, room(roomId)?.name ?: who) + "\n" + link
        return eu.eurise.core.suite.HubBridge.send(context, hub, permission, eu.eurise.core.suite.HubBridge.ACTION_TASK, title,
            due = at.toLocalDate(), time = at.toLocalTime(), notes = notes)
    }

    // --- Due account (tavole ChatDueAccount e ChatAccountAggiungi) ---

    private var otherLoop: Job? = null

    /** Ogni due minuti, ad app aperta, quante non lette ha l'altro account (solo il numero, niente messaggi). */
    private fun startOther() {
        otherLoop?.cancel()
        val other = SessionStore(context, account.other).load() ?: run { otherUnread = null; return }
        otherLoop = scope.launch {
            while (isActive) {
                withContext(Dispatchers.IO) { runCatching { ChatClient(other).unreadTotal() }.getOrNull() }?.let { otherUnread = it }
                delay(120_000)
            }
        }
    }

    /**
     * Passa all'altro account: si ferma la sincronizzazione, si svuota ciò che è in memoria (stanze, messaggi, chiavi
     * aperte) e si riparte con la sessione dell'altro; senza sessione si apre l'accesso, per aggiungerlo.
     */
    fun switchTo(a: Account) {
        if (a == account) return
        stop()
        clearMemory()
        account = a
        ChatAccounts.setActive(context, a)
        simple = Semplice.decode(simplePrefs.getString("simple_" + a.name, null))
        shareHours = simplePrefs.getBoolean("hours_" + a.name, false)
        keptOpen = simplePrefs.getStringSet("keep_" + a.name, emptySet()).orEmpty()
        balls = Palla.decode(ballPrefs.getString("list", null)).filter { it.account == a.name }
        given = Affida.decode(givenPrefs.getString("list", null)).filter { it.account == a.name }
        approvals = Approva.decode(approvalPrefs.getString("list", null)).filter { it.account == a.name }
        store = SessionStore(context, a)
        session = store.load()
        keptInSuite = store.keptInSuite
        accounts = ChatAccounts.signedIn(context)
        otherSession = SessionStore(context, a.other).load()
        if (session != null) start()
    }

    private fun clearMemory() {
        since = null
        rooms.clear(); data.clear(); timelines.clear()
        people.clear()
        crypto = null
        pending.clear()
        security = null
        verify = null
        verifyJob?.cancel()
        verifyPeers.clear()
        verifiedPeople.clear()
        openRoom = null
        studioStack.clear()
        ready = false
        error = null
        otherUnread = null
    }

    /** Fuori orario il lavoro tace (impostazione del foglio degli account). */
    @set:JvmName("assignWorkQuiet")
    var workQuiet by mutableStateOf(ChatAccounts.workQuiet(context))
        private set

    fun changeWorkQuiet(on: Boolean) { workQuiet = on; ChatAccounts.setWorkQuiet(context, on) }

    /** Fuori orario (tavola SpaFuoriOrario): orario di lavoro e chi passa lo stesso. */
    var offRules by mutableStateOf(ChatAccounts.rules(context))
        private set

    fun changeOffRules(r: FuoriOrario.Rules) {
        offRules = r
        ChatAccounts.setRules(context, r)
        if (shareHours) publishHours(all = true)
    }

    // --- Studio medico (tavole StuSpazio e StuPaziente) ---

    /** Le pagine dello Studio aperte, una sopra l'altra; vuoto = Studio chiuso. */
    private val studioStack = mutableStateListOf<StudioPage>()
    val studioPage: StudioPage? get() = studioStack.lastOrNull()

    fun studioGo(page: StudioPage) { studioStack += page }
    fun studioBack() { if (studioStack.isNotEmpty()) studioStack.removeAt(studioStack.lastIndex) }
    fun studioClose() = studioStack.clear()
    /** Apre lo Studio da capo (da un'altra app o dalla chat), sulla scheda di [patientId] se c'è. */
    fun studioOpen(patientId: String? = null) {
        openRoom = null
        studioStack.clear()
        studioStack += StudioPage.Home
        if (patientId != null) studioStack += StudioPage.Card(patientId)
    }

    /** Scritture dello Studio fuori dal thread principale. */
    fun studioIo(block: () -> Unit) { scope.launch(Dispatchers.IO) { block() } }

    /** Lo Studio si legge dal disco fuori dal thread principale, una volta sola. */
    fun loadStudio() { scope.launch(Dispatchers.IO) { StudioRepo.load(context) } }

    /**
     * I messaggi dei pazienti entrano nella coda dello Studio; un codice di verifica lega chi scrive alla sua scheda e
     * riceve una conferma. Solo i messaggi degli altri, solo con lo Studio acceso.
     */
    private fun studio(roomId: String, events: List<Event>) {
        val me = session?.userId ?: return
        val list = events.filter { it.sender != me && !it.id.startsWith("local:") }.mapNotNull { e -> studioText(e)?.let { e to it } }
        if (list.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            if (!StudioRepo.current(context).enabled) return@launch
            list.forEach { (e, text) ->
                val out = StudioRepo.receive(context, eu.eurise.core.studio.Channel.Chat, e.sender, text, e.time, "$roomId|${e.id}")
                if (out is eu.eurise.core.studio.Queue.Outcome.Verified) {
                    withContext(Dispatchers.Main) { send(roomId, context.getString(R.string.st_verified_reply)) { } }
                }
            }
        }
    }

    // --- La mia Bussola (tavole Bus*) ---

    /** Chi sono io nella stanza: lo studio, il paziente, o null se non è una stanza di La mia Bussola. */
    enum class BussolaRole { Studio, Patient }

    /** Le stanze di cui si è già letta tutta la storia (stati delle ricette, orari, terapia). */
    private val bussolaLoaded = HashSet<String>()

    fun bussolaItems(roomId: String): List<eu.eurise.core.studio.Bussola.Item> =
        timeline(roomId).filter { it.kind == Event.Kind.Bussola && it.data != null && !it.id.startsWith("local:") }.mapNotNull { e ->
            runCatching { eu.eurise.core.studio.Bussola.Item(e.id, e.sender, e.time, JSONObject(e.data!!)) }.getOrNull()
        }

    fun bussolaRole(roomId: String): BussolaRole? {
        val studio = eu.eurise.core.studio.Bussola.studioSender(bussolaItems(roomId)) ?: return null
        return if (studio == session?.userId) BussolaRole.Studio else BussolaRole.Patient
    }

    /** Una stanza con messaggi di Bussola: si legge tutta la storia una volta, e si ricorda per le notifiche neutre. */
    private fun bussolaSeen(roomId: String, fresh: List<Event>) {
        if (fresh.none { it.kind == Event.Kind.Bussola }) return
        if (bussolaLoaded.add(roomId)) {
            scope.launch(Dispatchers.IO) { BussolaRooms.add(context, roomId) }
            loadExpenses(roomId)
        }
    }

    fun sendBussola(roomId: String, content: JSONObject, failed: () -> Unit) = sendContent(roomId, content, Event.Kind.Bussola, failed)

    /**
     * Apre La mia Bussola nella chat con il paziente: orari e contatti dello studio e la sua terapia abituale (cifrati).
     * Da qui in poi il paziente vede i pulsanti grandi e lo stato dei rinnovi.
     */
    fun bussolaActivate(roomId: String, info: eu.eurise.core.studio.Bussola.StudioInfo, therapy: List<String>, failed: () -> Unit) {
        bussolaLoaded.add(roomId)
        scope.launch(Dispatchers.IO) { BussolaRooms.add(context, roomId) }
        sendBussola(roomId, eu.eurise.core.studio.Bussola.studio(info), failed)
        sendBussola(roomId, eu.eurise.core.studio.Bussola.therapy(therapy), failed)
    }

    // --- Promemoria di Millewin (tavola StuMillewin) ---

    private val millewinPrefs get() = context.getSharedPreferences("studio_millewin", Context.MODE_PRIVATE)

    /** La cartella dove Millewin (o la sincronizzazione del cloud dello studio) lascia i promemoria PDF. */
    var memoFolder: android.net.Uri?
        get() = millewinPrefs.getString("folder", null)?.let(android.net.Uri::parse)
        set(v) { millewinPrefs.edit().putString("folder", v?.toString()).apply() }

    private fun memoOf(uri: android.net.Uri): eu.eurise.core.studio.Millewin.Memo? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        eu.eurise.core.studio.Millewin.parse(eu.eurise.core.pdf.PdfText.extract(bytes, maxPages = 4))
    }.getOrNull()

    /** Un promemoria scelto a mano (PDF): codice NRE e codici fiscali, o null se non si legge. */
    fun readMemo(uri: android.net.Uri, done: (eu.eurise.core.studio.Millewin.Memo?) -> Unit) {
        scope.launch { done(withContext(Dispatchers.IO) { memoOf(uri) }) }
    }

    /**
     * Cerca nella cartella dei promemoria quello del paziente per questa richiesta: solo i PDF degli ultimi 14 giorni
     * (al massimo 40, i più recenti), abbinati per codice fiscale (o nome) e farmaci. Tutto sul telefono.
     */
    fun findMemo(fiscalCode: String?, name: String, drugs: List<String>, done: (eu.eurise.core.studio.Millewin.Memo?) -> Unit) {
        val tree = memoFolder ?: return done(null)
        scope.launch {
            done(withContext(Dispatchers.IO) {
                runCatching {
                    val cr = context.contentResolver
                    val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
                    val since = System.currentTimeMillis() - 14 * 86_400_000L
                    val found = mutableListOf<Pair<Long, String>>()
                    cr.query(children, arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                        android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        android.provider.DocumentsContract.Document.COLUMN_SIZE,
                    ), null, null, null)?.use { c ->
                        while (c.moveToNext()) {
                            val mime = c.getString(1).orEmpty()
                            val modified = if (c.isNull(2)) 0L else c.getLong(2)
                            val size = if (c.isNull(3)) 0L else c.getLong(3)
                            if (mime == "application/pdf" && modified >= since && size in 1..5_000_000) found += modified to c.getString(0)
                        }
                    }
                    val memos = found.sortedByDescending { it.first }.take(40).mapNotNull { (_, id) ->
                        memoOf(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                    eu.eurise.core.studio.Millewin.best(memos, fiscalCode, name, drugs)
                }.getOrNull()
            })
        }
    }

    /** La chat a due con [userId], se c'è già. */
    fun directWith(userId: String): String? = rooms.firstOrNull { !it.invite && peerOf(it.id).equals(userId, ignoreCase = true) }?.id

    /** I messaggi decifrati in ritardo (la chiave è arrivata dopo) prendono il posto di quelli «cifrati». */
    private fun resolve(solved: List<Pair<String, JSONObject>>) {
        if (solved.isEmpty()) return
        solved.forEach { (roomId, json) ->
            relate(roomId, org.json.JSONArray().put(json))
            val e = Matrix.event(json, data[roomId]?.names.orEmpty()) ?: return@forEach
            val list = timeline(roomId)
            val i = list.indexOfFirst { it.id == e.id }
            if (i >= 0) list[i] = e
            studio(roomId, listOf(e))
            bussolaSeen(roomId, listOf(e))
        }
        publish()
    }

    private fun apply(json: JSONObject) {
        val me = session?.userId.orEmpty()
        json.optJSONObject("account_data")?.optJSONArray("events")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                if (e.optString("type") == "m.direct") {
                    val c = e.optJSONObject("content") ?: continue
                    c.keys().forEach { user -> c.optJSONArray(user)?.let { ids -> for (j in 0 until ids.length()) data.getOrPut(ids.getString(j)) { RoomState(ids.getString(j)) }.direct = true } }
                }
            }
        }
        val roomsJson = json.optJSONObject("rooms") ?: JSONObject()
        roomsJson.optJSONObject("join")?.let { join ->
            join.keys().forEach { id ->
                val r = join.getJSONObject(id)
                val s = data.getOrPut(id) { RoomState(id) }
                s.invite = false
                r.optJSONObject("summary")?.optJSONArray("m.heroes")?.let { h -> s.heroes = (0 until h.length()).map { h.getString(it) } }
                val stateEvents = r.optJSONObject("state")?.optJSONArray("events")
                val tl = r.optJSONObject("timeline")
                val tlEvents = tl?.optJSONArray("events")
                listOfNotNull(stateEvents, tlEvents).forEach { a ->
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        if (!e.has("state_key")) continue
                        val type = e.optString("type")
                        val content = e.optJSONObject("content") ?: JSONObject()
                        when (type) {
                            "m.room.member" -> {
                                val user = e.optString("state_key")
                                content.optString("displayname").takeIf { it.isNotBlank() }?.let { s.names[user] = it; people[user] = it }
                            }
                            "m.room.encryption" -> s.encrypted = true
                            // L'orario di lavoro di ognuno (tavola ChatQuandoLavora), con la chiave del suo id.
                            Orari.TYPE -> s.state[Orari.TYPE + "|" + e.optString("state_key")] = content
                            // L'agenda condivisa con l'assistente (tavola InsAssistente), con la chiave di chi la condivide.
                            Assistente.TYPE -> s.state[Assistente.TYPE + "|" + e.optString("state_key")] = content
                            Arrivo.STATE -> s.state[Arrivo.STATE + "|" + e.optString("state_key")] = content
                            else -> if (e.optString("state_key").isEmpty()) s.state[type] = content
                        }
                    }
                }
                s.unread = r.optJSONObject("unread_notifications")?.optInt("notification_count") ?: s.unread
                val list = timeline(id)
                if (tl?.optBoolean("limited") == true && list.isNotEmpty()) list.clear()
                if (list.isEmpty()) s.prevBatch = tl?.optString("prev_batch")?.ifEmpty { null } ?: s.prevBatch
                relate(id, tlEvents)
                val fresh = Matrix.events(tlEvents, s.names).filter { ev -> list.none { it.id == ev.id } && !relations.redacted(ev.id) }
                // L'eco locale di un messaggio inviato da qui lascia il posto a quello vero.
                if (fresh.isNotEmpty()) list.removeAll { it.id.startsWith("local:") && fresh.any { f -> f.sender == me && f.text == it.text } }
                list.addAll(fresh)
                studio(id, fresh)
                bussolaSeen(id, fresh)
            }
        }
        roomsJson.optJSONObject("invite")?.let { inv ->
            inv.keys().forEach { id ->
                val s = data.getOrPut(id) { RoomState(id) }
                s.invite = true
                val a = inv.getJSONObject(id).optJSONObject("invite_state")?.optJSONArray("events")
                if (a != null) for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    val content = e.optJSONObject("content") ?: JSONObject()
                    when (e.optString("type")) {
                        "m.room.member" -> content.optString("displayname").takeIf { it.isNotBlank() }?.let { s.names[e.optString("state_key")] = it }
                        "m.room.name" -> s.state["m.room.name"] = content
                    }
                    if (e.optString("type") == "m.room.member" && e.optString("state_key") == me) s.heroes = listOf(e.optString("sender"))
                }
            }
        }
        roomsJson.optJSONObject("leave")?.keys()?.forEach { id -> data.remove(id); timelines.remove(id) }
        publish()
    }

    /** Rifà l'elenco delle stanze dallo stato, dalla più recente. */
    private fun publish() {
        val me = session?.userId.orEmpty()
        val list = data.values.map { s ->
            val events = timelines[s.id].orEmpty().filter { it.kind != Event.Kind.State && it.kind != Event.Kind.Beacon }
            val last = events.lastOrNull()
            val heroes = s.heroes.ifEmpty { s.names.keys.toList() }
            Room(
                id = s.id, name = Matrix.roomName(s.state, heroes, s.names, me),
                lastText = when (last?.kind) {
                    null -> ""
                    Event.Kind.Encrypted -> context.getString(R.string.ch_encrypted_short)
                    Event.Kind.Image -> context.getString(R.string.ch_photo)
                    Event.Kind.File -> context.getString(R.string.ch_file)
                    Event.Kind.Location -> context.getString(R.string.lo_preview)
                    // Con la trascrizione fatta (tavola ChatCartelle): «Papà: vocale trascritto · «arrivo alle 8»».
                    Event.Kind.Audio -> (if (last.sender == me) context.getString(R.string.ch_you) + ": " else "") +
                        ((voice.transcripts[voice.key(last)] as? VoiceHub.Transcript.Text)?.let { context.getString(R.string.cr_voice_text, it.text) }
                            ?: context.getString(R.string.vo_voice))
                    else -> (if (last.sender == me) context.getString(R.string.ch_you) + ": " else "") + last.text
                },
                lastTime = last?.time ?: 0L, unread = s.unread, encrypted = s.encrypted, invite = s.invite, direct = s.direct,
                prevBatch = s.prevBatch,
            )
        }.sortedWith(compareByDescending<Room> { it.invite }.thenByDescending { it.lastTime })
        rooms.clear()
        rooms.addAll(list)
    }

    // --- Conversazione ---

    fun open(roomId: String?) {
        if (roomId != openRoom) thread = null
        openRoom = roomId
        roomId ?: return
        markRead(roomId)
    }

    /** Segna letta la stanza fino all'ultimo messaggio (anche dal filo di una persona, senza aprire la chat). */
    fun markRead(roomId: String) {
        val last = timeline(roomId).lastOrNull { !it.id.startsWith("local:") } ?: return
        val c = client ?: return
        data[roomId]?.unread = 0
        publish()
        scope.launch(Dispatchers.IO) { c.read(roomId, last.id) }
    }

    /** I messaggi più vecchi, quando si arriva in cima. */
    fun older(roomId: String) {
        scope.launch { fetchOlder(roomId) }
    }

    /** Una pagina di messaggi più vecchi; falso quando non ce ne sono altri (o la richiesta è già in corso). */
    private suspend fun fetchOlder(roomId: String): Boolean {
        val s = data[roomId] ?: return false
        val from = s.prevBatch ?: return false
        val c = client ?: return false
        s.prevBatch = null
        val r = withContext(Dispatchers.IO) {
            runCatching { c.messages(roomId, from) }.getOrNull()?.also { r ->
                val a = r.optJSONArray("chunk") ?: return@also
                val cr = cryptoFor(c.session) ?: return@also
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    if (e.optString("type") == "m.room.encrypted") cr.decrypt(e, roomId)?.let { a.put(i, it) }
                }
            }
        } ?: run { s.prevBatch = from; return false }
        relate(roomId, r.optJSONArray("chunk"))
        val chunk = Matrix.events(r.optJSONArray("chunk"), s.names).reversed().filter { !relations.redacted(it.id) }
        val list = timeline(roomId)
        list.addAll(0, chunk.filter { e -> list.none { it.id == e.id } })
        s.prevBatch = r.optString("end").ifEmpty { null }
        return s.prevBatch != null
    }

    // --- Spese di gruppo (tavole SpeGruppo, SpeAggiungi, SpeSaldi) ---

    /** Chi è nella stanza, per chi ha pagato e tra chi si divide. */
    val members = mutableStateMapOf<String, List<String>>()
    /** Stanze di cui si sta leggendo tutta la storia per fare i conti. */
    val loadingHistory = mutableStateMapOf<String, Boolean>()

    /**
     * Per i conti servono tutte le spese, anche le più vecchie: si scorre la storia fino all'inizio (al massimo
     * [MAX_PAGES] pagine da 30 messaggi) e si leggono i membri.
     */
    fun loadExpenses(roomId: String) {
        val c = client ?: return
        if (loadingHistory[roomId] == true) return
        loadingHistory[roomId] = true
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { c.joinedMembers(roomId) }.getOrNull() }?.let { members[roomId] = it.sorted() }
            var pages = 0
            while (pages < MAX_PAGES && fetchOlder(roomId)) pages++
            loadingHistory[roomId] = false
        }
    }

    fun expenses(roomId: String): List<Expenses.Expense> =
        Expenses.current(timeline(roomId).filter { it.kind == Event.Kind.Expense }.mapNotNull { e ->
            e.data?.let { runCatching { Expenses.expense(JSONObject(it), e.time) }.getOrNull() }?.let { x ->
                // Una spesa la corregge o la toglie solo chi l'ha scritta.
                x.takeIf { firstAuthor(roomId, x.id) in listOf(null, e.sender) }
            }
        })

    fun settlements(roomId: String): List<Expenses.Settlement> =
        timeline(roomId).filter { it.kind == Event.Kind.Settlement }.mapNotNull { e ->
            e.data?.let { runCatching { Expenses.settlement(JSONObject(it), e.time) }.getOrNull() }
                // Un pagamento lo segna chi l'ha fatto o chi l'ha ricevuto.
                ?.takeIf { it.from == e.sender || it.to == e.sender }
        }

    private fun firstAuthor(roomId: String, expenseId: String): String? =
        timeline(roomId).firstOrNull { e ->
            e.kind == Event.Kind.Expense &&
                e.data?.let { runCatching { JSONObject(it).getJSONObject(Expenses.EXPENSE).optString("id") }.getOrNull() } == expenseId
        }?.sender

    fun sendExpense(roomId: String, e: Expenses.Expense, body: String, failed: () -> Unit) =
        sendContent(roomId, Expenses.content(e, body), Event.Kind.Expense, failed)

    fun sendSettlement(roomId: String, s: Expenses.Settlement, body: String, failed: () -> Unit) =
        sendContent(roomId, Expenses.content(s, body), Event.Kind.Settlement, failed)

    // --- Turni del gruppo (tavole ChatTurni e ChatTurniCrea; funzioni di Bussola CA) ---

    private fun shiftEvents(roomId: String) = timeline(roomId).filter { it.kind == Event.Kind.Shift && it.data != null }
        .mapNotNull { e -> runCatching { JSONObject(e.data!!) }.getOrNull()?.let { e to it } }

    /** Chi coordina: chi ha mandato per primo il piano. Solo le sue versioni valgono. */
    fun shiftCoordinator(roomId: String): String? =
        shiftEvents(roomId).firstOrNull { (_, c) -> c.optString("msgtype") == Shifts.PLAN }?.first?.sender

    fun shiftPlan(roomId: String): Shifts.Plan? {
        val boss = shiftCoordinator(roomId) ?: return null
        return shiftEvents(roomId).filter { (e, c) -> e.sender == boss && c.optString("msgtype") == Shifts.PLAN }
            .mapNotNull { (e, c) -> Shifts.plan(c, e.time) }.maxByOrNull { it.time }
    }

    /** Disponibilità e preferenze: l'ultima di ogni persona, valida solo se l'ha mandata lei. */
    fun shiftAvail(roomId: String): Map<String, Shifts.Avail> =
        shiftEvents(roomId).filter { (_, c) -> c.optString("msgtype") == Shifts.AVAIL }
            .mapNotNull { (e, c) -> Shifts.avail(c, e.sender, e.time) }.groupBy { it.person }.mapValues { (_, v) -> v.maxBy { it.time } }

    fun shiftEffective(roomId: String): Shifts.Effective? {
        val plan = shiftPlan(roomId) ?: return null
        val ev = shiftEvents(roomId)
        val changes = ev.filter { (_, c) -> c.optString("msgtype") == Shifts.CHANGE }.mapNotNull { (e, c) -> Shifts.change(c, e.sender, e.time) }
            .distinctBy { it.id }
        val replies = ev.filter { (_, c) -> c.optString("msgtype") == Shifts.REPLY }.mapNotNull { (e, c) -> Shifts.reply(c, e.sender, e.time) }
        return Shifts.effective(plan, changes, replies, shiftCoordinator(roomId))
    }

    fun sendShift(roomId: String, content: JSONObject, failed: () -> Unit) = sendContent(roomId, content, Event.Kind.Shift, failed)

    /** Il nome di chi fa i turni: dalla Chat, o quello scritto per chi la Chat non ce l'ha. */
    fun shiftName(id: String): String = if (id.startsWith(ShiftsIO.EXTERNAL)) ShiftsIO.display(id) else nameOf(id)

    /** I nomi del piano e del gruppo, per riconoscerli nei file importati. */
    fun shiftNames(roomId: String, plan: Shifts.Plan): Map<String, String> =
        (plan.people.map { it.id } + members[roomId].orEmpty()).distinct().associateWith { shiftName(it) }

    /**
     * Legge un file di turni (PDF con testo, CSV, XLSX, ODS, testo) fino a 20 MB, fuori dal thread principale; null se
     * non si apre.
     */
    fun readShiftFile(uri: android.net.Uri, done: (ShiftsIO.Table?) -> Unit) {
        scope.launch {
            val t = withContext(Dispatchers.IO) {
                runCatching {
                    val name = describe(uri)?.name.orEmpty()
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var n: Int; var total = 0
                        while (input.read(buf).also { n = it } > 0) { total += n; if (total > 20 * 1024 * 1024) return@runCatching null; out.write(buf, 0, n) }
                        out.toByteArray()
                    } ?: return@runCatching null
                    ShiftsIO.table(name, bytes) { eu.eurise.core.pdf.PdfText.extract(it, maxPages = 30) }
                }.getOrNull()
            }
            done(t)
        }
    }

    /** Abbinamenti di nomi e sigle scelti all'importazione, ricordati per gruppo (solo sul telefono). */
    fun shiftAliases(roomId: String, codes: Boolean): Map<String, String> {
        val raw = context.getSharedPreferences("shifts", android.content.Context.MODE_PRIVATE).getString((if (codes) "codes:" else "names:") + roomId, null) ?: return emptyMap()
        return runCatching { JSONObject(raw).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrDefault(emptyMap())
    }

    fun saveShiftAliases(roomId: String, codes: Boolean, map: Map<String, String>) {
        context.getSharedPreferences("shifts", android.content.Context.MODE_PRIVATE).edit()
            .putString((if (codes) "codes:" else "names:") + roomId, JSONObject(map).toString()).apply()
    }

    /** Riscrive i propri turni del gruppo nel calendario «Turni» del telefono, se acceso. */
    fun syncShiftCalendar(roomId: String) {
        val plan = shiftPlan(roomId) ?: return
        val eff = shiftEffective(roomId) ?: return
        val me = session?.userId ?: return
        val group = room(roomId)?.name.orEmpty()
        val names = shiftNames(roomId, plan)
        scope.launch(Dispatchers.IO) {
            ShiftCalendar.sync(context, roomId, group, plan, eff.entries, me, { names[it] ?: ShiftsIO.display(it) },
                context.getString(R.string.sh_calendar_name), plan.reminder)
        }
    }

    fun send(roomId: String, text: String, failed: () -> Unit) =
        sendContent(roomId, JSONObject().put("msgtype", "m.text").put("body", text), Event.Kind.Text, failed)

    /** Un messaggio qualsiasi (testo, spesa, pagamento): eco locale subito, cifrato se la stanza lo è. */
    private fun sendContent(roomId: String, content: JSONObject, kind: Event.Kind, failed: () -> Unit, type: String = "m.room.message") {
        val me = session?.userId.orEmpty()
        // L'eco locale ha lo stesso testo del messaggio che arriverà (per i sondaggi, la domanda).
        val text = if (kind == Event.Kind.Poll) Relations.poll(content)?.question.orEmpty() else content.optString("body")
        val local = Event("local:" + System.nanoTime(), me, text, System.currentTimeMillis(), kind,
            data = if (kind == Event.Kind.Text) null else content.toString(),
            replyTo = Relations.replyTo(content).takeUnless { Threads.fallingBack(content) }, thread = Threads.root(content))
        timeline(roomId).add(local)
        sendEvent(roomId, type, content) { timeline(roomId).remove(local); failed() }
    }

    /** Manda un evento di un tipo qualsiasi, cifrato se la stanza lo è. */
    private fun sendEvent(roomId: String, type: String, content: JSONObject, failed: () -> Unit) {
        val c = client ?: return
        val encrypted = data[roomId]?.encrypted == true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (encrypted) {
                        val cr = cryptoFor(c.session) ?: error("crypto")
                        c.sendEncrypted(roomId, cr.encrypt(c, roomId, c.joinedMembers(roomId), type, content))
                    } else c.sendEvent(roomId, type, content)
                }.isSuccess
            }
            if (!ok) failed()
        }
    }

    // --- Le basi (tavola ChatBasi): risposte, reazioni, modifiche, cancellazioni, fissati, inoltri, sondaggi ---

    /** L'avviso «Tieni premuto un messaggio…» si vede finché non si usa il menu la prima volta. */
    var basiHintSeen by mutableStateOf(context.getSharedPreferences("chat_ui", Context.MODE_PRIVATE).getBoolean("basi_hint", false))
        private set
    fun basiHintDone() {
        if (basiHintSeen) return
        basiHintSeen = true
        context.getSharedPreferences("chat_ui", Context.MODE_PRIVATE).edit().putBoolean("basi_hint", true).apply()
    }

    /** L'avviso «Per chi vede poco: tutti i messaggi letti da soli» (tavola ChatVocali) si vede finché non si tocca. */
    var readHintSeen by mutableStateOf(context.getSharedPreferences("chat_ui", Context.MODE_PRIVATE).getBoolean("read_hint", false))
        private set
    fun readHintDone() {
        readHintSeen = true
        context.getSharedPreferences("chat_ui", Context.MODE_PRIVATE).edit().putBoolean("read_hint", true).apply()
    }

    /** Reazioni, modifiche, voti e cancellazioni di tutte le stanze, per messaggio. */
    val relations = Relations.Store()
    /** Cambia a ogni reazione, modifica, voto o messaggio fissato, per ridisegnare. */
    var relationsTick by mutableStateOf(0)
        private set

    /** Raccoglie i riferimenti di un elenco di eventi grezzi; una cancellazione toglie il messaggio dalla conversazione. */
    private fun relate(roomId: String, a: org.json.JSONArray?) {
        a ?: return
        var changed = false
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("type") == Relations.PIN_STATE) changed = true
            val r = Relations.read(o) ?: continue
            if (relations.add(r)) {
                changed = true
                if (r is Relations.Redaction) timeline(roomId).removeAll { it.id == r.target }
            }
        }
        if (changed) relationsTick++
    }

    // --- Discussioni a filo (tavola ChatThread) ---

    /** La discussione aperta nella conversazione: id del messaggio di partenza, o null. */
    var thread by mutableStateOf<String?>(null)

    /** Risponde nella discussione [root]; [replyTo] se si risponde a un messaggio preciso della discussione. */
    fun sendInThread(roomId: String, root: String, text: String, replyTo: String?, failed: () -> Unit) =
        sendContent(roomId, Threads.content(text, root, Threads.last(timeline(roomId), root), replyTo), Event.Kind.Text, failed)

    /**
     * All'apertura di una discussione: tutte le risposte dal server (anche quelle più vecchie dei messaggi caricati) e,
     * se manca, il messaggio di partenza. Si decifrano come i messaggi più vecchi.
     */
    fun loadThread(roomId: String, root: String) {
        val c = client ?: return
        val s = data[roomId] ?: return
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    val a = c.threadReplies(roomId, root).optJSONArray("chunk") ?: org.json.JSONArray()
                    if (timeline(roomId).none { it.id == root }) runCatching { a.put(c.event(roomId, root)) }
                    val cr = cryptoFor(c.session)
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        if (cr != null && e.optString("type") == "m.room.encrypted") cr.decrypt(e, roomId)?.let { a.put(i, it) }
                    }
                    a
                }.getOrNull()
            } ?: return@launch
            relate(roomId, raw)
            val list = timeline(roomId)
            val missing = Matrix.events(raw, s.names).filter { e -> !relations.redacted(e.id) && list.none { it.id == e.id } }
            if (missing.isEmpty()) return@launch
            val sorted = (list + missing).sortedBy { it.time }
            list.clear()
            list.addAll(sorted)
        }
    }

    fun reply(roomId: String, target: String, text: String, failed: () -> Unit) =
        sendContent(roomId, Relations.reply(text, target), Event.Kind.Text, failed)

    /** Mette o toglie (se c'è già la mia) una reazione. */
    fun react(roomId: String, target: String, key: String, failed: () -> Unit) {
        val mine = relations.chips(target, session?.userId.orEmpty()).firstOrNull { it.key == key }?.mine
        if (mine != null) delete(roomId, mine, failed) else sendEvent(roomId, Relations.REACTION, Relations.reaction(target, key), failed)
    }

    fun edit(roomId: String, target: String, text: String, failed: () -> Unit) {
        // Subito sul telefono; l'evento vero arriva con la sincronizzazione.
        relations.add(Relations.Edit("local:" + System.nanoTime(), session?.userId.orEmpty(), System.currentTimeMillis(), target, text))
        relationsTick++
        sendEvent(roomId, "m.room.message", Relations.edit(target, text), failed)
    }

    /** Cancella per tutti un messaggio (o una reazione): resta solo che c'era. */
    fun delete(roomId: String, eventId: String, failed: () -> Unit) {
        val c = client ?: return
        relations.add(Relations.Redaction("local:" + System.nanoTime(), session?.userId.orEmpty(), System.currentTimeMillis(), eventId))
        timeline(roomId).removeAll { it.id == eventId }
        relationsTick++
        publish()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { c.redact(roomId, eventId, null) }.isSuccess }
            if (!ok) failed()
        }
    }

    /** I messaggi fissati della stanza, dal più recente. */
    fun pinned(roomId: String): List<String> = Relations.pinned(data[roomId]?.state?.get(Relations.PIN_STATE))

    fun pin(roomId: String, eventId: String, on: Boolean, failed: () -> Unit) {
        val c = client ?: return
        val s = data[roomId] ?: return
        val before = pinned(roomId)
        val after = if (on) (listOf(eventId) + before).distinct() else before - eventId
        s.state[Relations.PIN_STATE] = Relations.pinnedState(after)
        relationsTick++
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { c.setState(roomId, Relations.PIN_STATE, Relations.pinnedState(after)) }.isSuccess }
            if (!ok) { s.state[Relations.PIN_STATE] = Relations.pinnedState(before); relationsTick++; failed() }
        }
    }

    /** Inoltra un messaggio (testo, foto o file) in un'altra stanza, senza il riferimento alla risposta. */
    fun forward(e: Event, toRoom: String, failed: () -> Unit) {
        val content = e.data?.let { runCatching { JSONObject(it) }.getOrNull() }?.takeIf { e.kind == Event.Kind.Image || e.kind == Event.Kind.File || e.kind == Event.Kind.Audio }
            ?: JSONObject().put("msgtype", "m.text").put("body", relations.edited(e.id, e.sender) ?: e.text)
        content.remove("m.relates_to")
        sendContent(toRoom, content, if (e.kind == Event.Kind.Image || e.kind == Event.Kind.File || e.kind == Event.Kind.Audio) e.kind else Event.Kind.Text, failed)
    }

    fun sendPoll(roomId: String, question: String, answers: List<String>, closes: Long?, failed: () -> Unit) =
        sendContent(roomId, Relations.newPoll(question, answers, closes), Event.Kind.Poll, failed, type = "org.matrix.msc3381.poll.start")

    fun vote(roomId: String, poll: String, answer: String, failed: () -> Unit) {
        relations.add(Relations.Vote("local:" + System.nanoTime(), session?.userId.orEmpty(), System.currentTimeMillis(), poll, listOf(answer)))
        relationsTick++
        sendEvent(roomId, "org.matrix.msc3381.poll.response", Relations.vote(poll, listOf(answer)), failed)
    }

    fun endPoll(roomId: String, poll: String, text: String, failed: () -> Unit) {
        if (relations.endSent(poll)) return
        relations.add(Relations.PollEnd("local:" + System.nanoTime(), session?.userId.orEmpty(), System.currentTimeMillis(), poll))
        relationsTick++
        sendEvent(roomId, "org.matrix.msc3381.poll.end", Relations.endPoll(poll, text), failed)
    }

    fun accept(roomId: String) {
        val c = client ?: return
        scope.launch { withContext(Dispatchers.IO) { runCatching { c.join(roomId) } } }
    }

    fun decline(roomId: String) {
        val c = client ?: return
        data.remove(roomId)
        publish()
        scope.launch { withContext(Dispatchers.IO) { runCatching { c.leave(roomId) } } }
    }

    /** Una chat nuova con [input] («@anna:studio.it»); [done] con la stanza o il motivo del no. */
    fun newChat(input: String, done: (String?, Int?) -> Unit) {
        val userId = Matrix.normalizeId(input) ?: return done(null, R.string.ch_err_id)
        val c = client ?: return
        scope.launch {
            val result = withContext<Pair<String?, Int?>>(Dispatchers.IO) {
                if (runCatching { c.profile(userId) }.isFailure) return@withContext null to R.string.ch_err_unknown_user
                runCatching { c.createDirect(userId) }.fold({ it to null }, { null to R.string.ch_err_create })
            }
            result.first?.let { roomId -> data.getOrPut(roomId) { RoomState(roomId) }.apply { direct = true; encrypted = true; heroes = listOf(userId) }; publish() }
            done(result.first, result.second)
        }
    }

    fun newGroup(name: String, inputs: List<String>, done: (String?, Int?) -> Unit) {
        val ids = inputs.mapNotNull { Matrix.normalizeId(it) }
        if (name.isBlank()) return done(null, R.string.ch_err_group_name)
        val c = client ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { c.createGroup(name.trim(), ids) }.getOrNull() }
            if (r != null) {
                data.getOrPut(r) { RoomState(r) }.apply { encrypted = true; state["m.room.name"] = JSONObject().put("name", name.trim()) }
                publish()
            }
            done(r, if (r == null) R.string.ch_err_create else null)
        }
    }

    /**
     * Scarica un'immagine o un file della chat nella cache, per aprirlo. Quelli cifrati si aprono in memoria (impronta
     * controllata, vedi [Attachments]) e solo dopo vanno su disco; la cache si svuota da sola dopo un giorno.
     */
    fun media(e: Event, done: (File?) -> Unit) {
        val s = session ?: return
        val url = e.mediaUrl?.let { Matrix.mediaUrl(s.homeserver, it, thumbnail = false) } ?: return done(null)
        val content = e.data?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (content != null && eu.eurise.core.studio.Bussola.isReport(content) &&
            eu.eurise.core.studio.Bussola.reportExpired(e.time, System.currentTimeMillis())) return done(null)
        val info = content?.optJSONObject("file")
        // I vocali hanno tutti lo stesso nome («Messaggio vocale»): in cache ognuno il suo, e si riusa (ascolto e trascrizione).
        val name = if (e.kind == Event.Kind.Audio) "vocale-" + (e.mediaUrl.hashCode().toLong() and 0xffffffffL) +
            (if (Voice.info(content).mime.contains("ogg")) ".ogg" else ".m4a")
        else (content?.optString("filename")?.ifEmpty { null } ?: e.text).ifBlank { "file" }
            .replace(Regex("[/\\\\:*?\"<>|\\n]"), "-").take(120)
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "chat").apply { mkdirs() }
                val out = File(dir, name)
                if (e.kind == Event.Kind.Audio && out.length() > 0) return@withContext out
                runCatching {
                    val c = ChatClient(s)
                    if (info != null) out.also { it.writeBytes(Attachments.decrypt(c.fetch(url), info)) } else c.download(url, out)
                }.getOrNull()
            }
            done(f)
        }
    }

    /** I file aperti restano in cache al massimo un giorno (referti compresi): poi si tolgono. */
    private fun cleanCache() {
        scope.launch(Dispatchers.IO) {
            val limit = System.currentTimeMillis() - 86_400_000L
            File(context.cacheDir, "chat").listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
        }
    }

    // --- Allegati (tavola ChatAllega) e referti (tavola BusReferto) ---

    /** Un file scelto dal telefono. */
    data class Picked(val uri: android.net.Uri, val name: String, val mime: String, val size: Long)

    /** Nome, tipo e misura di un file scelto (dal selettore di sistema o da Eurise File). */
    fun describe(uri: android.net.Uri): Picked? = runCatching {
        val cr = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = cr.getType(uri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        Picked(uri, name, mime, size)
    }.getOrNull()

    /** Perché un invio non è riuscito (testo da mostrare). */
    class SendProblem(val text: Int) : Exception()

    /**
     * Manda un file: cifrato sul telefono se la stanza è cifrata (chiave nuova per ogni file, dentro il messaggio
     * cifrato), poi caricato e annunciato come `m.image` o `m.file`, che ogni app Matrix sa aprire. Con [reportNote] è un
     * referto di La mia Bussola: PDF, JPG o PNG fino a 10 MB, solo in una chat cifrata, cancellato dopo 45 giorni.
     */
    fun sendFile(roomId: String, picked: Picked, reportNote: String?, failed: (Int) -> Unit) {
        val c = client ?: return
        val me = session?.userId.orEmpty()
        val report = reportNote != null
        val note = reportNote?.let(eu.eurise.core.studio.Bussola::reportNote).orEmpty()
        val image = picked.mime.startsWith("image/")
        val echoText = if (report) note.ifBlank { picked.name } else picked.name
        val local = Event("local:" + System.nanoTime(), me, echoText, System.currentTimeMillis(),
            if (report) Event.Kind.Bussola else if (image) Event.Kind.Image else Event.Kind.File,
            data = if (report) JSONObject().put("msgtype", "m.file").put("body", echoText).put("filename", picked.name)
                .put(eu.eurise.core.studio.Bussola.REPORT, JSONObject().put("note", note)).toString() else null)
        timeline(roomId).add(local)
        val encrypted = data[roomId]?.encrypted == true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    if (report && !encrypted) throw SendProblem(R.string.bu_rep_plain)
                    val max = if (report) eu.eurise.core.studio.Bussola.REPORT_MAX_BYTES else MAX_FILE
                    val bytes = context.contentResolver.openInputStream(picked.uri)?.use { input ->
                        val buf = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            buf.write(chunk, 0, n)
                            if (buf.size() > max) throw SendProblem(if (report) R.string.bu_rep_too_big else R.string.ch_file_too_big)
                        }
                        buf.toByteArray()
                    } ?: throw SendProblem(R.string.ch_file_unreadable)
                    if (report) eu.eurise.core.studio.Bussola.checkReport(picked.mime, bytes.size.toLong())?.let { throw SendProblem(reportProblem(it)) }
                    // Larghezza e altezza delle foto (Element le usa per l'impaginazione); solo l'intestazione, niente decodifica.
                    val dims = if (image) android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        .also { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }.let { it.outWidth to it.outHeight } else null
                    val file: JSONObject?
                    val url: String?
                    if (encrypted) {
                        val sealed = Attachments.encrypt(bytes)
                        file = sealed.info.put("url", c.upload(sealed.bytes, "application/octet-stream", null))
                        url = null
                    } else {
                        file = null
                        url = c.upload(bytes, picked.mime, picked.name)
                    }
                    val content = if (report) {
                        eu.eurise.core.studio.Bussola.report(
                            eu.eurise.core.studio.Bussola.Report(picked.name, picked.mime, bytes.size.toLong(), note, System.currentTimeMillis()),
                            file!!, dims?.first, dims?.second)
                    } else {
                        val info = JSONObject().put("mimetype", picked.mime).put("size", bytes.size)
                        if (dims != null && dims.first > 0 && dims.second > 0) info.put("w", dims.first).put("h", dims.second)
                        JSONObject().put("msgtype", if (image) "m.image" else "m.file").put("body", picked.name)
                            .put("filename", picked.name).put("info", info)
                            .apply { if (file != null) put("file", file) else put("url", url) }
                    }
                    val eventId = if (encrypted) {
                        val cr = cryptoFor(c.session) ?: error("crypto")
                        c.sendEncrypted(roomId, cr.encrypt(c, roomId, c.joinedMembers(roomId), "m.room.message", content))
                    } else c.sendMessage(roomId, content)
                    if (report && eventId.isNotEmpty()) {
                        BussolaReports.add(context, roomId, eventId, System.currentTimeMillis() + eu.eurise.core.studio.Bussola.REPORT_DAYS * 86_400_000L)
                    }
                }
            }
            r.onFailure { e ->
                timeline(roomId).remove(local)
                failed((e as? SendProblem)?.text ?: R.string.ch_send_failed)
            }
        }
    }

    /**
     * Manda un vocale (tavola ChatVocali): AAC in un .m4a, cifrato se la stanza lo è, come `m.audio` con i campi dei
     * vocali di Element (durata e forma d'onda). Intanto si trascrive sul telefono dall'audio ancora in memoria.
     */
    fun sendVoice(roomId: String, pcm: ShortArray, failed: (Int) -> Unit) {
        val c = client ?: return
        val me = session?.userId.orEmpty()
        val durationMs = pcm.size * 1000L / Voice.RATE
        val wave = Voice.waveform(pcm)
        val body = context.getString(R.string.vo_voice)
        val echo = Voice.content(body, VoiceCodec.MIME, 0, durationMs, wave, null, null)
        val local = Event("local:" + System.nanoTime(), me, body, System.currentTimeMillis(), Event.Kind.Audio, data = echo.toString())
        timeline(roomId).add(local)
        voice.transcribeOwn(local.id, pcm)
        val encrypted = data[roomId]?.encrypted == true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "chat").apply { mkdirs() }
                    val f = File(dir, "invio-" + System.currentTimeMillis() + ".m4a")
                    if (!VoiceCodec.encode(pcm, f)) throw SendProblem(R.string.vo_encode_failed)
                    val bytes = f.readBytes()
                    f.delete()
                    if (bytes.size > MAX_FILE) throw SendProblem(R.string.ch_file_too_big)
                    val name = "vocale.m4a"
                    val content = if (encrypted) {
                        val sealed = Attachments.encrypt(bytes)
                        Voice.content(body, VoiceCodec.MIME, bytes.size, durationMs, wave, sealed.info.put("url", c.upload(sealed.bytes, "application/octet-stream", null)), null)
                    } else Voice.content(body, VoiceCodec.MIME, bytes.size, durationMs, wave, null, c.upload(bytes, VoiceCodec.MIME, name))
                    val url = content.optString("url").ifEmpty { content.optJSONObject("file")?.optString("url").orEmpty() }
                    if (encrypted) {
                        val cr = cryptoFor(c.session) ?: error("crypto")
                        c.sendEncrypted(roomId, cr.encrypt(c, roomId, c.joinedMembers(roomId), "m.room.message", content))
                    } else c.sendMessage(roomId, content)
                    url
                }
            }
            r.onSuccess { url -> if (url.isNotEmpty()) voice.move(local.id, url) }
            r.onFailure { e ->
                timeline(roomId).remove(local)
                voice.transcripts.remove(local.id)
                failed((e as? SendProblem)?.text ?: R.string.ch_send_failed)
            }
        }
    }

    fun reportProblem(p: eu.eurise.core.studio.Bussola.ReportProblem) = when (p) {
        eu.eurise.core.studio.Bussola.ReportProblem.Type -> R.string.bu_rep_type
        eu.eurise.core.studio.Bussola.ReportProblem.TooBig -> R.string.bu_rep_too_big
        eu.eurise.core.studio.Bussola.ReportProblem.Empty -> R.string.ch_file_unreadable
    }

    // --- Accesso (tavola ChatAccesso: «Collega», accedi nel browser, approva) ---

    fun begin(server: String, done: (LoginStep) -> Unit) {
        scope.launch {
            val step = withContext(Dispatchers.IO) {
                runCatching {
                    val hs = ChatClient.discover(server)
                    val flows = ChatClient.flows(hs)
                    when {
                        "m.login.sso" in flows -> LoginStep.Browser(hs, ChatClient.ssoUrl(hs, REDIRECT))
                        "m.login.password" in flows -> LoginStep.Password(hs)
                        else -> LoginStep.Failed(R.string.ch_err_flows)
                    }
                }.getOrElse { LoginStep.Failed(R.string.ch_err_server) }
            }
            if (step is LoginStep.Browser) pendingHomeserver = step.homeserver
            done(step)
        }
    }

    /**
     * Il server dell'accesso in corso nel browser (torna con [finishSso]). Vale dieci minuti e una volta sola: prima restava
     * salvato per sempre e un «eurisechat://login?loginToken=…» mandato da un'altra app, anche giorni dopo, poteva far
     * entrare la Chat nell'account di qualcun altro (porte d'ingresso, docs/SICUREZZA.md).
     */
    var pendingHomeserver: String?
        get() {
            val prefs = context.getSharedPreferences("chat_login", Context.MODE_PRIVATE)
            val at = prefs.getLong("hs_at", 0L)
            val age = System.currentTimeMillis() - at
            return prefs.getString("hs", null)?.takeIf { age in 0..SSO_WINDOW_MS }
        }
        set(v) {
            context.getSharedPreferences("chat_login", Context.MODE_PRIVATE).edit()
                .putString("hs", v).putLong("hs_at", if (v == null) 0L else System.currentTimeMillis()).apply()
        }

    fun finishSso(loginToken: String, done: (Boolean) -> Unit) {
        val hs = pendingHomeserver ?: return done(false)
        // Il ritorno dall'accesso vale una volta sola: un secondo «eurisechat://login» (anche da un'altra app) non entra.
        pendingHomeserver = null
        scope.launch {
            val s = withContext(Dispatchers.IO) { runCatching { ChatClient.loginWithToken(hs, loginToken) }.getOrNull() }
            if (s != null) signedIn(s)
            done(s != null)
        }
    }

    fun loginPassword(homeserver: String, user: String, password: String, done: (Boolean) -> Unit) {
        scope.launch {
            val s = withContext(Dispatchers.IO) { runCatching { ChatClient.loginWithPassword(homeserver, user.trim(), password) }.getOrNull() }
            if (s != null) signedIn(s)
            done(s != null)
        }
    }

    private fun signedIn(s: Session) {
        store.save(s)
        session = s
        since = null
        accounts = ChatAccounts.signedIn(context)
        otherSession = SessionStore(context, account.other).load()
        ChatWorker.schedule(context)
        start()
    }

    // --- Chiave di recupero e verifica (tavole ChatRecupero e ChatVerifica) ---

    /** Lo stato della chiave e della copia delle chiavi; null finché non si sa. */
    var security by mutableStateOf<SecurityStatus?>(null)
        private set
    /** Una verifica in corso o appena chiesta da un altro dispositivo. */
    var verify by mutableStateOf<Verify?>(null)
        private set
    /** La chiave di recupero custodita (sigillata dal Keystore) per «Mostra» e «Copia». */
    val recoveryKey: String? get() = store.recovery()
    /** La chiave è anche nel backup cifrato della suite (nell'app Eurise). */
    var keptInSuite by mutableStateOf(store.keptInSuite)
        private set
    private var verifyJob: Job? = null
    /** Le persone che hanno mandato passi di verifica nelle chat: si guarda se chiedono qualcosa. Scritto sul thread di rete. */
    private val verifyPeers: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** Le persone verificate tra quelle delle chat aperte (per lo scudo nella testata). */
    val verifiedPeople = mutableStateMapOf<String, Boolean>()
    private val hub get() = eu.eurise.core.suite.SecretsContract.hubPackage(context.packageName.endsWith(".debug"))

    private fun securityFor(): ChatSecurity? {
        val c = client ?: return null
        val cr = cryptoFor(c.session) ?: return null
        return ChatSecurity(c, cr)
    }

    fun refreshSecurity() {
        scope.launch { security = withContext(Dispatchers.IO) { securityFor()?.let { runCatching { it.status() }.getOrNull() } } }
    }

    /**
     * Crea la chiave (prima volta) o, con [text], apre quella che c'è già. [auth] è la conferma d'identità se il server
     * l'ha chiesta. Il risultato: la chiave a gruppi, o l'errore (AuthNeeded, AlreadySetUp, WrongKey…).
     */
    fun recovery(text: String?, auth: JSONObject?, done: (Result<String>) -> Unit) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val sec = securityFor() ?: error("offline")
                    if (text == null) sec.setup(auth) else sec.restore(text)
                }
            }
            r.onSuccess { key ->
                store.saveRecovery(key)
                if (keptInSuite) withContext(Dispatchers.IO) { eu.eurise.core.suite.SecretsContract.put(context, hub, SUITE_SECRET, key) }
            }
            refreshSecurity()
            done(r)
        }
    }

    /** La chiave salvata nel backup della suite (telefono nuovo, ripristinato): si può usare senza scriverla. */
    suspend fun suiteKey(): String? = withContext(Dispatchers.IO) { eu.eurise.core.suite.SecretsContract.get(context, hub, SUITE_SECRET) }

    /** Tiene (o toglie) la chiave nel backup cifrato della suite; falso se l'app Eurise non c'è. */
    fun keepInSuite(on: Boolean, done: (Boolean) -> Unit) {
        scope.launch {
            val key = recoveryKey
            val ok = withContext(Dispatchers.IO) {
                if (on) key != null && eu.eurise.core.suite.SecretsContract.put(context, hub, SUITE_SECRET, key)
                else eu.eurise.core.suite.SecretsContract.remove(context, hub, SUITE_SECRET)
            }
            if (ok || !on) { keptInSuite = on; store.keptInSuite = on }
            done(ok)
        }
    }

    fun devices(done: (Result<List<ChatDevice>>) -> Unit) {
        scope.launch { done(withContext(Dispatchers.IO) { runCatching { securityFor()?.devices() ?: error("offline") } }) }
    }

    /** Fa uscire un dispositivo; con [password] o dopo la conferma nel browser ([session]) se il server l'ha chiesta. */
    fun logoutDevice(id: String, session: String?, password: String?, done: (Result<Unit>) -> Unit) {
        scope.launch {
            done(withContext(Dispatchers.IO) {
                runCatching {
                    val sec = securityFor() ?: error("offline")
                    val auth = when {
                        session != null && password != null -> sec.passwordAuth(session, password)
                        session != null -> sec.sessionAuth(session)
                        else -> null
                    }
                    sec.logoutDevice(id, auth)
                }
            })
        }
    }

    fun ssoConfirmUrl(session: String): String? = client?.ssoFallback(session)

    /** Dopo ogni tornata: c'è un altro dispositivo che chiede la verifica? */
    private fun checkIncoming() {
        if (verify != null) return
        scope.launch {
            val found = withContext(Dispatchers.IO) { securityFor()?.incoming(verifyPeers.toList()) }
            if (found != null && verify == null) verify = found
        }
    }

    /** «Verifica questo telefono» da qui: lo chiede agli altri dispositivi dell'account. */
    fun startVerify() {
        scope.launch {
            val v = withContext(Dispatchers.IO) { runCatching { securityFor()?.requestSelf() }.getOrNull() }
            verify = v
            if (v != null) drive(v.flowId)
        }
    }

    /** L'altra persona di una chat a due, se la stanza è a due. */
    fun peerOf(roomId: String): String? {
        val me = session?.userId ?: return null
        val s = data[roomId] ?: return null
        val others = (s.heroes + s.names.keys).distinct().filter { it != me }
        return others.singleOrNull()?.takeIf { s.direct || others.size == 1 }
    }

    /** Guarda se la persona della chat è verificata (scudo nella testata). */
    fun checkPeer(roomId: String) {
        val peer = peerOf(roomId) ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { securityFor()?.isVerified(peer) } ?: return@launch
            verifiedPeople[peer] = ok
        }
    }

    /**
     * «Verifica» dalla chat con una persona: la richiesta parte nella chat, lei la accetta dal suo telefono (Eurise o
     * Element) e confrontate le figure, di persona o in chiamata. [failed] con il motivo se non si può.
     */
    fun startVerifyUser(roomId: String, failed: (Int) -> Unit) {
        val peer = peerOf(roomId) ?: return failed(R.string.ch_ver_person_none)
        verify = Verify.Waiting("", null, peer)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { securityFor()?.requestUser(roomId, peer) ?: error("offline") } }
            r.onSuccess { v -> verify = v; drive(v.flowId) }
            r.onFailure { e -> verify = null; failed(if (e is NoIdentity) R.string.ch_ver_person_no_keys else R.string.ch_ver_person_failed) }
        }
    }

    fun acceptVerify() {
        val v = verify ?: return
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { securityFor()?.accept(v.flowId, v.user) } }
            verify = Verify.Waiting(v.flowId, v.device, v.user)
            drive(v.flowId)
        }
    }

    /** La verifica avanza da sola mentre è aperta: le risposte dell'altro dispositivo arrivano con /sync. */
    private fun drive(flowId: String) {
        verifyJob?.cancel()
        val user = verify?.user
        verifyJob = scope.launch {
            while (isActive) {
                val v = withContext(Dispatchers.IO) { runCatching { securityFor()?.step(flowId, user) }.getOrNull() }
                if (v != null && verify?.flowId == flowId) verify = v
                if (v is Verify.Done || v is Verify.Cancelled || verify?.flowId != flowId) break
                delay(700)
            }
            if (verify is Verify.Done) { refreshSecurity(); user?.let { verifiedPeople[it] = true } }
        }
    }

    fun confirmVerify() {
        val v = verify ?: return
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { securityFor()?.confirm(v.flowId, v.user) } }
            verify = Verify.Confirmed(v.flowId, v.device, v.user)
        }
    }

    /** «Non sono uguali» ([mismatch]) o chiudi: la verifica si annulla anche sull'altro dispositivo. */
    fun cancelVerify(mismatch: Boolean) {
        val v = verify ?: return
        verifyJob?.cancel()
        verify = null
        if (v is Verify.Done || v is Verify.Cancelled || v.flowId.isEmpty()) return
        scope.launch(Dispatchers.IO) { runCatching { securityFor()?.cancel(v.flowId, mismatch, v.user) } }
    }

    fun logout() {
        val c = client
        scope.launch(Dispatchers.IO) { c?.logout() }
        forget()
    }

    /** Esce dall'account che si sta guardando; se c'è l'altro, si passa a quello. */
    private fun forget() {
        stop()
        store.clear()
        session = null
        clearMemory()
        accounts = ChatAccounts.signedIn(context)
        val rest = accounts.firstOrNull()
        if (rest != null) switchTo(rest) else ChatWorker.cancel(context)
    }

    companion object {
        /** Gli allegati qualsiasi (non referti): al massimo 25 MB, letti in memoria per cifrarli. */
        const val MAX_FILE = 25L * 1024 * 1024

        /**
         * Il testo con cui un messaggio entra nella coda dello Studio, o null se non ci entra: testi, avvisi, e da La mia
         * Bussola le richieste di rinnovo, le risposte agli inviti e i referti (con il nome del file e le due righe).
         */
        fun studioText(e: Event): String? {
            if (e.kind == Event.Kind.Text || e.kind == Event.Kind.Notice) return e.text
            if (e.kind != Event.Kind.Bussola) return null
            val c = e.data?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
            eu.eurise.core.studio.Bussola.readReport(c, e.time)?.let { r ->
                return "Referto: " + r.name + if (r.note.isNotBlank()) " — " + r.note else ""
            }
            return e.text.takeIf { c.optString("msgtype").let { t -> t == eu.eurise.core.studio.Bussola.RX_REQUEST || t == eu.eurise.core.studio.Bussola.CHECKUP_REPLY } }
        }

        const val REDIRECT = "eurisechat://login"
        /** Quanto vale un accesso iniziato nel browser ([pendingHomeserver]). */
        const val SSO_WINDOW_MS = 10 * 60 * 1000L
        /** Pagine di storia lette al massimo per i conti delle spese (30 messaggi l'una). */
        const val MAX_PAGES = 200
        /** Il nome della chiave di recupero tra i segreti affidati all'app Eurise. */
        const val SUITE_SECRET = "chat.recovery_key"
        /** La Chat è aperta e sincronizza da sé: il lavoro in background non tocca le chiavi nello stesso momento. */
        @Volatile var active = false
    }
}
