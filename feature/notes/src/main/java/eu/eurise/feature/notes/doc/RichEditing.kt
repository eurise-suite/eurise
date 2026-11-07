package eu.eurise.feature.notes.doc

/**
 * Applica al testo ricco una modifica fatta nel campo di testo, che restituisce solo il nuovo testo:
 * si ricava quale tratto è cambiato confrontando inizio e fine comuni, si spostano gli stili e al testo
 * inserito si danno gli stili "di scrittura".
 *
 * @param typing stili da dare al testo inserito; se nullo continuano quelli del carattere prima
 *   (scrivendo dopo una parola in grassetto si continua in grassetto).
 */
fun RichText.edit(newText: String, typing: Set<Mark>? = null): RichText {
    val old = text
    if (old == newText) return this
    var prefix = 0
    val maxPrefix = minOf(old.length, newText.length)
    while (prefix < maxPrefix && old[prefix] == newText[prefix]) prefix++
    var suffix = 0
    val maxSuffix = minOf(old.length, newText.length) - prefix
    while (suffix < maxSuffix && old[old.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++

    val oldEnd = old.length - suffix
    val inserted = newText.length - suffix - prefix
    val delta = newText.length - old.length

    // Gli span non si allungano da soli sul testo inserito ai loro bordi: ci pensano gli stili di scrittura.
    fun mapStart(pos: Int): Int = when {
        pos < prefix -> pos
        pos >= oldEnd -> pos + delta
        else -> prefix + inserted
    }

    fun mapEnd(pos: Int): Int = when {
        pos <= prefix -> pos
        pos >= oldEnd -> pos + delta
        else -> prefix
    }

    val marks = typing ?: marksIn(prefix, prefix)
    val moved = spans.map { s -> Span(mapStart(s.start), mapEnd(s.end), s.mark) }
    val added = if (inserted > 0) marks.map { Span(prefix, prefix + inserted, it) } else emptyList()
    return RichText(newText, normalize(newText.length, moved + added))
}

/** Toglie uno stile dai caratteri da [start] a [end]. */
fun RichText.removeMark(start: Int, end: Int, matches: (Mark) -> Boolean): RichText {
    val out = mutableListOf<Span>()
    for (s in spans) {
        if (!matches(s.mark) || s.end <= start || s.start >= end) {
            out += s
            continue
        }
        if (s.start < start) out += Span(s.start, start, s.mark)
        if (s.end > end) out += Span(end, s.end, s.mark)
    }
    return copy(spans = normalize(text.length, out))
}

/**
 * Attiva o disattiva uno stile sulla selezione: se tutti i caratteri lo hanno già viene tolto,
 * altrimenti viene dato a tutti. Colore ed evidenziatore sostituiscono quelli della stessa famiglia.
 */
fun RichText.toggleMark(start: Int, end: Int, mark: Mark): RichText {
    if (start >= end) return this
    if (mark in marksIn(start, end)) return removeMark(start, end) { it == mark }
    val cleared = removeMark(start, end) { it.sameFamily(mark) }
    return cleared.copy(spans = normalize(text.length, cleared.spans + Span(start, end, mark)))
}

/** Testo da [start] alla fine, con i suoi stili riportati all'inizio. */
fun RichText.substringFrom(start: Int): RichText = slice(start, text.length)

/** Testo da 0 a [end] escluso, con i suoi stili. */
fun RichText.substringTo(end: Int): RichText = slice(0, end)

fun RichText.slice(start: Int, end: Int): RichText {
    val a = start.coerceIn(0, text.length)
    val b = end.coerceIn(a, text.length)
    val cut = spans.mapNotNull { s ->
        val s0 = maxOf(s.start, a)
        val s1 = minOf(s.end, b)
        if (s1 > s0) Span(s0 - a, s1 - a, s.mark) else null
    }
    return RichText(text.substring(a, b), normalize(b - a, cut))
}

/** Unisce due testi ricchi uno dopo l'altro. */
operator fun RichText.plus(other: RichText): RichText {
    val shift = text.length
    return RichText(
        text + other.text,
        normalize(text.length + other.text.length, spans + other.spans.map { Span(it.start + shift, it.end + shift, it.mark) }),
    )
}
