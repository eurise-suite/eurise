package eu.eurise.feature.assistant

import eu.eurise.core.data.model.QuickAdd
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Ciò che Alfred ha capito da una frase, prima di cercare persone e calendari. */
sealed interface Command {
    /** [said]: il nome com'era detto, quando si è scelta un'altra persona dalle proposte (per la memoria). */
    data class Call(val who: String, val said: String? = null) : Command
    data class Note(val text: String) : Command
    data class Task(val title: String, val due: LocalDate?, val time: LocalTime?) : Command
    data class Event(val title: String, val who: String?, val day: LocalDate?, val time: LocalTime?) : Command
    data class Navigate(val place: String) : Command
    /** Una segnalazione per Eurise Mappe, segnata subito dove si è («Alfred, autovelox»). */
    data class Report(val kind: ReportKind) : Command
    data class Message(val who: String, val text: String, val channel: Channel?, val said: String? = null) : Command
    /** «Ricorda che…»: una cosa da tenere a mente (memoria di Alfred). */
    data class Remember(val text: String) : Command
    data class Alarm(val time: LocalTime, val day: LocalDate?) : Command
    data class Timer(val minutes: Int) : Command
    data class Search(val query: String, val scope: Scope) : Command
    data class Open(val app: String) : Command
    /**
     * Una domanda sui dati dell'utente (tavola AlfDati): si risponde cercando sul telefono, niente esce.
     * [day] e [days]: il giorno e quanti giorni (7 per «questa settimana»); [about]: la cosa cercata.
     */
    data class Ask(val kind: AskKind, val day: LocalDate? = null, val about: String = "", val days: Int = 1) : Command
    /**
     * Un seguito dell'ultima cosa fatta, entro due minuti (tavola AlfSeguito): «spostalo alle 10», «cancellalo»,
     * «mandalo anche a Luca». [eventId]: un impegno scelto dalla risposta sui dati («Sposta il dentista»).
     */
    data class Follow(
        val action: FollowAction,
        val day: LocalDate? = null,
        val time: LocalTime? = null,
        val who: String = "",
        val eventId: Long? = null,
    ) : Command
    /**
     * Un impegno detto per nome (tavola AlfAzioni, «Impegni»): «sposta la riunione a giovedì», «cancella il dentista».
     * [day] e [time]: per spostare, dove va; per cancellare, [day] dice quale («il dentista di domani»).
     */
    data class Change(val delete: Boolean, val about: String, val day: LocalDate? = null, val time: LocalTime? = null) : Command
    /** «ho comprato il latte», «spunta il latte»: la cosa da fare si segna come fatta (tavola AlfAzioni, «Attività»). */
    data class Complete(val about: String) : Command
    /** Un'impostazione del telefono o la musica: si fa subito, senza scheda (tavola AlfAzioni, «Telefono» e «Musica»). */
    data class Device(val action: DeviceAction, val until: LocalTime? = null, val minutes: Int? = null, val mode: String? = null) : Command
    /** Sveglie e timer già messi: «ferma il timer», «quanto manca al timer?», «spegni la sveglia di domani». */
    data class Clock(val action: ClockAction, val day: LocalDate? = null) : Command
    /** «Rispondi che arrivo»: a chi ha scritto per ultimo (o di cui si sono appena letti i messaggi). */
    data class Reply(val text: String) : Command
    /** In guida con Eurise Mappe: «quanto manca?» ([who] vuoto) o «avvisa Anna che arrivo» (con l'ora d'arrivo). */
    data class Guide(val who: String = "") : Command
    /** «Cosa sai fare?»: l'elenco della tavola AlfAzioni. */
    data object Help : Command
    /** Nessuna regola la capisce: tocca a Eurise AI (richiesta libera). */
    data class Free(val text: String) : Command
}

enum class DeviceAction {
    TorchOn, TorchOff, QuietOn, QuietOff, VolumeMin, VolumeMax, VolumeUp, VolumeDown, Mute,
    Silent, Vibrate, Sound, ModeOn, ModeOff, Play, Pause, Next, Previous,
}

/** StopTimer: ferma i timer accesi (e quello che suona); TimerLeft: quanto manca; AlarmOff: salta la prossima sveglia. */
enum class ClockAction { StopTimer, TimerLeft, AlarmOff }

/** Move: nuovo giorno o ora; Undo: togliere ciò che si è appena fatto; AlsoTo: mandarlo anche a qualcun altro. */
enum class FollowAction { Move, Undo, AlsoTo }

/** Agenda: «cosa ho domani?»; When: «quando ho il dentista?»; Calls: «chi mi ha chiamato?»; Notes: «cosa avevo scritto sul cancello?». */
enum class AskKind { Agenda, When, Calls, Notes, Messages }

enum class Channel { WhatsApp, Sms, Telegram, Signal }

/** Le sei voci di «Segnala in guida» di Eurise Mappe (stessi nomi di EuriseGuideReports.Kind). */
enum class ReportKind { CAMERA, LIMIT, CLOSED, WORKS, QUEUE, DANGER }

enum class Scope { Web, Suite }

/**
 * La grammatica dei comandi più comuni, in italiano, senza modello: subito e sul telefono. Le date e le ore le legge
 * [QuickAdd] (la stessa dell'aggiunta rapida). Una frase che non segue nessuna regola diventa [Command.Free].
 */
object Grammar {

    private const val O = "(?iu)"

    /** Cortesie e vocativi che non cambiano il comando: «Alfred, per favore…», «puoi…». */
    private val POLITE_HEAD = Regex("$O^(?:(?:ehi|ok|senti)\\s+)?(?:alfred[,.!]?\\s*)?(?:per\\s+(?:favore|piacere)[,]?\\s+)?(?:puoi|potresti|vorrei che\\s+tu)?\\s*")
    private val POLITE_TAIL = Regex("$O[,\\s]*(?:per\\s+(?:favore|piacere)|grazie)[.!]?\\s*$")

