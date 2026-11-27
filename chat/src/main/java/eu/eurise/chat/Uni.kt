package eu.eurise.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.chat.People.Channel
import eu.eurise.core.suite.PeopleContract
import eu.eurise.core.suite.PeopleContract.App
import eu.eurise.core.suite.PeopleContract.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * «Una persona, tutti i canali» nella Chat: la vista Persone (le ultime cose di Chat, Messaggi, Telefono ed Email messe
 * insieme per persona) e il filo di una persona. I dati delle altre app arrivano dai loro fornitori con il permesso di
 * firma ([PeopleContract]) e restano in memoria finché la Chat è aperta: nessuna copia sul disco, nessun server.
 */
class Uni(private val context: Context, private val scope: CoroutineScope, private val model: ChatModel) {
    private val prefs = context.getSharedPreferences("persone", Context.MODE_PRIVATE)

    var rows by mutableStateOf<List<People.Row>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** Vero se almeno una tra Messaggi, Telefono ed Email ha risposto (altrimenti la vista Persone è solo la Chat). */
    var linked by mutableStateOf(false)
        private set
    /** Vista dell'elenco: Persone, Solo Chat o Non lette. */
    var view by mutableStateOf(runCatching { View.valueOf(prefs.getString("view", View.People.name)!!) }.getOrDefault(View.People))
        private set
    /** «Rispondi dove ti ha scritto» (tavola UniCanale), acceso di base. */
    var auto by mutableStateOf(prefs.getBoolean("auto", true))
        private set

    /** La persona aperta (tavola UniConversazione) e il suo filo da Messaggi, Telefono ed Email (la Chat si aggiunge viva). */
    var who by mutableStateOf<People.Who?>(null)
        private set
    var thread by mutableStateOf<List<People.Entry>>(emptyList())
        private set
    var threadLoading by mutableStateOf(false)
        private set

    enum class View { People, Chat, Unread }

    fun changeView(v: View) { view = v; prefs.edit().putString("view", v.name).apply() }
    fun changeAuto(on: Boolean) { auto = on; prefs.edit().putBoolean("auto", on).apply() }

    fun preferred(key: String): Channel? = prefs.getString("pref_$key", null)?.let { runCatching { Channel.valueOf(it) }.getOrNull() }
    fun setPreferred(key: String, c: Channel) { prefs.edit().putString("pref_$key", c.name).apply(); preferredTick++ }
    /** Cambia a ogni stellina, per ridisegnare. */
    var preferredTick by mutableStateOf(0)
        private set

    // --- Vista Persone ---

    /** Le ultime cose delle altre app (lette a ogni [refresh]) e chi c'è in Rubrica dietro ogni indirizzo (si ricorda). */
    private var others: List<People.Seen> = emptyList()
    private val contacts = HashMap<String, People.Contact?>()

    /** Rilegge Messaggi, Telefono ed Email; con [full] falso rifà solo la parte della Chat (arriva un messaggio). */
    fun refresh(full: Boolean = true) {
        if (full && loading) return
        if (full) loading = true
        val chat = chatSeen()
        scope.launch {
            var any: Boolean? = null
            val out = withContext(Dispatchers.IO) {
                synchronized(contacts) {
                    if (full) {
                        val sms = PeopleContract.recent(context, App.Messages)
                        val calls = PeopleContract.recent(context, App.Phone)
                        val mails = PeopleContract.recent(context, App.Mail)
                        any = sms.isNotEmpty() || calls.isNotEmpty() || mails.isNotEmpty() || installed()
                        others = (sms + calls + mails).map { r -> seen(r) }
                    }
                    val all = chat + others
                    resolveMissing(all)
                    People.group(all, all.map { s -> link(s.address) ?: contacts[key(s)] })
                }
            }
            rows = out
            any?.let { linked = it }
            if (full) loading = false
        }
    }

    private fun installed() = App.entries.any { app ->
        runCatching { context.packageManager.getPackageInfo(PeopleContract.packageOf(context, app), 0); true }.getOrDefault(false)
    }

    /** Le chat a due della Chat, come «ultime cose» di un utente. I gruppi restano stanze (li mostra l'elenco). */
    private fun chatSeen(): List<People.Seen> = model.rooms.filter { !it.invite }.mapNotNull { r ->
        val peer = model.peerOf(r.id) ?: return@mapNotNull null
        People.Seen(Channel.Chat, peer.lowercase(), People.Entry("room:" + r.id, Channel.Chat, r.lastTime, true, r.lastText), r.unread, r.name, r.id)
    }

    private fun seen(r: PeopleContract.Recent): People.Seen {
        val e = entry(r.item)
        return People.Seen(e.channel, r.item.address, e, r.unread, r.item.name)
    }

    private fun key(s: People.Seen) = People.addressKey(s.channel, s.address)

