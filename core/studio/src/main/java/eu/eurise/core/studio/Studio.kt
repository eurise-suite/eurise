package eu.eurise.core.studio

import java.security.SecureRandom
import java.text.Normalizer

/** Medicina generale (convenzionata) o agopuntura (privata): la coda si guarda insieme o separata. */
enum class Practice { General, Acupuncture }

/** Da dove arriva una richiesta e da dove parte la risposta. */
enum class Channel { Chat, Email, Sms }

/**
 * I consensi del paziente (tavola StuPaziente). [Notice]: informativa sul trattamento dei dati consegnata;
 * [OnlineReports]: referti online (facoltativo, linee guida del Garante 2009); [Reminders]: promemoria degli appuntamenti.
 */
enum class Consent { Notice, OnlineReports, Reminders }

/** Un consenso dato o revocato: non si cancella mai, la storia resta scritta. */
data class ConsentEvent(val consent: Consent, val granted: Boolean, val at: Long)

/** Una voce della terapia abituale: quella che il paziente può chiedere senza visita. */
data class Therapy(val id: String, val name: String, val dose: String = "", val since: String = "")

data class Patient(
    val id: String,
    val name: String,
    val practices: Set<Practice> = setOf(Practice.General),
    val matrixId: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val preferred: Channel = Channel.Chat,
    /** Quando l'identità è stata verificata in studio (QR e codice), e da quale canale è arrivato il codice. */
    val verifiedAt: Long? = null,
    val verifiedBy: Channel? = null,
    val consents: List<ConsentEvent> = emptyList(),
    val therapies: List<Therapy> = emptyList(),
    val createdAt: Long = 0L,
) {
    /** Vale l'ultimo evento: un consenso revocato è revocato da subito. */
    fun has(c: Consent): Boolean = consents.lastOrNull { it.consent == c }?.granted == true

    /** Da quando vale il consenso [c] (o null se non è dato). */
    fun since(c: Consent): Long? = consents.lastOrNull { it.consent == c }?.takeIf { it.granted }?.at

    fun withConsent(c: Consent, granted: Boolean, at: Long): Patient =
        if (has(c) == granted) this else copy(consents = consents + ConsentEvent(c, granted, at))

    /** I canali che si possono usare davvero (c'è l'indirizzo). */
    fun channels(): List<Channel> = buildList {
        if (matrixId != null) add(Channel.Chat)
        if (email != null) add(Channel.Email)
        if (phone != null) add(Channel.Sms)
    }

    val verified get() = verifiedAt != null
}

/** Che cosa chiede chi scrive. L'ordine è quello della coda: le urgenze sempre in cima. */
enum class Need { Urgent, Prescription, Appointment, Report, Question, Thanks }

/** Una richiesta nella coda dello Studio. [ref] la ritrova nell'app d'origine (stanza ed evento, messaggio, SMS). */
data class Request(
    val id: String,
    val patientId: String,
    val channel: Channel,
    val need: Need,
    val practice: Practice,
    val text: String,
    val time: Long,
    val ref: String,
    val doneAt: Long? = null,
) {
    val open get() = doneAt == null
}

/** Un codice di verifica mostrato in studio, valido [Verification.VALID_MS]. */
data class PendingCode(val patientId: String, val code: String, val createdAt: Long)

/** Tutto lo Studio: si salva cifrato sul telefono (Chat) e non esce da lì. */
data class StudioState(
    val enabled: Boolean = false,
    /** Da quando lo Studio è acceso: i messaggi più vecchi non entrano in coda. */
    val enabledAt: Long = 0L,
    val patients: List<Patient> = emptyList(),
    val requests: List<Request> = emptyList(),
    val codes: List<PendingCode> = emptyList(),
    /** Orari e contatti dello studio, mandati nelle chat dei pazienti di La mia Bussola. */
    val info: Bussola.StudioInfo? = null,
) {
    fun patient(id: String) = patients.firstOrNull { it.id == id }
}