    private val NOTE = Regex("$O^(?:(?:nuova\\s+)?nota|annota|prendi\\s+(?:nota|appunto)|(?:scrivi|crea|fai|aggiungi|apri)\\s+(?:una\\s+)?(?:nuova\\s+)?nota|appunta)(?![\\p{L}])\\s*(?:[:,]|che)?\\s*(.*)$")
    /** «Ricorda che…», «tieni a mente che…», «impara che…» (non «ricordami di…», che è un promemoria). */
    private val REMEMBER = Regex("$O^(?:ricorda|ricordati|tieni\\s+a\\s+mente|impara|sappi)\\s+che\\s+(.+)$")
    /** «Aggiungi al calendario…», «crea un evento…», «metti nel calendario…»: almeno una delle parole del calendario. */
    private val CALENDAR = Regex("$O^(?:aggiungi|metti|mettimi|inserisci|segna|segnami|crea|salva|fissa|fissami|programma)\\s+(?:(?:un|un'|l'|il|lo|nuovo)\\s*)?(?:(evento|appuntamento|impegno)\\s*)?(?:(?:in|nel|al|sul|a)\\s+(calendario|agenda))?\\s*[:,]?\\s*(.*)$")
    private val TASK = Regex("$O^(?:ricordami\\s+(?:di|che)|promemoria(?:\\s+per)?|da\\s+fare|aggiungi\\s+(?:alle\\s+cose\\s+da\\s+fare|un'?\\s*attività)|devo)(?![\\p{L}])\\s*[:,]?\\s*(.+)$")
    private val TIMER = Regex("$O^(?:metti\\s+(?:un\\s+)?|imposta\\s+(?:un\\s+)?)?timer\\s+(?:di|da|per)?\\s*(\\d+|un|uno|mezz'?ora|un'?ora)\\s*(minut[oi]|or[ae])?$")
    private val ALARM = Regex("$O^(?:metti\\s+la\\s+|imposta\\s+la\\s+)?(?:sveglia|svegliami)\\b\\s*(.*)$")
    private val CALL = Regex("$O^(?:chiama(?:mi)?|telefona(?:\\s+a)?|fai\\s+(?:una\\s+)?chiamata\\s+a)\\s+(.+)$")
    private val NAVIGATE = Regex("$O^(?:portami|accompagnami|naviga|navigatore|indicazioni|strada|andiamo|guidami)\\s+(?:per\\s+andare\\s+)?(?:(?:verso|fino)\\s+)?(?:(?:a|ad|al|allo|alla|ai|agli|alle|all'|in|nel|nella|da|dal|dalla|per)\\s+)?(.+)$")
    private val MESSAGE = Regex(
        "$O^(?:manda|mandami|invia|scrivi|di'|dì|dici)\\s+(?:un\\s+messaggio\\s+)?(?:a|ad)\\s+(.+?)" +
            "(?:\\s+(?:su|con|via|per|in)\\s+(whatsapp|sms|messaggio|telegram|signal))?\\s*(?:che|:|,)\\s+(.+)$"
    )
    private val EVENT = Regex("$O^(appuntamento|riunione|incontro|cena|pranzo|colazione|aperitivo|visita|metti\\s+in\\s+calendario|segna(?:\\s+in\\s+calendario)?|fissa(?:\\s+un\\s+appuntamento)?)\\b\\s*(.*)$")
    private val SEARCH = Regex("$O^(?:cerca|trova|cercami)\\s+(?:(nei\\s+documenti|nelle\\s+note|nella\\s+suite|sul\\s+telefono|sul\\s+web|in\\s+rete|su\\s+internet)\\s+)?(.+)$")
    private val OPEN = Regex("$O^(?:apri|avvia|lancia|aprimi)\\s+(?:l'|il\\s+|la\\s+|lo\\s+|app\\s+)?(.+)$")
    /** Segnala in guida (tavola MapSegnalaGuida): poche parole, senza verbo o con «segnala», «c'è». */
    private val REPORT = Regex("$O^(?:segnala(?:re)?\\s+|segnalo\\s+|c'è\\s+|c'e\\s+|ce\\s+|attenzione\\s+)?(?:una\\s+|uno\\s+|un'\\s*|un\\s+)?" +
        "(autovelox|velox|tutor|limite\\s+(?:di\\s+velocità\\s+)?(?:sbagliato|errato)|strada\\s+chiusa|lavori(?:\\s+in\\s+corso)?|cantiere|coda|traffico|incolonnamento|pericolo|ostacolo)$")
    // --- Cosa sa fare (tavola AlfAzioni) ---
    private val HELP = Regex("$O^(?:(?:che\\s+)?(?:cosa|cos')\\s*(?:sai|puoi)\\s+fare(?:\\s+per\\s+me)?|aiuto|aiutami|come\\s+funzioni|cosa\\s+posso\\s+(?:dirti|chiederti)|che\\s+comandi\\s+(?:conosci|capisci))$")
    // Telefono: torcia, non disturbare, volume, suoneria, modalità della suite.
    private val TORCH = Regex("$O^(?:(accendi|attiva|apri|spegni|disattiva|chiudi|togli)\\s+)?(?:la\\s+|il\\s+)?(?:torcia|flash|luce\\s+del\\s+telefono)$")
    private val QUIET_ON = Regex("$O^(?:(?:metti|attiva|accendi|imposta)\\s+)?(?:il\\s+|la\\s+modalità\\s+)?non\\s+disturbare(?:\\s+(fino\\s+(?:alle|all'|a)\\s*.+|per\\s+.+))?$")
    private val QUIET_OFF = Regex("$O^(?:spegni|togli|disattiva|leva|basta)\\s+(?:il\\s+|col\\s+)?non\\s+disturbare$")
    private val VOLUME = Regex("$O^(?:(?:metti|porta|imposta)\\s+)?(?:il\\s+)?volume\\s+(?:al\\s+|a\\s+)?(minimo|massimo|zero|alto|basso|più\\s+alto|più\\s+basso|su|giù)$")
    private val VOLUME_UP = Regex("$O^(?:(?:alza|aumenta)(?:\\s+(?:il|un\\s+po'?(?:\\s+il)?))?\\s+volume|più\\s+forte|alza|più\\s+alto)$")
    private val VOLUME_DOWN = Regex("$O^(?:(?:abbassa|diminuisci)(?:\\s+(?:il|un\\s+po'?(?:\\s+il)?))?\\s+volume|più\\s+piano|abbassa|più\\s+basso)$")
    private val MUTE = Regex("$O^(?:togli\\s+(?:l'audio|il\\s+volume)|muto|silenzio\\s+(?:alla|la)\\s+musica)$")
    private val RINGER = Regex("$O^(?:(?:metti|imposta)\\s+(?:il\\s+telefono\\s+)?(?:in|su|a|col|con)\\s+(?:il\\s+|la\\s+)?|modalità\\s+)?(silenzioso|vibrazione|vibra|solo\\s+vibrazione|suoneria)$")
    private val RINGER_OFF = Regex("$O^(?:togli|leva|spegni|disattiva)\\s+(?:il\\s+|la\\s+)?(?:silenzioso|vibrazione)$")
    private val MODE_ON = Regex("$O^(?:(?:attiva|accendi|metti|passa\\s+(?:alla|in)|entra\\s+in)\\s+)?(?:la\\s+)?modalità\\s+(lavoro|casa|viaggio|notte)$")
    private val MODE_OFF = Regex("$O^(?:spegni|disattiva|togli|esci\\s+dalla)\\s+(?:la\\s+)?modalità(?:\\s+(?:lavoro|casa|viaggio|notte))?$")
    // Musica: l'app che suona (o l'ultima usata) riceve i tasti multimediali.
    private val PLAY = Regex("$O^(?:(?:metti|fai\\s+partire|fammi\\s+sentire|riproduci|suona|accendi|fai\\s+ripartire|riprendi|rimetti)\\s+(?:la\\s+|un\\s+po'\\s+di\\s+|della\\s+|un\\s+po'\\s+di\\s+)?musica|play|riprendi|continua|vai\\s+con\\s+la\\s+musica)$")
    private val PAUSE = Regex("$O^(?:pausa|metti\\s+in\\s+pausa|(?:ferma|stoppa|spegni)\\s+(?:la\\s+)?musica|basta\\s+musica|stop(?:\\s+musica)?)$")
    private val NEXT = Regex("$O^(?:avanti|prossima|prossimo|la\\s+prossima|(?:prossimo|prossima)\\s+(?:brano|canzone|pezzo)|(?:brano|canzone|pezzo)\\s+successiv[oa]|salta(?:\\s+(?:questa|questo|il\\s+brano|la\\s+canzone))?|cambia\\s+(?:canzone|brano))$")
    private val PREVIOUS = Regex("$O^(?:indietro|torna\\s+indietro\\s+(?:di\\s+una\\s+canzone|di\\s+un\\s+brano)|(?:brano|canzone|pezzo)\\s+precedente|quella\\s+di\\s+prima|la\\s+canzone\\s+di\\s+prima)$")
    // In guida (Eurise Mappe): quanto manca, chi avvisare, cosa c'è vicino.
    private val GUIDE_LEFT = Regex("$O^(?:quanto\\s+(?:manca|ci\\s+vuole|ci\\s+metto|ci\\s+mettiamo|resta)(?:\\s+(?:ad\\s+arrivare|all'arrivo|alla\\s+meta|per\\s+arrivare|a\\s+casa|di\\s+strada))?|a\\s+che\\s+ora\\s+arrivo|a\\s+che\\s+ora\\s+arriviamo|quando\\s+arrivo|quando\\s+arriviamo|tra\\s+quanto\\s+arrivo|tra\\s+quanto\\s+arriviamo|quanti\\s+(?:chilometri|km)\\s+mancano)$")
    private val GUIDE_NOTIFY = Regex("$O^(?:avvisa|avverti|avvisami)\\s+(.+?)\\s+che\\s+(?:sto\\s+arrivando|arrivo|arriviamo|stiamo\\s+arrivando)$")
    private val NEARBY = Regex(
        "$O^(?:dov'è|dove\\s+(?:è|trovo|c'è)|c'è|cerca(?:mi)?|trovami)\\s+(?:un|il|la|una|un'|l')?\\s*" +
            "(benzinaio|distributore|stazione\\s+di\\s+servizio|area\\s+di\\s+servizio|farmacia|bancomat|parcheggio|bar|ristorante|supermercato|ospedale|colonnina(?:\\s+di\\s+ricarica)?|officina|autogrill|pizzeria|hotel|albergo)" +
            "\\s*(?:più\\s+vicin[oa]|qui\\s+vicino|vicino|nei\\s+dintorni|in\\s+zona)?$"
    )
    // Messaggi: risposta a chi ha scritto.
    private val REPLY_TO = Regex("$O^rispondi\\s+(?:a|ad)\\s+(.+?)\\s+(?:che|:)\\s*(.+)$")
    private val REPLY = Regex("$O^(?:rispondi|rispondigli|rispondile|rispondi\\s+(?:pure|subito))\\s*(?:che|:|,)?\\s+(.+)$")
    // Sveglie e timer già messi.
    private val TIMER_STOP = Regex("$O^(?:ferma|fermami|stoppa|spegni|annulla|cancella|togli|elimina|basta\\s+con)\\s+(?:il\\s+|i\\s+|tutti\\s+i\\s+)?timer$")
    private val TIMER_LEFT = Regex("$O^(?:quanto\\s+(?:tempo\\s+)?(?:manca|resta|rimane)(?:\\s+(?:al|alla\\s+fine\\s+del|sul|del|nel)\\s+timer)?|quanto\\s+fa\\s+il\\s+timer|a\\s+che\\s+punto\\s+è\\s+il\\s+timer)$")
    private val TIMER_LEFT_STRICT = Regex("$O\\btimer\\b")
    private val ALARM_OFF = Regex("$O^(?:spegni|togli|disattiva|salta|cancella|annulla|elimina|levami|toglimi)\\s+(?:la\\s+)?sveglia(?:\\s+(?:di\\s+|per\\s+)?(.+))?$")
    // Impegni per nome e cose fatte.
    private val CHANGE_MOVE = Regex("$O^(?:sposta(?:mi)?|rimanda|anticipa|posticipa|cambia\\s+(?:l'ora|il\\s+giorno)\\s+(?:del|della|dello|di|dell'))\\s+(.+)$")
    private val CHANGE_DELETE = Regex("$O^(?:cancella(?:mi)?|elimina|togli(?:mi)?|annulla|disdici|leva)\\s+(?:dall'agenda\\s+|dal\\s+calendario\\s+)?(.+)$")
    private val COMPLETE = Regex(
        "$O^(?:ho\\s+(?:già\\s+|appena\\s+)?(?:comprato|preso|fatto|finito|pagato|chiamato|mandato|spedito|ritirato|portato|sistemato|prenotato|letto|scritto|lavato|pulito|consegnato|restituito|innaffiato|annaffiato|stirato|riparato)" +
            "|spunta|segna\\s+(?:come\\s+)?fatt[oaie]|segna\\s+che\\s+ho\\s+fatto|fatto\\s*[:,]?)\\s+(.+)$"
    )
    private val LEADING_ARTICLE = Regex("$O^(?:l'|il\\s+|la\\s+|lo\\s+|i\\s+|gli\\s+|le\\s+|un\\s+|una\\s+|un'|uno\\s+)")
    private val EVENT_WORDS = Regex("$O^(?:(?:appuntamento|impegno|evento)\\s+(?:con\\s+|del\\s+|della\\s+|dal\\s+|dalla\\s+|di\\s+|dal\\s+|col\\s+)?)")
    private val TRAILING_PREP = Regex("$O\\s+(?:a|ad|al|alla|alle|allo|per|di|da|in|verso|fino)$")
    /** Cose che «cancella…» non intende come impegni: hanno le loro regole o non sono in agenda. */
    private val NOT_EVENTS = setOf("timer", "sveglia", "la sveglia", "tutto", "nota", "la nota", "messaggio", "il messaggio")