    private fun resolveMissing(all: List<People.Seen>) {
        val missing = all.filter { key(it) !in contacts }.distinctBy { key(it) }.take(400)
        if (missing.isEmpty()) return
        val addresses = missing.map { s ->
            when (s.channel) {
                Channel.Chat -> PeopleContract.mx(s.address, s.name)
                Channel.Email -> PeopleContract.mailto(s.address)
                Channel.Sms, Channel.Call -> PeopleContract.tel(s.address)
            }
        }
        val (people, index) = PeopleContract.resolve(context, addresses)
        missing.forEachIndexed { i, s ->
            contacts[key(s)] = index.getOrNull(i)?.takeIf { it >= 0 }?.let { people.getOrNull(it) }?.let { People.Contact(it.key, it.name, it.phones, it.emails, it.matrix) }
        }
    }

    /** Il filo di una cosa delle altre app. */
    private fun entry(i: PeopleContract.Item): People.Entry {
        val channel = when (i.kind) {
            Kind.SmsIn, Kind.SmsOut -> Channel.Sms
            Kind.CallIn, Kind.CallOut, Kind.CallMissed -> Channel.Call
            Kind.MailIn, Kind.MailOut -> Channel.Email
        }
        val incoming = i.kind in setOf(Kind.SmsIn, Kind.CallIn, Kind.CallMissed, Kind.MailIn)
        return People.Entry(
            "${i.kind}:${i.time}:${i.ref}:${i.address}", channel, i.time, incoming, i.text, i.subject, i.duration, i.note,
            missed = i.kind == Kind.CallMissed, ref = i.ref, address = i.address,
        )
    }

    // --- Unire una persona della Chat a un contatto della Rubrica ---

    private fun link(address: String): People.Contact? = prefs.getString("link_" + address.lowercase(), null)?.let { s ->
        runCatching {
            val o = JSONObject(s)
            fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            People.Contact(o.getString("key"), o.getString("name"), list("phones"), list("emails"), list("matrix") + address.lowercase())
        }.getOrNull()
    }

    fun search(query: String, done: (List<PeopleContract.Person>) -> Unit) {
        scope.launch { done(withContext(Dispatchers.IO) { PeopleContract.search(context, query) }) }
    }

    /** L'utente della Chat [userId] è il contatto [p]: da qui in poi una riga sola. */
    fun linkTo(userId: String, p: PeopleContract.Person) {
        val o = JSONObject().put("key", p.key).put("name", p.name).put("phones", JSONArray(p.phones)).put("emails", JSONArray(p.emails))
            .put("matrix", JSONArray(p.matrix))
        prefs.edit().putString("link_" + userId.lowercase(), o.toString()).apply()
        who = who?.let { w -> w.copy(key = p.key, name = p.name, phones = p.phones, emails = p.emails, matrix = (w.matrix + p.matrix).distinct(), contact = true) }
        loadThread()
        refresh()
    }

    // --- Il filo di una persona ---

    fun open(row: People.Row) {
        val w = row.who
        who = w.copy(roomId = w.roomId ?: w.matrix.firstNotNullOfOrNull { model.directWith(it) })
        thread = emptyList()
        loadThread()
        markRead(w)
    }

