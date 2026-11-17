package eu.eurise.core.studio

import org.json.JSONArray
import org.json.JSONObject

/**
 * «La mia Bussola» in Chat (specifiche in docs/stato/BUSSOLA-CHAT.md; tavole Bus*, approvate il 17 novembre): il lato
 * paziente del gestionale BUSSOLA MMG come messaggi con un tipo nella stanza cifrata tra paziente e studio.
 * Sono `m.room.message` con un `msgtype` proprio e un `body` di riserva, così Element e le altre app Matrix mostrano
 * comunque un testo chiaro. Oggi li scrive il telefono del medico (con Millewin); domani anche Bussola MMG, uguali.
 *
 * Nessuna interpretazione automatica lato paziente (fuori dalla MDR): qui solo formati, stati e date.
 */
object Bussola {
    const val PREFIX = "it.bussola."
    const val RX_REQUEST = "it.bussola.rx_request"
    const val RX_STATUS = "it.bussola.rx_status"
    const val CHECKUP_INVITE = "it.bussola.checkup_invite"
    const val CHECKUP_REPLY = "it.bussola.checkup_reply"
    const val STUDIO = "it.bussola.studio"
    const val THERAPY = "it.bussola.therapy"

    /** La sola frase delle notifiche: mai dati sanitari. */
    const val NOTICE = "Hai ricevuto una nuova comunicazione da parte del tuo medico su La mia Bussola"
    const val URGENT = "Per un'urgenza chiama il 118: i messaggi non sono letti in tempo reale."
    const val VALID_DAYS = 30
    private const val DAY = 86_400_000L

    val CHECKUP_CHOICES = listOf("Ho prenotato", "L'ho già fatto", "Mi serve la richiesta", "Vorrei parlarne con il medico")

    enum class RxState { Received, Preparing, Ready, Review, Collected }

    /** Come appare una ricetta al paziente (tavola BusRicette). */
    enum class Prescription { Preparing, Valid, Collected, Expired, Review }

    data class RxRequest(val id: String, val drugs: List<String>)
    data class RxStatus(val requestId: String, val state: RxState, val nre: String? = null, val note: String? = null)
    data class Invite(val id: String, val text: String)
    data class Reply(val inviteId: String, val choice: String)
    data class StudioInfo(
        val name: String,
        val hours: String = "",
        val phone: String = "",
        val address: String = "",
        val guard: String = "",
        val absence: String = "",
        val substitute: String = "",
    )

    fun isBussola(msgtype: String) = msgtype.startsWith(PREFIX)

    /** Il codice della ricetta elettronica: 15 caratteri, lettere maiuscole e cifre (spazi e trattini tolti). */
    fun nre(input: String): String? = input.uppercase().filter { it.isLetterOrDigit() }.takeIf { it.length == 15 }

    /** «1500A 4XXXX XXXXX»: a gruppi di cinque, da leggere al banco. */
    fun nreGroups(nre: String) = nre.chunked(5).joinToString(" ")

    // --- Contenuti (con il testo di riserva) ---

    private fun content(type: String, body: String, data: JSONObject) =
        JSONObject().put("msgtype", type).put("body", body).put(type, data)

    fun request(r: RxRequest) = content(RX_REQUEST, "Richiesta di rinnovo: " + r.drugs.joinToString(", "),
        JSONObject().put("id", r.id).put("drugs", JSONArray(r.drugs)))

    fun status(s: RxStatus): JSONObject {
        val body = when (s.state) {
            RxState.Received -> "Richiesta ricevuta dallo studio."
            RxState.Preparing -> "La ricetta è in preparazione."
            RxState.Ready -> "Ricetta pronta. Codice NRE: ${s.nre?.let(::nreGroups).orEmpty()}. In farmacia basta la tessera sanitaria. Vale $VALID_DAYS giorni."
            RxState.Review -> "Da rivedere con il medico." + (s.note?.let { " $it" } ?: "")
            RxState.Collected -> "Ricetta ritirata."
        }
        return content(RX_STATUS, body, JSONObject().put("request", s.requestId).put("state", s.state.name).putOpt("nre", s.nre).putOpt("note", s.note))
            .put("m.relates_to", JSONObject().put("rel_type", "m.reference").put("event_id", s.requestId))
    }