    /** Le azioni nuove (tavola AlfAzioni): null se la frase non è una di queste. */
    internal fun action(text: String, now: LocalDateTime): Command? {
        HELP.matchEntire(text)?.let { return Command.Help }
        TORCH.matchEntire(text)?.let { m ->
            val off = m.groupValues[1].lowercase().let { it.startsWith("spegn") || it.startsWith("disatt") || it.startsWith("chiud") || it.startsWith("togl") }
            return Command.Device(if (off) DeviceAction.TorchOff else DeviceAction.TorchOn)
        }
        QUIET_OFF.matchEntire(text)?.let { return Command.Device(DeviceAction.QuietOff) }
        QUIET_ON.matchEntire(text)?.let { m ->
            val rest = m.groupValues[1].trim()
            if (rest.isEmpty()) return Command.Device(DeviceAction.QuietOn)
            if (rest.lowercase().startsWith("per")) {
                val r = rest.substringAfter(' ').trim()
                return Command.Device(DeviceAction.QuietOn, minutes = timerMinutes(r.substringBefore(' '), r.substringAfter(' ', "")) ?: return null)
            }
            val t = rest.replace(Regex("$O^fino\\s+"), "")
            val time = QuickAdd.parse(t, now).time ?: bareHour(t) ?: return null
            return Command.Device(DeviceAction.QuietOn, until = time)
        }
        RINGER_OFF.matchEntire(text)?.let { return Command.Device(DeviceAction.Sound) }
        RINGER.matchEntire(text)?.let { m ->
            return Command.Device(when {
                m.groupValues[1].lowercase().startsWith("silenz") -> DeviceAction.Silent
                m.groupValues[1].lowercase().startsWith("suon") -> DeviceAction.Sound
                else -> DeviceAction.Vibrate
            })
        }
        MODE_OFF.matchEntire(text)?.let { return Command.Device(DeviceAction.ModeOff) }
        MODE_ON.matchEntire(text)?.let { return Command.Device(DeviceAction.ModeOn, mode = it.groupValues[1].lowercase()) }
        VOLUME.matchEntire(text)?.let { m ->
            val w = m.groupValues[1].lowercase()
            return Command.Device(when {
                w == "minimo" || w == "basso" -> DeviceAction.VolumeMin
                w == "massimo" || w == "alto" -> DeviceAction.VolumeMax
                w == "zero" -> DeviceAction.Mute
                w.endsWith("alto") || w == "su" -> DeviceAction.VolumeUp
                else -> DeviceAction.VolumeDown
            })
        }
        VOLUME_UP.matchEntire(text)?.let { return Command.Device(DeviceAction.VolumeUp) }
        VOLUME_DOWN.matchEntire(text)?.let { return Command.Device(DeviceAction.VolumeDown) }
        MUTE.matchEntire(text)?.let { return Command.Device(DeviceAction.Mute) }
        PLAY.matchEntire(text)?.let { return Command.Device(DeviceAction.Play) }
        PAUSE.matchEntire(text)?.let { return Command.Device(DeviceAction.Pause) }
        NEXT.matchEntire(text)?.let { return Command.Device(DeviceAction.Next) }
        PREVIOUS.matchEntire(text)?.let { return Command.Device(DeviceAction.Previous) }
        GUIDE_LEFT.matchEntire(text)?.let { return Command.Guide() }
        GUIDE_NOTIFY.matchEntire(text)?.let { m -> return Command.Guide(m.groupValues[1].trim()) }
        NEARBY.matchEntire(text)?.let { m -> return Command.Navigate(m.groupValues[1].trim().lowercase()) }
        REPLY_TO.matchEntire(text)?.let { m -> return Command.Message(m.groupValues[1].trim(), capital(m.groupValues[2].trim()), Channel.Sms) }
        REPLY.matchEntire(text)?.let { m -> return Command.Reply(capital(m.groupValues[1].trim())) }
        TIMER_STOP.matchEntire(text)?.let { return Command.Clock(ClockAction.StopTimer) }
        TIMER_LEFT.matchEntire(text)?.let { m ->
            // «quanto manca?» da solo è anche la domanda in guida: qui solo se si parla del timer.
            if (TIMER_LEFT_STRICT.containsMatchIn(m.value)) return Command.Clock(ClockAction.TimerLeft)
        }
        ALARM_OFF.matchEntire(text)?.let { m ->
            val rest = m.groupValues[1].trim()
            val day = if (rest.isBlank()) null else {
                val q = QuickAdd.parse(rest.replace(Regex("$O^domattina$"), "domani"), now)
                if (q.pieces.any { it.kind == QuickAdd.Kind.Date }) q.due else null
            }
            return Command.Clock(ClockAction.AlarmOff, day)
        }
        COMPLETE.matchEntire(text)?.let { m ->
            val about = LEADING_ARTICLE.replace(m.groupValues[1].trim(), "").trim()
            if (about.isNotBlank()) return Command.Complete(about)
        }
        CHANGE_MOVE.matchEntire(text)?.let { m ->
            val q = QuickAdd.parse(m.groupValues[1], now)
            val hasDay = q.pieces.any { it.kind == QuickAdd.Kind.Date }
            val about = eventName(q.title)
            if (about.isNotBlank() && (hasDay || q.time != null)) return Command.Change(false, about, if (hasDay) q.due else null, q.time)
        }
        CHANGE_DELETE.matchEntire(text)?.let { m ->
            val q = QuickAdd.parse(m.groupValues[1], now)
            val about = eventName(q.title)
            if (about.isNotBlank() && about.lowercase() !in NOT_EVENTS && Butler.fold(about) !in NOT_EVENTS) {
                return Command.Change(true, about, if (q.pieces.any { it.kind == QuickAdd.Kind.Date }) q.due else null)
            }
        }
        return null
    }

