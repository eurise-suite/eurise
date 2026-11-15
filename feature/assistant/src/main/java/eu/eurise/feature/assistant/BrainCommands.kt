package eu.eurise.feature.assistant

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Le righe del cervello di Alfred («tipo|chi|testo|data|ora|canale», vedi BrainContract) diventano comandi della suite.
 * Una riga che non si capisce si salta: meglio un'azione in meno che una sbagliata.
 */
object BrainCommands {

    fun parse(lines: String, now: LocalDateTime): List<Command> = lines.lineSequence().mapNotNull { line(it, now) }.toList()

    private fun line(raw: String, now: LocalDateTime): Command? {
        val f = (raw.split('|').map { it.trim() } + List(6) { "" }).take(6)
        val (kind, who, text, dateS, timeS, channelS) = f
        val date = runCatching { LocalDate.parse(dateS) }.getOrNull()
        val time = runCatching { LocalTime.parse(timeS) }.getOrNull()
        val title = text.replaceFirstChar { it.uppercase() }
        return when (kind) {
            "chiama" -> who.takeIf { it.isNotBlank() }?.let { Command.Call(it) }
            "messaggio" -> Command.Message(who, title, when (channelS) {
                "whatsapp" -> Channel.WhatsApp
                "telegram" -> Channel.Telegram
                "signal" -> Channel.Signal
                "sms" -> Channel.Sms
                else -> null
            })
            "nota" -> Command.Note(title)
            "evento" -> Command.Event(listOf(title, who).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Impegno" }, null, date, time)
            "da_fare" -> title.takeIf { it.isNotBlank() }?.let { Command.Task(it, date, time) }
            "promemoria" -> title.takeIf { it.isNotBlank() }?.let { Command.Task(it, date ?: if (time != null) now.toLocalDate() else null, time) }
            "sveglia" -> time?.let { Command.Alarm(it, date) }
            "timer" -> text.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..1440 }?.let { Command.Timer(it) }
            "naviga" -> who.ifBlank { text }.takeIf { it.isNotBlank() }?.let { Command.Navigate(it) }
            "apri" -> who.ifBlank { text }.takeIf { it.isNotBlank() }?.let { Command.Open(it) }
            "domanda" -> Command.Free(text)
            else -> null
        }
    }

    private operator fun <T> List<T>.component6() = this[5]
}
