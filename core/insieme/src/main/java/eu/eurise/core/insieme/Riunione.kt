package eu.eurise.core.insieme

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * La nota della riunione (tavola InsRiunione), senza Android: si prova con i test.
 *
 * Nella nota condivisa le righe «@Paolo manda il computo aggiornato ven 22» diventano cose da fare assegnate: la persona
 * (riconosciuta dal nome, «@io» sei tu), cosa, e la scadenza scritta in fondo (oggi, domani, un giorno della settimana con o
 * senza il numero, 25/11). Ognuno le riceve come «Affida a…», con la scadenza; la nota resta dov'è. Le frasi «decidiamo
 * che…» sono proposte per il registro delle decisioni (tavola LavDecisioni).
 */
object Riunione {
    /** [who]: l'id della persona trovata (null se il nome non corrisponde a nessuno); [name]: il nome scritto dopo la @. */
    data class Task(val who: String?, val name: String, val text: String, val due: LocalDate?)

    private val days = mapOf(
        "lun" to DayOfWeek.MONDAY, "lunedì" to DayOfWeek.MONDAY, "lunedi" to DayOfWeek.MONDAY,
        "mar" to DayOfWeek.TUESDAY, "martedì" to DayOfWeek.TUESDAY, "martedi" to DayOfWeek.TUESDAY,
        "mer" to DayOfWeek.WEDNESDAY, "mercoledì" to DayOfWeek.WEDNESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "gio" to DayOfWeek.THURSDAY, "giovedì" to DayOfWeek.THURSDAY, "giovedi" to DayOfWeek.THURSDAY,
        "ven" to DayOfWeek.FRIDAY, "venerdì" to DayOfWeek.FRIDAY, "venerdi" to DayOfWeek.FRIDAY,
        "sab" to DayOfWeek.SATURDAY, "sabato" to DayOfWeek.SATURDAY, "dom" to DayOfWeek.SUNDAY, "domenica" to DayOfWeek.SUNDAY,
    )

    /** Le cose da fare della nota; [people]: (id, nome) di chi c'è; [me] il tuo id. */
    fun tasks(text: String, people: List<Pair<String, String>>, me: String, today: LocalDate): List<Task> =
        text.lines().mapNotNull { line ->
            val m = Regex("@([\\p{L}][\\p{L}.'-]*)\\s*:?\\s+(.+)").find(line.trim()) ?: return@mapNotNull null
            val name = m.groupValues[1]
            val (body, due) = dueOf(m.groupValues[2].trim().trimEnd('.'), today)
            if (body.isBlank()) return@mapNotNull null
            Task(who(name, people, me), name, body.replaceFirstChar { it.uppercase() }, due)
        }

    private fun who(name: String, people: List<Pair<String, String>>, me: String): String? {
        val n = name.lowercase()
        if (n == "io" || n == "me") return me
        val hits = people.filter { (_, full) -> full.lowercase().split(' ', '.').any { it == n } || full.lowercase().startsWith(n) }
        return hits.singleOrNull()?.first
    }

    /** La scadenza scritta in fondo alla riga, e la riga senza. */
    fun dueOf(s: String, today: LocalDate): Pair<String, LocalDate?> {
        val words = s.split(Regex("\\s+")).toMutableList()
        fun cut(n: Int) = words.dropLast(n).joinToString(" ").trimEnd(',', ' ', '·', '-', '—').let { it.removeSuffix(" entro").removeSuffix(" per").trim() }
        val last = words.lastOrNull()?.lowercase()?.trimEnd('.', ',') ?: return s to null
        val prev = words.getOrNull(words.size - 2)?.lowercase()?.trimEnd('.', ',')
        Regex("(\\d{1,2})/(\\d{1,2})").matchEntire(last)?.let { m ->
            val d = runCatching { LocalDate.of(today.year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() ?: return s to null
            return cut(1) to if (d.isBefore(today)) d.plusYears(1) else d
        }
        if (last == "oggi") return cut(1) to today
        if (last == "domani") return cut(1) to today.plusDays(1)
        days[last]?.let { return cut(1) to next(today, it) }
        val num = last.toIntOrNull()
        if (num != null && prev != null && days[prev] != null && num in 1..31) {
            var d = next(today, days.getValue(prev))
            // «ven 22»: il venerdì con quel numero, entro due mesi.
            repeat(9) { if (d.dayOfMonth != num) d = d.plusWeeks(1) }
            return cut(2) to (if (d.dayOfMonth == num) d else next(today, days.getValue(prev)))
        }
        return s to null
    }

    private fun next(today: LocalDate, d: DayOfWeek): LocalDate {
        var x = today
        while (x.dayOfWeek != d) x = x.plusDays(1)
        return x
    }

    /** Le frasi da proporre per il registro delle decisioni: «decidiamo che…», «si decide…», «deciso:». */
    fun decisions(text: String): List<String> = text.lines().map { it.trim() }.filter { l ->
        Regex("^(decidiamo|abbiamo deciso|si decide|deciso|decisione)\\b", RegexOption.IGNORE_CASE).containsMatchIn(l)
    }.map { l -> l.replace(Regex("^(decidiamo|abbiamo deciso|si decide|deciso|decisione)\\s*(che|di|:)?\\s*", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.uppercase() } }
        .filter { it.length >= 3 }
}