    /** «la riunione a» → «riunione»; «l'appuntamento con Anna» → «Anna». */
    private fun eventName(title: String): String {
        var t = title.trim().trimEnd(',', ' ')
        repeat(2) { t = TRAILING_PREP.replace(t, "").trim() }
        t = LEADING_ARTICLE.replace(t, "").trim()
        t = EVENT_WORDS.replace(t, "").trim()
        t = LEADING_ARTICLE.replace(t, "").trim()
        return t
    }

    // --- Domande sui dati (tavola AlfDati) ---
    /** «leggimi i messaggi», «chi mi ha scritto», «ho messaggi nuovi?», «cosa mi ha scritto Anna?» (SMS da Eurise Messaggi). */
    private val ASK_MESSAGES = Regex(
        "$O^(?:(?:leggimi|leggi|rileggimi)\\s+(?:i\\s+|gli\\s+ultimi\\s+|l'ultimo\\s+|il\\s+)?(?:nuovi\\s+)?(?:messaggi|messaggio|sms)(?:\\s+(?:nuovi|non\\s+letti|di\\s+oggi))?" +
            "|chi\\s+mi\\s+ha\\s+(?:scritto|mandato\\s+(?:un\\s+)?messaggi?o?)|ho\\s+(?:dei\\s+|nuovi\\s+|qualche\\s+)?(?:messaggi|sms)(?:\\s+(?:nuovi|non\\s+letti))?" +
            "|(?:ci\\s+sono\\s+)?(?:messaggi|sms)\\s+nuovi|(?:che\\s+)?(?:cosa|cos')\\s*mi\\s+ha\\s+scritto(?:\\s+(.+))?)$"
    )
    /** «cosa ho domani», «che impegni ho venerdì», «ho qualcosa stasera», «com'è la mia giornata». */
    private val ASK_AGENDA = Regex(
        "$O^(?:(?:che\\s+)?(?:cosa|cos'|che)\\s*(?:ho|c'è|c'e|ce|devo\\s+fare)(?:\\s+(?:in\\s+(?:programma|agenda|calendario)|da\\s+fare))?" +
            "|(?:che|quali)\\s+(?:impegni|appuntamenti|programmi)\\s+ho|ho\\s+(?:impegni|appuntamenti|qualcosa|da\\s+fare)" +
            "|com'è\\s+(?:la\\s+)?(?:mia\\s+)?(?:giornata|agenda|settimana)|(?:la\\s+)?(?:mia\\s+)?agenda(?:\\s+di)?)(?![\\p{L}])\\s*(.*)$"
    )
    /** «quando ho il dentista», «quando è la riunione», «a che ora ho il pranzo con Anna». */
    private val ASK_WHEN = Regex(
        "$O^(?:quando|a\\s+che\\s+ora|che\\s+giorno)\\s+(?:ho|c'è|c'e|è|e'|sarà|devo\\s+andare|ho\\s+appuntamento|devo)\\s+" +
            "(?:(?:con|da|dal|dalla|al|alla|a)\\s+)?(?:(?:il|la|lo|l'|i|gli|le|un|una|un')\\s*)?(.+)$"
    )
    /** «chi mi ha chiamato», «ho chiamate perse», «chiamate di oggi». */
    private val ASK_CALLS = Regex(
        "$O^(?:chi\\s+(?:mi\\s+)?ha\\s+(?:chiamato|telefonato|cercato)|(?:ho\\s+)?(?:delle\\s+|qualche\\s+)?chiamat[ae]\\s+pers[ae]" +
            "|(?:le\\s+)?(?:ultime\\s+)?chiamate(?:\\s+(?:perse|ricevute))?(?=\\s|$)|mi\\s+ha\\s+chiamato\\s+qualcuno)(?![\\p{L}])\\s*(.*)$"
    )
    /** «cosa avevo scritto sul cancello», «qual è il codice del cancello» (si cerca nelle note). */
    private val ASK_NOTES = Regex(
        "$O^(?:(?:che\\s+)?(?:cosa|cos'|che)\\s*(?:avevo|ho)\\s+(?:scritto|annotato|segnato|appuntato)" +
            "(?:\\s+(?:nelle\\s+note|nella\\s+nota))?\\s+(?:sull'|sulla|sullo|sulle|sui|sul|su|per|della|dello|del|di|riguardo\\s+(?:alla|al|a)|a\\s+proposito\\s+(?:della|del|di))(?:\\s+|(?<='))(.+)" +
            "|(?:qual\\s+è|qual\\s+e|qual'è|quale\\s+è|com'è|cos'è)\\s+(?:il|la|l'|lo)\\s*((?:codice|numero|password|pin|combinazione|indirizzo|targa|iban)(?![\\p{L}]).*))$"
    )
    private val DAY_FILLER = Regex("$O^(?:per|di|in|a|ancora|oggi\\s+ancora)\\s+|\\s+(?:in\\s+(?:agenda|calendario|programma)|da\\s+fare)$")
    private val WEEK = Regex("$O^(?:(?:per\\s+)?(?:questa|la)\\s+)?settimana(?:\\s+(prossima))?$|^(?:la\\s+)?prossima\\s+settimana$")

