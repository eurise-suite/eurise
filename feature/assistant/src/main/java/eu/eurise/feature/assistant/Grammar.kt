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
    /** Nessuna regola la capisce: tocca a Eurise AI (richiesta libera). */
    data class Free(val text: String) : Command
}

/** Agenda: «cosa ho domani?»; When: «quando ho il dentista?»; Calls: «chi mi ha chiamato?»; Notes: «cosa avevo scritto sul cancello?». */
enum class AskKind { Agenda, When, Calls, Notes }

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
    // --- Domande sui dati (tavola AlfDati) ---
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
}