/** Testo senza accenti e in minuscolo, per confrontare parole scritte di fretta. */
internal fun plain(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('’', '\'')

/**
 * Chi è chi: un indirizzo (Matrix, email, telefono) diventa un paziente. I numeri si confrontano senza prefisso
 * internazionale italiano, spazi e trattini.
 */
object Addresses {
    fun phone(raw: String): String {
        var d = raw.filter { it.isDigit() || it == '+' }
        if (d.startsWith("+39")) d = d.removePrefix("+39") else if (d.startsWith("0039")) d = d.removePrefix("0039")
        return d.filter { it.isDigit() }
    }

    fun email(raw: String): String = raw.trim().lowercase()

    fun matches(p: Patient, channel: Channel, address: String): Boolean = when (channel) {
        Channel.Chat -> p.matrixId != null && p.matrixId.equals(address.trim(), ignoreCase = true)
        Channel.Email -> p.email != null && email(p.email) == email(address)
        Channel.Sms -> p.phone != null && phone(p.phone).let { it.length >= 6 && it == phone(address) }
    }

    fun find(patients: List<Patient>, channel: Channel, address: String): Patient? =
        patients.firstOrNull { matches(it, channel, address) }
}

/**
 * La verifica in studio (tavola StuPaziente, «Verificato in studio con il QR»): il telefono del medico mostra il QR che
 * apre la chat con lo studio e un codice breve; il paziente lo scrive in Chat (anche con Element), per email o per SMS.
 * Quando il codice arriva da un indirizzo, quell'indirizzo è proprio del paziente. Niente lettere che si confondono.
 */
object Verification {
    const val VALID_MS = 30 * 60_000L
    private const val ALPHABET = "ACDEFHJKMNPQRTUVWXY3479"
    private val random = SecureRandom()

    /** «K7Q-2MX». */
    fun newCode(): String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("").let { it.take(3) + "-" + it.drop(3) }

    private fun squash(s: String) = s.uppercase().filter { it.isLetterOrDigit() }

    /** Il codice ancora valido scritto in [text], se c'è. */
    fun find(text: String, codes: List<PendingCode>, now: Long): PendingCode? {
        val t = squash(text)
        if (t.length < 6) return null
        return codes.firstOrNull { now - it.createdAt <= VALID_MS && t.contains(squash(it.code)) }
    }

    fun alive(codes: List<PendingCode>, now: Long) = codes.filter { now - it.createdAt <= VALID_MS }
}

/** Il riepilogo dello spazio Studio (tavola StuSpazio). */
data class Summary(
    val urgent: List<Request>,
    /** Per ogni bisogno (escluse le urgenze), le richieste aperte, dalla più vecchia. */
    val byNeed: Map<Need, List<Request>>,
    val byChannel: Map<Channel, Int>,
)

object Queue {
    /** Le richieste aperte di [practice] (null = tutte), urgenze a parte, poi per bisogno e per canale. */
    fun summary(state: StudioState, practice: Practice?): Summary {
        val open = state.requests.filter { it.open && (practice == null || it.practice == practice) }
        return Summary(
            urgent = open.filter { it.need == Need.Urgent }.sortedByDescending { it.time },
            byNeed = Need.entries.filter { it != Need.Urgent }.associateWith { n -> open.filter { it.need == n }.sortedBy { it.time } }
                .filterValues { it.isNotEmpty() },
            byChannel = Channel.entries.associateWith { c -> open.count { it.channel == c } },
        )
    }

    /** Le richieste di un paziente, dalla più recente (la «storia» della scheda). */
    fun history(state: StudioState, patientId: String) = state.requests.filter { it.patientId == patientId }.sortedByDescending { it.time }

    /** I nomi, senza ripetizioni, per la riga «Esposito, Colombo, Ferraro». */
    fun names(state: StudioState, list: List<Request>): List<String> =
        list.mapNotNull { state.patient(it.patientId)?.name }.distinct()

    /**
     * Che cosa succede quando arriva un messaggio da [address] su [channel]. Il codice di verifica ha la precedenza: lega
     * l'indirizzo al paziente. Poi, se è di un paziente e non è già in coda ([ref]), diventa una richiesta.
     * Lo Studio spento o un messaggio più vecchio dell'accensione non cambiano nulla.
     */
    fun receive(
        state: StudioState, channel: Channel, address: String, text: String, time: Long, ref: String, now: Long, newId: () -> String,
    ): Pair<StudioState, Outcome> {
        if (!state.enabled || text.isBlank()) return state to Outcome.Ignored
        Verification.find(text, state.codes, now)?.let { code ->
            val p = state.patient(code.patientId) ?: return@let
            val bound = when (channel) {
                Channel.Chat -> p.copy(matrixId = address.trim())
                Channel.Email -> p.copy(email = Addresses.email(address))
                Channel.Sms -> p.copy(phone = address.trim())
            }.copy(verifiedAt = now, verifiedBy = channel)
            return state.copy(
                patients = state.patients.map { if (it.id == p.id) bound else it },
                codes = state.codes.filter { it.patientId != p.id },
            ) to Outcome.Verified(bound)
        }
        if (time < state.enabledAt) return state to Outcome.Ignored
        val p = Addresses.find(state.patients, channel, address) ?: return state to Outcome.Ignored
        val need = Triage.need(text, p.therapies.map { it.name })
        // Già in coda: un testo più completo (l'email aperta dopo l'oggetto) la aggiorna, finché è da fare.
        state.requests.firstOrNull { it.ref == ref }?.let { old ->
            if (!old.open || text.trim().length <= old.text.length) return state to Outcome.Ignored
            val fuller = old.copy(text = text.trim().take(400), need = need, practice = Triage.practice(text, p.practices))
            return state.copy(requests = state.requests.map { if (it.id == old.id) fuller else it }) to Outcome.Queued(p, fuller)
        }
        val r = Request(newId(), p.id, channel, need, Triage.practice(text, p.practices), text.trim().take(400), time, ref)
        return state.copy(requests = state.requests + r) to Outcome.Queued(p, r)
    }

    sealed interface Outcome {
        data object Ignored : Outcome
        data class Verified(val patient: Patient) : Outcome
        data class Queued(val patient: Patient, val request: Request) : Outcome
    }
}