    /** Le domande sui dati, prima di tutti i comandi: null se la frase non è una di queste. */
    internal fun ask(text: String, now: LocalDateTime): Command.Ask? {
        ASK_MESSAGES.matchEntire(text)?.let { m -> return Command.Ask(AskKind.Messages, about = m.groupValues[1].trim()) }
        ASK_NOTES.matchEntire(text)?.let { m ->
            val about = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim().trimEnd('?')
            if (about.isNotBlank()) return Command.Ask(AskKind.Notes, about = about)
        }
        ASK_CALLS.matchEntire(text)?.let { m ->
            val rest = m.groupValues[1].trim()
            val day = if (rest.isBlank()) null else dayOf(rest, now)?.first ?: return@let
            return Command.Ask(AskKind.Calls, day = day)
        }
        ASK_AGENDA.matchEntire(text)?.let { m ->
            val (day, days) = dayOf(m.groupValues[1], now) ?: return@let
            return Command.Ask(AskKind.Agenda, day = day, days = days)
        }
        ASK_WHEN.matchEntire(text)?.let { m ->
            val about = m.groupValues[1].trim().trimEnd('?')
            if (about.isNotBlank()) return Command.Ask(AskKind.When, about = capital(about))
        }
        return null
    }

    /** Il giorno detto («domani», «venerdì», «questa settimana»; niente = oggi); null se ci sono altre parole. */
    internal fun dayOf(rest: String, now: LocalDateTime): Pair<LocalDate, Int>? {
        var r = rest.trim().trimEnd('?')
        repeat(2) { r = DAY_FILLER.replace(r, "").trim() }
        if (r.isEmpty()) return now.toLocalDate() to 1
        WEEK.matchEntire(r)?.let { m ->
            val today = now.toLocalDate()
            val next = m.groupValues[1].isNotEmpty() || r.lowercase().startsWith("la prossima") || r.lowercase().startsWith("prossima")
            return if (next) {
                val monday = today.plusDays((8 - today.dayOfWeek.value).toLong())
                monday to 7
            } else today to (8 - today.dayOfWeek.value)
        }
        val q = QuickAdd.parse(r, now)
        val hasDay = q.pieces.any { it.kind == QuickAdd.Kind.Date }
        val hasTime = q.pieces.any { it.kind == QuickAdd.Kind.Time }
        if (q.title.isNotBlank() || (!hasDay && !hasTime)) return null
        return (q.due ?: now.toLocalDate()) to 1
    }