    fun invite(i: Invite) = content(CHECKUP_INVITE, i.text + "\n(Rispondi: " + CHECKUP_CHOICES.joinToString(" · ") + ")",
        JSONObject().put("id", i.id).put("text", i.text))

    fun reply(r: Reply) = content(CHECKUP_REPLY, r.choice, JSONObject().put("invite", r.inviteId).put("choice", r.choice))

    fun studio(s: StudioInfo) = content(STUDIO, "Orari e contatti dello studio aggiornati.", JSONObject()
        .put("name", s.name).put("hours", s.hours).put("phone", s.phone).put("address", s.address)
        .put("guard", s.guard).put("absence", s.absence).put("substitute", s.substitute))

    /** La terapia abituale che il paziente può chiedere: va solo nella sua stanza, cifrata. */
    fun therapy(drugs: List<String>) = content(THERAPY, "Terapia abituale aggiornata: " + drugs.joinToString(", "),
        JSONObject().put("drugs", JSONArray(drugs)))

    // --- Referti (tavola BusReferto) ---

    /**
     * Un referto è un file vero (`m.file` o `m.image`, cifrato come ogni allegato della stanza) con un campo in più,
     * [REPORT]: così Element lo mostra e lo scarica, e La mia Bussola sa che è un referto, chi l'ha mandato, le due
     * righe per il medico e fino a quando resta visibile. Nessuna lettura del contenuto: il significato lo spiega il medico.
     */
    const val REPORT = "it.bussola.report"
    const val REPORT_DAYS = 45
    const val REPORT_MAX_BYTES = 10L * 1024 * 1024
    const val REPORT_NOTE_MAX = 200
    val REPORT_TYPES = listOf("application/pdf", "image/jpeg", "image/png")

    data class Report(val name: String, val mime: String, val size: Long, val note: String, val sentAt: Long) {
        val expiresAt get() = sentAt + REPORT_DAYS * DAY
    }

    /** Perché un file non va bene come referto (null = va bene). */
    enum class ReportProblem { Type, TooBig, Empty }

    fun checkReport(mime: String?, size: Long): ReportProblem? = when {
        size <= 0L -> ReportProblem.Empty
        mime?.lowercase()?.substringBefore(';')?.trim() !in REPORT_TYPES -> ReportProblem.Type
        size > REPORT_MAX_BYTES -> ReportProblem.TooBig
        else -> null
    }

    /** Le due righe facoltative per il medico: al massimo due righe e [REPORT_NOTE_MAX] caratteri. */
    fun reportNote(input: String) = input.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString("\n").take(REPORT_NOTE_MAX)

    fun isReport(c: JSONObject) = c.optJSONObject(REPORT) != null

    /**
     * Il contenuto del messaggio: [file] è il blocco «file» degli allegati cifrati (con l'indirizzo già caricato).
     * Con le due righe il «body» è la didascalia e il nome sta in «filename» (Matrix 1.10, la mostrano anche Element X e
     * FluffyChat); senza, il «body» è il nome.
     */
    fun report(r: Report, file: JSONObject, width: Int? = null, height: Int? = null): JSONObject {
        val image = r.mime.startsWith("image/")
        val info = JSONObject().put("mimetype", r.mime).put("size", r.size)
        if (image && width != null && height != null && width > 0 && height > 0) info.put("w", width).put("h", height)
        return JSONObject()
            .put("msgtype", if (image) "m.image" else "m.file")
            .put("body", r.note.ifBlank { r.name })
            .put("filename", r.name)
            .put("info", info)
            .put("file", file)
            .put(REPORT, JSONObject().put("note", r.note).put("expires", r.expiresAt))
    }

    /** Il referto di un messaggio; [sentAt] è l'ora del server (la scadenza conta da lì, non da ciò che dice il mittente). */
    fun readReport(c: JSONObject, sentAt: Long): Report? {
        val d = c.optJSONObject(REPORT) ?: return null
        val info = c.optJSONObject("info") ?: JSONObject()
        val name = c.optString("filename").ifEmpty { c.optString("body") }.ifEmpty { "referto" }
        return Report(name, info.optString("mimetype"), info.optLong("size"), d.optString("note"), sentAt)
    }

    /** Dopo 45 giorni il referto non si mostra più, e chi l'ha mandato lo cancella dalla stanza. */
    fun reportExpired(sentAt: Long, now: Long) = now >= sentAt + REPORT_DAYS * DAY

