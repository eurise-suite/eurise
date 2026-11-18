package eu.eurise.chat

import android.content.Context
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

    fun nameOf(userId: String): String = people[userId] ?: Matrix.localpart(userId)

    // --- Sincronizzazione ---

    /** Accende il ciclo di /sync (app aperta). */
    fun start() {
        if (session == null || loop?.isActive == true) return
        active = true
        cleanCache()
        client?.let { c -> scope.launch(Dispatchers.IO) { BussolaReports.redactDue(context, c) } }
        startOther()
        loop = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                val c = client ?: break
                val first = since == null
                val r = withContext(Dispatchers.IO) {
                    runCatching { c.sync(since, if (first) 0 else 30_000) }.map { json -> json to prepare(c, json) }
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

    fun stop() { loop?.cancel(); loop = null; otherLoop?.cancel(); otherLoop = null; active = false }

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
            val events = timelines[s.id].orEmpty().filter { it.kind != Event.Kind.State }
            val last = events.lastOrNull()
            val heroes = s.heroes.ifEmpty { s.names.keys.toList() }
            Room(
                id = s.id, name = Matrix.roomName(s.state, heroes, s.names, me),
                lastText = when (last?.kind) {
                    null -> ""
                    Event.Kind.Encrypted -> context.getString(R.string.ch_encrypted_short)
                    Event.Kind.Image -> context.getString(R.string.ch_photo)
                    Event.Kind.File -> context.getString(R.string.ch_file)
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
            data = if (kind == Event.Kind.Text) null else content.toString(), replyTo = Relations.replyTo(content))
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
        val content = e.data?.let { runCatching { JSONObject(it) }.getOrNull() }?.takeIf { e.kind == Event.Kind.Image || e.kind == Event.Kind.File }
            ?: JSONObject().put("msgtype", "m.text").put("body", relations.edited(e.id, e.sender) ?: e.text)
        content.remove("m.relates_to")
        sendContent(toRoom, content, if (e.kind == Event.Kind.Image || e.kind == Event.Kind.File) e.kind else Event.Kind.Text, failed)
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
        val name = (content?.optString("filename")?.ifEmpty { null } ?: e.text).ifBlank { "file" }
            .replace(Regex("[/\\\\:*?\"<>|\\n]"), "-").take(120)
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "chat").apply { mkdirs() }
                val out = File(dir, name)
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

    /** Il server dell'accesso in corso nel browser (torna con [finishSso]). */
    var pendingHomeserver: String?
        get() = context.getSharedPreferences("chat_login", Context.MODE_PRIVATE).getString("hs", null)
        set(v) { context.getSharedPreferences("chat_login", Context.MODE_PRIVATE).edit().putString("hs", v).apply() }

    fun finishSso(loginToken: String, done: (Boolean) -> Unit) {
        val hs = pendingHomeserver ?: return done(false)
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
        /** Pagine di storia lette al massimo per i conti delle spese (30 messaggi l'una). */
        const val MAX_PAGES = 200
        /** Il nome della chiave di recupero tra i segreti affidati all'app Eurise. */
        const val SUITE_SECRET = "chat.recovery_key"
        /** La Chat è aperta e sincronizza da sé: il lavoro in background non tocca le chiavi nello stesso momento. */
        @Volatile var active = false
    }
}