    // --- Seguiti (tavola AlfSeguito) e scelte (tavola AlfQuale) ---
    private val FOLLOW_MOVE = Regex(
        "$O^(?:(?:no|anzi|meglio|facciamo|scusa)[,]?\\s+)*(?:(?:sposta|rimanda|anticipa|posticipa|metti|porta|fa|fai|cambia)(?:lo|la|li|le|melo|mela)?(?:\\s+(?:l'ora|il\\s+giorno))?\\s+)?(.+)$"
    )
    private val FOLLOW_UNDO = Regex(
        "$O^(?:(?:no|anzi)[,]?\\s+)?(?:cancella(?:lo|la|li|le)?|elimina(?:lo|la|li|le)?|togli(?:lo|la|li|le)?|annulla(?:lo|la)?(?:\\s+tutto)?" +
            "|lascia\\s+(?:perdere|stare)|non\\s+(?:farlo|serve\\s+più|importa\\s+più)|ho\\s+sbagliato|torna\\s+indietro)$"
    )
    private val FOLLOW_ALSO = Regex(
        "$O^(?:(?:e\\s+)?(?:manda|mandalo|mandala|mandaglielo|invia|invialo|inviala|avvisa|dillo|dillo|scrivilo|giralo|girala|inoltralo|inoltrala|fallo\\s+sapere)(?:\\s+anche)?" +
            "|e\\s+anche|anche)\\s+(?:a|ad|al|alla)\\s+(.+)$"
    )

    /** Un seguito dell'ultima cosa fatta: si chiede solo quando Alfred ne ricorda una (due minuti). */
    fun follow(sentence: String, now: LocalDateTime): Command.Follow? {
        val text = POLITE_TAIL.replace(POLITE_HEAD.replace(normalize(sentence), ""), "").trim().trimStart(',', ' ').trim()
        if (text.isEmpty()) return null
        FOLLOW_UNDO.matchEntire(text)?.let { return Command.Follow(FollowAction.Undo) }
        FOLLOW_ALSO.matchEntire(text)?.let { m ->
            val who = m.groupValues[1].trim().replace(Regex("$O\\s+(?:su|con|via|per)\\s+\\S+$"), "")
            if (who.isNotBlank()) return Command.Follow(FollowAction.AlsoTo, who = who)
        }
        FOLLOW_MOVE.matchEntire(text)?.let { m ->
            val rest = m.groupValues[1].trim().replace(Regex("$O^(?:a|ad|per|di|in)\\s+(?=\\p{L})"), "")
            val q = QuickAdd.parse(rest, now)
            val day = q.pieces.any { it.kind == QuickAdd.Kind.Date }
            val time = q.time ?: if (Regex("$O^(?:alle|per\\s+le|verso\\s+le)\\s+").containsMatchIn(rest)) bareHour(rest) else null
            if (q.title.isBlank() && (day || time != null)) return Command.Follow(FollowAction.Move, if (day) q.due else null, time)
            // «alle 10 e mezza» o «alle 11» che QuickAdd lascia nel titolo: basta l'ora.
            if (time != null && Regex("$O^(?:alle|per\\s+le|verso\\s+le)\\s+[\\d.:]+(?:\\s+e\\s+mezza)?$").matches(rest)) {
                return Command.Follow(FollowAction.Move, null, if (rest.lowercase().endsWith("mezza")) time.plusMinutes(30) else time)
            }
        }
        return null
    }

    private val ORDINALS = listOf(
        Regex("$O\\b(?:il\\s+)?(?:primo|prima|uno|1)\\b"),
        Regex("$O\\b(?:il\\s+)?(?:secondo|seconda|due|2)\\b"),
        Regex("$O\\b(?:il\\s+)?(?:terzo|terza|tre|3)\\b"),
        Regex("$O\\b(?:il\\s+)?(?:quarto|quarta|quattro|4)\\b"),
    )
    private val PICK_STOP = setOf("quello", "quella", "del", "della", "dello", "dei", "il", "lo", "la", "di", "da", "con", "che", "sta", "chiama", "scrivi", "uno")

    /**
     * La risposta a «Quale Marco?» (tavola AlfQuale): «il primo», «Ferri», «quello del lavoro». [options] sono i nomi
     * con il loro dettaglio; null se la risposta non sceglie nessuno, o ne sceglie più d'uno.
     */
    fun pick(sentence: String, options: List<Pair<String, String>>): Int? {
        val t = POLITE_HEAD.replace(normalize(sentence).lowercase(), "").trim()
        if (Regex("$O\\b(?:l'ultimo|l'ultima|ultimo|ultima)\\b").containsMatchIn(t)) return options.lastIndex.takeIf { it >= 0 }
        val words = fold(t).split(' ').filter { it.length > 2 && it !in PICK_STOP }
        val scores = options.map { (name, detail) ->
            val n = fold(name).split(' ')
            val d = fold(detail).split(' ')
            words.count { w -> n.any { it.startsWith(w) } } * 2 + words.count { w -> d.any { it.startsWith(w) } }
        }
        val best = scores.maxOrNull() ?: 0
        // Un nome comune a tutti («Marco») non sceglie: contano le parole che li distinguono.
        if (best > 0 && scores.count { it == best } == 1) return scores.indexOf(best)
        ORDINALS.forEachIndexed { i, r -> if (r.containsMatchIn(t) && i < options.size) return i }
        return null
    }

    private val WITH = Regex("(?u)(?<![\\p{L}])[Cc]on\\s+([\\p{L}'’]+(?:\\s+\\p{Lu}[\\p{L}'’]+)?)")

    /** Parole dell'evento che restano nel titolo («Cena da Anna»); le altre («appuntamento») si tolgono. */
    private val KEEP_WORD = setOf("riunione", "incontro", "cena", "pranzo", "colazione", "aperitivo", "visita")

    /** La risposta a voce alla scheda di conferma: «sì, procedi» agisce, «no, lascia stare» annulla. */
    enum class Answer { Yes, No, Other }