    // --- Lettura ---

    private fun data(c: JSONObject, type: String): JSONObject? = c.takeIf { it.optString("msgtype") == type }?.optJSONObject(type)
    private fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }.filter { it.isNotBlank() }

    fun readRequest(c: JSONObject) = data(c, RX_REQUEST)?.let { RxRequest(it.optString("id"), strings(it.optJSONArray("drugs"))) }
    fun readStatus(c: JSONObject) = data(c, RX_STATUS)?.let { d ->
        val state = runCatching { RxState.valueOf(d.optString("state")) }.getOrNull() ?: return@let null
        RxStatus(d.optString("request"), state, d.optString("nre").ifEmpty { null }, d.optString("note").ifEmpty { null })
    }
    fun readInvite(c: JSONObject) = data(c, CHECKUP_INVITE)?.let { Invite(it.optString("id"), it.optString("text")) }
    fun readReply(c: JSONObject) = data(c, CHECKUP_REPLY)?.let { Reply(it.optString("invite"), it.optString("choice")) }
    fun readTherapy(c: JSONObject) = data(c, THERAPY)?.let { strings(it.optJSONArray("drugs")) }
    fun readStudio(c: JSONObject) = data(c, STUDIO)?.let {
        StudioInfo(it.optString("name"), it.optString("hours"), it.optString("phone"), it.optString("address"),
            it.optString("guard"), it.optString("absence"), it.optString("substitute"))
    }

    // --- Stati ---

    /** Un messaggio della stanza già letto: chi l'ha scritto, quando, che cosa. */
    data class Item(val eventId: String, val sender: String, val time: Long, val content: JSONObject)

    /**
     * Chi è lo studio in questa stanza: chi ha mandato per primo gli orari dello studio. Gli stati delle ricette, gli inviti,
     * gli orari e la terapia valgono solo se vengono da lì; il paziente non può cambiarli.
     */
    fun studioSender(items: List<Item>): String? = items.sortedBy { it.time }.firstOrNull { it.content.optString("msgtype") == STUDIO }?.sender

    fun latestStudio(items: List<Item>, studio: String?) =
        items.filter { it.sender == studio }.sortedBy { it.time }.mapNotNull { readStudio(it.content) }.lastOrNull()

    fun latestTherapy(items: List<Item>, studio: String?) =
        items.filter { it.sender == studio }.sortedBy { it.time }.mapNotNull { readTherapy(it.content) }.lastOrNull().orEmpty()

    /** L'ultimo stato di una richiesta (con l'ora), o «ricevuta» se lo studio non ha ancora scritto nulla. */
    fun statusOf(requestEvent: String, items: List<Item>, studio: String?): Pair<RxStatus, Long?> =
        items.sortedBy { it.time }
            .mapNotNull { i -> readStatus(i.content)?.takeIf { it.requestId == requestEvent }?.let { it to i } }
            // Gli stati li dà lo studio; «ritirata» può segnarla anche il paziente.
            .filter { (s, i) -> i.sender == studio || s.state == RxState.Collected }
            .map { (s, i) -> s to i.time }
            .lastOrNull() ?: (RxStatus(requestEvent, RxState.Received) to null)

    /**
     * Come vede il paziente una ricetta: pronta vale 30 giorni dall'invio del codice, poi scade se non ritirata. [readyAt]
     * è l'ora in cui è arrivata «pronta» (dopo «ritirata» conta lo stato, non l'ora).
     */
    fun prescription(status: RxStatus, readyAt: Long?, now: Long): Prescription = when (status.state) {
        RxState.Received, RxState.Preparing -> Prescription.Preparing
        RxState.Review -> Prescription.Review
        RxState.Collected -> Prescription.Collected
        RxState.Ready -> if (readyAt != null && now - readyAt > VALID_DAYS * DAY) Prescription.Expired else Prescription.Valid
    }

    fun validUntil(readyAt: Long) = readyAt + VALID_DAYS * DAY

    /** La risposta già data a un invito (l'ultima), se c'è. */
    fun replyTo(inviteId: String, items: List<Item>, patient: String): String? =
        items.filter { it.sender == patient }.sortedBy { it.time }.mapNotNull { readReply(it.content)?.takeIf { r -> r.inviteId == inviteId } }.lastOrNull()?.choice
}
