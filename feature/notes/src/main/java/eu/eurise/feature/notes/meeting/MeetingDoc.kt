package eu.eurise.feature.notes.meeting

import eu.eurise.feature.notes.doc.Block
import eu.eurise.feature.notes.doc.RichText
import eu.eurise.feature.notes.doc.TextBlock
import eu.eurise.feature.notes.doc.TextKind

/** Una persona della riunione: [present] è vero se è stata toccata come presente. */
data class MeetingPerson(val name: String, val present: Boolean)

/** Una voce dell'ordine del giorno. */
data class AgendaItem(val text: String, val done: Boolean)

/** Un'azione decisa alla chiusura, già diventata attività ([taskId]). */
data class MeetingAction(val title: String, val taskId: Long?, val done: Boolean)

/** Il contenuto di una nota di riunione come lo mostra la schermata della riunione. */
data class MeetingContent(
    val intro: String = "",
    val people: List<MeetingPerson> = emptyList(),
    val agenda: List<AgendaItem> = emptyList(),
    val notes: String = "",
    val actions: List<MeetingAction> = emptyList(),
)

/** I titoli delle sezioni (dalle risorse): servono sia per scrivere sia per rileggere la nota. */
data class MeetingLabels(val people: String, val absent: String, val agenda: String, val notes: String, val actions: String)

/**
 * La nota di una riunione è una nota normale, leggibile e modificabile anche dall'editor delle Note:
 * una riga d'apertura, poi le sezioni Presenti, Ordine del giorno, Appunti e (dopo la chiusura) Da fare.
 */
object MeetingDoc {

    fun write(content: MeetingContent, labels: MeetingLabels, ids: () -> Long): List<Block> = buildList {
        fun text(kind: TextKind, s: String, checked: Boolean = false, taskId: Long? = null) =
            add(TextBlock(ids(), kind, RichText(s), checked, taskId))

        if (content.intro.isNotBlank()) text(TextKind.Paragraph, content.intro)
        text(TextKind.Heading, labels.people)
        val (present, absent) = content.people.partition { it.present }
        text(TextKind.Paragraph, present.joinToString(", ") { it.name })
        if (absent.isNotEmpty()) text(TextKind.Paragraph, "${labels.absent}: " + absent.joinToString(", ") { it.name })
        text(TextKind.Heading, labels.agenda)
        content.agenda.forEach { text(TextKind.Check, it.text, it.done) }
        text(TextKind.Heading, labels.notes)
        content.notes.trimEnd().split('\n').forEach { text(TextKind.Paragraph, it) }
        if (content.actions.isNotEmpty()) {
            text(TextKind.Heading, labels.actions)
            content.actions.forEach { text(TextKind.Check, it.title, it.done, it.taskId) }
        }
    }

    /** Rilegge una nota scritta da [write], anche se modificata a mano; ciò che non riconosce va negli appunti. */
    fun read(blocks: List<Block>, labels: MeetingLabels): MeetingContent {
        var section: String? = null
        val intro = mutableListOf<String>()
        val people = mutableListOf<MeetingPerson>()
        val agenda = mutableListOf<AgendaItem>()
        val notes = mutableListOf<String>()
        val actions = mutableListOf<MeetingAction>()
        val absentPrefix = "${labels.absent}:"
        for (block in blocks) {
            if (block !is TextBlock) continue
            val s = block.text.text
            if (block.kind == TextKind.Heading || block.kind == TextKind.Subheading) {
                val known = listOf(labels.people, labels.agenda, labels.notes, labels.actions).firstOrNull { it.equals(s.trim(), ignoreCase = true) }
                if (known != null) { section = known; continue }
            }
            when (section) {
                null -> if (s.isNotBlank()) intro += s
                labels.people -> {
                    val absent = s.trim().startsWith(absentPrefix, ignoreCase = true)
                    s.trim().removePrefix(absentPrefix).split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        .forEach { people += MeetingPerson(it, present = !absent) }
                }
                labels.agenda -> if (s.isNotBlank()) agenda += AgendaItem(s, block.kind == TextKind.Check && block.checked)
                labels.actions -> if (s.isNotBlank()) actions += MeetingAction(s, block.taskId, block.checked)
                else -> notes += s
            }
        }
        return MeetingContent(intro.joinToString(" "), people.distinctBy { it.name.lowercase() }, agenda, notes.joinToString("\n").trim('\n'), actions)
    }

    /** Il riassunto da condividere: solo testo, solo quando la persona lo chiede. */
    fun summary(title: String, content: MeetingContent, labels: MeetingLabels): String = buildString {
        appendLine(title)
        if (content.intro.isNotBlank()) appendLine(content.intro)
        val present = content.people.filter { it.present }
        if (present.isNotEmpty()) { appendLine(); appendLine("${labels.people}: " + present.joinToString(", ") { it.name }) }
        if (content.agenda.isNotEmpty()) {
            appendLine(); appendLine(labels.agenda)
            content.agenda.forEach { appendLine((if (it.done) "✓ " else "– ") + it.text) }
        }
        if (content.notes.isNotBlank()) { appendLine(); appendLine(labels.notes); appendLine(content.notes.trim()) }
        if (content.actions.isNotEmpty()) {
            appendLine(); appendLine(labels.actions)
            content.actions.forEach { appendLine("– " + it.title) }
        }
    }.trimEnd()
}