    private val YES = Regex("$O^(?:sì|si|certo|certamente|ok(?:ay)?|va\\s+bene|procedi|proceda|vai|vada|fallo|lo\\s+faccia|confermo|conferma|esatto|perfetto|d'accordo|invia|manda|chiama|salva)(?![\\p{L}])")
    private val NO = Regex("$O^(?:no|annulla|lascia(?:\\s+(?:perdere|stare))?|lasci(?:\\s+(?:perdere|stare))?|non\\s+(?:ora|adesso|importa)|aspetta|ferma|fermati|niente|stop)(?![\\p{L}])")

    fun answer(sentence: String): Answer {
        val t = POLITE_HEAD.replace(sentence.trim().lowercase(), "").trim().trimStart(',', ' ')
        return when {
            NO.containsMatchIn(t) -> Answer.No
            YES.containsMatchIn(t) -> Answer.Yes
            else -> Answer.Other
        }
    }

    /** Solo le regole esatte: null se nessuna prende la frase (allora la legge Eurise AI, o la seconda lettura). */
    fun exact(sentence: String, now: LocalDateTime): Command? = parse(sentence, now, guessing = false).takeUnless { it is Command.Free }

    fun parse(sentence: String, now: LocalDateTime, guessing: Boolean = true): Command {
        val text = POLITE_TAIL.replace(POLITE_HEAD.replace(normalize(sentence), ""), "").trim().trimStart(',', ' ').trim()
        if (text.isEmpty()) return Command.Free(sentence)

        action(text, now)?.let { return it }
        ask(text, now)?.let { return it }
        REPORT.matchEntire(text)?.let { m -> return Command.Report(reportKind(m.groupValues[1].lowercase())) }
        REMEMBER.matchEntire(text)?.let { return Command.Remember(capital(it.groupValues[1])) }
        NOTE.matchEntire(text)?.let { return Command.Note(capital(it.groupValues[1])) }
        CALENDAR.matchEntire(text)?.let { m ->
            if (m.groupValues[1].isNotEmpty() || m.groupValues[2].isNotEmpty()) return event("", m.groupValues[3], now)
        }
        TASK.matchEntire(text)?.let { m ->
            val q = QuickAdd.parse(m.groupValues[1], now)
            return Command.Task(capital(q.title.ifBlank { m.groupValues[1] }), q.due, q.time)
        }
        TIMER.matchEntire(text)?.let { m -> timerMinutes(m.groupValues[1], m.groupValues[2])?.let { return Command.Timer(it) } }
        ALARM.matchEntire(text)?.let { m ->
            val q = QuickAdd.parse(m.groupValues[1], now)
            val time = q.time ?: bareHour(m.groupValues[1])
            if (time != null) {
                val explicitDay = q.pieces.any { it.kind == QuickAdd.Kind.Date }
                return Command.Alarm(time, if (explicitDay) q.due else null)
            }
        }
        CALL.matchEntire(text)?.let { return Command.Call(it.groupValues[1].trim()) }
        MESSAGE.matchEntire(text)?.let { m ->
            val channel = when (m.groupValues[2].lowercase()) {
                "whatsapp" -> Channel.WhatsApp
                "sms", "messaggio" -> Channel.Sms
                "telegram" -> Channel.Telegram
                "signal" -> Channel.Signal
                else -> null
            }
            return Command.Message(m.groupValues[1].trim(), capital(m.groupValues[3].trim()), channel)
        }
        NAVIGATE.matchEntire(text)?.let { return Command.Navigate(it.groupValues[1].trim()) }
        EVENT.matchEntire(text)?.let { m -> return event(m.groupValues[1], m.groupValues[2], now) }
        SEARCH.matchEntire(text)?.let { m ->
            val scope = if (m.groupValues[1].isBlank() || m.groupValues[1].lowercase().let { "web" in it || "rete" in it || "internet" in it }) Scope.Web else Scope.Suite
            return Command.Search(m.groupValues[2].trim(), scope)
        }
        OPEN.matchEntire(text)?.let { return Command.Open(it.groupValues[1].trim()) }

        // Una frase con giorno e ora ma senza verbo noto («dentista martedì alle 9») è un appuntamento.
        val q = QuickAdd.parse(text, now)
        if (q.time != null && q.pieces.any { it.kind == QuickAdd.Kind.Date } && text.split(' ').size <= 10) {
            return event("", text, now)
        }
        return (if (guessing) guess(text, now) else null) ?: Command.Free(sentence.trim())
    }

    /** Il testo come lo scrive Whisper: apostrofi tipografici, puntini, punteggiatura in fondo. */
    private fun normalize(s: String) = s.replace('’', '\'').replace("…", " ").replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?', ',', ';')

    private fun has(text: String, pattern: String) = Regex("$O$pattern").containsMatchIn(text)

    /** Ciò che segue la prima parola che corrisponde a [pattern] (senza articoli e preposizioni all'inizio). */
    private fun after(text: String, pattern: String): String {
        val m = Regex("$O$pattern").find(text) ?: return ""
        return text.substring(m.range.last + 1).trim().trimStart(',', ':', ' ')
            .replace(Regex("$O^(?:a|ad|al|alla|allo|ai|la|il|lo|l'|di|che|per|in|nel|nella|sul|sulla)\\s+"), "").trim()
    }

