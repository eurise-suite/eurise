package eu.eurise.feature.assistant

import eu.eurise.core.data.model.QuickAdd
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Ciò che Alfred ha capito da una frase, prima di cercare persone e calendari. */
sealed interface Command {
    data class Call(val who: String) : Command
    data class Note(val text: String) : Command
    data class Task(val title: String, val due: LocalDate?, val time: LocalTime?) : Command
    data class Event(val title: String, val who: String?, val day: LocalDate?, val time: LocalTime?) : Command
    data class Navigate(val place: String) : Command
    data class Message(val who: String, val text: String, val channel: Channel?) : Command
    data class Alarm(val time: LocalTime, val day: LocalDate?) : Command
    data class Timer(val minutes: Int) : Command
    data class Search(val query: String, val scope: Scope) : Command
    data class Open(val app: String) : Command
    /** Nessuna regola la capisce: tocca a Eurise AI (richiesta libera). */
    data class Free(val text: String) : Command
}

enum class Channel { WhatsApp, Sms, Telegram, Signal }

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

    private val NOTE = Regex("$O^(?:nota|annota|prendi\\s+(?:nota|appunto)|scrivi\\s+(?:una\\s+)?nota|appunta)(?![\\p{L}])\\s*(?:[:,]|che)?\\s*(.+)$")
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

    fun parse(sentence: String, now: LocalDateTime): Command {
        val text = POLITE_TAIL.replace(POLITE_HEAD.replace(sentence.trim().trimEnd('.', '!', '?'), ""), "").trim()
        if (text.isEmpty()) return Command.Free(sentence)

        NOTE.matchEntire(text)?.let { return Command.Note(capital(it.groupValues[1])) }
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
        return Command.Free(sentence.trim())
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