    /**
     * Aprendo il filo di una persona, ciò che era da leggere diventa letto anche nelle sue app: SMS (Messaggi), chiamate
     * perse (Telefono), email (Email). Così «Non lette» si svuota davvero.
     */
    private fun markRead(w: People.Who) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { PeopleContract.markRead(context, App.Messages, w.phones, emptyList()) }
                runCatching { PeopleContract.markRead(context, App.Phone, w.phones, emptyList()) }
                runCatching { PeopleContract.markRead(context, App.Mail, emptyList(), w.emails) }
            }
            refreshAfterRead()
        }
    }

    private fun refreshAfterRead() { loading = false; refresh(true) }

    /** «Segna tutte come lette» nella vista Non lette. */
    fun markAllRead(rows: List<People.Row>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val phones = rows.flatMap { it.who.phones }.distinct()
                val emails = rows.flatMap { it.who.emails }.distinct()
                runCatching { PeopleContract.markRead(context, App.Messages, phones, emptyList()) }
                runCatching { PeopleContract.markRead(context, App.Phone, phones, emptyList()) }
                runCatching { PeopleContract.markRead(context, App.Mail, emptyList(), emails) }
            }
            refreshAfterRead()
        }
    }

    fun close() { who = null; thread = emptyList() }

    fun loadThread() {
        val w = who ?: return
        threadLoading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                (PeopleContract.items(context, App.Messages, w.phones, emptyList()) +
                    PeopleContract.items(context, App.Phone, w.phones, emptyList()) +
                    PeopleContract.items(context, App.Mail, emptyList(), w.emails)).map(::entry)
            }
            if (who?.key == w.key) { thread = list; threadLoading = false }
        }
    }

    /**
     * Manda [text] a chi è aperto con [channel]: in Chat nella stanza a due (creata se non c'è ancora), per SMS da
     * Messaggi, per email aprendo Scrivi in Email con il testo già scritto.
     */
    fun send(text: String, channel: Channel, actions: ChatActions) {
        val w = who ?: return
        when (channel) {
            Channel.Chat -> {
                val room = w.roomId
                if (room != null) model.send(room, text) { actions.toast(actions.text(R.string.ch_send_failed)) }
                else w.matrix.firstOrNull()?.let { mx ->
                    model.newChat(mx) { id, err ->
                        if (id != null) { who = who?.copy(roomId = id); model.send(id, text) { actions.toast(actions.text(R.string.ch_send_failed)) } }
                        else actions.toast(actions.text(err ?: R.string.ch_send_failed))
                    }
                }
            }
            Channel.Sms -> {
                val number = thread.lastOrNull { it.channel == Channel.Sms }?.address?.takeIf { it.isNotBlank() } ?: w.phones.firstOrNull() ?: return
                val local = People.Entry("local:" + System.nanoTime(), Channel.Sms, System.currentTimeMillis(), false, text, address = number)
                thread = thread + local
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { PeopleContract.sendSms(context, number, text) }
                    if (!ok) {
                        thread = thread - local
                        actions.toast(actions.text(R.string.pe_sms_failed))
                        actions.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", text))
                    } else {
                        delay(2500)
                        loadThread()
                    }
                }
            }
            Channel.Email -> w.emails.firstOrNull()?.let { actions.launch(PeopleContract.composeMailIntent(context, it, text)) }
            Channel.Call -> call(actions)
        }
    }

    /** Chiama con un tocco con Eurise Telefono (o apre il tastierino di sistema col numero). */
    fun call(actions: ChatActions, number: String? = null) {
        val w = who
        val n = number ?: thread.lastOrNull { it.channel == Channel.Call }?.address?.takeIf { it.isNotBlank() } ?: w?.phones?.firstOrNull() ?: return
        actions.launch(PeopleContract.callIntent(context, n) ?: Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", n, null)))
    }

    // --- Invito sulla Chat (tavole ChatInvita e ChatInvitaElenco) ---

    /** L'invito per una persona sola (dalla scheda della Rubrica o dal filo), e l'elenco di chi invitare. */
    var invite by mutableStateOf<People.Who?>(null)
        private set
    var inviteUsed by mutableStateOf<Set<Channel>>(emptySet())
        private set
    var inviteList by mutableStateOf(false)
        private set

    fun openInvite(who: People.Who, used: Set<Channel> = emptySet()) { invite = who; inviteUsed = used }
    fun closeInvite() { invite = null }
    fun openInviteList(on: Boolean) { inviteList = on }

    /** Il link alla chat con me (account aperto), o null se non si è collegati. */
    fun inviteLink(): String? = model.session?.userId?.let(People::inviteLink)

    /** Quando è stata invitata questa persona (per non riproporla), o null. */
    fun invited(key: String): Long? = prefs.getLong("inv_$key", 0L).takeIf { it > 0 }

    /** Manda l'invito: per SMS da Messaggi (o l'app degli SMS se non parte), per email aprendo Scrivi già compilato. */
    fun sendInvite(who: People.Who, channel: Channel, address: String, text: String, actions: ChatActions, done: (Boolean) -> Unit) {
        val mark = { prefs.edit().putLong("inv_${who.key}", System.currentTimeMillis()).apply(); preferredTick++ }
        when (channel) {
            Channel.Sms -> scope.launch {
                val ok = withContext(Dispatchers.IO) { PeopleContract.sendSms(context, address, text) }
                if (!ok) actions.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(address))).putExtra("sms_body", text))
                mark()
                done(ok)
            }
            Channel.Email -> {
                actions.launch(PeopleContract.composeMailIntent(context, address, text, actions.text(R.string.inv_subject)))
                mark()
                done(true)
            }
            else -> done(false)
        }
    }

    fun openMail(ref: String, actions: ChatActions) {
        PeopleContract.openMailIntent(context, ref)?.let(actions::launch) ?: actions.toast(actions.text(R.string.pe_no_mail))
    }

    companion object {
        /** I contatti della Rubrica già uniti a una persona della Chat (per la scheda della Rubrica). */
        fun linkedKeys(context: Context): Set<String> = context.getSharedPreferences("persone", Context.MODE_PRIVATE).all
            .filterKeys { it.startsWith("link_") }.values.mapNotNull { v -> runCatching { JSONObject(v as String).getString("key") }.getOrNull() }.toSet()
    }
}
