package eu.eurise.core.data.model

import java.time.LocalDateTime

/**
 * Legge la frase scritta nel + unico: oltre a ciò che capisce [QuickAdd] riconosce le persone ("@Anna")
 * e propone che cosa creare. La persona resta nel titolo, senza la chiocciola.
 */
object Capture {

    enum class Target { Event, Task, Note }

    /** Una persona citata: [start] e [end] sono le posizioni nel testo, chiocciola compresa. */
    data class Mention(val name: String, val start: Int, val end: Int)

    data class Result(
        val quick: QuickAdd.Result,
        val mentions: List<Mention>,
        val suggested: Target,
        /** Titolo di un evento o di un'attività. */
        val title: String,
        /** Il testo intero senza chiocciole: una nota non perde il giorno e l'ora che contiene. */
        val noteText: String,
    )

    private val MENTION = Regex("""(?<![\p{L}\p{N}@._-])@(\p{L}[\p{L}\p{N}'’-]*)""")
    private const val LONG_TEXT_WORDS = 8

    fun parse(text: String, now: LocalDateTime, lists: List<String> = emptyList()): Result {
        val quick = QuickAdd.parse(text, now, lists)
        val mentions = MENTION.findAll(text).map { Mention(it.groupValues[1], it.range.first, it.range.last + 1) }.toList()
        return Result(
            quick = quick,
            mentions = mentions,
            suggested = suggest(text, quick),
            title = withoutAt(quick.title),
            noteText = withoutAt(text).trim(),
        )
    }

    /** La parte di un nome utilizzabile dopo la chiocciola: "Anna Maria" dà "Anna"; vuota se non comincia con una lettera. */
    fun mentionToken(name: String): String {
        val first = name.trim().substringBefore(' ')
        if (first.firstOrNull()?.isLetter() != true) return ""
        return first.takeWhile { it.isLetterOrDigit() || it == '\'' || it == '’' || it == '-' }
    }

    private fun withoutAt(text: String) = MENTION.replace(text) { it.groupValues[1] }

    private fun suggest(text: String, q: QuickAdd.Result): Target {
        val deadline = q.pieces.any { it.kind == QuickAdd.Kind.Date && text.substring(it.start, it.end).trimStart().lowercase().startsWith("entro") }
        return when {
            q.due != null && q.time != null && q.repeat == null && !deadline -> Target.Event
            q.due != null || q.repeat != null || q.priority != Task.PRIORITY_NONE || q.listName != null -> Target.Task
            '\n' in text.trim() || text.trim().split(Regex("\\s+")).size >= LONG_TEXT_WORDS -> Target.Note
            else -> Target.Task
        }
    }
}