    /**
     * Seconda lettura, quando nessuna regola esatta prende la frase: si cercano le parole dei comandi ovunque
     * (anche con le forme del verbo che Whisper scrive male: «chiamo», «chiamare», «nel calendario»). Ne esce la
     * scheda di conferma, che si corregge con un tocco: meglio una scheda da sistemare che una frase mandata a un'AI
     * online. Solo le frasi senza parole di comando restano richieste libere.
     */
    internal fun guess(text: String, now: LocalDateTime): Command? {
        val t = text.lowercase()
        // Le domande («quando ho l'appuntamento…», «chi mi ha chiamato») non sono comandi.
        if (has(t, "^(?:che|cosa|come|quando|quanto|quanta|quanti|quante|quale|quali|qual|chi|dove|perché|perche)\\b")) return null
        return when {
            has(t, "\\b(?:calendario|agenda|evento|appuntamento|impegno)\\b") ->
                event("", after(text, "\\b(?:evento|appuntamento|impegno|calendario|agenda)\\b(?:\\s+(?:in|nel|al|sul)\\s+(?:calendario|agenda))?"), now)
            has(t, "\\b(?:not[ae]|appunto|appunta|annota)\\b") ->
                Command.Note(capital(after(text, "\\b(?:not[ae]|appunto|appunta|annota)\\b")))
            has(t, "\\b(?:promemoria|ricord\\w*)\\b") -> {
                val rest = after(text, "\\b(?:promemoria|ricord\\w*)\\b")
                val q = QuickAdd.parse(rest, now)
                Command.Task(capital(q.title.ifBlank { rest }), q.due, q.time)
            }
            has(t, "\\b(?:messaggio|whatsapp|sms|telegram|signal|scrivi\\s+a|scrivere\\s+a|manda\\w*\\s+a|invia\\w*\\s+a|di'?\\s+a|dici\\s+a)\\b") -> {
                val who = Regex("$O\\b(?:a|ad)\\s+([\\p{L}']+(?:\\s+\\p{L}[\\p{L}']+)?)").find(text.substringBefore(" che ").substringBefore(":"))?.groupValues?.get(1)
                    ?.replace(Regex("$O\\s+(?:su|con|via|per|in)$"), "").orEmpty()
                val body = Regex("$O(?:\\bche\\b|:)\\s*(.+)$").find(text)?.groupValues?.get(1).orEmpty()
                val channel = when {
                    "whatsapp" in t -> Channel.WhatsApp
                    "telegram" in t -> Channel.Telegram
                    "signal" in t -> Channel.Signal
                    "sms" in t -> Channel.Sms
                    else -> null
                }
                Command.Message(who.trim(), capital(body), channel)
            }
            has(t, "\\b(?:chiam\\w*|telefon\\w*)\\b") ->
                Command.Call(after(text, "\\b(?:chiam\\w*|telefon\\w*)\\b"))
            has(t, "\\bsvegli\\w*\\b") -> {
                val rest = after(t, "\\bsvegli\\w*\\b")
                val q = QuickAdd.parse(rest, now)
                (q.time ?: bareHour(rest))?.let { Command.Alarm(it, if (q.pieces.any { p -> p.kind == QuickAdd.Kind.Date }) q.due else null) }
            }
            has(t, "\\btimer\\b") -> Regex("$O(\\d+|un|uno|mezz'?ora|un'?ora)\\s*(minut[oi]|or[ae])?").find(after(t, "\\btimer\\b"))
                ?.let { m -> timerMinutes(m.groupValues[1], m.groupValues[2])?.let { Command.Timer(it) } }
            has(t, "\\b(?:portami|naviga\\w*|indicazioni|accompagnami|guidami)\\b") ->
                Command.Navigate(after(text, "\\b(?:portami|naviga\\w*|indicazioni|accompagnami|guidami)\\b(?:\\s+(?:per|verso|fino))?"))
            has(t, "^(?:apri\\w*|avvia)\\b") -> Command.Open(after(text, "^(?:apri\\w*|avvia)\\b(?:\\s+(?:l'|il|la|lo|app))?"))
            else -> null
        }
    }

    private fun event(keyword: String, rest: String, now: LocalDateTime): Command.Event {
        val q = QuickAdd.parse(rest, now)
        var title = q.title
        var who: String? = null
        WITH.find(title)?.let { m ->
            who = m.groupValues[1]
            title = title.removeRange(m.range).replace(Regex("\\s{2,}"), " ").trim()
        }
        val word = keyword.lowercase().split(' ').first()
        title = when {
            word in KEEP_WORD && title.isNotBlank() -> "${capital(word)} $title"
            word in KEEP_WORD -> capital(word) + (who?.let { " con $it" } ?: "")
            title.isNotBlank() -> capital(title)
            who != null -> "Appuntamento con $who"
            else -> "Appuntamento"
        }
        return Command.Event(title, who, q.due, q.time)
    }

    private fun reportKind(w: String): ReportKind = when {
        w.startsWith("limite") -> ReportKind.LIMIT
        w.startsWith("strada") -> ReportKind.CLOSED
        w.startsWith("lavori") || w == "cantiere" -> ReportKind.WORKS
        w == "coda" || w == "traffico" || w == "incolonnamento" -> ReportKind.QUEUE
        w == "pericolo" || w == "ostacolo" -> ReportKind.DANGER
        else -> ReportKind.CAMERA
    }

    /** «sveglia alle 7» lo legge QuickAdd; «sveglia 7» o «sveglia 6.30» no. */
    private fun bareHour(s: String): LocalTime? {
        val m = Regex("(?<!\\d)(\\d{1,2})(?:[:.](\\d{2}))?(?!\\d)").find(s) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    private fun timerMinutes(n: String, unit: String): Int? {
        val l = n.lowercase()
        if (l.startsWith("mezz")) return 30
        if (l.replace("'", "").replace(" ", "") == "unora") return 60
        val value = when (l) { "un", "uno" -> 1; else -> l.toIntOrNull() } ?: return null
        return if (unit.lowercase().startsWith("or")) value * 60 else value
    }

    internal fun capital(s: String): String = s.trim().replaceFirstChar { it.uppercase() }

    /** Minuscole, senza accenti né punteggiatura (come Butler.fold). */
    internal fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(java.util.Locale.ITALIAN), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
}

/**
 * Le correzioni a mano (tavola AlfSeguito): se nella scheda si corregge una parola che Alfred aveva capito male
 * («dentisti» → «dentista»), la volta dopo si corregge da sé. Solo parole singole, mai frasi intere.
 */
object Corrections {
    /** Le parole cambiate tra il testo capito e quello corretto, quando il resto è uguale (al massimo due). */
    fun diff(before: String, after: String): List<Pair<String, String>> {
        val a = before.trim().split(Regex("\\s+"))
        val b = after.trim().split(Regex("\\s+"))
        if (a.size != b.size || a.size > 12) return emptyList()
        val changed = a.indices.filter { Grammar.fold(a[it]) != Grammar.fold(b[it]) }
            .map { a[it].trim(',', '.', '!', '?', ';', ':') to b[it].trim(',', '.', '!', '?', ';', ':') }
            .filter { (x, y) -> x.length > 1 && y.length > 1 && x.none { it.isDigit() } && y.none { it.isDigit() } }
        return if (changed.size in 1..2) changed else emptyList()
    }

    /** Applica le correzioni imparate a una frase: parola per parola, con la maiuscola com'era. */
    fun apply(sentence: String, fixes: Map<String, String>): String {
        if (fixes.isEmpty()) return sentence
        return Regex("\\p{L}+").replace(sentence) { m ->
            val fix = fixes[Grammar.fold(m.value)] ?: return@replace m.value
            if (m.value.first().isUpperCase()) fix.replaceFirstChar { it.uppercase() } else fix
        }
    }
}
