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
    private val store = SessionStore(context)
    var session by mutableStateOf(store.load())
        private set
    private val client get() = session?.let(::ChatClient)

    val rooms = mutableStateListOf<Room>()
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
                }.onFailure { e ->
                    if (e is MatrixException && e.code == "M_UNKNOWN_TOKEN") { forget(); return@launch }
                    error = context.getString(R.string.ch_offline)
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    fun stop() { loop?.cancel(); loop = null; active = false }

    /** I messaggi decifrati in ritardo (la chiave è arrivata dopo) prendono il posto di quelli «cifrati». */
    private fun resolve(solved: List<Pair<String, JSONObject>>) {
        if (solved.isEmpty()) return
        solved.forEach { (roomId, json) ->
            val e = Matrix.event(json, data[roomId]?.names.orEmpty()) ?: return@forEach
            val list = timeline(roomId)
            val i = list.indexOfFirst { it.id == e.id }
            if (i >= 0) list[i] = e
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
                val fresh = Matrix.events(tlEvents, s.names).filter { ev -> list.none { it.id == ev.id } }
                // L'eco locale di un messaggio inviato da qui lascia il posto a quello vero.
                if (fresh.isNotEmpty()) list.removeAll { it.id.startsWith("local:") && fresh.any { f -> f.sender == me && f.text == it.text } }
                list.addAll(fresh)
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
        val last = timeline(roomId).lastOrNull { !it.id.startsWith("local:") } ?: return
        val c = client ?: return
        data[roomId]?.unread = 0
        publish()
        scope.launch(Dispatchers.IO) { c.read(roomId, last.id) }
    }

    /** I messaggi più vecchi, quando si arriva in cima. */
    fun older(roomId: String) {
        val s = data[roomId] ?: return
        val from = s.prevBatch ?: return
        val c = client ?: return
        s.prevBatch = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { c.messages(roomId, from) }.getOrNull()?.also { r ->
                    val a = r.optJSONArray("chunk") ?: return@also
                    val cr = cryptoFor(c.session) ?: return@also
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        if (e.optString("type") == "m.room.encrypted") cr.decrypt(e, roomId)?.let { a.put(i, it) }
                    }
                }
            } ?: run { s.prevBatch = from; return@launch }
            val chunk = Matrix.events(r.optJSONArray("chunk"), s.names).reversed()
            val list = timeline(roomId)
            list.addAll(0, chunk.filter { e -> list.none { it.id == e.id } })
            s.prevBatch = r.optString("end").ifEmpty { null }
        }
    }

    fun send(roomId: String, text: String, failed: () -> Unit) {
        val c = client ?: return
        val me = session?.userId.orEmpty()
        val local = Event("local:" + System.nanoTime(), me, text, System.currentTimeMillis(), Event.Kind.Text)
        timeline(roomId).add(local)
        val encrypted = data[roomId]?.encrypted == true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (encrypted) {
                        val cr = cryptoFor(c.session) ?: error("crypto")
                        val content = JSONObject().put("msgtype", "m.text").put("body", text)
                        c.sendEncrypted(roomId, cr.encrypt(c, roomId, c.joinedMembers(roomId), "m.room.message", content))
                    } else c.sendText(roomId, text)
                }.isSuccess
            }
            if (!ok) { timeline(roomId).remove(local); failed() }
        }
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

    /** Scarica un'immagine o un file della chat nella cache, per aprirlo. */
    fun media(e: Event, done: (File?) -> Unit) {
        val s = session ?: return
        val url = e.mediaUrl?.let { Matrix.mediaUrl(s.homeserver, it, thumbnail = false) } ?: return done(null)
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "chat").apply { mkdirs() }
                runCatching { ChatClient(s).download(url, File(dir, e.text.ifBlank { "file" }.replace('/', '-'))) }.getOrNull()
            }
            done(f)
        }
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
        ChatWorker.schedule(context)
        start()
    }

    fun logout() {
        val c = client
        scope.launch(Dispatchers.IO) { c?.logout() }
        forget()
    }

    private fun forget() {
        stop()
        store.clear()
        session = null
        since = null
        rooms.clear(); data.clear(); timelines.clear()
        crypto = null
        pending.clear()
        ready = false
        ChatWorker.cancel(context)
    }

    companion object {
        const val REDIRECT = "eurisechat://login"
        /** La Chat è aperta e sincronizza da sé: il lavoro in background non tocca le chiavi nello stesso momento. */
        @Volatile var active = false
    }
}
